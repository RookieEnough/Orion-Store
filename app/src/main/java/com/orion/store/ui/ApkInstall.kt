package com.orion.store.ui

import com.orion.store.Lang
import com.orion.store.R

import android.content.Context
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
import androidx.compose.ui.res.stringResource
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
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Lang.wrap(newBase))
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
                ?: ApkStep.Failed(null, getString(R.string.apkinstall_file_cant_be_installed), getString(R.string.apkinstall_not_working_apk))
            // Wireless debugging connects while the sheet is read, so Install doesn't sit on discovery
            if (step is ApkStep.Ready && (optionRoute(Store.settings.flow.value) ?: route(Store.settings.flow.value)?.first) == "adb") WirelessAdb.warm(applicationContext)
            (step as? ApkStep.Ready)?.apk?.let { a ->
                inspecting = true
                insights = withContext(Dispatchers.IO) { ApkInsights.read(applicationContext, com.orion.store.platform.Bundles.parts(applicationContext, a.file).firstOrNull() ?: a.file) }
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
        val name = runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull()
        val f = Downloads.file("Ext_${System.currentTimeMillis()}.${fileExt(name ?: uri.toString())}") ?: return null
        contentResolver.openInputStream(uri)?.use { inp -> f.outputStream().use { inp.copyTo(it) } } ?: return null
        val base = com.orion.store.platform.Bundles.parts(applicationContext, f).firstOrNull() ?: run { f.delete(); return null }
        val pm = packageManager
        val pi = pm.getPackageArchiveInfo(base.absolutePath, 0) ?: run { f.delete(); return null }
        val ai = pi.applicationInfo?.apply { sourceDir = base.absolutePath; publicSourceDir = base.absolutePath }
        val label = ai?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() }?.takeIf { it.isNotBlank() && it != pi.packageName } ?: name?.substringBeforeLast('.') ?: pi.packageName
        val inst = tracker.appInfo(pi.packageName).takeIf { it.installed }
        return Apk(f, label, pi.packageName, pi.versionName.orEmpty(), androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi),
            ai?.let { runCatching { it.loadIcon(pm).toBitmap(144, 144) }.getOrNull() }, inst?.version, inst?.versionCode, tracker.clash(f)?.second)
    }

    /** The same order the store uses for its own installs. */
    private fun route(s: Settings): Pair<String, String>? = when {
        s.rootMode -> "root" to getString(R.string.apkinstall_route_root)
        s.useShizuku -> "shizuku" to getString(R.string.apkinstall_route_shizuku)
        s.useDhizuku -> "dhizuku" to getString(R.string.apkinstall_route_dhizuku)
        s.directInstall && WirelessAdb.supported -> "adb" to getString(R.string.apkinstall_route_adb)
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

    private fun install(apk: Apk, s: Settings, regular: Boolean = false) = lifecycleScope.launch {
        val withOpts = !regular && optsOn && optionRoute(s) != null
        val picked = if (withOpts) pickedOptions(opts, optInstaller).also { p -> Store.settings.update { it.copy(installFlags = p) } } else null
        val r = if (regular) null else if (withOpts) optionRoute(s) else route(s)?.first
        if (r == null && !tracker.canRequestInstalls()) {
            step = ApkStep.Failed(apk, getString(R.string.apkinstall_allow_orion_install), getString(R.string.apkinstall_allow_from_source))
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
        // The system installer reports cancel and failure alike; what is on the phone afterwards is the truth
        val now = withContext(Dispatchers.IO) { tracker.appInfo(apk.pkg) }
        val ok = err == null || (r == null && now.installed && now.versionCode == apk.code && now.versionCode != apk.installedCode)
        if (ok) { apk.file.delete(); step = ApkStep.Done(apk); return@launch }
        val msg = err?.message.orEmpty()
        if (r == null && "INSTALL_CANCELED" in msg) { step = ApkStep.Ready(apk); return@launch }
        // A connection problem is not an install failure: say what wireless debugging needs instead of a generic error
        if (r == "adb" && "INSTALL_" !in msg) { step = ApkStep.Failed(apk, getString(R.string.apkinstall_wireless_didnt_answer), getString(R.string.apkinstall_wireless_failed_body, msg.ifBlank { getString(R.string.apkinstall_wireless_unreachable) })); return@launch }
        val (t, b) = installFailureText(this@ApkInstallActivity, msg, apk.label)
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
                            st.apk.clash?.let { c -> val (t, b) = clashText(this@ApkInstallActivity, c, st.apk.label); Note(Sym.GppMaybe, t, b, cs.errorContainer, cs.onErrorContainer) }
                            // Refusals Android would only give after the install starts, said before it
                            ins?.let { r ->
                                val sdk = android.os.Build.VERSION.SDK_INT
                                if (r.minSdk > sdk) Note(Sym.ErrorOutline, stringResource(R.string.apkinstall_needs_android, ApkInsights.androidName(r.minSdk)), stringResource(R.string.apkinstall_phone_runs_android_refuse, ApkInsights.androidName(sdk)), cs.errorContainer, cs.onErrorContainer)
                                if (!r.runsHere) Note(Sym.ErrorOutline, stringResource(R.string.apkinstall_not_built_for_phone), stringResource(R.string.apkinstall_only_carries_code, r.abis.joinToString(), android.os.Build.SUPPORTED_ABIS.first()), cs.errorContainer, cs.onErrorContainer)
                            }
                            val r = route(s)
                            if (optsOn && optR != null) Note(Sym.Tune, stringResource(R.string.guide_beyond_install), stringResource(R.string.apkinstall_beyond_body, when (optR) { "root" -> "root"; "shizuku" -> "Shizuku"; else -> stringResource(R.string.apkinstall_route_name_wireless) }), cs.secondaryContainer, cs.onSecondaryContainer)
                            else if (r != null) Note(Sym.Bolt, stringResource(R.string.apkinstall_fast_install), r.second, cs.secondaryContainer, cs.onSecondaryContainer)
                            else if (WirelessAdb.supported) Note(Sym.Schedule, stringResource(R.string.apkinstall_skip_24h_wait), stringResource(R.string.apkinstall_skip_24h_body),
                                cs.surfaceContainerHigh, cs.onSurface) { TextButton({ startActivity(Intent(this@ApkInstallActivity, OrionActivity::class.java).setData("orionstore://settings/installer".toUri())); finish() }) { Text(stringResource(R.string.apkinstall_set_up)) } }
                            InsightsCard(st.apk, ins)
                            if (optR != null) {
                                Surface({ optsOn = !optsOn }, Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(20.dp), color = if (optsOn) cs.secondaryContainer else cs.surfaceContainerHigh) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Sym.Tune, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(stringResource(R.string.guide_beyond_install), style = MaterialTheme.typography.titleSmall)
                                            Text(if (need.isNotEmpty() && !optsOn) stringResource(R.string.apkinstall_needs_switches, need.joinToString { id -> INSTALL_OPTIONS.first { it.id == id }.label.lowercase() }) else stringResource(R.string.apkinstall_beyond_desc),
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
                            Icon(Sym.Security, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.detail_scan_with_virustotal))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_cancel)) }
                            Button({ install(st.apk, s) }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
                                Icon(Sym.InstallMobile, null); Spacer(Modifier.width(8.dp))
                                Text(when { st.apk.installed == null -> stringResource(R.string.apkinstall_install); st.apk.installedCode != null && st.apk.code > st.apk.installedCode -> stringResource(R.string.apkinstall_update); else -> stringResource(R.string.apkinstall_reinstall) }, maxLines = 1)
                            }
                        }
                    }
                    is ApkStep.Inspect -> {
                        Header(st.apk)
                        insights?.let { r -> Column(Modifier.weight(1f, fill = false).padding(top = 8.dp).verticalScroll(rememberScrollState())) { InspectPage(r) } }
                        TextButton({ step = ApkStep.Ready(st.apk) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.apkinstall_back_to_install)) }
                    }
                    // Your own VirusTotal key, saved with the store's: checks the exact file before it goes in
                    is ApkStep.Scan -> {
                        Header(st.apk)
                        Box(Modifier.padding(top = 16.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                            VtPanel(st.vt, com.orion.store.platform.ScanTarget(com.orion.store.platform.ScanTargetType.DOWNLOADED_APK, st.apk.label, st.apk.file.name), s.virusTotalApiKey,
                                save = { k -> Store.settings.update { it.copy(virusTotalApiKey = k) } }, revoke = { Store.settings.update { it.copy(virusTotalApiKey = "") }; st.vt.reset(); st.vt.view = com.orion.store.platform.VtView.KEY },
                                open = { tracker.open(it) }, onDone = { step = ApkStep.Ready(st.apk) })
                        }
                        if (st.vt.view != com.orion.store.platform.VtView.SCANNING) TextButton({ step = ApkStep.Ready(st.apk) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.apkinstall_back_to_install)) }
                    }
                    is ApkStep.Installing -> {
                        Header(st.apk)
                        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(40.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.apkinstall_installing), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    is ApkStep.Done -> {
                        Header(st.apk)
                        Note(Sym.CheckCircle, stringResource(R.string.apkinstall_installed), stringResource(R.string.apkinstall_installed_body, st.apk.label), cs.primaryContainer, cs.onPrimaryContainer)
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.xtra_done)) }
                            packageManager.getLaunchIntentForPackage(st.apk.pkg)?.let { li ->
                                Button({ runCatching { startActivity(li) }; finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
                                    Icon(Sym.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.myapps_open))
                                }
                            }
                        }
                    }
                    is ApkStep.Failed -> {
                        st.apk?.let { Header(it) }
                        Note(Sym.ErrorOutline, st.title, st.body, cs.errorContainer, cs.onErrorContainer)
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ finish() }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.xtra_close)) }
                            st.apk?.let { a -> Button({ install(a, s, regular = route(s) != null) }, modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
                                Text(if (route(s) != null) stringResource(R.string.apkinstall_use_android_installer) else stringResource(R.string.apkinstall_try_again), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Text(stringResource(R.string.guide_inside_this_apk), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (r == null) LoadingIndicator(Modifier.size(24.dp)) else Icon(Sym.ChevronRight, stringResource(R.string.apkinstall_see_everything), tint = cs.onSurfaceVariant)
                }
                if (r == null) { Text(stringResource(R.string.apkinstall_reading_code), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant); return@Column }
                InsightChips(r, Modifier.padding(top = 10.dp))
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
                Text(listOfNotNull(if (a.version.isBlank()) null else stringResource(R.string.apkinstall_version, a.version), if (a.installed == null) null else stringResource(R.string.apkinstall_installed_version, a.installed)).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${CatalogProcessor.formatSize(a.file.length())} · ${if (a.installed == null) stringResource(R.string.apkinstall_new_app) else if (a.installedCode != null && a.code < a.installedCode) stringResource(R.string.apkinstall_older_than_installed) else stringResource(R.string.apkinstall_update)}",
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

@Composable
fun InsightChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, warn: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = Pill, color = if (warn) cs.tertiaryContainer else cs.surfaceContainerHighest, contentColor = if (warn) cs.onTertiaryContainer else cs.onSurface) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

fun abiShort(abi: String) = when (abi) { "arm64-v8a" -> "arm64"; "armeabi-v7a" -> "arm32"; "armeabi" -> "arm (old)"; else -> abi }

/** Everything Orion read from the APK, section by section. */
@Composable
fun InspectPage(r: ApkInsights.Report) {
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

    Section(stringResource(R.string.apkinstall_built_with)) {
        if (r.frameworks.isEmpty()) Text(stringResource(R.string.apkinstall_plain_android), style = ty.bodyMedium)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { r.frameworks.forEach { InsightChip(Sym.Layers, it) } }
        Text(stringResource(R.string.apkinstall_dex_classes, r.dexCount, if (r.dexCount == 1) "" else "s", "%,d".format(r.classCount)), style = ty.bodySmall, color = cs.onSurfaceVariant)
    }
    Section(stringResource(R.string.apkinstall_compatibility)) {
        Line(stringResource(R.string.apkinstall_minimum), stringResource(R.string.apkinstall_android_api, ApkInsights.androidName(r.minSdk), r.minSdk), warn = r.minSdk > sdk, sub = if (r.minSdk > sdk) stringResource(R.string.apkinstall_phone_runs_android, ApkInsights.androidName(sdk)) else null)
        Line(stringResource(R.string.apkinstall_target), stringResource(R.string.apkinstall_android_api, ApkInsights.androidName(r.targetSdk), r.targetSdk), warn = sdk >= 34 && r.targetSdk < 24,
            sub = if (sdk >= 34 && r.targetSdk < 24) stringResource(R.string.apkinstall_target_block_sub) else null)
        if (r.compileSdk > 0) Line(stringResource(R.string.apkinstall_compiled_with), stringResource(R.string.apkinstall_api, r.compileSdk))
        Line(stringResource(R.string.apkinstall_processor), if (r.abis.isEmpty()) stringResource(R.string.apkinstall_any) else r.abis.joinToString { abiShort(it) }, warn = !r.runsHere,
            sub = if (r.abis.isEmpty()) stringResource(R.string.apkinstall_no_native_runs_everywhere) else if (r.runsHere) stringResource(R.string.apkinstall_runs_on_this_phone, phone.filter { it in r.abis }.joinToString { abiShort(it) }) else stringResource(R.string.apkinstall_wont_run))
        r.pageReady?.let { ok ->
            Line(stringResource(R.string.apkinstall_16kb_pages), if (ok) stringResource(R.string.apkinstall_ready) else stringResource(R.string.apkinstall_not_ready_count, r.unaligned.size), warn = !ok,
                sub = if (ok) stringResource(R.string.apkinstall_16kb_ready_sub) else stringResource(R.string.apkinstall_16kb_not_ready_sub, r.unaligned.joinToString { it.name }.take(160)))
        }
        if (r.debuggable || r.testOnly) Line(stringResource(R.string.apkinstall_build), listOfNotNull(if (r.debuggable) stringResource(R.string.apkinstall_debuggable) else null, if (r.testOnly) stringResource(R.string.apkinstall_test_only) else null).joinToString(" · "), warn = true,
            sub = if (r.testOnly) stringResource(R.string.apkinstall_needs_test_builds) else null)
    }
    Section(stringResource(R.string.apkinstall_trackers)) {
        if (r.trackers.isEmpty()) Text(stringResource(R.string.apkinstall_no_trackers_code), style = ty.bodyMedium)
        else r.trackers.forEach { (n, c) -> Line(n, c, warn = c == "Ads" || c == "Attribution") }
        Text(stringResource(R.string.apkinstall_trackers_offline_note), style = ty.bodySmall, color = cs.onSurfaceVariant)
    }
    Section(stringResource(R.string.apkinstall_permissions_count, r.perms.size)) {
        if (r.perms.isEmpty()) Text(stringResource(R.string.apkinstall_asks_for_nothing), style = ty.bodyMedium)
        (if (allPerms) r.perms else r.perms.take(8)).forEach { p ->
            Line(ApkInsights.permLabel(p.name), listOfNotNull(if (p.isNew) stringResource(R.string.apkinstall_new) else null, if (p.dangerous) stringResource(R.string.apkinstall_sensitive) else null).joinToString(" · "), warn = p.dangerous, sub = p.name)
        }
        if (r.perms.size > 8) TextButton({ allPerms = !allPerms }) { Text(if (allPerms) stringResource(R.string.apkinstall_show_fewer) else stringResource(R.string.apkinstall_show_all_count, r.perms.size)) }
    }
    Section(stringResource(R.string.apkinstall_components)) {
        r.components.forEach { c -> Line(c.kind, "${c.total}" + if (c.exported > 0) stringResource(R.string.apkinstall_exported_suffix, c.exported) else "") }
    }
    if (r.libs.isNotEmpty()) Section(stringResource(R.string.apkinstall_native_libraries_count, r.libs.size)) {
        (if (allLibs) r.libs else r.libs.take(8)).forEach { l ->
            val bad = l.abi in ApkInsights.ABI64 && l.align != null && l.align < ApkInsights.PAGE_16K
            Line(l.name, "${abiShort(l.abi)} · ${CatalogProcessor.formatSize(l.size)}", warn = bad, sub = listOfNotNull(l.known, if (bad) stringResource(R.string.apkinstall_not_16kb_aligned) else null).joinToString(" · ").ifEmpty { null })
        }
        if (r.libs.size > 8) TextButton({ allLibs = !allLibs }) { Text(if (allLibs) stringResource(R.string.apkinstall_show_fewer) else stringResource(R.string.apkinstall_show_all_count, r.libs.size)) }
    }
    Section(stringResource(R.string.apkinstall_size)) {
        val total = r.sizes.sumOf { it.second }.coerceAtLeast(1)
        r.sizes.forEach { (g, b) ->
            Column {
                Line(g, CatalogProcessor.formatSize(b))
                LinearProgressIndicator({ b.toFloat() / total }, Modifier.fillMaxWidth().padding(top = 4.dp), drawStopIndicator = {})
            }
        }
    }
    r.signer?.let { sig ->
        Section(stringResource(R.string.guide_signature)) {
            Line("SHA-256", if (r.debugSigned) stringResource(R.string.apkinstall_debug_key) else "", warn = r.debugSigned, sub = if (r.debugSigned) stringResource(R.string.apkinstall_debug_key_sub) else null)
            Text(sig, style = ty.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = cs.onSurfaceVariant)
            TextButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(sig)) }) { Icon(Sym.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.common_copy)) }
        }
    }
}

/** One chip per finding, warnings tinted: the at-a-glance read used by the installer sheet and the app page. */
@Composable
fun InsightChips(r: ApkInsights.Report, modifier: Modifier = Modifier) =
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        r.frameworks.forEach { InsightChip(Sym.Layers, it) }
        InsightChip(Sym.Android, stringResource(R.string.apkinstall_target_android, ApkInsights.androidName(r.targetSdk)))
        InsightChip(Sym.Memory, if (r.abis.isEmpty()) stringResource(R.string.apkinstall_no_native_code) else r.abis.joinToString(" · ") { abiShort(it) }, warn = !r.runsHere)
        r.pageReady?.let { InsightChip(if (it) Sym.CheckCircle else Sym.WarningAmber, if (it) stringResource(R.string.apkinstall_16kb_ready) else stringResource(R.string.apkinstall_not_16kb_ready), warn = !it) }
        InsightChip(Sym.Radar, when (r.trackers.size) { 0 -> stringResource(R.string.apkinstall_no_trackers_found); 1 -> stringResource(R.string.apkinstall_one_tracker); else -> stringResource(R.string.apkinstall_trackers_count, r.trackers.size) }, warn = r.trackers.isNotEmpty())
        InsightChip(Sym.Policy, stringResource(R.string.apkinstall_permissions_chip, r.perms.size) + r.newPerms.size.takeIf { it > 0 }?.let { stringResource(R.string.apkinstall_new_suffix, it) }.orEmpty(), warn = r.newPerms.any { it.dangerous })
        if (r.debuggable || r.debugSigned) InsightChip(Sym.BugReport, stringResource(R.string.apkinstall_debug_build), warn = true)
    }
