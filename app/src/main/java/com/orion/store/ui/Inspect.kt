package com.orion.store.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orion.store.data.*
import com.orion.store.platform.ApkInsights
import com.orion.store.platform.Bundles
import com.orion.store.platform.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Inspect reports from this session, keyed by app and version, so the app page can show what was found and a reopen is instant. */
object InspectCache {
    val reports = mutableStateMapOf<String, ApkInsights.Report>()
    fun key(app: AppItem) = app.id + "|" + app.latestVersion
}

/** Which copy gets read: the APK already downloaded, the one installed (when it is the current version), or a fresh download. */
private enum class InspectFrom { READY, INSTALLED, DOWNLOAD }

/** The build Download would fetch on this phone: the best matching architecture, else the plain link. */
fun inspectUrl(app: AppItem): String? {
    val abis = android.os.Build.SUPPORTED_ABIS.toList(); val raw = abis.firstOrNull().orEmpty()
    val vs = (app.availableVersions.firstOrNull()?.variants ?: emptyList()).ifEmpty { app.variants }
    val pick = vs.map { it to archCompat(it.arch, raw, abis) }.filter { it.second.first }
        .sortedWith(compareByDescending<Pair<AppVariant, Pair<Boolean, Boolean>>> { it.second.second }).firstOrNull()?.first?.url
    return sanitizeDownloadUrl(pick ?: vs.firstOrNull()?.url ?: app.downloadUrl).takeIf { it != "#" }
}

private val inspectClient by lazy { Net.client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).readTimeout(30, TimeUnit.SECONDS).build() }

private suspend fun fetchApk(url: String, into: File, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
    inspectClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) throw java.io.IOException("The server answered ${r.code}.")
        val body = r.body; val total = body.contentLength()
        into.parentFile?.mkdirs()
        body.byteStream().use { inp ->
            into.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var got = 0L; var last = 0L
                while (true) {
                    ensureActive()
                    val n = inp.read(buf); if (n < 0) break
                    out.write(buf, 0, n); got += n
                    if (got - last > 256 * 1024) { last = got; progress(got, total) }
                }
                progress(got, total)
            }
        }
    }
}

/** Package inspector for any app in the catalog, before it is installed: frameworks, trackers, permissions, libraries,
 *  compatibility and signature, read on the phone. A build that is not on the phone yet is downloaded once and kept, so Install does not fetch it again. */
@Composable
fun InspectSheet(vm: MainViewModel, app: AppItem, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val ctx = LocalContext.current; val s = LocalS.current; val d = LocalD.current; val cs = MaterialTheme.colorScheme
    val key = InspectCache.key(app)
    val ready = remember(d.readyToInstall[app.id]) { d.readyToInstall[app.id]?.let { Downloads.file(it) } }
    val local = s.installedVersions[app.id]
    val current = !local.isNullOrEmpty() && !hasAvailableUpdate(app, local, s.streamOf(app.id), s.installedBuilds[app.id])
    val installedApk = remember(app.packageName, current) {
        if (!current) null else app.packageName?.let { runCatching { File(ctx.packageManager.getApplicationInfo(it, 0).sourceDir) }.getOrNull() }
    }
    val from = when { ready != null -> InspectFrom.READY; installedApk != null -> InspectFrom.INSTALLED; else -> InspectFrom.DOWNLOAD }
    val url = remember(app) { inspectUrl(app) }
    val cached = InspectCache.reports[key] != null
    var phase by remember { mutableStateOf(if (cached) "done" else if (from == InspectFrom.DOWNLOAD) "ask" else "read") }
    var got by remember { mutableLongStateOf(0L) }; var total by remember { mutableLongStateOf(-1L) }
    var error by remember { mutableStateOf("") }; var attempt by remember { mutableIntStateOf(if (cached || from == InspectFrom.DOWNLOAD) 0 else 1) }
    var staged by remember { mutableStateOf(d.readyToInstall[app.id]) }
    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        phase = if (from == InspectFrom.DOWNLOAD) "fetch" else "read"; got = 0; total = -1
        val tmp = File(ctx.cacheDir, "inspect/${app.id.filter(Char::isLetterOrDigit)}.${fileExt(url)}")
        var keep = from != InspectFrom.DOWNLOAD
        try {
            val file = when (from) {
                InspectFrom.READY -> ready!!
                InspectFrom.INSTALLED -> installedApk!!
                InspectFrom.DOWNLOAD -> { fetchApk(url ?: throw java.io.IOException("This app has no direct APK link."), tmp) { g, t -> got = g; total = t }; phase = "read"; tmp }
            }
            val readFrom = withContext(Dispatchers.IO) { Bundles.parts(ctx, file).firstOrNull() ?: file }
            val r = withContext(Dispatchers.IO) { ApkInsights.read(ctx, readFrom) } ?: throw java.io.IOException("The file isn't a package Orion can read.")
            if (from == InspectFrom.DOWNLOAD) { vm.stageInspected(app, file, url); staged = d.readyToInstall[app.id] ?: apkFileName(app, app.latestVersion, url); keep = true }
            else staged = d.readyToInstall[app.id]
            InspectCache.reports[key] = r; phase = "done"; vm.haptic(H.SUCCESS)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Something went wrong."; phase = "failed"; vm.haptic(H.ERROR) }
        finally { if (!keep) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { tmp.delete() } }
    }
    SheetTitle("Inspect package", Sym.Science, when (from) {
        InspectFrom.READY -> "From the APK you downloaded"
        InspectFrom.INSTALLED -> "From the copy on this phone · v${local.orEmpty().removePrefix("v")}"
        InspectFrom.DOWNLOAD -> "${app.name} v${app.latestVersion.removePrefix("v")}"
    })
    Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>(); val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(phase, transitionSpec = { fadeIn(fx) togetherWith fadeOut(fast) }, label = "inspect") { p ->
            when (p) {
                "ask" -> Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MorphBlob(listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny), 0, 96.dp, cs.secondaryContainer) {
                        AppIcon(app, 52.dp)
                    }
                    Text("See inside before you install", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
                    Text("Orion downloads this build${app.size.takeIf { it.isNotBlank() && it != "Varies" }?.let { " ($it)" }.orEmpty()}, reads it on your phone, and keeps the file. Install after that uses the same download.",
                        Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                    CtaButton(if (url == null) "No direct APK to read" else "Download and inspect", { vm.haptic(H.MEDIUM); attempt++ }, Modifier.padding(top = 20.dp), Sym.Science, enabled = url != null)
                }
                "fetch", "read" -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val frac = if (p == "fetch" && total > 0) (got.toFloat() / total).coerceIn(0f, 1f) else null
                    val shown by animateFloatAsState(frac ?: 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dl")
                    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                        if (frac != null) CircularWavyProgressIndicator({ shown }, Modifier.matchParentSize()) else LoadingIndicator(Modifier.matchParentSize())
                        if (frac != null) AppIcon(app, 56.dp)
                    }
                    Text(if (p == "fetch") "Downloading to read it" else "Reading its code", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(if (p == "fetch") (if (total > 0) "${CatalogProcessor.formatSize(got)} of ${CatalogProcessor.formatSize(total)}" else CatalogProcessor.formatSize(got))
                        else "Libraries, trackers and permissions...", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                "failed" -> Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MorphBlob(listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny), 0, 88.dp, cs.errorContainer) { Icon(Sym.ErrorOutline, null, Modifier.size(36.dp), tint = cs.onErrorContainer) }
                    Text("Couldn't read this package", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(error, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                    CtaButton("Try again", { attempt++ }, Modifier.padding(top = 20.dp), Sym.Refresh, tonal = true)
                }
                else -> InspectCache.reports[key]?.let { r ->
                    Column(Modifier.fillMaxWidth()) {
                        Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(28.dp), color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                MorphBlob(listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf), 0, 56.dp, cs.onSecondaryContainer.copy(.1f)) {
                                    Icon(if (r.trackers.isEmpty()) Sym.VerifiedFilled else Sym.Radar, null, Modifier.size(26.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(when (r.trackers.size) { 0 -> "No trackers found"; 1 -> "1 tracker inside"; else -> "${r.trackers.size} trackers inside" }, style = MaterialTheme.typography.titleLargeEmphasized)
                                    Text("${r.perms.size} permissions · ${r.perms.count { it.dangerous }} sensitive", style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer.copy(.8f))
                                }
                            }
                        }
                        InsightChips(r, Modifier.padding(top = 12.dp))
                        InspectPage(r)
                        staged?.let { file ->
                            CtaButton("Install", { vm.haptic(H.MEDIUM); vm.installFile(app, file); onDismiss() }, Modifier.padding(top = 16.dp), Sym.InstallMobile)
                        }
                    }
                }
            }
        }
    }
}
