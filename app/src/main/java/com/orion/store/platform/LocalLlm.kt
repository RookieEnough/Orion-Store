package com.orion.store.platform

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** On-device models for Orion AI: LiteRT-LM builds from Hugging Face's litert-community, downloaded through Orion's own downloader
 *  (resumable, keeps going in the background) and run on the CPU. */
object LocalLlm {
    class Model(val id: String, val name: String, val repo: String, val file: String, val mb: Int, val ramGb: Int, val blurb: String) {
        val url get() = "https://huggingface.co/$repo/resolve/main/$file?download=true"
        val fileName get() = "orion-llm-$file"
    }

    val MODELS = listOf(
        Model("smollm2-360m", "SmolLM2 360M", "litert-community/SmolLM2-360M-Instruct", "SmolLM2_360M_instruct.litertlm", 374, 3, "最轻量。任何手机都能快速运行，适合简单问答。"),
        Model("qwen3-0.6b", "Qwen3 0.6B", "litert-community/Qwen3-0.6B", "Qwen3-0.6B.litertlm", 614, 4, "快速且强大，大多数手机的最佳选择。"),
        Model("qwen2.5-1.5b", "Qwen2.5 1.5B", "litert-community/Qwen2.5-1.5B-Instruct", "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm", 1598, 6, "较新的手机可获得更聪明的回答。"),
        Model("phi4-mini", "Phi-4 mini", "litert-community/Phi-4-mini-instruct", "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm", 3910, 12, "能力最强，仅适合旗舰手机。"),
    )

    /** The engine's native library, fetched from Google's Maven on first use and checked against this hash before it is ever loaded. */
    private const val ENGINE_URL = "https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.18.1/litertlm-android-0.18.1.aar"
    private const val ENGINE_SHA = "cf4c55139c6fc86425ecfe75f8a907e6193ca665ecc19fbabcaba7ab361bdc43"
    const val ENGINE_FILE = "orion-llm-engine.aar"
    const val ENGINE_MB = 20
    private fun engineLib(ctx: Context) = File(ctx.filesDir, "llm/liblitertlm_jni.so")
    @Volatile private var libLoaded = false

    fun engineReady(ctx: Context) = engineLib(ctx).length() > 0 || installEngine(ctx)
    fun engineDownloading() = ENGINE_FILE in Downloads.active()
    fun downloadEngine() { if (!engineDownloading()) Downloads.start(ENGINE_URL, ENGINE_FILE) }

    /** Pulls the arm64 library out of a finished engine download, keeps it only if the hash matches, and drops the archive. */
    @Synchronized private fun installEngine(ctx: Context): Boolean {
        val aar = Downloads.file(ENGINE_FILE)?.takeIf { it.length() > 0 && !engineDownloading() } ?: return false
        val out = engineLib(ctx); val tmp = File(out.path + ".tmp")
        val ok = runCatching {
            out.parentFile?.mkdirs()
            java.util.zip.ZipFile(aar).use { z -> z.getInputStream(z.getEntry("jni/arm64-v8a/liblitertlm_jni.so")).use { i -> tmp.outputStream().use { i.copyTo(it) } } }
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString("") { "%02x".format(it) }
            sha == ENGINE_SHA && tmp.renameTo(out) && out.setReadOnly()
        }.getOrDefault(false)
        tmp.delete(); aar.delete(); Downloads.forget(ENGINE_FILE)
        return ok
    }

    fun model(id: String) = MODELS.firstOrNull { it.id == id }
    /** LiteRT-LM ships for 64-bit ARM only. */
    val supported get() = "arm64-v8a" in Build.SUPPORTED_ABIS
    fun ramGb(ctx: Context) = ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem.let { (it / 1e9).roundToInt() }
    fun freeMb() = Downloads.dir().usableSpace / 1_000_000
    /** The biggest model this phone has the memory for. */
    fun recommended(ctx: Context) = ramGb(ctx).let { r -> MODELS.lastOrNull { it.ramGb <= r } ?: MODELS.first() }

    fun file(m: Model): File? = Downloads.file(m.fileName)
    fun ready(m: Model) = (file(m)?.length() ?: 0) > 0 && m.fileName !in Downloads.active()
    fun download(ctx: Context, m: Model) { if (!engineReady(ctx)) downloadEngine(); Downloads.start(m.url, m.fileName) }
    fun cancel(m: Model) { runCatching { Downloads.cancel(m.fileName) } }
    fun delete(m: Model) { if (loaded == m.id) close(); file(m)?.delete(); Downloads.forget(m.fileName) }

    private val lock = Mutex()
    private var engine: Engine? = null; private var loaded: String? = null
    fun close() { runCatching { engine?.close() }; engine = null; loaded = null }

    /** One reply from the on-device model, streamed into [onText] as it grows. The engine stays loaded between messages. */
    suspend fun chat(ctx: Context, m: Model, system: String, turns: List<OrionAi.Turn>, onText: (String) -> Unit): String = withContext(Dispatchers.Default) {
        if (!supported) throw AiError("On-device AI needs a 64-bit ARM phone.")
        lock.withLock {
            val f = file(m)?.takeIf { ready(m) } ?: throw AiError("Download ${m.name} in AI settings first.")
            if (!libLoaded) {
                if (!engineReady(ctx)) { downloadEngine(); throw AiError("The on-device engine is still downloading. Try again in a moment.") }
                runCatching { System.load(engineLib(ctx).path) }.getOrElse { engineLib(ctx).let { f2 -> f2.setWritable(true); f2.delete() }; throw AiError("The on-device engine could not start on this phone.") }
                libLoaded = true
            }
            val e = engine?.takeIf { loaded == m.id } ?: run {
                close()
                runCatching { Engine(EngineConfig(modelPath = f.path, backend = Backend.CPU(), maxNumTokens = 2048, cacheDir = ctx.cacheDir.path)).apply { initialize() } }
                    .getOrElse { throw AiError("This phone could not load ${m.name}. Try a smaller model.") }
                    .also { engine = it; loaded = m.id }
            }
            val history = turns.dropLast(1).map { if (it.user) Message.user(it.text) else Message.model(it.text) }
            e.createConversation(ConversationConfig(systemInstruction = Contents.of(system), initialMessages = history,
                samplerConfig = SamplerConfig(topK = 40, topP = .95, temperature = .6, seed = 0), thinkingConfig = ThinkingConfig(false))).use { conv ->
                val acc = StringBuilder()
                try {
                    conv.sendMessageAsync(turns.last().text).collect { msg ->
                        val t = msg.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                        if (t.isNotEmpty()) { acc.append(t); onText(acc.toString()) }
                    }
                } catch (c: CancellationException) { runCatching { conv.cancelProcess() }; throw c }
                acc.toString().ifBlank { throw AiError("${m.name} sent an empty answer.") }
            }
        }
    }
}
