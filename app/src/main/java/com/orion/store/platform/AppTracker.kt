package com.orion.store.platform

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.orion.store.data.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import rikka.shizuku.Shizuku
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class NativeError(code: String) : Exception(code)

data class PkgInfo(val installed: Boolean, val version: String = "", val versionCode: Long = 0)
data class Installer(val packageName: String, val label: String, val isSystem: Boolean)
data class RiskyApp(val name: String, val packageName: String, val isSystem: Boolean, val permissions: List<String>)
data class SysApp(val packageName: String, val name: String, val isInstalled: Boolean, val isSystem: Boolean)
data class PermStatus(val storage: Boolean, val media: Boolean, val location: Boolean, val isLegacy: Boolean)
data class NetSecurity(
    val adbEnabled: Boolean, val adbWifiEnabled: Boolean, val hasProxy: Boolean, val isVpnActive: Boolean = false,
    val isCaptivePortal: Boolean = false, val isHiddenSsid: Boolean = false, val encryptionType: String = "UNKNOWN", val dnsServers: List<String> = emptyList(),
)

/** Bridges activity-result launches (installer, document picker) from non-UI code. Set by MainActivity. */
object ResultHost {
    var launch: ((Intent, (Int, Intent?) -> Unit) -> Unit)? = null
    suspend fun run(i: Intent): Pair<Int, Intent?> = suspendCancellableCoroutine { c ->
        val l = launch ?: return@suspendCancellableCoroutine c.resume(Activity.RESULT_CANCELED to null)
        try { l(i) { code, data -> if (c.isActive) c.resume(code to data) } } catch (_: ActivityNotFoundException) { c.resume(Activity.RESULT_CANCELED to null) }
    }
}

private const val APK_MIME = "application/vnd.android.package-archive"
private const val SHIZUKU_CODE = 7302
private val DANGEROUS = setOf(
    "android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS", "android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS",
    "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
)

/** Native port of AppTrackerPlugin.java. Download engine lives in [Downloads]. */
class AppTracker(private val ctx: Context) {
    private val pm = ctx.packageManager
    private val GRANTED = PackageManager.PERMISSION_GRANTED

    fun appInfo(pkg: String): PkgInfo = try {
        val p = pm.getPackageInfo(pkg, 0); PkgInfo(true, p.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(p))
    } catch (_: PackageManager.NameNotFoundException) { PkgInfo(false) }

    /** Big lookups read one package list instead of a getPackageInfo binder call (plus a NameNotFound exception) per candidate:
     *  a catalog sync probes about 12 package-name variants per app, which used to be ~12k IPCs on every boot. */
    fun appInfos(pkgs: Collection<String>): Map<String, PkgInfo> {
        if (pkgs.size <= 24) return pkgs.associateWith(::appInfo)
        val all = installedInfos() ?: return pkgs.associateWith(::appInfo)
        return pkgs.associateWith { all[it] ?: PkgInfo(false) }
    }

    /** Every installed package in one call. Some OEM builds throw on the full list (oversized binder reply): null, so callers fall back to per-package lookups. */
    fun installedInfos(): Map<String, PkgInfo>? = runCatching {
        pm.getInstalledPackages(0).associate { it.packageName to PkgInfo(true, it.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(it)) }
    }.getOrNull()

    fun installedPackages(): List<Pair<String, String>> =
        pm.getInstalledPackages(PackageManager.GET_META_DATA).map { (it.applicationInfo?.loadLabel(pm)?.toString() ?: it.packageName) to it.packageName }

    fun canRequestInstalls() = Build.VERSION.SDK_INT < 26 || pm.canRequestPackageInstalls()
    fun installSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${ctx.packageName}".toUri()) else Intent(Settings.ACTION_SECURITY_SETTINGS)

    fun permissionStatus(): PermStatus {
        fun has(p: String) = ctx.checkSelfPermission(p) == GRANTED
        val loc = has(Manifest.permission.ACCESS_FINE_LOCATION)
        return when {
            Build.VERSION.SDK_INT >= 33 -> PermStatus(Environment.isExternalStorageManager(), has(Manifest.permission.READ_MEDIA_IMAGES), loc, false)
            Build.VERSION.SDK_INT >= 30 -> PermStatus(Environment.isExternalStorageManager(), has(Manifest.permission.READ_EXTERNAL_STORAGE), loc, false)
            else -> has(Manifest.permission.WRITE_EXTERNAL_STORAGE).let { PermStatus(it, it, loc, true) }
        }
    }

    fun universalStorageIntent(): Intent? = if (Build.VERSION.SDK_INT >= 30)
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${ctx.packageName}".toUri()).takeIf { it.resolveActivity(pm) != null }
            ?: Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) else null

    fun resolve(fileName: String): File {
        val f = Downloads.file(fileName) ?: throw NativeError("INVALID_FILE_NAME")
        if (!f.exists()) throw NativeError("FILE_MISSING"); return f
    }

    fun isValidApk(f: File): Boolean = runCatching {
        if (f.length() < 100) return false
        val h = ByteArray(4); f.inputStream().use { if (it.read(h) != 4) return false }
        h[0] == 0x50.toByte() && h[1] == 0x4B.toByte() && h[2] == 3.toByte() && h[3] == 4.toByte() &&
            !pm.getPackageArchiveInfo(f.absolutePath, 0)?.packageName.isNullOrEmpty()
    }.getOrDefault(false)

    fun archivePackage(f: File): String? = runCatching { pm.getPackageArchiveInfo(f.absolutePath, 0)?.packageName }.getOrNull()

    private fun isSys(ai: ApplicationInfo) = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0

    suspend fun install(fileName: String, pref: String, pkg: String) {
        val f = Downloads.file(fileName) ?: throw NativeError("INVALID_FILE_NAME")
        repeat(5) { if (!f.exists() || f.length() == 0L) delay(200) }
        if (!f.exists()) throw NativeError("FILE_MISSING")
        if (!withContext(Dispatchers.IO) { isValidApk(f) }) { f.delete(); throw NativeError("CORRUPT_APK") }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val base = Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true)
        val handlers = pm.queryIntentActivities(base, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo }
        if (handlers.isEmpty()) throw NativeError("NO_INSTALLER")
        val pick = when (pref) {
            "chooser" -> null
            "package" -> handlers.firstOrNull { it.packageName == pkg }
            else -> handlers.firstOrNull { isSys(it.applicationInfo) && "packageinstaller" in it.packageName }
                ?: handlers.firstOrNull { "packageinstaller" in it.packageName } ?: handlers.firstOrNull { isSys(it.applicationInfo) }
        }
        val intent = pick?.let { Intent(base).setClassName(it.packageName, it.name) } ?: Intent.createChooser(base, "安装应用")
        when (ResultHost.run(intent).first) {
            Activity.RESULT_OK -> Unit
            Activity.RESULT_CANCELED -> throw NativeError("INSTALL_CANCELED")
            else -> throw NativeError("INSTALL_FAILED")
        }
    }

    @Suppress("DEPRECATION")
    fun installers(): List<Installer> = listOf(Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE)
        .flatMap { a -> listOf("content://orion/a.apk", "file:///a.apk").map { Intent(a).setDataAndType(it.toUri(), APK_MIME) } }
        .flatMap { pm.queryIntentActivities(it, 0) }.map { it.activityInfo }.distinctBy { it.packageName }.filter { it.packageName != ctx.packageName }
        .map { Installer(it.packageName, it.loadLabel(pm).toString(), isSys(it.applicationInfo)) }
        .sortedWith(compareBy({ !it.isSystem }, { it.label.lowercase() }, { it.packageName }))

    fun icon(pkg: String): Bitmap? = runCatching { pm.getApplicationIcon(pkg).toBitmap(144, 144) }.getOrNull()

    // Elevated shell: root when Root mode is on, otherwise Shizuku. Every privileged tool goes through [sh], so both work everywhere.
    @Volatile var root = false
    fun shizukuRunning() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun elevatedReady() = root || runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == GRANTED }.getOrDefault(false)

    /** Asks the root manager for `su` (Magisk, KernelSU and APatch show their grant prompt here). */
    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0 && "uid=0" in p.inputStream.bufferedReader().readText()
        }.getOrDefault(false)
    }

    suspend fun requestElevated() {
        if (root) return
        try {
            if (!Shizuku.pingBinder()) throw NativeError("Shizuku 未运行。请先启动 Shizuku 应用。")
            if (Shizuku.checkSelfPermission() == GRANTED) return
            val ok = suspendCancellableCoroutine { c ->
                val l = object : Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(code: Int, result: Int) {
                        if (code != SHIZUKU_CODE) return
                        Shizuku.removeRequestPermissionResultListener(this); if (c.isActive) c.resume(result == GRANTED)
                    }
                }
                Shizuku.addRequestPermissionResultListener(l); Shizuku.requestPermission(SHIZUKU_CODE)
                c.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(l) }
            }
            if (!ok) throw NativeError("你拒绝了 Shizuku 权限。")
        } catch (e: NativeError) { throw e } catch (e: Throwable) { throw NativeError("Shizuku 出错（请检查 Manifest/Provider）：${e.message}") }
    }

    private fun sh(cmd: String): Process = if (root) Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)) else Shizuku::class.java
        .getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        .apply { isAccessible = true }.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process

    private fun Process.err() = errorStream.bufferedReader().readText()

    private fun requireShizuku() {
        if (root) return
        if (!shizukuRunning()) throw NativeError("Shizuku 未运行")
        if (Shizuku.checkSelfPermission() != GRANTED) throw NativeError("缺少 Shizuku 权限")
    }

    /** Streams the APK into `pm install`. Root also records Orion as the installer, so Android's "App details in store" and update ownership point here. */
    suspend fun installShizuku(fileName: String, lock: Boolean = false) = withContext(Dispatchers.IO) {
        val f = resolve(fileName)
        requireShizuku()
        val owner = if (root) " -i ${ctx.packageName}" + if (lock && Build.VERSION.SDK_INT >= 34) " --update-ownership" else "" else ""
        fun attempt(flags: String) = sh("pm install -r --restrict-permissions$flags -S ${f.length()}").let { p ->
            p.outputStream.use { o -> f.inputStream().use { it.copyTo(o) } }
            p.waitFor() to p.err()
        }
        var (code, err) = attempt(owner)
        // Some ROMs ship a pm without --update-ownership: install anyway rather than fail the update
        if (code != 0 && "update-ownership" in err) attempt(owner.replace(" --update-ownership", "")).let { code = it.first; err = it.second }
        if (code != 0) throw NativeError("安装失败（代码 $code）：$err")
    }

    /** Root: reinstalls an app's own APKs with Orion as installer. Same signature and version, so data stays; Orion then owns its updates. */
    suspend fun adopt(pkg: String) = withContext(Dispatchers.IO) {
        val dir = File(pm.getApplicationInfo(pkg, 0).sourceDir).parent ?: throw NativeError("未找到 APK 路径")
        val p = sh("pm install -r -i ${ctx.packageName} \"$dir\"/*.apk")
        val code = p.waitFor(); if (code != 0) throw NativeError("接管失败（代码 $code）：${p.err()}")
    }

    /** Root or Shizuku: user apps Orion did not install, as package to installer label. */
    @Suppress("DEPRECATION")
    fun foreignInstalls(pkgs: Collection<String>): Map<String, String> = pkgs.mapNotNull { p ->
        runCatching {
            if (isSys(pm.getApplicationInfo(p, 0))) return@runCatching null
            val by = if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(p).installingPackageName else pm.getInstallerPackageName(p)
            if (by == ctx.packageName) null else p to (by?.let { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrDefault(it) } ?: "侧载安装")
        }.getOrNull()
    }.toMap()

    /** Frees cache across every app at once: Android trims until the requested space is free. */
    suspend fun trimCaches() = withContext(Dispatchers.IO) {
        requireShizuku(); val p = sh("pm trim-caches 1024G"); if (p.waitFor() != 0) throw NativeError("清理失败：${p.err()}")
    }

    // Sentinel
    suspend fun riskyApps(): List<RiskyApp> = withContext(Dispatchers.IO) {
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { pi ->
            val req = pi.requestedPermissions ?: return@mapNotNull null; val fl = pi.requestedPermissionsFlags ?: return@mapNotNull null
            val ai = pi.applicationInfo ?: return@mapNotNull null
            val perms = req.indices.filter { fl[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 && req[it] in DANGEROUS }.map { req[it] }
            perms.takeIf { it.isNotEmpty() }?.let { RiskyApp(ai.loadLabel(pm).toString(), pi.packageName, isSys(ai), it) }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun systemApps(): List<SysApp> = withContext(Dispatchers.IO) {
        pm.getInstalledPackages(PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES).filter { it.packageName != ctx.packageName }.mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            SysApp(p.packageName, runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(p.packageName),
                ai.flags and ApplicationInfo.FLAG_INSTALLED != 0, ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
        }
    }

    suspend fun toggleSystemApp(pkg: String, enable: Boolean) = withContext(Dispatchers.IO) {
        requireShizuku()
        val p = sh(if (enable) "cmd package install-existing $pkg" else "pm uninstall -k --user 0 $pkg")
        val exit = p.waitFor(); if (exit != 0) throw NativeError("操作失败（代码 $exit）：${p.err()}")
    }

    suspend fun revokePermission(pkg: String, perm: String) = withContext(Dispatchers.IO) {
        requireShizuku(); if (sh("pm revoke $pkg $perm").waitFor() != 0) throw NativeError("撤销失败，应用可能会崩溃或重启。")
    }

    @Suppress("DEPRECATION")
    suspend fun extractApk(pkg: String): String = withContext(Dispatchers.IO) {
        try {
            val pi = pm.getPackageInfo(pkg, 0); val ai = pi.applicationInfo!!
            val name = pm.getApplicationLabel(ai).toString().replace(Regex("[^a-zA-Z0-9]"), "_") + "_" + pi.versionName + ".apk"
            val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dest = File(pub, name)
            if (elevatedReady() && runCatching { sh("cp \"${ai.sourceDir}\" \"${dest.absolutePath}\"").waitFor() == 0 }.getOrDefault(false)) return@withContext dest.absolutePath
            // Note: falls back to app-private Downloads when public storage is not writable (scoped storage), path stays readable for hashing
            val out = runCatching { File(ai.sourceDir).copyTo(dest, true) }.getOrElse { File(ai.sourceDir).copyTo(File(Downloads.dir(), name), true) }
            out.absolutePath
        } catch (e: Exception) { throw NativeError("提取失败：${e.message}") }
    }

    fun hash(path: String): String {
        val f = File(path); if (!f.exists()) throw NativeError("未找到文件")
        return hashOrNull(f) ?: throw NativeError("计算哈希失败")
    }

    private fun hashOrNull(f: File): String? = runCatching {
        val d = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(8192)
        f.inputStream().use { s -> while (true) { val n = s.read(buf); if (n < 0) break; d.update(buf, 0, n) } }
        d.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** Returns (httpStatus, body), mirrors uploadVirusTotalFile including the 32 MB upload_url hop. */
    suspend fun vtUpload(path: String, apiKey: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        if (path.isEmpty() || apiKey.isEmpty()) throw NativeError("缺少参数")
        val f = File(path).canonicalFile; if (!f.exists() || !f.canRead()) throw NativeError("FILE_MISSING")
        var uploadUrl = "https://www.virustotal.com/api/v3/files"
        if (f.length() >= 32L * 1024 * 1024) {
            val c = Net.client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
            try {
                c.newCall(Request.Builder().url("https://www.virustotal.com/api/v3/files/upload_url").header("x-apikey", apiKey).header("Accept", "application/json").build()).execute().use { r ->
                    val b = r.body.string()
                    if (!r.isSuccessful) return@withContext r.code to b.ifEmpty { "获取上传地址失败" }
                    uploadUrl = com.orion.store.data.json.parseToJsonElement(b).jsonObject["data"]!!.jsonPrimitive.content
                }
            } catch (e: Exception) { throw NativeError("获取 VirusTotal 上传地址失败：${e.message}") }
        }
        val c = Net.client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.MILLISECONDS).build()
        val body = MultipartBody.Builder("----orionvt" + System.currentTimeMillis()).setType(MultipartBody.FORM)
            .addFormDataPart("file", f.name, f.asRequestBody(APK_MIME.toMediaType())).build()
        try {
            c.newCall(Request.Builder().url(uploadUrl).post(body).header("x-apikey", apiKey).header("Accept", "application/json").build()).execute().use { it.code to it.body.string() }
        } catch (e: Exception) { throw NativeError(e.message ?: "上传失败") }
    }

    /** Walks shared storage hashing files over 1 KB, emitting batches of 50 (scanResultBatch / scanComplete). */
    @Suppress("DEPRECATION")
    suspend fun scanDirectory(onBatch: suspend (List<Pair<String, String>>) -> Unit) = withContext(Dispatchers.IO) {
        val batch = ArrayList<Pair<String, String>>()
        suspend fun walk(dir: File, depth: Int) {
            if (depth > 15) return
            for (f in dir.listFiles() ?: return) {
                if (f.isDirectory) { if (!f.name.startsWith(".") && f.name != "Android" && f.name != "data") walk(f, depth + 1) }
                else if (f.isFile && f.length() > 1000) hashOrNull(f)?.let { batch += f.absolutePath to it; if (batch.size >= 50) { onBatch(batch.toList()); batch.clear() } }
            }
        }
        walk(Environment.getExternalStorageDirectory(), 0); if (batch.isNotEmpty()) onBatch(batch.toList())
    }

    @Suppress("DEPRECATION")
    fun networkSecurity(): NetSecurity {
        val cr = ctx.contentResolver
        val adb = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1
        val adbWifi = Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1
        val proxy = !System.getProperty("http.proxyHost").isNullOrEmpty() && !System.getProperty("http.proxyPort").isNullOrEmpty()
        val base = NetSecurity(adb, adbWifi, proxy)
        val cm = ctx.getSystemService(ConnectivityManager::class.java); val n = cm.activeNetwork ?: return base
        val caps = cm.getNetworkCapabilities(n) ?: return base
        var r = base.copy(isVpnActive = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN), isCaptivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
            val wi = (if (Build.VERSION.SDK_INT >= 29) caps.transportInfo as? WifiInfo else null) ?: wm?.connectionInfo
            if (wi != null) r = r.copy(isHiddenSsid = wi.hiddenSSID, encryptionType = encryption(wm, wi))
        }
        cm.getLinkProperties(n)?.let { lp -> r = r.copy(dnsServers = lp.dnsServers.mapNotNull { it.hostAddress }) }
        return r
    }

    @Suppress("DEPRECATION")
    private fun encryption(wm: WifiManager?, wi: WifiInfo): String = runCatching {
        if (Build.VERSION.SDK_INT >= 31) when (wi.currentSecurityType) {
            WifiInfo.SECURITY_TYPE_SAE -> "WPA3"; WifiInfo.SECURITY_TYPE_OWE -> "OWE"
            WifiInfo.SECURITY_TYPE_EAP, WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA2-EAP"
            WifiInfo.SECURITY_TYPE_PSK -> "WPA2"; WifiInfo.SECURITY_TYPE_WEP -> "WEP"; WifiInfo.SECURITY_TYPE_OPEN -> "OPEN"; else -> "UNKNOWN"
        } else wm?.scanResults?.firstOrNull { it.BSSID == wi.bssid }?.capabilities?.let {
            when { "WPA3" in it -> "WPA3"; "WPA2" in it -> "WPA2"; "WPA" in it -> "WPA"; "WEP" in it -> "WEP"; else -> "OPEN" }
        } ?: "UNKNOWN"
    }.getOrDefault("UNKNOWN")

    fun deleteFile(name: String) { val f = Downloads.file(name) ?: throw NativeError("INVALID_FILE_NAME"); Downloads.io { f.delete() } }

    @Suppress("DEPRECATION")
    suspend fun exportFile(name: String): String = withContext(Dispatchers.IO) {
        val src = Downloads.file(name) ?: throw NativeError("INVALID_FILE_NAME")
        if (!src.exists()) throw NativeError("File not found")
        try {
            val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
            val dot = name.lastIndexOf('.'); val stem = if (dot > 0) name.take(dot) else name; val ext = if (dot > 0) name.substring(dot) else ""
            var dest = File(pub, name); var i = 1
            while (dest.exists()) dest = File(pub, "${stem}_${i++}$ext")
            src.copyTo(dest)
            if (dest.length() != src.length()) throw NativeError("Verification failed")
            src.delete(); dest.absolutePath
        } catch (e: NativeError) { throw e } catch (e: Exception) { throw NativeError("Export failed: ${e.message}") }
    }

    suspend fun saveFile(content: String, fileName: String) {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, fileName)
        val (code, data) = ResultHost.run(i)
        val uri: Uri = data?.data?.takeIf { code == Activity.RESULT_OK } ?: throw NativeError("Cancelled")
        withContext(Dispatchers.IO) { (ctx.contentResolver.openOutputStream(uri) ?: throw NativeError("Stream error")).use { it.write(content.toByteArray()) } }
    }

    fun setHighRefreshRate(a: Activity, enable: Boolean) {
        val w = a.window; val p = w.attributes
        @Suppress("DEPRECATION") val d = (if (Build.VERSION.SDK_INT >= 30) a.display else null) ?: a.windowManager.defaultDisplay
        // A mode id is a hard request (OEM skins often ignore the rate hint); stay on the current resolution, take its fastest rate.
        val cur = d?.mode
        val best = if (enable && d != null && cur != null) d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }.maxByOrNull { it.refreshRate } else null
        p.preferredDisplayModeId = best?.modeId ?: 0
        p.preferredRefreshRate = best?.refreshRate ?: 0f
        w.attributes = p
    }

    fun share(title: String, text: String, url: String) = ctx.startActivity(
        Intent.createChooser(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "$text $url").setType("text/plain"), title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun launch(pkg: String) = ctx.startActivity((pm.getLaunchIntentForPackage(pkg) ?: pm.getLeanbackLaunchIntentForPackage(pkg) ?: throw NativeError("App not found")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun uninstall(pkg: String) = ctx.startActivity(Intent(Intent.ACTION_DELETE, "package:$pkg".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    @Suppress("DEPRECATION")
    fun deviceArch(): Pair<String, List<String>> = Build.SUPPORTED_ABIS.takeIf { it.isNotEmpty() }?.let { it[0] to it.toList() }
        ?: (Build.CPU_ABI ?: "unknown").let { it to listOf(it) }
}
