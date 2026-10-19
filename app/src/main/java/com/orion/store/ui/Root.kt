package com.orion.store.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orion.store.R
import com.orion.store.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class NavItem(val tab: Tab, val icon: ImageVector, val selected: ImageVector, val label: String, val subtitle: String)
private val NAV = listOf(
    NavItem(Tab.ANDROID, Sym.Android, Sym.AndroidFilled, "Apps", "apps"), NavItem(Tab.PC, Sym.DesktopWindows, Sym.DesktopWindowsFilled, "PC", "PC"),
    NavItem(Tab.TV, Sym.Tv, Sym.TvFilled, "TV", "TV"), NavItem(Tab.MYAPPS, Sym.GridView, Sym.GridViewFilled, "My Apps", "my"),
    NavItem(Tab.ABOUT, Sym.Code, Sym.CodeFilled, "Dev", "About Orion"),
)

@Composable
fun OrionRoot(vm: MainViewModel) {
    val s by vm.settings.collectAsState(); val d by vm.data.collectAsState()
    val act = LocalActivity.current
    CompositionLocalProvider(LocalVm provides vm, LocalS provides s, LocalD provides d) {
        OrionTheme(s) { ThemeRevealHost {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { SharedTransitionLayout { CompositionLocalProvider(LocalShared provides this) {
                BackHandler { if (!vm.back()) act?.finish() }
                val screen = when {
                    vm.forcedTest != null || vm.forcedBlocking -> 0
                    (vm.config?.maintenanceMode == true || s.localMaintenanceMode) && !vm.bypassMaintenance -> 1
                    else -> 2
                }
                AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") {
                    when (it) { 0 -> ForcedGate(vm); 1 -> Maintenance(vm); else -> Home(vm) }
                }
                Overlays(vm)
            } } }
        } }
    }
}

@Composable
private fun Home(vm: MainViewModel) {
    val s = LocalS.current; val d = LocalD.current
    val items = NAV.filter { it.tab == Tab.ABOUT || it.tab.name.lowercase() !in s.hiddenTabs }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val density = LocalDensity.current
    val navDensity = Density(density.density * s.bottomNavScale.coerceIn(.8f, 1.2f), density.fontScale)
    val updateCount = vm.updates.size
    LaunchedEffect(vm.tab) { vm.navShown = true }
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) vm.navShown = false else if (available.y > 8f) vm.navShown = true
                return Offset.Zero
            }
        }
    }
    Row(Modifier.fillMaxSize()) {
        if (wide) CompositionLocalProvider(LocalDensity provides navDensity) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer, header = { Spacer(Modifier.height(12.dp)) }) {
                Spacer(Modifier.weight(1f))
                items.forEach { n -> NavigationRailItem(vm.tab == n.tab, { vm.navTo(n.tab) }, { NavIcon(n, vm.tab == n.tab, updateCount) }, label = { Text(n.label) }) }
                Spacer(Modifier.weight(1f))
            }
        }
        Box(Modifier.weight(1f)) {
            Scaffold(
                Modifier.nestedScroll(hideOnScroll),
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(vm.snackbar, Modifier.padding(bottom = if (wide) 0.dp else 80.dp)) },
                floatingActionButton = {
                    androidx.compose.animation.AnimatedVisibility(vm.cleanupPill && vm.selectedApp == null && vm.overlays.isEmpty(), Modifier.padding(bottom = if (wide) 0.dp else 80.dp),
                        enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        ExtendedFloatingActionButton({ vm.batchCleanup() }, containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                            Icon(Sym.CleaningServices, null); Spacer(Modifier.width(12.dp))
                            Column { Text("${d.pendingCleanup.size} files", fontWeight = FontWeight.Bold); Text("Tap to clean", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                },
                floatingActionButtonPosition = FabPosition.Center,
                containerColor = MaterialTheme.colorScheme.surface,
            ) { pad ->
                // Status bar is hidden; its slot is kept so the header clears the camera cutout, and content scrolls up through it.
                val top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
                val inner = PaddingValues(top = top, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (wide) 16.dp else 84.dp)
                // Fade-through on effects springs: a bouncy spatial scale wobbled the whole screen. Each tab keeps its saved state (scroll, entrances) while away.
                val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>(); val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                val saved = rememberSaveableStateHolder()
                AnimatedContent(vm.tab, Modifier.fillMaxSize(), transitionSpec = { (fadeIn(fx) + scaleIn(fx, .98f)) togetherWith fadeOut(fast) }, label = "tab") { t ->
                    saved.SaveableStateProvider(t.name) {
                        when (t) {
                            Tab.MYAPPS -> MyAppsTab(vm, inner)
                            Tab.ABOUT -> AboutTab(vm, inner)
                            else -> StoreTab(vm, t.platform ?: Platform.ANDROID, inner)
                        }
                    }
                }
            }
            if (!wide) CompositionLocalProvider(LocalDensity provides navDensity) {
                FloatingNav(vm, items, updateCount, vm.navShown, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** Floating pill dock: the active destination grows into an icon + label indicator; slides away while scrolling down. */
@Composable
private fun FloatingNav(vm: MainViewModel, items: List<NavItem>, updates: Int, shown: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme; val s = LocalS.current
    val drop by animateDpAsState(if (shown) 0.dp else 120.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "drop")
    Surface(modifier.navigationBarsPadding().padding(bottom = 12.dp).graphicsLayer { translationY = drop.toPx() }, shape = Pill,
        color = cs.surfaceContainerHigh.copy(alpha = if (s.glassEffect) .95f else 1f), shadowElevation = 8.dp) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { n ->
                val on = vm.tab == n.tab
                val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Color>(); val sp = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
                val bg by animateColorAsState(if (on) cs.secondaryContainer else Color.Transparent, fx, label = "bg")
                val fg by animateColorAsState(if (on) cs.onSecondaryContainer else cs.onSurfaceVariant, fx, label = "fg")
                Row(Modifier.height(52.dp).clip(Pill).background(bg).selectable(on, role = Role.Tab) { vm.navTo(n.tab) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalContentColor provides fg) { NavIcon(n, on, updates) }
                    AnimatedVisibility(on, enter = expandHorizontally(sp) + fadeIn(), exit = shrinkHorizontally(sp) + fadeOut()) {
                        Text(n.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavIcon(n: NavItem, on: Boolean, updates: Int) {
    val k = remember { Animatable(1f) }; val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(on) { if (on) { k.snapTo(.7f); k.animateTo(1f, spec) } }
    val icon = @Composable { Icon(if (on) n.selected else n.icon, n.label, Modifier.graphicsLayer { scaleX = k.value; scaleY = k.value }) }
    if (n.tab == Tab.MYAPPS && updates > 0) BadgedBox({ Badge { Text("$updates") } }) { icon() } else icon()
}

/** Tab header, drawn as the first item of every tab so it scrolls away with the content. */
@Composable
fun Header(vm: MainViewModel) {
    val s = LocalS.current; val d = LocalD.current; val cs = MaterialTheme.colorScheme
    val subtitle = NAV.firstOrNull { it.tab == vm.tab }?.subtitle ?: ""
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (vm.tab == Tab.ABOUT) Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(vm); Spacer(Modifier.width(12.dp)); Text(subtitle, style = MaterialTheme.typography.titleLargeEmphasized)
            } else SearchPill(vm, subtitle)
        }
        AnimatedVisibility(vm.storeUpdateAvailable) {
            FilledTonalIconButton({ vm.fBypassed = false; vm.loadApps(true) }, shapes = IconButtonDefaults.shapes()) { Icon(Sym.SystemUpdate, "Store update") }
        }
        val dl = d.activeDownloads.size; val up = vm.updates.size
        IconButton({ vm.openSettings() }) {
            BadgedBox({
                when {
                    dl > 0 -> Badge(containerColor = cs.primary) { Text("$dl") }
                    up > 0 -> Badge(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer) { Text("$up") }
                    vm.storeUpdateAvailable -> Badge { Text("!") }
                }
            }) { Icon(Sym.Settings, "Settings") }
        }
        val p = s.userProfile
        IconButton(vm::openProfileStats) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                if (!p?.avatarUrl.isNullOrEmpty()) AsyncImage(pngAvatar(p.avatarUrl), "Profile", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(p?.name?.firstOrNull()?.uppercase() ?: "U", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = cs.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun Logo(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.primary).clickable(onClick = vm::titleClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_splash_logo), "Orion Store", Modifier.size(40.dp), tint = cs.onPrimary)
    }
}

@Composable
private fun SearchPill(vm: MainViewModel, scope: String) {
    val s = LocalS.current; val cs = MaterialTheme.colorScheme
    val lib = vm.tab == Tab.MYAPPS
    // Read through LocalD: vm.tabState is a plain StateFlow read, invisible to Compose, so this skippable pill never saw the typed text.
    val q = if (lib) vm.libraryQuery else LocalD.current.tab(vm.tab.key()).query
    val set: (String) -> Unit = { if (lib) vm.libraryQuery = it else vm.search(it) }
    Row(Modifier.fillMaxWidth().height(52.dp).clip(Pill).background(cs.surfaceContainerHigh).padding(start = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo(vm)
        BasicTextField(q, set, Modifier.weight(1f).padding(horizontal = 12.dp), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (q.isEmpty()) Text(if (lib) "Search my apps" else "Search $scope", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    field()
                }
            })
        AnimatedContent(q.isNotEmpty(), label = "trail") { typing ->
            if (typing) IconButton({ set("") }) { Icon(Sym.Close, "Clear search") }
            else Row {
                AnnouncementButton(vm)
                val at = remember { mutableStateOf(Offset.Zero) }; val reveal = LocalThemeReveal.current
                IconButton({ reveal?.run(at.value) { vm.toggleTheme() } ?: vm.toggleTheme() }, Modifier.trackCenter(at)) {
                    AnimatedContent(s.theme, label = "theme") { Icon(when (it) { "light" -> Sym.LightMode; "dusk" -> Sym.Brightness6; else -> Sym.DarkMode }, "Theme: $it, tap to change") }
                }
            }
        }
    }
}

/** Announcements arrive as a rich tooltip that drops from a megaphone in the search pill; the megaphone pulses while unread. */
@Composable
private fun AnnouncementButton(vm: MainViewModel) {
    val ann = vm.config?.announcement?.takeIf { it.isNotBlank() } ?: return
    val cs = MaterialTheme.colorScheme
    val unread = !vm.announcementDismissed()
    val tip = rememberTooltipState(isPersistent = true)
    // The popup queue decides when the announcement drops; closing it hands the turn to the next popup
    LaunchedEffect(vm.announceNow) { if (vm.announceNow) { tip.show(); vm.announceNow = false } }
    DisposableEffect(Unit) { onDispose { vm.announceNow = false } }
    LaunchedEffect(vm.selectedApp, vm.overlays.size, vm.tab) { if (vm.selectedApp != null || vm.overlays.isNotEmpty()) tip.dismiss() }
    TooltipBox(
        TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below, 8.dp),
        tooltip = {
            RichTooltip(title = { Text("From the Orion team") }, caretShape = TooltipDefaults.caretShape(),
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ tip.dismiss() }) { Text("Later") }
                        if (unread) TextButton({ vm.dismissAnnouncement(); vm.haptic(H.SELECTION); tip.dismiss() }) { Text("Got it") }
                    }
                },
                colors = TooltipDefaults.richTooltipColors(containerColor = cs.secondaryContainer, contentColor = cs.onSecondaryContainer,
                    titleContentColor = cs.onSecondaryContainer, actionContentColor = cs.primary)) { Text(ann) }
        },
        state = tip, hasAction = true,
    ) {
        val scope = rememberCoroutineScope()
        IconButton({ scope.launch { tip.show() } }) {
            BadgedBox({ if (unread) {
                // The pulse only runs while unread: an idle infinite transition still ticks every frame.
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.18f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
                Badge(Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse })
            } }) {
                Icon(Sym.Campaign, "Announcement", tint = if (unread) cs.primary else LocalContentColor.current)
            }
        }
    }
}


@Composable
private fun Overlays(vm: MainViewModel) {
    var lastApp by remember { mutableStateOf<AppItem?>(null) }
    vm.selectedApp?.let { lastApp = it }
    val sp = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(); val fx = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedVisibility(vm.selectedApp != null, enter = fadeIn(fx) + scaleIn(sp, .92f), exit = fadeOut(fx) + scaleOut(targetScale = .94f)) {
        val r by transition.animateDp(label = "r") { if (it == EnterExitState.Visible) 0.dp else 48.dp }
        BackHandler { vm.selectedApp = null }
        Surface(Modifier.fillMaxSize().graphicsLayer { clip = true; shape = RoundedCornerShape(r) }, color = MaterialTheme.colorScheme.surface) {
            lastApp?.let { DetailScreen(vm, it) }
        }
    }
    AnimatedVisibility(Overlay.SETTINGS in vm.overlays, enter = slideInHorizontally { it / 3 } + fadeIn(), exit = slideOutHorizontally { it / 3 } + fadeOut()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { SettingsScreen(vm) }
    }
    fun o(x: Overlay) = x in vm.overlays
    if (o(Overlay.RELEASE_NOTES)) ReleaseNotesSheet(vm) { vm.close(Overlay.RELEASE_NOTES) }
    if (o(Overlay.FAQ)) FaqSheet(vm) { vm.close(Overlay.FAQ) }
    if (o(Overlay.SUBMISSION)) SubmissionSheet(vm) { vm.close(Overlay.SUBMISSION) }
    if (o(Overlay.AD_DONATION)) AdDonationSheet(vm) { vm.close(Overlay.AD_DONATION) }
    if (vm.welcomeOpen) WelcomeSheet(vm)
    if (o(Overlay.CUSTOM_BUNDLE)) CustomBundleSheet(vm) { vm.close(Overlay.CUSTOM_BUNDLE) }
    if (o(Overlay.PROFILE_STATS)) ProfileStatsSheet(vm) { vm.close(Overlay.PROFILE_STATS) }
    if (o(Overlay.NOTICE)) NoticeSheet(vm) { vm.dismissNotice() }
    if (o(Overlay.TUTORIAL)) TutorialSheet(vm) { vm.close(Overlay.TUTORIAL) }
    // The bundle stays selected while one of its apps is open, so backing out of the detail page lands on the bundle again.
    vm.selectedBundle?.takeIf { vm.selectedApp == null }?.let { BundlePreviewSheet(vm, it) { vm.selectedBundle = null } }
    vm.vtTarget?.let { VirusTotalSheet(vm, it) { vm.vtTarget = null } }
    vm.releaseNotesTarget?.let { (app, version) -> UpdateReleaseNotesSheet(vm, app, version) { vm.closeReleaseNotes() } }
    if (vm.changeRequest) vm.selectedApp?.let { ChangeRequestSheet(vm, it) { vm.changeRequest = false } }
    vm.autoReport?.let { log -> AutoReportDialog(vm, log) }
}

/** Shown on the first start after a background pass that hit a problem. Failed updates stay ready to install with one tap. */
@Composable
private fun AutoReportDialog(vm: MainViewModel, log: List<AutoResult>) {
    val failed = log.filter { !it.ok }; val ok = log.filter { it.ok }
    AlertDialog(vm::dismissAutoReport, icon = { Icon(Sym.SystemUpdate, null) },
        title = { Text(if (failed.size == 1) "1 update needs you" else "${failed.size} updates need you") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ok.isNotEmpty()) Text("Updated in the background: ${ok.joinToString { it.name }}.", style = MaterialTheme.typography.bodyMedium)
                failed.forEach { r ->
                    Column {
                        Text("${r.name} ${r.version}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(r.error ?: "Install failed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton({ vm.dismissAutoReport(); vm.openSettings("queue") }) { Text("Open queue") } },
        dismissButton = { TextButton(vm::dismissAutoReport) { Text("Later") } })
}
