package com.orion.store.platform

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.orion.store.R
import com.orion.store.data.HttpError
import com.orion.store.data.Net
import com.orion.store.data.VtStats
import com.orion.store.data.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder

enum class VtView { KEY, READY, SCANNING, RESULTS, ERROR }
enum class ScanTargetType { APK, DOWNLOADED_APK, REMOTE_APK }
data class ScanTarget(val type: ScanTargetType, val label: String, val detail: String)
data class VtEngine(val engine: String, val category: String, val result: String?)
data class VtResult(val type: ScanTargetType, val hash: String?, val stats: VtStats, val engines: List<VtEngine>, val fresh: Boolean, val permalink: String?)

private const val VT = "https://www.virustotal.com/api/v3"

private fun parseBody(ctx: Context, b: String): JsonObject {
    if (b.isEmpty()) return JsonObject(emptyMap())
    if (b.trimStart().startsWith("<")) throw Exception(ctx.getString(R.string.vt_virustotal_returned_an_html_page_instead_of_json))
    return runCatching { json.parseToJsonElement(b).jsonObject }.getOrElse { throw Exception(ctx.getString(R.string.vt_virustotal_returned_an_unexpected_response)) }
}

private fun apiError(ctx: Context, status: Int, fallback: String? = null) = when {
    status == 401 || status == 403 -> ctx.getString(R.string.vt_api_key_rejected_check_the_key)
    status == 404 -> ctx.getString(R.string.vt_no_virustotal_report_exists_for_this)
    status == 429 -> ctx.getString(R.string.vt_virustotal_quota_reached_free_keys_are)
    status >= 500 -> ctx.getString(R.string.vt_virustotal_is_having_trouble_right_now)
    else -> fallback ?: ctx.getString(R.string.vt_virustotal_request_failed)
}

private fun JsonObject.obj(k: String) = this[k] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.int(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 0
private fun JsonObject.str(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
private fun stats(o: JsonObject) = VtStats(o.int("harmless"), o.int("malicious"), o.int("suspicious"), o.int("undetected"), o.int("timeout"))
private fun engines(o: JsonObject) = o.map { (k, v) -> v.jsonObject.let { VtEngine(it.str("engine_name") ?: k, it.str("category") ?: "", it.str("result")) } }

/** Port of hooks/useVirusTotalScan.ts. Compose state so sheets observe it directly. */
class VtScan(private val tracker: AppTracker, private val appId: String, private val appName: String, private val packageName: String?, apiKey: String) {
    private val ctx: Context get() = tracker.ctx
    var view by mutableStateOf(if (apiKey.isNotBlank()) VtView.READY else VtView.KEY)
    var note by mutableStateOf(ctx.getString(R.string.vt_preparing_scan))
    var progress by mutableFloatStateOf(0f)
    var result by mutableStateOf<VtResult?>(null)
    var error by mutableStateOf("")

    private suspend fun get(path: String, key: String): Pair<Int, JsonObject> {
        val c = Net.client.newBuilder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        return try { 200 to parseBody(ctx, Net.text("$VT$path", mapOf("x-apikey" to key), c = c)) } catch (e: HttpError) { e.code to runCatching { parseBody(ctx, e.body) }.getOrDefault(JsonObject(emptyMap())) }
    }

    private fun step(n: String, p: Int) { note = n; progress = p / 100f }

    private suspend fun report(hash: String, key: String): VtResult? {
        val (s, b) = get("/files/$hash", key)
        if (s == 404) return null
        if (s !in 200..299) throw Exception(apiError(ctx, s))
        val a = b.obj("data").obj("attributes")
        return VtResult(ScanTargetType.APK, hash, stats(a.obj("last_analysis_stats")), engines(a.obj("last_analysis_results")), false, "https://www.virustotal.com/gui/file/$hash")
    }

    private suspend fun poll(id: String, key: String, hash: String): VtResult {
        repeat(18) { i ->
            step(if (i == 0) ctx.getString(R.string.vt_waiting_for_engines) else ctx.getString(R.string.vt_collecting_engine_verdicts), minOf(94, 58 + i * 2))
            delay(if (i < 3) 5000 else 10000)
            val (s, b) = get("/analyses/$id", key)
            if (s !in 200..299) throw Exception(apiError(ctx, s, ctx.getString(R.string.vt_analysis_polling_failed)))
            val a = b.obj("data").obj("attributes")
            if (a.str("status") == "completed") return VtResult(ScanTargetType.APK, hash, stats(a.obj("stats")), engines(a.obj("results")), true, "https://www.virustotal.com/gui/file/$hash")
        }
        throw Exception(ctx.getString(R.string.vt_virustotal_is_still_analyzing_this_app))
    }

    private suspend fun scanPath(path: String, key: String): VtResult {
        step(ctx.getString(R.string.vt_calculating_sha_256), 18)
        val hash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { tracker.hash(path) }
        step(ctx.getString(R.string.vt_checking_existing_virustotal_report), 34)
        report(hash, key)?.let { return it }
        step(ctx.getString(R.string.vt_uploading_apk_only_because_virustotal_has), 48)
        val (s, body) = tracker.vtUpload(path, key)
        val b = parseBody(ctx, body)
        if (s !in 200..299) throw Exception(apiError(ctx, s, ctx.getString(R.string.vt_upload_failed)))
        val id = b.obj("data").str("id") ?: throw Exception(ctx.getString(R.string.vt_virustotal_did_not_return_an_analysis))
        return poll(id, key, hash)
    }

    private fun tempName(url: String): String {
        var base = "${appId.ifEmpty { appName }}-vt-scan.apk"
        runCatching { URLDecoder.decode(URI(url).path.substringAfterLast('/'), "UTF-8") }.getOrNull()?.takeIf { Regex("\\.(apk|apks|xapk|zip)$", RegexOption.IGNORE_CASE).containsMatchIn(it) }?.let { base = it }
        val safe = base.replace(Regex("[^a-zA-Z0-9._-]"), "_"); val sfx = System.currentTimeMillis().toString(36); val dot = safe.lastIndexOf('.')
        return if (dot > 0) "${safe.take(dot)}_$sfx${safe.substring(dot)}" else "${safe}_$sfx.apk"
    }

    private suspend fun downloadAndScan(url: String, key: String): VtResult {
        val name = tempName(url)
        step(ctx.getString(R.string.vt_downloading_apk_for_file_scan), 8)
        try {
            Downloads.start(url, name)
            run wait@{
                repeat(180) {
                    val d = Downloads.status(name)
                    progress = d.progress.coerceIn(8, 40) / 100f
                    if (d.status == DlStatus.SUCCESSFUL) return@wait
                    if (d.status == DlStatus.FAILED) throw Exception(ctx.getString(R.string.vt_apk_download_failed_before_scanning))
                    delay(1000)
                }
                throw Exception(ctx.getString(R.string.vt_apk_download_took_too_long_try))
            }
            return scanPath(tracker.resolve(name).absolutePath, key)
        } finally { Downloads.forget(name); runCatching { tracker.deleteFile(name) } }
    }

    fun reset() { result = null; error = ""; progress = 0f; note = ctx.getString(R.string.vt_preparing_scan) }

    /** Returns the result, or null on error. [haptic] receives "success" | "warning" | "error". */
    suspend fun start(target: ScanTarget?, key: String, haptic: (String) -> Unit): VtResult? {
        val k = key.trim()
        if (k.isEmpty()) { view = VtView.KEY; return null }
        if (target == null) { error = ctx.getString(R.string.vt_no_installed_apk_downloaded_apk_or); view = VtView.ERROR; return null }
        reset(); view = VtView.SCANNING; step(ctx.getString(R.string.vt_starting_virustotal_scan), 8)
        return try {
            val r = when (target.type) {
                ScanTargetType.APK -> scanPath(tracker.extractApk(packageName ?: throw Exception(ctx.getString(R.string.vt_no_android_package_is_available_for))), k)
                ScanTargetType.DOWNLOADED_APK -> scanPath(tracker.resolve(target.detail).absolutePath, k)
                ScanTargetType.REMOTE_APK -> downloadAndScan(target.detail, k).copy(type = ScanTargetType.REMOTE_APK)
            }.let { if (target.type == ScanTargetType.DOWNLOADED_APK) it.copy(type = target.type) else it }
            step(ctx.getString(R.string.vt_report_ready), 100); delay(350)
            result = r; view = VtView.RESULTS
            haptic(if (r.stats.malicious + r.stats.suspicious >= 5) "warning" else "success"); r
        } catch (e: Exception) {
            error = e.message ?: ctx.getString(R.string.vt_virustotal_scan_failed); view = VtView.ERROR; haptic("error"); null
        }
    }
}
