package com.orion.store.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import com.orion.store.R
import com.orion.store.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val MODERN_SORTS = listOf(SortOption.NEWEST, SortOption.NAME_ASC, SortOption.NAME_DESC, SortOption.SIZE_ASC, SortOption.SIZE_DESC, SortOption.OLDEST)
private val CLASSIC_SORTS = SortOption.entries - SortOption.HOME
private val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
private val BLOB_SHAPES = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie6Sided, MaterialShapes.Pentagon, MaterialShapes.Flower, MaterialShapes.Gem)
private val FAN = listOf(-6f, 5f, 3f, -4f)

@Composable
fun StoreTab(vm: MainViewModel, platform: String, contentPadding: PaddingValues) {
    val s = LocalS.current; val d = LocalD.current
    val ts = d.tab(vm.tab.key())
    val modern = s.storeLayout == "modern"
    val cats = vm.filterCats
    val visible = vm.visibleForTab()
    val list = vm.classicApps()
    // Grouped once per list and sort, not on every recomposition (download progress alone recomposes this tab twice a second)
    val sort = vm.sortControl
    val groups = remember(list, sort) { if (sort == SortOption.NAME_ASC || sort == SortOption.NAME_DESC) list.groupBy { initial(it.name) }.toList() else listOf("" to list) }
    val modernHome = vm.modernCollections()
    val grid = rememberLazyGridState(); val scope = rememberCoroutineScope()
    // Jump to top only when the filter actually changes, not when returning to a tab whose scroll was restored.
    val view = "${ts.category}|${ts.sort}|${ts.filterFavorites}|${vm.showAllSorted}|${vm.expandedView}"
    var lastView by rememberSaveable { mutableStateOf(view) }
    LaunchedEffect(view) { if (view != lastView) { lastView = view; grid.scrollToItem(0) } }
    val ptr = rememberPullToRefreshState(); val wc = LocalWidthClass.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Classic rows become a card grid on medium and expanded windows: as many 320dp-or-wider columns as the store has room for
        val gridW = minOf(maxWidth, 1200.dp)
        val cols = if (!wc.wide) 1 else ((gridW - 32.dp + 12.dp) / (320.dp + 12.dp)).toInt().coerceAtLeast(1)
        PullToRefreshBox(vm.refreshing, vm::reload, Modifier.fillMaxSize(), state = ptr,
            indicator = { PtrIndicator(ptr, vm.refreshing, s.pullToRefreshCharacter, Modifier.align(Alignment.TopCenter)) }) {
            LazyVerticalGrid(GridCells.Adaptive(if (s.compactMode) 78.dp else 100.dp), Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 1200.dp), grid, contentPadding,
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item("header", span = full) { Header(vm) }
                item("filters", span = full) { Filters(vm, ts, cats, visible.size, platform, modern) }
                when {
                    vm.loading || !vm.tabReady -> item("loading", span = full) { StoreSkeleton(modern) }
                    list.isEmpty() -> item("empty", span = full) {
                        EmptyState(if (ts.filterFavorites) Sym.HeartBroken else Sym.SearchOff, if (ts.filterFavorites) stringResource(R.string.store_no_favorites_found) else stringResource(R.string.store_no_apps_found, platform),
                            if (ts.filterFavorites) stringResource(R.string.store_long_press_any_app_card_or) else null)
                    }
                    modern && modernHome.isNotEmpty() && vm.showModernHome() && !vm.showAllSorted && vm.expandedView == null ->
                        modernHome.forEachIndexed { i, c -> collection(vm, c, i) }
                    modern && vm.expandedView == "new_updated" && vm.expandedApps.isNotEmpty() -> {
                        item("exp-title", span = full) { SectionTitle(stringResource(R.string.store_new_updated), Modifier.padding(top = 8.dp)) }
                        itemsIndexed(vm.expandedApps, key = { _, it -> "exp-" + it.id }) { i, it -> AppTile(vm, it, "exp", Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).reveal(i)) }
                        item("exp-more", span = full) { MoreButton(stringResource(R.string.store_view_more_apps), vm::expandedMore) }
                    }
                    else -> classicList(vm, groups, view, cols)
                }
            }
        }
        val showTop by remember { derivedStateOf { grid.firstVisibleItemIndex > 8 } }
        // Sits just above the dock, and drops to the corner when the dock slides away, instead of floating at a fixed dock-clearing height.
        val lift by animateDpAsState(if (vm.navShown && navPlacement() == "bottom") 88.dp else 16.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "fab")
        AnimatedVisibility(showTop, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = lift), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            SmallFloatingActionButton({ scope.launch { grid.toTop() }; vm.haptic(H.LIGHT) }) { Icon(Sym.KeyboardArrowUp, stringResource(R.string.store_scroll_to_top)) }
        }
    }
}

@Composable
fun StoreSkeleton(modern: Boolean) = Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
    if (modern) {
        // Same footprint as the hero cards, which keep a phone card's width on wide screens
        Skeleton(Modifier.padding(horizontal = 16.dp).fillMaxWidth().widthIn(max = 480.dp).height(176.dp), RoundedCornerShape(28.dp))
        repeat(2) {
            Skeleton(Modifier.padding(start = 20.dp, top = 28.dp).size(180.dp, 22.dp), Pill)
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(4) { Column { Skeleton(Modifier.size(76.dp)); Skeleton(Modifier.padding(top = 10.dp).size(64.dp, 12.dp), Pill) } }
            }
        }
    } else repeat(7) { i ->
        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 2.dp).fillMaxWidth().clip(colShape(i, 7)).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(56.dp)); Spacer(Modifier.width(16.dp))
            Column { Skeleton(Modifier.size(160.dp, 14.dp), Pill); Skeleton(Modifier.padding(top = 8.dp).size(100.dp, 12.dp), Pill) }
        }
    }
}

@Composable
private fun MoreButton(label: String, onClick: () -> Unit) = Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
    FilledTonalButton(onClick, shapes = ButtonDefaults.shapes()) { Text(label); Spacer(Modifier.width(6.dp)); Icon(Sym.ArrowForward, null, Modifier.size(18.dp)) }
}

@Composable
private fun Filters(vm: MainViewModel, ts: TabViewState, cats: List<String>, count: Int, platform: String, modern: Boolean) {
    val cs = MaterialTheme.colorScheme
    val filtered = ts.category != "All" || ts.query.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            item("sort") { SortButton(vm, if (modern) MODERN_SORTS else CLASSIC_SORTS) }
            item("fav") {
                FilledTonalIconToggleButton(ts.filterFavorites, { vm.toggleFavorites() }, shapes = IconButtonDefaults.toggleableShapes()) {
                    Icon(if (ts.filterFavorites) Sym.Favorite else Sym.FavoriteBorder, stringResource(R.string.store_show_favorites))
                }
            }
            if (cats.isNotEmpty()) item("div") { VerticalDivider(Modifier.height(28.dp).padding(horizontal = 4.dp), color = cs.outlineVariant) }
            items(cats, key = { it }) { c ->
                val home = modern && c == "All"
                Choice(ts.category == c, if (home) "Home" else c, if (home) (if (ts.category == c) Sym.HomeFilled else Sym.Home) else null) {
                    if (home) vm.restoreBrowse() else vm.filterCategory(c)
                }
            }
        }
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (modern && filtered) Text(if (ts.category == "All") stringResource(R.string.store_search_results) else ts.category, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.store_apps, count) + (if (ts.query.isNotEmpty()) "“${ts.query}”" else vm.sortControl.label), style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // TV remotes cannot pull to refresh, so the button shows on modern too
            if (!modern || Device.isTv) IconButton(vm::reload, enabled = !vm.refreshing) { if (vm.refreshing) LoadingIndicator(Modifier.size(24.dp)) else Icon(Sym.Refresh, "Refresh") }
            vm.submissionCooldown?.let { AssistChip({}, { Text(it) }, leadingIcon = { Icon(Sym.Schedule, null, Modifier.size(16.dp)) }, enabled = false) }
                ?: TextButton({ vm.open(Overlay.SUBMISSION) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Sym.AddCircleOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.store_submit_app))
                }
        }
    }
}

/** M3 Expressive toggle button used for every filter choice: tonal at rest, filled and squarer when checked. */
@Composable
fun Choice(on: Boolean, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val vm = LocalVm.current
    // A step up on medium and expanded windows: taller, with a bigger icon and label, so filters keep their weight on a large canvas
    val wide = LocalWidthClass.current.wide
    val ic: (@Composable () -> Unit)? = icon?.let { { Icon(it, null, Modifier.size(if (wide) 22.dp else ToggleButtonDefaults.IconSize)) } }
    if (!wide) ToggleButton(on, { vm.haptic(H.SELECTION); onClick() }, shapes = ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.size), icon = ic) { Text(label, maxLines = 1) }
    else ToggleButton(on, { vm.haptic(H.SELECTION); onClick() }, Modifier.heightIn(min = 48.dp), shapes = ToggleButtonDefaults.shapesFor(48.dp),
        contentPadding = PaddingValues(horizontal = 20.dp), icon = ic) { Text(label, maxLines = 1, style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun SortButton(vm: MainViewModel, opts: List<SortOption>) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton({ open = true }, shapes = IconButtonDefaults.shapes()) { Icon(Sym.SwapVert, stringResource(R.string.store_sort_apps)) }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(20.dp)) {
            Text(stringResource(R.string.store_sort_by), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            opts.forEach { o ->
                val on = vm.sortControl == o
                DropdownMenuItem({ Text(o.label, fontWeight = if (on) FontWeight.Bold else null) }, { vm.sort(o); open = false; vm.haptic(H.SELECTION) },
                    leadingIcon = { RadioButton(on, null) })
            }
        }
    }
}

/** Known screenshot ratios: a revisit lays out at the final size at once instead of springing every card again. */
private val shotRatios = HashMap<String, Float>()

/** Screenshot at its natural aspect ratio: no frame, just rounded corners; shape settles once the real size is known (instantly from memory). */
@Composable
fun Shot(url: String, maxW: Dp, maxH: Dp, modifier: Modifier = Modifier, portrait: Boolean = true, corner: Dp = 20.dp, fallback: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return fallback?.invoke() ?: Unit
    val known = shotRatios[url]
    val anim = remember(url) { Animatable(known ?: if (portrait) 9f / 19.5f else 16f / 9f) }
    var loaded by remember(url) { mutableStateOf(known != null) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(); val scope = rememberCoroutineScope()
    val r = anim.value
    val w = if (r < maxW / maxH) maxH * r else maxW
    val shape = RoundedCornerShape(corner)
    val skeleton by animateFloatAsState(if (loaded) 0f else 1f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "sk")
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val src = remember(url) { optimizedImageUrl(url, null, 720) }
    AsyncImage(src, null,
        modifier.size(w, w / r).then(if (onClick != null) Modifier.springClick(shape, onClick = onClick) else Modifier.clip(shape)).drawBehind { if (skeleton > 0f) drawRect(bg.copy(alpha = skeleton)) },
        contentScale = ContentScale.Crop, onError = { failed = true }, onSuccess = {
            val i = it.result.image; val k = i.width.toFloat() / i.height.coerceAtLeast(1); shotRatios[url] = k; loaded = true
            if (anim.targetValue != k) scope.launch { if (it.result.dataSource == coil3.decode.DataSource.MEMORY_CACHE) anim.snapTo(k) else anim.animateTo(k, spec) }
        })
}

/** Classic layout: an M3 Expressive segmented list. Name sorts split into lettered groups, each group one rounded container. */
private fun LazyGridScope.classicList(vm: MainViewModel, groups: List<Pair<String, List<AppItem>>>, view: String, cols: Int) {
    groups.forEach { (letter, apps) ->
        if (letter.isNotEmpty()) item("L-$letter", span = full) { LetterHeader(letter, apps.size) }
        // Keyed per view: a new category or sort arrives as a fresh list with its entrance, instead of old rows gliding across into new slots.
        if (cols == 1) itemsIndexed(apps, key = { _, it -> view + "|" + it.id }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { i, app -> AppRow(vm, app, i, apps.size, Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).reveal(i)) }
        // Wide windows: rows of equal cards that fill their columns exactly, 16dp from the edges and 12dp apart, so no gutter is left over
        else itemsIndexed(apps.chunked(cols), key = { _, row -> view + "|" + row.first().id }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { i, row ->
            Row(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).reveal(i).padding(start = 16.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { app -> AppRow(vm, app, 0, 1, Modifier.weight(1f), inset = 0.dp) }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun initial(name: String) = java.text.Normalizer.normalize(name.trim().take(1), java.text.Normalizer.Form.NFD).firstOrNull()
    ?.takeIf { it.isLetter() }?.uppercase() ?: "#"

@Composable
private fun LetterHeader(letter: String, count: Int) = Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).background(cs.primaryContainer, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) {
        Text(letter, style = MaterialTheme.typography.titleMediumEmphasized, color = cs.onPrimaryContainer)
    }
    Spacer(Modifier.width(12.dp))
    Text(if (count == 1) stringResource(R.string.store_1_app) else stringResource(R.string.store_apps_2, count), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
}

private class Look(val shape: RoundedPolygon, val bg: Color, val fg: Color, val icon: ImageVector)

/** Install state as an expressive shape: cookie to get, sun for an update, clover when installed, gem when ready. Rounds out while the row is pressed. */
@Composable
private fun StateBlob(app: AppItem, src: MutableInteractionSource) {
    val (st, p) = cardState(app); val cs = MaterialTheme.colorScheme
    val look = when (st) {
        CardState.READY -> Look(MaterialShapes.Gem, cs.primary, cs.onPrimary, Sym.InstallMobile)
        CardState.UPDATE -> Look(MaterialShapes.Sunny, cs.tertiaryContainer, cs.onTertiaryContainer, Sym.ArrowUpward)
        CardState.INSTALLED -> Look(MaterialShapes.Clover4Leaf, cs.secondaryContainer, cs.onSecondaryContainer, Sym.Check)
        else -> Look(MaterialShapes.Cookie6Sided, cs.surfaceContainerHighest, cs.onSurfaceVariant, Sym.Download)
    }
    val pressed by src.collectIsPressedAsState()
    val k by animateFloatAsState(if (pressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "blob")
    val morph = remember(look.shape) { Morph(look.shape, MaterialShapes.Circle) }
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        if (st == CardState.DOWNLOADING) {
            val prog by animateFloatAsState(p / 100f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dl")
            if (p <= 0f) CircularWavyProgressIndicator(Modifier.fillMaxSize()) else CircularWavyProgressIndicator({ prog }, Modifier.fillMaxSize())
            Icon(Sym.Download, "Downloading", Modifier.size(18.dp), tint = cs.primary)
        } else {
            Box(Modifier.matchParentSize().graphicsLayer { rotationZ = k * 45f }.background(look.bg, MorphShape(morph, k)))
            Icon(look.icon, null, Modifier.size(20.dp), tint = look.fg)
        }
    }
}

/** List row used by every platform in the classic layout (and for search results): one segment of a grouped list. */
@Composable
fun AppRow(vm: MainViewModel, app: AppItem, index: Int = 0, count: Int = 1, modifier: Modifier = Modifier, inset: Dp = 16.dp) {
    val s = LocalS.current; val compact = s.compactMode; val fav = app.id in LocalD.current.favorites; val cs = MaterialTheme.colorScheme
    val status = statusText(app)
    val src = remember { MutableInteractionSource() }
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    val about = remember(app.description) { app.description.replace(SPACES, " ").trim() }
    // Swipe left to favorite (or unfavorite); the row springs back once the heart has filled.
    SwipeRow(shapes.shape, modifier.padding(horizontal = inset).focusRing(src, shapes.shape), left = SwipeAction(if (fav) Sym.HeartBroken else Sym.Favorite, if (fav) stringResource(R.string.store_remove_from_favorites) else stringResource(R.string.store_add_to_favorites),
        cs.tertiaryContainer, cs.onTertiaryContainer, removes = false) { vm.toggleFavorite(app.id) }) {
    SegmentedListItem({ vm.haptic(H.SELECTION); vm.selectApp(app, "list") }, shapes, if (compact) CompactRowHeight else Modifier,
        leadingContent = { AppIcon(app, if (compact) 44.dp else 52.dp, from = "list") },
        trailingContent = { StateBlob(app, src) },
        overlineContent = if (compact) null else ({
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(categoryIcon(app.category), null, Modifier.size(14.dp), tint = cs.primary); Spacer(Modifier.width(4.dp))
                Text(app.category, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }),
        supportingContent = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Compact drops the category line, so its icon rides along here instead
                    if (compact) { Icon(categoryIcon(app.category), app.category, Modifier.size(14.dp), tint = cs.primary); Spacer(Modifier.width(4.dp)) }
                    Text(status?.first ?: listOf(app.author, app.size).filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = status?.second ?: Color.Unspecified, fontWeight = if (status != null) FontWeight.Medium else null)
                }
                if (!compact && s.showAppAbout && about.isNotEmpty()) AboutPreview(about)
            }
        },
        onLongClick = { vm.toggleFavorite(app.id) }, onLongClickLabel = if (fav) stringResource(R.string.store_remove_from_favorites) else stringResource(R.string.store_add_to_favorites),
        colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainer), contentPadding = if (compact) CompactRowPadding else ListItemDefaults.ContentPadding,
        verticalAlignment = Alignment.CenterVertically, interactionSource = src) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(app.name, Modifier.weight(1f, false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AnimatedVisibility(fav, enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()), exit = scaleOut()) {
                Icon(Sym.Favorite, "Favorite", Modifier.padding(start = 6.dp).size(16.dp), tint = cs.primary)
            }
        }
    }
    }
}

private val SPACES = Regex("\\s+")

/** "About this app" in a classic row: two lines, then See more opens the rest in place. */
@Composable
private fun AboutPreview(text: String) {
    val cs = MaterialTheme.colorScheme
    var open by rememberSaveable(text) { mutableStateOf(false) }; var more by remember(text) { mutableStateOf(false) }
    Column(Modifier.padding(top = 4.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        Text(text, maxLines = if (open) 14 else 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            onTextLayout = { if (!open) more = it.hasVisualOverflow })
        if (more || open) Text(if (open) stringResource(R.string.store_see_less) else stringResource(R.string.store_see_more), Modifier.padding(top = 2.dp).clip(Pill).clickable { open = !open }.padding(vertical = 4.dp, horizontal = 2.dp),
            style = MaterialTheme.typography.labelLarge, color = cs.primary)
    }
}

/** Icon-first tile for grids and swimlanes on every platform. */
@Composable
fun AppTile(vm: MainViewModel, app: AppItem, from: String, modifier: Modifier = Modifier, width: Dp? = null) {
    val featured = app.tags.any { it == "Featured" || it == "Editor's Choice" }; val cs = MaterialTheme.colorScheme; val compact = LocalS.current.compactMode
    BoxWithConstraints(modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())) {
        val w = maxWidth
        Column(Modifier.fillMaxWidth().springClick(onLongClick = { vm.toggleFavorite(app.id) }) { vm.selectApp(app, from) }.padding(if (compact) 6.dp else 8.dp)) {
            AppIcon(app, (w - if (compact) 12.dp else 16.dp).coerceAtMost(if (compact) 72.dp else 96.dp), Modifier.align(Alignment.CenterHorizontally), state = true, from = from)
            Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
            // minLines keeps every tile the same height, so a two-line name scrolling into a lane never changes the lane height and shoves the page.
            val lines = if (compact) 1 else 2
            Text(app.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, minLines = lines, maxLines = lines, overflow = TextOverflow.Ellipsis)
            Text(if (featured) "Featured" else app.category.ifEmpty { app.size }, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                color = if (featured) cs.primary else cs.onSurfaceVariant)
        }
    }
}

private fun LazyGridScope.collection(vm: MainViewModel, c: StoreCollection, index: Int) {
    when (c.type) {
        CollectionType.HERO -> if (c.apps.isNotEmpty()) item(c.id, span = full) { Hero(vm, c, Modifier.reveal(index)) }
        CollectionType.BUNDLE -> if (c.apps.isNotEmpty()) item(c.id, span = full) { Spotlight(vm, c, Modifier.reveal(index)) }
        CollectionType.CATEGORY_CARDS -> if (c.categoryCards.isNotEmpty()) item(c.id, span = full) { Categories(vm, c, Modifier.reveal(index)) }
        CollectionType.RECOMMENDATION_BUNDLES -> if (c.bundles.isNotEmpty()) item(c.id, span = full) { Bundles(vm, c, Modifier.reveal(index)) }
        CollectionType.SORTED_GRID -> if (c.apps.isNotEmpty()) {
            item(c.id + "-t", span = full) { SectionTitle(c.title, Modifier.padding(top = 20.dp), c.subtitle) }
            itemsIndexed(c.apps, key = { _, it -> c.id + it.id }) { i, it -> AppTile(vm, it, c.id, Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).reveal(i)) }
            val total = c.totalAppCount ?: c.apps.size
            if (total > c.apps.size) item(c.id + "-all", span = full) { MoreButton(stringResource(R.string.store_show_all_apps, total), vm::showAll) }
        }
        CollectionType.RANKED -> if (c.apps.isNotEmpty()) item(c.id, span = full) { Ranked(vm, c, Modifier.reveal(index)) }
        CollectionType.SWIMLANE -> if (c.apps.isNotEmpty()) item(c.id, span = full) {
            when (c.id) { "updates-available" -> UpdateDeck(vm, c, Modifier.reveal(index)); "recommended_daily" -> Recommended(vm, c, Modifier.reveal(index)); else -> Swimlane(vm, c, Modifier.reveal(index)) }
        }
    }
}

@Composable
private fun Hero(vm: MainViewModel, c: StoreCollection, modifier: Modifier) = BoxWithConstraints(modifier.padding(top = 8.dp)) {
    val n = c.apps.size; val pager = rememberPagerState { n }; val s = LocalS.current; val cs = MaterialTheme.colorScheme
    val glide = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    // Phones: one card at a time. Wider windows: as many whole cards as fit at 420dp or more, sized to fill the row exactly, so no card
    // is ever cut by the screen edge (that cut left one rounded side and one square side)
    val gap = 10.dp; val room = maxWidth - 32.dp
    val k = if (!LocalWidthClass.current.wide) 1 else ((room + gap) / (420.dp + gap)).toInt().coerceIn(1, n.coerceAtLeast(1))
    val page = (room - gap * (k - 1)) / k
    LaunchedEffect(pager, n, k) {
        if (n > k && !s.calm) while (true) {
            delay(5500)
            if (!pager.isScrollInProgress) pager.animateScrollToPage(if (pager.currentPage >= n - k) 0 else pager.currentPage + 1, animationSpec = glide)
        }
    }
    Column {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(176.dp), contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = gap, key = { c.apps[it].id },
            pageSize = if (k == 1) androidx.compose.foundation.pager.PageSize.Fill else androidx.compose.foundation.pager.PageSize.Fixed(page)) { i ->
            // Side by side cards sit still; the paging tilt and parallax only make sense one card at a time
            HeroCard(vm, c.apps[i], c.id) { if (k == 1) pager.getOffsetDistanceInPages(i).coerceIn(-1f, 1f) else 0f }
        }
        // The dot row keeps its height even when there is nothing to page, so the next section always starts the same distance below
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp), contentAlignment = Alignment.Center) {
            if (n > k) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(n - k + 1) { j ->
                    val on = j == pager.currentPage.coerceAtMost(n - k)
                    val w by animateDpAsState(if (on) 22.dp else 6.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot")
                    Box(Modifier.height(6.dp).width(w).clip(Pill).background(if (on) cs.primary else cs.outlineVariant))
                }
            }
        }
    }
}

/** Featured app on its own icon-derived color, screenshots fanned out and drifting at parallax depth while paging. */
@Composable
private fun HeroCard(vm: MainViewModel, app: AppItem, from: String, offset: () -> Float) {
    val cs = MaterialTheme.colorScheme; val fx = MaterialTheme.motionScheme.slowEffectsSpec<Color>()
    val (bg0, fg0) = iconColor(app)?.let { tonal(it) } ?: (cs.primaryContainer to cs.onPrimaryContainer)
    val bg by animateColorAsState(bg0, fx, label = "bg"); val fg by animateColorAsState(fg0, fx, label = "fg")
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxSize().graphicsLayer { val k = 1f - abs(offset()) * .07f; scaleX = k; scaleY = k }.springClick(shape) { vm.selectApp(app, from) }.background(bg)) {
        Box(Modifier.align(Alignment.BottomEnd).offset(48.dp, 64.dp).size(190.dp).graphicsLayer { translationX = offset() * 90.dp.toPx(); rotationZ = offset() * 40f }
            .background(fg.copy(.08f), MaterialShapes.Cookie12Sided.toShape()))
        val portrait = app.platform == Platform.ANDROID
        val shots = app.screenshots.take(if (portrait) 2 else 1)
        Box(Modifier.align(Alignment.BottomEnd).padding(end = if (portrait) 18.dp else 12.dp).offset(y = if (portrait) 40.dp else 22.dp)
            .graphicsLayer { translationX = offset() * 64.dp.toPx() }) {
            val big = @Composable { AppIcon(app, 96.dp, Modifier.offset(x = (-12).dp, y = (-52).dp).graphicsLayer { rotationZ = -10f; shadowElevation = 12.dp.toPx(); this.shape = RoundedCornerShape(30.dp) }) }
            if (shots.isEmpty()) big()
            shots.forEachIndexed { k, u ->
                val back = k == 0 && shots.size > 1
                Shot(u, if (portrait) 92.dp else 170.dp, if (portrait) 180.dp else 112.dp, Modifier
                    .offset(x = if (back) (-50).dp else 0.dp, y = if (back) 12.dp else 0.dp)
                    .graphicsLayer { rotationZ = if (back) -7f else 4f; shadowElevation = 10.dp.toPx(); this.shape = RoundedCornerShape(16.dp); clip = false },
                    portrait = portrait, corner = 16.dp, fallback = if (k == shots.lastIndex) big else null)
            }
        }
        Column(Modifier.fillMaxHeight().fillMaxWidth(.56f).padding(18.dp).graphicsLayer { translationX = -offset() * 28.dp.toPx() }) {
            AppIcon(app, 48.dp, from = from)
            Spacer(Modifier.weight(1f))
            Text(app.name, style = MaterialTheme.typography.titleLargeEmphasized, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(app.category, app.author).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = fg.copy(.75f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Swimlane(vm: MainViewModel, c: StoreCollection, modifier: Modifier) = Column(modifier.padding(top = 20.dp)) {
    val all = (c.totalAppCount ?: 0) > c.apps.size
    SectionTitle(c.title, subtitle = c.subtitle, onMore = when { all -> ({ vm.showCollectionAll(c) }); c.filter != null -> ({ vm.categoryNavigate(c.filter) }); else -> null })
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
        items(c.apps, key = { it.id }) { AppTile(vm, it, c.id, width = if (LocalS.current.compactMode) 84.dp else 100.dp) }
    }
}

/** 12.3k style, for star counts. */
fun compactCount(n: Int): String = when {
    n < 1000 -> "$n"
    n < 1_000_000 -> "%.1fk".format(java.util.Locale.US, n / 1000.0).replace(".0k", "k")
    else -> "%.1fM".format(java.util.Locale.US, n / 1_000_000.0).replace(".0M", "M")
}

private val RANK_SHAPES = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie7Sided, MaterialShapes.Cookie6Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Pentagon)

/** A chart in columns of three: rank on an M3 shape, icon, name and stars. */
@Composable
private fun Ranked(vm: MainViewModel, c: StoreCollection, modifier: Modifier) = Column(modifier.padding(top = 20.dp)) {
    val cs = MaterialTheme.colorScheme
    SectionTitle(c.title, subtitle = c.subtitle)
    // Columns of three; on wider windows a chart short enough to fit shares the row instead of stopping halfway across it
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val groups = c.apps.chunked(3); val room = maxWidth - 16.dp
    val colW = if (LocalWidthClass.current.wide && groups.size * 296 <= room.value) room / groups.size.coerceAtLeast(1) else 296.dp
    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
        itemsIndexed(groups, key = { i, _ -> i }) { col, group ->
            Column(Modifier.width(colW)) {
                group.forEachIndexed { k, app ->
                    val rank = col * 3 + k; val top = rank < 3
                    Row(Modifier.fillMaxWidth().springClick { vm.selectApp(app, c.id) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).background(if (top) cs.primaryContainer else cs.surfaceContainerHigh, RANK_SHAPES[rank % RANK_SHAPES.size].toShape()), contentAlignment = Alignment.Center) {
                            Text("${rank + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (top) cs.onPrimaryContainer else cs.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        AppIcon(app, 52.dp, from = c.id)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(app.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Sym.StarFilled, null, Modifier.size(14.dp), tint = cs.tertiary); Spacer(Modifier.width(4.dp))
                                Text(compactCount(app.stars ?: 0), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                                if (app.category.isNotEmpty()) Text(" · ${app.category}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/** Updates as a slim strip, not a hero: overlapping icons, the count and who is waiting, and a one-tap Update all. */
@Composable
private fun UpdateDeck(vm: MainViewModel, c: StoreCollection, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme; val d = LocalD.current
    val n = c.totalAppCount ?: c.apps.size
    // A strip, not a banner: on wide screens it stops at a card's width instead of running the length of the store
    Row(modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).fillMaxWidth().widthIn(max = 760.dp).clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer)
        .clickable { vm.haptic(H.SELECTION); vm.showCollectionAll(c) }.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width((40 + (minOf(3, c.apps.size) - 1) * 22).dp).height(40.dp)) {
            c.apps.take(3).forEachIndexed { i, a -> AppIcon(a, 40.dp, Modifier.offset(x = (i * 22).dp).graphicsLayer { shadowElevation = 2.dp.toPx(); shape = RoundedCornerShape(10.dp) }, from = c.id) }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(if (n == 1) stringResource(R.string.store_1_update_ready) else stringResource(R.string.store_updates_ready, n), style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
            Text(c.apps.take(2).joinToString(", ") { it.name } + if (n > 2) " +${n - 2}" else "", style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton({ vm.haptic(H.MEDIUM); vm.updates.filter { it.installsHere() && it.platform == c.apps.firstOrNull()?.platform && it.id !in d.activeDownloads && it.id !in d.readyToInstall }.forEach { vm.download(it) } },
            shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text(stringResource(R.string.store_update_all)) }
    }
}

/** Recommended as an M3 multi-browse carousel: each card wears its icon's color, and copy fades out as a card shrinks to the edge. */
@Composable
private fun Recommended(vm: MainViewModel, c: StoreCollection, modifier: Modifier) = Column(modifier.padding(top = 20.dp)) {
    val cs = MaterialTheme.colorScheme
    SectionTitle(c.title, subtitle = c.subtitle)
    HorizontalMultiBrowseCarousel(androidx.compose.material3.carousel.rememberCarouselState { c.apps.size }, preferredItemWidth = 176.dp,
        Modifier.fillMaxWidth().height(220.dp), itemSpacing = 8.dp, contentPadding = PaddingValues(horizontal = 16.dp)) { i ->
        val app = c.apps[i]
        val (bg, fg) = iconColor(app)?.let { tonal(it) } ?: (cs.secondaryContainer to cs.onSecondaryContainer)
        val info = carouselItemDrawInfo
        Box(Modifier.fillMaxSize().maskClip(RoundedCornerShape(28.dp)).background(bg).clickable { vm.haptic(H.SELECTION); vm.selectApp(app, c.id) }) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 20.dp).size(132.dp).background(fg.copy(.1f), MaterialShapes.Cookie9Sided.toShape()))
            // No shared hand-off here: the carousel offsets items in a graphics layer, so the return flight lands off target and then snaps
            AppIcon(app, 84.dp, Modifier.align(Alignment.TopCenter).padding(top = 44.dp), state = true)
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp).graphicsLayer {
                alpha = ((info.size - info.minSize) / (info.maxSize - info.minSize).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }) {
                Text(app.name, style = MaterialTheme.typography.titleMediumEmphasized, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.category, style = MaterialTheme.typography.labelMedium, color = fg.copy(.8f), maxLines = 1)
            }
        }
    }
}

/** Curated picks as a numbered chart. Phones: three rows per column, scrolling sideways. Wider windows: the six picks fill the
 *  row in two or three equal columns, so a short list never leaves half the screen empty. */
@Composable
private fun Spotlight(vm: MainViewModel, c: StoreCollection, modifier: Modifier) = Column(modifier.padding(top = 20.dp)) {
    val cs = MaterialTheme.colorScheme; val picks = c.apps.take(6)
    SectionTitle(c.title, subtitle = c.subtitle)
    @Composable fun Pick(i: Int, app: AppItem, m: Modifier) =
        Row(m.springClick { vm.selectApp(app, c.id) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", Modifier.width(30.dp), style = MaterialTheme.typography.headlineSmallEmphasized, color = cs.primary)
            AppIcon(app, 56.dp, state = true, from = c.id)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(app.category, app.size).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant, maxLines = 1)
            }
        }
    if (!LocalWidthClass.current.wide) LazyHorizontalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().height(228.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        itemsIndexed(picks, key = { _, it -> it.id }) { i, app -> Pick(i, app, Modifier.width(312.dp)) }
    } else BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val cols = (maxWidth / 300.dp).toInt().coerceIn(2, 3)
        Column {
            picks.chunked(cols).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { k, app -> Pick(r * cols + k, app, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Categories as expressive shape blobs that round out while pressed. Bigger on medium and expanded windows, and spread across the
 *  whole row when they all fit, instead of huddling at the start. */
@Composable
private fun Categories(vm: MainViewModel, c: StoreCollection, modifier: Modifier) = Column(modifier.padding(top = 20.dp)) {
    val cs = MaterialTheme.colorScheme; val wide = LocalWidthClass.current.wide
    if (c.title.isNotEmpty()) SectionTitle(c.title, subtitle = c.subtitle)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = c.categoryCards.size.coerceAtLeast(1)
        val base = if (wide) 112.dp else 88.dp
        val itemW = if (!wide) base else ((maxWidth - 24.dp - 4.dp * (n - 1)) / n).coerceIn(base, 168.dp)
        val blob = if (wide) 88.dp else 72.dp
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(c.categoryCards, key = { _, it -> it.id }) { i, card ->
                val (bg, fg) = tonal(hex(card.accent, cs.primary))
                val src = remember { MutableInteractionSource() }; val pressed by src.collectIsPressedAsState()
                val morph = remember(i) { Morph(BLOB_SHAPES[i % BLOB_SHAPES.size], MaterialShapes.Circle) }
                val p by animateFloatAsState(if (pressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "m")
                Column(Modifier.width(itemW).springClick(RoundedCornerShape(24.dp), src = src) { vm.categoryNavigate(card.label) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(blob).graphicsLayer { rotationZ = p * 30f }.background(bg, MorphShape(morph, p)), contentAlignment = Alignment.Center) {
                        Icon(faIcon(card.icon, Sym.Layers), null, Modifier.size(if (wide) 36.dp else 30.dp).graphicsLayer { rotationZ = -p * 30f }, tint = fg)
                    }
                    Text(card.label, Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), style = if (wide) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.store_apps_3, card.count), style = if (wide) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }

            }
        }
    }
}

/** Bundles as app folders: the bundle's own color with its apps tumbling inside. */
@Composable
private fun Bundles(vm: MainViewModel, c: StoreCollection, modifier: Modifier) = Column(modifier.padding(top = 20.dp)) {
    val s = LocalS.current; val cs = MaterialTheme.colorScheme
    val folder = RoundedCornerShape(40.dp)
    SectionTitle(c.title, subtitle = c.subtitle)
    // Wider windows: folders grow to share the row when there are only a few, so the shelf never ends in a dead gap
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val n = c.bundles.size + 1
    val itemW = if (!LocalWidthClass.current.wide) 164.dp else ((maxWidth - 24.dp - 4.dp * (n - 1)) / n).coerceIn(164.dp, 300.dp)
    val box = itemW - 8.dp; val icon = (box - 48.dp) / 2
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(c.bundles, key = { _, b -> b.id }) { i, b ->
            val removed = s.removedBundleApps[b.id].orEmpty()
            val count = (b.apps.map { it.id }.filter { it !in removed } + s.extraBundleApps[b.id].orEmpty()).toSet().size
            if (count == 0) return@itemsIndexed
            val (bg, _) = b.color?.let { tonal(hex(it, cs.primary)) } ?: listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer)[i % 3].let { it to it }
            Column(Modifier.width(itemW).springClick(RoundedCornerShape(28.dp)) { vm.selectedBundle = b }.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
                Box(Modifier.size(box).clip(folder).background(bg), contentAlignment = Alignment.Center) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        b.apps.filter { it.id !in removed }.take(4).chunked(2).forEachIndexed { r, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEachIndexed { k, a -> AppIcon(a, icon, Modifier.graphicsLayer { rotationZ = FAN[(r * 2 + k) % FAN.size] }) }
                            }
                        }
                    }
                    b.badge?.let { Badge(Modifier.align(Alignment.TopEnd).padding(12.dp), containerColor = cs.primary, contentColor = cs.onPrimary) { Text(it, Modifier.padding(horizontal = 4.dp)) } }
                }
                Text(b.title, Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp), style = MaterialTheme.typography.titleSmallEmphasized, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.store_apps_2, count), Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        item("create") {
            val line = cs.outline
            Column(Modifier.width(itemW).springClick(RoundedCornerShape(28.dp)) { vm.open(Overlay.CUSTOM_BUNDLE) }.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
                Box(Modifier.size(box).drawBehind {
                    drawRoundRect(line, cornerRadius = CornerRadius(40.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
                }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(56.dp).background(cs.primaryContainer, MaterialShapes.Cookie6Sided.toShape()), contentAlignment = Alignment.Center) {
                        Icon(Sym.Add, null, tint = cs.onPrimaryContainer)
                    }
                }
                Text(stringResource(R.string.store_create_bundle), Modifier.padding(start = 6.dp, top = 10.dp), style = MaterialTheme.typography.titleSmallEmphasized)
                Text(stringResource(R.string.store_pick_your_own_apps), Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
    }
    }
}
