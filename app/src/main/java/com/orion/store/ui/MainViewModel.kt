package com.orion.store.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.orion.store.MainActivity
import com.orion.store.AutoUpdate
import com.orion.store.data.*
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import com.orion.store.platform.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import java.io.File

const val STORE_VERSION = com.orion.store.BuildConfig.VERSION_NAME // single source: versionName in app/build.gradle.kts

enum class H { LIGHT, MEDIUM, HEAVY, SELECTION, SUCCESS, WARNING, ERROR }
private val HAPTICS = mapOf(
    H.SELECTION to (longArrayOf(0, 30) to intArrayOf(0, 120)), H.LIGHT to (longArrayOf(0, 50) to intArrayOf(0, 110)),
    H.MEDIUM to (longArrayOf(0, 43) to intArrayOf(0, 180)), H.HEAVY to (longArrayOf(0, 60) to intArrayOf(0, 255)),
    H.SUCCESS to (longArrayOf(0, 35, 65, 21) to intArrayOf(0, 250, 0, 180)), H.WARNING to (longArrayOf(0, 30, 40, 30) to intArrayOf(0, 255, 0, 255)),
    H.ERROR to (longArrayOf(0, 27, 45, 50) to intArrayOf(0, 120, 0, 250)),
)
enum class Overlay { SETTINGS, RELEASE_NOTES, FAQ, SUBMISSION, AD_DONATION, CUSTOM_BUNDLE, PROFILE_STATS, NOTICE, TUTORIAL, GUIDE, AI }

val FORCED_GUARD = setOf("scanning", "scan_complete", "installing", "install_failed", "export_ready", "byok_required", "ready_to_scan_or_install")
val FORCED_TEST_PROGRESS = mapOf("checking" to 8, "downloading" to 64, "download_failed" to 32, "scanning" to 72, "installing" to 96)
val FORCED_TEST_TEXT = mapOf(
    "checking" to "正在检查 Orion Store 的最新版本。", "downloading" to "正在从更新源下载 OrionStore_9.9.9.apk。",
    "download_failed" to "下载中断。请重试传输，或稍后保存。", "ready_to_scan_or_install" to "更新包已就绪，请选择下一步。.",
    "byok_required" to "A VirusTotal API key is required before you can scan.", "scanning" to "Scanning the APK before install.",
)

/** Process-wide persisted stores (zustand persist equivalents). */
object Store {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var settings: Persisted<Settings>; lateinit var data: Persisted<DataState>
    fun init(ctx: Context) {
        if (::settings.isInitialized) return
        settings = Persisted(File(ctx.filesDir, "settings.json"), Settings.serializer(), Settings(), scope)
        data = Persisted(File(ctx.filesDir, "data.json"), DataState.serializer(), DataState(), scope)
    }
}

fun Tab.key() = if (platform != null) name.lowercase() else "android"
fun Tab.targetPlatform() = platform ?: Platform.ANDROID
private val PLATFORM_TABS = Tab.entries.filter { it.platform != null }

/** Port of App.tsx orchestration: catalog load, downloads, installs, verification, forced update, toasts, startup janitor. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    val tracker = AppTracker(app)
    private val repo = Repository(app)
    init { Store.init(app); Downloads.init(app, Store.settings.value.forcedStoreUpdate?.downloadFileName) }
    val settings = Store.settings.flow
    val data = Store.data.flow
    val s: Settings get() = Store.settings.value
    val d: DataState get() = Store.data.value
    fun set(fn: (Settings) -> Settings) = Store.settings.update(fn)
    fun upd(fn: (DataState) -> DataState) = Store.data.update(fn)
    private fun now() = System.currentTimeMillis()

    /** Home tab: TV devices open on the TV catalog, unless it is hidden. */
    val homeTab get() = if (Device.isTv && "tv" !in s.hiddenTabs) Tab.TV else Tab.ANDROID
    var tab by mutableStateOf(if (Device.isTv && "tv" !in Store.settings.value.hiddenTabs) Tab.TV else Tab.ANDROID)
    var config by mutableStateOf<StoreConfig?>(null)
    var mirrorSource by mutableStateOf("")
    var devStatus by mutableStateOf("idle"); var devError by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true); var refreshing by mutableStateOf(false)
    /** Every platform tab is precomputed off the main thread, so switching tabs only swaps references. */
    private var visibleBy by mutableStateOf<Map<String, List<AppItem>>>(emptyMap())
    private var fronts by mutableStateOf<Map<String, Pair<List<StoreCollection>, List<String>>>>(emptyMap())
    val visible get() = visibleBy[tab.targetPlatform()].orEmpty()
    val collections get() = fronts[tab.targetPlatform()]?.first.orEmpty()
    val filterCats get() = fronts[tab.targetPlatform()]?.second.orEmpty()
    /** Both the list and the storefront sections must be in, or modern home shows the quick updates lane alone before the rest lands. */
    val tabReady get() = tab.targetPlatform().let { it in visibleBy && it in fronts }
    var lookup by mutableStateOf<Map<String, AppItem>>(emptyMap())
    var updates by mutableStateOf<List<AppItem>>(emptyList())
    var selectedApp by mutableStateOf<AppItem?>(null); var selectedBundle by mutableStateOf<BundleItem?>(null)
    var vtTarget by mutableStateOf<AppItem?>(null); var changeRequest by mutableStateOf(false)
    val overlays = mutableStateListOf<Overlay>()
    var settingsMenu by mutableStateOf("none")
    var profileView by mutableStateOf("profile"); var badgeIndex by mutableStateOf<Int?>(null)
    var installingId by mutableStateOf<String?>(null); var scanningId by mutableStateOf<String?>(null)
    var expandedView by mutableStateOf<String?>(null); var expandedApps by mutableStateOf<List<AppItem>>(emptyList())
    private var returnToExpanded: String? = null
    var showAllSorted by mutableStateOf(false); var viewAllApps by mutableStateOf<List<AppItem>?>(null)
    var cleanupPill by mutableStateOf(false)
    var bypassMaintenance by mutableStateOf(false)
    var startupReady by mutableStateOf(false)
    // Declared before init: on a fresh install the migration callback fires synchronously from init
    var migratedFromWeb by mutableStateOf(false)
    val ai = AiSession()
    var submissionCooldown by mutableStateOf<String?>(null)
    val snackbar = SnackbarHostState()

    var fStatus by mutableStateOf("inactive"); var fText by mutableStateOf(""); var fError by mutableStateOf("")
    var fProgress by mutableIntStateOf(0); var fExportPath by mutableStateOf(""); var fBypassed by mutableStateOf(false)
    var fResult by mutableStateOf<VtResult?>(null); var storeUpdateAvailable by mutableStateOf(false)
    var forcedTest by mutableStateOf<String?>(null)
    val forcedVt = VtScan(tracker, "__orion_store_self_update__", "Orion Store", app.packageName, s.virusTotalApiKey)
    private var forcedUrl = ""; private var pendingScanAfterKey = false; private var forcedJob: Job? = null

    private val expectations = mutableMapOf<String, InstallExpectation>()
    private val initializing = mutableSetOf<String>()
    private var pendingRetry: Pair<AppItem, String>? = null
    private var pendingDeepLink: (() -> Unit)? = null
    private var loadGen = 0; private var syncJob: Job? = null; private var cleaning = false
    private var devClicks = 0; private var eggClicks = 0

    fun allApps() = d.apps + d.importedApps
    val tabState get() = d.tab(tab.key())
    val effectiveSort get() = displaySort(s.storeLayout, tabState)

    init {
        // 2.0: carry the web build's profile, badges and settings over, once
        if (!s.webMigrated) WebMigration.read(ctx, ::migrateWeb)
        // 2.2.5: the dock starts compact. A dock still on the old 100% default moves over once; a size picked after that stays put.
        if (flag("dock") == null) set { st -> st.copy(bottomNavScale = if (st.bottomNavScale == 1f) .85f else st.bottomNavScale, seenFlags = st.seenFlags + "dock=compact") }
        // 2.2.5: "About this app" previews start off; rows that never had the switch touched follow the new default once
        if (flag("about") == null) set { st -> st.copy(showAppAbout = false, seenFlags = st.seenFlags + "about=off") }
        // Tier badges follow the shared level, whichever XP source pushed it over
        viewModelScope.launch {
            combine(settings, data) { st, dt -> Xp.level(Xp.total(st, dt.appLibrary.size)) }.distinctUntilChanged().collect { lvl ->
                TIER_BADGES.filter { lvl >= it.first && it.second !in s.unlockedBadges }.forEach { (_, b) -> set { it.unlockBadge(b) } }
            }
        }
        viewModelScope.launch { settings.map { it.rootMode }.distinctUntilChanged().collect { tracker.root = it } }
        viewModelScope.launch { settings.map { it.autoUpdateEnabled to it.wifiOnly }.distinctUntilChanged().collect { AutoUpdate.schedule(ctx, s) } }
        upd { st -> st.persistedDownloads.entries.fold(st.copy(activeDownloads = st.persistedDownloads)) { acc, (id, v) ->
            val f = v.substringAfter('|'); if (Downloads.status(f).status == DlStatus.SUCCESSFUL) acc.completeDownload(id, f, now()) else acc.cancelDownload(id) } }
        viewModelScope.launch { Downloads.state.collect(::onDownloads) }
        val catalog = data.map { it.apps + it.importedApps }.distinctUntilChanged()
        viewModelScope.launch {
            combine(catalog, snapshotFlow { config?.storefrontModules }) { all, mods -> all to mods.orEmpty() }.collectLatest { (all, mods) ->
                if (all.isEmpty()) { fronts = emptyMap(); return@collectLatest }
                val (f, l) = withContext(Dispatchers.Default) {
                    PLATFORM_TABS.associate { t -> t.targetPlatform().let { p -> p to (CatalogProcessor.storefront(all, p, mods) to availableFilterCategories(all.filter { it.platform == p })) } } to all.associateBy { it.id }
                }
                fronts = f; lookup = l
                if (!warmed && f.isNotEmpty()) { warmed = true; warmStorefront(f) }
                pendingDeepLink?.takeIf { lookup.isNotEmpty() }?.let { pendingDeepLink = null; it() }
            }
        }
        viewModelScope.launch {
            combine(catalog, data.map { it.tabs }.distinctUntilChanged(), settings.map { it.storeLayout }.distinctUntilChanged(), ::Triple).collectLatest { (all, tabs, layout) ->
                // An empty catalog is "not loaded yet": leaving the map empty keeps tabReady false, so tabs show the skeleton instead of an empty state.
                if (all.isEmpty()) { visibleBy = emptyMap(); return@collectLatest }
                if (tabs.values.any { it.query.isNotEmpty() }) delay(150)
                visibleBy = withContext(Dispatchers.Default) {
                    PLATFORM_TABS.associate { t -> val ts = tabs[t.key()] ?: TabViewState(); val p = t.targetPlatform(); p to CatalogProcessor.search(ts.query, ts.category, displaySort(layout, ts), all.filter { it.platform == p }) }
                }
            }
        }
        viewModelScope.launch {
            combine(settings, data.map { it.apps + it.importedApps }.distinctUntilChanged()) { st, all -> availableUpdates(all, st, now()) }.distinctUntilChanged().collect { u ->
                updates = u
                if (s.autoUpdateEnabled) u.filter { it.installsHere() && it.id !in d.activeDownloads && it.id !in d.readyToInstall && it.id !in initializing }.forEach { download(it, auto = true) }
            }
        }
        viewModelScope.launch {
            data.map { it.pendingCleanup.size }.distinctUntilChanged().collectLatest { n -> cleanupPill = n > 0; if (n > 0 && s.deleteApk) { delay(10_000); cleanupPill = false } }
        }
        viewModelScope.launch { while (true) { submissionCooldown = cooldown(); delay(60_000) } }
        popups()
        loadApps(false)
        startup()
    }

    // region feedback
    private val vib: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(android.os.VibratorManager::class.java).defaultVibrator else ctx.getSystemService(Vibrator::class.java)
    }
    /** Explicit-amplitude waveforms, the same ones the web build played through Capacitor Haptics. Predefined effects and
     *  view haptic constants are skipped: some actuators and skins (and a disabled system touch-feedback setting) silence them. */
    fun haptic(h: H) {
        if (!s.hapticEnabled) return
        val (t, a) = HAPTICS.getValue(h)
        runCatching { if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(t, a, -1)) else @Suppress("DEPRECATION") vib.vibrate(t.sum()) }
    }
    fun hapticStr(k: String) = haptic(when (k) { "success" -> H.SUCCESS; "warning" -> H.WARNING; "error" -> H.ERROR; else -> H.LIGHT })

    private var toastJob: Job? = null
    fun toast(msg: String, ms: Long = 3000, action: String? = null, onAction: () -> Unit = {}) {
        toastJob?.cancel(); snackbar.currentSnackbarData?.dismiss()
        toastJob = viewModelScope.launch {
            withTimeoutOrNull(ms) { if (snackbar.showSnackbar(msg, action, withDismissAction = action == null, duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed) onAction() }
            snackbar.currentSnackbarData?.dismiss()
        }
    }
    fun error(msg: String) = toast(msg, 6000)
    fun installToast(app: AppItem, file: String) = toast("${app.name} 已就绪，可安装", 6000, "安装") { installFile(app, file) }
    // endregion

    // region startup
    private fun startup() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val nm = NotificationManagerCompat.from(ctx)
            nm.createNotificationChannel(NotificationChannelCompat.Builder("orion_updates", NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("Orion 更新").build())
            nm.createNotificationChannel(NotificationChannelCompat.Builder("orion_cleanup", NotificationManagerCompat.IMPORTANCE_HIGH).setName("清理").build())
            DownloadService.ensureChannel(ctx)
        }
        delay(1800); startupReady = true
        delay(1400)
        val now = now(); val del = d.pendingCleanup.filter { (_, e) -> s.deleteApk || now - e.timestamp > ONE_WEEK_MS }
        if (del.isEmpty()) return@launch
        val n = del.count { (id, e) -> purgeApk(id, e.fileName) }
        if (n > 0) queue { toast("清理工已清理 $n 个文件") }
    }

    private fun migrateWeb(st: String?, dt: String?) {
        val keep = s
        val imported = st?.let { webSettingsImport(it, keep) }?.takeIf { it !== keep }
        // forcedStoreUpdate points at a web-era APK; the welcome flag belongs to this build
        set { (imported ?: it).copy(webMigrated = true, seenNativeWelcome = it.seenNativeWelcome, forcedStoreUpdate = it.forcedStoreUpdate) }
        dt?.let { runCatching { json.parseToJsonElement(it).jsonObject["state"]?.jsonObject }.getOrNull() }?.let { d ->
            val fav = runCatching { json.decodeFromJsonElement<List<String>>(d["favorites"]!!) }.getOrNull()
            val lib = runCatching { json.decodeFromJsonElement<Map<String, LibraryEntry>>(d["appLibrary"]!!) }.getOrNull()
            upd { it.copy(favorites = fav ?: it.favorites, appLibrary = lib.orEmpty() + it.appLibrary) }
        }
        migratedFromWeb = imported != null
    }

    // Automatic popups (welcome, background update report, notice, Try New UI, tour, announcement, toasts) take turns:
    // each waits until nothing else holds the screen, shows, and holds its turn until the user closes it. One at a time, never on
    // the forced update or maintenance screens, never over an open app, sheet or Settings.
    var welcomeOpen by mutableStateOf(false); var announceNow by mutableStateOf(false)
    var tour by mutableStateOf(false); var donateOpen by mutableStateOf(false); var guidePrompt by mutableStateOf(false)
    private val turns = kotlinx.coroutines.sync.Mutex()
    private val screenFree get() = startupReady && !forcedBlocking && forcedTest == null && !((config?.maintenanceMode == true || s.localMaintenanceMode) && !bypassMaintenance) &&
        overlays.isEmpty() && selectedApp == null && selectedBundle == null && vtTarget == null && autoReport == null && !welcomeOpen && !announceNow && !donateOpen && !guidePrompt &&
        snackbar.currentSnackbarData == null
    // Polls every 400 ms: the gate mixes Compose state with the settings StateFlow. One boolean read, so it costs nothing
    private suspend fun untilFree() { while (!screenFree) delay(400) }
    /** Shows [show] on a free screen after a short breather, then waits for it to be closed before the next popup gets a turn. */
    private suspend fun takeTurn(show: () -> Unit) = turns.withLock { untilFree(); delay(1200); untilFree(); show(); delay(500); untilFree() }
    private fun queue(show: () -> Unit) { viewModelScope.launch { takeTurn(show) } }

    private fun popups() = viewModelScope.launch {
        while (!startupReady) delay(200)
        if (!s.seenNativeWelcome) takeTurn { welcomeOpen = true }
        autoReportTurn()
        withTimeoutOrNull(15_000) { while (loading || config == null) delay(300) }
        config?.notice?.takeIf { it.show && it.id != flag("notice") }?.let { takeTurn { open(Overlay.NOTICE) } }
        if (d.apps.isNotEmpty() && !s.hasSeenModernUITutorial && s.storeLayout == "classic") takeTurn { tour = false; open(Overlay.TUTORIAL) }
        if (d.apps.isNotEmpty() && flag("tour") == null) takeTurn { tour = true; open(Overlay.TUTORIAL) }
        // 2.2.0 brought Know Orion: every pilot hears about it once
        if (flag("guide") == null) takeTurn { guidePrompt = true }
        // The megaphone lives in the store tabs' search bar, so wait for one of those to be on screen
        if (config?.announcement?.isNotBlank() == true && !announcementDismissed()) { while (tab.platform == null) delay(400); takeTurn { announceNow = true } }
    }

    fun finishWelcome() { set { it.copy(seenNativeWelcome = true) }; welcomeOpen = false }
    fun dismissNotice() { config?.notice?.id?.let { setFlag("notice", it) }; overlays -= Overlay.NOTICE }

    fun flag(k: String) = s.seenFlags.firstOrNull { it.startsWith("$k=") }?.substringAfter('=')
    fun setFlag(k: String, v: String) = set { st -> st.copy(seenFlags = st.seenFlags.filterNot { it.startsWith("$k=") }.toSet() + "$k=$v") }
    fun announcementDismissed() = config?.announcement?.let { flag("announcement") == it.hashCode().toString() } ?: true
    fun dismissAnnouncement() = config?.announcement?.let { setFlag("announcement", it.hashCode().toString()) }

    private fun cooldown(): String? {
        val mins = maxOf(30, 180 - minOf(s.submissionCount * 15, 150))
        val left = s.lastSubmissionTime + mins * 60_000L - now()
        return if (s.lastSubmissionTime == 0L || left <= 0) null else "${left / 3_600_000} 小时 ${(left / 60_000) % 60} 分"
    }
    fun refreshCooldown() { submissionCooldown = cooldown() }

    fun onResume() {
        pendingRetry?.let { (a, f) -> if (tracker.canRequestInstalls()) { pendingRetry = null; viewModelScope.launch { delay(500); installFile(a, f) } } }
        if (installingId == null && scanningId == null) scheduleInstalledSync()
        announceQuest(); cloudBackup(); reportAutoUpdates()
    }

    /** What the background updater did while Orion was closed: a quiet toast when all went well, a report when something needs the user. */
    var autoReport by mutableStateOf<List<AutoResult>?>(null)
    private var reporting = false
    private fun reportAutoUpdates() { if (startupReady) viewModelScope.launch { autoReportTurn() } }
    private suspend fun autoReportTurn() {
        if (reporting || s.autoLog.isEmpty()) return
        reporting = true
        takeTurn {
            val log = s.autoLog
            if (log.any { !it.ok }) autoReport = log
            else if (log.isNotEmpty()) { set { it.copy(autoLog = emptyList()) }; toast(if (log.size == 1) "${log[0].name} 已在后台完成更新" else "${log.size} 个应用已在后台完成更新") }
        }
        reporting = false
    }
    fun dismissAutoReport() { autoReport = null; set { it.copy(autoLog = emptyList()) } }
    // endregion

    // region root + cloud backup
    /** Writes the identity to the user's cloud document once it is due. The identity only changes while Orion runs, so checking on every resume never misses a change. */
    private var cloudWarned = false
    fun cloudBackup(force: Boolean = false) = viewModelScope.launch {
        val st = s; val every = mapOf("day" to 1L, "week" to 7L, "month" to 30L)[st.cloudBackupEvery]?.times(86_400_000L)
        if (st.cloudBackupUri.isEmpty() || !force && (every == null || now() - st.lastCloudBackup < every)) return@launch
        runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(st.cloudBackupUri.toUri(), "wt")!!.use { it.write(identityExport(st, d.favorites, now()).toByteArray()) } } }
            .onSuccess { set { it.copy(lastCloudBackup = now()) }; if (force) { toast("资料已备份"); haptic(H.SUCCESS) } }
            .onFailure { if (force) error("云备份失败。请在 设置 › 备份 中重新选择备份文件。") else if (!cloudWarned) { cloudWarned = true; queue { error("云备份失败。请在 设置 › 备份 中重新选择备份文件。") } } }
    }

    /** Root: makes Orion the installer of catalog apps installed from elsewhere, so their updates land silently here. */
    fun adoptApps() = viewModelScope.launch {
        val owned = s.resolvedPackageNames.filterKeys { it in s.installedVersions }.values
        val foreign = withContext(Dispatchers.IO) { tracker.foreignInstalls(owned) }
        if (foreign.isEmpty()) { toast("Orion 已在更新你目录里的所有应用"); return@launch }
        toast("正在接管 ${foreign.size} 个应用…", 60_000)
        val failed = foreign.keys.count { runCatching { tracker.adopt(it) }.isFailure }
        haptic(if (failed == 0) H.SUCCESS else H.WARNING)
        toast(if (failed == 0) "Orion 现在多更新 ${foreign.size} 个应用" else "已接管 ${foreign.size - failed} 个，$failed 个失败（签名或分包不匹配）", 6000)
    }

    fun trimCaches() = viewModelScope.launch {
        runCatching { tracker.trimCaches() }.onSuccess { toast("应用缓存已清理"); haptic(H.SUCCESS) }.onFailure { error(it.message ?: "清理失败") }
    }
    // endregion

    // region catalog
    fun loadApps(manual: Boolean) = viewModelScope.launch {
        if (manual) { if (refreshing) return@launch; refreshing = true; haptic(H.LIGHT) }
        if (d.apps.isEmpty()) loading = true
        val gen = ++loadGen; val st = s; var primary = false
        if (!manual && d.apps.isEmpty()) launch {
            val c = withContext(Dispatchers.IO) { repo.cached() }
            if (primary || gen != loadGen) return@launch
            if (c != null && c.apps.isNotEmpty()) {
                if (config == null) config = c.config
                if (mirrorSource.isEmpty()) mirrorSource = c.source
                upd { it.copy(apps = c.apps) }; loading = false; scheduleInstalledSync()
            } else if ((st.devConfigEnabled || st.useRemoteJson) && st.loadLocalData) {
                val e = withContext(Dispatchers.IO) { runCatching { repo.embeddedApps() }.getOrNull() } ?: return@launch
                if (!primary && gen == loadGen) process(e, null)
            }
        }
        try {
            devStatus = if (st.devConfigEnabled) "loading" else "idle"; devError = null
            val r = repo.loadCatalog(st, manual)
            if (r.config != null) { config = r.config; evaluateStoreUpdate(r.config); if (st.devConfigEnabled) devStatus = "loaded" } else clearConfigState()
            r.devError?.let { devStatus = "error"; devError = it }
            mirrorSource = r.source; primary = true
            process(r.rawApps, r.mirror)
            if (r.config != null) CatalogCache(d.apps, r.config, r.source).let { c -> launch(Dispatchers.IO) { runCatching { repo.saveCache(c) } } }
        } catch (e: Exception) {
            if (d.apps.isEmpty()) { error("应用加载失败"); clearConfigState(); if (st.devConfigEnabled) { devStatus = "error"; devError = e.message ?: "开发者配置加载失败。" }; haptic(H.ERROR) }
            loading = false; refreshing = false
        }
    }

    private suspend fun process(raw: JsonElement?, mirror: JsonElement?) {
        val m = withContext(Dispatchers.Default) { CatalogProcessor.parseMirror(mirror) }
        // If the mirror fetch failed (empty) but we already have processed apps with
        // download info, keep them. Overwriting with unprocessed data would mark the
        // entire catalog unavailable and poison the disk cache.
        if (m.isEmpty() && d.apps.any { it.availableVersions.isNotEmpty() }) {
            loading = false; refreshing = false; scheduleInstalledSync()
            return
        }
        val imported = d.importedApps
        val (apps, imp) = withContext(Dispatchers.Default) { CatalogProcessor.process(raw, m) to imported.map { CatalogProcessor.processItem(it, m) } }
        upd { it.copy(apps = apps, importedApps = imp) }
        loading = false; refreshing = false; scheduleInstalledSync()
    }

    fun reload() { if (!refreshing) loadApps(true) }
    // endregion

    // region forced store update
    private fun clearSnap() { if (s.forcedStoreUpdate != null) set { it.copy(forcedStoreUpdate = null) } }
    private fun snap(fn: (ForcedSnapshot) -> ForcedSnapshot) = set { st -> st.copy(forcedStoreUpdate = st.forcedStoreUpdate?.let(fn)) }

    private fun clearConfigState() {
        if (fStatus in FORCED_GUARD) return
        config = null; storeUpdateAvailable = false; forcedUrl = ""; fStatus = "inactive"; fText = ""; fExportPath = ""; fResult = null; clearSnap()
    }

    private fun evaluateStoreUpdate(c: StoreConfig) {
        if (fStatus in FORCED_GUARD) { c.storeDownloadUrl?.ifEmpty { null }?.let { forcedUrl = it }; return }
        storeUpdateAvailable = false; forcedUrl = ""
        val latest = c.latestStoreVersion?.ifEmpty { null } ?: c.minStoreVersion?.ifEmpty { null }
        if (latest == null || !isStoreUpdateAvailable(STORE_VERSION, latest)) { fBypassed = false; fStatus = "inactive"; fText = ""; clearSnap(); return }
        storeUpdateAvailable = true; forcedUrl = c.storeDownloadUrl.orEmpty()
        if (forcedUrl.isEmpty()) { fStatus = "inactive"; clearSnap(); return }
        val cur = s.forcedStoreUpdate
        if (canResumeForcedUpdate(cur, STORE_VERSION, latest, c.minStoreVersion) && cur != null) {
            if (cur.downloadCompleted && Downloads.file(cur.downloadFileName)?.exists() != true) {
                fStatus = "download_failed"; fError = "找不到之前下载的文件，请重试。"; return
            }
            fStatus = if (cur.status == "export_ready") "export_ready".also { fExportPath = "" } else "ready_to_scan_or_install"
            fText = "Orion 更新包已经下载好了。"; return
        }
        set { it.copy(forcedStoreUpdate = ForcedSnapshot(targetVersion = latest, downloadFileName = forcedStoreFileName(latest), status = "downloading", downloadCompleted = false, exported = false)) }
        fStatus = "downloading"; fText = "正在下载最新版 Orion…"; forcedDownload()
    }

    private fun forcedDownload() {
        forcedJob?.cancel()
        forcedJob = viewModelScope.launch {
            val name = s.forcedStoreUpdate?.downloadFileName ?: return@launch
            if (forcedUrl.isEmpty()) { fStatus = "download_failed"; fError = "未找到此更新的下载地址。"; return@launch }
            fError = ""; fProgress = 0
            if (runCatching { Downloads.start(forcedUrl, name) }.isFailure) { fStatus = "download_failed"; fError = "下载失败。请检查网络后重试。"; return@launch }
            while (true) {
                delay(800)
                val dl = Downloads.status(name); fProgress = dl.progress
                if (dl.status == DlStatus.RUNNING) continue
                Downloads.forget(name)
                if (dl.status == DlStatus.SUCCESSFUL) {
                    snap { it.copy(status = "ready_to_scan_or_install", downloadCompleted = true) }
                    fStatus = "ready_to_scan_or_install"; fText = "下载完成。"; fProgress = 100; haptic(H.SUCCESS)
                } else { fStatus = "download_failed"; fError = "下载失败。请检查网络后重试。"; haptic(H.ERROR) }
                return@launch
            }
        }
    }

    private fun forcedSnapshot(): ForcedSnapshot? = s.forcedStoreUpdate ?: (config?.latestStoreVersion?.ifEmpty { null } ?: config?.minStoreVersion?.ifEmpty { null })?.let { v ->
        ForcedSnapshot(targetVersion = v, downloadFileName = forcedStoreFileName(v), status = fStatus, downloadCompleted = Downloads.file(forcedStoreFileName(v))?.exists() == true, exported = false)
            .also { sn -> set { it.copy(forcedStoreUpdate = sn) } }
    }

    fun forcedRetryDownload() {
        fError = ""; fProgress = 0; fResult = null
        snap { it.copy(status = "downloading", downloadCompleted = false) }
        fStatus = "downloading"; fText = "正在重试下载…"; forcedDownload()
    }

    fun forcedInstall() = viewModelScope.launch {
        val sn = forcedSnapshot() ?: run { fError = "Update package metadata is missing. Retry the download, then install again."; return@launch }
        fError = ""; fText = "正在打开安装器…"
        if (!tracker.canRequestInstalls()) { fError = "请先授予安装权限，然后再点一次“立即安装”。"; runCatching { ctx.startActivity(tracker.installSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; return@launch }
        fStatus = "installing"; snap { it.copy(status = "installing") }
        try {
            tracker.install(sn.downloadFileName, s.installerPreference, s.installerPackage)
            runCatching { tracker.deleteFile(sn.downloadFileName) }
            snap { it.copy(status = "installed") }; fStatus = "installed"; haptic(H.SUCCESS)
        } catch (e: Exception) {
            fStatus = "install_failed"; snap { it.copy(status = "install_failed") }
            fError = if (e.message?.contains("CANCELED") == true) "安装已取消。APK 已保存，可稍后手动安装。" else "安装失败。APK 已保存，可稍后手动安装。"
            haptic(H.ERROR)
        }
    }

    fun forcedExport() = viewModelScope.launch {
        val sn = s.forcedStoreUpdate ?: return@launch
        if (sn.exported) return@launch
        try {
            fExportPath = tracker.exportFile(sn.downloadFileName)
            snap { it.copy(status = "install_failed", exported = true) }
            fError = ""; fText = "已保存到下载文件夹。"; haptic(H.SUCCESS)
        } catch (e: Exception) { fError = e.message ?: "导出失败，请重试。" }
    }

    fun forcedScan(key: String = s.virusTotalApiKey) {
        if (key.isBlank()) { pendingScanAfterKey = true; fStatus = "byok_required"; fText = "在下方填入你的 VirusTotal API 密钥，然后再扫描更新。"; return }
        pendingScanAfterKey = false
        val sn = forcedSnapshot()
        val target = if (sn?.downloadCompleted == true) ScanTarget(ScanTargetType.DOWNLOADED_APK, "已下载的更新", sn.downloadFileName) else ScanTarget(ScanTargetType.REMOTE_APK, "更新包", forcedUrl)
        fStatus = "scanning"; fText = "正在用 VirusTotal 扫描 APK。"; fError = ""
        viewModelScope.launch {
            val r = forcedVt.start(target, key, ::hapticStr)
            if (r != null) { fResult = r; fStatus = "scan_complete"; fText = "扫描完成。随时可以安装。"; snap { it.copy(status = "scan_complete") } }
            else { fStatus = "ready_to_scan_or_install"; fError = forcedVt.error.ifEmpty { "扫描失败，请重试。" } }
        }
    }

    fun forcedSaveKey(key: String) {
        val k = key.trim(); if (k.isEmpty()) return
        set { it.copy(virusTotalApiKey = k) }
        if (fStatus == "byok_required" || pendingScanAfterKey) forcedScan(k)
    }

    fun forcedByok() = openSettings("network")
    fun forcedReport() = (fResult ?: forcedVt.result)?.permalink?.let(tracker::open)
    fun forcedBypass() { fBypassed = true; fStatus = "inactive"; fText = ""; fResult = null }

    val forcedBlocking get() = (fStatus != "inactive" || fResult != null || forcedVt.view == VtView.RESULTS || s.forcedStoreUpdate?.status == "scan_complete") && !fBypassed
    // endregion

    // region downloads
    private fun onWifi() = ctx.getSystemService(ConnectivityManager::class.java).let { cm -> cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }

    fun download(app: AppItem, url: String? = null, auto: Boolean = false) {
        d.readyToInstall[app.id]?.let { if (!auto) installFile(app, it); return }
        if (app.id in d.activeDownloads) { if (!auto) selectedApp = app; return }
        if (!initializing.add(app.id)) return
        if (s.directInstall && WirelessAdb.supported) WirelessAdb.warm(ctx) // connected by the time the APK lands
        try {
            val meta = downloadVersionMeta(app, url, s.streamOf(app.id))
            set { it.copy(appStreams = it.appStreams + (app.id to meta.second)) }
            val target = url ?: app.variants.firstOrNull()?.url ?: app.downloadUrl
            if (target.isEmpty() || target == "#") return
            if (s.wifiOnly && !onWifi()) { if (!auto) { error("下载被阻止：当前为“仅限 WiFi”模式。"); haptic(H.ERROR) }; return }
            val safe = sanitizeDownloadUrl(target)
            if (safe == "#") { if (!auto) { error("下载链接无效或不受支持。"); haptic(H.ERROR) }; return }
            val std = safe.lowercase().let { it.endsWith(".apk") || it.endsWith(".exe") || it.endsWith(".zip") }
            if (!app.installsHere()) { if (!auto && (std || app.platform == Platform.PC || app.platform == Platform.TV)) tracker.open(safe); return }
            if (Downloads.dir().usableSpace < 64L * 1024 * 1024) { if (!auto) error("设备存储空间不足！"); return }
            val file = apkFileName(app, meta.first)
            val id = runCatching { Downloads.start(safe, file) }.getOrElse { if (!auto) tracker.open(safe); return }
            upd { it.startDownload(app.id, id, file) }; haptic(H.MEDIUM)
        } finally { initializing -= app.id }
    }

    private fun onDownloads(m: Map<String, Dl>) {
        d.activeDownloads.forEach { (id, v) ->
            val file = v.substringAfter('|'); val dl = m[file] ?: return@forEach
            when (dl.status) {
                DlStatus.RUNNING -> if (d.downloadProgress[id] != dl.progress.toFloat()) upd { it.updateDownload(id, dl.progress.toFloat(), "RUNNING") }
                DlStatus.SUCCESSFUL -> { Downloads.forget(file); downloadComplete(id, true) }
                DlStatus.FAILED -> {
                    Downloads.forget(file)
                    if (dl.progress > 90 && Downloads.status(file).status == DlStatus.SUCCESSFUL) downloadComplete(id, true)
                    else { downloadComplete(id, false); error("下载失败 - 网络错误") }
                }
            }
        }
    }

    private fun downloadComplete(id: String, ok: Boolean) {
        if (ok) {
            val file = d.activeDownloads[id]?.substringAfter('|') ?: return
            upd { it.completeDownload(id, file, now()) }
            (lookup[id] ?: allApps().firstOrNull { it.id == id })?.let { installToast(it, file); notifyReady(it, file) }
        } else upd { it.failDownload(id) }
        haptic(if (ok) H.SUCCESS else H.ERROR)
    }

    private fun notifyReady(app: AppItem, file: String) = runCatching {
        val i = Intent(ctx, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("appId", app.id).putExtra("fileName", file)
        NotificationManagerCompat.from(ctx).notify(app.id.hashCode(), NotificationCompat.Builder(ctx, "orion_updates").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成").setContentText("${app.name} 已就绪，可安装。").setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, app.id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
    }

    /** Pilots who already beat Chapter 1 hear once that Chapter 2 is out; everyone else walks into it from the sheet. */
    private fun announceQuest() {
        val ch = questChapter(s.adWatchCount); if (ch <= s.questChapterSeen) return
        set { it.copy(questChapterSeen = ch) }
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) { queue { toast("新任务：第二章 · 分叉纪元", 8000, "去玩") { open(Overlay.AD_DONATION) } }; return }
        nm.createNotificationChannel(NotificationChannelCompat.Builder("orion_quest", NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("为代码加油任务").build())
        val i = Intent(ctx, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open", "quest")
        runCatching {
            nm.notify(7320, NotificationCompat.Builder(ctx, "orion_quest").setSmallIcon(com.orion.store.R.drawable.ic_launcher_monochrome)
                .setContentTitle("新任务已解锁：分叉纪元").setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle().bigText("熵的最后一口气把 Orion 撕成了上千个失控分支。《为代码加油》第二章上线：三个新 Boss、三枚新徽章。"))
                .setContentText("《为代码加油》第二章已上线。")
                .setContentIntent(PendingIntent.getActivity(ctx, 7320, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
        }
    }

    fun onNotificationIntent(i: Intent?) {
        if (i?.getStringExtra("open") == "quest") { i.removeExtra("open"); open(Overlay.AD_DONATION); return }
        val id = i?.getStringExtra("appId") ?: return; val file = i.getStringExtra("fileName")
        val app = allApps().firstOrNull { it.id == id } ?: return
        if (file != null && id !in d.pendingCleanup) upd { it.copy(readyToInstall = it.readyToInstall + (id to file)) }
        haptic(H.HEAVY); selectedApp = app
        i.removeExtra("appId")
    }

    fun cancelDownload(appId: String) {
        d.activeDownloads[appId]?.substringAfter('|')?.let { runCatching { Downloads.cancel(it) } }
        upd { it.cancelDownload(appId) }
    }

    fun bundleDownload(b: BundleItem) = viewModelScope.launch {
        val st = s; val removed = st.removedBundleApps[b.id].orEmpty().toSet()
        val apps = (b.apps.filter { it.id !in removed } + st.extraBundleApps[b.id].orEmpty().mapNotNull { lookup[it] }).distinctBy { it.id }
        if (apps.isEmpty()) return@launch
        haptic(H.MEDIUM)
        val eligible = apps.filter { st.installedVersions[it.id].isNullOrEmpty() }
        if (eligible.isEmpty()) { toast("「${b.title}」中的应用都已安装。"); return@launch }
        toast("正在把「${b.title}」的 ${eligible.size} 个应用加入队列…")
        var queued = 0; var skipped = apps.size - eligible.size
        for (a in eligible) {
            if (a.id in d.activeDownloads || a.id in d.readyToInstall || a.id in initializing) { skipped++; continue }
            runCatching { download(a, auto = true); queued++ }
            delay(180)
        }
        if (queued > 0) { toast("已将「${b.title}」的 $queued 个应用加入队列${if (skipped > 0) "（跳过 $skipped 个）" else ""}。"); haptic(H.SUCCESS) }
        else toast("「${b.title}」里的应用都已在队列中或已安装。")
    }

    fun batchCleanup() = viewModelScope.launch {
        if (cleaning) return@launch
        val ids = d.pendingCleanup.keys.toList(); if (ids.isEmpty()) return@launch
        cleaning = true; haptic(H.HEAVY)
        val ok = ids.filter { id -> d.pendingCleanup[id]?.let { purgeApk(id, it.fileName) } == true }
        if (ok.isNotEmpty()) { haptic(H.SUCCESS); toast("已清理 ${ok.size} 个文件") }
        cleaning = false
    }
    // endregion

    // region install + verification
    fun installFile(app: AppItem, file: String, regular: Boolean = false): kotlinx.coroutines.Job = viewModelScope.launch {
        val k = normalizePackageName(app.packageName)
        val meta = versionMetaFromFileName(app, file, s.streamOf(app.id))
        // Direct install runs as the shell user (no 24-hour wait, no "unknown apps" permission). When adbd is unreachable the
        // user picks the regular installer explicitly: silently falling back lands on the very wait this setting exists to skip.
        val direct = !regular && !s.rootMode && s.directInstall && WirelessAdb.supported
        if (direct && !WirelessAdb.connected) {
            installingId = app.id
            val err = runCatching { WirelessAdb.connect(ctx) }.exceptionOrNull()
            if (err != null) { installingId = null; toast(err.message ?: "无法连接无线调试。", 8000, "用安装器") { installFile(app, file, true) }; return@launch }
        }
        val shizuku = !regular && !direct && (s.useShizuku || s.rootMode)
        if (!direct && !shizuku) try {
            if (!tracker.canRequestInstalls()) {
                error("请授予权限后回到这里"); pendingRetry = app to file
                ctx.startActivity(tracker.installSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return@launch
            }
        } catch (e: Exception) { error("权限检查失败，无法安装。"); return@launch }
        haptic(H.HEAVY); installingId = app.id
        if (k.isNotEmpty()) expectations[k] = InstallExpectation(app.id, meta.first)
        if (meta.second.isNotEmpty()) set { it.copy(appStreams = it.appStreams + (app.id to meta.second)) }
        // The APK names its own package: a channel build (com.lagradost.cloudstream3.prerelease) is tracked under that name, not the catalog's
        if (k.isNotEmpty()) withContext(Dispatchers.IO) { runCatching { tracker.archivePackage(tracker.resolve(file)) }.getOrNull() }
            ?.takeIf { it.isNotBlank() && normalizePackageName(it) != k }?.let { p -> set { it.copy(resolvedPackageNames = it.resolvedPackageNames + (app.id to p)) } }
        try {
            when {
                direct -> { WirelessAdb.install(ctx, tracker.resolve(file)); toast("已极速安装") }
                shizuku -> { tracker.installShizuku(file, s.updateLock); toast(if (s.rootMode) "已通过 Root 安装" else "已通过 Shizuku 安装") }
                else -> { tracker.install(file, s.installerPreference, if (s.installerPreference == "package") s.installerPackage else ""); snackbar.currentSnackbarData?.dismiss() }
            }
            beginVerify(app)
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            if (!direct && !shizuku && ("INSTALL_CANCELED" in msg || "INSTALL_FAILED" in msg) && "INSTALL_FAILED_UPDATE_INCOMPATIBLE" !in msg && "INSTALL_PARSE_FAILED" !in msg) { beginVerify(app); return@launch }
            installingId = null; scanningId = null; expectations -= k
            when {
                "CORRUPT" in msg || "PARSE_ERROR" in msg -> { error("文件已损坏，正在删除…"); deleteReadyFile(app, file) }
                "package appears to be invalid" in msg || "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in msg || "INSTALL_PARSE_FAILED" in msg ->
                    error("更新失败：已安装的应用与此包不兼容，可能需要重新安装。")
                direct -> error("直接安装失败：$msg")
                shizuku || "Shizuku" in msg -> error("「${if (s.rootMode) "Root" else "Shizuku"}」安装失败：$msg")
                else -> error("安装失败。")
            }
        }
    }

    private fun beginVerify(app: AppItem) { installingId = null; scanningId = app.id; viewModelScope.launch { verify(app) } }

    private suspend fun verify(app: AppItem) {
        val k = normalizePackageName(app.packageName); val exp = expectations[k]?.version
        val max = if (!exp.isNullOrEmpty() && isComparableVersion(exp)) 12 else 8; val t0 = now()
        repeat(max) {
            delay(1000)
            val (inst, ver) = syncTargets(listOf(app))[app.id] ?: (false to "")
            if (inst && (exp.isNullOrEmpty() || !isComparableVersion(exp) || compareVersions(ver, exp) >= 0)) {
                delay(maxOf(0, 2500 - (now() - t0)))
                set { st -> st.copy(lastRemoteVersions = if (ver.isNotEmpty()) st.lastRemoteVersions + (app.id to ver) else st.lastRemoteVersions, packageOwners = if (k.isNotEmpty()) st.packageOwners + (k to app.id) else st.packageOwners) }
                expectations -= k
                d.readyToInstall[app.id]?.let { file ->
                    upd { it.recordInstalled(app.id, ver, file, now()).copy(readyToInstall = it.readyToInstall - app.id, pendingCleanup = if (app.id in it.pendingCleanup) it.pendingCleanup else it.pendingCleanup + (app.id to CleanupEntry(file, now()))) }
                    if (s.deleteApk && purgeApk(app.id, file)) toast("已安装，APK 已删除")
                }
                scanningId = null; return
            }
        }
        expectations -= k; scanningId = null; delay(2200); syncInstalledApps()
    }

    /** Groups apps sharing a package, probes candidates, and assigns the install to exactly one owner. */
    private suspend fun syncTargets(targets: List<AppItem>): Map<String, Pair<Boolean, String>> {
        val st = s; val all = allApps()
        val (groups, cand, info) = withContext(Dispatchers.Default) {
            val byPkg = all.filter { !it.packageName.isNullOrBlank() }.groupBy { normalizePackageName(it.packageName) }
            val groups = LinkedHashMap<String, List<AppItem>>()
            targets.forEach { a -> val k = normalizePackageName(a.packageName); if (k.isNotEmpty() && k !in groups) groups[k] = byPkg[k] ?: listOf(a) }
            val installed = if (groups.isEmpty()) null else tracker.installedInfos()
            val cand = groups.mapValues { (_, g) -> g.flatMap { packageCandidates(it, st.resolvedPackageNames[it.id], installed?.keys.orEmpty()) }.distinct() }
            val all = cand.values.flatten().toSet()
            Triple(groups, cand, when { groups.isEmpty() -> emptyMap(); installed != null -> all.associateWith { installed[it] ?: PkgInfo(false) }; else -> tracker.appInfos(all) })
        }
        if (groups.isEmpty()) return emptyMap()
        val ver = st.installedVersions.toMutableMap(); val res = st.resolvedPackageNames.toMutableMap(); val own = st.packageOwners.toMutableMap()
        val stale = mutableSetOf<String>(); val out = mutableMapOf<String, Pair<Boolean, String>>()
        groups.forEach { (k, g) ->
            val hit = cand.getValue(k).firstOrNull { info[it]?.installed == true }; val v = hit?.let { info.getValue(it).version }.orEmpty()
            val owner = hit?.let { resolveOwnedAppId(g, k, v, st, expectations[k]) }
            if (hit == null || owner == null) {
                // A pending install keeps the package its APK named, so a sync while the installer is open does not forget it
                own -= k; g.forEach { ver -= it.id; if (expectations[k]?.appId != it.id) res -= it.id; out[it.id] = false to ""; if (hit == null && st.lastRemoteVersions[it.id] != null) stale += it.id }
            } else {
                own[k] = owner
                g.forEach { if (it.id == owner) { ver[it.id] = v; res[it.id] = hit; out[it.id] = true to v } else { ver -= it.id; res -= it.id; out[it.id] = false to "" } }
            }
        }
        set { it.copy(installedVersions = ver, resolvedPackageNames = res, packageOwners = own, lastRemoteVersions = it.lastRemoteVersions - stale) }
        return out
    }

    private suspend fun syncInstalledApps() {
        val all = allApps().filter { !it.packageName.isNullOrBlank() }; if (all.isEmpty()) return
        val r = syncTargets(all); val now = now(); val d0 = d
        val installed = mutableListOf<Triple<String, String, String?>>(); val doneReady = mutableSetOf<String>(); val cleanup = mutableMapOf<String, CleanupEntry>()
        val remote = mutableMapOf<String, String>(); val owners = mutableMapOf<String, String>()
        all.forEach { a ->
            val (inst, v) = r[a.id] ?: (false to ""); val file = d0.readyToInstall[a.id]
            if (inst && installingId != a.id) installed += Triple(a.id, v, file ?: d0.appLibrary[a.id]?.fileName)
            if (!inst || file == null || installingId == a.id) return@forEach
            val exp = versionMetaFromFileName(a, file, s.streamOf(a.id)).first
            if (exp.isNotEmpty() && isComparableVersion(exp) && compareVersions(v, exp) < 0) return@forEach
            doneReady += a.id; if (v.isNotEmpty()) remote[a.id] = v
            normalizePackageName(a.packageName).let { owners[it] = a.id; expectations -= it }
            if (a.id !in d0.pendingCleanup) cleanup[a.id] = CleanupEntry(file, now)
        }
        upd { st -> installed.fold(st) { acc, (id, v, f) -> acc.recordInstalled(id, v, f, now) }.copy(readyToInstall = st.readyToInstall - doneReady, pendingCleanup = st.pendingCleanup + cleanup) }
        if (s.deleteApk) cleanup.forEach { (id, e) -> purgeApk(id, e.fileName) }
        if (remote.isNotEmpty() || owners.isNotEmpty()) set { it.copy(lastRemoteVersions = it.lastRemoteVersions + remote, packageOwners = it.packageOwners + owners) }
    }

    fun scheduleInstalledSync() { if (syncJob?.isActive == true) return; syncJob = viewModelScope.launch { delay(900); runCatching { syncInstalledApps() } } }

    fun syncApp(app: AppItem) = viewModelScope.launch {
        if (app.packageName.isNullOrBlank()) return@launch
        val inst = runCatching { syncTargets(listOf(app))[app.id]?.first == true }.getOrDefault(true)
        if (!inst && s.lastRemoteVersions[app.id] != null) set { it.copy(lastRemoteVersions = it.lastRemoteVersions - app.id) }
    }

    fun batchInstall() = viewModelScope.launch {
        if (!s.useShizuku && !s.directInstall && !s.rootMode) return@launch
        val ready = d.readyToInstall; if (ready.isEmpty()) return@launch
        haptic(H.HEAVY); toast("正在更新 ${ready.size} 个应用…")
        for ((id, f) in ready) { lookup[id]?.let { installFile(it, f).join(); delay(1000) } }
        toast("全部更新完成。"); haptic(H.SUCCESS)
    }

    fun exportApk(app: AppItem, file: String) = viewModelScope.launch {
        haptic(H.SELECTION)
        try { tracker.exportFile(file); toast("已导出到下载目录"); haptic(H.SUCCESS); upd { it.copy(pendingCleanup = it.pendingCleanup - app.id) } }
        catch (e: Exception) { error(e.message ?: "导出失败") }
    }

    /** Deletes a downloaded APK and forgets it everywhere it is tracked (ready, pending cleanup, library), so no screen offers it again.
     *  Every delete path goes through here; clearing only some of these maps is what kept "Delete APK" coming back. */
    fun purgeApk(appId: String, file: String?): Boolean {
        if (file != null && runCatching { tracker.deleteFile(file) }.isFailure) return false
        upd { it.copy(pendingCleanup = it.pendingCleanup - appId, readyToInstall = it.readyToInstall - appId).removeLibraryFile(appId, now()) }
        return true
    }

    fun deleteReadyFile(app: AppItem, file: String) {
        if (!purgeApk(app.id, file)) return
        expectations -= normalizePackageName(app.packageName)
        set { it.copy(lastRemoteVersions = it.lastRemoteVersions - app.id) }
        haptic(H.SUCCESS)
    }

    fun deleteLibraryApk(app: AppItem) {
        val file = d.readyToInstall[app.id] ?: d.pendingCleanup[app.id]?.fileName ?: d.appLibrary[app.id]?.fileName
        if (!purgeApk(app.id, file)) { error("APK 无法删除。"); return }
        if (file != null) { set { it.copy(lastRemoteVersions = it.lastRemoteVersions - app.id) }; haptic(H.SUCCESS) }
    }
    // endregion

    // region navigation + browse
    var openFrom by mutableStateOf<String?>(null)
    /** Floating dock visibility (hides while scrolling down); the scroll-to-top button rides just above it. */
    var navShown by mutableStateOf(true)
    var libraryQuery by mutableStateOf("")
    fun selectApp(app: AppItem, from: String? = null) { openFrom = from; selectedApp = app; syncApp(app); prefetchShots(app.screenshots.drop(2)) }

    /** Pulls screenshots into Coil's disk cache ahead of time, so they paint from disk instead of the network. The tiny decode keeps it cheap;
     *  the real request misses memory (different size) but hits disk. Same URL builder as Shot, so the cache keys match. */
    private fun prefetchShots(urls: List<String>) {
        val loader = ctx.imageLoader
        urls.forEach { loader.enqueue(ImageRequest.Builder(ctx).data(optimizedImageUrl(it, null, 720)).size(32).memoryCachePolicy(CachePolicy.DISABLED).build()) }
    }

    private var warmed = false
    /** Hero and first-lane screenshots of every tab, on unmetered networks only, after the first screen has had the network to itself. */
    private fun warmStorefront(f: Map<String, Pair<List<StoreCollection>, List<String>>>) = viewModelScope.launch {
        delay(2500)
        if (runCatching { ctx.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered }.getOrDefault(true)) return@launch
        prefetchShots(f.values.flatMap { (cols, _) -> cols.filter { it.type == CollectionType.HERO }.flatMap { c -> c.apps.flatMap { it.screenshots.take(2) } } }.distinct())
    }
    private fun openById(id: String, miss: String = "不存在") { lookup[id]?.let { selectApp(it) } ?: toast("应用「$id」$miss") }
    fun navigateToApp(id: String) = openById(id)
    fun navigateFromSettings(id: String) { overlays -= Overlay.SETTINGS; viewModelScope.launch { delay(100); openById(id) } }

    fun deepLink(uri: Uri?) {
        if (uri?.scheme != "orionstore") return
        val seg = (listOfNotNull(uri.host) + uri.pathSegments).filter { it.isNotEmpty() }
        if (seg.getOrNull(0) != "app" || seg.getOrNull(1) == null) return
        val id = Uri.decode(seg[1])
        whenLoaded { openById(id, "不在 Orion Store 中") }
    }

    /** "App details in store" from system App info. Prefers the catalog entry that installed it: forks can share a package name. */
    fun showAppInfo(pkg: String) = whenLoaded {
        val k = normalizePackageName(pkg)
        val id = s.packageOwners[k]?.takeIf { it in lookup }
            ?: allApps().firstOrNull { normalizePackageName(s.resolvedPackageNames[it.id] ?: it.packageName) == k }?.id
        if (id != null) openById(id) else toast("该应用不在 Orion Store 中")
    }

    private fun whenLoaded(open: () -> Unit) { if (lookup.isEmpty()) pendingDeepLink = open else open() }

    fun openSettings(menu: String = "none") { settingsMenu = menu; if (Overlay.SETTINGS !in overlays) overlays += Overlay.SETTINGS }
    fun open(o: Overlay) { if (o !in overlays) overlays += o }
    fun close(o: Overlay) { overlays -= o; if (o == Overlay.PROFILE_STATS) badgeIndex = null }

    fun navTo(t: Tab) { if (t != Tab.ABOUT && t.name.lowercase() in s.hiddenTabs) return; haptic(H.LIGHT); tab = t }

    fun toggleTheme() {
        val next = when (s.theme) { "light" -> "dusk"; "dusk" -> "dark"; "dark" -> "light"; "oled" -> "light"; else -> "dark" }
        set { it.copy(theme = next, isOled = next == "oled") }; haptic(H.MEDIUM)
    }

    fun titleClick() {
        if (s.isDevUnlocked) { toast("你已经是开发者了。"); return }
        devClicks++; val left = 7 - devClicks
        if (left in 1..4) { toast("距离成为开发者还差 $left 步。"); haptic(H.LIGHT) }
        if (devClicks >= 7) { set { it.withDevUnlocked(true) }; toast("你已成为开发者！"); haptic(H.SUCCESS); devClicks = 0 }
    }

    fun profileClick(view: String? = null, index: Int? = null) {
        if (view == "badges") { profileView = "badges"; badgeIndex = index; open(Overlay.PROFILE_STATS); haptic(H.LIGHT); return }
        badgeIndex = null
        if (++eggClicks >= 7) {
            // The egg only unlocks its badge: counting it as an ad watch made 7 taps worth 40 XP, endlessly.
            tracker.open(config?.easterEggUrl ?: Defaults.EASTER_EGG); eggClicks = 0
            set { it.copy(isLegend = true) }; haptic(H.SUCCESS)
        } else haptic(H.LIGHT)
    }
    fun openProfileStats() { profileView = "profile"; badgeIndex = null; open(Overlay.PROFILE_STATS) }

    private fun clearExpanded() { expandedView = null; expandedApps = emptyList(); viewAllApps = null; showAllSorted = false }
    private fun tabUpd(fn: (TabViewState) -> TabViewState) { upd { it.withTab(tab.key(), fn) }; refreshVisible() }

    /** Category, sort and favorites taps recompute the current tab right here, so the next frame already shows the new list.
     *  Waiting on the background pass showed the old list (or home) for a beat and then shuffled it. Typing stays debounced off-thread. */
    private fun refreshVisible() {
        val ts = tabState; val all = allApps()
        if (ts.query.isNotEmpty() || all.isEmpty()) return
        val p = tab.targetPlatform()
        visibleBy = visibleBy + (p to CatalogProcessor.search("", ts.category, displaySort(s.storeLayout, ts), all.filter { it.platform == p }))
    }

    fun restoreBrowse() { clearExpanded(); tabUpd { it.copy(query = "", category = "All", filterFavorites = false, sort = SortOption.NEWEST) } }
    fun search(q: String) { clearExpanded(); returnToExpanded = null; tabUpd { it.copy(query = q) } }
    fun filterCategory(c: String) { clearExpanded(); tabUpd { it.copy(category = c) } }
    fun categoryNavigate(c: String) {
        haptic(H.SELECTION); clearExpanded(); returnToExpanded = null
        tabUpd { it.copy(category = c, sort = if (it.sort == SortOption.HOME) SortOption.NEWEST else it.sort) }
    }
    fun sort(o: SortOption) {
        clearExpanded()
        if (s.storeLayout == "modern" && o == SortOption.HOME) { restoreBrowse(); return }
        tabUpd { it.copy(sort = o) }
    }
    fun toggleFavorites() { haptic(H.SELECTION); tabUpd { it.copy(filterFavorites = !it.filterFavorites) } }
    fun toggleFavorite(id: String) { haptic(H.LIGHT); upd { it.toggleFavorite(id) } }
    fun showAll() { expandedView = null; expandedApps = emptyList(); returnToExpanded = null; viewAllApps = null; showAllSorted = true }

    private fun platformApps() = allApps().filter { it.platform == tab.targetPlatform() }
    fun showCollectionAll(c: StoreCollection) {
        if (c.id == "new_updated") {
            val list = platformApps().reversed().take(18)
            if (list.isEmpty()) { expandedView = null; expandedApps = emptyList(); viewAllApps = null; showAllSorted = true; tabUpd { it.copy(sort = SortOption.NEWEST) }; return }
            showAllSorted = false; viewAllApps = null; expandedView = "new_updated"; expandedApps = list; return
        }
        expandedView = null; expandedApps = emptyList()
        viewAllApps = if (c.id == "updates-available") updates.filter { it.platform == tab.targetPlatform() } else c.apps
        showAllSorted = true
    }
    fun expandedMore() { returnToExpanded = expandedView; expandedView = null; viewAllApps = platformApps().reversed(); showAllSorted = true; tabUpd { it.copy(sort = SortOption.NEWEST) } }

    private var favCache: Triple<List<AppItem>, List<String>, List<AppItem>>? = null
    /** Favorites-filtered list is reused while the tab list and favorites are unchanged, so recompositions skip the filter and keep a stable reference. */
    fun visibleForTab(): List<AppItem> {
        val v = visible
        if (!tabState.filterFavorites) return v
        val fav = d.favorites
        favCache?.let { (cv, cf, out) -> if (cv === v && cf === fav) return out }
        return fav.toSet().let { f -> v.filter { it.id in f } }.also { favCache = Triple(v, fav, it) }
    }
    fun classicApps() = if (showAllSorted) viewAllApps ?: visibleForTab() else visibleForTab()
    fun showModernHome() = s.storeLayout == "modern" && isModernHome(tabState)

    fun modernCollections(): List<StoreCollection> {
        if (showAllSorted || !showModernHome()) return emptyList()
        val sort = effectiveSort
        if (sort != SortOption.HOME) return sortedModernCollections(visibleForTab(), sort)
        val withUpdates = injectAfterHero(collections, if (tab == Tab.ANDROID || tab == Tab.TV && Device.isTv) updatesCollection(updates.filter { it.platform == tab.targetPlatform() }) else null)
        if (s.customBundles.isEmpty()) return withUpdates
        // The id map is already built off the main thread; only the platform check is left per app
        val p = tab.targetPlatform()
        val local = s.customBundles.map { cb -> BundleItem(cb.id, cb.title, cb.description, cb.appIds, cb.appIds.mapNotNull { id -> lookup[id]?.takeIf { it.platform == p } }, badge = "Local", icon = cb.icon) }.filter { it.apps.isNotEmpty() }
        if (local.isEmpty()) return withUpdates
        val idx = withUpdates.indexOfFirst { it.type == CollectionType.RECOMMENDATION_BUNDLES }
        if (idx == -1) return injectAfterHero(withUpdates, StoreCollection("recommendation_bundles_local", "推荐应用合集", CollectionType.RECOMMENDATION_BUNDLES, "为你平台精选的应用", bundles = local))
        return withUpdates.toMutableList().also { l -> val t = l[idx]; val ids = t.bundles.map { it.id }.toSet(); l[idx] = t.copy(bundles = t.bundles + local.filter { it.id !in ids }) }
    }

    fun canResetBrowse() = expandedView != null || if (s.storeLayout == "modern") effectiveSort != SortOption.HOME
        else tabState.let { it.category != "All" || it.query.isNotEmpty() || it.filterFavorites || it.sort != SortOption.NEWEST }
    val sortControl get() = effectiveSort.let { if (s.storeLayout == "modern" && it == SortOption.HOME) SortOption.NEWEST else it }

    /** Hardware back for non-sheet state, in App.tsx handleBack order. Returns false when the activity should finish. */
    fun back(): Boolean {
        when {
            forcedBlocking && forcedTest == null -> return false
            forcedTest != null -> forcedTest = null
            expandedView != null -> { expandedView = null; expandedApps = emptyList() }
            showAllSorted -> {
                showAllSorted = false; viewAllApps = null
                if (returnToExpanded == "new_updated") { expandedView = "new_updated"; expandedApps = platformApps().reversed().take(18); returnToExpanded = null }
            }
            canResetBrowse() && tab.platform != null -> restoreBrowse()
            tab != homeTab -> tab = homeTab
            else -> return false
        }
        return true
    }
    // endregion
}
