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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DlStatus { RUNNING, SUCCESSFUL, FAILED }
data class Dl(val fileName: String, val progress: Int = 0, val status: DlStatus = DlStatus.RUNNING)

/** Download engine ported from AppTrackerPlugin.DownloadTask: Range resume, 3 retries, html/4xx fail fast, .tmp then rename. */
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
    val state = MutableStateFlow<Map<String, Dl>>(emptyMap())
    private enum class R { OK, RETRY, FATAL }

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
        cancelled += fileName; calls.remove(fileName)?.cancel(); jobs[fileName]?.cancel()
    }

    /** getDownloadProgress semantics: active → RUNNING/FAILED, else SUCCESSFUL only when the file exists and is non-empty. */
    fun status(fileName: String): Dl = state.value[fileName]?.takeIf { it.status != DlStatus.SUCCESSFUL }
        ?: file(fileName)?.takeIf { it.exists() && it.length() > 0 }?.let { Dl(fileName, 100, DlStatus.SUCCESSFUL) } ?: Dl(fileName, 0, DlStatus.FAILED)

    fun forget(fileName: String) = state.update { it - fileName }

    private fun set(name: String, fn: (Dl) -> Dl) = state.update { m -> m[name]?.let { m + (name to fn(it)) } ?: m }

    private suspend fun run(url: String, name: String) {
        var result = R.RETRY
        try {
            var tries = 0
            while (tries < 3 && name !in cancelled) {
                result = runCatching { attempt(url, name) }.getOrDefault(R.RETRY)
                if (result != R.RETRY || name in cancelled) break
                tries++; delay(tries * 2000L)
            }
        } finally {
            jobs.remove(name); calls.remove(name)
            if (name in cancelled) { File(dir(), "$name.tmp").delete(); forget(name) }
            else set(name) { if (result == R.OK) it.copy(progress = 100, status = DlStatus.SUCCESSFUL) else it.copy(status = DlStatus.FAILED) }
            if (jobs.isEmpty()) wake?.takeIf { it.isHeld }?.release()
        }
    }

    private fun attempt(url: String, name: String): R {
        val temp = File(dir(), "$name.tmp"); val fin = File(dir(), name)
        var existing = if (temp.exists()) temp.length() else 0L
        val rb = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/110.0.0.0").header("Accept-Encoding", "identity")
        if (existing > 0) rb.header("Range", "bytes=$existing-")
        val call = client.newCall(rb.build()).also { calls[name] = it }
        call.execute().use { r ->
            if (r.code >= 400 || r.body.contentType()?.toString()?.contains("text/html") == true) return R.FATAL
            var total = r.body.contentLength(); val resume = r.code == 206
            if (resume) total += existing else existing = 0
            RandomAccessFile(temp, "rw").use { out ->
                if (resume) out.seek(existing) else out.setLength(0)
                val buf = ByteArray(16384); var dl = existing; var last = 0L
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
            .setContentTitle(if (count > 1) "$count 个下载进行中" else "正在下载…")
            .setContentText(if (avg in 1..99) "$avg% · Orion Store 将在后台继续下载" else "Orion Store 将在后台继续下载")
            .setOnlyAlertOnce(true).setOngoing(true).setSilent(true).setCategory(NotificationCompat.CATEGORY_PROGRESS).setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, avg.coerceIn(0, 100), avg <= 0)
            .apply { launch?.let { setContentIntent(PendingIntent.getActivity(this@DownloadService, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) } }
            .build()
    }

    companion object {
        const val CHANNEL = "orion_downloads_foreground"
        const val ID = 7301
        fun ensureChannel(ctx: Context) = NotificationManagerCompat.from(ctx).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW).setName("下载进度").setShowBadge(false).build())
        fun sync(ctx: Context) { runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java)) } }
    }
}
