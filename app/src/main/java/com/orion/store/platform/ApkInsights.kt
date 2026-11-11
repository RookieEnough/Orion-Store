package com.orion.store.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/** What is inside an APK, read offline before it installs: the frameworks it was built with, its native libraries and whether they
 *  run on this phone and on 16 KB page phones, trackers found in its code, permissions (and which ones an update adds), components,
 *  SDK levels, signer and where the size goes. Nothing leaves the phone. */
object ApkInsights {
    data class Lib(val name: String, val abi: String, val size: Long, val align: Long?, val known: String?)
    data class Perm(val name: String, val dangerous: Boolean, val isNew: Boolean)
    data class Component(val kind: String, val total: Int, val exported: Int)
    data class Report(
        val frameworks: List<String>, val abis: List<String>, val runsHere: Boolean, val libs: List<Lib>,
        /** null when the APK has no 64-bit native code, so page size can't matter. */
        val pageReady: Boolean?,
        val trackers: List<Pair<String, String>>, val dexCount: Int, val classCount: Int,
        val sizes: List<Pair<String, Long>>,
        val minSdk: Int, val targetSdk: Int, val compileSdk: Int,
        val perms: List<Perm>, val components: List<Component>,
        val signer: String?, val debugSigned: Boolean, val debuggable: Boolean, val testOnly: Boolean,
    ) {
        val newPerms get() = perms.filter { it.isNew }
        val unaligned get() = libs.filter { it.abi in ABI64 && it.align != null && it.align < PAGE_16K }
    }

    const val PAGE_16K = 16_384L
    val ABI64 = setOf("arm64-v8a", "x86_64")

    // region rules (pure, unit tested)

    /** Frameworks and engines, told apart by files only they ship. Order is what users care about most first. */
    fun frameworks(entries: Collection<String>, packages: Set<String>): List<String> {
        val libs = entries.filter { it.startsWith("lib/") }.map { it.substringAfterLast('/') }.toSet()
        fun lib(vararg n: String) = n.any { it in libs }
        fun file(p: String) = entries.any { it.startsWith(p) }
        fun pkg(p: String) = packages.any { it == p || it.startsWith("$p/") }
        return buildList {
            if (lib("libflutter.so") || file("assets/flutter_assets/")) add("Flutter")
            if (lib("libreactnativejni.so", "libreactnative.so") || file("assets/index.android.bundle")) add(if (lib("libhermes.so")) "React Native (Hermes)" else "React Native")
            if (lib("libunity.so")) add(if (lib("libil2cpp.so")) "Unity (IL2CPP)" else "Unity")
            if (lib("libUE4.so", "libUnreal.so")) add("Unreal Engine")
            if (lib("libgodot_android.so")) add("Godot")
            if (lib("libcocos2dcpp.so", "libcocos2djs.so", "libcocos.so")) add("Cocos")
            if (lib("libgdx.so")) add("libGDX")
            if (lib("libmonodroid.so", "libmonosgen-2.0.so") || file("assemblies/")) add(".NET MAUI / Xamarin")
            if (libs.any { it.startsWith("libQt5Core") || it.startsWith("libQt6Core") }) add("Qt")
            if (lib("libNativeScript.so")) add("NativeScript")
            if (file("assets/capacitor.config.json")) add("Capacitor") else if (file("assets/www/cordova.js")) add("Cordova")
            if (libs.any { it.startsWith("libpython3") }) add("Python")
            if (lib("libgojni.so")) add("Go")
            if (pkg("androidx/compose/runtime") || file("META-INF/androidx.compose.runtime_runtime.version")) add("Jetpack Compose")
            if (pkg("kotlin") || file("kotlin/kotlin.kotlin_builtins")) add("Kotlin")
        }
    }

    /** Well-known native libraries, by file name prefix. */
    private val KNOWN_LIBS = listOf(
        "libflutter" to "Flutter engine", "libapp.so" to "Flutter app code", "libhermes" to "Hermes JavaScript engine", "libjsc" to "JavaScriptCore",
        "libreactnative" to "React Native", "libunity" to "Unity engine", "libil2cpp" to "Unity IL2CPP code", "libmonodroid" to ".NET runtime", "libmonosgen" to ".NET runtime",
        "libc++_shared" to "C++ standard library", "libsqlcipher" to "SQLCipher encrypted database", "libsqlite" to "SQLite", "librealm" to "Realm database",
        "libmmkv" to "MMKV storage", "libtensorflowlite" to "TensorFlow Lite", "libonnxruntime" to "ONNX Runtime", "libopencv" to "OpenCV",
        "libavcodec" to "FFmpeg", "libavformat" to "FFmpeg", "libavutil" to "FFmpeg", "libffmpeg" to "FFmpeg", "libswscale" to "FFmpeg", "libvlc" to "VLC media engine", "libmpv" to "mpv player",
        "libsentry" to "Sentry crash reporting", "libcrashlytics" to "Firebase Crashlytics", "libbarhopper" to "ML Kit barcode scanner", "libimagepipeline" to "Fresco images",
        "libyoga" to "Yoga layout", "libconscrypt" to "Conscrypt TLS", "libcronet" to "Cronet networking", "libssl" to "OpenSSL", "libcrypto" to "OpenSSL", "libjnidispatch" to "JNA",
        "libwebp" to "WebP images", "libpl_droidsonroids_gif" to "GIF decoder", "libgodot" to "Godot engine", "libUE4" to "Unreal Engine", "libv8" to "V8 JavaScript engine",
        "libnode" to "Node.js", "libpython" to "Python", "libgojni" to "Go runtime", "libQt" to "Qt", "libaria2" to "aria2 downloads", "libtor" to "Tor",
        "libwg-go" to "WireGuard", "libsodium" to "libsodium crypto", "libzstd" to "Zstandard", "libdatastore_shared_counter" to "AndroidX DataStore",
        "libsurface_util_jni" to "CameraX", "libimage_processing_util_jni" to "CameraX", "libandroidx.graphics.path" to "AndroidX Graphics", "libmlkit" to "ML Kit",
        "libtermux" to "Termux", "libmain" to "Native entry point", "libgame" to "Game code", "libnative-lib" to "App native code",
    )
    fun knownLib(name: String) = KNOWN_LIBS.firstOrNull { name.startsWith(it.first) }?.second

    /** Tracker SDKs by code package (Exodus-style signatures, a curated subset), with what they do. */
    private val TRACKERS = listOf(
        Triple("com/google/firebase/analytics", "Google Firebase Analytics", "Analytics"), Triple("com/google/android/gms/analytics", "Google Analytics", "Analytics"),
        Triple("com/google/android/gms/tagmanager", "Google Tag Manager", "Analytics"), Triple("com/google/firebase/crashlytics", "Firebase Crashlytics", "Crash reporting"),
        Triple("com/crashlytics", "Firebase Crashlytics", "Crash reporting"), Triple("com/google/firebase/perf", "Firebase Performance", "Profiling"),
        Triple("com/google/android/gms/ads", "Google AdMob", "Ads"), Triple("com/facebook/appevents", "Facebook Analytics", "Analytics"),
        Triple("com/facebook/ads", "Facebook Audience Network", "Ads"), Triple("com/appsflyer", "AppsFlyer", "Attribution"), Triple("com/adjust/sdk", "Adjust", "Attribution"),
        Triple("io/branch", "Branch", "Attribution"), Triple("com/kochava", "Kochava", "Attribution"), Triple("com/amplitude", "Amplitude", "Analytics"),
        Triple("com/mixpanel", "Mixpanel", "Analytics"), Triple("com/segment/analytics", "Segment", "Analytics"), Triple("com/flurry", "Flurry", "Analytics"),
        Triple("io/sentry", "Sentry", "Crash reporting"), Triple("com/bugsnag", "Bugsnag", "Crash reporting"), Triple("com/instabug", "Instabug", "Crash reporting"),
        Triple("com/microsoft/appcenter", "Microsoft App Center", "Analytics"), Triple("com/microsoft/clarity", "Microsoft Clarity", "Profiling"),
        Triple("com/newrelic", "New Relic", "Profiling"), Triple("com/datadog", "Datadog", "Profiling"), Triple("com/onesignal", "OneSignal", "Notifications"),
        Triple("com/clevertap", "CleverTap", "Profiling"), Triple("com/braze", "Braze", "Profiling"), Triple("com/appboy", "Braze", "Profiling"),
        Triple("com/applovin", "AppLovin", "Ads"), Triple("com/unity3d/ads", "Unity Ads", "Ads"), Triple("com/unity3d/services", "Unity Ads", "Ads"),
        Triple("com/ironsource", "ironSource", "Ads"), Triple("com/vungle", "Vungle", "Ads"), Triple("com/chartboost", "Chartboost", "Ads"), Triple("com/inmobi", "InMobi", "Ads"),
        Triple("com/mopub", "MoPub", "Ads"), Triple("com/bytedance/sdk/openadsdk", "Pangle", "Ads"), Triple("com/mbridge", "Mintegral", "Ads"),
        Triple("com/yandex/metrica", "Yandex AppMetrica", "Analytics"), Triple("io/appmetrica", "Yandex AppMetrica", "Analytics"),
        Triple("com/huawei/hms/analytics", "Huawei Analytics", "Analytics"), Triple("org/matomo", "Matomo", "Analytics"), Triple("com/posthog", "PostHog", "Analytics"),
        Triple("io/heap", "Heap", "Analytics"), Triple("com/smartlook", "Smartlook", "Profiling"), Triple("com/uxcam", "UXCam", "Profiling"), Triple("ly/count/android", "Countly", "Analytics"),
    )

    /** Tracker name to category for every signature one of [packages] (slash form, like `com/appsflyer/internal`) falls under. */
    fun trackers(packages: Set<String>): List<Pair<String, String>> = TRACKERS
        .filter { (p, _, _) -> packages.any { it == p || it.startsWith("$p/") } }
        .map { it.second to it.third }.distinct()

    private fun u16(b: ByteArray, o: Int, le: Boolean) = if (le) (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) else ((b[o].toInt() and 0xff) shl 8) or (b[o + 1].toInt() and 0xff)
    private fun u32(b: ByteArray, o: Int, le: Boolean): Long = (0 until 4).fold(0L) { acc, i -> acc or ((b[o + i].toLong() and 0xff) shl (8 * if (le) i else 3 - i)) }
    private fun u64(b: ByteArray, o: Int, le: Boolean): Long = if (le) u32(b, o, true) or (u32(b, o + 4, true) shl 32) else (u32(b, o, false) shl 32) or u32(b, o + 4, false)

    /** The smallest PT_LOAD alignment of an ELF shared library, from its first bytes. 16 KB page phones need at least [PAGE_16K]. */
    fun elfLoadAlign(b: ByteArray): Long? = runCatching {
        if (b.size < 52 || b[0] != 0x7f.toByte() || b[1] != 'E'.code.toByte() || b[2] != 'L'.code.toByte() || b[3] != 'F'.code.toByte()) return null
        val is64 = b[4] == 2.toByte(); val le = b[5] == 1.toByte()
        val phoff = (if (is64) u64(b, 0x20, le) else u32(b, 0x1C, le)).toInt()
        val entSize = u16(b, if (is64) 0x36 else 0x2A, le); val count = u16(b, if (is64) 0x38 else 0x2C, le)
        var min: Long? = null
        for (i in 0 until count) {
            val o = phoff + i * entSize
            if (o < 0 || o + entSize > b.size) return min
            if (u32(b, o, le) == 1L) { val a = if (is64) u64(b, o + 0x30, le) else u32(b, o + 0x1C, le); min = minOf(min ?: a, a) }
        }
        min
    }.getOrNull()

    /** Packages (slash form) of every class a dex file references, read from its type table. */
    fun dexPackages(b: ByteArray, into: MutableSet<String>): Int = runCatching {
        if (b.size < 0x70 || b[0] != 'd'.code.toByte() || b[1] != 'e'.code.toByte() || b[2] != 'x'.code.toByte()) return 0
        val strOff = u32(b, 0x3C, true).toInt(); val typeCount = u32(b, 0x40, true).toInt(); val typeOff = u32(b, 0x44, true).toInt()
        var classes = 0
        for (t in 0 until typeCount) {
            val ti = typeOff + 4 * t; if (ti + 4 > b.size) break
            val si = strOff + 4 * u32(b, ti, true).toInt(); if (si + 4 > b.size || si < 0) continue
            var p = u32(b, si, true).toInt(); if (p < 0 || p >= b.size) continue
            while (p < b.size && b[p].toInt() and 0x80 != 0) p++ // uleb128 length
            p++
            if (p >= b.size || b[p] != 'L'.code.toByte()) continue
            var e = p; while (e < b.size && b[e] != 0.toByte()) e++
            val d = String(b, p + 1, e - p - 1, Charsets.UTF_8)
            val slash = d.lastIndexOf('/')
            if (slash > 0) into += d.substring(0, slash)
            classes++
        }
        classes
    }.getOrDefault(0)

    /** Where the bytes go, by part of the APK. */
    fun sizeGroup(entry: String) = when {
        entry.endsWith(".dex") -> "Code"
        entry.startsWith("lib/") -> "Native libraries"
        entry.startsWith("res/") || entry == "resources.arsc" -> "Resources"
        entry.startsWith("assets/") -> "Assets"
        else -> "Other"
    }
    // endregion

    private const val MAX_DEX = 96L * 1024 * 1024
    private val DEX = Regex("""classes\d*\.dex""")

    /** Reads everything; slow parts (dex scanning) are capped so a huge APK still answers in seconds. Run off the main thread. */
    @Suppress("DEPRECATION")
    fun read(ctx: Context, f: File): Report? = runCatching {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pi = pm.getPackageArchiveInfo(f.absolutePath, flags) ?: return null
        val ai = pi.applicationInfo
        val libs = ArrayList<Lib>(); val packages = HashSet<String>(); val sizes = LinkedHashMap<String, Long>()
        var dexCount = 0; var classCount = 0; var dexBytes = 0L
        val names = ArrayList<String>()
        ZipFile(f).use { z ->
            for (e in z.entries()) {
                if (e.isDirectory) continue
                val n = e.name; names += n
                sizes.merge(sizeGroup(n), e.compressedSize.coerceAtLeast(0), Long::plus)
                if (n.startsWith("lib/") && n.endsWith(".so") && n.count { it == '/' } == 2) {
                    // The program headers sit right after the ELF header, well inside the first 64 KB
                    val head = z.getInputStream(e).use { s -> ByteArray(64 * 1024).let { b -> var k = 0; while (k < b.size) { val r = s.read(b, k, b.size - k); if (r < 0) break; k += r }; b.copyOf(k) } }
                    libs += Lib(n.substringAfterLast('/'), n.split('/')[1], e.size, elfLoadAlign(head), knownLib(n.substringAfterLast('/')))
                } else if (n.matches(DEX)) {
                    dexCount++
                    if (dexBytes + e.size <= MAX_DEX) { dexBytes += e.size; classCount += dexPackages(z.getInputStream(e).use { it.readBytes() }, packages) }
                }
            }
        }
        val abis = libs.map { it.abi }.distinct().sortedBy { if (it in ABI64) 0 else 1 }
        val supported = Build.SUPPORTED_ABIS.toSet()
        val libs64 = libs.filter { it.abi in ABI64 }
        val installedPerms = runCatching { pm.getPackageInfo(pi.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toSet() }.getOrNull()
        val perms = pi.requestedPermissions.orEmpty().distinct().map { p ->
            val level = runCatching { pm.getPermissionInfo(p, 0).protectionLevel and PermissionInfo.PROTECTION_MASK_BASE }.getOrNull()
            Perm(p, level == PermissionInfo.PROTECTION_DANGEROUS || p in SENSITIVE, installedPerms != null && p !in installedPerms)
        }.sortedWith(compareByDescending<Perm> { it.isNew }.thenByDescending { it.dangerous }.thenBy { it.name })
        fun <T> comp(kind: String, list: Array<T>?, exported: (T) -> Boolean) = Component(kind, list?.size ?: 0, list?.count(exported) ?: 0)
        val cert = signerCert(pi)
        Report(
            frameworks = frameworks(names, packages), abis = abis, runsHere = abis.isEmpty() || abis.any { it in supported }, libs = libs.sortedWith(compareBy({ it.abi !in ABI64 }, { it.abi }, { it.name })),
            pageReady = if (libs64.isEmpty()) null else libs64.all { (it.align ?: PAGE_16K) >= PAGE_16K },
            trackers = trackers(packages), dexCount = dexCount, classCount = classCount,
            sizes = sizes.entries.sortedByDescending { it.value }.map { it.key to it.value },
            minSdk = ai?.minSdkVersion ?: 0, targetSdk = ai?.targetSdkVersion ?: 0,
            compileSdk = if (Build.VERSION.SDK_INT >= 31) ai?.compileSdkVersion ?: 0 else 0,
            perms = perms,
            components = listOf(comp("Activities", pi.activities) { it.exported }, comp("Services", pi.services) { it.exported },
                comp("Receivers", pi.receivers) { it.exported }, comp("Providers", pi.providers) { it.exported }),
            signer = cert?.let { MessageDigest.getInstance("SHA-256").digest(it.encoded).joinToString(":") { b -> "%02X".format(b) } },
            debugSigned = cert?.subjectX500Principal?.name?.contains("CN=Android Debug") == true,
            debuggable = ai != null && ai.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0, testOnly = ai != null && ai.flags and ApplicationInfo.FLAG_TEST_ONLY != 0,
        )
    }.getOrNull()

    /** Special access that is not a runtime permission but matters just as much. */
    private val SENSITIVE = setOf("android.permission.SYSTEM_ALERT_WINDOW", "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.QUERY_ALL_PACKAGES", "android.permission.PACKAGE_USAGE_STATS", "android.permission.WRITE_SETTINGS", "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.REQUEST_DELETE_PACKAGES", "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.USE_FULL_SCREEN_INTENT")

    @Suppress("DEPRECATION")
    private fun signerCert(pi: PackageInfo): X509Certificate? = runCatching {
        val sig = if (Build.VERSION.SDK_INT >= 28) pi.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }?.lastOrNull() else pi.signatures?.firstOrNull()
        sig?.let { CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(it.toByteArray())) as X509Certificate }
    }.getOrNull()

    /** A readable permission name: android.permission.READ_CONTACTS -> Read contacts. */
    fun permLabel(p: String) = p.substringAfterLast('.').lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** Android's marketing version for an API level. */
    fun androidName(api: Int) = when (api) {
        0 -> "?"; in 1..20 -> "4.x"; 21, 22 -> "5"; 23 -> "6"; 24, 25 -> "7"; 26, 27 -> "8"; 28 -> "9"; 29, 30, 31 -> (api - 19).toString(); 32 -> "12L"; else -> (api - 20).toString()
    }
}
