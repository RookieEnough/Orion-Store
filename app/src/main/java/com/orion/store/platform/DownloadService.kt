package com.orion.store.platform

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Environment
import android.os.PowerManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.orion.store.data.Net
import com.orion.store.data.githubAssetApiUrl
import com.orion.store.data.githubAssetHeaders
import com.orion.store.data.parseGithubAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class DlStatus { RUNNING, SUCCESSFUL, FAILED }
data class Dl(val fileName: String, val progress: Int = 0, val status: DlStatus = DlStatus.RUNNING)

/** Download engine ported from AppTrackerPlugin.DownloadTask: Range resume, 3 retries, html/4xx fail fast, .tmp then rename.
 *  Big files from servers that take ranges come down over [PARTS] connections at once: CDNs like GitHub's cap speed per connection. */
object Downloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val calls = ConcurrentHashMap<String, Call>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private lateinit var app: Context
    private var wake: PowerManager.WakeLock? = null
    private val client by lazy {
        Net.client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }
    /** HTTP/2 would multiplex the parts onto one connection, which is exactly the cap being dodged. */
    private val h1 by lazy { client.newBuilder().protocols(listOf(Protocol.HTTP_1_1)).build() }
    private const val PARTS = 4
    private const val MIN_SPLIT = 4L shl 20
    val state = MutableStateFlow<Map<String, Dl>>(emptyMap())
    private enum class R { OK, RETRY, FATAL }

    /** The saved GitHub token, set at startup: a private repository's release assets only download with it. */
    @Volatile var githubToken: () -> String = { "" }

    /** For entry points that start without the store (the APK installer sheet): the folder only, no cleanup of other downloads. */
    fun attach(ctx: Context) { if (!::app.isInitialized) app = ctx.applicationContext }

    fun init(ctx: Context, keep: String?) {
        app = ctx.applicationContext
        runCatching {
            File(dir(), ".nomedia").createNewFile()
            dir().listFiles { f -> f.name.startsWith("OrionStore_") && f.name.endsWith(".apk") && f.name != keep && !jobs.containsKey(f.name) }?.forEach { it.delete() }
        }
    }

    fun dir(): File = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.apply { mkdirs() }
    fun io(block: () -> Unit) { scope.launch { runCatching(block) } }

    fun file(name: String?): File? {
        if (name.isNullOrBlank() || name == "." || name == ".." || '/' in name || '\\' in name) return null
        val d = dir().canonicalFile; val f = File(d, name).canonicalFile
        return f.takeIf { it.parentFile == d }
    }

    fun active() = jobs.keys.toList()

    fun start(url: String, fileName: String): String {
        if (file(fileName) == null) throw NativeError("INVALID_FILE_NAME")
        if (jobs.containsKey(fileName)) return fileName
        cancelled -= fileName
        state.update { it + (fileName to Dl(fileName)) }
        if (wake == null) wake = app.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Orion:DownloadLock").apply { setReferenceCounted(false) }
        wake?.acquire(30 * 60 * 1000L)
        jobs[fileName] = scope.launch { run(url, fileName) }
        DownloadService.sync(app)
        return fileName
    }

    fun cancel(fileName: String) {
        if (!jobs.containsKey(fileName)) throw NativeError("INVALID_DOWNLOAD_ID")
        cancelled += fileName; calls.keys.filter { it == fileName || it.startsWith("$fileName#") }.forEach { calls.remove(it)?.cancel() }; jobs[fileName]?.cancel()
    }

    /** getDownloadProgress semantics: active → RUNNING/FAILED, else SUCCESSFUL only when the file exists and is non-empty. */
    fun status(fileName: String): Dl = state.value[fileName]?.takeIf { it.status != DlStatus.SUCCESSFUL }
        ?: file(fileName)?.takeIf { it.exists() && it.length() > 0 }?.let { Dl(fileName, 100, DlStatus.SUCCESSFUL) } ?: Dl(fileName, 0, DlStatus.FAILED)

    fun forget(fileName: String) = state.update { it - fileName }

    private fun set(name: String, fn: (Dl) -> Dl) = state.update { m -> m[name]?.let { m + (name to fn(it)) } ?: m }

    /** Public links go out untouched. A GitHub release asset that answers 404 while a token is saved is a private repository's: the same file is asked from the API with the token. */
    private suspend fun target(url: String): Pair<String, Map<String, String>> {
        val token = githubToken()
        if (token.isBlank() || parseGithubAsset(url) == null) return url to emptyMap()
        val code = runCatching { client.newCall(req(url).header("Range", "bytes=0-0").build()).execute().use { it.code } }.getOrNull()
        if (code != 404) return url to emptyMap()
        val api = githubAssetApiUrl(url, token) { u, h -> Net.text(u, h) } ?: return url to emptyMap()
        return api to githubAssetHeaders(token)
    }

    private suspend fun run(url: String, name: String) {
        var result = R.RETRY
        try {
            val (source, hdr) = runCatching { target(url) }.getOrDefault(url to emptyMap())
            var tries = 0
            while (tries < 3 && name !in cancelled) {
                result = runCatching { attempt(source, name, hdr) }.getOrDefault(R.RETRY)
                if (result != R.RETRY || name in cancelled) break
                tries++; delay(tries * 2000L)
            }
        } finally {
            jobs.remove(name); calls.keys.filter { it == name || it.startsWith("$name#") }.forEach { calls.remove(it) }
            File(dir(), "$name.parts").delete()
            if (name in cancelled) { File(dir(), "$name.tmp").delete(); forget(name) }
            else set(name) { if (result == R.OK) it.copy(progress = 100, status = DlStatus.SUCCESSFUL) else it.copy(status = DlStatus.FAILED) }
            if (jobs.isEmpty()) wake?.takeIf { it.isHeld }?.release()
        }
    }

    private fun req(url: String, hdr: Map<String, String> = emptyMap()) = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/110.0.0.0").header("Accept-Encoding", "identity")
        .also { b -> hdr.forEach { (k, v) -> b.header(k, v) } }

    /** Null when the file is small or the server can't split it: the caller falls back to one stream. */
    private suspend fun split(url: String, name: String, fin: File, hdr: Map<String, String>): R? {
        val (real, total) = h1.newBuilder().readTimeout(12, TimeUnit.SECONDS).build().newCall(req(url, hdr).header("Range", "bytes=0-0").build()).also { calls[name] = it }.execute().use { r ->
            if (r.code != 206 || r.body.contentType()?.toString()?.contains("text/html") == true) return null
            r.request.url.toString() to (r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: return null)
        }
        if (total < MIN_SPLIT) return null
        // Its own name, so a single-stream resume never mistakes a half-filled split file for a finished prefix
        val temp = File(dir(), "$name.parts").apply { delete() }
        RandomAccessFile(temp, "rw").use { it.setLength(total) }
        val done = AtomicLong(); val step = total / PARTS; val stalled = java.util.concurrent.atomic.AtomicBoolean()
        val ok = coroutineScope {
            // Some servers (often what TVs reach through) accept the ranges and then send nothing: after 15 s without a byte, drop to one plain stream
            val ticker = launch {
                var seen = -1L; var still = 0
                while (true) {
                    delay(500); val now = done.get(); set(name) { it.copy(progress = (now * 100 / total).toInt()) }
                    if (now == seen) { if (++still >= 30) { stalled.set(true); calls.keys.filter { it.startsWith("$name#") }.forEach { calls.remove(it)?.cancel() } } } else { seen = now; still = 0 }
                }
            }
            val parts = (0 until PARTS).map { i ->
                async(Dispatchers.IO) {
                    val end = if (i == PARTS - 1) total - 1 else (i + 1) * step - 1; var pos = i * step
                    repeat(3) { t ->
                        runCatching {
                            h1.newCall(req(real).header("Range", "bytes=$pos-$end").build()).also { calls["$name#$i"] = it }.execute().use { r ->
                                if (r.code != 206) return@async false
                                RandomAccessFile(temp, "rw").use { out ->
                                    out.seek(pos); val buf = ByteArray(1 shl 16); val inp = r.body.byteStream()
                                    while (pos <= end) {
                                        if (name in cancelled || stalled.get()) return@async false
                                        val n = inp.read(buf, 0, minOf(buf.size.toLong(), end - pos + 1).toInt()); if (n < 0) break
                                        out.write(buf, 0, n); pos += n; done.addAndGet(n.toLong())
                                    }
                                    out.fd.sync()
                                }
                            }
                        }
                        if (pos > end) return@async true
                        if (name in cancelled || stalled.get()) return@async false
                        delay((t + 1) * 1000L)
                    }
                    false
                }
            }.awaitAll().all { it }
            ticker.cancel(); parts
        }
        if (name in cancelled) return R.RETRY
        if (!ok) { temp.delete(); return null }
        fin.delete(); return if (temp.renameTo(fin)) R.OK else R.RETRY
    }

    private suspend fun attempt(url: String, name: String, hdr: Map<String, String>): R {
        val temp = File(dir(), "$name.tmp"); val fin = File(dir(), name)
        var existing = if (temp.exists()) temp.length() else 0L
        // A probe that times out or breaks falls through to the plain stream instead of retrying the split forever
        if (existing == 0L) runCatching { split(url, name, fin, hdr) }.getOrNull()?.let { return it }
        val rb = req(url, hdr)
        if (existing > 0) rb.header("Range", "bytes=$existing-")
        val call = client.newCall(rb.build()).also { calls[name] = it }
        call.execute().use { r ->
            if (r.code >= 400 || r.body.contentType()?.toString()?.contains("text/html") == true) return R.FATAL
            var total = r.body.contentLength(); val resume = r.code == 206
            if (resume) total += existing else existing = 0
            RandomAccessFile(temp, "rw").use { out ->
                if (resume) out.seek(existing) else out.setLength(0)
                val buf = ByteArray(1 shl 16); var dl = existing; var last = 0L
                r.body.byteStream().use { inp ->
                    while (true) {
                        if (name in cancelled || !scope.isActive) return R.RETRY
                        val n = inp.read(buf); if (n < 0) break
                        out.write(buf, 0, n); dl += n
                        val now = System.currentTimeMillis()
                        if (now - last > 500) { last = now; val p = if (total > 0) (dl * 100 / total).toInt() else (dl % 100).toInt(); set(name) { it.copy(progress = p) } }
                    }
                }
                out.fd.sync()
            }
            if (total > 0 && temp.length() < total) return R.RETRY
            fin.delete(); return if (temp.renameTo(fin)) R.OK else R.RETRY
        }
    }
}

/** Foreground keep-alive for active downloads (channel orion_downloads_foreground, id 7301). */
class DownloadService : Service() {
    private val scope = MainScope()
    private var watch: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { ServiceCompat.startForeground(this, ID, note(0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }.onFailure { stopSelf(); return START_NOT_STICKY }
        if (watch == null) watch = scope.launch {
            Downloads.state.collect { m ->
                val active = m.values.filter { it.status == DlStatus.RUNNING }
                if (active.isEmpty()) { ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf() }
                else runCatching { NotificationManagerCompat.from(this@DownloadService).notify(ID, note(active.size, active.sumOf { it.progress } / active.size)) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun note(count: Int, avg: Int): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (count > 1) "$count downloads in progress" else "Downloading…")
            .setContentText(if (avg in 1..99) "$avg% · Orion Store will keep going in the background" else "Orion Store will keep going in the background")
            .setOnlyAlertOnce(true).setOngoing(true).setSilent(true).setCategory(NotificationCompat.CATEGORY_PROGRESS).setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, avg.coerceIn(0, 100), avg <= 0)
            .apply { launch?.let { setContentIntent(PendingIntent.getActivity(this@DownloadService, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) } }
            .build()
    }

    companion object {
        const val CHANNEL = "orion_downloads_foreground"
        const val ID = 7301
        fun ensureChannel(ctx: Context) = NotificationManagerCompat.from(ctx).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW).setName("Download Progress").setShowBadge(false).build())
        fun sync(ctx: Context) { runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java)) } }
    }
}
