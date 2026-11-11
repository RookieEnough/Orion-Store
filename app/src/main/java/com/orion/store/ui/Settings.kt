package com.orion.store.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.orion.store.Lang
import com.orion.store.R
import com.orion.store.findActivity
import com.orion.store.data.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.orion.store.R
import com.orion.store.platform.DhizukuBridge
import com.orion.store.platform.Installer
import com.orion.store.platform.OrionAi
import com.orion.store.platform.WirelessAdb
import com.orion.store.platform.ResultHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.CacheControl
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

val Pink = Color(0xFFEC4899); val Orange = Color(0xFFF97316); val Purple = Color(0xFFA855F7)
val Green = Color(0xFF22C55E); val Yellow = Color(0xFFEAB308); val SkyC = Color(0xFF38BDF8)
val Fuchsia = Color(0xFFD946EF); val Teal = Color(0xFF14B8A6); val Cyan = Color(0xFF06B6D4)


// region identity (.osf) port of handleExportIdentity / handleImportIdentity
private const val ID_SALT = "ORION_PROTOCOL_OMEGA_8842_SECURE_HASH_V1"
private fun jsNum(f: Float) = if (f % 1f == 0f) JsonPrimitive(f.toLong()) else JsonPrimitive(f.toString().toDouble())

fun identityExport(s: Settings, favorites: List<String>, now: Long): String {
    val data = buildJsonObject {
        put("adWatchCount", s.adWatchCount); put("submissionCount", s.submissionCount); put("changeRequestCount", s.changeRequestCount)
        put("lastSubmissionTime", s.lastSubmissionTime); put("lastLeaderboardSubmissionTime", s.lastLeaderboardSubmissionTime)
        put("isLegend", s.isLegend); put("isContributor", s.isContributor); put("isDevUnlocked", s.isDevUnlocked)
        put("theme", s.theme); put("palette", s.palette); put("appFont", s.appFont); put("customFonts", json.encodeToJsonElement(s.customFonts)); put("customPalettes", json.encodeToJsonElement(s.customPalettes)); put("storeLayout", s.storeLayout)
        put("isOled", s.isOled); put("hiddenTabs", json.encodeToJsonElement(s.hiddenTabs)); put("disableAnimations", s.disableAnimations)
        put("compactMode", s.compactMode); put("showAppAbout", s.showAppAbout); put("bottomNavScale", jsNum(s.bottomNavScale)); put("highRefreshRate", s.highRefreshRate); put("immersive", s.immersive); put("perfMode", s.perfMode)
        put("pullToRefreshCharacter", s.pullToRefreshCharacter); put("hapticEnabled", s.hapticEnabled); put("glassEffect", s.glassEffect)
        put("quickTiles", s.quickTiles?.let { json.encodeToJsonElement(it) } ?: JsonNull); put("settingsOrder", json.encodeToJsonElement(s.settingsOrder))
        put("autoUpdateEnabled", s.autoUpdateEnabled); put("wifiOnly", s.wifiOnly); put("stableOnly", s.stableOnly); put("deleteApk", s.deleteApk); put("useShizuku", s.useShizuku)
        put("installerPreference", s.installerPreference); put("installerPackage", s.installerPackage); put("installerLabel", s.installerLabel)
        put("useRemoteJson", s.useRemoteJson); put("loadLocalData", s.loadLocalData); put("appStreams", json.encodeToJsonElement(s.appStreams))
        put("ignoredUpdates", json.encodeToJsonElement(s.ignoredUpdates)); put("appManage", json.encodeToJsonElement(s.appManage)); put("skipOtherStores", s.skipOtherStores)
        put("myAppsFilters", json.encodeToJsonElement(s.myAppsFilters)); put("userProfile", s.userProfile?.let { json.encodeToJsonElement(it) } ?: JsonNull)
        put("customBundles", json.encodeToJsonElement(s.customBundles)); put("removedBundleApps", json.encodeToJsonElement(s.removedBundleApps))
        put("extraBundleApps", json.encodeToJsonElement(s.extraBundleApps)); put("favorites", json.encodeToJsonElement(favorites)); put("timestamp", now)
    }
    val pkg = buildJsonObject { put("data", data); put("sig", sha256Hex((data.toString() + ID_SALT).toByteArray())); put("ver", "1.1") }
    return Base64.encodeToString(pkg.toString().toByteArray(), Base64.NO_WRAP)
}

class IdentityError(@StringRes val resId: Int) : Exception()

private inline fun <reified T> JsonObject.or(k: String, fb: T): T =
    get(k)?.takeIf { it !is JsonNull }?.let { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() } ?: fb

/** Returns merged settings plus favorites (null = keep current). Throws IdentityError with the user-facing message resource. */
fun identityImport(raw: String, c: Settings): Pair<Settings, List<String>?> {
    val text = runCatching { String(Base64.decode(raw.trim(), Base64.DEFAULT)) }.getOrNull()?.takeIf { it.trimStart().startsWith("{") } ?: raw
    val d: JsonObject; val sig: String
    try {
        val pkg = json.parseToJsonElement(text).jsonObject
        d = pkg["data"] as? JsonObject ?: throw IllegalStateException("Invalid Save File")
        sig = (pkg["sig"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: throw IllegalStateException("Invalid Save File")
    } catch (_: Exception) { throw IdentityError(R.string.settings_corrupt_or_incompatible_save_file) }
    if (sha256Hex((d.toString() + ID_SALT).toByteArray()) != sig) throw IdentityError(R.string.settings_tampering_detected_save_integrity_check_failed)
    val merged = try { mergeIdentity(d, c) } catch (_: Exception) { throw IdentityError(R.string.settings_corrupt_or_incompatible_save_file) }
    // The signing salt ships in the APK, so a valid signature alone doesn't prove the numbers are real
    if (!Xp.plausible(merged, System.currentTimeMillis())) throw IdentityError(R.string.settings_this_save_has_more_progress_than)
    // Tier badges follow the level: drop the claimed ones and the level watcher grants back whatever the restored XP really earns
    val fair = merged.copy(unlockedBadges = merged.unlockedBadges.filter { b -> TIER_BADGES.none { it.second == b } })
    return fair to (d["favorites"] as? JsonArray)?.let { d.or<List<String>?>("favorites", null) }
}

/** The identity fields, read leniently one by one: shared by backup import and the web carry-over fallback. */
fun mergeIdentity(d: JsonObject, c: Settings): Settings =
    c.copy(
        adWatchCount = d.or("adWatchCount", c.adWatchCount), submissionCount = d.or("submissionCount", c.submissionCount),
        changeRequestCount = d.or("changeRequestCount", c.changeRequestCount), lastSubmissionTime = d.or("lastSubmissionTime", c.lastSubmissionTime),
        lastLeaderboardSubmissionTime = d.or("lastLeaderboardSubmissionTime", c.lastLeaderboardSubmissionTime),
        isLegend = d.or("isLegend", c.isLegend), isContributor = d.or("isContributor", c.isContributor), isDevUnlocked = d.or("isDevUnlocked", c.isDevUnlocked),
        theme = d.or("theme", c.theme), palette = d.or("palette", c.palette), appFont = d.or("appFont", c.appFont), customFonts = d.or("customFonts", c.customFonts), customPalettes = d.or("customPalettes", c.customPalettes),
        storeLayout = d.or("storeLayout", c.storeLayout), isOled = d.or("isOled", c.isOled), hiddenTabs = d.or("hiddenTabs", c.hiddenTabs),
        disableAnimations = d.or("disableAnimations", c.disableAnimations), compactMode = d.or("compactMode", c.compactMode), showAppAbout = d.or("showAppAbout", c.showAppAbout),
        bottomNavScale = d.or("bottomNavScale", c.bottomNavScale).takeIf { it.isFinite() } ?: c.bottomNavScale,
        highRefreshRate = d.or("highRefreshRate", c.highRefreshRate), immersive = d.or("immersive", c.immersive), perfMode = d.or("perfMode", c.perfMode), pullToRefreshCharacter = d.or("pullToRefreshCharacter", c.pullToRefreshCharacter),
        hapticEnabled = d.or("hapticEnabled", c.hapticEnabled), glassEffect = d.or("glassEffect", c.glassEffect),
        quickTiles = d.or("quickTiles", c.quickTiles), settingsOrder = d.or("settingsOrder", c.settingsOrder),
        autoUpdateEnabled = d.or("autoUpdateEnabled", c.autoUpdateEnabled), wifiOnly = d.or("wifiOnly", c.wifiOnly), stableOnly = d.or("stableOnly", c.stableOnly), deleteApk = d.or("deleteApk", c.deleteApk),
        useShizuku = d.or("useShizuku", c.useShizuku), installerPreference = d.or("installerPreference", c.installerPreference),
        installerPackage = d.or("installerPackage", c.installerPackage), installerLabel = d.or("installerLabel", c.installerLabel),
        useRemoteJson = d.or("useRemoteJson", c.useRemoteJson), loadLocalData = d.or("loadLocalData", c.loadLocalData),
        appStreams = d.or("appStreams", c.appStreams), ignoredUpdates = d.or("ignoredUpdates", c.ignoredUpdates),
        appManage = d.or("appManage", c.appManage), skipOtherStores = d.or("skipOtherStores", c.skipOtherStores), myAppsFilters = d.or("myAppsFilters", c.myAppsFilters), userProfile = d.or("userProfile", c.userProfile),
        customBundles = d.or("customBundles", c.customBundles), removedBundleApps = d.or("removedBundleApps", c.removedBundleApps),
        extraBundleApps = d.or("extraBundleApps", c.extraBundleApps),
    )

/** First-run carry-over from the web build's zustand settings store, whose fields share Settings' names. A whole-object decode keeps
 *  everything (badges, tokens, tracking); if one field's type clashed, fall back to the identity fields alone. */
fun webSettingsImport(raw: String, c: Settings): Settings {
    val st = runCatching { json.parseToJsonElement(raw).jsonObject["state"]?.jsonObject }.getOrNull() ?: return c
    return runCatching { json.decodeFromJsonElement<Settings>(st) }.getOrElse { mergeIdentity(st, c) }
}
// endregion

// region catalog diagnostic, port of utils/catalogScanner.ts
data class MissionResult(val appId: String, val appName: String, val issues: List<String>)
private val headClient by lazy { Net.client.newBuilder().callTimeout(5, TimeUnit.SECONDS).build() }
private suspend fun alive(url: String) = url.startsWith("http") && withContext(Dispatchers.IO) {
    runCatching { headClient.newCall(Request.Builder().url(url).head().cacheControl(CacheControl.FORCE_NETWORK).build()).execute().use { it.code in 200..399 } }.getOrDefault(false)
}
private suspend fun scanApp(a: AppItem, ctx: android.content.Context): MissionResult? {
    val i = mutableListOf<String>()
    if (a.author.isBlank() || a.author.equals("unknown", true)) i += ctx.getString(R.string.settings_scan_missing_developer_name)
    if (a.description.trim().length < 20) i += ctx.getString(R.string.settings_scan_description_missing_or_too_short)
    if (a.screenshots.isEmpty()) i += ctx.getString(R.string.settings_scan_no_screenshots_provided)
    if (a.icon.isNotEmpty()) { if (!alive(a.icon)) i += ctx.getString(R.string.settings_scan_dead_icon_url, a.icon) } else i += ctx.getString(R.string.settings_scan_missing_icon_url)
    if (a.downloadUrl.isNotEmpty()) { if (!alive(a.downloadUrl)) i += ctx.getString(R.string.settings_scan_dead_download_url, a.downloadUrl) } else i += ctx.getString(R.string.settings_scan_missing_download_url)
    a.screenshots.firstOrNull()?.let { if (!alive(it)) i += ctx.getString(R.string.settings_scan_dead_screenshot_url, it) }
    return if (i.isEmpty()) null else MissionResult(a.id, a.name, i)
}
suspend fun runCatalogDiagnostic(apps: List<AppItem>, ctx: android.content.Context, onProgress: (Int, Int) -> Unit): List<MissionResult> {
    val out = mutableListOf<MissionResult>(); var n = 0
    apps.chunked(10).forEach { b -> out += coroutineScope { b.map { async { scanApp(it, ctx) } }.awaitAll() }.filterNotNull(); n += b.size; onProgress(n, apps.size) }
    return out
}
// endregion

private suspend fun pickDocument(types: Array<String>): Uri? {
    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, types)
    return ResultHost.run(i).let { (code, data) -> data?.data?.takeIf { code == android.app.Activity.RESULT_OK } }
}

// region building blocks
/** Accent of the open settings page; rows and headers tint from it. */
private val LocalAccent = compositionLocalOf { Color(0xFF6366F1) }

private data class Cat(val id: String, val icon: ImageVector, val color: Color, @StringRes val title: Int, @StringRes val desc: Int, val shape: RoundedPolygon)
private val CATS = listOf(
    Cat("queue", Sym.Downloading, Blue, R.string.settings_downloads, R.string.settings_queue_installs_and_pending_updates, MaterialShapes.Cookie9Sided),
    Cat("visuals", Sym.Palette, Purple, R.string.settings_look_feel, R.string.settings_theme_font_motion_and_density, MaterialShapes.Flower),
    Cat("network", Sym.Wifi, SkyC, R.string.settings_network, R.string.settings_wi_fi_rules_and_auto_update, MaterialShapes.Clover4Leaf),
    Cat("storage", Sym.CleaningServices, Orange, R.string.settings_storage, R.string.settings_janitor_and_saved_apks, MaterialShapes.Sunny),
    Cat("installer", Sym.Shield, Green, R.string.settings_orion_xtra, R.string.settings_guardian_sentinel_and_installer, MaterialShapes.Gem),
    Cat("identity", Sym.Backup, Pink, R.string.settings_backup, R.string.settings_save_or_restore_your_identity, MaterialShapes.Cookie6Sided),
    Cat("interface", Sym.ViewQuilt, Teal, R.string.settings_interface, R.string.settings_store_layout_and_visible_tabs, MaterialShapes.Pentagon),
    Cat("developer", Sym.Terminal, Yellow, R.string.settings_developer, R.string.settings_debug_switches_and_tools, MaterialShapes.SoftBurst),
)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) = Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(title, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 10.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = tonal(LocalAccent.current).second)
    Column(Modifier.clip(RoundedCornerShape(28.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@Composable
private fun SRow(icon: ImageVector, title: String, desc: String? = null, enabled: Boolean = true, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) =
    ListItem(
        { Text(title, style = MaterialTheme.typography.titleMedium) },
        Modifier.clip(RoundedCornerShape(4.dp)).then(if (onClick != null) Modifier.clickable(enabled, onClick = onClick) else Modifier),
        supportingContent = desc?.let { { Text(it) } }, leadingContent = { TonalIcon(icon, LocalAccent.current) }, trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )

@Composable
private fun Toggle(icon: ImageVector, title: String, desc: String, checked: Boolean, onChange: () -> Unit) {
    val vm = LocalVm.current
    SRow(icon, title, desc, onClick = { vm.haptic(H.SELECTION); onChange() }) {
        Switch(checked, { vm.haptic(H.SELECTION); onChange() },
            thumbContent = { Icon(if (checked) Sym.Check else Sym.Close, null, Modifier.size(SwitchDefaults.IconSize)) })
    }
}

@Composable
private fun Chevron(meta: String? = null) = Row(verticalAlignment = Alignment.CenterVertically) {
    meta?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = tonal(LocalAccent.current).second, fontWeight = FontWeight.Bold) }
    Icon(Sym.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InfoCard(text: String, title: String? = null, icon: ImageVector = Sym.Info, action: (@Composable () -> Unit)? = null) {
    val (bg, fg) = tonal(LocalAccent.current)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(bg).padding(16.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = fg); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg) }
            Text(text, style = MaterialTheme.typography.bodySmall, color = fg.copy(.85f))
            action?.invoke()
        }
    }
}

@Composable
private fun Selectable(selected: Boolean, title: String, meta: String? = null, onClick: () -> Unit) {
    val r by animateDpAsState(if (selected) 28.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "sel")
    Surface(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(r),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
            if (selected) Icon(Sym.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) else Icon(Sym.KeyboardArrowRight, null)
        }
    }
}

/** Decorative MaterialShape that slowly turns; used behind hub tiles and page heroes. */
@Composable
private fun Spinner(shape: RoundedPolygon, color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, periodMs: Int = 24_000) {
    val still = LocalS.current.calm
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(0f, if (still) 0f else 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "a")
    Box(modifier.size(size).graphicsLayer { rotationZ = turn }.background(color, shape.toShape()))
}

/** Quick Settings tile: rests as a rounded rectangle, turns into a filled pill when on. */
@Composable
private fun QuickTile(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, state: String? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme; val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val r by animateDpAsState(if (on) 36.dp else 20.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "r")
    val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHigh, fx, label = "bg")
    val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurface, fx, label = "fg")
    val bounce = remember { Animatable(1f) }; val sp = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(on) { bounce.snapTo(.6f); bounce.animateTo(1f, sp) }
    Row(modifier.height(76.dp).springClick(RoundedCornerShape(r), onClick = onClick).background(bg).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }, tint = fg)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state ?: stringResource(if (on) R.string.common_on else R.string.common_off), style = MaterialTheme.typography.labelMedium, color = fg.copy(.75f), maxLines = 1)
        }
    }
}
// endregion

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = LocalS.current; val d = LocalD.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val customFontFallback = stringResource(R.string.settings_custom_font)
    val menu = vm.settingsMenu
    var modal by remember { mutableStateOf("none") }
    var picker by remember { mutableStateOf<String?>(null) }
    var installerStep by remember { mutableStateOf("mode") }
    var shizukuError by remember { mutableStateOf<String?>(null) }
    var importStatus by remember { mutableStateOf("" to "neutral") }
    var fontError by remember { mutableStateOf<String?>(null) }; var importingFont by remember { mutableStateOf(false) }
    var installers by remember { mutableStateOf<List<Installer>>(emptyList()) }
    var installersLoading by remember { mutableStateOf(false) }; var installersError by remember { mutableStateOf<String?>(null) }
    val all = vm.allApps()
    val active = d.activeDownloads.size; val ready = d.readyToInstall.size
    val pending = vm.updates.count { it.id !in d.activeDownloads && it.id !in d.readyToInstall }
    fun back() {
        vm.settingsMenu = "none"; importStatus = "" to "neutral"; shizukuError = null; picker = null; installerStep = "mode"
    }
    BackHandler { if (vm.settingsMenu != "none") back() else vm.close(Overlay.SETTINGS) }
    LaunchedEffect(menu) {
        if (menu != "installer") return@LaunchedEffect
        installersLoading = true; installersError = null
        runCatching { withContext(Dispatchers.IO) { vm.tracker.installers() } }.onSuccess { installers = it }.onFailure { installersError = ctx.getString(R.string.settings_failed_to_load_installers) }
        installersLoading = false
    }
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val saved = rememberSaveableStateHolder()
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(menu, transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "menu") { m ->
            saved.SaveableStateProvider(m) {
            val avs = this@AnimatedContent
            val cat = CATS.firstOrNull { it.id == m }
            if (cat == null) Hub(vm, s, d, active, ready, pending) { c ->
                Modifier.sharedBounds(rememberSharedContentState("cat-${c.id}"), avs, resizeMode = PAGE_RESIZE)
            } else Page(cat, Modifier.sharedBounds(rememberSharedContentState("cat-${cat.id}"), avs, resizeMode = PAGE_RESIZE),
                onBack = ::back) {
                when (m) {
                    "identity" -> {
                        Section(stringResource(R.string.settings_your_data_stays_here)) {
                            InfoCard(stringResource(R.string.settings_save_your_progress_to_a_file), stringResource(R.string.settings_orion_is_serverless), Sym.CloudOff)
                        }
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val saving = remember { mutableStateOf(BackupPhase.IDLE) }
                            Button({
                                scope.backupRun(saving, vm) {
                                    runCatching { vm.tracker.saveFile(identityExport(vm.s, vm.d.favorites, System.currentTimeMillis()), "orion_identity_${System.currentTimeMillis()}.osf") }
                                        .onSuccess { importStatus = ctx.getString(R.string.settings_identity_saved_successfully) to "success" }
                                        .onFailure { importStatus = ctx.getString(R.string.settings_export_failed, it.message) to "error" }.isSuccess
                                }
                            }, modifier = Modifier.weight(1f).heightIn(min = 64.dp), shapes = ButtonDefaults.shapes()) {
                                BackupGlyph(saving.value, 32.dp, MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp))
                                AnimatedContent(saving.value, transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) }, label = "save") { p ->
                                    Column {
                                        Text(when (p) { BackupPhase.WORKING -> stringResource(R.string.settings_saving); BackupPhase.DONE -> stringResource(R.string.settings_saved); BackupPhase.FAILED -> stringResource(R.string.settings_failed); else -> stringResource(R.string.settings_back_up) }, fontWeight = FontWeight.Bold)
                                        Text(if (p == BackupPhase.DONE) stringResource(R.string.settings_check_your_files) else stringResource(R.string.settings_signed_osf_file), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            FilledTonalButton({
                                scope.launch {
                                    val uri = pickDocument(arrayOf("*/*")) ?: return@launch
                                    try {
                                        val raw = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                                        val (merged, fav) = identityImport(raw, vm.s)
                                        vm.set { merged }; fav?.let { f -> vm.upd { it.copy(favorites = f) } }
                                        importStatus = ctx.getString(R.string.settings_identity_restored_successfully) to "success"; vm.haptic(H.SUCCESS)
                                        delay(1500); vm.reload()
                                    } catch (e: IdentityError) { importStatus = ctx.getString(e.resId) to "error"; vm.haptic(H.ERROR) }
                                    catch (_: Exception) { importStatus = ctx.getString(R.string.settings_corrupt_or_incompatible_save_file) to "error"; vm.haptic(H.ERROR) }
                                }
                            }, modifier = Modifier.weight(1f).heightIn(min = 64.dp), shapes = ButtonDefaults.shapes()) {
                                Icon(Sym.FileDownload, null); Spacer(Modifier.width(8.dp)); Column { Text(stringResource(R.string.xtra_restore), fontWeight = FontWeight.Bold); Text(stringResource(R.string.settings_import_a_save), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        AnimatedVisibility(importStatus.first.isNotEmpty()) {
                            val c = when (importStatus.second) { "success" -> Safe; "error" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (importStatus.second == "error") Sym.ErrorOutline else Sym.CheckCircle, null, tint = c); Spacer(Modifier.width(8.dp))
                                Text(importStatus.first, fontWeight = FontWeight.Bold, color = c)
                            }
                        }
                        CloudBackup(vm, s)
                    }
                    "network" -> Section(stringResource(R.string.settings_downloads)) {
                        Toggle(Sym.Wifi, stringResource(R.string.guide_wi_fi_only), stringResource(R.string.settings_skip_downloads_on_cellular_data), s.wifiOnly) { vm.set { it.copy(wifiOnly = !it.wifiOnly) } }
                        Toggle(Sym.Autorenew, stringResource(R.string.settings_auto_update_apps), stringResource(R.string.settings_check_and_download_updates_every_few), s.autoUpdateEnabled) { vm.set { it.copy(autoUpdateEnabled = !it.autoUpdateEnabled) } }
                        if (s.autoUpdateEnabled) {
                            Toggle(Sym.SystemUpdate, stringResource(R.string.guide_install_automatically), stringResource(R.string.settings_install_downloaded_updates_over_the_current), s.autoInstall) {
                                vm.set { it.copy(autoInstall = !it.autoInstall) }
                            }
                            if (s.autoInstall) InfoCard(when {
                                s.rootMode -> stringResource(R.string.settings_root_mode_is_on_every_update)
                                s.useShizuku -> stringResource(R.string.settings_silent_installs_run_through_shizuku_as)
                                android.os.Build.VERSION.SDK_INT >= 31 -> stringResource(R.string.settings_apps_orion_installed_update_silently_apps)
                                else -> stringResource(R.string.settings_this_android_version_asks_before_every)
                            })
                        }
                        Toggle(Sym.Verified, stringResource(R.string.guide_stable_releases_only), stringResource(R.string.settings_skip_beta_alpha_and_nightly_builds), s.stableOnly) { vm.set { it.copy(stableOnly = !it.stableOnly) } }
                        Toggle(Sym.FilterList, stringResource(R.string.new_skip_apps_from_other_stores), stringResource(R.string.new_apps_installed_by_play_store_or_another), s.skipOtherStores) {
                            vm.set { it.copy(skipOtherStores = !it.skipOtherStores) }
                        }
                    }
                    "installer" -> {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeatureTile(Sym.Shield, stringResource(R.string.settings_guardian), stringResource(R.string.settings_permissions_apk_export_debloat), Green, MaterialShapes.Cookie9Sided, Modifier.weight(1f)) { modal = "guardian" }
                            FeatureTile(Sym.MonitorHeart, stringResource(R.string.settings_sentinel), stringResource(R.string.settings_malware_scan_for_installed_apps), Blue, MaterialShapes.Sunny, Modifier.weight(1f)) { modal = "sentinel" }
                        }
                        val ch = ptr(s.pullToRefreshCharacter)
                        Section(stringResource(R.string.pixel_pull_to_refresh)) {
                            SRow(Sym.AutoFixHigh, stringResource(R.string.settings_refresh_character), stringResource(ch.name), onClick = { picker = "ptr" }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PtrBadge(ch, 48.dp)
                                    Chevron()
                                }
                            }
                        }
                        Section(stringResource(R.string.settings_install_behavior)) {
                            SRow(Sym.Android, stringResource(R.string.settings_apk_installer), when (s.installerPreference) {
                                "package" -> s.installerLabel.ifEmpty { stringResource(R.string.settings_specific_installer) }; "chooser" -> stringResource(R.string.settings_ask_every_time); else -> stringResource(R.string.settings_system_installer)
                            }, onClick = { installerStep = "mode"; picker = "installer" }) { Chevron() }
                            // Orion is also an installer: any APK from a file manager or browser opens on Orion's sheet
                            SRow(Sym.InstallMobile, stringResource(R.string.settings_install_an_apk_file), stringResource(R.string.settings_pick_any_apk_on_this_phone_and_install_it_with_orion), onClick = {
                                vm.haptic(H.LIGHT); vm.pickApk()
                            }) { Chevron() }
                            if (WirelessAdb.supported) Toggle(Sym.Speed, stringResource(R.string.guide_skip_the_24_hour_wait), stringResource(R.string.settings_install_instantly_through_built_in_wireless), s.directInstall) {
                                if (vm.s.directInstall) vm.set { it.copy(directInstall = false) } else { picker = "direct" }
                            }
                            Toggle(Sym.Bolt, stringResource(R.string.settings_silent_install), stringResource(R.string.settings_install_apps_in_the_background), s.useShizuku) {
                                shizukuError = null
                                if (!vm.s.useShizuku) scope.launch {
                                    try { vm.tracker.requestElevated(); vm.set { it.copy(useShizuku = !it.useShizuku) } }
                                    catch (e: Exception) { shizukuError = if ((e.message ?: "").contains("Shizuku is NOT running")) ctx.getString(R.string.settings_shizuku_service_is_not_running) else ctx.getString(R.string.settings_permission_was_denied_by_user) }
                                } else vm.set { it.copy(useShizuku = !it.useShizuku) }
                            }
                            if (android.os.Build.VERSION.SDK_INT >= 26) Toggle(Sym.AdminPanelSettings, "Dhizuku", stringResource(R.string.settings_dhizuku_desc), s.useDhizuku) {
                                shizukuError = null
                                if (vm.s.useDhizuku) vm.set { it.copy(useDhizuku = false) }
                                else scope.launch {
                                    runCatching { DhizukuBridge.request(ctx) }.onSuccess { ok -> if (ok) { vm.set { it.copy(useDhizuku = true) }; vm.haptic(H.SUCCESS) } else shizukuError = ctx.getString(R.string.settings_dhizuku_permission_denied) }
                                        .onFailure { shizukuError = it.message ?: ctx.getString(R.string.settings_dhizuku_not_available) }
                                }
                            }
                            Toggle(Sym.Tune, stringResource(R.string.settings_beyond_install), stringResource(R.string.settings_beyond_install_desc), s.installOptions) {
                                shizukuError = null
                                if (!vm.s.installOptions && !vm.s.rootMode) scope.launch {
                                    try { vm.tracker.requestElevated(); vm.set { it.copy(installOptions = true) } }
                                    catch (e: Exception) { shizukuError = if ((e.message ?: "").contains("Shizuku is NOT running")) ctx.getString(R.string.settings_shizuku_service_is_not_running) else ctx.getString(R.string.settings_permission_was_denied_by_user) }
                                } else vm.set { it.copy(installOptions = !it.installOptions) }
                            }
                            shizukuError?.let { Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold) }
                            InfoCard(stringResource(R.string.settings_silent_installs_need_shizuku_running_or_dhizuku_activated_as_device_owner)) {
                                Row {
                                    TextButton({ vm.navigateFromSettings("shizuku") }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.settings_get_shizuku)) }
                                    Spacer(Modifier.width(16.dp))
                                    TextButton({ vm.tracker.open(DhizukuBridge.URL) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.settings_get_dhizuku)) }
                                }
                            }
                        }
                        Section(stringResource(R.string.settings_root)) {
                            var adopt by remember { mutableStateOf<String?>(null) }
                            ConfirmDialog(adopt, stringResource(R.string.settings_adopt), onConfirm = vm::adoptApps, onDismiss = { adopt = null })
                            Toggle(Sym.Terminal, stringResource(R.string.guide_root_mode), stringResource(R.string.settings_silent_installs_and_every_guardian_tool), s.rootMode) {
                                if (vm.s.rootMode) vm.set { it.copy(rootMode = false) }
                                else scope.launch { if (vm.tracker.requestRoot()) { vm.set { it.copy(rootMode = true) }; vm.haptic(H.SUCCESS) } else vm.error(ctx.getString(R.string.settings_root_access_was_denied_or_this)) }
                            }
                            if (s.rootMode) {
                                if (android.os.Build.VERSION.SDK_INT >= 34) Toggle(Sym.Lock, stringResource(R.string.guide_update_lock), stringResource(R.string.settings_other_stores_must_ask_you_before), s.updateLock) { vm.set { it.copy(updateLock = !it.updateLock) } }
                                SRow(Sym.Sync, stringResource(R.string.guide_adopt_installed_apps), stringResource(R.string.settings_make_orion_the_updater_of_catalog), onClick = {
                                    adopt = ctx.getString(R.string.settings_orion_reinstalls_the_same_apk_of)
                                }) { Chevron() }
                                SRow(Sym.DeleteSweep, stringResource(R.string.guide_clear_every_app_s_cache), stringResource(R.string.settings_free_space_across_all_apps_in), onClick = vm::trimCaches) { Chevron() }
                            }
                        }
                        Section(stringResource(R.string.guide_sideloading_protection)) {
                            val vs = vm.verifierState
                            SRow(if (vs == com.orion.store.platform.Verifier.State.ACTIVE) Sym.GppBad else Sym.GppGood, stringResource(R.string.settings_developer_verification), when (vs) {
                                com.orion.store.platform.Verifier.State.ACTIVE -> stringResource(R.string.settings_verifier_on_turn_it_off)
                                com.orion.store.platform.Verifier.State.OFF -> stringResource(R.string.settings_verifier_off_sideloading_is_protected)
                                else -> stringResource(R.string.settings_verifier_not_on_this_phone)
                            }, onClick = { vm.haptic(H.LIGHT); vm.openVerifier() }) { Chevron() }
                            Toggle(Sym.Shield, stringResource(R.string.settings_protection_shield), stringResource(R.string.settings_protection_shield_desc), s.verifierBadge) { vm.set { it.copy(verifierBadge = !it.verifierBadge) } }
                        }
                        Section(stringResource(R.string.settings_recovery)) {
                            SRow(Sym.Sync, stringResource(R.string.settings_force_rescan_packages), stringResource(R.string.settings_reload_every_installed_app_list), onClick = { vm.reload(); vm.scheduleInstalledSync(); vm.close(Overlay.SETTINGS) })
                        }
                    }
                    "storage" -> {
                        StatHero("${d.pendingCleanup.size}", if (d.pendingCleanup.size == 1) stringResource(R.string.settings_apk_waiting_for_cleanup) else stringResource(R.string.settings_apks_waiting_for_cleanup)) {
                            if (d.pendingCleanup.isNotEmpty()) Button({ vm.batchCleanup(); vm.haptic(H.SUCCESS) }, shapes = ButtonDefaults.shapes()) {
                                Icon(Sym.CleaningServices, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_clean_now))
                            }
                        }
                        Section(stringResource(R.string.guide_janitor)) {
                            Toggle(Sym.CleaningServices, stringResource(R.string.settings_auto_cleanup_installer), stringResource(R.string.settings_remove_apks_after_they_install), s.deleteApk) { vm.set { it.copy(deleteApk = !it.deleteApk) } }
                            InfoCard(stringResource(R.string.settings_janitor_mode_clears_installed_apk_files))
                        }
                    }
                    "visuals" -> {
                        Section(stringResource(R.string.settings_language)) {
                            val lctx = LocalContext.current
                            val langOptions = listOf("" to stringResource(R.string.settings_language_system_default), "en" to "English", "es" to "Español", "zh-CN" to "中文", "ja" to "日本語", "ru" to "Русский", "hi" to "हिन्दी", "ar" to "العربية", "tl" to "Tagalog", "fr" to "Français", "pl" to "Polski")
                            var showLang by remember { mutableStateOf(false) }
                            val currentLang = langOptions.firstOrNull { it.first == Lang.tag(lctx) }?.second ?: langOptions[0].second
                            SRow(Sym.Translate, stringResource(R.string.settings_language), currentLang, onClick = { showLang = true }) { Chevron() }
                            if (showLang) AlertDialog({ showLang = false }, title = { Text(stringResource(R.string.settings_language)) },
                                text = { Column { langOptions.forEach { (tag, label) ->
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { Lang.set(lctx, tag); showLang = false; if (android.os.Build.VERSION.SDK_INT < 33) lctx.findActivity()?.recreate() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(Lang.tag(lctx) == tag, { Lang.set(lctx, tag); showLang = false; if (android.os.Build.VERSION.SDK_INT < 33) lctx.findActivity()?.recreate() })
                                        Spacer(Modifier.width(8.dp)); Text(label, style = MaterialTheme.typography.bodyLarge)
                                    } } } },
                                confirmButton = { TextButton({ showLang = false }) { Text(stringResource(R.string.root_got_it)) } })
                        }
                        ThemePicker(vm, s)
                        PalettePicker(vm, s)
                        Section(stringResource(R.string.settings_color_type)) {
                            Toggle(Sym.AutoAwesome, stringResource(R.string.guide_material_you), stringResource(R.string.settings_dynamic_wallpaper_colors_android_12), s.materialYouEnabled) { vm.set { it.copy(materialYouEnabled = !it.materialYouEnabled) } }
                            SRow(Sym.FontDownload, stringResource(R.string.settings_font), fontLabel(s), onClick = { fontError = null; picker = "font" }) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("Aa", style = MaterialTheme.typography.headlineSmallEmphasized); Chevron() }
                            }
                        }
                        Text(stringResource(R.string.settings_performance), Modifier.padding(start = 32.dp, top = 16.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = tonal(LocalAccent.current).second)
                        Text(if (s.perfMode == "auto") stringResource(R.string.settings_auto_picks_for_this_phone, s.perf.replaceFirstChar { it.uppercase() })
                            else stringResource(R.string.new_lite_stops_decorative_motion_boost_keeps_more), Modifier.padding(horizontal = 32.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // One connected group across the page, the same 16dp margins as the groups below: no scrolling, nothing off to one side
                        val modes = listOf("auto" to stringResource(R.string.settings_perf_auto), "lite" to stringResource(R.string.settings_perf_lite), "balanced" to stringResource(R.string.settings_perf_balanced), "boost" to stringResource(R.string.settings_perf_boost))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            modes.forEachIndexed { i, (id, label) ->
                                ToggleButton(s.perfMode == id, { vm.haptic(H.SELECTION); vm.set { it.copy(perfMode = id) } }, Modifier.weight(if (id == "balanced") 1.3f else 1f),
                                    shapes = when (i) { 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(); modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(); else -> ButtonGroupDefaults.connectedMiddleButtonShapes() },
                                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label, maxLines = 1) }
                            }
                        }
                        Section(stringResource(R.string.settings_motion_feel)) {
                            Toggle(Sym.TouchApp, stringResource(R.string.guide_haptic_feedback), stringResource(R.string.settings_tactile_taps_on_buttons_and_toggles), s.hapticEnabled) { vm.set { it.copy(hapticEnabled = !it.hapticEnabled) } }
                            Toggle(Sym.Speed, stringResource(R.string.guide_smooth_motion), stringResource(R.string.settings_prefer_high_refresh_rate_scrolling), s.highRefreshRate) { vm.set { it.copy(highRefreshRate = !it.highRefreshRate) } }
                            Toggle(Sym.MotionPhotosOff, stringResource(R.string.guide_disable_animations), stringResource(R.string.settings_every_transition_snaps_instantly), s.disableAnimations) { vm.set { it.copy(disableAnimations = !it.disableAnimations) } }
                            Toggle(Sym.BlurOn, stringResource(R.string.guide_glass_dock), stringResource(R.string.settings_slightly_translucent_floating_navigation), s.glassEffect) { vm.set { it.copy(glassEffect = !it.glassEffect) } }
                            Toggle(Sym.OpenInFull, stringResource(R.string.settings_full_screen), stringResource(R.string.settings_hide_the_status_bar_while_orion_is_open), s.immersive) { vm.set { it.copy(immersive = !it.immersive) } }
                            Toggle(Sym.Compress, stringResource(R.string.guide_compact_mode), stringResource(R.string.settings_smaller_rows_with_denser_spacing), s.compactMode) { vm.set { it.copy(compactMode = !it.compactMode) } }
                        }
                        if (!Device.isTv) IconPicker(vm, s)
                        DockPicker(vm, s)
                        DockSize(vm, s)
                        DockPosition(vm, s)
                    }
                    "interface" -> {
                        LayoutPicker(vm, s)
                        Section(stringResource(R.string.settings_navigation_tabs)) {
                            listOf(Triple("android", "Apps", stringResource(R.string.settings_android_apps)), Triple("pc", "PC", stringResource(R.string.settings_pc_and_desktop_software)),
                                Triple("tv", "TV", stringResource(R.string.settings_tv_and_big_screen_apps)), Triple("orbit", "Orbit", stringResource(R.string.settings_apps_you_track_from_their_own_source)), Triple("myapps", stringResource(R.string.root_my_apps), stringResource(R.string.settings_library_and_download_history))).forEach { (t, title, desc) ->
                                Toggle(when (t) { "android" -> Sym.Android; "pc" -> Sym.DesktopWindows; "tv" -> Sym.Tv; "orbit" -> Sym.Orbit; else -> Sym.GridView }, title, desc, t !in s.hiddenTabs) {
                                    vm.set { it.toggleHiddenTab(t) }
                                }
                            }
                        }
                        Section(stringResource(R.string.guide_orion_ai)) {
                            Toggle(Sym.AutoAwesome, stringResource(R.string.settings_ai_star), stringResource(R.string.settings_ai_star_desc), s.aiButton) { vm.set { it.copy(aiButton = !it.aiButton) } }
                            Toggle(Sym.Notifications, stringResource(R.string.settings_ai_sound_effects), stringResource(R.string.settings_ai_sound_effects_desc), s.aiSounds) { vm.set { it.copy(aiSounds = !it.aiSounds) } }
                            SRow(Sym.Key, stringResource(R.string.settings_provider_and_key), OrionAi.provider(s.aiProvider).label + if (vm.ai.ready(s)) stringResource(R.string.settings_connected_suffix) else stringResource(R.string.settings_not_connected_suffix),
                                onClick = { modal = "ai" }) { Chevron() }
                            SRow(Sym.Chat, stringResource(R.string.settings_open_orion_ai), stringResource(R.string.settings_find_apps_by_describing_what_you_need), onClick = { vm.close(Overlay.SETTINGS); vm.open(Overlay.AI) }) { Chevron() }
                        }
                    }
                    "developer" -> {
                        Section(stringResource(R.string.settings_testing_and_debug)) {
                            InfoCard(stringResource(R.string.settings_these_options_are_for_testing_and), icon = Sym.Warning)
                            Toggle(Sym.Storage, stringResource(R.string.settings_enable_built_in_data), stringResource(R.string.settings_load_packaged_data_instead_of_remote), s.loadLocalData) { vm.set { it.copy(loadLocalData = !it.loadLocalData) } }
                            Toggle(Sym.Construction, stringResource(R.string.settings_local_maintenance_mode), stringResource(R.string.settings_work_on_bundles_and_data_locally), s.localMaintenanceMode) {
                                vm.set { it.copy(localMaintenanceMode = !it.localMaintenanceMode) }
                            }
                            SRow(Sym.Shield, stringResource(R.string.settings_test_sideloading_protection), stringResource(R.string.settings_play_the_secure_me_popup_with_a_fake_success), onClick = { vm.haptic(H.LIGHT); vm.testVerifier("done") }) { Chevron() }
                            SRow(Sym.GppMaybe, stringResource(R.string.settings_test_sideloading_protection_failure), stringResource(R.string.settings_the_same_popup_ending_in_a_fake_error), onClick = { vm.haptic(H.LIGHT); vm.testVerifier("failed") }) { Chevron() }
                            SRow(Sym.Radar, stringResource(R.string.settings_run_catalog_scanner_admin), stringResource(R.string.settings_check_every_app_for_missing_text), onClick = { modal = "scanner" }) { Chevron() }
                            SRow(Sym.DeleteSweep, stringResource(R.string.settings_clear_system_cache), stringResource(R.string.settings_reset_pending_cleanup_and_ready_installs), onClick = {
                                vm.upd { it.copy(pendingCleanup = emptyMap(), readyToInstall = emptyMap()) }; vm.haptic(H.WARNING)
                            })
                        }
                        DevConfigEditor(vm, s)
                        DevTools(vm)
                    }
                }
            }
            }
        }
    }
    when (picker) {
        "ptr" -> PtrPickerSheet(s.pullToRefreshCharacter, { c -> vm.set { it.withPtrCharacter(c) }; picker = null }) { picker = null }
        "font" -> FontSheet(vm, s, fontError, importingFont, onImport = {
            scope.launch {
                fontError = null
                val uri = pickDocument(arrayOf("font/ttf", "font/otf", "font/woff2", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")) ?: return@launch
                val (name, size) = ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                    if (cur.moveToFirst()) cur.getString(cur.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) to cur.getLong(cur.getColumnIndexOrThrow(OpenableColumns.SIZE)) else null
                } ?: ("" to 0L)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in setOf("ttf", "otf", "woff2")) { fontError = ctx.getString(R.string.settings_choose_a_ttf_otf_or_woff2); return@launch }
                if (size > 8 * 1024 * 1024) { fontError = ctx.getString(R.string.settings_fonts_must_be_8_mb_or); return@launch }
                importingFont = true
                val id = "font-${System.currentTimeMillis()}-${(1..6).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")}"
                runCatching {
                    withContext(Dispatchers.IO) {
                        val f = File(File(ctx.filesDir, "fonts/custom").apply { mkdirs() }, "$id.$ext")
                        ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }; f
                    }
                }.onSuccess { f ->
                    val font = CustomFont(id, name.substringBeforeLast('.').trim().ifEmpty { customFontFallback }, name, ctx.contentResolver.getType(uri) ?: "font/$ext", f.absolutePath, System.currentTimeMillis())
                    vm.set { it.copy(customFonts = it.customFonts + font, appFont = "custom:$id") }
                }.onFailure { fontError = ctx.getString(R.string.settings_could_not_read_this_font) }
                importingFont = false
            }
        }) { picker = null }
        "installer" -> InstallerSheet(vm, s, installerStep, { installerStep = it }, installers, installersLoading, installersError) { picker = null; installerStep = "mode" }
        "direct" -> DirectInstallSheet(vm) { picker = null }
    }
    when (modal) {
        "guardian" -> GuardianSheet(vm) { modal = "none" }
        "ai" -> AiSettingsSheet(vm) { modal = "none" }
        "sentinel" -> SentinelSheet(vm) { modal = "none" }
        "scanner" -> ScannerSheet(all) { modal = "none" }
    }
}

/** Pages are measured once and scaled through the container transform; remeasuring Look & feel every frame was the jank. */
private val PAGE_RESIZE = SharedTransitionScope.ResizeMode.scaleToBounds(androidx.compose.ui.layout.ContentScale.FillWidth, Alignment.TopCenter)

/** One closing tip per page, so short pages end on something useful instead of empty surface. */
private val TIPS = mapOf(
    "queue" to R.string.settings_downloads_keep_running_in_the_background,
    "visuals" to R.string.settings_the_theme_button_in_the_search,
    "network" to R.string.settings_auto_update_queues_new_versions_whenever,
    "storage" to R.string.settings_the_janitor_clears_leftover_apks_a,
    "installer" to R.string.settings_with_shizuku_installs_and_updates_go,
    "identity" to R.string.settings_your_backup_is_a_single_small,
    "interface" to R.string.settings_hidden_tabs_come_back_from_here,
    "developer" to R.string.settings_close_developer_options_from_the_bottom,
)

@Composable
private fun PageFooter(cat: Cat) {
    val (_, fg) = tonal(cat.color)
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Spinner(cat.shape, fg.copy(.12f), 112.dp, periodMs = 30_000)
            Icon(cat.icon, null, Modifier.size(40.dp), tint = fg.copy(.7f))
        }
        Text(stringResource(TIPS[cat.id] ?: cat.desc), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Settings home: quick toggles on top, then a bento of categories that expand into their pages. No staggered rise here: it fought the slide-in and read as jitter. */
@Composable
private fun Hub(vm: MainViewModel, s: Settings, d: DataState, active: Int, ready: Int, pending: Int, shared: @Composable (Cat) -> Modifier) {
    val cs = MaterialTheme.colorScheme
    fun cat(id: String) = CATS.first { it.id == id }
    Column(Modifier.fillMaxSize().background(cs.surface).readableWidth().verticalScroll(rememberScrollState()).readableWidth().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(bottom = 32.dp)) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Settings", style = MaterialTheme.typography.displaySmallEmphasized)
                Text(stringResource(R.string.settings_orion_store_v_apps_in_catalog, STORE_VERSION, vm.allApps().size), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            // Announcements already read leave the header; the latest one stays readable here until a new one arrives
            val ann = vm.config?.announcement?.takeIf { it.isNotBlank() }
            var showAnn by remember { mutableStateOf(false) }
            if (ann != null) IconButton({ vm.haptic(H.LIGHT); showAnn = true }) { Icon(Sym.Campaign, stringResource(R.string.settings_last_announcement), tint = cs.onSurfaceVariant) }
            if (showAnn && ann != null) AlertDialog({ showAnn = false }, icon = { Icon(Sym.Campaign, null) }, title = { Text(stringResource(R.string.root_from_the_orion_team)) },
                text = { Text(ann, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton({ showAnn = false; vm.dismissAnnouncement() }) { Text(stringResource(R.string.root_got_it)) } })
        }
        var editing by remember { mutableStateOf(false) }
        HubHeader(stringResource(R.string.guide_quick_toggles)) { IconButton({ editing = true }) { Icon(Sym.Edit, stringResource(R.string.settings_edit_quick_toggles), tint = cs.primary) } }
        val quick = (s.quickTiles ?: DEFAULT_QUICK).mapNotNull { id -> QUICK.firstOrNull { it.id == id && it.shown(s) } }
        if (quick.isEmpty()) Text(stringResource(R.string.settings_no_quick_toggles_tap_the_pencil), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        // Three across on big screens, so the tiles keep a tile's shape instead of stretching into long bars
        val per = if (LocalWidthClass.current.wide) 3 else 2
        quick.chunked(per).forEach { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { q -> QuickTile(q.icon, stringResource(q.label), q.get(s), Modifier.weight(1f)) { vm.haptic(H.SELECTION); vm.set(q.flip) } }
                repeat(per - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (editing) QuickTilesSheet(vm, s) { editing = false }

        // Long-press a card and drag: cards trade places live as you pass over them, the displaced one gliding to its new spot.
        // Slots keep their sizes, so the bento never breaks. The gesture belongs to the slot where it started (keyed by slot index),
        // so trading places mid-drag never cancels it.
        fun orderOf(st: Settings) = (st.settingsOrder + HUB_ORDER).distinct().filter { it in HUB_ORDER }
        var live by remember { mutableStateOf<List<String>?>(null) }
        val order = live ?: orderOf(s)
        var dragging by remember { mutableStateOf<String?>(null) }; var pos by remember { mutableStateOf(Offset.Zero) }
        val slots = remember { mutableMapOf<Int, Rect>() }
        val glide = remember { mutableStateMapOf<String, Animatable<Offset, AnimationVector2D>>() }
        val motion = rememberCoroutineScope(); val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Offset>()
        fun center(i: Int) = slots[i]?.center ?: Offset.Zero
        fun slide(id: String, from: Offset) = motion.launch { Animatable(from, Offset.VectorConverter).also { glide[id] = it }.animateTo(Offset.Zero, spring) }
        fun start(slot: Int) { val o = live ?: orderOf(vm.s); live = o; dragging = o[slot]; pos = center(slot); vm.haptic(H.HEAVY) }
        fun move(delta: Offset) {
            val id = dragging ?: return; val o = live ?: return; pos += delta
            val cur = o.indexOf(id); val to = slots.entries.firstOrNull { it.key != cur && it.value.contains(pos) }?.key ?: return
            val other = o[to]
            live = o.toMutableList().also { it[cur] = other; it[to] = id }
            slide(other, center(to) - center(cur)); vm.haptic(H.SELECTION)
        }
        fun end() {
            val id = dragging; val o = live
            if (id != null && o != null) {
                slide(id, pos - center(o.indexOf(id)))
                if (o != orderOf(vm.s)) { vm.set { it.copy(settingsOrder = o) }; vm.haptic(H.SUCCESS) }
            }
            dragging = null
        }
        // The saved order lands a frame after the drop: keep showing the live one until it does, so nothing flickers back
        LaunchedEffect(s.settingsOrder) { if (dragging == null) live = null }
        fun Modifier.movable(slot: Int, id: String) = onGloballyPositioned { slots[slot] = it.boundsInRoot() }
            .zIndex(if (dragging == id) 1f else 0f)
            .graphicsLayer {
                val t = if (dragging == id) pos - center(slot) else glide[id]?.value ?: Offset.Zero
                translationX = t.x; translationY = t.y; val k = if (dragging == id) 1.05f else 1f; scaleX = k; scaleY = k
            }
            .pointerInput(slot) { detectDragGesturesAfterLongPress({ start(slot) }, ::end, ::end) { c, delta -> c.consume(); move(delta) } }
        fun z(vararg ids: Int) = if (ids.any { order[it] == dragging || glide[order[it]]?.isRunning == true }) 1f else 0f
        val queued = active + ready + pending
        @Composable fun Slot(i: Int, modifier: Modifier, compact: Boolean = false) {
            val c = cat(order[i])
            val badge = when (c.id) { "queue" -> queued.takeIf { it > 0 }; "storage" -> d.pendingCleanup.size.takeIf { it > 0 }; else -> null }?.toString()
            Bento(vm, c, shared, modifier.movable(i, c.id), compact, badge) {
                if (!compact) when (c.id) {
                    "queue" -> {
                        Text(if (queued == 0) stringResource(R.string.settings_all_caught_up) else stringResource(R.string.settings_active_ready_updates, active, ready, pending), style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(.8f))
                        val prog = d.activeDownloads.keys.mapNotNull { d.downloadProgress[it] }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
                        if (prog != null) LinearWavyProgressIndicator({ prog / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp), color = LocalContentColor.current)
                    }
                    "visuals" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            listOf(cs.primary, cs.secondary, cs.tertiary).forEach { Box(Modifier.size(28.dp).clip(CircleShape).background(cs.surface).padding(2.dp).clip(CircleShape).background(it)) }
                        }
                        Text(fontLabel(s) + " · " + s.theme.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(.8f), modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        HubHeader(stringResource(R.string.settings_everything_else), stringResource(R.string.settings_long_press_a_card_to_move)) {
            if (s.settingsOrder.isNotEmpty()) TextButton({ vm.set { it.copy(settingsOrder = emptyList()) }; vm.haptic(H.MEDIUM) }) { Text(stringResource(R.string.common_reset)) }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Slot(0, Modifier.zIndex(z(0)).fillMaxWidth().height(156.dp))
            Row(Modifier.zIndex(z(1, 2, 3)).height(268.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot(1, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.zIndex(z(2, 3)).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slot(2, Modifier.fillMaxWidth().weight(1f), compact = true)
                    Slot(3, Modifier.fillMaxWidth().weight(1f), compact = true)
                }
            }
            Slot(4, Modifier.zIndex(z(4)).fillMaxWidth().height(140.dp))
            Row(Modifier.zIndex(z(5, 6)).height(132.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot(5, Modifier.weight(1f).fillMaxHeight(), compact = true)
                Slot(6, Modifier.weight(1f).fillMaxHeight(), compact = true)
            }
            if (s.isDevUnlocked) Bento(vm, cat("developer"), shared, Modifier.fillMaxWidth().height(108.dp))
        }
    }
}

/** Hub section header with an optional trailing action. */
@Composable
private fun HubHeader(title: String, hint: String? = null, action: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 2.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action()
    }

/** Bento slots, top to bottom, in their default order. Developer stays pinned last. */
private val HUB_ORDER = listOf("queue", "visuals", "network", "storage", "installer", "identity", "interface")

/** Every setting a quick tile can flip. Ones that need a grant first (Shizuku, root, wireless pairing) stay on their pages. */
private class Quick(val id: String, val icon: ImageVector, @StringRes val label: Int, val get: (Settings) -> Boolean, val shown: (Settings) -> Boolean = { true }, val flip: (Settings) -> Settings)
private val QUICK = listOf(
    Quick("autoUpdate", Sym.Autorenew, R.string.guide_auto_update, { it.autoUpdateEnabled }) { it.copy(autoUpdateEnabled = !it.autoUpdateEnabled) },
    Quick("wifiOnly", Sym.Wifi, R.string.guide_wi_fi_only, { it.wifiOnly }) { it.copy(wifiOnly = !it.wifiOnly) },
    Quick("haptics", Sym.Vibration, R.string.settings_haptics, { it.hapticEnabled }) { it.copy(hapticEnabled = !it.hapticEnabled) },
    Quick("materialYou", Sym.Wallpaper, R.string.guide_material_you, { it.materialYouEnabled }, { android.os.Build.VERSION.SDK_INT >= 31 }) { it.copy(materialYouEnabled = !it.materialYouEnabled) },
    Quick("smooth", Sym.Speed, R.string.guide_smooth_motion, { it.highRefreshRate }) { it.copy(highRefreshRate = !it.highRefreshRate) },
    Quick("compact", Sym.Compress, R.string.settings_compact, { it.compactMode }) { it.copy(compactMode = !it.compactMode) },
    Quick("stableOnly", Sym.Verified, R.string.settings_stable_only, { it.stableOnly }) { it.copy(stableOnly = !it.stableOnly) },
    Quick("cleanup", Sym.CleaningServices, R.string.settings_auto_cleanup, { it.deleteApk }) { it.copy(deleteApk = !it.deleteApk) },
    Quick("noMotion", Sym.MotionPhotosOff, R.string.settings_no_animations, { it.disableAnimations }) { it.copy(disableAnimations = !it.disableAnimations) },
    Quick("glass", Sym.BlurOn, R.string.guide_glass_dock, { it.glassEffect }) { it.copy(glassEffect = !it.glassEffect) },
    Quick("updateLock", Sym.Lock, R.string.guide_update_lock, { it.updateLock }, { it.rootMode && android.os.Build.VERSION.SDK_INT >= 34 }) { it.copy(updateLock = !it.updateLock) },
    Quick("localData", Sym.Storage, R.string.settings_built_in_data, { it.loadLocalData }, { it.isDevUnlocked }) { it.copy(loadLocalData = !it.loadLocalData) },
    Quick("maintenance", Sym.Construction, R.string.settings_maintenance, { it.localMaintenanceMode }, { it.isDevUnlocked }) { it.copy(localMaintenanceMode = !it.localMaintenanceMode) },
)
private val DEFAULT_QUICK = listOf("autoUpdate", "wifiOnly", "haptics", "materialYou", "smooth", "compact")

/** Your tiles on top: hold and drag to reorder (they trade places live), tap to remove. Everything else below: tap to add. */
@Composable
private fun QuickTilesSheet(vm: MainViewModel, s: Settings, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val cs = MaterialTheme.colorScheme
    fun pickedOf(st: Settings) = (st.quickTiles ?: DEFAULT_QUICK).filter { id -> QUICK.any { it.id == id && it.shown(st) } }
    val picked = pickedOf(s)
    var live by remember { mutableStateOf<List<String>?>(null) }; var dragging by remember { mutableStateOf<String?>(null) }
    var dy by remember { mutableFloatStateOf(0f) }; var step by remember { mutableFloatStateOf(1f) }
    val glide = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    val motion = rememberCoroutineScope(); val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(); val gap = with(LocalDensity.current) { ListItemDefaults.SegmentedGap.toPx() }
    fun slide(id: String, from: Float) = motion.launch { Animatable(from).also { glide[id] = it }.animateTo(0f, spring) }
    fun move(d: Float) {
        val id = dragging ?: return; val o = live ?: return; dy += d
        val i = o.indexOf(id); val j = when { dy > step / 2 && i < o.lastIndex -> i + 1; dy < -step / 2 && i > 0 -> i - 1; else -> return }
        val other = o[j]
        live = o.toMutableList().also { it[i] = other; it[j] = id }; dy -= (j - i) * step; slide(other, (j - i) * step); vm.haptic(H.SELECTION)
    }
    fun end() {
        dragging?.let { slide(it, dy) }; dragging = null; dy = 0f
        live?.takeIf { it != pickedOf(vm.s) }?.let { o -> vm.set { it.copy(quickTiles = o) }; vm.haptic(H.SUCCESS) }
    }
    LaunchedEffect(picked) { if (dragging == null) live = null }
    val tiles = live ?: picked
    fun toggle(id: String) { vm.haptic(H.SELECTION); vm.set { st -> pickedOf(st).let { cur -> st.copy(quickTiles = if (id in cur) cur - id else cur + id) } } }
    SheetTitle(stringResource(R.string.guide_quick_toggles), Sym.Edit, stringResource(R.string.settings_hold_and_drag_to_reorder_tap))
    Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        if (tiles.isNotEmpty()) Text(stringResource(R.string.settings_your_tiles), Modifier.padding(start = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = cs.primary)
        tiles.forEachIndexed { i, id -> key(id) {
            val q = QUICK.first { it.id == id }
            SegmentedListItem({ toggle(id) }, ListItemDefaults.segmentedShapes(i, tiles.size),
                modifier = Modifier.onSizeChanged { step = it.height + gap }.zIndex(if (dragging == id) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging == id) dy else glide[id]?.value ?: 0f; val k = if (dragging == id) 1.03f else 1f; scaleX = k; scaleY = k }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress({ live = live ?: pickedOf(vm.s); dragging = id; dy = 0f; vm.haptic(H.HEAVY) }, ::end, ::end) { c, d -> c.consume(); move(d.y) }
                    },
                colors = ListItemDefaults.segmentedColors(containerColor = if (dragging == id) cs.primaryContainer else cs.secondaryContainer),
                leadingContent = { Icon(q.icon, null) }, trailingContent = { Icon(Sym.DragHandle, stringResource(R.string.settings_hold_to_move)) }) {
                Text(stringResource(q.label), fontWeight = FontWeight.SemiBold)
            }
        } }
        val rest = QUICK.filter { it.shown(s) && it.id !in tiles }
        if (rest.isNotEmpty()) Text(stringResource(R.string.settings_add_tiles), Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = cs.primary)
        rest.forEachIndexed { i, q ->
            SegmentedListItem({ toggle(q.id) }, ListItemDefaults.segmentedShapes(i, rest.size), colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainerHigh),
                leadingContent = { Icon(q.icon, null) }, trailingContent = { Icon(Sym.Add, stringResource(R.string.common_add)) }) {
                Text(stringResource(q.label), fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ vm.set { it.copy(quickTiles = null) }; vm.haptic(H.MEDIUM) }, modifier = Modifier.weight(1f), enabled = s.quickTiles != null, shapes = ButtonDefaults.shapes()) {
            Icon(Sym.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.common_reset))
        }
        Button(LocalSheetClose.current, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Icon(Sym.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.xtra_done)) }
    }
}

@Composable
private fun Bento(vm: MainViewModel, cat: Cat, shared: @Composable (Cat) -> Modifier, modifier: Modifier, compact: Boolean = false, badge: String? = null, extra: @Composable ColumnScope.() -> Unit = {}) {
    val (bg, fg) = tonal(cat.color)
    Box(modifier.then(shared(cat)).springClick(RoundedCornerShape(28.dp)) { vm.haptic(H.LIGHT); if (cat.id == "queue") vm.open(Overlay.DOWNLOADS) else vm.settingsMenu = cat.id }.background(bg)) {
        // Short tiles (the full-width Developer strip) get the small shape, so it hugs the corner like the others instead of filling the strip
        Spinner(cat.shape, fg.copy(.1f), if (compact || cat.id == "developer") 120.dp else 168.dp, Modifier.align(Alignment.BottomEnd).offset(36.dp, 36.dp))
        CompositionLocalProvider(LocalContentColor provides fg) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(fg.copy(.14f), cat.shape.toShape()), contentAlignment = Alignment.Center) { Icon(cat.icon, null, Modifier.size(24.dp)) }
                    Spacer(Modifier.weight(1f))
                    badge?.let { Badge(containerColor = fg, contentColor = bg) { Text(it, Modifier.padding(horizontal = 4.dp)) } }
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(cat.title), style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
                if (!compact) Text(stringResource(cat.desc), style = MaterialTheme.typography.bodySmall, color = fg.copy(.8f), maxLines = 2)
                extra()
            }
        }
    }
}

/** Category page: expands out of its hub tile, opens on a tinted hero with a turning shape. */
@Composable
private fun Page(cat: Cat, modifier: Modifier, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme; val (bg, fg) = tonal(cat.color)
    CompositionLocalProvider(LocalAccent provides cat.color) {
        val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
        // At least a screen tall, so the footer settles at the bottom of short pages (weight works once the scroll column has a min height).
        // The tinted hero runs edge to edge on every screen; only the text and rows keep to a readable column
        Column(modifier.fillMaxSize().background(cs.surface).verticalScroll(rememberScrollState()).heightIn(min = screen).padding(bottom = 40.dp)) {
            Box(Modifier.fillMaxWidth().clipToBounds().background(Brush.verticalGradient(listOf(bg, cs.surface)))) {
                Spinner(cat.shape, fg.copy(.08f), 260.dp, Modifier.align(Alignment.TopEnd).offset(70.dp, (-30).dp), periodMs = 40_000)
                Column(Modifier.readableWidth().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(bottom = 12.dp)) {
                    Box(Modifier.padding(start = 24.dp, top = 24.dp).size(72.dp), contentAlignment = Alignment.Center) {
                        Spinner(cat.shape, fg.copy(.16f), 72.dp, periodMs = 18_000)
                        Icon(cat.icon, null, Modifier.size(34.dp), tint = fg)
                    }
                    Text(stringResource(cat.title), Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.displaySmallEmphasized)
                    Text(stringResource(cat.desc), Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }
            Column(Modifier.readableWidth().reveal(1)) { content() }
            Spacer(Modifier.weight(1f))
            Box(Modifier.readableWidth()) { PageFooter(cat) }
        }
    }
}

/** Auto backup through Android's storage picker: Drive, OneDrive, Proton Drive or any app with a document provider, with no account linking inside Orion. */
@Composable
private fun CloudBackup(vm: MainViewModel, s: Settings) = Section(stringResource(R.string.settings_cloud_backup)) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val pm = ctx.packageManager
    val uri = s.cloudBackupUri.takeIf { it.isNotEmpty() }?.toUri()
    val where = remember(uri) { uri?.authority?.let { a -> runCatching { pm.resolveContentProvider(a, 0)?.loadLabel(pm)?.toString() }.getOrNull() ?: a } }
    val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    fun connect() = scope.launch {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "orion_identity.osf")
        val u = ResultHost.run(i).let { (code, data) -> data?.data?.takeIf { code == android.app.Activity.RESULT_OK } } ?: return@launch
        if (runCatching { ctx.contentResolver.takePersistableUriPermission(u, rw) }.isFailure) { vm.error(ctx.getString(R.string.settings_that_storage_app_does_not_allow)); return@launch }
        vm.set { it.copy(cloudBackupUri = u.toString(), cloudBackupEvery = it.cloudBackupEvery.takeIf { e -> e != "off" } ?: "week") }; vm.cloudBackup(true)
    }
    SRow(Sym.Cloud, if (uri == null) stringResource(R.string.settings_connect_a_cloud) else stringResource(R.string.settings_backing_up_to, where.orEmpty()), if (uri == null) stringResource(R.string.settings_google_drive_onedrive_proton_drive_or) else stringResource(R.string.settings_tap_to_pick_another_file), onClick = ::connect) { Chevron() }
    if (uri != null) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("off" to stringResource(R.string.common_off), "day" to stringResource(R.string.settings_daily), "week" to stringResource(R.string.settings_weekly), "month" to stringResource(R.string.settings_monthly)).forEach { (id, label) -> Choice(s.cloudBackupEvery == id, label) { vm.set { it.copy(cloudBackupEvery = id) } } }
        }
        BackupNowRow(vm, s, where)
        SRow(Sym.CloudOff, stringResource(R.string.settings_disconnect), stringResource(R.string.settings_stop_backing_up_the_file_in), onClick = {
            runCatching { ctx.contentResolver.releasePersistableUriPermission(uri, rw) }; vm.set { it.copy(cloudBackupUri = "", cloudBackupEvery = "off") }
        })
    }
}

private enum class BackupPhase { IDLE, WORKING, DONE, FAILED }

/** Runs [work] as a visible backup: working for at least [minMs] so the motion reads, then done or failed for a beat, then back to idle. */
private fun kotlinx.coroutines.CoroutineScope.backupRun(phase: MutableState<BackupPhase>, vm: MainViewModel, minMs: Long = 900, work: suspend () -> Boolean) {
    if (phase.value != BackupPhase.IDLE) return
    launch {
        val started = System.currentTimeMillis(); phase.value = BackupPhase.WORKING; vm.haptic(H.LIGHT)
        val ok = runCatching { work() }.getOrDefault(false)
        delay((minMs - (System.currentTimeMillis() - started)).coerceAtLeast(0))
        phase.value = if (ok) BackupPhase.DONE else BackupPhase.FAILED; vm.haptic(if (ok) H.SUCCESS else H.ERROR)
        delay(2400); phase.value = BackupPhase.IDLE
    }
}

/** Backup glyph: the arrow waits inside a wavy ring while working, then lands as a check on a turning cookie, or a cross on failure. */
@Composable
private fun BackupGlyph(phase: BackupPhase, size: androidx.compose.ui.unit.Dp = 40.dp, tint: Color = LocalAccent.current) {
    val cs = MaterialTheme.colorScheme
    val (bg0, fg0) = tonal(when (phase) { BackupPhase.DONE -> Safe; BackupPhase.FAILED -> cs.error; else -> tint })
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val bg by animateColorAsState(bg0, fx, label = "bg"); val fg by animateColorAsState(fg0, fx, label = "fg")
    val done by animateFloatAsState(if (phase == BackupPhase.DONE) 1f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "done")
    val morph = remember { androidx.graphics.shapes.Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = done * 90f }.clip(MorphShape(morph, done)).background(bg))
        if (phase == BackupPhase.WORKING) CircularWavyProgressIndicator(Modifier.matchParentSize(), color = fg, trackColor = Color.Transparent)
        val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        AnimatedContent(phase, transitionSpec = {
            (scaleIn(pop, .3f) + fadeIn()) togetherWith (scaleOut(targetScale = .3f) + fadeOut())
        }, label = "glyph") { p ->
            val icon = when (p) { BackupPhase.DONE -> Sym.Check; BackupPhase.FAILED -> Sym.Close; else -> Sym.FileUpload }
            Icon(icon, null, Modifier.size(size * .5f), tint = fg)
        }
    }
}

/** Cloud "Back up now" with feedback: the glyph runs, the caption follows each step, and the row warms green when the copy lands. */
@Composable
private fun BackupNowRow(vm: MainViewModel, s: Settings, where: String?) {
    val scope = rememberCoroutineScope(); val cs = MaterialTheme.colorScheme
    val phase = remember { mutableStateOf(BackupPhase.IDLE) }; val p = phase.value
    val last = if (s.lastCloudBackup == 0L) stringResource(R.string.settings_never_backed_up) else stringResource(R.string.settings_last_backup) + android.text.format.DateUtils.getRelativeTimeSpanString(s.lastCloudBackup)
    val caption = when (p) { BackupPhase.WORKING -> stringResource(R.string.settings_sending_your_identity_to, where ?: stringResource(R.string.settings_your_cloud)); BackupPhase.DONE -> stringResource(R.string.settings_backed_up_just_now); BackupPhase.FAILED -> stringResource(R.string.settings_backup_failed_tap_to_try_again); else -> last }
    val container by animateColorAsState(if (p == BackupPhase.DONE) androidx.compose.ui.graphics.lerp(cs.surfaceContainer, tonal(Safe).first, .6f) else cs.surfaceContainer,
        MaterialTheme.motionScheme.slowEffectsSpec(), label = "row")
    ListItem(
        { Text(stringResource(R.string.settings_back_up_now), style = MaterialTheme.typography.titleMedium) },
        Modifier.clip(RoundedCornerShape(4.dp)).clickable(p == BackupPhase.IDLE) {
            scope.backupRun(phase, vm) { val before = System.currentTimeMillis(); vm.cloudBackup(true).join(); vm.s.lastCloudBackup >= before }
        },
        supportingContent = {
            AnimatedContent(caption, transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) }, label = "cap") { Text(it) }
        },
        leadingContent = { BackupGlyph(p) },
        trailingContent = {
            val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            AnimatedContent(p, transitionSpec = { (scaleIn(pop) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "trail") {
                when (it) {
                    BackupPhase.WORKING -> LoadingIndicator(Modifier.size(32.dp))
                    BackupPhase.DONE -> Text(stringResource(R.string.settings_saved), style = MaterialTheme.typography.labelLargeEmphasized, color = tonal(Safe).second)
                    else -> Chevron()
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = container),
    )
}

@Composable
private fun FeatureTile(icon: ImageVector, title: String, desc: String, c: Color, shape: RoundedPolygon, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg) = tonal(c)
    Column(modifier.height(176.dp).springClick(RoundedCornerShape(28.dp), onClick = onClick).background(bg).padding(18.dp)) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Spinner(shape, fg.copy(.16f), 52.dp, periodMs = 16_000); Icon(icon, null, tint = fg) }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = fg)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = fg.copy(.8f), maxLines = 2)
    }
}

/** The download queue as a compact sheet over whatever is open, like the profile card: active downloads, ready installs and pending
 *  updates. Tapping a row opens that app; the sheet closes so the app page lands on top. */
@Composable
fun DownloadsSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val d = LocalD.current; val cs = MaterialTheme.colorScheme
    val active = d.activeDownloads.size; val ready = d.readyToInstall.size
    val updates = vm.updates.filter { it.id !in d.activeDownloads && it.id !in d.readyToInstall }
    val all = vm.allApps()
    fun app(id: String) = all.firstOrNull { it.id == id }
    fun open(id: String) { vm.close(Overlay.DOWNLOADS); vm.navigateFromSettings(id) }
    CompositionLocalProvider(LocalAccent provides Blue) {
        SheetTitle(stringResource(R.string.settings_downloads), Sym.Downloading, if (active + ready + updates.size == 0) stringResource(R.string.settings_all_caught_up) else stringResource(R.string.settings_active_ready_updates, active, ready, updates.size))
        val silent = s.useShizuku || s.useDhizuku || s.directInstall || s.rootMode
        val rows = ready + active + updates.size
        if (rows == 0) EmptyState(Sym.DownloadDone, stringResource(R.string.settings_queue_is_empty), stringResource(R.string.settings_downloads_and_updates_show_up_here), Modifier.navigationBarsPadding())
        else Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            var i = 0
            val colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainerHigh)
            d.readyToInstall.forEach { (id, file) ->
                val a = app(id)
                SegmentedListItem({ a?.let { open(it.id) } }, ListItemDefaults.segmentedShapes(i++, rows), colors = colors,
                    leadingContent = { a?.let { AppIcon(it, 44.dp) } ?: TonalIcon(Sym.Inventory, Safe) }, supportingContent = { Text(stringResource(R.string.logic_ready_to_install)) },
                    trailingContent = {
                        if (vm.installingId == id) LoadingIndicator(Modifier.size(32.dp))
                        else Button({ a?.let { vm.installFile(it, file) } }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text(stringResource(R.string.common_install)) }
                    }) { Text(a?.name ?: id, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            d.activeDownloads.keys.forEach { id ->
                val a = app(id); val p = d.downloadProgress[id] ?: 0f
                SegmentedListItem({ a?.let { open(it.id) } }, ListItemDefaults.segmentedShapes(i++, rows), colors = colors,
                    leadingContent = { a?.let { AppIcon(it, 44.dp) } ?: TonalIcon(Sym.Downloading, Blue) },
                    supportingContent = {
                        Column {
                            Text(if (p > 0f) stringResource(R.string.common_downloading, p.roundToInt()) else stringResource(R.string.common_starting_download))
                            if (p <= 0f) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp)) else LinearWavyProgressIndicator({ p / 100f }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                    },
                    trailingContent = { IconButton({ vm.cancelDownload(id) }) { Icon(Sym.Close, stringResource(R.string.detail_cancel_download)) } }) {
                    Text(a?.name ?: id, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            updates.forEach { a ->
                SegmentedListItem({ open(a.id) }, ListItemDefaults.segmentedShapes(i++, rows), colors = colors,
                    leadingContent = { AppIcon(a, 44.dp) }, supportingContent = { Text(stringResource(R.string.settings_update_to_v, a.latestVersion.removePrefix("v")), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { FilledTonalButton({ vm.download(a) }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text(stringResource(R.string.common_update)) } }) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (silent && ready > 1) CtaButton(stringResource(R.string.settings_install_all, ready), vm::batchInstall, Modifier.padding(top = 12.dp), Sym.Bolt)
            else if (updates.size > 1) CtaButton(stringResource(R.string.settings_update_all, updates.size), { vm.haptic(H.MEDIUM); updates.filter { it.installsHere() }.forEach { vm.download(it) } }, Modifier.padding(top = 12.dp), Sym.SystemUpdate, tonal = true)
            Text(stringResource(TIPS.getValue("queue")), Modifier.fillMaxWidth().padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** A big number with a caption, for pages whose state is a count. */
@Composable
private fun StatHero(value: String, caption: String, action: @Composable () -> Unit = {}) {
    val (bg, fg) = tonal(LocalAccent.current)
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            AnimatedContent(value, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "n") {
                Text(it, style = MaterialTheme.typography.displayMediumEmphasized, color = fg)
            }
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = fg.copy(.85f))
        }
        action()
    }
}

/** Seed palettes as Android-style split swatches in the current theme, then the user's own and a tile to make one.
 *  Picking one turns Material You off; the toggle below turns it back on. */
@Composable
private fun PalettePicker(vm: MainViewModel, s: Settings) {
    val cs = MaterialTheme.colorScheme; val reveal = LocalThemeReveal.current
    val theme = if (s.isOled) "oled" else s.theme
    var maker by remember { mutableStateOf<CustomPalette?>(null) }; var making by remember { mutableStateOf(false) }
    @Composable fun Swatch(p: Palette, onLong: (() -> Unit)? = null) {
        val sc = p.scheme(theme); val on = !s.materialYouEnabled && s.palette == p.id
        val at = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(60.dp).trackCenter(at).springClick(if (on) MaterialShapes.Cookie9Sided.toShape() else CircleShape, onLongClick = onLong) {
                vm.haptic(H.SELECTION)
                val apply = { vm.set { it.copy(palette = p.id, materialYouEnabled = false) } }
                if (reveal != null && !on) reveal.run(at.value, apply) else apply()
            }, contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(sc.primary))
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(sc.secondaryContainer))
                        Box(Modifier.weight(1f).fillMaxHeight().background(sc.tertiary))
                    }
                }
                if (on) Box(Modifier.size(24.dp).clip(CircleShape).background(sc.surface), contentAlignment = Alignment.Center) { Icon(Sym.Check, null, Modifier.size(16.dp), tint = sc.primary) }
            }
            Text(p.label, Modifier.padding(top = 8.dp), style = if (on) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                color = if (on) cs.primary else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Section(stringResource(R.string.settings_palette)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PALETTES.forEach { Swatch(it) }
            s.customPalettes.forEach { cp -> Swatch(customScheme(cp)) { vm.haptic(H.LIGHT); maker = cp; making = true } }
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(60.dp).springClick(MaterialShapes.Cookie9Sided.toShape()) { maker = null; making = true }.background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Sym.Add, stringResource(R.string.settings_make_a_palette), tint = cs.onPrimaryContainer)
                }
                Text(stringResource(R.string.settings_make), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
        }
        s.customPalettes.firstOrNull { it.id == s.palette && !s.materialYouEnabled }?.let { cp ->
            SRow(Sym.Edit, stringResource(R.string.settings_edit_palette, cp.name), stringResource(R.string.settings_edit_palette_desc), onClick = { maker = cp; making = true }) { Chevron() }
        }
    }
    if (making) PaletteMakerSheet(vm, maker) { making = false }
}

/** Theme previews drawn from each real scheme; picking one sweeps the change across the screen from the tap. */
@Composable
private fun ThemePicker(vm: MainViewModel, s: Settings) {
    val reveal = LocalThemeReveal.current
    val current = if (s.isOled) "oled" else s.theme
    Section(stringResource(R.string.settings_theme)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("light" to stringResource(R.string.common_light), "dusk" to stringResource(R.string.common_dusk), "dark" to stringResource(R.string.common_dark), "oled" to stringResource(R.string.common_oled)).forEach { (t, label) ->
                val p = previewScheme(t, s.palette, s.customPalettes); val on = current == t
                val at = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                val r by animateDpAsState(if (on) 24.dp else 14.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "r")
                val ring by animateDpAsState(if (on) 3.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "ring")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(.62f).trackCenter(at).springClick(RoundedCornerShape(r)) {
                        vm.haptic(H.SELECTION)
                        val apply = { vm.set { it.copy(theme = if (t == "oled") "dark" else t, isOled = t == "oled") } }
                        if (reveal != null) reveal.run(at.value, apply) else apply()
                    }.border(ring, MaterialTheme.colorScheme.primary, RoundedCornerShape(r)).background(p.surface).padding(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.fillMaxWidth().height(10.dp).clip(Pill).background(p.surfaceContainerHigh))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(p.primaryContainer))
                                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(p.tertiaryContainer))
                            }
                            Box(Modifier.fillMaxWidth(.7f).height(6.dp).clip(Pill).background(p.onSurface.copy(.5f)))
                            Box(Modifier.fillMaxWidth(.5f).height(6.dp).clip(Pill).background(p.onSurface.copy(.3f)))
                        }
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp).clip(Pill).background(p.primary))
                        if (on) Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Icon(Sym.Check, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(label, Modifier.padding(top = 8.dp), style = if (on) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Modern vs classic store layout, chosen by picture rather than by switch. */
@Composable
private fun LayoutPicker(vm: MainViewModel, s: Settings) = Section(stringResource(R.string.settings_store_layout)) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("modern" to stringResource(R.string.common_modern), "classic" to stringResource(R.string.common_classic)).forEach { (id, label) ->
            val on = s.storeLayout == id
            val r by animateDpAsState(if (on) 28.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "r")
            Column(Modifier.weight(1f).springClick(RoundedCornerShape(r)) { vm.haptic(H.SELECTION); vm.set { it.copy(storeLayout = id) } }
                .background(if (on) cs.secondaryContainer else cs.surfaceContainerHigh).padding(12.dp)) {
                Column(Modifier.fillMaxWidth().height(116.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (id == "modern") {
                        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(cs.tertiaryContainer)) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(cs.outlineVariant)) } }
                    } else repeat(4) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(cs.tertiaryContainer)); Spacer(Modifier.width(6.dp))
                            Box(Modifier.fillMaxWidth(.8f).height(8.dp).clip(Pill).background(cs.outlineVariant))
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(on, null); Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
    Toggle(Sym.Description, stringResource(R.string.detail_about_this_app), stringResource(R.string.settings_classic_rows_preview_each_app_s), s.showAppAbout) { vm.set { it.copy(showAppAbout = !it.showAppAbout) } }
}

/** Floating dock size, previewed live at the chosen scale. */
@Composable
private fun DockSize(vm: MainViewModel, s: Settings) = Section(stringResource(R.string.guide_dock_size)) {
    val cs = MaterialTheme.colorScheme
    val scale = s.bottomNavScale.takeIf { it.isFinite() } ?: .85f
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).padding(16.dp)) {
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(Pill).background(cs.surfaceContainerHighest).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Sym.AndroidFilled, Sym.DesktopWindows, Sym.Tv, Sym.GridView, Sym.Code).forEachIndexed { i, ic ->
                    val on = i == 0
                    Row(Modifier.height(44.dp).clip(Pill).background(if (on) cs.secondaryContainer else Color.Transparent).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ic, null, Modifier.size(20.dp), tint = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
                        if (on) Text("Apps", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
                    }
                }
            }
        }
        Slider(scale, { v -> vm.set { it.copy(bottomNavScale = (v * 20).roundToInt() / 20f) } }, valueRange = .85f..1.15f, steps = 5)
        Row { Text(stringResource(R.string.settings_compact), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium); Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLargeEmphasized); Text(stringResource(R.string.common_large), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.labelMedium) }
    }
}

/** Tablets, foldables and TVs: keep the side rail, or bring the phone dock to the bottom or top. Phones always keep the bottom dock. */
@Composable
private fun DockPosition(vm: MainViewModel, s: Settings) = Section(stringResource(R.string.guide_big_screen_navigation)) {
    val cs = MaterialTheme.colorScheme
    val opts = listOf(Triple("rail", stringResource(R.string.common_side), Sym.ViewQuilt), Triple("bottom", stringResource(R.string.common_bottom), Sym.ArrowDownward), Triple("top", stringResource(R.string.common_top), Sym.ArrowUpward))
    val cur = s.wideNav.takeIf { w -> opts.any { it.first == w } } ?: "rail"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            opts.forEachIndexed { i, (id, label, icon) ->
                ToggleButton(cur == id, { vm.haptic(H.SELECTION); vm.set { it.copy(wideNav = id) } }, Modifier.weight(1f),
                    shapes = when (i) { 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(); opts.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(); else -> ButtonGroupDefaults.connectedMiddleButtonShapes() },
                    contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(stringResource(R.string.settings_big_screen_navigation_desc),
            Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun fontLabel(s: Settings) = if (s.appFont.startsWith("custom:")) s.customFonts.firstOrNull { "custom:${it.id}" == s.appFont }?.name ?: stringResource(R.string.settings_custom_font)
    else Defaults.FONTS.firstOrNull { it.first == s.appFont }?.second ?: "Space Grotesk"

@Composable
private fun DevConfigEditor(vm: MainViewModel, s: Settings) = Section(stringResource(R.string.settings_developer_config_source)) {
    val url = s.devConfigUrl.trim(); val loading = vm.devStatus == "loading"
    Toggle(Sym.Science, stringResource(R.string.settings_enable_dev_config), stringResource(R.string.settings_load_an_isolated_config_json_url), s.devConfigEnabled) {
        vm.set { it.copy(devConfigEnabled = !it.devConfigEnabled) }
    }
    Column(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(s.devConfigUrl, { v -> vm.set { it.copy(devConfigUrl = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_dev_config_url)) },
            placeholder = { Text("https://example.com/config.json") }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(vm::reload, modifier = Modifier.weight(1f), enabled = s.devConfigEnabled && url.isNotEmpty() && !loading, shapes = ButtonDefaults.shapes()) {
                Text(if (loading) stringResource(R.string.settings_reloading) else stringResource(R.string.settings_reload_dev_config))
            }
            OutlinedButton({ vm.set { it.copy(devConfigEnabled = false, devConfigUrl = "") }; vm.devStatus = "idle"; vm.devError = null; vm.reload() }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_disable_and_clear))
            }
        }
        Text(if (s.devConfigEnabled) stringResource(R.string.settings_status, when (vm.devStatus) { "loading" -> stringResource(R.string.settings_status_loading); "loaded" -> stringResource(R.string.settings_status_loaded); "error" -> stringResource(R.string.settings_status_failed); else -> stringResource(R.string.settings_status_idle) }) else stringResource(R.string.settings_status_production_mode),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        if (s.devConfigEnabled && url.isEmpty()) Text(stringResource(R.string.settings_enter_a_config_url_to_fully), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.devConfigEnabled && vm.devStatus == "loaded") Text(stringResource(R.string.settings_last_loaded, url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.devError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FontSheet(vm: MainViewModel, s: Settings, error: String?, importing: Boolean, onImport: () -> Unit, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    SheetTitle(stringResource(R.string.guide_fonts), Sym.FontDownload, stringResource(R.string.settings_select_app_typeface))
    LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.settings_aa_bb_gg_123), style = MaterialTheme.typography.displaySmallEmphasized)
                    Text(stringResource(R.string.settings_the_quick_brown_fox_jumps_over), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        @Composable fun FontRow(key: String, label: String, tag: String?, onDelete: (() -> Unit)? = null) {
            val on = s.appFont == key
            ListItem({ Text(label, fontWeight = if (on) FontWeight.Black else FontWeight.Medium) },
                Modifier.clip(RoundedCornerShape(if (on) 24.dp else 8.dp)).clickable { vm.haptic(H.SELECTION); vm.set { it.copy(appFont = key) } },
                supportingContent = tag?.let { { Text(it) } },
                trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                    onDelete?.let { IconButton(it) { Icon(Sym.DeleteOutline, stringResource(R.string.settings_delete, label)) } }
                    if (on) Icon(Sym.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                } },
                colors = ListItemDefaults.colors(containerColor = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer))
        }
        items(Defaults.FONTS) { (k, l) -> FontRow(k, l, when (k) { "spaceGrotesk" -> stringResource(R.string.common_default); "systemDefault" -> stringResource(R.string.common_system); else -> null }) }
        items(s.customFonts, key = { it.id }) { f -> FontRow("custom:${f.id}", f.name, stringResource(R.string.common_custom)) { vm.set { it.removeCustomFont(f.id) } } }
        item {
            OutlinedButton(onImport, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !importing, shapes = ButtonDefaults.shapes()) {
                Icon(Sym.Upload, null); Spacer(Modifier.width(8.dp)); Text(if (importing) stringResource(R.string.settings_importing) else stringResource(R.string.settings_import_custom_font))
            }
            error?.let { Text(it, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_active_label), style = MaterialTheme.typography.bodySmall); Spacer(Modifier.width(4.dp)); Text(fontLabel(s), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Button(onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.xtra_done)) }
            }
        }
    }
    Spacer(Modifier.navigationBarsPadding())
}

/** One-time guided setup for direct installs: Wi-Fi, Developer options, then pairing through the notification shade. */
@Composable
internal fun DirectInstallSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val ctx = LocalContext.current; val close = LocalSheetClose.current
    val st by WirelessAdb.status.collectAsState()
    var wifi by remember { mutableStateOf(WirelessAdb.onWifi(ctx)) }; var dev by remember { mutableStateOf(WirelessAdb.devOptions(ctx)) }
    var wl by remember { mutableStateOf(WirelessAdb.wirelessOn(ctx)) }
    var code by remember { mutableStateOf("") }; var enabled by remember { mutableStateOf(false) }
    fun enable() { if (enabled) return; enabled = true; vm.set { it.copy(directInstall = true) }; vm.haptic(H.SUCCESS); vm.toast(ctx.getString(R.string.settings_installs_now_skip_the_24_hour)); close() }
    // Already paired: no setup needed
    LaunchedEffect(Unit) { if (runCatching { WirelessAdb.connect(ctx) }.isSuccess) enable() }
    LaunchedEffect(st.phase) { if (st.phase == WirelessAdb.Phase.READY) enable() }
    // Re-check while the user is away in system Settings
    LaunchedEffect(Unit) { while (true) { delay(1000); wifi = WirelessAdb.onWifi(ctx); dev = WirelessAdb.devOptions(ctx); wl = WirelessAdb.wirelessOn(ctx) } }

    Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.guide_skip_the_24_hour_wait), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(stringResource(R.string.settings_one_time_setup_orion_installs_through),
            Modifier.padding(horizontal = 8.dp).padding(top = 4.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SetupStep(1, stringResource(R.string.settings_connect_to_wi_fi), if (wifi) stringResource(R.string.settings_status_connected) else stringResource(R.string.settings_wireless_debugging_only_works_on_wi), wifi)
            SetupStep(2, stringResource(R.string.settings_unlock_developer_options), if (dev) stringResource(R.string.settings_unlocked) else stringResource(R.string.settings_tap_build_number_7_times_in), dev)
            SetupStep(3, stringResource(R.string.settings_turn_on_wireless_debugging), if (wl) stringResource(R.string.common_on) else stringResource(R.string.settings_switch_it_on_and_tap_allow), wl)
            SetupStep(4, stringResource(R.string.settings_pair_orion), stringResource(R.string.settings_tap_pair_device_with_pairing_code), st.phase == WirelessAdb.Phase.READY)
        }
        Text(stringResource(R.string.settings_orion_s_notification_walks_you_through), Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { WirelessAdb.startSetup(ctx) }, modifier = Modifier.fillMaxWidth(), enabled = wifi && st.phase != WirelessAdb.Phase.PAIRING, shapes = ButtonDefaults.shapes()) {
            Text(if (st.phase == WirelessAdb.Phase.IDLE) stringResource(R.string.settings_start_setup) else stringResource(R.string.common_continue))
        }
        if (st.phase == WirelessAdb.Phase.PAIRING || st.phase == WirelessAdb.Phase.FAILED) Row(Modifier.padding(8.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (st.phase == WirelessAdb.Phase.PAIRING) { LoadingIndicator(Modifier.size(32.dp)); Spacer(Modifier.width(8.dp)) }
            Text(st.msg, color = if (st.phase == WirelessAdb.Phase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
            label = { Text(stringResource(R.string.settings_or_type_the_code_here_split)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = { TextButton({ WirelessAdb.pairAsync(ctx, code) }, enabled = code.length == 6 && st.phase != WirelessAdb.Phase.PAIRING) { Text(stringResource(R.string.settings_pair)) } })
    }
    Spacer(Modifier.navigationBarsPadding().height(16.dp))
}

@Composable
private fun SetupStep(n: Int, title: String, desc: String, done: Boolean, action: (@Composable () -> Unit)? = null) = ListItem(
    { Text(title, style = MaterialTheme.typography.titleMedium) },
    supportingContent = { Column { Text(desc); action?.invoke() } },
    leadingContent = {
        if (done) TonalIcon(Sym.Check, Green)
        else Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text("$n", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
)

@Composable
private fun InstallerSheet(vm: MainViewModel, s: Settings, step: String, setStep: (String) -> Unit, list: List<Installer>, loading: Boolean, error: String?, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BackHandler(step == "specific") { setStep("mode") }
        Text(stringResource(R.string.settings_choose_installer), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.headlineSmallEmphasized)
        if (step == "specific" && s.hiddenInstallers.isNotEmpty()) IconButton({ vm.set { it.copy(hiddenInstallers = emptyList()) } }) { Icon(Sym.RestartAlt, stringResource(R.string.settings_reset_hidden_installers)) }
    }
    AnimatedContent(step, label = "step") { st ->
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (st == "mode") {
                Selectable(s.installerPreference == "system", stringResource(R.string.settings_system_installer)) { vm.set { it.withInstaller("system") }; onDismiss() }
                Selectable(s.installerPreference == "chooser", stringResource(R.string.settings_ask_every_time)) { vm.set { it.withInstaller("chooser") }; onDismiss() }
                Selectable(s.installerPreference == "package", stringResource(R.string.settings_specific_installer), if (s.installerPreference == "package") s.installerLabel.ifEmpty { stringResource(R.string.settings_selected) } else null) { setStep("specific") }
            } else when {
                loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { LoadingIndicator(); Text(stringResource(R.string.common_loading), Modifier.padding(start = 12.dp)) }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                list.isEmpty() -> EmptyState(Sym.Android, stringResource(R.string.settings_no_installers_found))
                else -> LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val shown = list.filter { it.packageName !in s.hiddenInstallers }
                    items(shown, key = { it.packageName }) { ins ->
                        InstallerRow(vm, ins, s.installerPreference == "package" && s.installerPackage == ins.packageName, ins == shown.first(),
                            { vm.set { it.withInstaller("package", ins.packageName, ins.label) }; onDismiss() }) { vm.set { it.hideInstaller(ins.packageName) } }
                    }
                }
            }
        }
    }
    Spacer(Modifier.navigationBarsPadding())
}

@Composable
private fun InstallerRow(vm: MainViewModel, ins: Installer, selected: Boolean, hint: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, ins.packageName) { value = withContext(Dispatchers.IO) { vm.tracker.icon(ins.packageName)?.asImageBitmap() } }
    SwipeRow(RoundedCornerShape(20.dp), right = SwipeAction(Sym.VisibilityOff, stringResource(R.string.settings_hide_installer), MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, removes = true, run = onRemove), hint = hint) {
        ListItem({ Text(ins.label, fontWeight = FontWeight.Bold) }, Modifier.clickable(onClick = onSelect),
            supportingContent = { Text(ins.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = { icon?.let { Image(it, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))) } ?: TonalIcon(Sym.Android, MaterialTheme.colorScheme.primary) },
            trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                if (ins.isSystem) Text(stringResource(R.string.common_system), Modifier.clip(Pill).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                if (selected) Icon(Sym.CheckCircle, null, Modifier.padding(start = 8.dp), tint = MaterialTheme.colorScheme.primary)
            } },
            colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}

@Composable
private fun ScannerSheet(apps: List<AppItem>, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }; var done by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }; var total by remember { mutableIntStateOf(apps.size) }
    var results by remember { mutableStateOf<List<MissionResult>>(emptyList()) }
    fun start() = scope.launch {
        scanning = true; progress = 0; done = false; results = emptyList()
        results = runCatalogDiagnostic(apps, ctx) { p, t -> progress = p; total = t }
        scanning = false; done = true
    }
    SheetTitle(stringResource(R.string.settings_mission_scanner_admin), Sym.Radar)
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        when {
            scanning -> {
                Text(stringResource(R.string.settings_scanning_catalog), style = MaterialTheme.typography.titleMedium)
                LinearWavyProgressIndicator({ if (total == 0) 0f else progress / total.toFloat() }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
                Text(stringResource(R.string.settings_apps_checked, progress, total), style = MaterialTheme.typography.bodySmall)
            }
            done -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_scan_complete), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_found_apps_with_issues_out_of, results.size, total), style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(::start, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.settings_scan_again)) }
                }
                if (results.isEmpty()) Text(stringResource(R.string.settings_everything_looks_perfect), Modifier.padding(vertical = 24.dp), color = Safe, fontWeight = FontWeight.Bold)
                else LazyColumn(Modifier.heightIn(max = 480.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.appId }) { r ->
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer.copy(.4f))) {
                            Column(Modifier.padding(14.dp)) {
                                Text(r.appName, fontWeight = FontWeight.Bold); Text(r.appId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                r.issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                            }
                        }
                    }
                }
            }
            else -> {
                Text(stringResource(R.string.settings_ready_to_scan), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_this_will_run_a_diagnostic_on, apps.size),
                    Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(::start, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.settings_run_diagnostic_now)) }
            }
        }
    }
    Spacer(Modifier.navigationBarsPadding().height(16.dp))
}

private val ICON_ART = mapOf(
    "default" to (R.mipmap.ic_launcher_background to R.mipmap.ic_launcher_foreground),
    "midnight" to (R.drawable.ic_icon_midnight_bg to R.drawable.ic_icon_midnight_fg), "sunset" to (R.drawable.ic_icon_sunset_bg to R.drawable.ic_icon_sunset_fg),
    "ocean" to (R.drawable.ic_icon_ocean_bg to R.drawable.ic_icon_ocean_fg), "mint" to (R.drawable.ic_icon_mint_bg to R.drawable.ic_icon_mint_fg),
    "neon" to (R.drawable.ic_icon_neon_bg to R.drawable.ic_icon_neon_fg), "gold" to (R.drawable.ic_icon_gold_bg to R.drawable.ic_icon_gold_fg),
)

/** A scrolling row of picks; locked ones carry their ad count and route to Fuel the Code. */
@Composable
private fun CosmeticRow(items: List<Cosmetic>, selected: String, width: androidx.compose.ui.unit.Dp, onPick: (Cosmetic) -> Unit, art: @Composable (Cosmetic) -> Unit) {
    val s = LocalS.current; val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).horizontalScroll(rememberScrollState()).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { c ->
            val on = c.id == selected; val open = c.unlocked(s)
            val r by animateDpAsState(if (on) 28.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "r")
            Column(Modifier.width(width).springClick(RoundedCornerShape(r)) { onPick(c) }.background(if (on) cs.secondaryContainer else cs.surfaceContainerHigh).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(64.dp).fillMaxWidth().graphicsLayer { alpha = if (open) 1f else .45f }, contentAlignment = Alignment.Center) { art(c) }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!open) { Icon(Sym.Lock, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(c.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                Text(when { open -> if (on) stringResource(R.string.settings_in_use) else stringResource(R.string.settings_tap_to_use); s.unlockTarget == c.id -> stringResource(R.string.settings_unlocking_progress, c.progress(s), c.ads); else -> stringResource(R.string.settings_ads_progress, c.progress(s), c.ads) }, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IconPicker(vm: MainViewModel, s: Settings) = Section(stringResource(R.string.settings_app_icon)) {
    CosmeticRow(APP_ICONS, s.appIcon, 92.dp, { vm.pickCosmetic(it, true) }) { c ->
        val (bg, fg) = ICON_ART.getValue(c.id)
        Box(Modifier.size(56.dp).clip(CircleShape)) {
            Image(painterResource(bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Image(painterResource(fg), c.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
    InfoCard(stringResource(R.string.settings_app_icon_info), icon = Sym.Palette)
}

/** Each dock drawn small, in its own colors and selected-item style. */
@Composable
private fun DockPicker(vm: MainViewModel, s: Settings) = Section(stringResource(R.string.settings_dock_style)) {
    val cs = MaterialTheme.colorScheme
    CosmeticRow(DOCKS, s.navDock, 132.dp, { vm.pickCosmetic(it, false) }) { c ->
        val icons = listOf(Sym.AndroidFilled, Sym.DesktopWindows, Sym.GridView, Sym.Code)
        val (bg, fg) = cs.surfaceContainerHighest to cs.onSurfaceVariant
        val shape = when (c.id) { "bar" -> RoundedCornerShape(4.dp); "labels" -> RoundedCornerShape(12.dp); else -> Pill }
        Row(Modifier.fillMaxWidth().clip(shape).background(bg).padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            icons.forEachIndexed { i, ic ->
                val on = i == 0
                val (ibg, ifg, ishape) = when (c.id) {
                    "vibrant" -> Triple(if (on) cs.primary else Color.Transparent, if (on) cs.onPrimary else fg, if (on) RoundedCornerShape(8.dp) else CircleShape)
                    "shapes" -> Triple(if (on) cs.secondaryContainer else Color.Transparent, if (on) cs.onSecondaryContainer else fg, if (on) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                    else -> Triple(if (on) cs.secondaryContainer else Color.Transparent, if (on) cs.onSecondaryContainer else fg, Pill)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(if (c.id == "bar" || c.id == "labels") 26.dp else 22.dp, if (c.id == "bar" || c.id == "labels") 16.dp else 22.dp).clip(ishape).background(ibg), contentAlignment = Alignment.Center) {
                        Icon(ic, null, Modifier.size(12.dp), tint = ifg)
                    }
                    if (c.id == "bar" || c.id == "labels") Box(Modifier.padding(top = 2.dp).size(14.dp, 3.dp).clip(Pill).background(fg.copy(.5f)))
                }
            }
        }
    }
    InfoCard(stringResource(R.string.settings_dock_style_info), icon = Sym.Palette)
}
