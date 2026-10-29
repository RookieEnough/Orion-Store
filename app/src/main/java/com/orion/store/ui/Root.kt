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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { SharedTransitionLayout(if (Device.isTv) Modifier.padding(horizontal = 48.dp, vertical = 27.dp) else Modifier) { CompositionLocalProvider(LocalShared provides this) {
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
            NavigationRail(containerColor = if (s.isOled) Color.Black else MaterialTheme.colorScheme.surfaceContainer, header = {
                Spacer(Modifier.height(12.dp))
                if (s.aiButton) AiStarFace(vm, Modifier.padding(vertical = 8.dp))
            }) {
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
                // The root Surface already paints the page; a second full-screen fill here was pure overdraw on every frame
                containerColor = Color.Transparent,
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
            if (!wide && s.aiButton) AiStar(vm)
        }
    }
}

/** Orion AI's entry point: a small translucent star on a slowly turning cookie. Drag it anywhere; the spot is saved as a fraction
 *  of the screen so it survives rotation. The rail gets the same star, fixed in place. */
@Composable
private fun AiStarFace(vm: MainViewModel, modifier: Modifier = Modifier, lifted: Boolean = false) {
    val cs = MaterialTheme.colorScheme; val still = LocalS.current.calm
    val spin by rememberInfiniteTransition(label = "star").animateFloat(0f, if (still) 0f else 360f, infiniteRepeatable(tween(14_000, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    val k by animateFloatAsState(if (lifted) 1.18f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "lift")
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(modifier.size(44.dp).graphicsLayer { scaleX = k; scaleY = k }.focusRing(src, CircleShape).clip(CircleShape)
        .clickable(src, ripple()) { vm.haptic(H.MEDIUM); vm.open(Overlay.AI) }, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = spin }.background(cs.primaryContainer.copy(alpha = .6f), MaterialShapes.Cookie9Sided.toShape()))
        Icon(Sym.AutoAwesome, "Orion AI", Modifier.size(22.dp), tint = cs.onPrimaryContainer)
    }
}

@Composable
private fun BoxScope.AiStar(vm: MainViewModel) = BoxWithConstraints(Modifier.matchParentSize()) {
    val s = LocalS.current; val d = LocalDensity.current
    val size = with(d) { 44.dp.toPx() }; val m = with(d) { 12.dp.toPx() }
    val spanX = (constraints.maxWidth - size - 2 * m).coerceAtLeast(1f); val spanY = (constraints.maxHeight - size - 2 * m).coerceAtLeast(1f)
    // Default spot: right edge, just above the dock
    var x by remember { mutableFloatStateOf(s.aiFabX.takeIf { it >= 0f } ?: 1f) }; var y by remember { mutableFloatStateOf(s.aiFabY.takeIf { it >= 0f } ?: .84f) }
    var dragging by remember { mutableStateOf(false) }
    AiStarFace(vm, Modifier.offset { androidx.compose.ui.unit.IntOffset((m + x * spanX).toInt(), (m + y * spanY).toInt()) }
        .pointerInput(spanX, spanY) {
            detectDragGestures(onDragStart = { dragging = true; vm.haptic(H.LIGHT) }, onDragCancel = { dragging = false },
                onDragEnd = { dragging = false; vm.set { it.copy(aiFabX = x, aiFabY = y) } }) { c, delta ->
                c.consume(); x = (x + delta.x / spanX).coerceIn(0f, 1f); y = (y + delta.y / spanY).coerceIn(0f, 1f)
            }
        }, dragging)
}

/** The dock in the style picked in Settings (see [DOCKS]); every style slides away while scrolling down. A locked style falls back to the pill. */
@Composable
private fun FloatingNav(vm: MainViewModel, items: List<NavItem>, updates: Int, shown: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme; val s = LocalS.current
    val dock = DOCKS.firstOrNull { it.id == s.navDock && it.unlocked(s) }?.id ?: "pill"
    val drop by animateFloatAsState(if (shown) 0f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "drop")
    // Slides by its own height plus everything below it (system bar, margin, shadow), so no sliver stays on screen at any dock size or inset
    val below = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    val slide = Modifier.graphicsLayer { translationY = drop * (size.height + below + 32.dp.toPx()) }
    if (dock == "bar") {
        NavigationBar(modifier.then(slide), containerColor = if (s.isOled) Color.Black else cs.surfaceContainer) {
            items.forEach { n -> NavigationBarItem(vm.tab == n.tab, { vm.navTo(n.tab) }, { NavIcon(n, vm.tab == n.tab, updates) }, label = { Text(n.label, maxLines = 1) }) }
        }
        return
    }
    if (dock == "vibrant") { ExpressiveDock(vm, items, updates, modifier.navigationBarsPadding().padding(bottom = 12.dp).then(slide)); return }
    val oled = s.isOled
    val color = if (oled) Color.Black else cs.surfaceContainerHigh.copy(alpha = if (s.glassEffect) .95f else 1f)
    // OLED: a pitch-black dock, set apart from the black page by a hairline outline instead of a lighter fill
    Surface(modifier.navigationBarsPadding().padding(bottom = 12.dp).then(slide), shape = if (dock == "labels") RoundedCornerShape(28.dp) else Pill, color = color, shadowElevation = 8.dp,
        border = if (oled) androidx.compose.foundation.BorderStroke(1.dp, cs.outline.copy(alpha = .5f)) else null) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(if (dock == "pill") 2.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { n ->
                val on = vm.tab == n.tab
                val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Color>(); val sp = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
                val (onBg, onFg, offFg) = Triple(cs.secondaryContainer, cs.onSecondaryContainer, cs.onSurfaceVariant)
                val bg by animateColorAsState(if (on) onBg else Color.Transparent, fx, label = "bg")
                val fg by animateColorAsState(if (on) onFg else offFg, fx, label = "fg")
                val tap = Modifier.selectable(on, role = Role.Tab) { vm.navTo(n.tab) }
                when (dock) {
                    "pill" -> Row(Modifier.height(52.dp).clip(Pill).background(bg).then(tap).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompositionLocalProvider(LocalContentColor provides fg) { NavIcon(n, on, updates) }
                        AnimatedVisibility(on, enter = expandHorizontally(sp) + fadeIn(), exit = shrinkHorizontally(sp) + fadeOut()) {
                            Text(n.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                        }
                    }
                    "labels" -> Column(Modifier.clip(RoundedCornerShape(22.dp)).then(tap).padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(56.dp, 32.dp).clip(Pill).background(bg), contentAlignment = Alignment.Center) { CompositionLocalProvider(LocalContentColor provides fg) { NavIcon(n, on, updates) } }
                        Text(n.label, Modifier.padding(top = 4.dp), style = if (on) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                            color = if (on) cs.onSurface else offFg, maxLines = 1)
                    }
                    // Each tab owns an M3 shape: picking it morphs its indicator from a circle into that shape
                    "shapes" -> MorphBlob(listOf(MaterialShapes.Circle, DOCK_SHAPES[items.indexOf(n) % DOCK_SHAPES.size]), if (on) 1 else 0, 52.dp, bg, Modifier.clip(CircleShape).then(tap)) {
                        CompositionLocalProvider(LocalContentColor provides fg) { NavIcon(n, on, updates) }
                    }
                    else -> Box(Modifier.size(52.dp).clip(CircleShape).background(bg).then(tap), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides fg) { NavIcon(n, on, updates) }
                    }
                }
            }
        }
    }
}

private val DOCK_SHAPES = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Flower, MaterialShapes.Gem, MaterialShapes.Pentagon)

/** The all-Expressive dock: an M3 floating toolbar whose tabs are toggle buttons that morph from circle to rounded square when picked
 *  and squash on press, in the toolbar's own color roles. */
@Composable
private fun ExpressiveDock(vm: MainViewModel, items: List<NavItem>, updates: Int, modifier: Modifier) {
    val s = LocalS.current
    HorizontalFloatingToolbar(true, modifier, colors = if (s.isOled) FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = Color.Black)
        else FloatingToolbarDefaults.standardFloatingToolbarColors()) {
        items.forEach { n ->
            val on = vm.tab == n.tab
            FilledIconToggleButton(on, { vm.navTo(n.tab) }, modifier = Modifier.size(52.dp), shapes = IconButtonDefaults.toggleableShapes(),
                colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = Color.Transparent)) { NavIcon(n, on, updates) }
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
        if (Device.isTv) TvBetaPill(vm)
        if (vm.tab == Tab.ABOUT) IconButton({ vm.haptic(H.LIGHT); vm.open(Overlay.GUIDE) }) { Icon(Sym.MenuBook, "Know Orion") }
        // The pill carries the shield on store tabs; the Dev tab has no pill, so it sits here. The megaphone, when there is news, lives here too.
        if (vm.tab == Tab.ABOUT) ShieldButton(vm)
        else AnnouncementButton(vm)
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

/** TV support is in beta: a pill in the header says so and leads to a prefilled bug report. */
@Composable
private fun TvBetaPill(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme; var open by remember { mutableStateOf(false) }
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface({ open = true; vm.haptic(H.LIGHT) }, Modifier.padding(end = 4.dp).focusRing(src, Pill), shape = Pill, color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, interactionSource = src) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.Tv, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("TV · Beta", style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
    if (open) AlertDialog({ open = false }, icon = { Icon(Sym.Tv, null) }, title = { Text("Orion on TV is in beta") },
        text = { Text("Browse with your remote, and install TV and Android apps right on this TV. A few screens are still made for touch. If something feels off, report it and it gets fixed.") },
        confirmButton = { TextButton({ open = false; vm.tracker.open(TV_BUG_URL) }) { Text("Report a TV bug") } },
        dismissButton = { TextButton({ open = false }) { Text("Got it") } })
}

private const val TV_BUG_URL = "https://github.com/RookieEnough/Orion-Store/issues/new?title=%5BTV%20beta%5D%20&body=TV%20model%3A%20%0AAndroid%20TV%20version%3A%20%0AWhat%20happened%3A%20"

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
                ShieldButton(vm)
                val at = remember { mutableStateOf(Offset.Zero) }; val reveal = LocalThemeReveal.current
                IconButton({ reveal?.run(at.value) { vm.toggleTheme() } ?: vm.toggleTheme() }, Modifier.trackCenter(at)) {
                    AnimatedContent(s.theme, label = "theme") { Icon(when (it) { "light" -> Sym.LightMode; "dusk" -> Sym.Brightness6; else -> Sym.DarkMode }, "Theme: $it, tap to change") }
                }
            }
        }
    }
}

/** Sideloading protection, always one tap away: crossed while Android's developer verification is on, checked once Orion turned it off.
 *  Only the Orion Xtra switch hides it. */
@Composable
private fun ShieldButton(vm: MainViewModel) {
    val s = LocalS.current; val cs = MaterialTheme.colorScheme
    if (!s.verifierBadge || (vm.verifierState == com.orion.store.platform.Verifier.State.ABSENT && "verifier=secured" !in s.seenFlags)) return
    val on = vm.verifierState != com.orion.store.platform.Verifier.State.ACTIVE
    IconButton({ vm.haptic(H.LIGHT); vm.openVerifier() }) {
        Icon(if (on) Sym.GppGood else Sym.GppBad, if (on) "Sideloading protected, tap to revert" else "Sideloading not protected", tint = if (on) cs.primary else cs.error)
    }
}

/** Announcements arrive as a rich tooltip that drops from a megaphone in the header; it pulses while unread and leaves after Got it. */
@Composable
private fun AnnouncementButton(vm: MainViewModel) {
    val ann = vm.config?.announcement?.takeIf { it.isNotBlank() } ?: return
    val cs = MaterialTheme.colorScheme
    // Gone for good once read; only a new announcement brings it back. Old ones are read from Settings.
    val unread = !vm.announcementDismissed()
    if (!unread) return
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
    // Under the app page, so an app opened from the chat lands on top and backing out returns to the conversation
    AnimatedVisibility(Overlay.AI in vm.overlays, enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 5 } + fadeIn(), exit = slideOutVertically { it / 5 } + fadeOut()) {
        Surface(Modifier.fillMaxSize().trapFocus(), color = MaterialTheme.colorScheme.surface) { AiScreen(vm) { vm.close(Overlay.AI) } }
    }
    AnimatedVisibility(vm.selectedApp != null, enter = fadeIn(fx) + scaleIn(sp, .92f), exit = fadeOut(fx) + scaleOut(targetScale = .94f)) {
        val r by transition.animateDp(label = "r") { if (it == EnterExitState.Visible) 0.dp else 48.dp }
        BackHandler { vm.selectedApp = null }
        Surface(Modifier.fillMaxSize().trapFocus().graphicsLayer { clip = true; shape = RoundedCornerShape(r) }, color = MaterialTheme.colorScheme.surface) {
            lastApp?.let { DetailScreen(vm, it) }
        }
    }
    AnimatedVisibility(Overlay.SETTINGS in vm.overlays, enter = slideInHorizontally { it / 3 } + fadeIn(), exit = slideOutHorizontally { it / 3 } + fadeOut()) {
        Surface(Modifier.fillMaxSize().trapFocus(), color = MaterialTheme.colorScheme.surface) { SettingsScreen(vm) }
    }
    AnimatedVisibility(Overlay.GUIDE in vm.overlays, enter = slideInVertically { it / 4 } + fadeIn(), exit = slideOutVertically { it / 4 } + fadeOut()) {
        Surface(Modifier.fillMaxSize().trapFocus(), color = MaterialTheme.colorScheme.surface) { KnowOrionScreen(vm) { vm.close(Overlay.GUIDE) } }
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
    if (vm.changeRequest) vm.selectedApp?.let { ChangeRequestSheet(vm, it) { vm.changeRequest = false } }
    vm.autoReport?.let { log -> AutoReportDialog(vm, log) }
    if (vm.guidePrompt) GuidePromptDialog(vm)
    if (vm.verifierPrompt) VerifierDialog(vm)
    if (vm.verifierSetup) DirectInstallSheet(vm) { vm.verifierSetupClosed() }
    vm.devProfile?.let { (dev, from) -> if (vm.selectedApp?.id == from) DevSheet(vm, dev) { vm.devProfile = null } else SideEffect { vm.devProfile = null } }
    if (vm.donateNudge) DonateNudgeDialog(vm)
    vm.installAsk?.let { (a, f) -> InstallOptionsSheet(vm, a, f) { vm.installAsk = null } }
    vm.uninstallManyAsk?.let { list ->
        AlertDialog({ vm.uninstallManyAsk = null }, icon = { Icon(Sym.Delete, null) }, title = { Text("Uninstall ${list.size} apps?") },
            text = { Text(list.joinToString(", ") { it.name } + ". Their data on this phone is removed too.", maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            confirmButton = { Button({ vm.uninstallManyAsk = null; vm.uninstallMany(list) }, shapes = ButtonDefaults.shapes()) { Text("Uninstall") } },
            dismissButton = { TextButton({ vm.uninstallManyAsk = null }) { Text("Cancel") } })
    }
    vm.uninstallAsk?.let { a ->
        AlertDialog({ vm.uninstallAsk = null }, icon = { Icon(Sym.Delete, null) }, title = { Text("Uninstall ${a.name}?") },
            text = { Text("The app and all of its data will be removed from this phone.") },
            confirmButton = { Button({ vm.uninstallAsk = null; vm.uninstall(a, true) }, shapes = ButtonDefaults.shapes()) { Text("Uninstall") } },
            dismissButton = { TextButton({ vm.uninstallAsk = null }) { Text("Cancel") } })
    }
    vm.directFailed?.let { f ->
        fun close() { vm.directFailed = null }
        var always by remember(f) { mutableStateOf(false) }
        AlertDialog(::close, icon = { Icon(Sym.ErrorOutline, null) },
            title = { Text(if (f.noWifi) "Wireless debugging needs Wi-Fi" else "Wireless debugging didn't answer") },
            text = {
                Column {
                    Text(if (f.noWifi) "Connect to Wi-Fi to install instantly, or use Android's installer. Android may hold or block apps from unverified developers there."
                        else f.msg + "\n\nTry again, or install with Android's installer.")
                    if (f.noWifi) Row(Modifier.fillMaxWidth().clickable { always = !always }.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(always, { always = it }); Text("Always use the installer without Wi-Fi")
                    }
                }
            },
            confirmButton = { TextButton({ if (always) vm.set { it.copy(offWifiInstall = "installer") }; close(); vm.installFile(f.app, f.file, regular = true) }) { Text("Use installer") } },
            dismissButton = { TextButton(::close) { Text("Cancel") } })
    }
    vm.installIssue?.let { i ->
        fun close() { vm.installIssue = null }
        AlertDialog(::close, icon = { Icon(Sym.ErrorOutline, null) }, title = { Text(i.title) },
            text = { Text(i.text, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) },
            confirmButton = {
                if (i.uninstall != null) TextButton({ close(); vm.tracker.uninstall(i.uninstall) }) { Text("Uninstall current") }
                else TextButton(::close) { Text("Got it") }
            },
            dismissButton = { i.retry?.let { f -> TextButton({ close(); vm.installFile(i.app, f, force = true) }) { Text("Try anyway") } } })
    }
}

/** The occasional ask for support, shown after an install now and then (see MainViewModel.maybeNudgeDonate). */
@Composable
private fun DonateNudgeDialog(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    fun done(support: Boolean, never: Boolean = false) {
        vm.donateNudge = false
        if (never) vm.set { it.copy(donateNudgeOff = true) }
        if (support) { vm.haptic(H.SUCCESS); vm.tab = Tab.ABOUT; vm.donateOpen = true }
    }
    AlertDialog({ done(false) },
        icon = { MorphBlob(listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Heart), 0, 64.dp, cs.tertiaryContainer) { Icon(Sym.VolunteerActivism, null, Modifier.size(30.dp), tint = cs.onTertiaryContainer) } },
        title = { Text("Orion is one person", style = MaterialTheme.typography.headlineSmallEmphasized) },
        text = { Text("Hey, it's RookieZ. The app, the catalog, the mirror bots, the leaderboard relay, every Morphe build and the support inbox are all made and run by me, alone. No tracking and no forced ads. If Orion saved you some time, a small donation keeps it alive.") },
        confirmButton = { Button({ done(true) }, shapes = ButtonDefaults.shapes()) { Text("Support Orion") } },
        dismissButton = {
            Row {
                TextButton({ done(false, never = true) }) { Text("Don't ask again") }
                TextButton({ done(false) }) { Text("Maybe later") }
            }
        })
}

/** Sideloading protection. Offered once by itself while Android's developer-verification component is on, then always reachable from the
 *  header shield, which also reverts it in one tap. Taps outside never close it; only its buttons and Back do. */
@Composable
private fun VerifierDialog(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val step = vm.verifierStep; val revert = vm.verifierReverting
    val working = step == "working"; val safe = step == "protected"; val failed = step == "failed"; val setup = step == "setup"
    AlertDialog({ if (!working) vm.finishVerifier() },
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        icon = {
            when {
                working -> LoadingIndicator(Modifier.size(64.dp))
                safe -> MorphBlob(listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny), 0, 64.dp, cs.primaryContainer) { Icon(Sym.GppGood, null, Modifier.size(32.dp), tint = cs.onPrimaryContainer) }
                else -> MorphBlob(listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Heart), 0, 64.dp, if (failed) cs.errorContainer else cs.tertiaryContainer) {
                    Icon(when { failed -> Sym.GppMaybe; setup -> Sym.Speed; else -> Sym.GppBad }, null, Modifier.size(32.dp), tint = if (failed) cs.onErrorContainer else cs.onTertiaryContainer)
                }
            }
        },
        title = {
            Text(when {
                working -> if (revert) "Reverting" else "Securing"; safe -> "Protected"; failed -> if (revert) "Couldn't revert" else "Couldn't secure"
                setup -> "One quick setup first"; else -> "Protect sideloading"
            }, style = MaterialTheme.typography.headlineSmallEmphasized)
        },
        text = {
            Text(when {
                working -> if (revert) "Switching developer verification back on." else "Switching off developer verification."
                safe -> "Developer verification is off and sideloaded apps install freely. Changed your mind? Revert puts everything back as it was."
                failed -> vm.verifierError
                setup -> "Orion needs a way to run the change: root, Shizuku, or its built-in wireless debugging. Set up wireless debugging once and this popup comes back to finish."
                else -> "Android switched on developer verification, which can slow or block sideloaded apps. Orion can turn it off for you, and undo it any time from the shield up top."
            })
        },
        confirmButton = {
            when {
                working -> {}
                safe -> Button({ vm.finishVerifier() }, shapes = ButtonDefaults.shapes()) { Text("Done") }
                failed -> Button({ if (revert) vm.revertVerifier() else vm.secureDevice() }, shapes = ButtonDefaults.shapes()) { Text("Try again") }
                setup -> Button({ vm.setupForVerifier() }, shapes = ButtonDefaults.shapes()) { Text("Set up") }
                else -> Button({ vm.secureDevice() }, shapes = ButtonDefaults.shapes()) { Text("Secure me") }
            }
        },
        dismissButton = {
            when {
                working -> {}
                safe -> TextButton({ vm.revertVerifier() }) { Text("Revert") }
                else -> TextButton({ vm.finishVerifier() }) { Text(if (failed || setup) "Close" else "Not now") }
            }
        })
}

/** One-time hello for 2.2.0: points everyone at Know Orion. */
@Composable
private fun GuidePromptDialog(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    fun done(open: Boolean) { vm.guidePrompt = false; vm.setFlag("guide", STORE_VERSION); if (open) { vm.haptic(H.SUCCESS); vm.open(Overlay.GUIDE) } }
    AlertDialog({ done(false) },
        icon = { MorphBlob(listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny), 0, 64.dp, cs.primaryContainer) { Icon(Sym.MenuBook, null, Modifier.size(30.dp), tint = cs.onPrimaryContainer) } },
        title = { Text("Get to know Orion", style = MaterialTheme.typography.headlineSmallEmphasized) },
        text = { Text("Every feature now lives in one field guide: backups, compact mode, XP and badges, the Hall of Fame, the Dino run and the rest. You can always find it on the Dev tab.") },
        confirmButton = { Button({ done(true) }, shapes = ButtonDefaults.shapes()) { Text("Show me") } },
        dismissButton = { TextButton({ done(false) }) { Text("Later") } })
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
