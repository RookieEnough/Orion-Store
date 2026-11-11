package com.orion.store.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.orion.store.R
import com.orion.store.data.*
import com.orion.store.platform.ResultHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

@Composable
private fun sourceIcon(s: OrbitSource): ImageVector = when (s) {
    OrbitSource.GITHUB -> Sym.BrandGitHub; OrbitSource.GITLAB -> Sym.BrandGitLab; OrbitSource.CODEBERG, OrbitSource.FORGEJO -> Sym.BrandCodeberg
    OrbitSource.FDROID, OrbitSource.IZZY -> Sym.Inventory
    OrbitSource.UPTODOWN -> Sym.Public; OrbitSource.APKMIRROR -> Sym.Layers; OrbitSource.APKPURE -> Sym.Download
    OrbitSource.APTOIDE -> Sym.DownloadForOffline; OrbitSource.SOURCEFORGE -> Sym.Folder
    OrbitSource.ITCH -> Sym.SportsEsports; OrbitSource.FARSROID -> Sym.Public; OrbitSource.LITEAPKS -> Sym.Download
    OrbitSource.APK4FREE -> Sym.DownloadForOffline; OrbitSource.COOLAPK -> Sym.Apps; OrbitSource.SOURCEHUT -> Sym.Terminal
    OrbitSource.JENKINS -> Sym.AccountTree
    OrbitSource.TELEGRAM -> Sym.Chat; OrbitSource.DISCORD -> Sym.BrandDiscord; OrbitSource.APK -> Sym.Android; OrbitSource.WEB -> Sym.Public
}

/** Orbit: apps the user tracks straight from their source. One card per app with the one action it needs right now,
 *  and add, discover and import one tap away. Phones get a list, wider windows a grid of cards. */
@Composable
fun OrbitTab(vm: MainViewModel, padding: PaddingValues) {
    val d = LocalD.current; val s = LocalS.current
    val orbit = d.orbit
    val items = remember(orbit) { orbit.associate { it.id to it.toItem() } }
    var add by remember { mutableStateOf<OrbitApp?>(null) }; var adding by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<List<OrbitApp>?>(null) }; var discover by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf<String?>(null) }
    var pickedSources by remember { mutableStateOf(setOf<String>()) }
    var pickedTags by remember { mutableStateOf(setOf<String>()) }
    var updatesOnly by remember { mutableStateOf(false) }
    var tokens by remember { mutableStateOf(false) }
    var sourcesHelp by remember { mutableStateOf(false) }
    LaunchedEffect(vm.orbitIncoming) {
        val inc = vm.orbitIncoming ?: return@LaunchedEffect
        vm.orbitIncoming = null
        if (inc.size == 1) { add = inc[0]; adding = true } else importing = inc
    }
    val q = vm.orbitQuery.trim()
    val updates = vm.updates.filter { Orbit.owns(it.id) && it.id !in d.activeDownloads && it.id !in d.readyToInstall }
    val updateIds = updates.map { it.id }.toSet()
    val sources = remember(orbit) { orbit.map { Orbit.sourceOf(it.url) }.distinct() }
    val tags = remember(orbit) { orbit.flatMap { it.tags }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted() }
    val shown = remember(orbit, q, pickedSources, pickedTags, updatesOnly, updateIds) {
        orbit.filter { o ->
            val hit = q.isEmpty() || listOf(o.name, o.author, o.url, o.tags.joinToString(" ")).any { it.contains(q, true) }
            val srcOk = pickedSources.isEmpty() || Orbit.sourceOf(o.url).label in pickedSources
            val tagOk = pickedTags.isEmpty() || o.tags.any { it in pickedTags }
            val updOk = !updatesOnly || o.id in updateIds
            hit && srcOk && tagOk && updOk
        }
    }
    val groups = remember(shown, pickedSources, pickedTags, updatesOnly, q) {
        if (pickedSources.isNotEmpty() || pickedTags.isNotEmpty() || updatesOnly || q.isNotEmpty()) listOf("" to shown) else shown.groupBy { Orbit.sourceOf(it.url).label }.entries.map { it.key to it.value }
    }
    val ptr = rememberPullToRefreshState()
    var shareNow by remember { mutableStateOf(false) }
    LaunchedEffect(vm.orbitShareFocus) {
        val id = vm.orbitShareFocus ?: return@LaunchedEffect
        vm.orbitShareFocus = null
        shareNow = true
        acting = id
    }
    // Same as the store: the header and the actions scroll away, and the last cards slide under the dock instead of stopping above it.
    PullToRefreshBox(vm.orbitRefreshing, { vm.orbitRefresh() }, Modifier.fillMaxSize(), state = ptr,
        indicator = { PtrIndicator(ptr, vm.orbitRefreshing, s.pullToRefreshCharacter, Modifier.align(Alignment.TopCenter)) }) {
        LazyVerticalGrid(GridCells.Adaptive(340.dp), Modifier.fillMaxSize().adaptiveContentWidth(1200.dp), contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item("header", span = full) { Header(vm) }
            item("bar", span = full) {
                OrbitBar(sources, tags, pickedSources, pickedTags, updatesOnly, updates.size, { pickedSources = emptySet(); pickedTags = emptySet(); updatesOnly = false }, { updatesOnly = !updatesOnly }, { pickedSources = it }, { pickedTags = it }, { add = null; adding = true }, { discover = true }, { importing = emptyList() }, { tokens = true }, { sourcesHelp = true })
            }
            if (orbit.isEmpty()) item("empty", span = full) { OrbitEmpty() }
            else if (shown.isEmpty()) item("none", span = full) { EmptyState(Sym.SearchOff, stringResource(R.string.orbit_nothing_in_this_view), stringResource(R.string.orbit_try_another_tag_or_clear_search)) }
            groups.forEach { (title, apps) ->
                if (title.isNotEmpty() && groups.size > 1) item("h-$title", span = full) { SectionTitle(title, Modifier.padding(start = 16.dp, top = 6.dp)) }
                items(apps, key = { it.id }) { o ->
                    val item = items[o.id] ?: return@items
                    OrbitCard(vm, o, item, Modifier.padding(horizontal = 16.dp).animateItem(fadeInSpec = null, fadeOutSpec = null), onOpen = { vm.selectApp(item, "orbit") }) { shareNow = false; acting = o.id }
                }
            }
        }
    }
    if (!s.seenOrbitHello) OrbitHello(vm)
    vm.orbitRefusal?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.orbitRefusal = null },
            icon = { Icon(Sym.WarningAmber, null) },
            title = { Text(stringResource(R.string.guide_not_for_orbit)) },
            text = { Text(msg) },
            confirmButton = { Button({ vm.orbitRefusal = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_ok)) } },
        )
    }
    if (vm.orbitShareAsk) AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Sym.VolunteerActivism, null) },
        title = { Text(stringResource(R.string.orbit_share_a_few_with_library)) },
        text = { Text(stringResource(R.string.orbit_share_prompt_body, orbit.size)) },
        confirmButton = { Button(vm::shareFromPrompt, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.orbit_share_one)) } },
        dismissButton = { TextButton(vm::laterOrbitShare) { Text(stringResource(R.string.common_later)) } },
    )
    if (adding) OrbitAddSheet(vm, add) { adding = false; add = null }
    importing?.let { OrbitImportSheet(vm, it) { importing = null } }
    if (discover) OrbitDiscoverSheet(vm) { discover = false }
    if (tokens) OrbitTokenSheet(vm) { tokens = false }
    if (sourcesHelp) OrbitSourcesSheet { sourcesHelp = false }
    acting?.let { id -> orbit.firstOrNull { it.id == id }?.let { o -> OrbitAppSheet(vm, o, items[o.id], shareNow, { acting = null; shareNow = false; add = o; adding = true }) { acting = null; shareNow = false } } }
}

/** GitHub, GitLab and Codeberg tokens. GitHub writes the token Developer tools already uses. */
@Composable
private fun OrbitTokenSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val s = LocalS.current
    var gh by remember { mutableStateOf(s.githubToken) }
    var gl by remember { mutableStateOf(s.gitlabToken) }
    var cb by remember { mutableStateOf(s.codebergToken) }
    var show by remember { mutableStateOf(false) }
    OrionSheet(onDismiss) {
        SheetTitle(stringResource(R.string.guide_source_tokens), Sym.Key, stringResource(R.string.orbit_paste_token_link_makes_one))
        Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.orbit_token_explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            @Composable fun Field(label: String, value: String, hint: String, help: String, link: String, linkLabel: String, onChange: (String) -> Unit) {
                OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text(hint) }, supportingText = { Text(help) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (show) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Sym.VisibilityOff else Sym.Visibility, if (show) stringResource(R.string.common_hide) else stringResource(R.string.common_show)) } })
                TextButton({ vm.tracker.open(link) }) { Icon(Sym.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(linkLabel) }
            }
            Field("GitHub", gh, "ghp_ or github_pat_", stringResource(R.string.orbit_github_token_help), "https://github.com/settings/tokens/new?description=Orion%20Orbit&scopes=public_repo", stringResource(R.string.orbit_create_github_token), { gh = it })
            Field("GitLab", gl, "glpat-", stringResource(R.string.orbit_gitlab_token_help), "https://gitlab.com/-/user_settings/personal_access_tokens?name=Orion%20Orbit&scopes=read_api", stringResource(R.string.orbit_create_gitlab_token), { gl = it })
            Field("Codeberg", cb, stringResource(R.string.orbit_paste_the_token), stringResource(R.string.orbit_codeberg_token_help), "https://codeberg.org/user/settings/applications", stringResource(R.string.orbit_create_codeberg_token), { cb = it })
            Button({
                vm.set { it.copy(githubToken = gh.trim(), gitlabToken = gl.trim(), codebergToken = cb.trim()) }
                vm.haptic(H.SUCCESS); onDismiss()
            }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_save)) }
        }
    }
}

/** First open of Orbit. It stays until Hide or Keep is tapped, then it does not come back. */
@Composable
private fun OrbitHello(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).widthIn(max = 400.dp), shape = RoundedCornerShape(32.dp), color = cs.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Sym.Orbit, null, Modifier.size(32.dp), tint = cs.onPrimaryContainer)
                }
                Text(stringResource(R.string.orbit_this_is_orbit), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
                Text(stringResource(R.string.orbit_is_your_own_list), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Text(stringResource(R.string.orbit_other_tabs_are_orions), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Text(stringResource(R.string.orbit_only_want_those_hide), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                Button({
                    vm.set { it.copy(seenOrbitHello = true, hiddenTabs = if ("orbit" in it.hiddenTabs) it.hiddenTabs else it.hiddenTabs + "orbit") }
                    vm.navTo(vm.homeTab)
                }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.guide_hide_orbit)) }
                TextButton({ vm.set { it.copy(seenOrbitHello = true) } }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.orbit_keep_orbit)) }
            }
        }
    }
}

/** What a pasted link can be. Forgejo uses the same path as Codeberg, so it is not listed on its own. */
@Composable
private fun OrbitSourcesSheet(onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val rows = OrbitSource.entries.filter { it != OrbitSource.FORGEJO }
    SheetTitle(stringResource(R.string.xtra_sources), Sym.Info, stringResource(R.string.orbit_paste_one_of_these_links))
    Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { i, s ->
            SegmentedListItem(ListItemDefaults.segmentedShapes(i, rows.size), colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                leadingContent = { Icon(sourceIcon(s), null) }, supportingContent = { Text(stringResource(sourceNote(s))) }) { Text(s.label) }
        }
    }
}

@StringRes
private fun sourceNote(s: OrbitSource): Int = when (s) {
    OrbitSource.GITHUB -> R.string.orbit_note_github
    OrbitSource.GITLAB -> R.string.orbit_note_gitlab
    OrbitSource.CODEBERG -> R.string.orbit_note_codeberg
    OrbitSource.FDROID -> R.string.orbit_note_fdroid
    OrbitSource.IZZY -> R.string.orbit_note_izzy
    OrbitSource.UPTODOWN -> R.string.orbit_note_uptodown
    OrbitSource.APKMIRROR -> R.string.orbit_note_apkmirror
    OrbitSource.APKPURE -> R.string.orbit_note_apkpure
    OrbitSource.APTOIDE -> R.string.orbit_note_aptoide
    OrbitSource.SOURCEFORGE -> R.string.orbit_note_sourceforge
    OrbitSource.ITCH -> R.string.orbit_note_itch
    OrbitSource.FARSROID -> R.string.orbit_note_farsroid
    OrbitSource.LITEAPKS -> R.string.orbit_note_liteapks
    OrbitSource.APK4FREE -> R.string.orbit_note_apk4free
    OrbitSource.COOLAPK -> R.string.orbit_note_coolapk
    OrbitSource.SOURCEHUT -> R.string.orbit_note_sourcehut
    OrbitSource.JENKINS -> R.string.orbit_note_jenkins
    OrbitSource.TELEGRAM -> R.string.orbit_note_telegram
    OrbitSource.DISCORD -> R.string.orbit_note_discord
    OrbitSource.APK -> R.string.orbit_note_apk
    OrbitSource.WEB -> R.string.orbit_note_web
    OrbitSource.FORGEJO -> R.string.orbit_note_forgejo
}
@Composable
private fun OrbitBar(sources: List<OrbitSource>, tags: List<String>, pickedSources: Set<String>, pickedTags: Set<String>, updatesOnly: Boolean, updates: Int, onAll: () -> Unit, onToggleUpdates: () -> Unit, onSourcesPicked: (Set<String>) -> Unit, onTagsPicked: (Set<String>) -> Unit, onAdd: () -> Unit, onDiscover: () -> Unit, onImport: () -> Unit, onTokens: () -> Unit, onSources: () -> Unit) {
    val cs = MaterialTheme.colorScheme; val vm = LocalVm.current
    var box by remember { mutableStateOf("") }
    val allOn = pickedSources.isEmpty() && pickedTags.isEmpty() && !updatesOnly
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer) {
            Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
                BarAct(Sym.AddLink, stringResource(R.string.common_add), onAdd)
                VerticalDivider(Modifier.height(28.dp), color = cs.outlineVariant)
                BarAct(Sym.TravelExplore, stringResource(R.string.guide_discover), onDiscover)
                VerticalDivider(Modifier.height(28.dp), color = cs.outlineVariant)
                BarAct(Sym.FileDownload, stringResource(R.string.common_import), onImport)
                VerticalDivider(Modifier.padding(horizontal = 10.dp).height(28.dp), color = cs.outlineVariant)
                Box(Modifier.padding(end = 12.dp).size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).clickable { vm.haptic(H.SELECTION); onTokens() }, contentAlignment = Alignment.Center) {
                    Icon(Sym.Key, stringResource(R.string.orbit_tokens), Modifier.size(20.dp), tint = cs.onSurface)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FitPill(stringResource(R.string.myapps_select_all), allOn) { vm.haptic(H.SELECTION); onAll() }
            if (updates > 0) FitPill(if (updates == 1) stringResource(R.string.orbit_one_up) else stringResource(R.string.orbit_updates_up, updates), updatesOnly) { vm.haptic(H.SELECTION); onToggleUpdates() }
            FitPill(if (pickedSources.isEmpty()) stringResource(R.string.xtra_sources) else stringResource(R.string.orbit_sources_count, pickedSources.size), pickedSources.isNotEmpty(), sources.isNotEmpty()) { vm.haptic(H.SELECTION); box = "src" }
            FitPill(if (pickedTags.isEmpty()) stringResource(R.string.guide_tags) else stringResource(R.string.orbit_tags_count, pickedTags.size), pickedTags.isNotEmpty(), tags.isNotEmpty()) { vm.haptic(H.SELECTION); box = "tag" }
            FilledTonalIconButton({ vm.haptic(H.SELECTION); onSources() }, Modifier.size(40.dp)) { Icon(Sym.Info, stringResource(R.string.orbit_sources_orion_can_read)) }
        }
    }
    if (box == "src") PickBox(stringResource(R.string.xtra_sources), sources.map { it.label to sourceIcon(it) }, pickedSources, { box = "" }, { onSourcesPicked(it); box = "" })
    if (box == "tag") PickBox(stringResource(R.string.guide_tags), tags.map { it to Sym.Sell }, pickedTags, { box = "" }, { onTagsPicked(it); box = "" })
}

/** One equal slice of the filter row, so the row never has to scroll. */
@Composable
private fun RowScope.FitPill(label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(20.dp), color = when {
        !enabled -> cs.surfaceContainer; on -> cs.secondaryContainer; else -> cs.surfaceContainerHigh
    }) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).clickable(enabled) { onClick() }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, color = if (enabled) if (on) cs.onSecondaryContainer else cs.onSurface else cs.onSurface.copy(alpha = .38f))
        }
    }
}

/** Pick any number of sources or tags, then Done. Nothing is applied until then. */
@Composable
private fun PickBox(title: String, options: List<Pair<String, ImageVector>>, picked: Set<String>, onDismiss: () -> Unit, onDone: (Set<String>) -> Unit) {
    val cs = MaterialTheme.colorScheme; val vm = LocalVm.current
    var draft by remember { mutableStateOf(picked) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (label, icon) ->
                    val on = label in draft
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { vm.haptic(H.SELECTION); draft = if (on) draft - label else draft + label }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(22.dp), tint = if (on) cs.primary else cs.onSurfaceVariant)
                        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Checkbox(on, null)
                    }
                }
            }
        },
        confirmButton = { Button({ onDone(draft) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.xtra_done)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun RowScope.BarAct(icon: ImageVector, label: String, onClick: () -> Unit) {
    val vm = LocalVm.current; val cs = MaterialTheme.colorScheme
    Column(Modifier.weight(1f).fillMaxHeight().clickable { vm.haptic(H.SELECTION); onClick() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, label, Modifier.size(20.dp), tint = cs.primary)
        Text(label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurface, maxLines = 1)
    }
}

@Composable
private fun OrbitEmpty() = Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(Sym.Orbit, null, Modifier.size(32.dp), tint = cs.onSecondaryContainer)
    }
    Text(stringResource(R.string.orbit_nothing_in_orbit_yet), Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleLargeEmphasized)
    Text(stringResource(R.string.orbit_empty_body),
        Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** One Orbit app: icon with its install state, name, where it stands, and the single action it needs now. */
@Composable
private fun OrbitCard(vm: MainViewModel, o: OrbitApp, item: AppItem, modifier: Modifier, onOpen: () -> Unit, onMore: () -> Unit) {
    val s = LocalS.current; val cs = MaterialTheme.colorScheme
    val (st, p) = cardState(item)
    val local = s.installedVersions[item.id]
    val src = Orbit.sourceOf(o.url)
    val line = when {
        o.error != null && o.versions.isEmpty() -> o.error to cs.error
        st == CardState.DOWNLOADING -> (if (p > 0f) stringResource(R.string.common_downloading, p.toInt()) else stringResource(R.string.common_starting_download)) to cs.primary
        st == CardState.READY -> stringResource(R.string.logic_ready_to_install) to cs.primary
        st == CardState.UPDATE -> stringResource(R.string.orbit_version_update, local.orEmpty().removePrefix("v"), item.latestVersion.removePrefix("v")) to cs.tertiary
        st == CardState.INSTALLED -> stringResource(R.string.orbit_version_up_to_date, local.orEmpty().removePrefix("v")) to cs.onSurfaceVariant
        o.versions.isEmpty() -> stringResource(R.string.orbit_looking_for_releases) to cs.onSurfaceVariant
        else -> stringResource(R.string.orbit_version_source, item.latestVersion.removePrefix("v"), src.label) to cs.onSurfaceVariant
    }
    Surface(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).combinedClickableOrbit(onOpen, onMore, stringResource(R.string.detail_more_options)), shape = RoundedCornerShape(28.dp), color = cs.surfaceContainer) {
        Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(item, 52.dp, state = true, from = "orbit")
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, Modifier.weight(1f, false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (o.paused) { Spacer(Modifier.width(6.dp)); Icon(Sym.NotificationsOff, stringResource(R.string.orbit_updates_paused), Modifier.size(16.dp), tint = cs.onSurfaceVariant) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(sourceIcon(src), null, Modifier.size(14.dp), tint = cs.onSurfaceVariant); Spacer(Modifier.width(6.dp))
                    Text(line.first, style = MaterialTheme.typography.bodySmall, color = line.second, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (o.tags.isNotEmpty()) Text(o.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val busy = vm.installingId == item.id || vm.scanningId == item.id
            when {
                busy || st == CardState.DOWNLOADING -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    val prog by animateFloatAsState(p / 100f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dl")
                    if (busy || p <= 0f) LoadingIndicator(Modifier.size(36.dp)) else CircularWavyProgressIndicator({ prog }, Modifier.size(36.dp))
                }
                st == CardState.READY -> Button({ vm.download(item) }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text(stringResource(R.string.common_install)) }
                st == CardState.UPDATE -> FilledTonalButton({ vm.haptic(H.MEDIUM); vm.download(item) }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text(stringResource(R.string.common_update)) }
                st == CardState.INSTALLED -> TextButton({ (s.resolvedPackageNames[item.id] ?: item.packageName)?.let { runCatching { vm.tracker.launch(it) } } }) { Text(stringResource(R.string.myapps_open)) }
                o.versions.isEmpty() -> IconButton({ vm.orbitRefresh(setOf(o.id)) }) { Icon(Sym.Refresh, stringResource(R.string.orbit_check_again)) }
                else -> Button({ vm.haptic(H.MEDIUM); vm.download(item) }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text(stringResource(R.string.common_install)) }
            }
        }
    }
}

@Composable
private fun Modifier.combinedClickableOrbit(onClick: () -> Unit, onLong: () -> Unit, moreOptionsLabel: String): Modifier {
    val vm = LocalVm.current
    return this.combinedClickable(onLongClick = { vm.haptic(H.HEAVY); onLong() }, onLongClickLabel = moreOptionsLabel) { vm.haptic(H.SELECTION); onClick() }
}

// region add and edit
/** Add by link, or edit an entry: the source is read as you type, a preview shows what Orion found, and the fine controls
 *  (APK filter, pre-releases, version pattern, icon, screenshots) sit folded under the preview. */
@Composable
fun OrbitAddSheet(vm: MainViewModel, base: OrbitApp?, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val cs = MaterialTheme.colorScheme; val close = LocalSheetClose.current; val clip = LocalClipboardManager.current
    val editing = base != null && LocalD.current.orbit.any { it.id == base.id }
    var url by remember { mutableStateOf(base?.url.orEmpty()) }
    var draft by remember { mutableStateOf(base?.takeIf { it.versions.isNotEmpty() || editing }) }
    var probing by remember { mutableStateOf(false) }
    var fine by remember { mutableStateOf(false) }
    // Typing a link reads it after a pause; changing the filters re-reads the releases with the new rules
    val key = "$url|${draft?.apkFilter}|${draft?.includePre}|${draft?.versionRegex}"
    LaunchedEffect(key) {
        val u = url.trim(); if (!u.startsWith("http")) return@LaunchedEffect
        Orbit.refused(u)?.let { vm.orbitRefuse(it); draft = null; return@LaunchedEffect }
        delay(650)
        probing = true
        val cur = draft
        draft = runCatching { if (cur != null && cur.url == u) withContext(Dispatchers.IO) { Orbit.refresh(cur, vm.s.orbitTokens(), System.currentTimeMillis()) } else vm.orbitProbe(u, base?.takeIf { it.url == u }) }.getOrNull() ?: cur
        draft?.let { o -> Orbit.refused(o.url, o.name, o.packageName.orEmpty(), o.author)?.let { vm.orbitRefuse(it); draft = null } }
        probing = false
    }
    SheetTitle(if (editing) stringResource(R.string.orbit_edit_in_orbit) else stringResource(R.string.orbit_add_to_orbit), Sym.AddLink)
    Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(url, { url = it.trim(); if (draft?.url != url.trim()) draft = null }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp),
            label = { Text(stringResource(R.string.common_link)) }, leadingIcon = { Icon(if (url.startsWith("http")) sourceIcon(Orbit.sourceOf(url)) else Sym.Link, null) },
            trailingIcon = {
                if (url.isEmpty()) IconButton({ clip.getText()?.text?.trim()?.let { url = it } }) { Icon(Sym.ContentCopy, stringResource(R.string.common_paste)) }
                else IconButton({ url = ""; draft = null }) { Icon(Sym.Close, stringResource(R.string.common_clear)) }
            },
            supportingText = { Text(if (url.startsWith("http")) Orbit.sourceOf(url).label else stringResource(R.string.orbit_link_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        AnimatedContent(Triple(probing && draft == null, draft != null, url.startsWith("http")), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "probe") { (loading, found, link) ->
            when {
                loading -> Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(40.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.orbit_reading_the_source), style = MaterialTheme.typography.titleSmall)
                }
                found -> draft?.let { OrbitPreview(it, probing) } ?: Spacer(Modifier)
                link -> Spacer(Modifier.height(4.dp))
                else -> Spacer(Modifier.height(4.dp))
            }
        }
        draft?.let { dr ->
            Surface({ fine = !fine; vm.haptic(H.SELECTION) }, shape = RoundedCornerShape(20.dp), color = if (fine) cs.secondaryContainer else cs.surfaceContainerHigh) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.Tune, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.guide_fine_tune), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    val t by animateFloatAsState(if (fine) 180f else 0f, label = "chev")
                    Icon(Sym.ExpandMore, null, Modifier.graphicsLayer { rotationZ = t })
                }
            }
            AnimatedVisibility(fine, enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrbitField(dr.name, { draft = draft?.copy(name = it) }, stringResource(R.string.common_name))
                    TagEditor(dr.tags) { draft = draft?.copy(tags = it) }
                    OrbitField(dr.author, { draft = draft?.copy(author = it) }, stringResource(R.string.settings_developer))
                    OrbitField(dr.icon, { draft = draft?.copy(icon = it.trim()) }, stringResource(R.string.orbit_icon_link), uri = true)
                    OrbitField(dr.apkFilter, { draft = draft?.copy(apkFilter = it) }, stringResource(R.string.orbit_apk_filter), hint = stringResource(R.string.orbit_apk_filter_hint))
                    OrbitField(dr.versionRegex, { draft = draft?.copy(versionRegex = it) }, stringResource(R.string.orbit_version_pattern), hint = stringResource(R.string.orbit_version_pattern_hint))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).clickable { draft = draft?.copy(includePre = !dr.includePre) }
                        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.orbit_pre_releases), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Switch(dr.includePre, { draft = draft?.copy(includePre = it) })
                    }
                    ShotsEditor(dr.screenshots) { draft = draft?.copy(screenshots = it) }
                }
            }
        }
    }
    val dr = draft
    val joinedMsg = dr?.let { stringResource(R.string.orbit_joined_your_orbit, it.name) }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!editing && dr != null && dr.versions.isNotEmpty()) FilledTonalButton({
            Orbit.refused(dr.url, dr.name, dr.packageName.orEmpty(), dr.author)?.let { vm.orbitRefuse(it) } ?: run { vm.orbitSave(dr); vm.haptic(H.SUCCESS); vm.download(dr.toItem()); close() }
        },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.orbit_add_and_install), maxLines = 1) }
        Button({
            dr?.let { o ->
                val saved = o.copy(id = base?.id?.takeIf { editing } ?: o.id)
                if (!editing) Orbit.refused(saved.url, saved.name, saved.packageName.orEmpty(), saved.author)?.let { vm.orbitRefuse(it); return@Button }
                vm.orbitSave(saved); vm.haptic(H.SUCCESS); if (!editing) joinedMsg?.let { msg -> vm.toast(msg) }
            }; close()
        },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp), enabled = dr != null && !probing, shapes = ButtonDefaults.shapes()) {
            Icon(if (editing) Sym.Check else Sym.Orbit, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (editing) stringResource(R.string.common_save) else stringResource(R.string.orbit_add_to_orbit), maxLines = 1)
        }
    }
}

@Composable
private fun OrbitPreview(o: OrbitApp, refreshing: Boolean) {
    val cs = MaterialTheme.colorScheme; val item = remember(o) { o.toItem() }
    val latest = o.versions.firstOrNull()
    Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteIcon(o.icon, item.name, 56.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (o.author.isNotBlank()) Text(o.author, style = MaterialTheme.typography.bodySmall, color = cs.primary, maxLines = 1)
                }
                if (refreshing) LoadingIndicator(Modifier.size(28.dp))
            }
            if (o.about.isNotBlank()) Text(o.about, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (latest != null) {
                    InsightChip(Sym.NewReleases, stringResource(R.string.orbit_version_only, latest.version.removePrefix("v")))
                    val kind = fileExt(latest.variants.firstOrNull()?.url).uppercase()
                    InsightChip(Sym.Layers, if (latest.variants.size == 1) stringResource(R.string.orbit_one_file_kind, kind) else stringResource(R.string.root_files, latest.variants.size))
                    InsightChip(Sym.History, if (o.versions.size == 1) stringResource(R.string.orbit_one_release) else stringResource(R.string.orbit_releases_count, o.versions.size))
                } else InsightChip(Sym.WarningAmber, o.error ?: stringResource(R.string.orbit_no_apk_found), warn = true)
                o.packageName?.let { InsightChip(Sym.Android, it) }
                if (o.screenshots.isNotEmpty()) InsightChip(Sym.Image, stringResource(R.string.community_screenshots_count, o.screenshots.size))
            }
        }
    }
}

@Composable
private fun TagEditor(tags: List<String>, onChange: (List<String>) -> Unit) {
    val cs = MaterialTheme.colorScheme; var text by remember { mutableStateOf("") }
    fun add() {
        val t = text.trim()
        if (t.isNotEmpty() && t !in tags) onChange(tags + t)
        text = ""
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.guide_tags), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleSmall)
        if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { t -> InputChip(true, { onChange(tags - t) }, { Text(t) }, trailingIcon = { Icon(Sym.Close, stringResource(R.string.community_remove, t), Modifier.size(16.dp)) }) }
        }
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text(stringResource(R.string.orbit_add_a_tag)) },
            trailingIcon = { IconButton(::add, enabled = text.isNotBlank()) { Icon(Sym.Add, stringResource(R.string.orbit_add_tag)) } }, keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }))
    }
}

@Composable
private fun OrbitField(value: String, onChange: (String) -> Unit, label: String, hint: String? = null, uri: Boolean = false) =
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(20.dp), label = { Text(label) },
        supportingText = hint?.let { { Text(it) } }, keyboardOptions = if (uri) KeyboardOptions(keyboardType = KeyboardType.Uri) else KeyboardOptions.Default)

/** Screenshots as a strip of thumbnails: tap the cross to drop one, add more by link. */
@Composable
private fun ShotsEditor(shots: List<String>, onChange: (List<String>) -> Unit) {
    val cs = MaterialTheme.colorScheme; var link by remember { mutableStateOf("") }
    fun add() { val u = link.trim(); if (u.startsWith("http") && explainRejectedImageLink(u) == null) { onChange(shots + sanitizeGitHubImageUrl(u)); link = "" } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.guide_screenshots), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleSmall)
        if (shots.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shots.forEachIndexed { i, u ->
                Box(Modifier.size(72.dp, 128.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHighest)) {
                    AsyncImage(u, stringResource(R.string.community_screenshot_2, i + 1), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    FilledTonalIconButton({ onChange(shots.filterIndexed { j, _ -> j != i }) }, Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)) { Icon(Sym.Close, stringResource(R.string.community_remove_screenshot, i + 1), Modifier.size(14.dp)) }
                }
            }
        }
        OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text(stringResource(R.string.orbit_image_link)) },
            trailingIcon = { IconButton(::add, enabled = link.isNotBlank()) { Icon(Sym.AddPhotoAlternate, stringResource(R.string.community_add_screenshot)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    }
}
// endregion

// region actions
/** Long-press on an Orbit app: everything you can do with it, and sharing it with the Orion library. */
@Composable
private fun OrbitAppSheet(vm: MainViewModel, o: OrbitApp, item: AppItem?, startSharing: Boolean, onEdit: () -> Unit, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val cs = MaterialTheme.colorScheme; val close = LocalSheetClose.current
    var sharing by remember { mutableStateOf(startSharing) }; var removing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RemoteIcon(o.icon, o.name, 56.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(o.name, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val checked = if (o.checkedAt == 0L) stringResource(R.string.orbit_not_checked_yet) else stringResource(R.string.orbit_checked, android.text.format.DateUtils.getRelativeTimeSpanString(o.checkedAt))
            Text(stringResource(R.string.orbit_source_and_checked, Orbit.sourceOf(o.url).label, checked), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
        }
    }
    AnimatedContent(sharing, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "share") { share ->
        if (share) OrbitShare(vm, o) { sharing = false }
        else Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            val rows = 6; var i = 0
            @Composable fun Action(icon: ImageVector, title: String, sub: String?, tint: androidx.compose.ui.graphics.Color = cs.primary, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) =
                SegmentedListItem(onClick, ListItemDefaults.segmentedShapes(i++, rows), colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainerHigh),
                    leadingContent = { IconBox(icon, tint) }, supportingContent = sub?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } }, trailingContent = trailing) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            Action(Sym.OpenInNew, stringResource(R.string.orbit_app_page), null) { item?.let { vm.selectApp(it, "orbit") }; close() }
            Action(Sym.Refresh, stringResource(R.string.orbit_check_for_updates), o.error) { vm.orbitRefresh(setOf(o.id)); vm.haptic(H.LIGHT); close() }
            Action(Sym.Edit, stringResource(R.string.common_edit), stringResource(R.string.orbit_edit_subtitle)) { onEdit() }
            Action(Sym.NotificationsOff, stringResource(R.string.guide_pause_updates), if (o.paused) stringResource(R.string.orbit_updates_are_paused) else null, trailing = { Switch(o.paused, { vm.orbitSave(o.copy(paused = it)) }) }) { vm.orbitSave(o.copy(paused = !o.paused)) }
            Action(Sym.Publish, if (o.published) stringResource(R.string.orbit_shared_with_orion_library) else stringResource(R.string.guide_share_with_the_orion_library), stringResource(R.string.orbit_everyone_gets_it), tint = cs.tertiary) { sharing = true; vm.haptic(H.LIGHT) }
            Action(Sym.Delete, stringResource(R.string.orbit_remove_from_orbit), stringResource(R.string.orbit_app_stays_installed), tint = cs.error) { removing = true }
        }
    }
    if (removing) AlertDialog({ removing = false }, icon = { Icon(Sym.Orbit, null) }, title = { Text(stringResource(R.string.orbit_remove_name_from_orbit, o.name)) },
        text = { Text(stringResource(R.string.orbit_remove_dialog_body)) },
        confirmButton = { Button({ removing = false; vm.orbitRemove(o.id); close() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.xtra_remove)) } },
        dismissButton = { TextButton({ removing = false }) { Text(stringResource(R.string.common_keep)) } })
}

/** Share with the Orion library: what is still missing, why it matters, and the ready submission. */
@Composable
private fun OrbitShare(vm: MainViewModel, o: OrbitApp, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme; val close = LocalSheetClose.current
    val gaps = remember(o) { Orbit.publishGaps(o) }; val wait = vm.submissionCooldown
    val shareLabel = stringResource(R.string.common_share)
    Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(cs.onTertiaryContainer.copy(.12f)), contentAlignment = Alignment.Center) {
                    Icon(Sym.VolunteerActivism, null, Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.orbit_put_in_orbit_for_everyone), style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(stringResource(R.string.orbit_share_body, o.name),
                        Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer.copy(.85f))
                }
            }
        }
        if (gaps.isNotEmpty()) Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Info, null, Modifier.size(20.dp), tint = cs.primary); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.orbit_still_needed, gaps.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onBack, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.xtra_back)) }
            Button({ vm.orbitPublish(o); close() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), enabled = gaps.isEmpty() && wait == null, shapes = ButtonDefaults.shapes()) {
                Text(wait ?: shareLabel, maxLines = 1)
            }
        }
    }
}
// endregion

// region import, export and discover
/** Bring apps in from Obtainium (its export file, a JSON list, obtainium:// links or plain links), or take the whole Orbit out
 *  as an Obtainium export. [preset] arrives from an outside link and is shown ready to add. */
@Composable
private fun OrbitImportSheet(vm: MainViewModel, preset: List<OrbitApp>, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val cs = MaterialTheme.colorScheme; val close = LocalSheetClose.current; val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    val orbit = LocalD.current.orbit
    var text by remember { mutableStateOf("") }; var parsed by remember { mutableStateOf(preset) }; var off by remember { mutableStateOf(emptySet<String>()) }
    val noAppsInFileMsg = stringResource(R.string.orbit_no_apps_found_in_file)
    val exportedMsg = stringResource(R.string.orbit_exported_in_obtainium_format)
    LaunchedEffect(text) { if (text.isNotBlank()) { delay(300); parsed = Orbit.parseImport(text, System.currentTimeMillis()) } }
    SheetTitle(stringResource(R.string.common_import), Sym.FileDownload, stringResource(R.string.orbit_from_obtainium_or_list))
    Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton({
                scope.launch {
                    val u = ResultHost.run(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")).second?.data ?: return@launch
                    val raw = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)!!.use { it.readBytes().decodeToString() } }.getOrNull() } ?: return@launch
                    parsed = Orbit.parseImport(raw, System.currentTimeMillis()); if (parsed.isEmpty()) vm.error(noAppsInFileMsg)
                }
            }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shapes = ButtonDefaults.shapes()) { Icon(Sym.FolderOpen, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.community_obtainium_export), maxLines = 1) }
            FilledTonalButton({ scope.launch { vm.tracker.saveFile(Orbit.export(orbit), "orion_orbit_${System.currentTimeMillis()}.json"); vm.toast(exportedMsg) } },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp), enabled = orbit.isNotEmpty(), shapes = ButtonDefaults.shapes()) { Icon(Sym.FileUpload, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.common_export), maxLines = 1) }
        }
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp), shape = RoundedCornerShape(20.dp), label = { Text(stringResource(R.string.common_paste)) },
            placeholder = { Text(stringResource(R.string.orbit_import_placeholder)) }, maxLines = 8)
        val have = orbit.map { it.id }.toSet()
        if (parsed.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            parsed.forEachIndexed { i, o ->
                val already = o.id in have; val on = !already && o.id !in off
                SegmentedListItem({ if (!already) off = if (o.id in off) off - o.id else off + o.id }, ListItemDefaults.segmentedShapes(i, parsed.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = if (on) cs.secondaryContainer else cs.surfaceContainerHigh),
                    leadingContent = { Icon(sourceIcon(Orbit.sourceOf(o.url)), null) },
                    supportingContent = { Text(if (already) stringResource(R.string.orbit_already_in_orbit) else o.url.removePrefix("https://"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Checkbox(on, null, enabled = !already) }) { Text(o.name.ifBlank { Orbit.sourceOf(o.url).label }, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    val pick = parsed.filter { it.id !in off && it.id !in orbit.map { o -> o.id }.toSet() }
    CtaButton(if (pick.isEmpty()) stringResource(R.string.common_add) else stringResource(R.string.orbit_add_count_to_orbit, pick.size), { vm.orbitImport(pick); close() }, Modifier.navigationBarsPadding().padding(16.dp), Sym.Orbit, enabled = pick.isNotEmpty())
}

/** The Obtainium apps directory, inside Orion: hundreds of ready configs, searchable, one tap to add. */
@Composable
private fun OrbitDiscoverSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val cs = MaterialTheme.colorScheme; val have = LocalD.current.orbit.map { it.id }.toSet()
    var q by remember { mutableStateOf("") }; var page by remember { mutableIntStateOf(1) }
    var list by remember { mutableStateOf<List<DirectoryApp>>(emptyList()) }; var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }; var failed by remember { mutableStateOf(false) }
    LaunchedEffect(q) { delay(if (q.isEmpty()) 0 else 450); page = 1; list = emptyList() }
    LaunchedEffect(q, page) {
        loading = true; failed = false
        runCatching { withContext(Dispatchers.IO) { Orbit.directory(q, page, System.currentTimeMillis()) } }
            .onSuccess { (a, m) -> list = if (page == 1) a else list + a; more = m }.onFailure { failed = true }
        loading = false
    }
    SheetTitle(stringResource(R.string.guide_discover), Sym.TravelExplore, stringResource(R.string.orbit_discover_subtitle))
    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = RoundedCornerShape(28.dp),
        leadingIcon = { Icon(Sym.Search, null) }, placeholder = { Text(stringResource(R.string.orbit_search_apps)) })
    Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        list.forEachIndexed { i, a ->
            val cfg = a.configs.first(); val added = cfg.id in have
            SegmentedListItem({ }, ListItemDefaults.segmentedShapes(i, list.size), colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainerHigh),
                leadingContent = { RemoteIcon(a.icon, a.name, 48.dp) },
                supportingContent = { Text(a.about.ifBlank { a.author }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    if (added) Icon(Sym.CheckCircle, stringResource(R.string.orbit_in_orbit), tint = cs.primary)
                    else FilledTonalIconButton({ vm.haptic(H.SUCCESS); vm.orbitImport(listOf(cfg)) }, shapes = IconButtonDefaults.shapes()) { Icon(Sym.Add, stringResource(R.string.orbit_add_name, a.name)) }
                }) { Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(44.dp)) }
            failed -> EmptyState(Sym.CloudOff, stringResource(R.string.orbit_directory_didnt_answer), stringResource(R.string.orbit_check_connection_try_again))
            list.isEmpty() -> EmptyState(Sym.SearchOff, stringResource(R.string.orbit_no_configs_for, q), stringResource(R.string.orbit_paste_app_link_in_add))
            more -> Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { FilledTonalButton({ page++ }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.orbit_show_more)) } }
        }
    }
}
// endregion
