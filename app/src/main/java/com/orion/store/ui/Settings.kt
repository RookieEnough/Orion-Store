package com.orion.store.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
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

class IdentityError(msg: String) : Exception(msg)

private inline fun <reified T> JsonObject.or(k: String, fb: T): T =
    get(k)?.takeIf { it !is JsonNull }?.let { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() } ?: fb

/** Returns merged settings plus favorites (null = keep current). Throws IdentityError with the user-facing message. */
fun identityImport(raw: String, c: Settings): Pair<Settings, List<String>?> {
    val text = runCatching { String(Base64.decode(raw.trim(), Base64.DEFAULT)) }.getOrNull()?.takeIf { it.trimStart().startsWith("{") } ?: raw
    val d: JsonObject; val sig: String
    try {
        val pkg = json.parseToJsonElement(text).jsonObject
        d = pkg["data"] as? JsonObject ?: throw IllegalStateException("Invalid Save File")
        sig = (pkg["sig"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: throw IllegalStateException("Invalid Save File")
    } catch (_: Exception) { throw IdentityError("Corrupt or incompatible save file.") }
    if (sha256Hex((d.toString() + ID_SALT).toByteArray()) != sig) throw IdentityError("Tampering Detected. Save integrity check failed.")
    val merged = try { mergeIdentity(d, c) } catch (_: Exception) { throw IdentityError("Corrupt or incompatible save file.") }
    // The signing salt ships in the APK, so a valid signature alone doesn't prove the numbers are real
    if (!Xp.plausible(merged, System.currentTimeMillis())) throw IdentityError("This save has more progress than anyone could have earned. It was not restored.")
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
private suspend fun scanApp(a: AppItem): MissionResult? {
    val i = mutableListOf<String>()
    if (a.author.isBlank() || a.author.equals("unknown", true)) i += "Missing Developer Name"
    if (a.description.trim().length < 20) i += "Description missing or too short"
    if (a.screenshots.isEmpty()) i += "No screenshots provided"
    if (a.icon.isNotEmpty()) { if (!alive(a.icon)) i += "Dead Icon URL: ${a.icon}" } else i += "Missing Icon URL"
    if (a.downloadUrl.isNotEmpty()) { if (!alive(a.downloadUrl)) i += "Dead Download URL: ${a.downloadUrl}" } else i += "Missing Download URL"
    a.screenshots.firstOrNull()?.let { if (!alive(it)) i += "Dead Screenshot URL: $it" }
    return if (i.isEmpty()) null else MissionResult(a.id, a.name, i)
}
suspend fun runCatalogDiagnostic(apps: List<AppItem>, onProgress: (Int, Int) -> Unit): List<MissionResult> {
    val out = mutableListOf<MissionResult>(); var n = 0
    apps.chunked(10).forEach { b -> out += coroutineScope { b.map { async { scanApp(it) } }.awaitAll() }.filterNotNull(); n += b.size; onProgress(n, apps.size) }
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

private data class Cat(val id: String, val icon: ImageVector, val color: Color, val title: String, val desc: String, val shape: RoundedPolygon)
private val CATS = listOf(
    Cat("queue", Sym.Downloading, Blue, "Downloads", "Queue, installs and pending updates", MaterialShapes.Cookie9Sided),
    Cat("visuals", Sym.Palette, Purple, "Look & feel", "Theme, font, motion and density", MaterialShapes.Flower),
    Cat("network", Sym.Wifi, SkyC, "Network", "Wi-Fi rules and auto-update", MaterialShapes.Clover4Leaf),
    Cat("storage", Sym.CleaningServices, Orange, "Storage", "Janitor and saved APKs", MaterialShapes.Sunny),
    Cat("installer", Sym.Shield, Green, "Orion Xtra", "Guardian, Sentinel and installer", MaterialShapes.Gem),
    Cat("identity", Sym.Backup, Pink, "Backup", "Save or restore your identity", MaterialShapes.Cookie6Sided),
    Cat("interface", Sym.ViewQuilt, Teal, "Interface", "Store layout and visible tabs", MaterialShapes.Pentagon),
    Cat("developer", Sym.Terminal, Yellow, "Developer", "Debug switches and tools", MaterialShapes.SoftBurst),
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
private fun QuickTile(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, state: String = if (on) "On" else "Off", onClick: () -> Unit) {
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
            Text(state, style = MaterialTheme.typography.labelMedium, color = fg.copy(.75f), maxLines = 1)
        }
    }
}
// endregion

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = LocalS.current; val d = LocalD.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
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
        runCatching { withContext(Dispatchers.IO) { vm.tracker.installers() } }.onSuccess { installers = it }.onFailure { installersError = "Failed to load installers." }
        installersLoading = false
    }
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(menu, transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "menu") { m ->
            val avs = this
            val cat = CATS.firstOrNull { it.id == m }
            if (cat == null) Hub(vm, s, d, active, ready, pending) { c ->
                Modifier.sharedBounds(rememberSharedContentState("cat-${c.id}"), avs, resizeMode = PAGE_RESIZE)
            } else Page(cat, Modifier.sharedBounds(rememberSharedContentState("cat-${cat.id}"), avs, resizeMode = PAGE_RESIZE),
                onBack = ::back) {
                when (m) {
                    "identity" -> {
                        Section("Your data stays here") {
                            InfoCard("Save your progress to a file, or let Orion keep a copy in a cloud you pick. Nothing goes to an Orion server.", "Orion is serverless", Sym.CloudOff)
                        }
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val saving = remember { mutableStateOf(BackupPhase.IDLE) }
                            Button({
                                scope.backupRun(saving, vm) {
                                    runCatching { vm.tracker.saveFile(identityExport(vm.s, vm.d.favorites, System.currentTimeMillis()), "orion_identity_${System.currentTimeMillis()}.osf") }
                                        .onSuccess { importStatus = "Identity saved successfully." to "success" }
                                        .onFailure { importStatus = "Export failed: ${it.message}" to "error" }.isSuccess
                                }
                            }, modifier = Modifier.weight(1f).heightIn(min = 64.dp), shapes = ButtonDefaults.shapes()) {
                                BackupGlyph(saving.value, 32.dp, MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp))
                                AnimatedContent(saving.value, transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) }, label = "save") { p ->
                                    Column {
                                        Text(when (p) { BackupPhase.WORKING -> "Saving..."; BackupPhase.DONE -> "Saved"; BackupPhase.FAILED -> "Failed"; else -> "Back up" }, fontWeight = FontWeight.Bold)
                                        Text(if (p == BackupPhase.DONE) "Check your files" else "Signed .osf file", style = MaterialTheme.typography.labelSmall)
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
                                        importStatus = "Identity restored successfully!" to "success"; vm.haptic(H.SUCCESS)
                                        delay(1500); vm.reload()
                                    } catch (e: IdentityError) { importStatus = (e.message ?: "") to "error"; vm.haptic(H.ERROR) }
                                    catch (_: Exception) { importStatus = "Corrupt or incompatible save file." to "error"; vm.haptic(H.ERROR) }
                                }
                            }, modifier = Modifier.weight(1f).heightIn(min = 64.dp), shapes = ButtonDefaults.shapes()) {
                                Icon(Sym.FileDownload, null); Spacer(Modifier.width(8.dp)); Column { Text("Restore", fontWeight = FontWeight.Bold); Text("Import a save", style = MaterialTheme.typography.labelSmall) }
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
                    "network" -> Section("Downloads") {
                        Toggle(Sym.Wifi, "Wi-Fi only", "Skip downloads on cellular data", s.wifiOnly) { vm.set { it.copy(wifiOnly = !it.wifiOnly) } }
                        Toggle(Sym.Autorenew, "Auto-update apps", "Check and download updates every few hours, even with Orion closed", s.autoUpdateEnabled) { vm.set { it.copy(autoUpdateEnabled = !it.autoUpdateEnabled) } }
                        if (s.autoUpdateEnabled) {
                            Toggle(Sym.SystemUpdate, "Install automatically", "Install downloaded updates over the current app. Anything that fails shows up next time you open Orion.", s.autoInstall) {
                                vm.set { it.copy(autoInstall = !it.autoInstall) }
                            }
                            if (s.autoInstall) InfoCard(when {
                                s.rootMode -> "Root mode is on: every update installs silently."
                                s.useShizuku -> "Silent installs run through Shizuku, as long as Shizuku is running."
                                android.os.Build.VERSION.SDK_INT >= 31 -> "Apps Orion installed update silently. Apps from other stores need one tap, or adopt them with Root mode."
                                else -> "This Android version asks before every install. Root or Shizuku make updates silent."
                            })
                        }
                        Toggle(Sym.Verified, "Stable releases only", "Skip Beta, Alpha and Nightly builds when a stable one exists", s.stableOnly) { vm.set { it.copy(stableOnly = !it.stableOnly) } }
                        Toggle(Sym.FilterList, "Skip apps from other stores", "Apps installed by Play Store or another store leave My Apps and get no updates here. Bring one back from its page.", s.skipOtherStores) {
                            vm.set { it.copy(skipOtherStores = !it.skipOtherStores) }
                        }
                    }
                    "installer" -> {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeatureTile(Sym.Shield, "Guardian", "Permissions, APK export, debloat", Green, MaterialShapes.Cookie9Sided, Modifier.weight(1f)) { modal = "guardian" }
                            FeatureTile(Sym.MonitorHeart, "Sentinel", "Malware scan for installed apps", Blue, MaterialShapes.Sunny, Modifier.weight(1f)) { modal = "sentinel" }
                        }
                        val ch = ptr(s.pullToRefreshCharacter)
                        Section("Pull to refresh") {
                            SRow(Sym.AutoFixHigh, "Refresh character", ch.name, onClick = { picker = "ptr" }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PtrBadge(ch, 48.dp)
                                    Chevron()
                                }
                            }
                        }
                        Section("Install behavior") {
                            SRow(Sym.Android, "APK installer", when (s.installerPreference) {
                                "package" -> s.installerLabel.ifEmpty { "Specific Installer" }; "chooser" -> "Ask Every Time"; else -> "System Installer"
                            }, onClick = { installerStep = "mode"; picker = "installer" }) { Chevron() }
                            // Orion is also an installer: any APK from a file manager or browser opens on Orion's sheet
                            SRow(Sym.InstallMobile, "Install an APK file", "Pick any APK on this phone and install it with Orion", onClick = {
                                vm.haptic(H.LIGHT); vm.pickApk()
                            }) { Chevron() }
                            if (WirelessAdb.supported) Toggle(Sym.Speed, "Skip the 24-hour wait", "Install instantly through built-in wireless debugging", s.directInstall) {
                                if (vm.s.directInstall) vm.set { it.copy(directInstall = false, offWifiInstall = "") } else { picker = "direct" }
                            }
                            Toggle(Sym.Bolt, "Silent install", "Install apps in the background", s.useShizuku) {
                                shizukuError = null
                                if (!vm.s.useShizuku) scope.launch {
                                    try { vm.tracker.requestElevated(); vm.set { it.copy(useShizuku = !it.useShizuku) } }
                                    catch (e: Exception) { shizukuError = if ((e.message ?: "").contains("Shizuku is NOT running")) "Shizuku service is not running." else "Permission was denied by user." }
                                } else vm.set { it.copy(useShizuku = !it.useShizuku) }
                            }
                            if (android.os.Build.VERSION.SDK_INT >= 26) Toggle(Sym.AdminPanelSettings, "Dhizuku", "Silent installs and uninstalls through Dhizuku's device owner, no Shizuku or root", s.useDhizuku) {
                                shizukuError = null
                                if (vm.s.useDhizuku) vm.set { it.copy(useDhizuku = false) }
                                else scope.launch {
                                    runCatching { DhizukuBridge.request(ctx) }.onSuccess { ok -> if (ok) { vm.set { it.copy(useDhizuku = true) }; vm.haptic(H.SUCCESS) } else shizukuError = "Dhizuku permission was denied." }
                                        .onFailure { shizukuError = it.message ?: "Dhizuku is not available." }
                                }
                            }
                            Toggle(Sym.Tune, "Beyond install", "Choose downgrade, permissions, Play Store install and more before each install. Uses Shizuku or root.", s.installOptions) {
                                shizukuError = null
                                if (!vm.s.installOptions && !vm.s.rootMode) scope.launch {
                                    try { vm.tracker.requestElevated(); vm.set { it.copy(installOptions = true) } }
                                    catch (e: Exception) { shizukuError = if ((e.message ?: "").contains("Shizuku is NOT running")) "Shizuku service is not running." else "Permission was denied by user." }
                                } else vm.set { it.copy(installOptions = !it.installOptions) }
                            }
                            shizukuError?.let { Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold) }
                            InfoCard("Silent installs need Shizuku running, or Dhizuku activated as device owner.") {
                                Row {
                                    TextButton({ vm.navigateFromSettings("shizuku") }, contentPadding = PaddingValues(0.dp)) { Text("Get Shizuku") }
                                    Spacer(Modifier.width(16.dp))
                                    TextButton({ vm.tracker.open(DhizukuBridge.URL) }, contentPadding = PaddingValues(0.dp)) { Text("Get Dhizuku") }
                                }
                            }
                        }
                        Section("Root") {
                            var adopt by remember { mutableStateOf<String?>(null) }
                            ConfirmDialog(adopt, "Adopt", onConfirm = vm::adoptApps, onDismiss = { adopt = null })
                            Toggle(Sym.Terminal, "Root mode", "Silent installs and every Guardian tool through su, no Shizuku or wireless debugging", s.rootMode) {
                                if (vm.s.rootMode) vm.set { it.copy(rootMode = false) }
                                else scope.launch { if (vm.tracker.requestRoot()) { vm.set { it.copy(rootMode = true) }; vm.haptic(H.SUCCESS) } else vm.error("Root access was denied or this device is not rooted.") }
                            }
                            if (s.rootMode) {
                                if (android.os.Build.VERSION.SDK_INT >= 34) Toggle(Sym.Lock, "Update lock", "Other stores must ask you before updating apps Orion installs", s.updateLock) { vm.set { it.copy(updateLock = !it.updateLock) } }
                                SRow(Sym.Sync, "Adopt installed apps", "Make Orion the updater of catalog apps you got elsewhere. Keeps their data.", onClick = {
                                    adopt = "Orion reinstalls the same APK of each catalog app you installed from another source and becomes its installer. App data stays. The other store stops updating them."
                                }) { Chevron() }
                                SRow(Sym.DeleteSweep, "Clear every app's cache", "Free space across all apps in one tap", onClick = vm::trimCaches) { Chevron() }
                            }
                        }
                        Section("Sideloading protection") {
                            val vs = vm.verifierState
                            SRow(if (vs == com.orion.store.platform.Verifier.State.ACTIVE) Sym.GppBad else Sym.GppGood, "Developer verification", when (vs) {
                                com.orion.store.platform.Verifier.State.ACTIVE -> "On. Tap to turn it off with Secure me"
                                com.orion.store.platform.Verifier.State.OFF -> "Off, sideloading is protected. Tap to revert"
                                else -> "Not on this phone"
                            }, onClick = { vm.haptic(H.LIGHT); vm.openVerifier() }) { Chevron() }
                            Toggle(Sym.Shield, "Protection shield", "Show the shield in the header. It opens Secure me and Revert in one tap", s.verifierBadge) { vm.set { it.copy(verifierBadge = !it.verifierBadge) } }
                        }
                        Section("Recovery") {
                            SRow(Sym.Sync, "Force rescan packages", "Reload every installed app list", onClick = { vm.reload(); vm.scheduleInstalledSync(); vm.close(Overlay.SETTINGS) })
                        }
                    }
                    "storage" -> {
                        StatHero("${d.pendingCleanup.size}", if (d.pendingCleanup.size == 1) "APK waiting for cleanup" else "APKs waiting for cleanup") {
                            if (d.pendingCleanup.isNotEmpty()) Button({ vm.batchCleanup(); vm.haptic(H.SUCCESS) }, shapes = ButtonDefaults.shapes()) {
                                Icon(Sym.CleaningServices, null); Spacer(Modifier.width(8.dp)); Text("Clean now")
                            }
                        }
                        Section("Janitor") {
                            Toggle(Sym.CleaningServices, "Auto-cleanup installer", "Remove APKs after they install", s.deleteApk) { vm.set { it.copy(deleteApk = !it.deleteApk) } }
                            InfoCard("Janitor mode clears installed APK files on next app start.")
                        }
                    }
                    "queue" -> {
                        StatHero("${active + ready + pending}", "in the queue · $active active · $ready ready · $pending updates") {
                            if ((s.useShizuku || s.useDhizuku || s.directInstall || s.rootMode) && ready > 1) Button(vm::batchInstall, shapes = ButtonDefaults.shapes()) { Icon(Sym.Bolt, null); Spacer(Modifier.width(8.dp)); Text("Update all ($ready)") }
                        }
                        if (active == 0 && vm.updates.isEmpty() && ready == 0) EmptyState(Sym.DownloadDone, "Queue is empty", "Downloads and updates show up here.")
                        else Section("Queue") {
                            fun app(id: String) = all.firstOrNull { it.id == id }
                            d.readyToInstall.forEach { (id, file) ->
                                val a = app(id)
                                ListItem({ Text(a?.name ?: id, style = MaterialTheme.typography.titleMedium) }, Modifier.clip(RoundedCornerShape(4.dp)).clickable { a?.let { vm.navigateFromSettings(it.id) } },
                                    supportingContent = { Text("Ready to install") }, leadingContent = { a?.let { AppIcon(it, 44.dp) } ?: TonalIcon(Sym.Inventory, Safe) },
                                    trailingContent = {
                                        if (vm.installingId == id) LoadingIndicator(Modifier.size(32.dp))
                                        else Button({ a?.let { vm.installFile(it, file) } }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text("Install") }
                                    }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
                            }
                            d.activeDownloads.keys.forEach { id ->
                                val a = app(id); val p = d.downloadProgress[id] ?: 0f
                                Column(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { a?.let { vm.navigateFromSettings(it.id) } }.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        a?.let { AppIcon(it, 44.dp) }; Spacer(Modifier.width(12.dp))
                                        Text(a?.name ?: id, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${p.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                                        IconButton({ vm.cancelDownload(id) }) { Icon(Sym.Close, "Cancel download") }
                                    }
                                    LinearWavyProgressIndicator({ p / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp))
                                }
                            }
                            vm.updates.filter { it.id !in d.activeDownloads && it.id !in d.readyToInstall }.forEach { a ->
                                ListItem({ Text(a.name, style = MaterialTheme.typography.titleMedium) }, Modifier.clip(RoundedCornerShape(4.dp)).clickable { vm.navigateFromSettings(a.id) },
                                    supportingContent = { Text("Update to v${a.latestVersion}") }, leadingContent = { AppIcon(a, 44.dp) },
                                    trailingContent = { FilledTonalButton({ vm.download(a) }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text("Update") } },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
                            }
                        }
                    }
                    "visuals" -> {
                        ThemePicker(vm, s)
                        PalettePicker(vm, s)
                        Section("Color & type") {
                            Toggle(Sym.AutoAwesome, "Material You", "Dynamic wallpaper colors (Android 12+)", s.materialYouEnabled) { vm.set { it.copy(materialYouEnabled = !it.materialYouEnabled) } }
                            SRow(Sym.FontDownload, "Font", fontLabel(s), onClick = { fontError = null; picker = "font" }) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("Aa", style = MaterialTheme.typography.headlineSmallEmphasized); Chevron() }
                            }
                        }
                        Text("Performance", Modifier.padding(start = 32.dp, top = 16.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = tonal(LocalAccent.current).second)
                        Text(if (s.perfMode == "auto") "Auto picks ${s.perf.replaceFirstChar { it.uppercase() }} for this phone. Lite stops decorative motion, Boost keeps more images ready."
                            else "Lite stops decorative motion, Boost keeps more images ready. Image changes apply after a restart.", Modifier.padding(horizontal = 32.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // One connected group across the page, the same 16dp margins as the groups below: no scrolling, nothing off to one side
                        val modes = listOf("auto" to "Auto", "lite" to "Lite", "balanced" to "Balanced", "boost" to "Boost")
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            modes.forEachIndexed { i, (id, label) ->
                                ToggleButton(s.perfMode == id, { vm.haptic(H.SELECTION); vm.set { it.copy(perfMode = id) } }, Modifier.weight(if (id == "balanced") 1.3f else 1f),
                                    shapes = when (i) { 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(); modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(); else -> ButtonGroupDefaults.connectedMiddleButtonShapes() },
                                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label, maxLines = 1) }
                            }
                        }
                        Section("Motion & feel") {
                            Toggle(Sym.TouchApp, "Haptic feedback", "Tactile taps on buttons and toggles", s.hapticEnabled) { vm.set { it.copy(hapticEnabled = !it.hapticEnabled) } }
                            Toggle(Sym.Speed, "Smooth motion", "Prefer high refresh rate scrolling", s.highRefreshRate) { vm.set { it.copy(highRefreshRate = !it.highRefreshRate) } }
                            Toggle(Sym.MotionPhotosOff, "Disable animations", "Every transition snaps instantly", s.disableAnimations) { vm.set { it.copy(disableAnimations = !it.disableAnimations) } }
                            Toggle(Sym.BlurOn, "Glass dock", "Slightly translucent floating navigation", s.glassEffect) { vm.set { it.copy(glassEffect = !it.glassEffect) } }
                            Toggle(Sym.OpenInFull, "Full screen", "Hide the status bar while Orion is open. Swipe down from the top to peek it", s.immersive) { vm.set { it.copy(immersive = !it.immersive) } }
                            Toggle(Sym.Compress, "Compact mode", "Smaller rows with denser spacing", s.compactMode) { vm.set { it.copy(compactMode = !it.compactMode) } }
                        }
                        if (!Device.isTv) IconPicker(vm, s)
                        DockPicker(vm, s)
                        DockSize(vm, s)
                    }
                    "interface" -> {
                        LayoutPicker(vm, s)
                        Section("Navigation tabs") {
                            listOf(Triple("android", "Apps", "Android apps"), Triple("pc", "PC", "PC and desktop software"),
                                Triple("tv", "TV", "TV and big-screen apps"), Triple("myapps", "My Apps", "Library and download history")).forEach { (t, title, desc) ->
                                Toggle(when (t) { "android" -> Sym.Android; "pc" -> Sym.DesktopWindows; "tv" -> Sym.Tv; else -> Sym.GridView }, title, desc, t !in s.hiddenTabs) {
                                    vm.set { it.toggleHiddenTab(t) }
                                }
                            }
                        }
                        Section("Orion AI") {
                            Toggle(Sym.AutoAwesome, "AI star", "A small star you can drag anywhere, tap it to open Orion AI", s.aiButton) { vm.set { it.copy(aiButton = !it.aiButton) } }
                            Toggle(Sym.Notifications, "AI sound effects", "Little chimes when you chat with Orion AI", s.aiSounds) { vm.set { it.copy(aiSounds = !it.aiSounds) } }
                            SRow(Sym.Key, "Provider and key", OrionAi.provider(s.aiProvider).label + if (vm.ai.ready(s)) " · connected" else " · not connected",
                                onClick = { modal = "ai" }) { Chevron() }
                            SRow(Sym.Chat, "Open Orion AI", "Find apps by describing what you need", onClick = { vm.close(Overlay.SETTINGS); vm.open(Overlay.AI) }) { Chevron() }
                        }
                    }
                    "developer" -> {
                        Section("Testing and debug") {
                            InfoCard("These options are for testing and modifying core app behavior. They may cause instability.", icon = Sym.Warning)
                            Toggle(Sym.Storage, "Enable built-in data", "Load packaged data instead of remote sources", s.loadLocalData) { vm.set { it.copy(loadLocalData = !it.loadLocalData) } }
                            Toggle(Sym.Construction, "Local maintenance mode", "Work on bundles and data locally", s.localMaintenanceMode) {
                                vm.set { it.copy(localMaintenanceMode = !it.localMaintenanceMode) }
                            }
                            SRow(Sym.Shield, "Test sideloading protection", "Play the Secure me popup with a fake success. Nothing on the phone changes", onClick = { vm.haptic(H.LIGHT); vm.testVerifier("done") }) { Chevron() }
                            SRow(Sym.GppMaybe, "Test sideloading protection failure", "The same popup ending in a fake error, to check Try again", onClick = { vm.haptic(H.LIGHT); vm.testVerifier("failed") }) { Chevron() }
                            SRow(Sym.Radar, "Run catalog scanner (Admin)", "Check every app for missing text and dead links", onClick = { modal = "scanner" }) { Chevron() }
                            SRow(Sym.DeleteSweep, "Clear system cache", "Reset pending cleanup and ready installs", onClick = {
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
                if (ext !in setOf("ttf", "otf", "woff2")) { fontError = "Choose a TTF, OTF, or WOFF2 file."; return@launch }
                if (size > 8 * 1024 * 1024) { fontError = "Fonts must be 8 MB or smaller."; return@launch }
                importingFont = true
                val id = "font-${System.currentTimeMillis()}-${(1..6).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")}"
                runCatching {
                    withContext(Dispatchers.IO) {
                        val f = File(File(ctx.filesDir, "fonts/custom").apply { mkdirs() }, "$id.$ext")
                        ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }; f
                    }
                }.onSuccess { f ->
                    val font = CustomFont(id, name.substringBeforeLast('.').trim().ifEmpty { "Custom Font" }, name, ctx.contentResolver.getType(uri) ?: "font/$ext", f.absolutePath, System.currentTimeMillis())
                    vm.set { it.copy(customFonts = it.customFonts + font, appFont = "custom:$id") }
                }.onFailure { fontError = "Could not read this font." }
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
    "queue" to "Downloads keep running in the background, with live progress in your notifications.",
    "visuals" to "The theme button in the search bar cycles Light, Dusk and Dark from anywhere.",
    "network" to "Auto-update queues new versions whenever the catalog refreshes. Wi-Fi only blocks downloads on mobile data.",
    "storage" to "The janitor clears leftover APKs a week after install, or on the next launch with Delete APK on.",
    "installer" to "With Shizuku, installs and updates go through without a confirmation prompt each time.",
    "identity" to "Your backup is a single small .osf file. Keep a copy in your cloud drive and restore it on any phone.",
    "interface" to "Hidden tabs come back from here at any time. The Dev tab always stays.",
    "developer" to "Close developer options from the bottom of this page when you are done.",
)

@Composable
private fun PageFooter(cat: Cat) {
    val (_, fg) = tonal(cat.color)
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Spinner(cat.shape, fg.copy(.12f), 112.dp, periodMs = 30_000)
            Icon(cat.icon, null, Modifier.size(40.dp), tint = fg.copy(.7f))
        }
        Text(TIPS[cat.id] ?: cat.desc, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium,
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
                Text("Orion Store v$STORE_VERSION · ${vm.allApps().size} apps in catalog", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            // Announcements already read leave the header; the latest one stays readable here until a new one arrives
            val ann = vm.config?.announcement?.takeIf { it.isNotBlank() }
            var showAnn by remember { mutableStateOf(false) }
            if (ann != null) IconButton({ vm.haptic(H.LIGHT); showAnn = true }) { Icon(Sym.Campaign, "Last announcement", tint = cs.onSurfaceVariant) }
            if (showAnn && ann != null) AlertDialog({ showAnn = false }, icon = { Icon(Sym.Campaign, null) }, title = { Text("From the Orion team") },
                text = { Text(ann, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton({ showAnn = false; vm.dismissAnnouncement() }) { Text("Got it") } })
        }
        var editing by remember { mutableStateOf(false) }
        HubHeader("Quick toggles") { IconButton({ editing = true }) { Icon(Sym.Edit, "Edit quick toggles", tint = cs.primary) } }
        val quick = (s.quickTiles ?: DEFAULT_QUICK).mapNotNull { id -> QUICK.firstOrNull { it.id == id && it.shown(s) } }
        if (quick.isEmpty()) Text("No quick toggles. Tap the pencil to add your favorites.", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        quick.chunked(2).forEach { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { q -> QuickTile(q.icon, q.label, q.get(s), Modifier.weight(1f)) { vm.haptic(H.SELECTION); vm.set(q.flip) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
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
                        Text(if (queued == 0) "All caught up" else "$active active · $ready ready · $pending updates", style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(.8f))
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
        HubHeader("Everything else", "Long-press a card to move it") {
            if (s.settingsOrder.isNotEmpty()) TextButton({ vm.set { it.copy(settingsOrder = emptyList()) }; vm.haptic(H.MEDIUM) }) { Text("Reset") }
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
private class Quick(val id: String, val icon: ImageVector, val label: String, val get: (Settings) -> Boolean, val shown: (Settings) -> Boolean = { true }, val flip: (Settings) -> Settings)
private val QUICK = listOf(
    Quick("autoUpdate", Sym.Autorenew, "Auto-update", { it.autoUpdateEnabled }) { it.copy(autoUpdateEnabled = !it.autoUpdateEnabled) },
    Quick("wifiOnly", Sym.Wifi, "Wi-Fi only", { it.wifiOnly }) { it.copy(wifiOnly = !it.wifiOnly) },
    Quick("haptics", Sym.Vibration, "Haptics", { it.hapticEnabled }) { it.copy(hapticEnabled = !it.hapticEnabled) },
    Quick("materialYou", Sym.Wallpaper, "Material You", { it.materialYouEnabled }, { android.os.Build.VERSION.SDK_INT >= 31 }) { it.copy(materialYouEnabled = !it.materialYouEnabled) },
    Quick("smooth", Sym.Speed, "Smooth motion", { it.highRefreshRate }) { it.copy(highRefreshRate = !it.highRefreshRate) },
    Quick("compact", Sym.Compress, "Compact", { it.compactMode }) { it.copy(compactMode = !it.compactMode) },
    Quick("stableOnly", Sym.Verified, "Stable only", { it.stableOnly }) { it.copy(stableOnly = !it.stableOnly) },
    Quick("cleanup", Sym.CleaningServices, "Auto-cleanup", { it.deleteApk }) { it.copy(deleteApk = !it.deleteApk) },
    Quick("noMotion", Sym.MotionPhotosOff, "No animations", { it.disableAnimations }) { it.copy(disableAnimations = !it.disableAnimations) },
    Quick("glass", Sym.BlurOn, "Glass dock", { it.glassEffect }) { it.copy(glassEffect = !it.glassEffect) },
    Quick("updateLock", Sym.Lock, "Update lock", { it.updateLock }, { it.rootMode && android.os.Build.VERSION.SDK_INT >= 34 }) { it.copy(updateLock = !it.updateLock) },
    Quick("localData", Sym.Storage, "Built-in data", { it.loadLocalData }, { it.isDevUnlocked }) { it.copy(loadLocalData = !it.loadLocalData) },
    Quick("maintenance", Sym.Construction, "Maintenance", { it.localMaintenanceMode }, { it.isDevUnlocked }) { it.copy(localMaintenanceMode = !it.localMaintenanceMode) },
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
    SheetTitle("Quick toggles", Sym.Edit, "Hold and drag to reorder. Tap to add or remove.")
    Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        if (tiles.isNotEmpty()) Text("Your tiles", Modifier.padding(start = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = cs.primary)
        tiles.forEachIndexed { i, id -> key(id) {
            val q = QUICK.first { it.id == id }
            SegmentedListItem({ toggle(id) }, ListItemDefaults.segmentedShapes(i, tiles.size),
                modifier = Modifier.onSizeChanged { step = it.height + gap }.zIndex(if (dragging == id) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging == id) dy else glide[id]?.value ?: 0f; val k = if (dragging == id) 1.03f else 1f; scaleX = k; scaleY = k }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress({ live = live ?: pickedOf(vm.s); dragging = id; dy = 0f; vm.haptic(H.HEAVY) }, ::end, ::end) { c, d -> c.consume(); move(d.y) }
                    },
                colors = ListItemDefaults.segmentedColors(containerColor = if (dragging == id) cs.primaryContainer else cs.secondaryContainer),
                leadingContent = { Icon(q.icon, null) }, trailingContent = { Icon(Sym.DragHandle, "Hold to move") }) {
                Text(q.label, fontWeight = FontWeight.SemiBold)
            }
        } }
        val rest = QUICK.filter { it.shown(s) && it.id !in tiles }
        if (rest.isNotEmpty()) Text("Add tiles", Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = cs.primary)
        rest.forEachIndexed { i, q ->
            SegmentedListItem({ toggle(q.id) }, ListItemDefaults.segmentedShapes(i, rest.size), colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainerHigh),
                leadingContent = { Icon(q.icon, null) }, trailingContent = { Icon(Sym.Add, "Add") }) {
                Text(q.label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ vm.set { it.copy(quickTiles = null) }; vm.haptic(H.MEDIUM) }, modifier = Modifier.weight(1f), enabled = s.quickTiles != null, shapes = ButtonDefaults.shapes()) {
            Icon(Sym.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Reset")
        }
        Button(LocalSheetClose.current, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Icon(Sym.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Done") }
    }
}

@Composable
private fun Bento(vm: MainViewModel, cat: Cat, shared: @Composable (Cat) -> Modifier, modifier: Modifier, compact: Boolean = false, badge: String? = null, extra: @Composable ColumnScope.() -> Unit = {}) {
    val (bg, fg) = tonal(cat.color)
    Box(modifier.then(shared(cat)).springClick(RoundedCornerShape(28.dp)) { vm.haptic(H.LIGHT); vm.settingsMenu = cat.id }.background(bg)) {
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
                Text(cat.title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
                if (!compact) Text(cat.desc, style = MaterialTheme.typography.bodySmall, color = fg.copy(.8f), maxLines = 2)
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
        Column(modifier.fillMaxSize().background(cs.surface).readableWidth().verticalScroll(rememberScrollState()).readableWidth().heightIn(min = screen).padding(bottom = 40.dp)) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(bg, cs.surface)))) {
                Spinner(cat.shape, fg.copy(.08f), 260.dp, Modifier.align(Alignment.TopEnd).offset(70.dp, (-30).dp), periodMs = 40_000)
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(bottom = 12.dp)) {
                    Box(Modifier.padding(start = 24.dp, top = 24.dp).size(72.dp), contentAlignment = Alignment.Center) {
                        Spinner(cat.shape, fg.copy(.16f), 72.dp, periodMs = 18_000)
                        Icon(cat.icon, null, Modifier.size(34.dp), tint = fg)
                    }
                    Text(cat.title, Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.displaySmallEmphasized)
                    Text(cat.desc, Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }
            Column(Modifier.reveal(1)) { content() }
            Spacer(Modifier.weight(1f))
            PageFooter(cat)
        }
    }
}

/** Auto backup through Android's storage picker: Drive, OneDrive, Proton Drive or any app with a document provider, with no account linking inside Orion. */
@Composable
private fun CloudBackup(vm: MainViewModel, s: Settings) = Section("Cloud backup") {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val pm = ctx.packageManager
    val uri = s.cloudBackupUri.takeIf { it.isNotEmpty() }?.toUri()
    val where = remember(uri) { uri?.authority?.let { a -> runCatching { pm.resolveContentProvider(a, 0)?.loadLabel(pm)?.toString() }.getOrNull() ?: a } }
    val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    fun connect() = scope.launch {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "orion_identity.osf")
        val u = ResultHost.run(i).let { (code, data) -> data?.data?.takeIf { code == android.app.Activity.RESULT_OK } } ?: return@launch
        if (runCatching { ctx.contentResolver.takePersistableUriPermission(u, rw) }.isFailure) { vm.error("That storage app does not allow automatic backups. Pick another."); return@launch }
        vm.set { it.copy(cloudBackupUri = u.toString(), cloudBackupEvery = it.cloudBackupEvery.takeIf { e -> e != "off" } ?: "week") }; vm.cloudBackup(true)
    }
    SRow(Sym.Cloud, if (uri == null) "Connect a cloud" else "Backing up to $where", if (uri == null) "Google Drive, OneDrive, Proton Drive or any storage app" else "Tap to pick another file", onClick = ::connect) { Chevron() }
    if (uri != null) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("off" to "Off", "day" to "Daily", "week" to "Weekly", "month" to "Monthly").forEach { (id, label) -> Choice(s.cloudBackupEvery == id, label) { vm.set { it.copy(cloudBackupEvery = id) } } }
        }
        BackupNowRow(vm, s, where)
        SRow(Sym.CloudOff, "Disconnect", "Stop backing up. The file in your cloud stays.", onClick = {
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
    val last = if (s.lastCloudBackup == 0L) "Never backed up" else "Last backup " + android.text.format.DateUtils.getRelativeTimeSpanString(s.lastCloudBackup)
    val caption = when (p) { BackupPhase.WORKING -> "Sending your identity to ${where ?: "your cloud"}..."; BackupPhase.DONE -> "Backed up just now"; BackupPhase.FAILED -> "Backup failed, tap to try again"; else -> last }
    val container by animateColorAsState(if (p == BackupPhase.DONE) androidx.compose.ui.graphics.lerp(cs.surfaceContainer, tonal(Safe).first, .6f) else cs.surfaceContainer,
        MaterialTheme.motionScheme.slowEffectsSpec(), label = "row")
    ListItem(
        { Text("Back up now", style = MaterialTheme.typography.titleMedium) },
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
                    BackupPhase.DONE -> Text("Saved", style = MaterialTheme.typography.labelLargeEmphasized, color = tonal(Safe).second)
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
    Section("Palette") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PALETTES.forEach { Swatch(it) }
            s.customPalettes.forEach { cp -> Swatch(customScheme(cp)) { vm.haptic(H.LIGHT); maker = cp; making = true } }
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(60.dp).springClick(MaterialShapes.Cookie9Sided.toShape()) { maker = null; making = true }.background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Sym.Add, "Make a palette", tint = cs.onPrimaryContainer)
                }
                Text("Make", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
        }
        s.customPalettes.firstOrNull { it.id == s.palette && !s.materialYouEnabled }?.let { cp ->
            SRow(Sym.Edit, "Edit ${cp.name}", "Change its colors, style or name. Long-press any of your palettes to edit it.", onClick = { maker = cp; making = true }) { Chevron() }
        }
    }
    if (making) PaletteMakerSheet(vm, maker) { making = false }
}

/** Theme previews drawn from each real scheme; picking one sweeps the change across the screen from the tap. */
@Composable
private fun ThemePicker(vm: MainViewModel, s: Settings) {
    val reveal = LocalThemeReveal.current
    val current = if (s.isOled) "oled" else s.theme
    Section("Theme") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("light" to "Light", "dusk" to "Dusk", "dark" to "Dark", "oled" to "OLED").forEach { (t, label) ->
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
private fun LayoutPicker(vm: MainViewModel, s: Settings) = Section("Store layout") {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainer).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("modern" to "Modern", "classic" to "Classic").forEach { (id, label) ->
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
    Toggle(Sym.Description, "About this app", "Classic rows preview each app's description, tap See more to read it all", s.showAppAbout) { vm.set { it.copy(showAppAbout = !it.showAppAbout) } }
}

/** Floating dock size, previewed live at the chosen scale. */
@Composable
private fun DockSize(vm: MainViewModel, s: Settings) = Section("Dock size") {
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
        Row { Text("Compact", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium); Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLargeEmphasized); Text("Large", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.labelMedium) }
    }
}

private fun fontLabel(s: Settings) = if (s.appFont.startsWith("custom:")) s.customFonts.firstOrNull { "custom:${it.id}" == s.appFont }?.name ?: "Custom Font"
    else Defaults.FONTS.firstOrNull { it.first == s.appFont }?.second ?: "Space Grotesk"

@Composable
private fun DevConfigEditor(vm: MainViewModel, s: Settings) = Section("Developer Config Source") {
    val url = s.devConfigUrl.trim(); val loading = vm.devStatus == "loading"
    Toggle(Sym.Science, "Enable Dev Config", "Load an isolated config.json URL instead of the production config", s.devConfigEnabled) {
        vm.set { it.copy(devConfigEnabled = !it.devConfigEnabled) }
    }
    Column(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(s.devConfigUrl, { v -> vm.set { it.copy(devConfigUrl = v) } }, Modifier.fillMaxWidth(), label = { Text("Dev config URL") },
            placeholder = { Text("https://example.com/config.json") }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(vm::reload, modifier = Modifier.weight(1f), enabled = s.devConfigEnabled && url.isNotEmpty() && !loading, shapes = ButtonDefaults.shapes()) {
                Text(if (loading) "Reloading..." else "Reload Dev Config")
            }
            OutlinedButton({ vm.set { it.copy(devConfigEnabled = false, devConfigUrl = "") }; vm.devStatus = "idle"; vm.devError = null; vm.reload() }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                Text("Disable and Clear")
            }
        }
        Text(if (s.devConfigEnabled) "Status: ${when (vm.devStatus) { "loading" -> "loading"; "loaded" -> "loaded"; "error" -> "failed"; else -> "idle" }}" else "Status: production mode",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        if (s.devConfigEnabled && url.isEmpty()) Text("Enter a config URL to fully isolate testing from production config.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.devConfigEnabled && vm.devStatus == "loaded") Text("Last loaded: $url", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.devError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FontSheet(vm: MainViewModel, s: Settings, error: String?, importing: Boolean, onImport: () -> Unit, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    SheetTitle("Fonts", Sym.FontDownload, "Select app typeface")
    LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Aa Bb Gg 123", style = MaterialTheme.typography.displaySmallEmphasized)
                    Text("The quick brown fox jumps over the lazy dog.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        @Composable fun FontRow(key: String, label: String, tag: String?, onDelete: (() -> Unit)? = null) {
            val on = s.appFont == key
            ListItem({ Text(label, fontWeight = if (on) FontWeight.Black else FontWeight.Medium) },
                Modifier.clip(RoundedCornerShape(if (on) 24.dp else 8.dp)).clickable { vm.haptic(H.SELECTION); vm.set { it.copy(appFont = key) } },
                supportingContent = tag?.let { { Text(it) } },
                trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                    onDelete?.let { IconButton(it) { Icon(Sym.DeleteOutline, "Delete $label") } }
                    if (on) Icon(Sym.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                } },
                colors = ListItemDefaults.colors(containerColor = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer))
        }
        items(Defaults.FONTS) { (k, l) -> FontRow(k, l, when (k) { "spaceGrotesk" -> "Default"; "systemDefault" -> "System"; else -> null }) }
        items(s.customFonts, key = { it.id }) { f -> FontRow("custom:${f.id}", f.name, "Custom") { vm.set { it.removeCustomFont(f.id) } } }
        item {
            OutlinedButton(onImport, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !importing, shapes = ButtonDefaults.shapes()) {
                Icon(Sym.Upload, null); Spacer(Modifier.width(8.dp)); Text(if (importing) "Importing..." else "Import Custom Font")
            }
            error?.let { Text(it, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Active: ", style = MaterialTheme.typography.bodySmall); Text(fontLabel(s), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Button(onDismiss, shapes = ButtonDefaults.shapes()) { Text("Done") }
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
    fun enable() { if (enabled) return; enabled = true; vm.set { it.copy(directInstall = true) }; vm.haptic(H.SUCCESS); vm.toast("Installs now skip the 24-hour wait"); close() }
    // Already paired: no setup needed
    LaunchedEffect(Unit) { if (runCatching { WirelessAdb.connect(ctx) }.isSuccess) enable() }
    LaunchedEffect(st.phase) { if (st.phase == WirelessAdb.Phase.READY) enable() }
    // Re-check while the user is away in system Settings
    LaunchedEffect(Unit) { while (true) { delay(1000); wifi = WirelessAdb.onWifi(ctx); dev = WirelessAdb.devOptions(ctx); wl = WirelessAdb.wirelessOn(ctx) } }

    Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text("Skip the 24-hour wait", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineSmallEmphasized)
        Text("One-time setup. Orion installs through Wireless debugging, the ADB route Google leaves open, so apps install right away.",
            Modifier.padding(horizontal = 8.dp).padding(top = 4.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SetupStep(1, "Connect to Wi-Fi", if (wifi) "Connected" else "Wireless debugging only works on Wi-Fi.", wifi)
            SetupStep(2, "Unlock Developer options", if (dev) "Unlocked" else "Tap Build number 7 times in About phone.", dev)
            SetupStep(3, "Turn on Wireless debugging", if (wl) "On" else "Switch it on and tap Allow.", wl)
            SetupStep(4, "Pair Orion", "Tap Pair device with pairing code, then type the code into Orion's notification.", st.phase == WirelessAdb.Phase.READY)
        }
        Text("Orion's notification walks you through each step and moves on by itself.", Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { WirelessAdb.startSetup(ctx) }, modifier = Modifier.fillMaxWidth(), enabled = wifi && st.phase != WirelessAdb.Phase.PAIRING, shapes = ButtonDefaults.shapes()) {
            Text(if (st.phase == WirelessAdb.Phase.IDLE) "Start setup" else "Continue")
        }
        if (st.phase == WirelessAdb.Phase.PAIRING || st.phase == WirelessAdb.Phase.FAILED) Row(Modifier.padding(8.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (st.phase == WirelessAdb.Phase.PAIRING) { LoadingIndicator(Modifier.size(32.dp)); Spacer(Modifier.width(8.dp)) }
            Text(st.msg, color = if (st.phase == WirelessAdb.Phase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
            label = { Text("Or type the code here (split screen)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = { TextButton({ WirelessAdb.pairAsync(ctx, code) }, enabled = code.length == 6 && st.phase != WirelessAdb.Phase.PAIRING) { Text("Pair") } })
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
        Text("Choose Installer", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.headlineSmallEmphasized)
        if (step == "specific" && s.hiddenInstallers.isNotEmpty()) IconButton({ vm.set { it.copy(hiddenInstallers = emptyList()) } }) { Icon(Sym.RestartAlt, "Reset Hidden Installers") }
    }
    AnimatedContent(step, label = "step") { st ->
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (st == "mode") {
                Selectable(s.installerPreference == "system", "System Installer") { vm.set { it.withInstaller("system") }; onDismiss() }
                Selectable(s.installerPreference == "chooser", "Ask Every Time") { vm.set { it.withInstaller("chooser") }; onDismiss() }
                Selectable(s.installerPreference == "package", "Specific Installer", if (s.installerPreference == "package") s.installerLabel.ifEmpty { "Selected" } else null) { setStep("specific") }
            } else when {
                loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { LoadingIndicator(); Text("Loading...", Modifier.padding(start = 12.dp)) }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                list.isEmpty() -> EmptyState(Sym.Android, "No installers found.")
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
    SwipeRow(RoundedCornerShape(20.dp), right = SwipeAction(Sym.VisibilityOff, "Hide installer", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, removes = true, run = onRemove), hint = hint) {
        ListItem({ Text(ins.label, fontWeight = FontWeight.Bold) }, Modifier.clickable(onClick = onSelect),
            supportingContent = { Text(ins.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = { icon?.let { Image(it, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))) } ?: TonalIcon(Sym.Android, MaterialTheme.colorScheme.primary) },
            trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) {
                if (ins.isSystem) Text("System", Modifier.clip(Pill).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                if (selected) Icon(Sym.CheckCircle, null, Modifier.padding(start = 8.dp), tint = MaterialTheme.colorScheme.primary)
            } },
            colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}

@Composable
private fun ScannerSheet(apps: List<AppItem>, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }; var done by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }; var total by remember { mutableIntStateOf(apps.size) }
    var results by remember { mutableStateOf<List<MissionResult>>(emptyList()) }
    fun start() = scope.launch {
        scanning = true; progress = 0; done = false; results = emptyList()
        results = runCatalogDiagnostic(apps) { p, t -> progress = p; total = t }
        scanning = false; done = true
    }
    SheetTitle("Mission Scanner (Admin)", Sym.Radar)
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        when {
            scanning -> {
                Text("Scanning Catalog...", style = MaterialTheme.typography.titleMedium)
                LinearWavyProgressIndicator({ if (total == 0) 0f else progress / total.toFloat() }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
                Text("$progress / $total apps checked", style = MaterialTheme.typography.bodySmall)
            }
            done -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Scan Complete", style = MaterialTheme.typography.titleMedium)
                        Text("Found ${results.size} apps with issues out of $total.", style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(::start, shapes = ButtonDefaults.shapes()) { Text("Scan Again") }
                }
                if (results.isEmpty()) Text("Everything looks perfect!", Modifier.padding(vertical = 24.dp), color = Safe, fontWeight = FontWeight.Bold)
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
                Text("Ready to Scan", style = MaterialTheme.typography.titleMedium)
                Text("This will run a diagnostic on all ${apps.size} apps in your catalog, checking for missing text and sending HTTP HEAD requests to verify URLs.",
                    Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(::start, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shapes = ButtonDefaults.shapes()) { Text("Run Diagnostic Now") }
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
                Text(when { open -> if (on) "In use" else "Tap to use"; s.unlockTarget == c.id -> "${c.progress(s)}/${c.ads} · unlocking"; else -> "${c.progress(s)}/${c.ads} ads" }, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IconPicker(vm: MainViewModel, s: Settings) = Section("App icon") {
    CosmeticRow(APP_ICONS, s.appIcon, 92.dp, { vm.pickCosmetic(it, true) }) { c ->
        val (bg, fg) = ICON_ART.getValue(c.id)
        Box(Modifier.size(56.dp).clip(CircleShape)) {
            Image(painterResource(bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Image(painterResource(fg), c.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
    InfoCard("Tap Neon or Gold, then watch ads in Fuel the Code: each one counts toward it. Unlock both for the Icon Collector badge.", icon = Sym.Palette)
}

/** Each dock drawn small, in its own colors and selected-item style. */
@Composable
private fun DockPicker(vm: MainViewModel, s: Settings) = Section("Dock style") {
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
    InfoCard("Tap Expressive or Shape shift, then watch ads in Fuel the Code: each one counts toward it. Unlock both for the Dock Master badge.", icon = Sym.Palette)
}
