package com.orion.store

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.orion.store.data.*
import com.orion.store.platform.AppTracker
import com.orion.store.platform.DlStatus
import com.orion.store.platform.Downloads
import com.orion.store.platform.WirelessAdb
import com.orion.store.platform.DhizukuBridge
import com.orion.store.ui.Store
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Background updates that run with Orion closed, and across reboots (persisted job).
 * Each pass: refresh the catalog, read installed versions from Android itself, download what is newer, check the APK is the
 * same package, then install it over the existing app. Root and Shizuku install anything; without them Android 12+ installs
 * updates silently for apps Orion installed. Whatever can't finish here is logged, kept ready to install, and reported on the next start.
 */
object AutoUpdate {
    private const val JOB_ID = 7340
    private const val CHANNEL = "orion_updates"
    /** Set by OrionActivity: while the store is on screen it handles updates itself, so the job steps aside. */
    @Volatile var uiVisible = false

    @Suppress("DEPRECATION")
    fun schedule(ctx: Context, s: Settings) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        if (!s.autoUpdateEnabled) { js.cancel(JOB_ID); return }
        val net = if (s.wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
        // Rescheduling an identical job restarts its clock, so only touch it when it is missing or the network rule changed
        if (js.getPendingJob(JOB_ID)?.networkType == net) return
        js.schedule(JobInfo.Builder(JOB_ID, ComponentName(ctx, AutoUpdateJob::class.java))
            .setPeriodic(6 * 3_600_000L, 3_600_000L).setRequiredNetworkType(net).setPersisted(true)
            .apply { if (Build.VERSION.SDK_INT >= 26) setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true) }
            .build())
    }

    suspend fun run(ctx: Context) {
        Store.init(ctx); val s0 = Store.settings.value
        if (!s0.autoUpdateEnabled || uiVisible) return
        Downloads.init(ctx, s0.forcedStoreUpdate?.downloadFileName)
        val tracker = AppTracker(ctx).apply { root = s0.rootMode }
        val apps = catalog(ctx, s0); if (apps.isEmpty()) return
        val now = System.currentTimeMillis()
        // Trust Android, not the last sync: the user may have updated or removed apps since Orion last ran
        val live = s0.installedVersions.mapNotNull { (id, _) ->
            val pkg = s0.resolvedPackageNames[id] ?: apps.firstOrNull { it.id == id }?.packageName ?: return@mapNotNull null
            tracker.appInfo(pkg).takeIf { it.installed }?.let { id to it.version }
        }.toMap()
        val todo = availableUpdates(apps, s0.copy(installedVersions = live), now)
            .filter { it.installsHere() && it.id !in Store.data.value.activeDownloads && (s0.resolvedPackageNames[it.id] ?: it.packageName) != ctx.packageName }
        val log = mutableListOf<AutoResult>()
        for (app in todo) {
            if (uiVisible) break
            val r = update(ctx, tracker, app, s0)
            if (r != null) log += r
        }
        if (log.isNotEmpty()) Store.settings.update { it.copy(autoLog = (it.autoLog + log).takeLast(30)) }
        Store.settings.save(); Store.data.save()
        notifySummary(ctx, log)
    }

    /** Catalog for this pass: fresh when the network allows, otherwise the last one the store cached. */
    private suspend fun catalog(ctx: Context, s: Settings): List<AppItem> = withContext(Dispatchers.IO) {
        val repo = Repository(ctx)
        val fresh = runCatching {
            val r = repo.loadCatalog(s, false); val m = r.mirror.orEmpty()
            if (r.failed || m.isEmpty()) null else CatalogProcessor.process(r.rawApps, m) + Store.data.value.importedApps.map { CatalogProcessor.processItem(it, m) }
        }.getOrNull()
        val base = fresh?.takeIf { it.isNotEmpty() } ?: (repo.cached()?.apps.orEmpty() + Store.data.value.importedApps)
        // Orbit apps are checked at their own sources on the same pass, keeping anything the user edited meanwhile
        val now = System.currentTimeMillis()
        val orbit = runCatching { Orbit.refreshAll(Store.data.value.orbit, s.orbitTokens(), now) }.getOrNull()?.associateBy { it.id }
        if (orbit != null) Store.data.update { st -> st.copy(orbit = st.orbit.map { cur -> orbit[cur.id]?.let { f -> cur.copy(versions = f.versions, checkedAt = f.checkedAt, error = f.error) } ?: cur }) }
        base + Store.data.value.orbit.filter { !it.paused }.map { it.toItem() }
    }

    /** One app: download (or reuse a finished download), verify, install. Null = nothing worth reporting (download-only mode). */
    private suspend fun update(ctx: Context, tracker: AppTracker, app: AppItem, s: Settings): AutoResult? {
        val pkg = s.resolvedPackageNames[app.id] ?: app.packageName.orEmpty()
        val version = downloadVersionMeta(app, null, s.streamOf(app.id)).first
        fun result(ok: Boolean, error: String? = null) = AutoResult(app.id, app.name, version, ok, error, System.currentTimeMillis())
        // The build of the app's own channel: a Beta user gets the next Beta, not the Stable APK under a Beta version
        val variants = app.availableVersions.firstOrNull { it.version == version }?.variants?.ifEmpty { null } ?: app.variants
        val url = sanitizeDownloadUrl(variants.firstOrNull()?.url ?: app.downloadUrl)
        if (url == "#" || !url.startsWith("http")) return result(false, "No direct APK download for this app.")
        val name = apkFileName(app, version)
        val f = Downloads.file(name) ?: return result(false, "Invalid file name.")
        if (!f.exists() || f.length() == 0L) {
            runCatching { Downloads.start(url, name) }.onFailure { return result(false, "Download could not start.") }
            val st = withTimeoutOrNull(20 * 60_000L) { Downloads.state.first { it[name]?.status != DlStatus.RUNNING } }?.get(name)?.status
            Downloads.forget(name)
            if (st != DlStatus.SUCCESSFUL || Downloads.status(name).status != DlStatus.SUCCESSFUL) return result(false, "Download failed. Orion retries on its next pass.")
        }
        // Never install something other than what is installed: a wrong or tampered file stops here
        val actual = withContext(Dispatchers.IO) { if (tracker.isValidApk(f)) tracker.archivePackage(f) else null }
        if (actual == null) { f.delete(); return result(false, "The downloaded file is not a valid APK.") }
        if (pkg.isNotEmpty() && actual != pkg) { f.delete(); return result(false, "The download is a different app ($actual), so it was not installed.") }
        val now = System.currentTimeMillis()
        Store.data.update { if (app.id in it.readyToInstall) it else it.completeDownload(app.id, name, now) }
        if (!s.autoInstall) { notifyReady(ctx, app, name); return null }

        val error = install(ctx, tracker, s, f, actual, app.name)
        if (error != null) { notifyReady(ctx, app, name, error); return result(false, error) }
        val ver = tracker.appInfo(actual).version
        Store.settings.update { it.copy(installedVersions = it.installedVersions + (app.id to ver), lastRemoteVersions = it.lastRemoteVersions + (app.id to ver),
            installedBuilds = it.installedBuilds + (app.id to InstalledBuild(ver, version)), packageOwners = it.packageOwners + (normalizePackageName(actual) to app.id)) }
        Store.data.update { it.recordInstalled(app.id, ver, null, now).copy(readyToInstall = it.readyToInstall - app.id) }
        f.delete()
        return result(true)
    }

    /** Null on success, else why it failed, worded for the user. Also used by the store for auto-updates it downloads itself. */
    suspend fun install(ctx: Context, tracker: AppTracker, s: Settings, f: File, pkg: String, name: String): String? {
        tracker.clash(f)?.let { return clashText(it.second, name).second }
        if (s.rootMode || s.useShizuku) return runCatching { tracker.installShizuku(f.name, s.updateLock, if (s.installOptions) s.installFlags else emptyList()) }.exceptionOrNull()?.let { installFailureText(it.message, name).second }
        if (s.useDhizuku) return runCatching { DhizukuBridge.install(ctx, f) }.exceptionOrNull()?.let { installFailureText(it.message, name).second }
        // Skip the 24-hour wait: the same shell-user install as the button, falling back to a session when adbd is unreachable
        if (s.directInstall && WirelessAdb.supported && runCatching { WirelessAdb.install(ctx, f) }.isSuccess) return null
        if (Build.VERSION.SDK_INT < 31) return "Android ${Build.VERSION.RELEASE} needs you to confirm installs. Root or Shizuku make them silent."
        if (!tracker.canRequestInstalls()) return "Allow Orion to install apps, then updates can install on their own."
        val (status, msg) = sessionInstall(ctx, f, pkg)
        return when (status) {
            PackageInstaller.STATUS_SUCCESS -> null
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "Android asked to confirm this one. That happens for apps another store installed, or apps built for an old Android version."
            else -> installFailureText(msg, name).second
        }
    }


    private val sessions = ConcurrentHashMap<Int, CompletableDeferred<Pair<Int, String?>>>()

    /** PackageInstaller session that asks for no user action. Android allows it for apps Orion installed (Android 12+). */
    private suspend fun sessionInstall(ctx: Context, f: File, pkg: String): Pair<Int, String?> = withContext(Dispatchers.IO) {
        val parts = com.orion.store.platform.Bundles.parts(ctx, f).ifEmpty { listOf(f) }
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg); setSize(parts.sumOf { it.length() }); setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params); val done = CompletableDeferred<Pair<Int, String?>>(); sessions[id] = done
        try {
            pi.openSession(id).use { session ->
                parts.forEachIndexed { i, part ->
                    session.openWrite(if (i == 0) "base.apk" else "split$i.apk", 0, part.length()).use { out -> part.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                }
                val i = Intent(ctx, InstallReceiver::class.java).setPackage(ctx.packageName)
                session.commit(PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender)
            }
            withTimeoutOrNull(3 * 60_000L) { done.await() } ?: (PackageInstaller.STATUS_FAILURE to "Android did not answer in time. Orion retries on its next pass.")
        } catch (e: Exception) {
            runCatching { pi.abandonSession(id) }; PackageInstaller.STATUS_FAILURE to e.message
        } finally { sessions -= id }
    }

    /** Session results. A pending confirmation is abandoned: the file stays ready, and the user installs it with one tap from the notification. */
    fun onInstallResult(ctx: Context, i: Intent) {
        if (com.orion.store.platform.SessionInstall.onResult(ctx, i)) return
        val id = i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) runCatching { ctx.packageManager.packageInstaller.abandonSession(id) }
        sessions[id]?.complete(status to i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }

    private fun channel(ctx: Context) = NotificationManagerCompat.from(ctx).createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("Orion Updates").build())

    /** Opens the app's page with the file ready, the same extras the store's own "ready" notification uses. */
    private fun notifyReady(ctx: Context, app: AppItem, file: String, error: String? = null) = runCatching {
        channel(ctx)
        val i = Intent(ctx, OrionActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("appId", app.id).putExtra("fileName", file)
        NotificationManagerCompat.from(ctx).notify(app.id.hashCode(), NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(if (error == null) "${app.name} update ready" else "${app.name} needs you").setContentText(error ?: "Tap to install.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(error ?: "Tap to install.")).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, app.id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
    }

    private fun notifySummary(ctx: Context, log: List<AutoResult>) = runCatching {
        val ok = log.filter { it.ok }; if (ok.isEmpty()) return@runCatching
        channel(ctx)
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        NotificationManagerCompat.from(ctx).notify(JOB_ID, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(com.orion.store.R.drawable.ic_launcher_monochrome)
            .setContentTitle(if (ok.size == 1) "${ok[0].name} updated" else "${ok.size} apps updated").setContentText(ok.joinToString { it.name }).setAutoCancel(true)
            .apply { launch?.let { setContentIntent(PendingIntent.getActivity(ctx, JOB_ID, it, PendingIntent.FLAG_IMMUTABLE)) } }.build())
    }
}

class AutoUpdateJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    override fun onStartJob(p: JobParameters): Boolean {
        job = scope.launch { runCatching { AutoUpdate.run(applicationContext) }; jobFinished(p, false) }
        return true
    }
    // Stopped mid-pass (constraints lost, time limit): half-done downloads resume next pass, so ask to be retried
    override fun onStopJob(p: JobParameters): Boolean { job?.cancel(); runCatching { Store.settings.save(); Store.data.save() }; return true }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) = AutoUpdate.onInstallResult(ctx, i)
}
