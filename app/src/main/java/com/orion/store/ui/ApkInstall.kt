package com.orion.store.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.orion.store.OrionActivity
import com.orion.store.data.*
import com.orion.store.platform.ApkInsights
import com.orion.store.platform.AppTracker
import com.orion.store.platform.DhizukuBridge
import com.orion.store.platform.Downloads
import com.orion.store.platform.ResultHost
import com.orion.store.platform.WirelessAdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the opened file is, read from the APK itself. */
private class Apk(val file: File, val label: String, val pkg: String, val version: String, val code: Long, val icon: Bitmap?, val installed: String?, val installedCode: Long?, val clash: Clash?)

private sealed interface ApkStep {
    data object Reading : ApkStep
    data class Ready(val apk: Apk) : ApkStep
    data class Installing(val apk: Apk) : ApkStep
    data class Scan(val apk: Apk, val vt: com.orion.store.platform.VtScan) : ApkStep
    data class Inspect(val apk: Apk) : ApkStep
    data class Done(val apk: Apk) : ApkStep
    data class Failed(val apk: Apk?, val title: String, val body: String) : ApkStep
}

/** Opens APKs from file managers, browsers and share sheets as a sheet over the app that sent them, then installs them
 *  through the fastest route the user set up: root, Shizuku, Dhizuku or built-in wireless debugging, which skips Android's 24-hour wait. */
class ApkInstallActivity : ComponentActivity() {
    private val tracker by lazy { AppTracker(applicationContext) }
    private var step by mutableStateOf<ApkStep>(ApkStep.Reading)
    private var pending: ((Int, Intent?) -> Unit)? = null
    private val results = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> pending?.also { pending = null }?.invoke(r.resultCode, r.data) }
    private var previousHost: ((Intent, (Int, Intent?) -> Unit) -> Unit)? = null
    /** What is inside the APK, read in the background while the sheet already shows; null until then (or when it can't be read). */
    private var insights by mutableStateOf<ApkInsights.Report?>(null); private var inspecting by mutableStateOf(false)
    /** Beyond install for this sheet: starts as the store's setting, picks start from the last ones used. */
    private var optsOn by mutableStateOf(false); private var opts by mutableStateOf(emptySet<String>()); private var optInstaller by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(applicationContext); Downloads.attach(applicationContext)
        previousHost = ResultHost.launch
        ResultHost.launch = { i, cb -> pending = cb; results.launch(i) }
        tracker.root = Store.settings.flow.value.rootMode
        Store.settings.flow.value.let { st ->
            optsOn = st.installOptions; opts = st.installFlags.filterNot { it.startsWith("installer=") }.toSet()
            optInstaller = st.installFlags.firstOrNull { it.startsWith("installer=") }?.substringAfter('=').orEmpty()
        }
        read(intent)
        setContent {
            val s by Store.settings.flow.collectAsState()
            CompositionLocalProvider(LocalS provides s) { OrionTheme(s) { Sheet(s) } }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); read(intent) }

    override fun onDestroy() {
        ResultHost.launch = previousHost
        (step as? ApkStep.Ready)?.apk?.file?.delete(); (step as? ApkStep.Failed)?.apk?.file?.delete(); (step as? ApkStep.Scan)?.apk?.file?.delete(); (step as? ApkStep.Inspect)?.apk?.file?.delete()
        super.onDestroy()
    }

    private fun read(i: Intent?) {
        val uri = i?.data ?: (if (i?.action == Intent.ACTION_SEND) @Suppress("DEPRECATION") i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else null)
        if (uri == null) { finish(); return }
        step = ApkStep.Reading; insights = null
        lifecycleScope.launch {
            step = withContext(Dispatchers.IO) { runCatching { parse(uri) }.getOrNull() }?.let { ApkStep.Ready(it) }
                ?: ApkStep.Failed(null, "This file can't be installed", "It isn't a working APK. Split bundles (.apks, .xapk) aren't supported yet.")
            // Wireless debugging connects while the sheet is read, so Install doesn't sit on discovery
            if (step is ApkStep.Ready && (optionRoute(Store.settings.flow.value) ?: route(Store.settings.flow.value)?.first) == "adb") WirelessAdb.warm(applicationContext)
            (step as? ApkStep.Ready)?.apk?.let { a ->
                inspecting = true
                insights = withContext(Dispatchers.IO) { ApkInsights.read(applicationContext, a.file) }
                inspecting = false
                // Switches this APK can't install without are picked for the user; turning options on stays their call
                needed(a).takeIf { it.isNotEmpty() }?.let { opts = opts + it }
            }
        }
    }

    /** Copies the file in first: content URIs only stay readable while this activity lives, and every install route needs a real file. */
    private fun parse(uri: Uri): Apk? {
        // Files from earlier sheets that were swiped away mid-copy
        Downloads.dir().listFiles { x -> x.name.startsWith("Ext_") && System.currentTimeMillis() - x.lastModified() > 3_600_000 }?.forEach { it.delete() }
        val f = Downloads.file("Ext_${System.currentTimeMillis()}.apk") ?: return null
        contentResolver.openInputStream(uri)?.use { inp -> f.outputStream().use { inp.copyTo(it) } } ?: return null
        if (!tracker.isValidApk(f)) { f.delete(); return null }
        val pm = packageManager
        val pi = pm.getPackageArchiveInfo(f.absolutePath, 0) ?: run { f.delete(); return null }
        val ai = pi.applicationInfo?.apply { sourceDir = f.absolutePath; publicSourceDir = f.absolutePath }
        val name = runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull()
        val label = ai?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() }?.takeIf { it.isNotBlank() && it != pi.packageName } ?: name?.removeSuffix(".apk") ?: pi.packageName
        val inst = tracker.appInfo(pi.packageName).takeIf { it.installed }
        return Apk(f, label, pi.packageName, pi.versionName.orEmpty(), androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi),
            ai?.let { runCatching { it.loadIcon(pm).toBitmap(144, 144) }.getOrNull() }, inst?.version, inst?.versionCode, tracker.clash(f)?.second)
    }

    /** The same order the store uses for its own installs. */
    private fun route(s: Settings): Pair<String, String>? = when {
        s.rootMode -> "root" to "Installs with root, no prompts and no 24-hour wait"
        s.useShizuku -> "shizuku" to "Installs through Shizuku, no prompts and no 24-hour wait"
        s.useDhizuku -> "dhizuku" to "Installs through Dhizuku, no prompts"
        s.directInstall && WirelessAdb.supported -> "adb" to "Installs instantly through wireless debugging, skipping the 24-hour wait"
        else -> null
    }

    /** Beyond install runs pm install with switches: root, Shizuku or wireless debugging can, Dhizuku and Android's installer can't. */
    private fun optionRoute(s: Settings): String? = when {
        s.rootMode -> "root"
        s.useShizuku || tracker.elevatedReady() -> "shizuku"
        s.directInstall && WirelessAdb.supported -> "adb"
        else -> null
    }

    /** The switches this APK needs on this phone: older than installed, a test build, or a target SDK Android blocks. */
    private fun needed(a: Apk): Set<String> = buildSet {
        if (a.installedCode != null && a.code < a.installedCode) add("downgrade")
        insights?.let { r ->
            if (r.testOnly) add("test")
            val sdk = android.os.Build.VERSION.SDK_INT
            if (sdk >= 34 && r.targetSdk in 1 until (if (sdk >= 35) 24 else 23)) add("lowsdk")
        }
    }

    private fun install(apk: Apk, s: Settings, regular: Boolean = false): kotlinx.coroutines.Job = lifecycleScope.launch {
        val withOpts = !regular && optsOn && optionRoute(s) != null
        val picked = if (withOpts) pickedOptions(opts, optInstaller).also { p -> Store.settings.update { it.copy(installFlags = p) } } else null
        val r = if (regular) null else if (withOpts) optionRoute(s) else route(s)?.first
        if (r == null && !tracker.canRequestInstalls()) {
            step = ApkStep.Failed(apk, "Allow Orion to install apps", "Turn on \"Allow from this source\" for Orion Store, then come back and tap Install again.")
            runCatching { startActivity(tracker.installSettingsIntent()) }; return@launch
        }
        step = ApkStep.Installing(apk)
        val err = runCatching {
            when (r) {
                "root", "shizuku" -> { if (r == "shizuku" && !tracker.elevatedReady()) tracker.requestElevated(); tracker.installShizuku(apk.file.name, s.updateLock, picked ?: emptyList()) }
                "dhizuku" -> DhizukuBridge.install(applicationContext, apk.file)
                "adb" -> { if (!WirelessAdb.connected) WirelessAdb.connect(applicationContext); WirelessAdb.install(applicationContext, apk.file, picked) }
                else -> tracker.install(apk.file.name, "system", "")
            }
        }.exceptionOrNull()
        // No Wi-Fi and the user already chose Android's installer for that case; otherwise the failure sheet below offers it
        if (r == "adb" && err is WirelessAdb.NoWifiError && s.offWifiInstall == "installer") { install(apk, s, regular = true); return@launch }
        // The system installer reports cancel and failure alike; what is on the phone afterwards is the truth
        val now = withContext(Dispatchers.IO) { tracker.appInfo(apk.pkg) }
        val ok = err == null || (r == null && now.installed && now.versionCode == apk.code && now.versionCode != apk.installedCode)
        if (ok) { apk.file.delete(); step = ApkStep.Done(apk); return@launch }
        val msg = err?.message.orEmpty()
        if (r == null && "INSTALL_CANCELED" in msg) { step = ApkStep.Ready(apk); return@launch }
        // A connection problem is not an install failure: say what wireless debugging needs instead of a generic error
        if (r == "adb" && "INSTALL_" !in msg) { step = ApkStep.Failed(apk, "Wireless debugging didn't answer", "${msg.ifBlank { "Orion couldn't reach wireless debugging." }}\n\nTry again, or use Android's installer."); return@launch }
        val (t, b) = installFailureText(msg, apk.label)
        step = ApkStep.Failed(apk, t, b)
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun Sheet(s: Settings) {
        val cs = MaterialTheme.colorScheme
        ModalBottomSheet({ finish() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = cs.surfaceContainerLow,
            sheetMaxWidth = 560.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
                when (val st = step) {
                    ApkStep.Reading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
                    is ApkStep.Ready -> {
                        Header(st.apk)
                        val ins = insights; val need = needed(st.apk); val optR = optionRoute(s)
                        // Everything above the buttons scrolls, so the option list never pushes Install off screen
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            st.apk.clash?.let { c -> val (t, b) = clashText(c, st.apk.label); Note(Sym.GppMaybe, t, b, cs.errorContainer, cs.onErrorContainer) }
                            // Refusals Android would only give after the install starts, said before it
                            ins?.let { r ->
                                val sdk = android.os.Build.VERSION.SDK_INT
                                if (r.minSdk > sdk) Note(Sym.ErrorOutline, "Needs Android ${ApkInsights.androidName(r.minSdk)}", "This phone runs Android ${ApkInsights.androidName(sdk)}, so Android will refuse this APK.", cs.errorContainer, cs.onErrorContainer)
                                if (!r.runsHere) Note(Sym.ErrorOutline, "Not built for this phone", "It only carries ${r.abis.joinToString()} code; this phone needs ${android.os.Build.SUPPORTED_ABIS.first()}.", cs.errorContainer, cs.onErrorContainer)
                            }
                            val r = route(s)
                            if (optsOn && optR != null) Note(Sym.Tune, "Beyond install", "Installs through ${when (optR) { "root" -> "root"; "shizuku" -> "Shizuku"; else -> "wireless debugging" }} with the switches below.", cs.secondaryContainer, cs.onSecondaryContainer)
                            else if (r != null) Note(Sym.Bolt, "Fast install", r.second, cs.secondaryContainer, cs.onSecondaryContainer)
                            else if (WirelessAdb.supported) Note(Sym.Schedule, "Skip the 24-hour wait", "Android may hold apps from unverified developers for a day. Turn on Skip the 24-hour wait in Orion's settings to install them right away.",
                                cs.surfaceContainerHigh, cs.onSurface) { TextButton({ startActivity(Intent(this@ApkInstallActivity, OrionActivity::class.java).setData("orionstore://settings/installer".toUri())); finish() }) { Text("Set up") } }
                            InsightsCard(st.apk, ins)
                            if (optR != null) {
                                Surface({ optsOn = !optsOn }, Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(20.dp), color = if (optsOn) cs.secondaryContainer else cs.surfaceContainerHigh) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Sym.Tune, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Beyond install", style = MaterialTheme.typography.titleSmall)
                                            Text(if (need.isNotEmpty() && !optsOn) "This APK needs ${need.joinToString { id -> INSTALL_OPTIONS.first { it.id == id }.label.lowercase() }}" else "Downgrade, grant permissions, Play Store install and more",
                                                style = MaterialTheme.typography.bodySmall, color = if (need.isNotEmpty() && !optsOn) cs.tertiary else cs.onSurfaceVariant)
                                        }
                                        Switch(optsOn, { optsOn = it })
                                    }
                                }
                                androidx.compose.animation.AnimatedVisibility(optsOn) {
                                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                        InstallOptionsList(opts, optInstaller, { opts = it }, { optInstaller = it }, need) {}
                                    }
                                }
                            }
                        }
                        FilledTonalButton({ step = ApkStep.Scan(st.apk, com.orion.store.platform.VtScan(tracker, "apk:${st.apk.pkg}", st.apk.label, st.apk.pkg, s.virusTotalApiKey)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shapes = ButtonDefaults.shapes()) {
                            Icon(Sym.Security, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Scan with VirusTotal")
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text("Cancel") }
                            Button({ install(st.apk, s) }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
                                Icon(Sym.InstallMobile, null); Spacer(Modifier.width(8.dp))
                                Text(when { st.apk.installed == null -> "Install"; st.apk.installedCode != null && st.apk.code > st.apk.installedCode -> "Update"; else -> "Reinstall" }, maxLines = 1)
                            }
                        }
                    }
                    is ApkStep.Inspect -> {
                        Header(st.apk)
                        insights?.let { r -> Column(Modifier.weight(1f, fill = false).padding(top = 8.dp).verticalScroll(rememberScrollState())) { InspectPage(r) } }
                        TextButton({ step = ApkStep.Ready(st.apk) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Back to install") }
                    }
                    // Your own VirusTotal key, saved with the store's: checks the exact file before it goes in
                    is ApkStep.Scan -> {
                        Header(st.apk)
                        Box(Modifier.padding(top = 16.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                            VtPanel(st.vt, com.orion.store.platform.ScanTarget(com.orion.store.platform.ScanTargetType.DOWNLOADED_APK, st.apk.label, st.apk.file.name), s.virusTotalApiKey,
                                save = { k -> Store.settings.update { it.copy(virusTotalApiKey = k) } }, revoke = { Store.settings.update { it.copy(virusTotalApiKey = "") }; st.vt.reset(); st.vt.view = com.orion.store.platform.VtView.KEY },
                                open = { tracker.open(it) }, onDone = { step = ApkStep.Ready(st.apk) })
                        }
                        if (st.vt.view != com.orion.store.platform.VtView.SCANNING) TextButton({ step = ApkStep.Ready(st.apk) }, Modifier.fillMaxWidth()) { Text("Back to install") }
                    }
                    is ApkStep.Installing -> {
                        Header(st.apk)
                        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(40.dp)); Spacer(Modifier.width(12.dp)); Text("Installing...", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    is ApkStep.Done -> {
                        Header(st.apk)
                        Note(Sym.CheckCircle, "Installed", "${st.apk.label} is ready. If it's in the Orion catalog, Orion keeps it updated from now on.", cs.primaryContainer, cs.onPrimaryContainer)
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text("Done") }
                            packageManager.getLaunchIntentForPackage(st.apk.pkg)?.let { li ->
                                Button({ runCatching { startActivity(li) }; finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
                                    Icon(Sym.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open")
                                }
                            }
                        }
                    }
                    is ApkStep.Failed -> {
                        st.apk?.let { Header(it) }
                        Note(Sym.ErrorOutline, st.title, st.body, cs.errorContainer, cs.onErrorContainer)
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text("Close") }
                            st.apk?.let { a -> Button({ install(a, s, regular = route(s) != null) }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
                                Text(if (route(s) != null) "Use Android's installer" else "Try again", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } }
                        }
                    }
                }
            }
        }
    }

    /** The at-a-glance read of what is inside: one chip per finding, warnings tinted, and the full page one tap away. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun InsightsCard(a: Apk, r: ApkInsights.Report?) {
        val cs = MaterialTheme.colorScheme
        if (r == null && !inspecting) return
        Surface({ if (r != null) step = ApkStep.Inspect(a) }, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = r != null, shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.Science, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                    Text("Inside this APK", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (r == null) LoadingIndicator(Modifier.size(24.dp)) else Icon(Sym.ChevronRight, "See everything", tint = cs.onSurfaceVariant)
                }
                if (r == null) { Text("Reading its code, libraries and permissions...", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant); return@Column }
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.frameworks.forEach { Chip(Sym.Layers, it) }
                    Chip(Sym.Android, "Target Android ${ApkInsights.androidName(r.targetSdk)}")
                    Chip(Sym.Memory, if (r.abis.isEmpty()) "No native code" else r.abis.joinToString(" · ") { abiShort(it) }, warn = !r.runsHere)
                    r.pageReady?.let { Chip(if (it) Sym.CheckCircle else Sym.WarningAmber, if (it) "16 KB ready" else "Not 16 KB ready", warn = !it) }
                    Chip(Sym.Radar, when (r.trackers.size) { 0 -> "No trackers found"; 1 -> "1 tracker"; else -> "${r.trackers.size} trackers" }, warn = r.trackers.isNotEmpty())
                    Chip(Sym.Policy, "${r.perms.size} permissions" + r.newPerms.size.takeIf { it > 0 }?.let { " · $it new" }.orEmpty(), warn = r.newPerms.any { it.dangerous })
                    if (r.debuggable || r.debugSigned) Chip(Sym.BugReport, "Debug build", warn = true)
                }
            }
        }
    }

    @Composable
    private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, warn: Boolean = false) {
        val cs = MaterialTheme.colorScheme
        Surface(shape = Pill, color = if (warn) cs.tertiaryContainer else cs.surfaceContainerHighest, contentColor = if (warn) cs.onTertiaryContainer else cs.onSurface) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }

    private fun abiShort(abi: String) = when (abi) { "arm64-v8a" -> "arm64"; "armeabi-v7a" -> "arm32"; "armeabi" -> "arm (old)"; else -> abi }

    /** Everything Orion read from the APK, section by section. */
    @Composable
    private fun InspectPage(r: ApkInsights.Report) {
        val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography
        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
        val sdk = android.os.Build.VERSION.SDK_INT; val phone = android.os.Build.SUPPORTED_ABIS.toSet()
        var allPerms by remember { mutableStateOf(false) }; var allLibs by remember { mutableStateOf(false) }
        @Composable fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
            Text(title, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp), style = ty.labelLarge, color = cs.primary)
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
        }
        @Composable fun Line(label: String, value: String, warn: Boolean = false, sub: String? = null) = Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(label, style = ty.bodyMedium)
                sub?.let { Text(it, style = ty.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.width(12.dp))
            Text(value, style = ty.labelLarge, color = if (warn) cs.tertiary else cs.onSurfaceVariant, maxLines = 2)
        }

        Section("Built with") {
            if (r.frameworks.isEmpty()) Text("Plain Android: Java or Kotlin with Android's own views.", style = ty.bodyMedium)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { r.frameworks.forEach { Chip(Sym.Layers, it) } }
            Text("${r.dexCount} dex file${if (r.dexCount == 1) "" else "s"} · ${"%,d".format(r.classCount)} classes", style = ty.bodySmall, color = cs.onSurfaceVariant)
        }
        Section("Compatibility") {
            Line("Minimum", "Android ${ApkInsights.androidName(r.minSdk)} (API ${r.minSdk})", warn = r.minSdk > sdk, sub = if (r.minSdk > sdk) "This phone runs Android ${ApkInsights.androidName(sdk)}" else null)
            Line("Target", "Android ${ApkInsights.androidName(r.targetSdk)} (API ${r.targetSdk})", warn = sdk >= 34 && r.targetSdk < 24,
                sub = if (sdk >= 34 && r.targetSdk < 24) "Android blocks apps this old. Beyond install has Bypass target SDK block" else null)
            if (r.compileSdk > 0) Line("Compiled with", "API ${r.compileSdk}")
            Line("Processor", if (r.abis.isEmpty()) "Any" else r.abis.joinToString { abiShort(it) }, warn = !r.runsHere,
                sub = if (r.abis.isEmpty()) "No native code, so it runs on every phone" else if (r.runsHere) "Runs on this phone (${phone.filter { it in r.abis }.joinToString { abiShort(it) }})" else "Won't run on this phone")
            r.pageReady?.let { ok ->
                Line("16 KB pages", if (ok) "Ready" else "${r.unaligned.size} not ready", warn = !ok,
                    sub = if (ok) "Every 64-bit library works on Android 15+ phones with 16 KB memory pages" else "Phones with 16 KB pages run it in compatibility mode or refuse it: ${r.unaligned.joinToString { it.name }.take(160)}")
            }
            if (r.debuggable || r.testOnly) Line("Build", listOfNotNull("Debuggable".takeIf { r.debuggable }, "Test only".takeIf { r.testOnly }).joinToString(" · "), warn = true,
                sub = if (r.testOnly) "Needs Allow test builds in Beyond install" else null)
        }
        Section("Trackers") {
            if (r.trackers.isEmpty()) Text("None of the known tracker SDKs were found in its code.", style = ty.bodyMedium)
            else r.trackers.forEach { (n, c) -> Line(n, c, warn = c == "Ads" || c == "Attribution") }
            Text("Checked offline against tracker signatures, the way Exodus Privacy does.", style = ty.bodySmall, color = cs.onSurfaceVariant)
        }
        Section("Permissions · ${r.perms.size}") {
            if (r.perms.isEmpty()) Text("Asks for nothing.", style = ty.bodyMedium)
            (if (allPerms) r.perms else r.perms.take(8)).forEach { p ->
                Line(ApkInsights.permLabel(p.name), listOfNotNull("New".takeIf { p.isNew }, "Sensitive".takeIf { p.dangerous }).joinToString(" · "), warn = p.dangerous, sub = p.name)
            }
            if (r.perms.size > 8) TextButton({ allPerms = !allPerms }) { Text(if (allPerms) "Show fewer" else "Show all ${r.perms.size}") }
        }
        Section("Components") {
            r.components.forEach { c -> Line(c.kind, "${c.total}" + if (c.exported > 0) " · ${c.exported} open to other apps" else "") }
        }
        if (r.libs.isNotEmpty()) Section("Native libraries · ${r.libs.size}") {
            (if (allLibs) r.libs else r.libs.take(8)).forEach { l ->
                val bad = l.abi in ApkInsights.ABI64 && l.align != null && l.align < ApkInsights.PAGE_16K
                Line(l.name, "${abiShort(l.abi)} · ${CatalogProcessor.formatSize(l.size)}", warn = bad, sub = listOfNotNull(l.known, "Not 16 KB aligned".takeIf { bad }).joinToString(" · ").ifEmpty { null })
            }
            if (r.libs.size > 8) TextButton({ allLibs = !allLibs }) { Text(if (allLibs) "Show fewer" else "Show all ${r.libs.size}") }
        }
        Section("Size") {
            val total = r.sizes.sumOf { it.second }.coerceAtLeast(1)
            r.sizes.forEach { (g, b) ->
                Column {
                    Line(g, CatalogProcessor.formatSize(b))
                    LinearProgressIndicator({ b.toFloat() / total }, Modifier.fillMaxWidth().padding(top = 4.dp), drawStopIndicator = {})
                }
            }
        }
        r.signer?.let { sig ->
            Section("Signature") {
                Line("SHA-256", if (r.debugSigned) "Debug key" else "", warn = r.debugSigned, sub = if (r.debugSigned) "Signed with Android's public debug key, so anyone can sign an update for it" else null)
                Text(sig, style = ty.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = cs.onSurfaceVariant)
                TextButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(sig)) }) { Icon(Sym.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copy") }
            }
        }
    }

    @Composable
    private fun Header(a: Apk) {
        val cs = MaterialTheme.colorScheme
        Row(verticalAlignment = Alignment.CenterVertically) {
            a.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))) } ?: Blob(Sym.Android, cs.primary, 64)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(a.label, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(a.version.ifBlank { null }?.let { "Version $it" }, a.installed?.let { "installed $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${CatalogProcessor.formatSize(a.file.length())} · ${if (a.installed == null) "New app" else if (a.installedCode != null && a.code < a.installedCode) "Older than installed" else "Update"}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, action: (@Composable () -> Unit)? = null) =
        Surface(Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(20.dp), color = bg, contentColor = fg) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = fg.copy(.85f))
                    action?.invoke()
                }
            }
        }
}
