package com.orion.store.platform

import com.orion.store.data.AppItem
import com.orion.store.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Chat providers for Orion AI. Gemini and Claude have their own formats, Local runs on the phone, the rest speak the OpenAI chat format. */
enum class AiProvider(val id: String, val label: String, val base: String, val model: String, val keyUrl: String, val keyHint: String) {
    GEMINI("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash", "https://aistudio.google.com/apikey", "AIza..."),
    OPENAI("openai", "ChatGPT", "https://api.openai.com/v1", "gpt-4o-mini", "https://platform.openai.com/api-keys", "sk-..."),
    CLAUDE("claude", "Claude", "https://api.anthropic.com/v1", "claude-haiku-4-5", "https://console.anthropic.com/settings/keys", "sk-ant-..."),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto", "https://openrouter.ai/keys", "sk-or-..."),
    GROQ("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "https://console.groq.com/keys", "gsk_..."),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "https://platform.deepseek.com/api_keys", "sk-..."),
    MISTRAL("mistral", "Mistral", "https://api.mistral.ai/v1", "mistral-small-latest", "https://console.mistral.ai/api-keys", "Mistral key"),
    CUSTOM("custom", "Custom", "", "", "", "Key, if the server needs one"),
    LOCAL("local", "On device", "", "", "", "");
}

class AiError(msg: String) : Exception(msg)

object OrionAi {
    data class Turn(val user: Boolean, val text: String)
    data class Answer(val reply: String, val picks: List<Int>, val features: List<Int> = emptyList())

    private val http by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }
    private val JSON_TYPE = "application/json".toMediaType()

    fun provider(id: String) = AiProvider.entries.firstOrNull { it.id == id } ?: AiProvider.GEMINI

    /** Cloud models get every catalog name (a few tokens each, same text every turn so providers cache it) and draw on what they
     *  know about those apps. On-device models have a small context, so they only see each message's local matches. */
    fun system(tv: Boolean, catalog: List<AppItem>, guide: List<Pair<String, String>>, full: Boolean) = buildString {
        append("""你是 Orion Store 内置的 Orion AI。Orion Store 是一款免费开源、无广告的安卓应用商店。请帮助用户从 Orion 目录中查找应用。
- 推荐的每个应用都要在方括号里标注目录编号，例如 Metrolist [12]。用户想要应用时推荐 3 到 5 款，好的排前面，每款附简短说明。
- 只推荐${if (full) "下方目录中的应用。消息中的“目录匹配”会为可能的结果补充详情" else "最新消息中“目录匹配”里的应用"}。绝不要编造应用或编号。如果没有合适的，请如实说明并给出更好的提问建议。
- 关于 Orion 本身的问题（怎么做某件事、某个设置在哪、某功能是什么），请根据${if (full) "下方" else "消息中的"}Orion 指南用两三句话回答，并带上编号提及该功能，例如 调色板制作器 [g12]，方便用户直接打开。绝不要编造功能。
- 打招呼和闲聊时，用一句话回答，不提应用。
- 只输出纯文本，不要 Markdown，控制在 90 字以内。
- 关于 Orion：安卓、电脑和电视目录，自动更新，Guardian 与 Sentinel 安全工具，备份，主题与调色板，经验值与徽章。
- 用户当前使用${if (tv) "的是安卓电视，请优先推荐电视应用，其次是安卓应用" else "的是安卓手机，请优先推荐安卓应用"}。只有用户明确要电脑或 Windows 软件时才推荐电脑应用。""")
        if (full) { append("\n\nOrion 指南（编号 章节 / 功能：作用）：\n"); guide.forEachIndexed { i, (t, b) -> append('g').append(i).append(' ').append(t).append(": ").append(b).append('\n') } }
        if (!full) { append("\n目录分类："); append(catalog.map { it.category }.filter { it.isNotBlank() }.distinct().sorted().joinToString(", ")); return@buildString }
        append("\n\n目录（编号 名称），按平台和分类分组：\n")
        catalog.withIndex().groupBy { it.value.platform.ifBlank { "Android" } }.forEach { (platform, apps) ->
            append("\n# ").append(platform).append(" apps\n")
            apps.groupBy { it.value.category.ifBlank { "Other" } }.toSortedMap().forEach { (cat, list) ->
                append('[').append(cat).append("] "); list.joinTo(this, "; ") { "${it.index} ${it.value.name}" }; append('\n')
            }
        }
    }

    private val SYNONYMS = mapOf(
        "music" to "audio song player stream", "song" to "music audio", "audio" to "music player", "podcast" to "audio podcast",
        "video" to "player movie stream", "movie" to "video film stream", "film" to "movie video", "watch" to "video stream", "show" to "video stream anime",
        "anime" to "manga stream", "manga" to "comic reader", "book" to "reader ebook", "read" to "reader", "comic" to "manga reader",
        "ad" to "adblock block", "ads" to "adblock block", "adblock" to "block", "privacy" to "tracker vpn", "vpn" to "privacy proxy",
        "game" to "emulator games", "emulat" to "game console retro", "retro" to "emulator", "photo" to "camera gallery image", "picture" to "gallery photo",
        "chat" to "message messenger", "message" to "messenger chat", "download" to "downloader", "file" to "manager", "browser" to "web",
        "keyboard" to "input", "launcher" to "home", "note" to "notes", "youtube" to "video", "spotify" to "music", "instagram" to "social",
        "translate" to "translator language", "wallpaper" to "theme customization", "icon" to "customization theme", "backup" to "sync",
    )
    private val STOP = setOf("app", "apps", "the", "and", "for", "that", "with", "want", "need", "some", "which", "lets", "let", "can", "best", "good",
        "like", "any", "you", "give", "find", "show", "please", "something", "him", "her", "them", "his", "use", "using", "get", "what", "whats", "how",
        "hey", "hello", "orion", "store", "free", "without", "from", "this", "are", "there", "have", "has", "not", "but", "make", "more", "one", "also")

    /** Catalog indices that match the words of [query], best first. A small synonym table bridges "listen" style wording to app text. */
    fun matches(catalog: List<AppItem>, query: String, limit: Int): List<Int> {
        val base = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in STOP }.map { it.removeSuffix("s") }
        if (base.isEmpty()) return emptyList()
        val extra = base.flatMap { w -> SYNONYMS.entries.firstOrNull { w.startsWith(it.key) }?.value?.split(' ').orEmpty() }
        val words = (base.map { it to 2 } + extra.map { it to 1 }).distinctBy { it.first }
        return catalog.withIndex().map { (i, a) ->
            val n = a.name.lowercase(); val c = a.category.lowercase(); val t = a.tags.joinToString(" ").lowercase(); val d = a.description.lowercase()
            i to words.sumOf { (w, k) -> k * ((if (w in n) 4 else 0) + (if (w in c) 3 else 0) + (if (w in t) 2 else 0) + (if (w in d) 1 else 0)) }
        }.filter { it.second > 1 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    /** Guide entries whose words match [query], for on-device models that cannot hold the whole guide. */
    fun guideMatches(guide: List<Pair<String, String>>, query: String, limit: Int): List<Int> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in STOP }.map { it.removeSuffix("s") }
        if (words.isEmpty()) return emptyList()
        return guide.withIndex().map { (i, g) -> i to words.sumOf { w -> (if (w in g.first.lowercase()) 3 else 0) + (if (w in g.second.lowercase()) 1 else 0) } }
            .filter { it.second > 1 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun describe(catalog: List<AppItem>, ids: List<Int>, words: Int): String =
        if (ids.isEmpty()) "目录匹配：本条消息没有匹配的应用。"
        else "目录匹配：\n" + ids.joinToString("\n") { i -> catalog[i].let { "$i ${it.name} (${it.category}, ${it.platform}): ${it.description.replace(Regex("\\s+"), " ").take(words)}" } }

    /** Streams one reply; [onText] gets the whole text so far after every chunk. Returns the final text. */
    suspend fun stream(p: AiProvider, key: String, model: String, base: String, system: String, turns: List<Turn>, onText: (String) -> Unit): String {
        val m = model.ifBlank { p.model }.ifBlank { throw AiError("请先在 AI 设置中选择模型。") }
        val acc = StringBuilder()
        fun add(s: String?) { if (!s.isNullOrEmpty()) { acc.append(s); onText(acc.toString()) } }
        when (p) {
            AiProvider.GEMINI -> {
                val body = buildJsonObject {
                    putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
                    putJsonArray("contents") { turns.forEach { t -> addJsonObject { put("role", if (t.user) "user" else "model"); putJsonArray("parts") { addJsonObject { put("text", t.text) } } } } }
                    putJsonObject("generationConfig") {
                        put("temperature", .6); put("maxOutputTokens", 600)
                        // Thinking is what made answers take minutes; picking apps from a short list does not need it
                        when {
                            "2.5-flash" in m -> putJsonObject("thinkingConfig") { put("thinkingBudget", 0) }
                            m.startsWith("gemini-3") -> putJsonObject("thinkingConfig") { put("thinkingLevel", "low") }
                        }
                    }
                }
                sse(Request.Builder().url("${root(p, base)}/models/$m:streamGenerateContent?alt=sse").header("x-goog-api-key", key).post(body.toString().toRequestBody(JSON_TYPE)).build(), m) { o ->
                    o["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                        if (part.jsonObject["thought"]?.jsonPrimitive?.booleanOrNull != true) add(part.jsonObject["text"]?.jsonPrimitive?.contentOrNull)
                    }
                }
            }
            AiProvider.CLAUDE -> {
                val body = buildJsonObject {
                    put("model", m); put("max_tokens", 600); put("system", system); put("stream", true)
                    putJsonArray("messages") { turns.forEach { t -> addJsonObject { put("role", if (t.user) "user" else "assistant"); put("content", t.text) } } }
                }
                sse(Request.Builder().url("${root(p, base)}/messages").header("x-api-key", key).header("anthropic-version", "2023-06-01")
                    .post(body.toString().toRequestBody(JSON_TYPE)).build(), m) { o ->
                    if (o["type"]?.jsonPrimitive?.contentOrNull == "content_block_delta") add(o["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull)
                }
            }
            else -> {
                val body = buildJsonObject {
                    put("model", m); put("stream", true)
                    when { m.startsWith("gpt-5") -> put("reasoning_effort", "minimal"); Regex("^o\\d").containsMatchIn(m) -> put("reasoning_effort", "low") }
                    putJsonArray("messages") {
                        addJsonObject { put("role", "system"); put("content", system) }
                        turns.forEach { t -> addJsonObject { put("role", if (t.user) "user" else "assistant"); put("content", t.text) } }
                    }
                }
                sse(authed(p, key, Request.Builder().url("${root(p, base)}/chat/completions")).post(body.toString().toRequestBody(JSON_TYPE)).build(), m) { o ->
                    add(o["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull)
                }
            }
        }
        return acc.toString().ifBlank { throw AiError("服务商返回了空回答。") }
    }

    /** Models the key can use, for the picker in AI settings. */
    suspend fun models(p: AiProvider, key: String, base: String): List<String> = withContext(Dispatchers.IO) {
        when (p) {
            AiProvider.GEMINI -> get(Request.Builder().url("${root(p, base)}/models?pageSize=200").header("x-goog-api-key", key).build())["models"]?.jsonArray.orEmpty().map { it.jsonObject }
                .filter { o -> o["supportedGenerationMethods"]?.jsonArray?.any { it.jsonPrimitive.contentOrNull == "generateContent" } == true }
                .mapNotNull { it["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") }
            AiProvider.CLAUDE -> get(Request.Builder().url("${root(p, base)}/models?limit=100").header("x-api-key", key).header("anthropic-version", "2023-06-01").build())["data"]
                ?.jsonArray.orEmpty().mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
            else -> get(authed(p, key, Request.Builder().url("${root(p, base)}/models")).build())["data"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                .filterNot { id -> listOf("embed", "tts", "whisper", "dall-e", "image", "audio", "realtime", "moderation", "transcribe", "search").any { it in id } }
        }.sorted()
    }

    private fun root(p: AiProvider, base: String) = (if (p == AiProvider.CUSTOM) base.trim() else p.base).trimEnd('/').ifEmpty { throw AiError("请在 AI 设置中填写服务器地址。") }

    private fun authed(p: AiProvider, key: String, b: Request.Builder) = b.apply {
        if (key.isNotBlank()) header("Authorization", "Bearer $key")
        if (p == AiProvider.OPENROUTER) { header("HTTP-Referer", "https://github.com/RookieEnough/Orion-Store"); header("X-Title", "Orion Store") }
    }

    private fun fail(code: Int, body: String, model: String): AiError {
        val e = runCatching { json.parseToJsonElement(body).jsonObject["error"] }.getOrNull()
        val msg = (e as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: (e as? JsonPrimitive)?.contentOrNull
        return AiError(when (code) {
            401, 403 -> "服务商拒绝了此密钥，请在 AI 设置中检查。"
            404 -> if (model.isNotEmpty()) "未找到模型 $model，请在 AI 设置中另选一个。" else "未找到服务商地址。"
            429 -> "已达到速率限制或配额。请稍等，或向服务商确认你的套餐。"
            else -> msg?.take(200) ?: "服务商返回错误 $code。"
        })
    }

    private fun get(req: Request): JsonObject = try {
        http.newCall(req).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw fail(r.code, text, "")
            runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: throw AiError("服务商返回了 Orion 无法读取的内容。")
        }
    } catch (e: java.io.IOException) { throw AiError("无法连接服务商，请检查网络。") }

    /** Server-sent events: each "data:" line is one JSON chunk. Cancelling the coroutine cancels the call, which is what Stop does. */
    private suspend fun sse(req: Request, model: String, onChunk: (JsonObject) -> Unit) = withContext(Dispatchers.IO) {
        val call = http.newCall(req)
        val hook = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { r ->
                if (!r.isSuccessful) throw fail(r.code, r.body.string(), model)
                val src = r.body.source()
                while (true) {
                    coroutineContext.ensureActive()
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val d = line.substring(5).trim()
                    if (d == "[DONE]") break
                    runCatching { json.parseToJsonElement(d).jsonObject }.getOrNull()?.let { o ->
                        o["error"]?.let { throw AiError((it as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: "The provider stopped with an error.") }
                        onChunk(o)
                    }
                }
            }
        } catch (e: java.io.IOException) {
            coroutineContext.ensureActive(); throw AiError("无法连接服务商，请检查网络。")
        } finally { hook?.dispose() }
    }

    private val FEATURE = Regex("\\s*\\[g(\\d{1,3})]")
    private val REF = Regex("\\s*\\[(\\d{1,4}(?:\\s*,\\s*\\d{1,4})*)]")
    private val THINK = Regex("<think>[\\s\\S]*?(</think>|$)")

    /** Display text without the [12] markers or any leftover reasoning, plus the referenced catalog numbers in order. */
    fun parse(raw: String): Answer {
        val clean = raw.replace(THINK, "")
        val picks = REF.findAll(clean).flatMap { m -> m.groupValues[1].split(',').mapNotNull { it.trim().toIntOrNull() } }.distinct().toList()
        val features = FEATURE.findAll(clean).mapNotNull { it.groupValues[1].toIntOrNull() }.distinct().toList()
        return Answer(clean.replace(FEATURE, "").replace(REF, "").replace("**", "").trim(), picks, features)
    }
}
