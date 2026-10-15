package com.orion.store.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.orion.store.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MICROG_PKGS = listOf("app.revanced.android.gms", "com.mgoogle.android.gms")

@Composable
private fun streamColor(t: String) = when (t) {
    "Stable" -> Safe; "Beta" -> MaterialTheme.colorScheme.primary
    "Alpha" -> Color(0xFFF97316); "Nightly" -> Color(0xFFA855F7); else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun StreamBadge(t: String) = Tag(t.uppercase(), streamColor(t))

@Composable
fun DetailScreen(vm: MainViewModel, base: AppItem) {
    val s = LocalS.current; val d = LocalD.current; val scope = rememberCoroutineScope(); val cs = MaterialTheme.colorScheme
    val app = remember(base, s.resolvedPackageNames) { base.copy(packageName = s.resolvedPackageNames[base.id] ?: base.packageName) }
    val local = s.installedVersions[app.id]
    val stream = effectiveStreamFor(app, s)
    val needsUpdate = hasEligibleUpdate(app, local, s)
    val prereleaseBlocked = s.excludePrereleases && isPrereleaseOnly(app)
    val installed = !local.isNullOrEmpty(); val upToDate = installed && !needsUpdate
    val activeId = d.activeDownloads[app.id]; val cleanup = d.pendingCleanup[app.id]?.fileName; val ready = d.readyToInstall[app.id]
    val status = d.downloadStatus[app.id]; val progress = d.downloadProgress[app.id] ?: 0f
    val installing = vm.installingId == app.id; val scanning = vm.scanningId == app.id
    val android = app.platform == Platform.ANDROID
    val fallback = app.latestVersion == "Unknown" || app.version == "View on GitHub"
    val hasTarget = (app.downloadUrl.isNotEmpty() && app.downloadUrl != "#") || app.variants.isNotEmpty() || app.availableVersions.isNotEmpty()
    val unavailable = ready == null && cleanup == null && !installed && !hasTarget && app.latestVersion == "Latest" && app.size == "Varies"
    val lower = app.downloadUrl.lowercase()
    val external = (lower.startsWith("http://") || lower.startsWith("https://")) && listOf(".apk", ".exe", ".zip", ".dmg").none(lower::endsWith) && "github.com" !in lower
    val domain = if (external) runCatching { app.downloadUrl.toUri().host!!.replace("www.", "") }.getOrDefault("Source") else "External Source"
    val fav = app.id in d.favorites

    var target by remember(app.id) { mutableStateOf<VersionOption?>(null) }
    var versions by remember { mutableStateOf(false) }; var variants by remember { mutableStateOf(false) }
    var microG by remember { mutableStateOf(false) }; var shareSheet by remember { mutableStateOf(false) }
    var ignoreMenu by remember { mutableStateOf(false) }; var lightbox by remember { mutableStateOf<Int?>(null) }
    var cleanupPrompt by remember(app.id) { mutableStateOf(cleanup != null) }; var handled by remember(app.id) { mutableStateOf(false) }
    var cleaning by remember { mutableStateOf(false) }; var more by remember { mutableStateOf(false) }
    LaunchedEffect(cleanup) { if (cleanup != null) { if (!handled) cleanupPrompt = true } else { cleanupPrompt = false; handled = false } }

    fun microGThen(u: String?) {
        if (isExcludedDownload(app, u, s)) {
            vm.error("Prereleases are excluded. Turn off Stable releases only in Settings to install this version.")
            return
        }
        if (app.id in Defaults.MICROG_DEPENDENT_APPS && ready == null && MICROG_PKGS.none { runCatching { vm.tracker.appInfo(it).installed }.getOrDefault(false) }) { microG = true; return }
        vm.download(app, u)
    }
    fun proceed(v: VersionOption) {
        if (s.excludePrereleases && v.type != "Stable") {
            vm.error("Prereleases are excluded. Turn off Stable releases only in Settings to install this version.")
            return
        }
        if (v.variants.size > 1) { target = v; variants = true } else microGThen(v.variants.firstOrNull()?.url ?: app.downloadUrl)
    }
    fun action(u: String? = null) {
        if (installing || activeId != null || scanning || unavailable) return
        if (prereleaseBlocked && ready == null) {
            vm.error("Only prerelease versions exist. Turn off Stable releases only in Settings to install.")
            return
        }
        when {
            u != null -> microGThen(u)
            ready != null -> vm.download(app)
            target == null && app.availableVersions.size > 1 -> {
                if (s.excludePrereleases && !hasStable(app)) {
                    vm.error("Only prerelease versions exist. Turn off Stable releases only in Settings to install.")
                    return
                }
                versions = true
            }
            else -> {
                val v = target ?: app.availableVersions.firstOrNull { it.type == stream }
                    ?: if (s.excludePrereleases) null else app.availableVersions.firstOrNull()
                if (v != null) proceed(v) else if (prereleaseBlocked) {
                    vm.error("Only prerelease versions exist. Turn off Stable releases only in Settings to install.")
                } else if (app.variants.size > 1) variants = true else microGThen(app.downloadUrl)
            }
        }
    }
    fun launch() {
        val p = app.packageName ?: return
        vm.haptic(H.HEAVY); runCatching { vm.tracker.launch(p) }.onFailure { vm.error("Could not launch app. It may be restricted.") }
    }
    fun ignore(type: String, v: String? = null) {
        vm.set { it.ignoreUpdate(app.id, type, v, System.currentTimeMillis()) }; ignoreMenu = false; vm.selectedApp = null; vm.haptic(H.SUCCESS)
    }
    fun keep() { vm.haptic(H.SELECTION); handled = true; cleanupPrompt = false }
    fun cleanupDelete() {
        val f = cleanup ?: return
        cleaning = true; handled = true
        scope.launch {
            if (vm.purgeApk(app.id, f)) vm.haptic(H.SUCCESS)
            delay(450)
            cleanupPrompt = false; cleaning = false
        }
    }
    fun report() = vm.tracker.open("mailto:${vm.config?.supportEmail ?: Defaults.SUPPORT_EMAIL}?subject=${Uri.encode("Report Issue: ${app.name}")}")

    val from = remember(app.id) { vm.openFrom }
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { AnimatedVisibility(scrolled) { Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                actions = {
                    IconToggleButton(fav, { vm.haptic(H.HEAVY); vm.upd { it.toggleFavorite(app.id) } }) {
                        Icon(if (fav) Sym.Favorite else Sym.FavoriteBorder, "Favorite", tint = if (fav) cs.primary else LocalContentColor.current)
                    }
                    IconButton({ vm.haptic(H.SELECTION); vm.tracker.share(app.name, "Check out ${app.name} on Orion Store!", Share.webLink(app.id, app.icon)) }) { Icon(Sym.Share, "Share") }
                    Box {
                        IconButton({ more = true }) { Icon(Sym.MoreVert, "More options") }
                        DropdownMenu(more, { more = false }, shape = RoundedCornerShape(20.dp)) {
                            DropdownMenuItem({ Text("Request app change") }, { more = false; vm.haptic(H.LIGHT); vm.changeRequest = true }, leadingIcon = { Icon(Sym.EditNote, null) })
                            DropdownMenuItem({ Text("Report an issue") }, { more = false; vm.haptic(H.LIGHT); report() }, leadingIcon = { Icon(Sym.Flag, null) })
                            if (s.isDevUnlocked) DropdownMenuItem({ Text("Developer deep links") }, { more = false; vm.haptic(H.SELECTION); shareSheet = true }, leadingIcon = { Icon(Sym.Link, null) })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (scrolled) cs.surfaceContainer else Color.Transparent),
                windowInsets = WindowInsets.statusBarsIgnoringVisibility, expandedHeight = 48.dp,
            )
        },
        bottomBar = {
            Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Row(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val h = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)
                    val side = Modifier.size(ButtonDefaults.MediumContainerHeight)
                    val big = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)
                    @Composable fun Main(icon: ImageVector?, label: String, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) =
                        Button(onClick, modifier = h.weight(1f), enabled = enabled, shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight)) {
                            if (busy) LoadingIndicator(Modifier.size(28.dp), color = LocalContentColor.current) else icon?.let { Icon(it, null) }
                            Spacer(Modifier.width(10.dp)); Text(label, style = big, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    @Composable fun Side(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) =
                        FilledTonalIconButton(onClick, modifier = side, enabled = enabled, shapes = IconButtonDefaults.shapes()) { Icon(icon, label) }
                    when {
                        scanning -> Main(null, "Scanning...", enabled = false, busy = true) {}
                        activeId != null || status == "RUNNING" || status == "PENDING" -> {
                            val p by animateFloatAsState(progress / 100f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dl")
                            Surface(h.weight(1f), shape = Pill, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (progress <= 0f) LinearWavyProgressIndicator(Modifier.weight(1f)) else LinearWavyProgressIndicator({ p }, Modifier.weight(1f))
                                    Spacer(Modifier.width(14.dp))
                                    Text("${progress.toInt()}%", style = MaterialTheme.typography.titleMediumEmphasized)
                                }
                            }
                            Side(Sym.Close, "Cancel download") { vm.cancelDownload(app.id) }
                        }
                        ready != null -> {
                            Main(Sym.InstallMobile, if (installing) "Installing..." else "Install now", enabled = !installing, busy = installing) { action() }
                            Side(Sym.Refresh, "Redownload", enabled = !installing) { vm.haptic(H.MEDIUM); vm.deleteReadyFile(app, ready) }
                        }
                        cleanup != null && android -> {
                            Main(Sym.OpenInNew, "Open", onClick = ::launch)
                            Side(Sym.SaveAlt, "Export APK to Downloads") { vm.exportApk(app, cleanup) }
                        }
                        upToDate && android -> Main(Sym.OpenInNew, "Open", onClick = ::launch)
                        else -> {
                            val (icon, label) = when {
                                unavailable -> Sym.Block to "Currently unavailable"
                                prereleaseBlocked -> Sym.Block to "Prerelease only"
                                fallback -> Sym.Code to "View on GitHub"
                                external -> Sym.OpenInNew to "Get from source"
                                needsUpdate -> Sym.SystemUpdate to "Update now"
                                app.platform == Platform.PC -> Sym.DesktopWindows to "Get on PC"
                                else -> Sym.Download to "Download"
                            }
                            Main(icon, if (installing) "Installing..." else label, enabled = !installing && !fallback && !unavailable && !prereleaseBlocked, busy = installing) { action() }
                            if (needsUpdate && !installing && !fallback && !unavailable && isUpdateIgnored(app, s, System.currentTimeMillis()))
                                Side(Sym.Notifications, "Stop ignoring this update") { vm.set { it.copy(ignoredUpdates = it.ignoredUpdates - app.id) }; vm.haptic(H.SUCCESS); vm.toast("Updates for ${app.name} will show again") }
                            else if (needsUpdate && !installing && !fallback && !unavailable) Box {
                                Side(Sym.NotificationsOff, "Ignore Update Options") { ignoreMenu = !ignoreMenu; if (ignoreMenu) vm.haptic(H.LIGHT) }
                                DropdownMenu(ignoreMenu, { ignoreMenu = false }, shape = RoundedCornerShape(20.dp)) {
                                    Text("Ignore update", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                    DropdownMenuItem({ Text("Ignore for 1 week") }, { ignore("week") }, leadingIcon = { Icon(Sym.Schedule, null) })
                                    DropdownMenuItem({ Text("Until next version") }, { ignore("version", app.latestVersion) }, leadingIcon = { Icon(Sym.SkipNext, null) })
                                    DropdownMenuItem({ Text("Never show again") }, { ignore("never") }, leadingIcon = { Icon(Sym.Block, null) })
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item("head") {
                val wash by animateColorAsState(iconColor(app)?.let { tonal(it).first } ?: cs.surfaceContainer, MaterialTheme.motionScheme.slowEffectsSpec(), label = "wash")
                // The bar only holds actions on the right now, so the head tucks up right under it.
                Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(wash, cs.surface))).padding(top = pad.calculateTopPadding())
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, 96.dp, state = true, from = from, target = true)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(app.author, style = MaterialTheme.typography.titleSmall, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                            Spacer(Modifier.width(4.dp)); Icon(Sym.VerifiedFilled, "Verified", Modifier.size(16.dp), tint = cs.primary)
                        }
                        statusText(app)?.let { (t, c) -> Text(t, style = MaterialTheme.typography.labelLarge, color = c, modifier = Modifier.padding(top = 2.dp)) }
                        if (prereleaseBlocked) Box(Modifier.padding(top = 6.dp)) { Tag("Prerelease only · excluded by Stable releases only", Caution, Sym.Block) }
                        else if (cleanup != null) Box(Modifier.padding(top = 6.dp)) { Tag("Pending cleanup", Caution, Sym.CleaningServices) }
                        else if (external) Box(Modifier.padding(top = 6.dp)) { Tag("Hosted on $domain", Caution, Sym.OpenInNew) }
                    }
                }
            }
            item("stats") {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(IntrinsicSize.Min)) {
                    @Composable fun Cell(value: @Composable () -> Unit, label: String) = Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) { value() }
                        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                    val divider = @Composable { VerticalDivider(Modifier.padding(vertical = 8.dp), color = cs.outlineVariant) }
                    Cell({ Text(app.latestVersion.removePrefix("v"), style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis) }, "Version")
                    divider()
                    Cell({ Text(app.size, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1) }, "Size")
                    divider()
                    Cell({ Icon(when (app.platform) { Platform.ANDROID -> Sym.Android; Platform.TV -> Sym.Tv; else -> Sym.DesktopWindows }, null, Modifier.size(24.dp)) }, app.platform)
                }
            }
            if (app.screenshots.isNotEmpty()) item("shots") {
                val portrait = app.platform == Platform.ANDROID
                LazyRow(Modifier.reveal(1).padding(top = 8.dp), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    itemsIndexed(app.screenshots) { i, u -> Shot(u, 320.dp, if (portrait) 340.dp else 200.dp, Modifier.reveal(i), portrait = portrait) { lightbox = i; vm.haptic(H.SELECTION) } }
                }
            }
            item("about") { Column(Modifier.reveal(2)) {
                var open by remember { mutableStateOf(false) }
                SectionTitle("About this app", Modifier.padding(top = 20.dp))
                Text(app.description, Modifier.padding(horizontal = 20.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).clickable { open = !open },
                    style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = if (open) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                if (app.description.length > 180) TextButton({ open = !open }, Modifier.padding(start = 8.dp)) { Text(if (open) "Show less" else "Read more") }
            } }
            if (app.patches.isNotEmpty()) item("patches") { Column(Modifier.reveal(3).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
                var all by remember { mutableStateOf(false) }
                val list = app.patches.sortedBy { it.length }
                SectionTitle("Patches", Modifier.padding(top = 12.dp), "${app.patches.size} modifications applied")
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (all) list else list.take(6)).forEach { p ->
                        Surface(shape = RoundedCornerShape(12.dp), color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer) {
                            Text(p, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (list.size > 6) Surface({ all = !all }, shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHigh) {
                        Text(if (all) "Show less" else "+${list.size - 6} more", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            } }
            item("source") { Column(Modifier.reveal(4)) {
                SectionTitle("Source & safety", Modifier.padding(top = 20.dp))
                val repo = app.repoUrl?.takeIf { it.isNotBlank() && it != "#" }
                val n = if (repo != null) 2 else 1
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    repo?.let { r ->
                        val (mark, name, tint) = repoBrand(r)
                        SegmentedListItem({ vm.haptic(H.SELECTION); vm.tracker.open(r) }, ListItemDefaults.segmentedShapes(0, n), colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainer),
                            leadingContent = { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(mark, null, Modifier.size(30.dp), tint = tint) } },
                            trailingContent = { Icon(Sym.OpenInNew, "Open $name") },
                            supportingContent = { Text(r.substringAfter("://").substringAfter('/').trimEnd('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) }) { Text(name, style = MaterialTheme.typography.titleMedium) }
                    }
                    SegmentedListItem({ vm.haptic(H.SELECTION); vm.vtTarget = app }, ListItemDefaults.segmentedShapes(n - 1, n), colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainer),
                        leadingContent = { IconBox(Sym.Shield) }, trailingContent = { Icon(Sym.ChevronRight, null) },
                        supportingContent = { Text("Check the download against 70+ antivirus engines", maxLines = 1, overflow = TextOverflow.Ellipsis) }) { Text("Scan with VirusTotal", style = MaterialTheme.typography.titleMedium) }
                }
            } }
            item("info") { Column(Modifier.reveal(5)) {
                var open by rememberSaveable { mutableStateOf(false) }
                val rows = listOfNotNull(
                    local?.takeIf { installed }?.let { Triple(Sym.CheckCircle, "Installed", it) }, Triple(Sym.Person, "Developer", app.author), Triple(Sym.Storage, "Size", app.size),
                    app.githubRepo?.takeIf { it.isNotBlank() }?.let { Triple(Sym.BrandGitHub, "GitHub", it) }, app.gitlabRepo?.takeIf { it.isNotBlank() }?.let { Triple(Sym.BrandGitLab, "GitLab", it) },
                    app.codebergRepo?.takeIf { it.isNotBlank() }?.let { Triple(Sym.BrandCodeberg, "Codeberg", it) },
                    app.packageName?.takeIf { it.isNotBlank() }?.let { Triple(Sym.Package2, "Package", it) },
                )
                val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "chev")
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SegmentedListItem({ open = !open; vm.haptic(H.SELECTION) }, ListItemDefaults.segmentedShapes(0, if (open) rows.size + 1 else 1),
                        colors = ListItemDefaults.segmentedColors(containerColor = if (open) cs.secondaryContainer else cs.surfaceContainer),
                        leadingContent = { IconBox(Sym.Info) }, trailingContent = { Icon(Sym.ExpandMore, if (open) "Hide app info" else "Show app info", Modifier.graphicsLayer { rotationZ = turn }) },
                        supportingContent = { Text(if (open) "Tap to hide" else "Developer, size, package and source", maxLines = 1) }) { Text("App info", style = MaterialTheme.typography.titleMedium) }
                    AnimatedVisibility(open, enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(), exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                            rows.forEachIndexed { i, (ic, k, v) ->
                                SegmentedListItem(ListItemDefaults.segmentedShapes(i + 1, rows.size + 1), colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainer),
                                    leadingContent = { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(ic, null, Modifier.size(22.dp)) } }, overlineContent = { Text(k) }) {
                                    SelectionContainer { Text(v, style = MaterialTheme.typography.bodyLarge) }
                                }
                            }
                        }
                    }
                }
            } }
        }
    }

    if (versions) OrionSheet({ versions = false }) {
        SheetTitle("Select version", Sym.History, "Choose your release channel")
        if (s.excludePrereleases) Text(
            "Stable releases only is on. Prereleases are disabled.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            app.availableVersions.forEachIndexed { i, v ->
                val disabled = s.excludePrereleases && v.type != "Stable"
                SegmentedListItem(
                    onClick = {
                        if (disabled) {
                            vm.error("Prereleases are excluded. Turn off Stable releases only in Settings to install this version.")
                        } else {
                            versions = false; target = v; proceed(v)
                        }
                    },
                    ListItemDefaults.segmentedShapes(i, app.availableVersions.size), colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainerHigh),
                    leadingContent = { IconBox(Sym.Layers, streamColor(v.type)) }, trailingContent = { Icon(Sym.ChevronRight, null) },
                    supportingContent = { Text(if (disabled) "${v.date} · Excluded" else v.date) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(v.version, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); StreamBadge(v.type) }
                }
            }
        }
    }

    if (variants) OrionSheet({ variants = false }) {
        val (raw, abis) = remember { vm.tracker.deviceArch() }
        val list2 = remember(target) {
            (target?.variants ?: app.variants).distinctBy { it.arch }.map { it to archCompat(it.arch, raw, abis) }
                .sortedWith(compareByDescending<Pair<AppVariant, Pair<Boolean, Boolean>>> { it.second.second }.thenByDescending { it.second.first })
        }
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Select architecture", style = MaterialTheme.typography.headlineSmallEmphasized)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target:", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant); Spacer(Modifier.width(6.dp))
                    Text(target?.version ?: app.latestVersion, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp))
                    StreamBadge(target?.type ?: "Stable")
                }
            }
        }
        Surface(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = cs.secondaryContainer) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Memory, null); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Device architecture", style = MaterialTheme.typography.labelMedium)
                    Text(friendlyArch(raw), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (normalizeArch(raw) != Arch.Unknown) Tag("Your phone", cs.primary)
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            list2.forEachIndexed { i, (v, c) ->
                val (compat, rec) = c
                SegmentedListItem({ variants = false; action(v.url) }, ListItemDefaults.segmentedShapes(i, list2.size),
                    colors = if (rec) ListItemDefaults.segmentedColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer) else ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainerHigh),
                    leadingContent = {
                        Box(Modifier.size(40.dp).clip(if (rec) MaterialShapes.Cookie6Sided.toShape() else CircleShape).background(cs.primary.copy(if (rec) .2f else .1f)), contentAlignment = Alignment.Center) {
                            Icon(Sym.DeveloperBoard, null, tint = cs.primary)
                        }
                    },
                    trailingContent = { Icon(Sym.Download, null) },
                    supportingContent = { Text(if (rec) "Best match for your phone" else "APK") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(v.arch, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp))
                        if (rec) Tag("Recommended", cs.primary) else if (compat && v.arch.equals("universal", true)) Tag("Universal", Safe)
                    }
                }
            }
        }
    }

    if (microG) AlertDialog({ microG = false },
        confirmButton = { Button({ microG = false; vm.navigateToApp("microG-re") }, shapes = ButtonDefaults.shapes()) { Text("Install MicroG") } },
        dismissButton = { TextButton({ microG = false; vm.tracker.open(Defaults.MICROG_INFO_URL) }) { Text("Learn about MicroG") } },
        icon = { Icon(Sym.GppMaybe, null, tint = Caution) }, title = { Text("MicroG required") },
        text = { Text("This app requires MicroG to sign in to your Google Account. Make sure you have it installed first.") })

    if (cleanupPrompt && cleanup != null) AlertDialog({ if (!cleaning) keep() },
        confirmButton = { AnimatedVisibility(!cleaning) { Button({ cleanupDelete() }, shapes = ButtonDefaults.shapes()) { Icon(Sym.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete APK") } } },
        dismissButton = { AnimatedVisibility(!cleaning) { TextButton({ keep() }) { Text("Keep for now") } } },
        icon = { if (cleaning) LoadingIndicator(Modifier.size(48.dp)) else Icon(Sym.CleaningServices, null) },
        title = { Text(if (cleaning) "Scrubbing bits..." else "Cleanup time!") },
        text = {
            Text(if (cleaning) AnnotatedString("Reclaiming space for you") else buildAnnotatedString {
                append("The APK for "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(app.name) }; append(" is no longer needed.")
            })
        })

    if (shareSheet) ShareLinksSheet(vm, app) { shareSheet = false }
    lightbox?.let { Lightbox(app.screenshots, it) { lightbox = null } }
}

/** 40dp tonal icon container for list rows. */
@Composable
private fun repoBrand(url: String): Triple<ImageVector, String, Color> = when {
    "gitlab" in url -> Triple(Sym.BrandGitLab, "GitLab", Color(0xFFFC6D26))
    "codeberg" in url -> Triple(Sym.BrandCodeberg, "Codeberg", Color(0xFF2185D0))
    "github" in url -> Triple(Sym.BrandGitHub, "GitHub", MaterialTheme.colorScheme.onSurface)
    else -> Triple(Sym.Code, "Source code", MaterialTheme.colorScheme.primary)
}

@Composable
fun IconBox(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) =
    Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = tint) }

@Composable
private fun Lightbox(shots: List<String>, start: Int, onClose: () -> Unit) = Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    // The dialog is its own window: hide its system bars too, so screenshots open truly full screen (swipe from an edge to peek).
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).run {
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
    val n = shots.size
    val pager = rememberPagerState(start) { n }; val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }; var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pager.currentPage) { zoom = 1f; pan = Offset.Zero }
    val tf = rememberTransformableState { z, p, _ -> zoom = (zoom * z).coerceIn(1f, 4f); pan = if (zoom > 1f) pan + p else Offset.Zero }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = zoom == 1f, beyondViewportPageCount = 1) { i ->
            val cur = i == pager.currentPage
            AsyncImage(optimizedImageUrl(shots[i], null, 1600), "Screenshot ${i + 1}", Modifier.fillMaxSize()
                .then(if (cur) Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (zoom > 1f) { zoom = 1f; pan = Offset.Zero } else zoom = 2.5f }) }.transformable(tf, canPan = { zoom > 1f }) else Modifier)
                .graphicsLayer { if (cur) { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y } }, contentScale = ContentScale.Fit)
        }
        val chrome = IconButtonDefaults.filledTonalIconButtonColors(Color.White.copy(.14f), Color.White)
        if (n > 1 && zoom == 1f) {
            FilledTonalIconButton({ scope.launch { pager.animateScrollToPage((pager.currentPage - 1 + n) % n) } }, Modifier.align(Alignment.CenterStart).padding(12.dp), colors = chrome) { Icon(Sym.ChevronLeft, "Previous") }
            FilledTonalIconButton({ scope.launch { pager.animateScrollToPage((pager.currentPage + 1) % n) } }, Modifier.align(Alignment.CenterEnd).padding(12.dp), colors = chrome) { Icon(Sym.ChevronRight, "Next") }
            Text("${pager.currentPage + 1} / $n", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).clip(Pill).background(Color.White.copy(.14f)).padding(horizontal = 14.dp, vertical = 6.dp),
                color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ShareLinksSheet(vm: MainViewModel, app: AppItem, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val clip = LocalClipboardManager.current; val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf<String?>(null) }; var copied by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copied) { if (copied != null) { delay(2000); copied = null } }
    val web = Share.webLink(app.id, app.icon)
    data class Row5(val key: String, val label: String, val helper: String, val value: String, val action: String, val multi: Boolean)
    val rows = listOf(
        Row5("deep", "Deep Link", "Launches Orion Store directly to this app", Share.deepLink(app.id), "Copy", false),
        Row5("web", "Web Link", "Uses the fallback redirect page before opening the app", web, "Copy", false),
        Row5("md", "Markdown Badge", "Badge snippet for markdown readmes and posts", Share.markdown(web), "Copy Markdown", true),
        Row5("html", "HTML Badge", "Badge snippet for websites and embedded pages", Share.html(web), "Copy HTML", true),
    )
    SheetTitle("Developer deep links", Sym.Link)
    Text(buildAnnotatedString { append("Open one item at a time to copy links and badge snippets for "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(app.name) }; append(".") },
        Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { i, r ->
            val on = open == r.key
            Surface(shape = ListItemDefaults.segmentedShapes(i, rows.size).shape, color = cs.surfaceContainerHigh) {
                Column {
                    ListItem({ Text(r.label, fontWeight = FontWeight.Bold) }, Modifier.clickable { open = if (on) null else r.key; vm.haptic(H.SELECTION) },
                        supportingContent = { Text(r.helper) }, trailingContent = { Icon(if (on) Sym.ExpandLess else Sym.ExpandMore, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    AnimatedVisibility(on) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            SelectionContainer {
                                Text(r.value, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerLowest).padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = if (r.multi) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton({ clip.setText(AnnotatedString(r.value)); copied = r.key; vm.haptic(H.SUCCESS) }, modifier = Modifier.padding(top = 10.dp), shapes = ButtonDefaults.shapes()) {
                                Icon(if (copied == r.key) Sym.Check else Sym.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                Text(if (copied == r.key) "Copied" else r.action)
                            }
                        }
                    }
                }
            }
        }
    }
}
