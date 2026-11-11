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
import com.orion.store.OrionActivity
import com.orion.store.R
import com.orion.store.AutoUpdate
import com.orion.store.data.*
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import com.orion.store.platform.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.serialization.json.JsonArray
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
enum class Overlay { SETTINGS, RELEASE_NOTES, FAQ, SUBMISSION, AD_DONATION, CUSTOM_BUNDLE, PROFILE_STATS, NOTICE, TUTORIAL, GUIDE, AI, DOWNLOADS }

val FORCED_GUARD = setOf("scanning", "scan_complete", "installing", "install_failed", "export_ready", "byok_required", "ready_to_scan_or_install")
val FORCED_TEST_PROGRESS = mapOf("checking" to 8, "downloading" to 64, "download_failed" to 32, "scanning" to 72, "installing" to 96)
val FORCED_TEST_TEXT = mapOf(
    "checking" to R.string.gate_checking_newest, "downloading" to R.string.gate_downloading_update,
    "download_failed" to R.string.gate_download_interrupted, "ready_to_scan_or_install" to R.string.gate_update_ready,
    "byok_required" to R.string.gate_byok_required, "scanning" to R.string.gate_scanning_apk,
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
    val forcedVt = VtScan(tracker, "__orion_store_self_update__", ctx.getString(R.string.vm_orion_store), app.packageName, s.virusTotalApiKey)
    private var forcedUrl = ""; private var pendingScanAfterKey = false; private var forcedJob: Job? = null

    private val expectations = mutableMapOf<String, InstallExpectation>()
    private val initializing = mutableSetOf<String>()
    private var pendingRetry: Pair<AppItem, String>? = null
    private var pendingDeepLink: (() -> Unit)? = null
    private var loadGen = 0; private var syncJob: Job? = null; private var cleaning = false
    private var devClicks = 0; private var eggClicks = 0

    fun allApps() = d.apps + d.importedApps + orbitItems()

    /** Orbit entries as store items, rebuilt only when the Orbit list itself changes. */
    private var orbitMemo: Pair<List<OrbitApp>, List<AppItem>> = emptyList<OrbitApp>() to emptyList()
    fun orbitItems(): List<AppItem> { val o = d.orbit; if (orbitMemo.first !== o) orbitMemo = o to o.map { it.toItem() }; return orbitMemo.second }
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
                fronts = f; lookup = l + orbitItems().associateBy { it.id }
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
            // Orbit apps update like catalog ones, unless the user paused them
            combine(settings, data.map { it.apps + it.importedApps + it.orbit.filter { o -> !o.paused }.map(OrbitApp::toItem) }.distinctUntilChanged()) { st, all -> availableUpdates(all, st, now()) }.distinctUntilChanged().collect { u ->
                updates = u
                if (s.autoUpdateEnabled) u.filter { it.installsHere() && it.id !in d.activeDownloads && it.id !in d.readyToInstall && it.id !in initializing }.forEach { download(it, auto = true) }
            }
        }
        viewModelScope.launch {
            data.map { it.pendingCleanup.size }.distinctUntilChanged().collectLatest { n -> cleanupPill = n > 0; if (n > 0 && s.deleteApk) { delay(10_000); cleanupPill = false } }
        }
        viewModelScope.launch { while (true) { submissionCooldown = cooldown(); delay(60_000) } }
        // Orbit entries join the lookup as they change, once the catalog has filled it
        viewModelScope.launch { data.map { it.orbit }.distinctUntilChanged().collect { if (lookup.isNotEmpty()) lookup = lookup.filterKeys { !Orbit.owns(it) } + orbitItems().associateBy { it.id } } }
        // Sources are checked again on start when the last look is older than half an hour
        viewModelScope.launch { delay(4000); if (d.orbit.any { !it.paused && now() - it.checkedAt > 30 * 60_000L }) orbitRefresh() }
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
    fun installToast(app: AppItem, file: String) = toast(ctx.getString(R.string.vm_is_ready_to_install, app.name), 6000, ctx.getString(R.string.vm_install)) { installFile(app, file) }
    // endregion

    // region startup
    private fun startup() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val nm = NotificationManagerCompat.from(ctx)
            nm.createNotificationChannel(NotificationChannelCompat.Builder("orion_updates", NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(ctx.getString(R.string.autoupdate_orion_updates)).build())
            nm.createNotificationChannel(NotificationChannelCompat.Builder("orion_cleanup", NotificationManagerCompat.IMPORTANCE_HIGH).setName(ctx.getString(R.string.vm_cleanup)).build())
            DownloadService.ensureChannel(ctx)
        }
        delay(1800); startupReady = true
        delay(1400)
        val now = now(); val del = d.pendingCleanup.filter { (_, e) -> s.deleteApk || now - e.timestamp > ONE_WEEK_MS }
        if (del.isEmpty()) return@launch
        val n = del.count { (id, e) -> purgeApk(id, e.fileName) }
        if (n > 0) queue { toast(ctx.getString(R.string.vm_janitor_cleaned_files, n)) }
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
    /** Sideloading protection popup, one step at a time: intro / protected / working / failed / setup. [verifierState] is the phone's
     *  component, read again on every resume, and drives the header shield. */
    var verifierPrompt by mutableStateOf(false); var verifierStep by mutableStateOf("intro"); var verifierError by mutableStateOf("")
    var verifierState by mutableStateOf(Verifier.State.ABSENT); var verifierReverting by mutableStateOf(false)
    /** Wireless debugging setup opened from the popup; the popup comes back once it closes paired. */
    var verifierSetup by mutableStateOf(false)
    /** Developer test run: "done" or "failed" plays that outcome without running a single command or touching the seen flag. */
    var verifierFake by mutableStateOf<String?>(null)
    fun testVerifier(outcome: String) { verifierFake = outcome; verifierStep = "intro"; verifierError = ""; verifierPrompt = true }
    fun refreshVerifier() { verifierState = Verifier.state(ctx) }
    /** The popup on the step that fits the phone: Secure me while verification is on, Revert once it is off. */
    fun openVerifier() {
        refreshVerifier()
        if (verifierState == Verifier.State.ABSENT && flag("verifier") != "secured") { toast(ctx.getString(R.string.vm_no_developer_verification)); return }
        verifierFake = null; verifierError = ""; verifierReverting = false
        verifierStep = if (verifierState == Verifier.State.ACTIVE) "intro" else "protected"; verifierPrompt = true
    }
    private val turns = kotlinx.coroutines.sync.Mutex()
    private val screenFree get() = startupReady && !forcedBlocking && forcedTest == null && !((config?.maintenanceMode == true || s.localMaintenanceMode) && !bypassMaintenance) &&
        overlays.isEmpty() && selectedApp == null && selectedBundle == null && vtTarget == null && autoReport == null && !welcomeOpen && !announceNow && !donateOpen && !donateNudge && !guidePrompt && !verifierPrompt && !verifierSetup &&
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
        // Offered once by itself; after that the header shield opens it
        refreshVerifier()
        if (flag("verifier") == null && verifierState == Verifier.State.ACTIVE) takeTurn { openVerifier() }
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

    /** Root or a granted Shizuku first, then built-in wireless ADB, whose shell user is enough for every command. Null when none is set up,
     *  so the popup can offer the setup instead of failing. A route that is set up but not answering throws its own reason. */
    private suspend fun verifierShell(): (suspend (String) -> String)? {
        if (tracker.elevatedReady()) return { tracker.runPrivileged(it) }
        if (s.useShizuku && runCatching { tracker.requestElevated() }.isSuccess && tracker.elevatedReady()) return { tracker.runPrivileged(it) }
        if (!WirelessAdb.supported || !(s.directInstall || WirelessAdb.paired(ctx))) return null
        WirelessAdb.connect(ctx)
        return { WirelessAdb.shell(ctx, it) }
    }

    /** Runs the change one command at a time, then trusts only the phone: success means the component really is off (or back on). */
    private fun runVerifier(revert: Boolean) = viewModelScope.launch {
        verifierReverting = revert; verifierStep = "working"; verifierError = ""
        verifierFake?.let { f ->
            delay(2200)
            if (f == "done") { verifierStep = if (revert) "intro" else "protected"; haptic(H.SUCCESS) }
            else { verifierError = ctx.getString(R.string.vm_verifier_test_refused); verifierStep = "failed"; haptic(H.ERROR) }
            return@launch
        }
        val sh = try { verifierShell() } catch (e: Exception) {
            verifierError = (e as? NativeError)?.message ?: e.message ?: ctx.getString(R.string.vm_wireless_debugging_didnt_answer); verifierStep = "failed"; haptic(H.ERROR); return@launch
        } ?: run { verifierStep = "setup"; haptic(H.LIGHT); return@launch }
        val log = StringBuilder()
        suspend fun run(cmd: String) = runCatching { sh(cmd) }.getOrElse { it.message.orEmpty() }.also { log.appendLine(it) }
        withContext(Dispatchers.IO) {
            if (!revert) {
                // The originals are saved once, so a second Secure me never records Orion's own values
                if (flag("verifierPrev") == null) setFlag("verifierPrev", Verifier.GLOBALS.map { (k, _) -> run("settings get global $k").trim().takeIf { it.matches(Regex("-?\\d+")) } ?: "null" }.joinToString("|"))
                val pkgDump = run("dumpsys package ${Verifier.PKG}")
                val overlayDump = run("dumpsys package ${Verifier.OVERLAY}")
                log.appendLine("verifier home: ${Verifier.home(pkgDump).name.lowercase()}")
                Verifier.secureSteps(pkgDump, overlayDump).forEach { run(it) }
            } else {
                Verifier.RESTORE_CMDS.forEach { run(it) }
                val prev = flag("verifierPrev")?.split("|")
                Verifier.GLOBALS.forEachIndexed { i, (k, _) -> prev?.getOrNull(i)?.takeIf { it != "null" }?.let { run("settings put global $k $it") } ?: run("settings delete global $k") }
            }
        }
        refreshVerifier()
        val ok = if (revert) verifierState == Verifier.State.ACTIVE else verifierState != Verifier.State.ACTIVE
        if (ok) {
            if (revert) { set { st -> st.copy(seenFlags = st.seenFlags.filterNot { it.startsWith("verifierPrev=") }.toSet()) }; toast(ctx.getString(R.string.vm_developer_verification_back_on)) }
            setFlag("verifier", if (revert) "seen" else "secured")
            verifierStep = if (revert) "intro" else "protected"; haptic(H.SUCCESS)
        } else {
            val why = Verifier.refusal(log.toString())
            verifierError = (if (why != null) ctx.getString(R.string.vm_android_refused_change, why) else if (revert) ctx.getString(R.string.vm_android_kept_verification_off) else ctx.getString(R.string.vm_phone_kept_verification_on)) +
                if (!revert && !s.rootMode) ctx.getString(R.string.vm_root_can_get_through) else ""
            verifierStep = "failed"; haptic(H.ERROR)
        }
    }

    fun secureDevice() = runVerifier(false)
    fun revertVerifier() = runVerifier(true)

    /** Closing never hides protection: the header shield stays and opens this popup again. */
    fun finishVerifier() { if (verifierFake == null && flag("verifier") == null) setFlag("verifier", "seen"); verifierFake = null; verifierPrompt = false; verifierStep = "intro"; verifierError = "" }
    fun setupForVerifier() { verifierPrompt = false; verifierSetup = true }
    fun verifierSetupClosed() { verifierSetup = false; if (s.directInstall) openVerifier() }
    fun dismissNotice() { config?.notice?.id?.let { setFlag("notice", it) }; overlays -= Overlay.NOTICE }

    fun flag(k: String) = s.seenFlags.firstOrNull { it.startsWith("$k=") }?.substringAfter('=')
    fun setFlag(k: String, v: String) = set { st -> st.copy(seenFlags = st.seenFlags.filterNot { it.startsWith("$k=") }.toSet() + "$k=$v") }
    fun announcementDismissed() = config?.announcement?.let { flag("announcement") == it.hashCode().toString() } ?: true
    fun dismissAnnouncement() = config?.announcement?.let { setFlag("announcement", it.hashCode().toString()) }

    private fun cooldown(): String? {
        val mins = maxOf(30, 180 - minOf(s.submissionCount * 15, 150))
        val left = s.lastSubmissionTime + mins * 60_000L - now()
        return if (s.lastSubmissionTime == 0L || left <= 0) null else "${left / 3_600_000}h ${(left / 60_000) % 60}m"
    }
    fun refreshCooldown() { submissionCooldown = cooldown() }

    fun onResume() {
        pendingRetry?.let { (a, f) -> if (tracker.canRequestInstalls()) { pendingRetry = null; viewModelScope.launch { delay(500); installFile(a, f) } } }
        if (installingId == null && scanningId == null) scheduleInstalledSync()
        announceQuest(); cloudBackup(); reportAutoUpdates(); leaderboardSync()
        refreshVerifier() // the user may have flipped it in system Settings meanwhile
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
            else if (log.isNotEmpty()) { set { it.copy(autoLog = emptyList()) }; toast(if (log.size == 1) ctx.getString(R.string.vm_was_updated_in_the_background, log[0].name) else ctx.getString(R.string.vm_apps_were_updated_in_the_background, log.size)) }
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
            .onSuccess { set { it.copy(lastCloudBackup = now()) }; if (force) { toast(ctx.getString(R.string.vm_identity_backed_up)); haptic(H.SUCCESS) } }
            .onFailure { if (force) error(ctx.getString(R.string.vm_cloud_backup_failed_pick_the_backup)) else if (!cloudWarned) { cloudWarned = true; queue { error(ctx.getString(R.string.vm_cloud_backup_failed_pick_the_backup)) } } }
    }

    /** Keeps a joined pilot's Hall of Fame entry current without the Fame Gate: at most every 2 days, and only after a level up, so the relay's free quota lasts. */
    private fun leaderboardSync() = viewModelScope.launch {
        val st = s; val p = st.userProfile ?: return@launch; val t = now()
        if (st.lastLeaderboardSubmissionTime == 0L || t - st.lastLeaderboardSubmissionTime < 2 * Xp.DAY_MS) return@launch
        val xp = Xp.total(st, d.appLibrary.size)
        if (Xp.level(xp) <= Xp.level(st.leaderboardXp) || !(st.adWatchCount >= 5 || st.isContributor) || !Xp.plausible(st, t)) return@launch
        val url = p.avatarUrl ?: CHARACTERS.firstOrNull { it.id == p.avatarId }?.url ?: return@launch
        if (runCatching { postLeaderboard(st, xp, p.name, url, t) }.getOrNull()?.first in 200..299) set { it.copy(lastLeaderboardSubmissionTime = t, leaderboardXp = xp) }
    }

    /** Root: makes Orion the installer of catalog apps installed from elsewhere, so their updates land silently here. */
    fun adoptApps() = viewModelScope.launch {
        val owned = s.resolvedPackageNames.filterKeys { it in s.installedVersions }.values
        val foreign = withContext(Dispatchers.IO) { tracker.foreignInstalls(owned) }
        if (foreign.isEmpty()) { toast(ctx.getString(R.string.vm_orion_already_updates_every_catalog_app)); return@launch }
        toast(ctx.getString(R.string.vm_adopting_apps, foreign.size), 60_000)
        val failed = foreign.keys.count { runCatching { tracker.adopt(it) }.isFailure }
        haptic(if (failed == 0) H.SUCCESS else H.WARNING)
        toast(if (failed == 0) ctx.getString(R.string.vm_orion_now_updates_more_apps, foreign.size) else ctx.getString(R.string.vm_adopted_failed_signature_or_split_mismatch, foreign.size - failed, failed), 6000)
    }

    fun trimCaches() = viewModelScope.launch {
        runCatching { tracker.trimCaches() }.onSuccess { toast(ctx.getString(R.string.vm_app_caches_cleared)); haptic(H.SUCCESS) }.onFailure { error(it.message ?: ctx.getString(R.string.vm_trim_failed)) }
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
            // The embedded fallback is older than any catalog already on screen
            process(if (r.config == null && d.apps.isNotEmpty()) null else r.rawApps, r.mirror, r.stars)
            if (r.config != null) CatalogCache(d.apps, r.config, r.source).let { c -> launch(Dispatchers.IO) { runCatching { repo.saveCache(c) } } }
        } catch (e: Exception) {
            if (d.apps.isEmpty()) { error(ctx.getString(R.string.vm_failed_to_load_apps)); clearConfigState(); if (st.devConfigEnabled) { devStatus = "error"; devError = e.message ?: ctx.getString(R.string.vm_failed_to_load_developer_config) }; haptic(H.ERROR) }
            loading = false; refreshing = false
        }
    }

    private suspend fun process(raw: JsonElement?, mirror: Map<String, List<CatalogProcessor.Release>>?, stars: Map<String, Int>? = null) {
        // A load that brought no catalog at all keeps what is on screen rather than emptying the store
        if (raw !is JsonArray && d.apps.isNotEmpty()) { loading = false; refreshing = false; scheduleInstalledSync(); return }
        val m = mirror.orEmpty(); val imported = d.importedApps; val old = allApps()
        // Without a mirror, apps keep their last known releases: marking every app unavailable would also poison the disk cache
        val (apps, imp) = withContext(Dispatchers.Default) {
            val a = CatalogProcessor.process(raw, m); val i = imported.map { CatalogProcessor.processItem(it, m) }
            (if (m.isEmpty()) CatalogProcessor.carryVersions(a, old) to CatalogProcessor.carryVersions(i, old) else a to i)
                .let { (x, y) -> CatalogProcessor.withStars(x, stars, old) to CatalogProcessor.withStars(y, stars, old) }
        }
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
                fStatus = "download_failed"; fError = ctx.getString(R.string.vm_previously_downloaded_file_not_found_please); return
            }
            fStatus = if (cur.status == "export_ready") "export_ready".also { fExportPath = "" } else "ready_to_scan_or_install"
            fText = ctx.getString(R.string.vm_the_orion_update_is_already_downloaded); return
        }
        set { it.copy(forcedStoreUpdate = ForcedSnapshot(targetVersion = latest, downloadFileName = forcedStoreFileName(latest), status = "downloading", downloadCompleted = false, exported = false)) }
        fStatus = "downloading"; fText = ctx.getString(R.string.vm_downloading_the_latest_orion_build); forcedDownload()
    }

    private fun forcedDownload() {
        forcedJob?.cancel()
        forcedJob = viewModelScope.launch {
            val name = s.forcedStoreUpdate?.downloadFileName ?: return@launch
            if (forcedUrl.isEmpty()) { fStatus = "download_failed"; fError = ctx.getString(R.string.vm_no_download_url_found_for_this); return@launch }
            fError = ""; fProgress = 0
            if (runCatching { Downloads.start(forcedUrl, name) }.isFailure) { fStatus = "download_failed"; fError = ctx.getString(R.string.vm_download_failed_check_your_connection_and); return@launch }
            while (true) {
                delay(800)
                val dl = Downloads.status(name); fProgress = dl.progress
                if (dl.status == DlStatus.RUNNING) continue
                Downloads.forget(name)
                if (dl.status == DlStatus.SUCCESSFUL) {
                    snap { it.copy(status = "ready_to_scan_or_install", downloadCompleted = true) }
                    fStatus = "ready_to_scan_or_install"; fText = ctx.getString(R.string.vm_download_complete); fProgress = 100; haptic(H.SUCCESS)
                } else { fStatus = "download_failed"; fError = ctx.getString(R.string.vm_download_failed_check_your_connection_and); haptic(H.ERROR) }
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
        fStatus = "downloading"; fText = ctx.getString(R.string.vm_retrying_download); forcedDownload()
    }

    fun forcedInstall() = viewModelScope.launch {
        val sn = forcedSnapshot() ?: run { fError = ctx.getString(R.string.vm_update_package_metadata_is_missing_retry); return@launch }
        fError = ""; fText = ctx.getString(R.string.vm_opening_installer)
        if (!tracker.canRequestInstalls()) { fError = ctx.getString(R.string.vm_grant_install_permission_then_tap_install); runCatching { ctx.startActivity(tracker.installSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; return@launch }
        fStatus = "installing"; snap { it.copy(status = "installing") }
        try {
            tracker.install(sn.downloadFileName, s.installerPreference, s.installerPackage)
            runCatching { tracker.deleteFile(sn.downloadFileName) }
            snap { it.copy(status = "installed") }; fStatus = "installed"; haptic(H.SUCCESS)
        } catch (e: Exception) {
            fStatus = "install_failed"; snap { it.copy(status = "install_failed") }
            fError = if (e.message?.contains("CANCELED") == true) ctx.getString(R.string.vm_install_cancelled_save_the_apk_to) else ctx.getString(R.string.vm_install_failed_save_the_apk_to)
            haptic(H.ERROR)
        }
    }

    fun forcedExport() = viewModelScope.launch {
        val sn = s.forcedStoreUpdate ?: return@launch
        if (sn.exported) return@launch
        try {
            fExportPath = tracker.exportFile(sn.downloadFileName)
            snap { it.copy(status = "install_failed", exported = true) }
            fError = ""; fText = ctx.getString(R.string.vm_saved_to_your_downloads_folder); haptic(H.SUCCESS)
        } catch (e: Exception) { fError = e.message ?: ctx.getString(R.string.vm_export_failed_try_again) }
    }

    fun forcedScan(key: String = s.virusTotalApiKey) {
        if (key.isBlank()) { pendingScanAfterKey = true; fStatus = "byok_required"; fText = ctx.getString(R.string.vm_add_your_virustotal_api_key_below); return }
        pendingScanAfterKey = false
        val sn = forcedSnapshot()
        val target = if (sn?.downloadCompleted == true) ScanTarget(ScanTargetType.DOWNLOADED_APK, ctx.getString(R.string.vm_downloaded_update), sn.downloadFileName) else ScanTarget(ScanTargetType.REMOTE_APK, ctx.getString(R.string.vm_update_package), forcedUrl)
        fStatus = "scanning"; fText = ctx.getString(R.string.vm_scanning_the_apk_with_virustotal); fError = ""
        viewModelScope.launch {
            val r = forcedVt.start(target, key, ::hapticStr)
            if (r != null) { fResult = r; fStatus = "scan_complete"; fText = ctx.getString(R.string.vm_scan_complete_install_whenever_you_are); snap { it.copy(status = "scan_complete") } }
            else { fStatus = "ready_to_scan_or_install"; fError = forcedVt.error.ifEmpty { ctx.getString(R.string.vm_scan_failed_try_again) } }
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
        if (auto) autoIds += app.id
        if (s.directInstall && WirelessAdb.supported) WirelessAdb.warm(ctx)
        viewModelScope.launch {
            try {
                val meta = downloadVersionMeta(app, url, s.streamOf(app.id))
                set { it.copy(appStreams = it.appStreams + (app.id to meta.second)) }
                val target = url ?: app.variants.firstOrNull()?.url ?: app.downloadUrl
                if (target.isEmpty() || target == "#") return@launch
                if (s.wifiOnly && !onWifi()) { if (!auto) { error(ctx.getString(R.string.vm_download_blocked_wifi_only_mode)); haptic(H.ERROR) }; return@launch }
                val safe = sanitizeDownloadUrl(target)
                if (safe == "#") { if (!auto) { error(ctx.getString(R.string.vm_download_link_is_invalid_or_unsupported)); haptic(H.ERROR) }; return@launch }
                val std = safe.lowercase().let { it.endsWith(".apk") || it.endsWith(".xapk") || it.endsWith(".apkm") || it.endsWith(".apks") || it.endsWith(".exe") || it.endsWith(".zip") }
                if (!app.installsHere()) { if (!auto && (std || app.platform == Platform.PC || app.platform == Platform.TV)) tracker.open(safe); return@launch }
                if (Downloads.dir().usableSpace < 64L * 1024 * 1024) { if (!auto) error(ctx.getString(R.string.vm_not_enough_space_on_device)); return@launch }
                val resolved = runCatching { withContext(Dispatchers.IO) { Orbit.prepareDownload(safe) } }.getOrElse { e ->
                    if (!auto) error(if (e is HttpError && (e.code == 403 || e.code == 429)) ctx.getString(R.string.vm_rate_limited_by_the_host_try_later) else ctx.getString(R.string.vm_couldn_t_get_the_download_link))
                    return@launch
                }
                val file = apkFileName(app, meta.first, resolved)
                val id = runCatching { Downloads.start(resolved, file) }.getOrElse { if (!auto) tracker.open(safe); return@launch }
                upd { it.startDownload(app.id, id, file) }; haptic(H.MEDIUM)
            } finally { initializing -= app.id }
        }
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
                    else { downloadComplete(id, false); error(ctx.getString(R.string.vm_download_failed_network_error)) }
                }
            }
        }
    }

    private fun downloadComplete(id: String, ok: Boolean) {
        if (ok) {
            val file = d.activeDownloads[id]?.substringAfter('|') ?: return
            upd { it.completeDownload(id, file, now()) }
            (lookup[id] ?: allApps().firstOrNull { it.id == id })?.let { if (autoIds.remove(id) && s.autoInstall) autoInstall(it, file) else { installToast(it, file); notifyReady(it, file) } }
        } else upd { it.failDownload(id) }
        haptic(if (ok) H.SUCCESS else H.ERROR)
    }

    /** Auto-updates the store downloaded while open (the background job steps aside then). */
    private val autoIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Install automatically for an update Orion downloaded while open: the silent routes through [installFile], otherwise the
     *  background job's no-prompt session. Whatever can't go silently stays ready with its notification. */
    private fun autoInstall(app: AppItem, file: String) = viewModelScope.launch {
        val st = s
        if (st.rootMode || st.useShizuku || st.useDhizuku || st.directInstall && WirelessAdb.supported) { installFile(app, file, opts = if (st.installOptions) st.installFlags else null); return@launch }
        val f = Downloads.file(file) ?: return@launch
        val pkg = withContext(Dispatchers.IO) { tracker.archivePackage(f) }
        val err = if (pkg == null) ctx.getString(R.string.autoupdate_the_downloaded_file_is_not_a) else AutoUpdate.install(ctx, tracker, st, f, pkg, app.name)
        if (err == null) beginVerify(app) else { notifyReady(app, file); toast(ctx.getString(R.string.vm_app_error, app.name, err), 6000, ctx.getString(R.string.vm_install)) { installFile(app, file) } }
    }

    private fun notifyReady(app: AppItem, file: String) = runCatching {
        val i = Intent(ctx, OrionActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("appId", app.id).putExtra("fileName", file)
        NotificationManagerCompat.from(ctx).notify(app.id.hashCode(), NotificationCompat.Builder(ctx, "orion_updates").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(ctx.getString(R.string.vm_download_complete_2)).setContentText(ctx.getString(R.string.vm_is_ready_to_install_2, app.name)).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, app.id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
    }

    /** Pilots who already beat Chapter 1 hear once that Chapter 2 is out; everyone else walks into it from the sheet. */
    private fun announceQuest() {
        val ch = questChapter(s.adWatchCount); if (ch <= s.questChapterSeen) return
        set { it.copy(questChapterSeen = ch) }
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) { queue { toast(ctx.getString(R.string.vm_new_mission_chapter_2_the_forkening), 8000, ctx.getString(R.string.vm_play)) { open(Overlay.AD_DONATION) } }; return }
        nm.createNotificationChannel(NotificationChannelCompat.Builder("orion_quest", NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(ctx.getString(R.string.vm_fuel_the_code_missions)).build())
        val i = Intent(ctx, OrionActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open", "quest")
        runCatching {
            nm.notify(7320, NotificationCompat.Builder(ctx, "orion_quest").setSmallIcon(com.orion.store.R.drawable.ic_launcher_monochrome)
                .setContentTitle(ctx.getString(R.string.vm_new_mission_unlocked_the_forkening)).setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.vm_entropy_s_last_breath_split_orion)))
                .setContentText(ctx.getString(R.string.vm_chapter_2_of_fuel_the_code))
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
        if (eligible.isEmpty()) { toast(ctx.getString(R.string.vm_all_apps_from_are_already_installed, b.title)); return@launch }
        toast(ctx.getString(R.string.vm_queueing_apps_from, eligible.size, b.title))
        var queued = 0; var skipped = apps.size - eligible.size
        for (a in eligible) {
            if (a.id in d.activeDownloads || a.id in d.readyToInstall || a.id in initializing) { skipped++; continue }
            runCatching { download(a, auto = true); queued++ }
            delay(180)
        }
        if (queued > 0) { toast(ctx.getString(R.string.vm_queued_apps_from, queued, b.title, if (skipped > 0) ctx.getString(R.string.vm_skipped_suffix, skipped) else "") + "."); haptic(H.SUCCESS) }
        else toast(ctx.getString(R.string.vm_everything_in_is_already_queued_or, b.title))
    }

    fun batchCleanup() = viewModelScope.launch {
        if (cleaning) return@launch
        val ids = d.pendingCleanup.keys.toList(); if (ids.isEmpty()) return@launch
        cleaning = true; haptic(H.HEAVY)
        val ok = ids.filter { id -> d.pendingCleanup[id]?.let { purgeApk(id, it.fileName) } == true }
        if (ok.isNotEmpty()) { haptic(H.SUCCESS); toast(ctx.getString(R.string.vm_cleaned_files, ok.size)) }
        cleaning = false
    }
    // endregion

    // region install + verification
    /** An install that can't or didn't work, explained in a dialog: [uninstall] offers removing the blocking app, [retry] installing anyway. */
    data class InstallIssue(val app: AppItem, val title: String, val text: String, val uninstall: String? = null, val retry: String? = null)
    var installIssue by mutableStateOf<InstallIssue?>(null)
    /** Developer page: the author, and the app page it was opened from (it closes once another app opens). */
    var devProfile by mutableStateOf<Pair<String, String?>?>(null)

    /** Beyond install: the APK waiting on the options sheet. */
    var installAsk by mutableStateOf<Pair<AppItem, String>?>(null)

    /** The file Inspect just downloaded becomes the one Install uses, so the build is not fetched a second time. */
    fun stageInspected(app: AppItem, file: File, url: String?) {
        val name = apkFileName(app, app.latestVersion, url)
        val dest = Downloads.file(name) ?: return
        if (file.absolutePath != dest.absolutePath) {
            file.copyTo(dest, overwrite = true)
            file.delete()
        }
        upd { it.completeDownload(app.id, name, System.currentTimeMillis()) }
    }

    /** [opts]: pm install options picked on the options sheet, which always installs through root or Shizuku. */
    fun installFile(app0: AppItem, file: String, regular: Boolean = false, force: Boolean = false, opts: Collection<String>? = null): kotlinx.coroutines.Job = viewModelScope.launch {
        // An Orbit app added by link learns its package from the first APK it downloads, so installs and updates can be tracked
        val app = if (Orbit.owns(app0.id) && app0.packageName.isNullOrBlank()) withContext(Dispatchers.IO) { runCatching { tracker.archivePackage(tracker.resolve(file)) }.getOrNull() }
            ?.takeIf { it.isNotBlank() }?.let { p -> upd { st -> st.copy(orbit = st.orbit.map { if (it.id == app0.id) it.copy(packageName = p) else it }) }; app0.copy(packageName = p) } ?: app0 else app0
        if (opts == null && !regular && s.installOptions) { installAsk = app to file; return@launch }
        val k = normalizePackageName(app.packageName)
        val meta = versionMetaFromFileName(app, file, s.streamOf(app.id))
        // Direct install runs as the shell user (no 24-hour wait, no "unknown apps" permission). When adbd is unreachable the
        // user picks the regular installer explicitly: silently falling back lands on the very wait this setting exists to skip.
        val dhizuku = opts == null && !regular && !s.rootMode && s.useDhizuku
        val direct = opts == null && !regular && !dhizuku && !s.rootMode && s.directInstall && WirelessAdb.supported
        if (direct && !WirelessAdb.connected) {
            installingId = app.id
            val err = runCatching { WirelessAdb.connect(ctx) }.exceptionOrNull()
            if (err != null) { installingId = null; toast(err.message ?: ctx.getString(R.string.vm_wireless_debugging_unreachable), 8000, ctx.getString(R.string.vm_use_installer)) { installFile(app, file, true) }; return@launch }
        }
        val shizuku = opts != null || !regular && !direct && !dhizuku && (s.useShizuku || s.rootMode)
        if (!direct && !shizuku && !dhizuku) try {
            if (!tracker.canRequestInstalls()) {
                error(ctx.getString(R.string.vm_please_allow_permission_and_return_here)); pendingRetry = app to file
                ctx.startActivity(tracker.installSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return@launch
            }
        } catch (e: Exception) { error(ctx.getString(R.string.vm_permission_check_failed_cannot_install)); return@launch }
        // Every route (installer, Shizuku, root, adb) fails the same way on a signer clash, so say why before trying
        if (!force) withContext(Dispatchers.IO) { runCatching { tracker.clash(tracker.resolve(file)) }.getOrNull() }?.let { (pkg, c) ->
            val (t, x) = clashText(ctx, c, app.name); haptic(H.WARNING)
            installIssue = InstallIssue(app, t, x, pkg.takeIf { c == Clash.INSTALLED }, file); return@launch
        }
        haptic(H.HEAVY); installingId = app.id
        if (k.isNotEmpty()) expectations[k] = InstallExpectation(app.id, meta.first)
        if (meta.second.isNotEmpty()) set { it.copy(appStreams = it.appStreams + (app.id to meta.second)) }
        // The APK names its own package: a channel build (com.lagradost.cloudstream3.prerelease) is tracked under that name, not the catalog's
        if (k.isNotEmpty()) withContext(Dispatchers.IO) { runCatching { tracker.archivePackage(tracker.resolve(file)) }.getOrNull() }
            ?.takeIf { it.isNotBlank() && normalizePackageName(it) != k }?.let { p -> set { it.copy(resolvedPackageNames = it.resolvedPackageNames + (app.id to p)) } }
        try {
            when {
                dhizuku -> { DhizukuBridge.install(ctx, tracker.resolve(file)); toast(ctx.getString(R.string.vm_installed_via_dhizuku)) }
                direct -> { WirelessAdb.install(ctx, tracker.resolve(file)); toast(ctx.getString(R.string.vm_installed_instantly)) }
                shizuku -> { tracker.installShizuku(file, s.updateLock, opts.orEmpty()); toast(if (s.rootMode) ctx.getString(R.string.vm_installed_with_root) else ctx.getString(R.string.vm_installed_via_shizuku)) }
                else -> { tracker.install(file, s.installerPreference, if (s.installerPreference == "package") s.installerPackage else ""); snackbar.currentSnackbarData?.dismiss() }
            }
            beginVerify(app)
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            if (!direct && !shizuku && !dhizuku && ("INSTALL_CANCELED" in msg || "INSTALL_FAILED" in msg) && "INSTALL_FAILED_UPDATE_INCOMPATIBLE" !in msg && "INSTALL_PARSE_FAILED" !in msg) { beginVerify(app); return@launch }
            installingId = null; scanningId = null; expectations -= k
            if ("CORRUPT" in msg || "PARSE_ERROR" in msg) deleteReadyFile(app, file)
            val (t, x) = installFailureText(ctx, msg, app.name); haptic(H.ERROR)
            val signer = listOf("UPDATE_INCOMPATIBLE", "signatures do not match").any { it in msg }
            installIssue = InstallIssue(app, t, x, (s.resolvedPackageNames[app.id] ?: app.packageName)?.takeIf { signer && it.isNotBlank() })
        }
    }

    private fun beginVerify(app: AppItem) { installingId = null; scanningId = app.id; viewModelScope.launch { verify(app) } }

    private suspend fun verify(app: AppItem) {
        val k = normalizePackageName(app.packageName); val exp = expectations[k]?.version; val before = s.installedVersions[app.id]
        val max = if (!exp.isNullOrEmpty() && isComparableVersion(exp)) 12 else 8; val t0 = now()
        repeat(max) {
            delay(1000)
            val (inst, ver) = syncTargets(listOf(app))[app.id] ?: (false to "")
            // A changed versionName also counts: Nightly and Beta builds often name themselves unlike their catalog version
            if (inst && (exp.isNullOrEmpty() || !isComparableVersion(exp) || compareVersions(ver, exp) >= 0 || (ver.isNotEmpty() && ver != before))) {
                delay(maxOf(0, 2500 - (now() - t0)))
                set { st -> st.copy(lastRemoteVersions = if (ver.isNotEmpty()) st.lastRemoteVersions + (app.id to ver) else st.lastRemoteVersions,
                    installedBuilds = if (ver.isNotEmpty() && !exp.isNullOrEmpty()) st.installedBuilds + (app.id to InstalledBuild(ver, exp)) else st.installedBuilds, packageOwners = if (k.isNotEmpty()) st.packageOwners + (k to app.id) else st.packageOwners) }
                expectations -= k
                d.readyToInstall[app.id]?.let { file ->
                    upd { it.recordInstalled(app.id, ver, file, now()).copy(readyToInstall = it.readyToInstall - app.id, pendingCleanup = if (app.id in it.pendingCleanup) it.pendingCleanup else it.pendingCleanup + (app.id to CleanupEntry(file, now()))) }
                    if (s.deleteApk && purgeApk(app.id, file)) toast(ctx.getString(R.string.vm_installed_apk_deleted))
                }
                scanningId = null; maybeNudgeDonate(); return
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
        val src = withContext(Dispatchers.IO) { cand.values.flatten().filter { info[it]?.installed == true }.associateWith(tracker::installerOf) }
        val ver = st.installedVersions.toMutableMap(); val res = st.resolvedPackageNames.toMutableMap(); val own = st.packageOwners.toMutableMap()
        val from = st.installSources.toMutableMap()
        val stale = mutableSetOf<String>(); val out = mutableMapOf<String, Pair<Boolean, String>>()
        groups.forEach { (k, g) ->
            val hit = cand.getValue(k).firstOrNull { info[it]?.installed == true }; val v = hit?.let { info.getValue(it).version }.orEmpty()
            val owner = hit?.let { resolveOwnedAppId(g, k, v, st, expectations[k]) }
            if (hit == null || owner == null) {
                // A pending install keeps the package its APK named, so a sync while the installer is open does not forget it
                own -= k; g.forEach { ver -= it.id; from -= it.id; if (expectations[k]?.appId != it.id) res -= it.id; out[it.id] = false to ""; if (hit == null && st.lastRemoteVersions[it.id] != null) stale += it.id }
            } else {
                own[k] = owner
                g.forEach { if (it.id == owner) { ver[it.id] = v; res[it.id] = hit; from[it.id] = src[hit].orEmpty(); out[it.id] = true to v } else { ver -= it.id; res -= it.id; from -= it.id; out[it.id] = false to "" } }
            }
        }
        set { it.copy(installedVersions = ver, resolvedPackageNames = res, packageOwners = own, installSources = from, lastRemoteVersions = it.lastRemoteVersions - stale) }
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

    /** The support reminder: never before 3 apps, at most once every 10 days, and then only on a 1 in 3 chance per install, so it stays rare and unpredictable. */
    var donateNudge by mutableStateOf(false)
    private fun maybeNudgeDonate() {
        val st = s
        if (st.donateNudgeOff || d.appLibrary.size < 3 || now() - st.lastDonateNudge < 10 * Xp.DAY_MS || kotlin.random.Random.nextInt(3) != 0) return
        set { it.copy(lastDonateNudge = now()) }
        queue { donateNudge = true }
    }

    fun scheduleInstalledSync() { if (syncJob?.isActive == true) return; syncJob = viewModelScope.launch { delay(900); runCatching { syncInstalledApps() } } }

    fun syncApp(app: AppItem) = viewModelScope.launch {
        if (app.packageName.isNullOrBlank()) return@launch
        val inst = runCatching { syncTargets(listOf(app))[app.id]?.first == true }.getOrDefault(true)
        if (!inst && s.lastRemoteVersions[app.id] != null) set { it.copy(lastRemoteVersions = it.lastRemoteVersions - app.id) }
    }

    /** Applies a launcher icon or dock style; a locked one points to Fuel the Code instead. */
    fun pickCosmetic(c: Cosmetic, icon: Boolean) {
        if (!c.unlocked(s)) {
            haptic(H.WARNING)
            set { it.copy(unlockTarget = c.id) }
            toast(ctx.getString(R.string.vm_ads_count_toward, c.label, c.progress(s), c.ads), 5000, ctx.getString(R.string.vm_fuel_up)) { open(Overlay.AD_DONATION) }
            return
        }
        haptic(H.SELECTION)
        if (!icon) { set { it.copy(navDock = c.id) }; return }
        if (runCatching { tracker.setLauncherIcon(c.id) }.isFailure) { error(ctx.getString(R.string.vm_could_not_change_app_icon)); return }
        set { it.copy(appIcon = c.id) }; toast(ctx.getString(R.string.vm_icon_changed))
    }

    /** Asked before a silent root or Shizuku uninstall; the system prompt asks on its own. */
    var uninstallAsk by mutableStateOf<AppItem?>(null)

    /** Removes an installed catalog app without leaving Orion: silently with root or Shizuku once confirmed, otherwise through Android's prompt. */
    fun uninstall(app: AppItem, confirmed: Boolean = false) = viewModelScope.launch {
        val pkg = (s.resolvedPackageNames[app.id] ?: app.packageName)?.takeIf { it.isNotBlank() } ?: return@launch
        val dhizuku = s.useDhizuku && !s.rootMode && DhizukuBridge.granted(ctx)
        val elevated = dhizuku || (s.useShizuku || s.rootMode) && tracker.elevatedReady()
        if (elevated && !confirmed) { uninstallAsk = app; haptic(H.WARNING); return@launch }
        haptic(H.MEDIUM)
        if (removeApp(app, pkg, dhizuku, elevated)) { toast(ctx.getString(R.string.vm_app_uninstalled, app.name)); haptic(H.SUCCESS) }
    }

    private suspend fun removeApp(app: AppItem, pkg: String, dhizuku: Boolean, elevated: Boolean): Boolean {
        val err = runCatching { when { dhizuku -> DhizukuBridge.uninstall(ctx, pkg); elevated -> tracker.uninstallSilent(pkg); else -> tracker.uninstallPrompt(pkg) } }.exceptionOrNull()
        if (err != null) { error(err.message ?: ctx.getString(R.string.vm_uninstall_failed)); return false }
        delay(300)
        if (withContext(Dispatchers.IO) { tracker.appInfo(pkg).installed }) return false
        syncApp(app).join(); return true
    }

    /** Several apps from My Apps, one after another: silent with root, Shizuku or Dhizuku, otherwise one system prompt each. */
    var uninstallManyAsk by mutableStateOf<List<AppItem>?>(null)
    fun uninstallMany(apps: List<AppItem>) = viewModelScope.launch {
        val dhizuku = s.useDhizuku && !s.rootMode && DhizukuBridge.granted(ctx)
        val elevated = dhizuku || (s.useShizuku || s.rootMode) && tracker.elevatedReady()
        haptic(H.MEDIUM)
        val done = apps.count { a -> (s.resolvedPackageNames[a.id] ?: a.packageName)?.takeIf { it.isNotBlank() }?.let { removeApp(a, it, dhizuku, elevated) } == true }
        if (done > 0) { toast(if (done == 1) ctx.getString(R.string.vm_one_app_uninstalled) else ctx.getString(R.string.vm_apps_uninstalled, done)); haptic(H.SUCCESS) }
    }

    fun batchInstall() = viewModelScope.launch {
        if (!s.useShizuku && !s.useDhizuku && !s.directInstall && !s.rootMode) return@launch
        val ready = d.readyToInstall; if (ready.isEmpty()) return@launch
        haptic(H.HEAVY); toast(ctx.getString(R.string.vm_updating_apps, ready.size))
        for ((id, f) in ready) { lookup[id]?.let { installFile(it, f, opts = if (s.installOptions) s.installFlags else null).join(); delay(1000) } }
        toast(ctx.getString(R.string.vm_all_updates_completed)); haptic(H.SUCCESS)
    }

    fun exportApk(app: AppItem, file: String) = viewModelScope.launch {
        haptic(H.SELECTION)
        try { tracker.exportFile(file); toast(ctx.getString(R.string.vm_exported_to_downloads)); haptic(H.SUCCESS); upd { it.copy(pendingCleanup = it.pendingCleanup - app.id) } }
        catch (e: Exception) { error(e.message ?: ctx.getString(R.string.vm_export_failed)) }
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
        if (!purgeApk(app.id, file)) { error(ctx.getString(R.string.vm_apk_could_not_be_deleted)); return }
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
    /** After the first screen has had the network to itself: the first screenful of icons of every tab goes into the memory cache
     *  (from disk only on metered networks), so the first switch to PC or TV doesn't decode a whole page at once. Hero and
     *  first-lane screenshots follow on unmetered networks. */
    private fun warmStorefront(f: Map<String, Pair<List<StoreCollection>, List<String>>>) = viewModelScope.launch {
        delay(2500)
        val metered = runCatching { ctx.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered }.getOrDefault(true)
        val loader = ctx.imageLoader
        f.forEach { (p, front) ->
            (front.first.take(4).flatMap { it.apps.take(8) } + visibleBy[p].orEmpty().take(12)).mapNotNull { it.icon.takeIf(String::isNotEmpty) }.distinct().forEach { icon ->
                // Same data as RemoteIcon, so the cached bitmap is the one the tile asks for
                loader.enqueue(ImageRequest.Builder(ctx).data(optimizedImageUrl(icon, 256, 256)).size(160)
                    .networkCachePolicy(if (metered) CachePolicy.DISABLED else CachePolicy.ENABLED).build())
            }
        }
        if (metered) return@launch
        prefetchShots(f.values.flatMap { (cols, _) -> cols.filter { it.type == CollectionType.HERO }.flatMap { c -> c.apps.flatMap { it.screenshots.take(2) } } }.distinct())
    }
    private fun openById(id: String, miss: String = ctx.getString(R.string.vm_not_found)) { lookup[id]?.let { selectApp(it) } ?: toast(ctx.getString(R.string.vm_app, id, miss)) }
    fun navigateToApp(id: String) = openById(id)
    fun navigateFromSettings(id: String) { overlays -= Overlay.SETTINGS; viewModelScope.launch { delay(100); openById(id) } }

    fun deepLink(uri: Uri?) {
        // Directory and README "Add to Obtainium" links can open in Orion too: they land in Orbit
        if (uri?.scheme == "obtainium") { Orbit.parseImport(uri.toString(), now()).takeIf { it.isNotEmpty() }?.let { orbitIncoming = it; tab = Tab.ORBIT }; return }
        if (uri?.scheme != "orionstore") return
        val seg = (listOfNotNull(uri.host) + uri.pathSegments).filter { it.isNotEmpty() }
        if (seg.getOrNull(0) == "settings") { openSettings(seg.getOrNull(1) ?: "none"); return }
        if (seg.getOrNull(0) != "app" || seg.getOrNull(1) == null) return
        val id = Uri.decode(seg[1])
        whenLoaded { openById(id, ctx.getString(R.string.vm_not_found_in_orion_store)) }
    }

    /** "App details in store" from system App info. Prefers the catalog entry that installed it: forks can share a package name. */
    fun showAppInfo(pkg: String) = whenLoaded {
        val k = normalizePackageName(pkg)
        val id = s.packageOwners[k]?.takeIf { it in lookup }
            ?: allApps().firstOrNull { normalizePackageName(s.resolvedPackageNames[it.id] ?: it.packageName) == k }?.id
        if (id != null) openById(id) else toast(ctx.getString(R.string.vm_this_app_isn_t_in_orion))
    }

    private fun whenLoaded(open: () -> Unit) { if (lookup.isEmpty()) pendingDeepLink = open else open() }

    /** Any APK on the phone, opened on the installer sheet. */
    fun pickApk() = viewModelScope.launch {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
            "application/vnd.android.package-archive", "application/octet-stream", "application/zip", "application/x-zip-compressed", "application/xapk", "application/vnd.apkm"))
        val u = com.orion.store.platform.ResultHost.run(pick).second?.data ?: return@launch
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u, ctx, ApkInstallActivity::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** The download queue opens as a sheet over the current screen, not as a Settings page. */
    fun openSettings(menu: String = "none") { if (menu == "queue") return open(Overlay.DOWNLOADS); settingsMenu = menu; if (Overlay.SETTINGS !in overlays) overlays += Overlay.SETTINGS }
    fun open(o: Overlay) { if (o !in overlays) overlays += o }
    fun close(o: Overlay) { overlays -= o; if (o == Overlay.PROFILE_STATS) badgeIndex = null }

    // region Orbit
    var orbitQuery by mutableStateOf(""); var orbitRefreshing by mutableStateOf(false)
    /** An add request from outside (an obtainium:// link): the Orbit tab opens its add sheet with these entries. */
    var orbitIncoming by mutableStateOf<List<OrbitApp>?>(null)

    var orbitShareAsk by mutableStateOf(false)
    /** Why an app was refused from Orbit, shown until the user taps OK. */
    var orbitRefusal by mutableStateOf<String?>(null)
    fun orbitRefuse(msg: String) { if (orbitRefusal != msg) { orbitRefusal = msg; haptic(H.WARNING) } }
    /** Set when the reminder's Share button should open that app's share sheet. */
    var orbitShareFocus by mutableStateOf<String?>(null)

    private fun considerSharePrompt() {
        val n = d.orbit.size
        if (n >= 5 && n - s.orbitSharePromptAt >= 5) orbitShareAsk = true
    }
    fun laterOrbitShare() { orbitShareAsk = false; set { it.copy(orbitSharePromptAt = d.orbit.size) } }
    fun shareFromPrompt() {
        orbitShareAsk = false
        set { it.copy(orbitSharePromptAt = d.orbit.size) }
        val git = setOf(OrbitSource.GITHUB, OrbitSource.GITLAB, OrbitSource.CODEBERG)
        orbitShareFocus = d.orbit.firstOrNull { !it.published && Orbit.sourceOf(it.url) in git }?.id ?: d.orbit.firstOrNull { !it.published }?.id
    }

    fun orbitSave(o: OrbitApp) {
        val adding = d.orbit.none { it.id == o.id }
        if (adding) Orbit.refused(o.url, o.name, o.packageName.orEmpty(), o.author)?.let { orbitRefuse(it); return }
        upd { st -> st.copy(orbit = if (st.orbit.any { it.id == o.id }) st.orbit.map { if (it.id == o.id) o else it } else st.orbit + o) }
        if (adding) considerSharePrompt()
        scheduleInstalledSync()
    }
    fun orbitRemove(id: String) { upd { st -> st.copy(orbit = st.orbit.filter { it.id != id }) }; haptic(H.MEDIUM) }

    suspend fun orbitProbe(url: String, base: OrbitApp? = null) = withContext(Dispatchers.IO) { Orbit.probe(url, s.orbitTokens(), now(), base) }

    /** Checks every source (or [ids]) again and keeps what the user edited meanwhile: only versions, time and errors are taken over. */
    fun orbitRefresh(ids: Set<String>? = null) = viewModelScope.launch {
        if (orbitRefreshing) return@launch
        orbitRefreshing = true
        val fresh = withContext(Dispatchers.IO) { Orbit.refreshAll(d.orbit.filter { ids == null || it.id in ids }, s.orbitTokens(), now()) }.associateBy { it.id }
        upd { st -> st.copy(orbit = st.orbit.map { cur -> fresh[cur.id]?.let { f -> cur.copy(versions = f.versions, checkedAt = f.checkedAt, error = f.error) } ?: cur }) }
        orbitRefreshing = false
    }

    /** Imported entries land at once, then fill in their details and releases in the background. */
    fun orbitImport(list: List<OrbitApp>) = viewModelScope.launch {
        val have = d.orbit.map { it.id }.toSet(); val fresh = list.filter { it.id !in have }
        val blocked = fresh.mapNotNull { Orbit.refused(it.url, it.name, it.packageName.orEmpty(), it.author) }.distinct()
        val ok = fresh.filter { Orbit.refused(it.url, it.name, it.packageName.orEmpty(), it.author) == null }
        if (blocked.isNotEmpty()) orbitRefuse(blocked.joinToString("\n\n"))
        if (ok.isEmpty()) { if (blocked.isEmpty()) toast(ctx.getString(R.string.vm_everything_already_in_orbit)); return@launch }
        upd { st -> st.copy(orbit = st.orbit + ok) }; haptic(H.SUCCESS); toast(if (ok.size == 1) ctx.getString(R.string.vm_joined_orbit_single, ok[0].name.ifBlank { ctx.getString(R.string.vm_one_app) }) else ctx.getString(R.string.vm_joined_orbit_many, ok.size))
        considerSharePrompt()
        ok.chunked(3).forEach { batch ->
            val done = batch.map { o -> async(Dispatchers.IO) { runCatching { Orbit.probe(o.url, s.orbitTokens(), now(), o) }.getOrDefault(o) } }.awaitAll()
            val drop = done.filter { Orbit.refused(it.url, it.name, it.packageName.orEmpty(), it.author) != null }
            if (drop.isNotEmpty()) orbitRefuse(drop.mapNotNull { Orbit.refused(it.url, it.name, it.packageName.orEmpty(), it.author) }.distinct().joinToString("\n\n"))
            val keep = done.filter { it.id !in drop.map { d -> d.id }.toSet() }.associateBy { it.id }
            upd { st -> st.copy(orbit = st.orbit.mapNotNull { if (it.id in drop.map { d -> d.id }) null else keep[it.id] ?: it }) }
        }
        scheduleInstalledSync()
    }

    /** Shares an Orbit app with everyone: opens the same ready GitHub issue the submission form makes, and counts as a contribution. */
    fun orbitPublish(o: OrbitApp) {
        val body = pretty.encodeToString(kotlinx.serialization.json.JsonArray.serializer(), kotlinx.serialization.json.JsonArray(listOf(Orbit.catalogJson(o))))
        tracker.open(submissionIssueUrl(body, 1, o.name, STORE_VERSION))
        set { it.registerSubmission(now()) }
        upd { st -> st.copy(orbit = st.orbit.map { if (it.id == o.id) it.copy(published = true) else it }) }
        haptic(H.SUCCESS)
    }
    // endregion

    fun navTo(t: Tab) {
        if (t != Tab.ABOUT && t.name.lowercase() in s.hiddenTabs) return
        haptic(H.LIGHT); val was = tab; tab = t
        if (t != was && t.platform != null) followTab()
    }

    /** New & Updated and the show-all lists are shared by the store tabs, so switching platforms refills them for the new one
     *  (New & Updated stays open, now with PC or TV apps). A collection's own list has no twin on another platform: back to its home. */
    private fun followTab() {
        when {
            expandedView == "new_updated" -> platformApps().reversed().take(18).let { if (it.isEmpty()) clearExpanded() else expandedApps = it }
            showAllSorted && viewAllApps != null -> if (returnToExpanded == "new_updated") viewAllApps = platformApps().reversed() else { clearExpanded(); returnToExpanded = null }
        }
    }

    fun toggleTheme() {
        val next = when (s.theme) { "light" -> "dusk"; "dusk" -> "dark"; "dark" -> "light"; "oled" -> "light"; else -> "dark" }
        set { it.copy(theme = next, isOled = next == "oled") }; haptic(H.MEDIUM)
    }

    fun titleClick() {
        if (s.isDevUnlocked) { toast(ctx.getString(R.string.vm_already_a_developer)); return }
        devClicks++; val left = 7 - devClicks
        if (left in 1..4) { toast(ctx.getString(R.string.vm_you_are_steps_away_from_being, left)); haptic(H.LIGHT) }
        if (devClicks >= 7) { set { it.withDevUnlocked(true) }; toast(ctx.getString(R.string.vm_you_are_now_a_developer)); haptic(H.SUCCESS); devClicks = 0 }
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
        val local = s.customBundles.map { cb -> BundleItem(cb.id, cb.title, cb.description, cb.appIds, cb.appIds.mapNotNull { id -> lookup[id]?.takeIf { it.platform == p } }, badge = ctx.getString(R.string.vm_local), icon = cb.icon) }.filter { it.apps.isNotEmpty() }
        if (local.isEmpty()) return withUpdates
        val idx = withUpdates.indexOfFirst { it.type == CollectionType.RECOMMENDATION_BUNDLES }
        if (idx == -1) return injectAfterHero(withUpdates, StoreCollection("recommendation_bundles_local", ctx.getString(R.string.vm_recommended_app_bundles), CollectionType.RECOMMENDATION_BUNDLES, ctx.getString(R.string.vm_curated_picks_for_your_platform), bundles = local))
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
