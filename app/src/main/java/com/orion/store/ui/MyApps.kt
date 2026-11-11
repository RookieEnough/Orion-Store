package com.orion.store.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orion.store.R
import com.orion.store.data.*
import kotlinx.coroutines.launch

private val Sky = Color(0xFF0EA5E9)
private fun filterIcon(f: MyAppsFilter) = when (f) {
    MyAppsFilter.ALL -> Sym.Inventory2; MyAppsFilter.INSTALLED -> Sym.CheckCircle
    MyAppsFilter.DOWNLOADED -> Sym.Download; MyAppsFilter.UPDATES -> Sym.ArrowUpward
}
private val EMPTY = mapOf(MyAppsFilter.ALL to R.string.myapps_no_apps_yet, MyAppsFilter.INSTALLED to R.string.myapps_no_installed_apps, MyAppsFilter.DOWNLOADED to R.string.myapps_no_downloaded_apps, MyAppsFilter.UPDATES to R.string.myapps_no_pending_updates)

@Composable
private fun tone(r: MyAppRow): Pair<ImageVector, Color> = when {
    r.status == "downloading" -> Sym.ArrowDownward to MaterialTheme.colorScheme.primary
    r.status == "ready" -> Sym.Inventory to Safe
    r.hasUpdate -> Sym.ArrowUpward to Caution
    r.isInstalledNow -> Sym.CheckCircle to Sky
    r.libraryStatus == "installed" -> Sym.History to MaterialTheme.colorScheme.onSurfaceVariant
    else -> Sym.FileDownload to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun MyAppsTab(vm: MainViewModel, padding: PaddingValues) {
    val s = LocalS.current; val d = LocalD.current
    val filters = myAppsFilterOrder(s.myAppsFilters)
    var savedFilter by rememberSaveable { mutableStateOf(MyAppsFilter.ALL.name) }
    val pager = rememberPagerState(initialPage = filters.indexOfFirst { it.name == savedFilter }.coerceAtLeast(0)) { filters.size }
    val pageFilter = filters.getOrElse(pager.currentPage) { filters.first() }
    LaunchedEffect(pager.currentPage, filters) { savedFilter = pageFilter.name }
    val scope = rememberCoroutineScope()
    val query = vm.libraryQuery
    var unmanagedOpen by rememberSaveable { mutableStateOf(false) }
    val updateIds = vm.updates.map { it.id }.toSet()
    val rows = remember(d, s, updateIds) { buildMyAppsRows(vm.allApps(), s, d, updateIds) }
    val pending = rows.filter { it.hasUpdate && it.status != "downloading" }
    val visible = rows.filter { it.matches(pageFilter) && it.matchesQuery(query) }
    val queue = d.activeDownloads.size + d.readyToInstall.size
    val cs = MaterialTheme.colorScheme
    // Long-press an installed app to start picking, tap more to add them, then uninstall them together
    var picked by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val removable = visible.filter { it.isInstalledNow && it.app.installsHere() }
    LaunchedEffect(rows) { picked = picked.filterTo(mutableSetOf()) { id -> rows.any { it.app.id == id && it.isInstalledNow } } }
    androidx.activity.compose.BackHandler(picked.isNotEmpty()) { picked = emptySet() }
    Column(Modifier.fillMaxSize().readableWidth()) {
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            Header(vm)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton({ vm.haptic(H.SELECTION); vm.openSettings("queue") }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) {
                    Icon(Sym.Downloading, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Queue")
                    if (queue > 0) { Spacer(Modifier.width(6.dp)); Badge { Text("$queue") } }
                }
                VerticalDivider(Modifier.height(28.dp).padding(horizontal = 4.dp), color = cs.outlineVariant)
                FilterPills(vm, s.myAppsFilters) { f -> Choice(pageFilter == f, "${f.label} · ${rows.count { it.matches(f) }}", filterIcon(f)) {
                    scope.launch { pager.animateScrollToPage(filters.indexOf(f).coerceAtLeast(0)) }
                } }
            }
        }
        if (picked.isNotEmpty()) Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(28.dp), color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, shadowElevation = 2.dp) {
            Row(Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ picked = emptySet(); vm.haptic(H.LIGHT) }) { Icon(Sym.Close, stringResource(R.string.myapps_stop_selecting)) }
                Text(stringResource(R.string.myapps_selected_count, picked.size), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (picked.size < removable.size) TextButton({ picked = removable.map { it.app.id }.toSet(); vm.haptic(H.SELECTION) }) { Text(stringResource(R.string.myapps_select_all)) }
                Button({ vm.uninstallManyAsk = rows.filter { it.app.id in picked }.map { it.app }; picked = emptySet() }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) {
                    Icon(Sym.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.myapps_uninstall))
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val filter = filters[page]
            val shown = rows.filter { it.matches(filter) && it.matchesQuery(query) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
                if (filter == MyAppsFilter.UPDATES && pending.isNotEmpty()) item {
                    CtaButton(stringResource(R.string.myapps_update_all, pending.size), { vm.haptic(H.MEDIUM); pending.forEach { vm.download(it.app) } }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), Sym.SystemUpdate)
                }
                if (vm.loading) item("loading") { StoreSkeleton(false) }
                else if (shown.isEmpty()) item {
                    if (query.isNotBlank()) Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Sym.SearchOff, null, Modifier.size(48.dp), tint = cs.onSurfaceVariant)
                        Text(stringResource(R.string.myapps_no_apps_matching, query), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                        TextButton({ vm.libraryQuery = "" }) { Text(stringResource(R.string.guide_clear_search)) }
                    } else EmptyState(filterIcon(filter), stringResource(EMPTY.getValue(filter)), if (filter == MyAppsFilter.ALL) stringResource(R.string.myapps_apps_you_download_swipe) else stringResource(R.string.myapps_swipe_sideways), Modifier.padding(top = 32.dp))
                }
                itemsIndexed(shown, key = { _, it -> it.app.id }) { i, r ->
                    val can = r.isInstalledNow && r.app.installsHere()
                    LibraryRow(vm, r, Modifier.animateItem().reveal(i), if (picked.isEmpty()) null else (r.app.id in picked),
                        onPick = if (!can) null else ({ vm.haptic(H.SELECTION); picked = if (r.app.id in picked) picked - r.app.id else picked + r.app.id }))
                }
                val now = System.currentTimeMillis(); val lookup = vm.lookup
                val ignored = if (filter == MyAppsFilter.ALL || filter == MyAppsFilter.UPDATES)
                    s.ignoredUpdates.keys.mapNotNull { id -> lookup[id]?.takeIf { isManaged(id, s) && isUpdateIgnored(it, s, now) } } else emptyList()
                if (ignored.isNotEmpty()) item("ignored-title") {
                    Text(stringResource(R.string.myapps_ignored_updates, ignored.size), Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMediumEmphasized)
                }
                itemsIndexed(ignored, key = { _, a -> "ignored-${a.id}" }) { i, a ->
                    val ig = s.ignoredUpdates[a.id]
                    val until = when (ig?.type) {
                        "week" -> ((ONE_WEEK_MS - (now - (ig.timestamp ?: now))) / 86_400_000L + 1).coerceIn(1, 7).let { if (it == 1L) stringResource(R.string.myapps_hidden_more_day) else stringResource(R.string.myapps_hidden_more_days, it) }
                        "version" -> stringResource(R.string.myapps_hidden_until_the_next_version); else -> stringResource(R.string.myapps_hidden_for_good)
                    }
                    SideRow(a, i, ignored.size, until, stringResource(R.string.myapps_unignore)) { vm.set { it.copy(ignoredUpdates = it.ignoredUpdates - a.id) } }
                }
                if (filter == MyAppsFilter.ALL || filter == MyAppsFilter.INSTALLED) {
                    val off = vm.allApps().filter { !isManaged(it.id, s) && (it.id in s.installedVersions || s.appManage[it.id] == false) }
                    if (off.isNotEmpty()) item("unmanaged-title") {
                        val open = unmanagedOpen
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp).clickable { vm.haptic(H.SELECTION); unmanagedOpen = !open }.padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.myapps_not_managed_count, off.size), Modifier.weight(1f), style = MaterialTheme.typography.titleMediumEmphasized)
                            Icon(if (open) Sym.ExpandLess else Sym.ExpandMore, if (open) stringResource(R.string.myapps_collapse) else stringResource(R.string.myapps_expand), tint = cs.onSurfaceVariant)
                        }
                    }
                    if (unmanagedOpen) itemsIndexed(off, key = { _, a -> "unmanaged-${a.id}" }) { i, a ->
                        val why = if (s.appManage[a.id] == false) stringResource(R.string.myapps_hidden_by_you) else otherStore(a.id, s)?.let { stringResource(R.string.myapps_installed_by, vm.tracker.labelOf(it)) } ?: stringResource(R.string.myapps_not_managed)
                        SideRow(a, i, off.size, why, stringResource(R.string.myapps_manage)) { vm.set { it.withManaged(a.id, true) } }
                    }
                }
            }
        }
    }
}

/** A segmented row with one tonal action, for the ignored and not-managed lists. Segmented items draw their own rounded container,
 *  so the corners come out the same on every GPU instead of depending on clip support. */
@Composable
private fun LazyItemScope.SideRow(a: AppItem, i: Int, n: Int, supporting: String, action: String, onAction: () -> Unit) {
    val vm = LocalVm.current; val compact = LocalS.current.compactMode
    SegmentedListItem({ vm.selectApp(a) }, ListItemDefaults.segmentedShapes(i, n), Modifier.animateItem().padding(horizontal = 16.dp).then(if (compact) CompactRowHeight else Modifier),
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingContent = { AppIcon(a, if (compact) 36.dp else 44.dp) },
        trailingContent = { FilledTonalButton({ onAction(); vm.haptic(H.SUCCESS) }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text(action) } },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), contentPadding = if (compact) CompactRowPadding else ListItemDefaults.ContentPadding,
        verticalAlignment = Alignment.CenterVertically) {
        Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Filter pills in the saved order. Long-press a pill and drag it sideways to move it; the order saves on release. */
@Composable
private fun FilterPills(vm: MainViewModel, saved: List<String>, pill: @Composable (MyAppsFilter) -> Unit) {
    var order by remember(saved) { mutableStateOf(myAppsFilterOrder(saved)) }
    var dragged by remember { mutableStateOf<MyAppsFilter?>(null) }; var dx by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableMapOf<MyAppsFilter, Int>() }; val gap = with(LocalDensity.current) { 6.dp.toPx() }
    fun end() { if (dragged != null) vm.set { it.copy(myAppsFilters = order.map { f -> f.name }) }; dragged = null; dx = 0f }
    order.forEach { f ->
        key(f) {
            val on = dragged == f
            Box(Modifier.onSizeChanged { widths[f] = it.width }.zIndex(if (on) 1f else 0f)
                .graphicsLayer { if (on) { translationX = dx; scaleX = 1.06f; scaleY = 1.06f } }
                .pointerInput(f) {
                    detectDragGesturesAfterLongPress({ dragged = f; dx = 0f; vm.haptic(H.MEDIUM) }, ::end, ::end) { c, delta ->
                        c.consume(); dx += delta.x
                        val i = order.indexOf(f); val j = if (dx > 0) i + 1 else i - 1
                        val w = (order.getOrNull(j)?.let { widths[it] } ?: return@detectDragGesturesAfterLongPress) + gap
                        if (kotlin.math.abs(dx) > w / 2) { order = order.toMutableList().apply { add(j, removeAt(i)) }; dx -= if (j > i) w else -w; vm.haptic(H.SELECTION) }
                    }
                }) { pill(f) }
        }
    }
}

@Composable
private fun LibraryRow(vm: MainViewModel, r: MyAppRow, modifier: Modifier, picked: Boolean? = null, onPick: (() -> Unit)? = null) {
    val (icon, c) = tone(r); val cs = MaterialTheme.colorScheme; val compact = LocalS.current.compactMode
    val bg by animateColorAsState(if (picked == true) cs.secondaryContainer else Color.Transparent, label = "pick")
    // While picking a tap toggles the row (rows that can't be uninstalled do nothing); otherwise a long-press starts picking
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp).background(bg, RoundedCornerShape(20.dp))
        .springClick(onLongClick = if (picked == null) onPick else null) { if (picked == null) vm.selectApp(r.app, "lib") else onPick?.invoke() }
        .animateContentSize().padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val size = if (compact) 40.dp else 52.dp
            Box(contentAlignment = Alignment.Center) {
                AppIcon(r.app, size, state = true, from = "lib")
                if (picked == true) Box(Modifier.size(size).background(cs.primary, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) { Icon(Sym.Check, "Selected", tint = cs.onPrimary) }
            }
            Spacer(Modifier.width(if (compact) 12.dp else 16.dp))
            Column(Modifier.weight(1f)) {
                Text(r.app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) Text("${r.app.author} · ${r.app.platform} · ${r.versionDetail}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(14.dp), tint = c); Spacer(Modifier.width(4.dp))
                    Text(r.statusLabel, style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1)
                }
            }
            val action = r.primaryAction
            if (r.hasSavedApk && action in setOf("install", "update", "download")) IconButton({ vm.deleteLibraryApk(r.app) }) { Icon(Sym.DeleteOutline, stringResource(R.string.detail_delete_apk)) }
            @Composable fun Primary(label: String, onClick: () -> Unit) =
                FilledTonalButton(onClick, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text(label) }
            when {
                r.status == "downloading" -> FilledTonalIconButton({ vm.cancelDownload(r.app.id) }, shapes = IconButtonDefaults.shapes()) { Icon(Sym.Close, stringResource(R.string.detail_cancel_download)) }
                action == "install" -> Primary("Install") { r.savedFile?.let { vm.installFile(r.app, it) } }
                action == "update" -> Primary("Update") { vm.download(r.app) }
                action == "download" -> Primary("Download") { vm.download(r.app) }
                action == "delete" -> IconButton({ vm.deleteLibraryApk(r.app) }) { Icon(Sym.DeleteOutline, stringResource(R.string.detail_delete_apk)) }
                !r.isInstalledNow || !r.app.installsHere() -> Icon(Sym.ChevronRight, stringResource(R.string.myapps_open_details), tint = cs.onSurfaceVariant)
            }
            if (picked == null && r.isInstalledNow && r.app.installsHere() && r.status != "downloading") Box {
                var menu by remember { mutableStateOf(false) }
                val launchErr = stringResource(R.string.myapps_could_not_launch)
                IconButton({ menu = true; vm.haptic(H.LIGHT) }) { Icon(Sym.MoreVert, stringResource(R.string.myapps_more_options_for, r.app.name)) }
                DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(20.dp)) {
                    val pkg = LocalS.current.resolvedPackageNames[r.app.id] ?: r.app.packageName
                    if (pkg != null) DropdownMenuItem({ Text(stringResource(R.string.myapps_open)) }, { menu = false; runCatching { vm.tracker.launch(pkg) }.onFailure { vm.error(launchErr) } },
                        leadingIcon = { Icon(Sym.OpenInNew, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.myapps_uninstall)) }, { menu = false; vm.uninstall(r.app) }, leadingIcon = { Icon(Sym.Delete, null) })
                }
            }
        }
        if (r.status == "downloading") Row(Modifier.padding(start = if (compact) 52.dp else 68.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val p by animateFloatAsState(r.progress / 100f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "p")
            LinearWavyProgressIndicator({ p }, Modifier.weight(1f)); Spacer(Modifier.width(12.dp))
            Text("${Math.round(r.progress)}%", style = MaterialTheme.typography.labelLarge)
        }
    }
}
