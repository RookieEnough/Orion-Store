package com.orion.store.data

import androidx.core.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File
import kotlin.math.floor
import kotlin.math.max

@Serializable data class CleanupEntry(val fileName: String, val timestamp: Long)
@Serializable data class CustomFont(val id: String, val name: String, val fileName: String, val mimeType: String, val path: String, val addedAt: Long)
@Serializable data class LibraryEntry(val status: String, val fileName: String? = null, val version: String? = null, val downloadedAt: Long, val installedAt: Long? = null, val lastSeenAt: Long)
@Serializable data class TabViewState(val query: String = "", val category: String = "All", val sort: SortOption = SortOption.NEWEST, val filterFavorites: Boolean = false)
/** What Orion installed: the app's own versionName and the catalog version it was offered as, so later checks compare like with like. */
@Serializable data class InstalledBuild(val versionName: String, val offered: String)
@Serializable data class IgnoredUpdate(val type: String, val timestamp: Long? = null, val version: String? = null)
@Serializable data class UserProfile(val name: String, val avatarId: String? = null, val avatarUrl: String? = null, val createdAt: Long = 0)
@Serializable data class CustomBundle(val id: String, val title: String, val description: String, val icon: String, val appIds: List<String>, val createdAt: Long = 0, val submittedAt: Long? = null)
@Serializable data class AutoResult(val appId: String, val name: String, val version: String, val ok: Boolean, val error: String? = null, val at: Long)
/** A palette from the palette maker: seed colors as ARGB. A null secondary or tertiary is derived from the primary. */
@Serializable data class CustomPalette(val id: String, val name: String, val primary: Int, val secondary: Int? = null, val tertiary: Int? = null, val style: String = "balanced")
@Serializable data class ForcedSnapshot(val schemaVersion: Int = 1, val targetVersion: String, val downloadFileName: String, val status: String, val downloadCompleted: Boolean, val exported: Boolean)

@Serializable data class Settings(
    val theme: String = "light",
    val palette: String = "orion",
    val appFont: String = "spaceGrotesk",
    val customFonts: List<CustomFont> = emptyList(),
    val customPalettes: List<CustomPalette> = emptyList(),
    val storeLayout: String = "modern",
    val isOled: Boolean = false,
    val materialYouEnabled: Boolean = false,
    val hiddenTabs: List<String> = emptyList(),
    val virusTotalApiKey: String = "",
    val autoUpdateEnabled: Boolean = false,
    /** With auto-update on, the background job also installs what it downloads, even while Orion is closed. */
    val autoInstall: Boolean = false,
    /** Background update results not yet shown; the next app start reports them, then clears the list. */
    val autoLog: List<AutoResult> = emptyList(),
    /** Anti-farming: reward times in the last 24 hours, and today's game XP (keyed by epoch day). */
    val adStamps: List<Long> = emptyList(), val gameXpDay: Long = 0, val gameXpToday: Long = 0,
    val wifiOnly: Boolean = false,
    /** Beta, Alpha and Nightly builds are hidden wherever a stable release exists; overrides per-app channels while on. */
    val stableOnly: Boolean = false,
    val deleteApk: Boolean = false,
    val useShizuku: Boolean = false,
    /** Silent installs and uninstalls through Dhizuku's shared device owner. */
    val useDhizuku: Boolean = false,
    /** Root or Shizuku installs open a sheet of pm install options first; [installFlags] are the ones picked last time. */
    val installOptions: Boolean = false, val installFlags: List<String> = emptyList(),
    /** Launcher icon and dock style, see [APP_ICONS] and [DOCKS]. */
    val appIcon: String = "default", val navDock: String = "pill",
    /** Ads counted toward each locked icon or dock, from zero for everyone: only ads watched while [unlockTarget] is set count. */
    val unlockProgress: Map<String, Int> = emptyMap(), val unlockTarget: String? = null,
    /** Run every privileged action (installs, Guardian) through `su` instead of Shizuku or wireless ADB. */
    val rootMode: Boolean = false,
    /** Root installs ask Android 14+ to make other stores get consent before updating apps Orion installed. */
    val updateLock: Boolean = false,
    /** Settings hub layout: quick tile ids (null = default set) and category order (empty = default). */
    val quickTiles: List<String>? = null,
    val settingsOrder: List<String> = emptyList(),
    /** Auto backup of the identity to a user-picked document (any cloud app's provider). Every = off, day, week or month. */
    val cloudBackupUri: String = "", val cloudBackupEvery: String = "off", val lastCloudBackup: Long = 0,
    /** Install through Orion's built-in wireless ADB (shell user), skipping the 24-hour unverified-app wait. */
    val directInstall: Boolean = false,
    /** What to do when direct install has no Wi-Fi: "" = ask each time, "installer" = use Android's installer. */
    val offWifiInstall: String = "",
    val installerPreference: String = "system",
    val installerPackage: String = "",
    val installerLabel: String = "",
    val disableAnimations: Boolean = false,
    /** auto, lite, balanced or boost. Auto is Lite on low-end phones, Balanced elsewhere. */
    val perfMode: String = "auto",
    val compactMode: Boolean = false,
    /** Classic rows show a two-line "About this app" preview that expands in place. */
    val showAppAbout: Boolean = false,
    val bottomNavScale: Float = .85f,
    val highRefreshRate: Boolean = true,
    /** Status bar hidden while Orion is open; a swipe from the top peeks it. */
    val immersive: Boolean = true,
    val pullToRefreshCharacter: String = "cat",
    val hapticEnabled: Boolean = true,
    val glassEffect: Boolean = true,
    val isDevUnlocked: Boolean = false,
    val isLegend: Boolean = false,
    val isContributor: Boolean = false,
    val adWatchCount: Int = 0,
    /** Highest Fuel the Code chapter the pilot has been told about; drives the one-time "new mission" notice. */
    val questChapterSeen: Int = 1,
    /** Pilot card back: strokes as "x,y x,y|..." in 0..1 pad space, plus the chosen ink. */
    val pilotSignature: String = "", val signatureInk: Int = 0,
    /** One-time flags for the 2.0 native switch: web data carried over, welcome shown. */
    val webMigrated: Boolean = false, val seenNativeWelcome: Boolean = false,
    val submissionCount: Int = 0,
    val changeRequestCount: Int = 0,
    val lastSubmissionTime: Long = 0,
    val lastLeaderboardSubmissionTime: Long = 0,
    /** XP on the leaderboard after the last upload; a joined pilot re-uploads by itself once a day when it grew. */
    val leaderboardXp: Long = 0,
    /** The occasional support reminder after installs: when it last showed, and the user's "don't ask again". */
    val lastDonateNudge: Long = 0, val donateNudgeOff: Boolean = false,
    val useRemoteJson: Boolean = true,
    val loadLocalData: Boolean = false,
    val githubToken: String = "",
    val forcedStoreUpdate: ForcedSnapshot? = null,
    val installedVersions: Map<String, String> = emptyMap(),
    val lastRemoteVersions: Map<String, String> = emptyMap(),
    val appStreams: Map<String, String> = emptyMap(),
    val installedBuilds: Map<String, InstalledBuild> = emptyMap(),
    val resolvedPackageNames: Map<String, String> = emptyMap(),
    val packageOwners: Map<String, String> = emptyMap(),
    val ignoredUpdates: Map<String, IgnoredUpdate> = emptyMap(),
    /** Per-app manage choice (false = out of My Apps and updates); apps without one follow [skipOtherStores]. */
    val appManage: Map<String, Boolean> = emptyMap(),
    val skipOtherStores: Boolean = false,
    /** Installer package of each installed catalog app, from the last package sync ("" = none recorded). */
    val installSources: Map<String, String> = emptyMap(),
    /** My Apps filter pill order (enum names); missing ones follow in default order. */
    val myAppsFilters: List<String> = emptyList(),
    val hasSeenModernUITutorial: Boolean = false,
    val userProfile: UserProfile? = null,
    val customBundles: List<CustomBundle> = emptyList(),
    val removedBundleApps: Map<String, List<String>> = emptyMap(),
    val extraBundleApps: Map<String, List<String>> = emptyMap(),
    val gameXP: Long = 0,
    val dinoHighScore: Long = 0,
    val unlockedBadges: List<String> = emptyList(),
    val showcasedBadges: List<String> = emptyList(),
    val localMaintenanceMode: Boolean = false,
    val hiddenInstallers: List<String> = emptyList(),
    val seenFlags: Set<String> = emptySet(),
    val devConfigEnabled: Boolean = false,
    val devConfigUrl: String = "",
    /** Orion AI: the button on store tabs, chat sounds, and the provider in use. Keys stay on this device and only go to their own provider. */
    val aiButton: Boolean = true,
    val aiSounds: Boolean = true,
    val aiProvider: String = "gemini",
    val aiKeys: Map<String, String> = emptyMap(),
    val aiModels: Map<String, String> = emptyMap(),
    val aiBaseUrl: String = "",
    /** The on-device model in use, and where the user dragged the AI star (fractions of the screen, -1 = default spot). */
    val aiLocalModel: String = "",
    val aiFabX: Float = -1f, val aiFabY: Float = -1f,
    @Transient val coinFlipHintCount: Int = 0,
    /** The sideloading-protection shield in the header, on whenever the phone has the developer-verification component. Off only from Orion Xtra. */
    val verifierBadge: Boolean = true,
) {
    fun toggleHiddenTab(tab: String): Settings {
        val next = if (tab in hiddenTabs) hiddenTabs - tab else hiddenTabs + tab
        return if (listOf("android", "pc", "tv").none { it !in next }) this else copy(hiddenTabs = next)
    }
    fun withInstaller(pref: String, pkg: String = "", label: String = "") =
        copy(installerPreference = pref, installerPackage = if (pref == "package") pkg else "", installerLabel = if (pref == "package") label else "")
    val perf get() = if (perfMode == "auto") (if (Device.lowEnd) "lite" else "balanced") else perfMode
    /** Endless decorative motion (turning shapes, pulses, hero auto-advance, tilt) stops in Lite and with animations off. */
    val calm get() = disableAnimations || perf == "lite"
    fun streamOf(id: String) = if (stableOnly) "Stable" else appStreams[id] ?: "Stable"
    fun hideInstaller(pkg: String) = if (pkg in hiddenInstallers) this else copy(hiddenInstallers = hiddenInstallers + pkg)
    fun incrementCoinFlipHint() = copy(coinFlipHintCount = minOf(3, coinFlipHintCount + 1))
    fun withPtrCharacter(c: String) = copy(pullToRefreshCharacter = if (c == "pikachu") "pokeball" else c)
    fun withDevUnlocked(v: Boolean) = copy(isDevUnlocked = v, localMaintenanceMode = if (v) localMaintenanceMode else false)
    /** Null when an ad may pay out now, else the user-facing reason it can't. */
    fun adBlockReason(now: Long): String? {
        val recent = adStamps.filter { now - it < Xp.DAY_MS }
        return when {
            recent.size >= Xp.AD_DAILY -> "You've fueled the code ${Xp.AD_DAILY} times today. Come back tomorrow, pilot."
            recent.any { now - it < Xp.AD_GAP_MS } -> "Catch your breath. The next mission opens in a few seconds."
            else -> null
        }
    }
    fun incrementAdWatch(now: Long): Settings {
        if (adBlockReason(now) != null) return this
        val n = adWatchCount + 1
        val t = unlockTarget; val need = (APP_ICONS + DOCKS).firstOrNull { it.id == t }?.ads ?: 0; val got = (unlockProgress[t] ?: 0) + 1
        return copy(adWatchCount = n, isContributor = isContributor || n >= 3, isLegend = isLegend || n >= 25, adStamps = adStamps.filter { now - it < Xp.DAY_MS } + now,
            unlockProgress = if (t != null && need > 0) unlockProgress + (t to minOf(got, need)) else unlockProgress, unlockTarget = t.takeIf { need > 0 && got < need })
    }
    fun registerSubmission(now: Long) = copy(submissionCount = submissionCount + 1, lastSubmissionTime = now)
    fun registerChangeRequest(now: Long) = copy(changeRequestCount = changeRequestCount + 1, submissionCount = submissionCount + 1, lastSubmissionTime = now)
    fun withSubmissionCount(c: Double) = copy(submissionCount = if (c.isFinite()) max(0.0, floor(c)).toInt() else 0)
    fun ignoreUpdate(id: String, type: String, version: String?, now: Long) = copy(ignoredUpdates = ignoredUpdates + (id to IgnoredUpdate(type, now, version)))
    /** Stores an override only when it differs from what the store filter would decide. */
    fun withManaged(id: String, on: Boolean) = copy(appManage = appManage - id).let { if (isManaged(id, it) == on) it else it.copy(appManage = appManage + (id to on)) }
    fun withProfile(p: UserProfile?, now: Long) = copy(userProfile = p?.copy(createdAt = userProfile?.createdAt ?: now))
    fun removeCustomFont(id: String) = copy(customFonts = customFonts.filterNot { it.id == id }, appFont = if (appFont == "custom:$id") "spaceGrotesk" else appFont)
    fun toggleBundleAppRemoval(bundleId: String, appId: String): Settings {
        val cur = removedBundleApps[bundleId].orEmpty()
        return copy(removedBundleApps = removedBundleApps + (bundleId to if (appId in cur) cur - appId else cur + appId))
    }
    fun addAppToBundle(bundleId: String, appId: String): Settings {
        val cur = extraBundleApps[bundleId].orEmpty()
        return if (appId in cur) this else copy(extraBundleApps = extraBundleApps + (bundleId to cur + appId))
    }
    fun removeAppFromBundleExtras(bundleId: String, appId: String) = copy(extraBundleApps = extraBundleApps + (bundleId to extraBundleApps[bundleId].orEmpty() - appId))
    fun resetBundle(bundleId: String) = copy(removedBundleApps = removedBundleApps - bundleId, extraBundleApps = extraBundleApps - bundleId)
    /** Max 50 per run and [Xp.GAME_DAILY] per day, so the mini game can't be ground into the leaderboard. */
    fun addGameXP(amount: Double, now: Long): Settings {
        val day = now / Xp.DAY_MS; val today = if (gameXpDay == day) gameXpToday else 0
        val safe = minOf(if (amount.isFinite()) floor(amount.coerceIn(0.0, 50.0)).toLong() else 0, Xp.GAME_DAILY - today).coerceAtLeast(0)
        return copy(gameXP = max(0, gameXP + safe), gameXpDay = day, gameXpToday = today + safe)
    }
    fun updateDinoHighScore(score: Long) = copy(dinoHighScore = max(dinoHighScore, max(0, score)))
    fun unlockBadge(id: String) = if (id in unlockedBadges) this else copy(unlockedBadges = unlockedBadges + id)
    fun toggleShowcasedBadge(id: String) = when {
        id in showcasedBadges -> copy(showcasedBadges = showcasedBadges - id)
        showcasedBadges.size >= 3 -> this
        else -> copy(showcasedBadges = showcasedBadges + id)
    }
    fun setShowcasedBadgeAt(index: Int, id: String): Settings {
        if (index !in 0..2 || showcasedBadges.getOrNull(index) == id || id in showcasedBadges) return this
        val next = showcasedBadges.toMutableList()
        if (index >= next.size) { if (next.size < 3) next += id } else next[index] = id
        return copy(showcasedBadges = next.take(3))
    }
}

@Serializable data class DataState(
    @Transient val apps: List<AppItem> = emptyList(),
    val importedApps: List<AppItem> = emptyList(),
    val tabs: Map<String, TabViewState> = mapOf("android" to TabViewState(), "pc" to TabViewState(), "tv" to TabViewState()),
    @Transient val activeDownloads: Map<String, String> = emptyMap(),
    @Transient val downloadProgress: Map<String, Float> = emptyMap(),
    @Transient val downloadStatus: Map<String, String> = emptyMap(),
    val persistedDownloads: Map<String, String> = emptyMap(),
    val readyToInstall: Map<String, String> = emptyMap(),
    val pendingCleanup: Map<String, CleanupEntry> = emptyMap(),
    val appLibrary: Map<String, LibraryEntry> = emptyMap(),
    val favorites: List<String> = emptyList(),
) {
    fun tab(t: String) = tabs[t] ?: TabViewState()
    fun withTab(t: String, fn: (TabViewState) -> TabViewState) = copy(tabs = tabs + (t to fn(tab(t))))
    fun startDownload(id: String, dlId: String, file: String) = copy(
        activeDownloads = activeDownloads + (id to "$dlId|$file"), persistedDownloads = persistedDownloads + (id to "$dlId|$file"),
        downloadStatus = downloadStatus + (id to "PENDING"), readyToInstall = readyToInstall - id,
    )
    fun updateDownload(id: String, progress: Float, status: String) = copy(downloadProgress = downloadProgress + (id to progress), downloadStatus = downloadStatus + (id to status))
    fun completeDownload(id: String, file: String, now: Long): DataState {
        val prev = appLibrary[id]
        return copy(
            activeDownloads = activeDownloads - id, persistedDownloads = persistedDownloads - id, downloadProgress = downloadProgress - id, downloadStatus = downloadStatus - id,
            readyToInstall = readyToInstall + (id to file),
            appLibrary = appLibrary + (id to LibraryEntry("downloaded", file, prev?.version, prev?.downloadedAt ?: now, prev?.installedAt, now)),
        )
    }
    fun failDownload(id: String) = copy(activeDownloads = activeDownloads - id, persistedDownloads = persistedDownloads - id)
    fun cancelDownload(id: String) = copy(activeDownloads = activeDownloads - id, persistedDownloads = persistedDownloads - id, downloadProgress = downloadProgress - id, downloadStatus = downloadStatus - id)
    fun recordInstalled(id: String, version: String?, file: String?, now: Long): DataState {
        val prev = appLibrary[id]
        return copy(appLibrary = appLibrary + (id to LibraryEntry("installed", file?.ifEmpty { null } ?: prev?.fileName, version?.ifEmpty { null } ?: prev?.version, prev?.downloadedAt ?: now, prev?.installedAt ?: now, now)))
    }
    fun removeLibraryFile(id: String, now: Long) = appLibrary[id]?.let { copy(appLibrary = appLibrary + (id to it.copy(fileName = null, lastSeenAt = now))) } ?: this
    fun toggleFavorite(id: String) = copy(favorites = if (id in favorites) favorites - id else favorites + id)
}

class Persisted<T>(file: File, private val serializer: KSerializer<T>, default: T, scope: CoroutineScope) {
    private val atomic = AtomicFile(file)
    val flow = MutableStateFlow(runCatching { json.decodeFromString(serializer, atomic.readFully().decodeToString()) }.getOrDefault(default))
    val value: T get() = flow.value
    fun update(fn: (T) -> T) = flow.update(fn)

    /** Writes now. Background jobs call this before finishing: the process may die before the debounced write lands. */
    fun save(state: T = value) = synchronized(atomic) {
        val text = json.encodeToString(serializer, state)
        // Transient-only changes (download progress ticks) serialize the same; skip rewriting an identical file
        if (text == written) return@synchronized
        val out = atomic.startWrite()
        runCatching { out.write(text.toByteArray()); atomic.finishWrite(out); written = text }.onFailure { atomic.failWrite(out) }
    }
    private var written: String? = null

    init {
        @OptIn(FlowPreview::class)
        scope.launch(Dispatchers.IO) { flow.drop(1).debounce(250).collect { save(it) } }
    }
}
