package com.orion.store.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orion.store.R
import com.orion.store.data.*
import com.orion.store.platform.ScanTarget
import com.orion.store.platform.ScanTargetType
import com.orion.store.platform.VtResult
import com.orion.store.platform.VtScan
import com.orion.store.platform.VtView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

val ty: Typography @Composable @ReadOnlyComposable get() = MaterialTheme.typography
val cs: ColorScheme @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

/** Vertical segmented list corners: 20dp at group ends, 4dp between rows. */
fun colShape(i: Int, n: Int) = RoundedCornerShape(if (i == 0) 20.dp else 4.dp, if (i == 0) 20.dp else 4.dp, if (i == n - 1) 20.dp else 4.dp, if (i == n - 1) 20.dp else 4.dp)

@Composable
fun Eyebrow(text: String, c: Color = cs.primary, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = ty.labelMedium, fontWeight = FontWeight.Bold, color = c, letterSpacing = 1.2.sp)

/** Sheet lead block: optional visual, eyebrow, emphasized headline, supporting body. */
@Composable
fun Lead(title: String, body: String? = null, eyebrow: String? = null, eyebrowColor: Color = cs.primary, center: Boolean = false, top: (@Composable () -> Unit)? = null) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        top?.let { it(); Spacer(Modifier.height(16.dp)) }
        eyebrow?.let { Eyebrow(it, eyebrowColor) }
        Text(title, style = ty.headlineSmallEmphasized, textAlign = align)
        body?.let { Text(it, Modifier.padding(top = 6.dp), style = ty.bodyMedium, color = cs.onSurfaceVariant, textAlign = align) }
    }
}

@Composable
fun PickerHeader(title: String, subtitle: String?, onBack: () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BackHandler(onBack = onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = ty.headlineSmallEmphasized)
            subtitle?.let { Text(it, style = ty.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }

/** Pulsing placeholder that keeps the final card geometry. */
@Composable
fun Skeleton(modifier: Modifier, shape: Shape = RoundedCornerShape(20.dp)) {
    val a by rememberInfiniteTransition(label = "sk").animateFloat(.45f, .9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(shape).graphicsLayer { alpha = a }.background(cs.surfaceContainerHighest))
}

/** Searchable, category-filtered, paged app list shared by the bundle editor and creator. */
@Composable
fun AppPickerList(pool: List<AppItem>, platform: String, modifier: Modifier = Modifier, selected: Set<String> = emptySet(), status: ((Int, Int) -> String)? = null, onPick: (AppItem) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }; var cat by rememberSaveable { mutableStateOf("All") }; var limit by rememberSaveable { mutableIntStateOf(20) }
    val cats = remember(pool) { pool.groupingBy { it.category }.eachCount().toSortedMap() }
    val filtered = remember(pool, q, cat) {
        val t = q.trim()
        pool.filter { (cat == "All" || it.category == cat) && (t.isEmpty() || it.name.contains(t, true) || it.author.contains(t, true) || it.packageName?.contains(t, true) == true) }
    }
    val shown = filtered.take(limit); val left = filtered.size - shown.size
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            OutlinedTextField(q, { q = it; limit = 20 }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.sheets_search_apps, platform)) }, singleLine = true, shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(Sym.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton({ q = ""; limit = 20 }) { Icon(Sym.Close, stringResource(R.string.guide_clear_search)) } })
            Row(Modifier.fillMaxWidth().bleed(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("All" to pool.size) + cats.toList()).forEach { (k, n) ->
                    FilterChip(cat == k, { cat = k; limit = 20 }, { Text(if (k == "All") "${stringResource(R.string.myapps_select_all)} ($n)" else "$k ($n)") }, leadingIcon = if (cat == k) ({ Icon(Sym.Check, null, Modifier.size(18.dp)) }) else null)
                }
            }
            Text(status?.invoke(shown.size, filtered.size) ?: stringResource(R.string.sheets_showing, shown.size, filtered.size), Modifier.padding(start = 4.dp, bottom = 8.dp), style = ty.labelMedium, color = cs.onSurfaceVariant)
        }
        if (filtered.isEmpty()) item { EmptyState(Sym.SearchOff, stringResource(R.string.sheets_no_apps_match)) }
        itemsIndexed(shown, key = { _, a -> a.id }) { i, a ->
            val on = a.id in selected
            GroupItem(i, shown.size, selected = on, onClick = { onPick(a) }) {
                ListItem({ Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = if (on) ty.titleMediumEmphasized else ty.titleMedium) },
                    supportingContent = { Text("${a.category} · ${a.author}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AppIcon(a, 40.dp) },
                    trailingContent = { Icon(if (on) Sym.CheckCircle else Sym.AddCircleOutline, null, tint = if (on) cs.primary else cs.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
        if (left > 0) item {
            FilledTonalButton({ limit += 20 }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.sheets_load_more_left, min(20, left), left)) }
        }
    }
}

// ---------------- Notice ----------------

@Composable
fun NoticeSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val n = vm.config?.notice
    if (n == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    OrionSheet(onDismiss) {
        val close = LocalSheetClose.current
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Lead(n.title, n.message, stringResource(R.string.sheets_notice), center = true) { Blob(Sym.Campaign, cs.primary, 80, MaterialShapes.Sunny) }
            Spacer(Modifier.height(16.dp))
            CtaButton(stringResource(R.string.root_got_it), close, icon = Sym.Check)
        }
    }
}

// ---------------- FAQ ----------------

@Composable
fun FaqSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val faqs = vm.config?.faqs?.takeIf { it.isNotEmpty() } ?: Defaults.FAQS
    var open by rememberSaveable { mutableIntStateOf(-1) }
    SheetTitle(stringResource(R.string.guide_faqs), Sym.HelpOutline, stringResource(R.string.sheets_common_questions_secrets))
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)) {
        itemsIndexed(faqs) { i, f ->
            val on = open == i
            GroupItem(i, faqs.size, selected = on, onClick = { open = if (on) -1 else i; vm.haptic(H.SELECTION) }) {
                Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(if (on) MaterialShapes.Cookie6Sided.toShape() else CircleShape).background(if (on) cs.primary else cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            Icon(faIcon(f.icon, Sym.QuestionMark), null, Modifier.size(20.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(f.question, Modifier.weight(1f), style = if (on) ty.titleSmallEmphasized else ty.titleSmall)
                        Icon(if (on) Sym.ExpandLess else Sym.ExpandMore, null, tint = cs.onSurfaceVariant)
                    }
                    if (on) Text(f.answer, Modifier.padding(start = 54.dp, top = 10.dp), style = ty.bodyMedium)
                }
            }
        }
    }
}

// ---------------- What's new (GitHub Releases) ----------------

/** What's new reads GitHub Releases directly: publishing a release is the only step, no JSON to maintain. Cached for offline and rate limits. */
@Composable
fun ReleaseNotesSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val ctx = LocalContext.current
    var releases by remember { mutableStateOf<List<Release>?>(null) }; var failed by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val f = File(ctx.filesDir, "releases.json")
        withContext(Dispatchers.IO) { runCatching { parseReleases(f.readText()) }.getOrNull() }?.let { releases = it }
        // GitHub allows 60 unauthenticated calls an hour per network, so a fresh cache is used as-is
        if (releases == null || System.currentTimeMillis() - f.lastModified() > 30 * 60_000L) runCatching {
            val raw = Net.text(Defaults.RELEASES_API, mapOf("Accept" to "application/vnd.github+json"))
            releases = parseReleases(raw); withContext(Dispatchers.IO) { f.writeText(raw) }
        }.onFailure { if (releases == null) failed = true }
    }
    SheetTitle(stringResource(R.string.about_what_s_new), Sym.NewReleases, stringResource(R.string.sheets_straight_from_orion_s_releases))
    val list = releases
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            list == null && !failed -> items(2) { Skeleton(Modifier.fillMaxWidth().height(140.dp)) }
            list.isNullOrEmpty() -> item {
                EmptyState(Sym.CloudOff, stringResource(R.string.sheets_can_t_reach_github), stringResource(R.string.sheets_release_notes_load_once_you_re))
                TextButton({ vm.tracker.open("https://github.com/RookieEnough/Orion-Store/releases") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sheets_open_releases_on_github)) }
            }
            else -> itemsIndexed(list, key = { _, r -> r.version }) { i, r ->
                ReleaseCard(vm, r, latest = i == 0, expanded = (open ?: list[0].version) == r.version) { open = if ((open ?: list[0].version) == r.version) "" else r.version }
            }
        }
    }
}

/** Collapsed: version, title, when. Open: section titles with one-line headlines, and a link to the full notes. */
@Composable
private fun ReleaseCard(vm: MainViewModel, r: Release, latest: Boolean, expanded: Boolean, toggle: () -> Unit) {
    val installed = isSameVersion(r.version, STORE_VERSION)
    val fg = if (latest) cs.onPrimaryContainer else cs.onSurface
    Surface(toggle, shape = RoundedCornerShape(if (expanded) 28.dp else 20.dp), color = if (latest) cs.primaryContainer else cs.surfaceContainerHigh, contentColor = fg) {
        Column(Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${r.version}", Modifier.weight(1f), style = ty.titleLargeEmphasized)
                if (installed) Tag(stringResource(R.string.sheets_installed), if (latest) cs.primary else Safe) else if (latest) Tag(stringResource(R.string.sheets_latest), cs.primary)
            }
            if (r.name.isNotEmpty()) Text(r.name, style = ty.titleMedium, fontWeight = FontWeight.SemiBold)
            val count = r.sections.sumOf { it.items.size }
            val whenText = if (r.publishedAt > 0) android.text.format.DateUtils.getRelativeTimeSpanString(r.publishedAt).toString() else ""
            Text(listOf(whenText, if (count > 0) stringResource(R.string.sheets_changes, count) else "").filter { it.isNotEmpty() }.joinToString(" · "), style = ty.labelMedium, color = fg.copy(.7f))
            if (expanded) {
                r.sections.forEach { sec ->
                    if (sec.title.isNotEmpty()) Text(sec.title, Modifier.padding(top = 16.dp, bottom = 4.dp), style = ty.titleSmallEmphasized, color = cs.primary)
                    sec.items.forEach { line ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(fg.copy(.5f)))
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = ty.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (r.url.isNotEmpty()) TextButton({ vm.tracker.open(r.url) }, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.sheets_full_notes_on_github)); Spacer(Modifier.width(6.dp)); Icon(Sym.OpenInNew, null, Modifier.size(16.dp)) }
            }
        }
    }
}

// ---------------- Tutorial (Modern UI intro + feature tour) ----------------

private data class Step(val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int)

private val HERO_SHAPES = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.SoftBurst, MaterialShapes.Cookie12Sided)
private val UI_STEPS = listOf(
    Step(Sym.AutoAwesome, R.string.sheets_try_new_ui, R.string.sheets_orion_has_a_fresh_modern_ui),
    Step(Sym.Settings, R.string.sheets_open_settings, R.string.sheets_tap_the_gear_icon_in_the),
    Step(Sym.Palette, R.string.sheets_go_to_interface, R.string.sheets_find_the_interface_section_in_settings),
    Step(Sym.ToggleOn, R.string.sheets_enable_modern_ui, R.string.sheets_toggle_on_modern_ui_to_unlock),
)
private val TOUR_STEPS = listOf(
    Step(Sym.Terminal, R.string.sheets_developer_menu, R.string.sheets_tap_the_dev_tab_to_see),
    Step(Sym.VolunteerActivism, R.string.about_support_orion, R.string.sheets_we_ve_added_new_ways_to),
    Step(Sym.Favorite, R.string.sheets_thank_you, R.string.sheets_you_can_now_support_orion_via),
)

/** Hero moment: the container morphs to a new MaterialShape and turns a quarter on every step. */
@Composable
private fun StepHero(steps: List<Step>, i: Int, dots: IntRange?) {
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>(); val sf = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val sp = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MorphBlob(HERO_SHAPES, i, 136.dp, cs.primaryContainer) {
            AnimatedContent(i, transitionSpec = { (fadeIn(fx) + scaleIn(sf, .6f)) togetherWith (fadeOut(fx) + scaleOut(sf, .6f)) }, label = "icon") {
                Icon(steps[it].icon, null, Modifier.size(56.dp), tint = cs.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(i, transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (fadeIn(fx) + slideInHorizontally(sp) { it / 4 * dir }) togetherWith (fadeOut(fx) + slideOutHorizontally(sp) { -it / 4 * dir })
        }, label = "text") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(steps[it].title), style = ty.headlineMediumEmphasized, textAlign = TextAlign.Center)
                Text(stringResource(steps[it].body), Modifier.padding(top = 8.dp), style = ty.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        dots?.let { r ->
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.forEach { d ->
                    val w by animateDpAsState(if (d == i) 24.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dw")
                    val col by animateColorAsState(if (d == i) cs.primary else cs.outlineVariant, MaterialTheme.motionScheme.fastEffectsSpec(), label = "dc")
                    Box(Modifier.height(8.dp).width(w).clip(Pill).background(col))
                }
            }
        }
    }
}

@Composable
fun TutorialSheet(vm: MainViewModel, onDismiss: () -> Unit) = if (vm.tour) TourSheet(vm, onDismiss) else ModernUiSheet(vm, onDismiss)

@Composable
private fun ModernUiSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var enable by remember { mutableStateOf(false) }; var settings by remember { mutableStateOf(false) }
    OrionSheet({
        vm.set { it.copy(hasSeenModernUITutorial = true, storeLayout = if (enable) "modern" else it.storeLayout) }
        onDismiss(); if (settings) vm.openSettings()
    }) {
        val close = LocalSheetClose.current
        val now = { enable = true; vm.haptic(H.SUCCESS); close() }
        StepHero(UI_STEPS, step, if (step > 0) 1..3 else null)
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (step) {
                0 -> {
                    CtaButton(stringResource(R.string.sheets_enable_now), now, icon = Sym.Bolt)
                    CtaButton(stringResource(R.string.sheets_how_to_enable), { step = 1; vm.haptic(H.SELECTION) }, icon = Sym.Lightbulb, tonal = true)
                    TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sheets_skip)) }
                }
                UI_STEPS.lastIndex -> {
                    CtaButton(stringResource(R.string.sheets_enable_now), now, icon = Sym.Bolt)
                    CtaButton(stringResource(R.string.sheets_open_settings), { settings = true; close() }, icon = Sym.Settings, tonal = true)
                }
                else -> {
                    CtaButton(stringResource(R.string.sheets_next), { step++; vm.haptic(H.SELECTION) }, icon = Sym.ArrowForward)
                    TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sheets_skip)) }
                }
            }
        }
    }
}

@Composable
private fun TourSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    OrionSheet({ vm.setFlag("tour", "1"); onDismiss() }) {
        val close = LocalSheetClose.current
        StepHero(TOUR_STEPS, step, TOUR_STEPS.indices)
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            CtaButton(if (step == TOUR_STEPS.lastIndex) stringResource(R.string.sheets_got_it) else stringResource(R.string.sheets_next), {
                vm.haptic(H.SELECTION)
                when (step) {
                    0 -> { vm.tab = Tab.ABOUT; step = 1 }
                    1 -> { vm.tab = Tab.ABOUT; vm.donateOpen = true; step = 2 }
                    else -> close()
                }
            }, icon = if (step == TOUR_STEPS.lastIndex) Sym.Check else Sym.ArrowForward)
        }
    }
}

// ---------------- Bundle preview / editor ----------------

@Composable
fun BundlePreviewSheet(vm: MainViewModel, b: BundleItem, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val d = LocalD.current; val close = LocalSheetClose.current
    val removed = s.removedBundleApps[b.id].orEmpty(); val extras = s.extraBundleApps[b.id].orEmpty()
    val all = remember(d.apps, d.importedApps) { (d.apps + d.importedApps).distinctBy { it.id } }
    val byId = remember(all) { all.associateBy { it.id } }
    val apps = (b.apps.filter { it.id !in removed } + extras.mapNotNull { byId[it] }).distinctBy { it.id }
    val present = apps.map { it.id }.toSet()
    val local = b.badge == "Local"; val customized = removed.isNotEmpty() || extras.isNotEmpty()
    val first = remember { apps.firstOrNull()?.id }
    var picking by rememberSaveable { mutableStateOf(false) }; var ask by remember { mutableStateOf<String?>(null) }
    val deleteBundleMsg = stringResource(R.string.sheets_delete_this_bundle)
    ConfirmDialog(ask, stringResource(R.string.sheets_delete), onConfirm = {
        vm.set { st -> st.resetBundle(b.id).copy(customBundles = st.customBundles.filterNot { it.id == b.id }) }; vm.haptic(H.WARNING); close()
    }, onDismiss = { ask = null })
    AnimatedContent(picking, label = "bundle") { pick ->
        if (pick) Column {
            val platform = vm.tab.targetPlatform()
            PickerHeader(stringResource(R.string.sheets_add_to_bundle), b.title) { picking = false }
            AppPickerList(all.filter { it.platform == platform && it.id !in present }, platform) { a ->
                vm.set { it.addAppToBundle(b.id, a.id) }; vm.haptic(H.SUCCESS); picking = false
            }
        } else LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(cs.tertiaryContainer, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) {
                        if (b.icon != null) Icon(faIcon(b.icon), null, Modifier.size(30.dp), tint = cs.onTertiaryContainer)
                        else Text((b.monogram ?: b.title).take(2).uppercase(), style = ty.titleLarge, fontWeight = FontWeight.Black, color = cs.onTertiaryContainer)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Eyebrow(stringResource(R.string.sheets_recommended_bundle))
                        Text(b.title, style = ty.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.sheets_app, apps.size, if (apps.size == 1) "" else "s") + (b.badge?.let { " · $it" } ?: ""), style = ty.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                if (b.description.isNotBlank()) Text(b.description, Modifier.padding(horizontal = 8.dp), style = ty.bodyMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                CtaButton(stringResource(R.string.sheets_download), { vm.bundleDownload(b) }, icon = Sym.Download, enabled = apps.isNotEmpty())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ picking = true }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                        Icon(Sym.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.sheets_add_app))
                    }
                    if (customized) FilledTonalButton({ vm.set { it.resetBundle(b.id) }; vm.haptic(H.MEDIUM) }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                        Icon(Sym.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.sheets_reset))
                    }
                    if (local) OutlinedButton({ ask = deleteBundleMsg }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)) {
                        Icon(Sym.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.sheets_delete))
                    }
                }
            }
            if (apps.isEmpty()) item { EmptyState(Sym.Inventory2, stringResource(R.string.sheets_bundle_is_empty), stringResource(R.string.sheets_add_apps_with_the_button_above)) }
            itemsIndexed(apps, key = { _, a -> a.id }) { i, a ->
                BundleRow(vm, a, colShape(i, apps.size), a.id in extras, !s.installedVersions[a.id].isNullOrEmpty(), a.id == first, onOpen = { vm.selectApp(a) }) {
                    vm.set { st ->
                        var x = st
                        if (a.id in extras) x = x.removeAppFromBundleExtras(b.id, a.id)
                        if (b.apps.any { it.id == a.id }) x = x.toggleBundleAppRemoval(b.id, a.id)
                        x
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleRow(vm: MainViewModel, a: AppItem, shape: Shape, extra: Boolean, installed: Boolean, hint: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    SwipeRow(shape, Modifier.padding(bottom = 2.dp), right = SwipeAction(Sym.Delete, stringResource(R.string.sheets_remove_from_bundle), cs.error, cs.onError, removes = true, run = onRemove), hint = hint) {
        ListItem({ Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) }, Modifier.clickable(onClick = onOpen),
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.category, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (installed) Tag(stringResource(R.string.sheets_installed), Safe)
                    if (extra) Tag(stringResource(R.string.sheets_added), cs.tertiary)
                }
            },
            leadingContent = { AppIcon(a, 44.dp) }, trailingContent = { Icon(Sym.ChevronRight, null) },
            colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh))
    }
}

// ---------------- VirusTotal ----------------

private val MOD_PAGES = listOf(
    Step(Sym.Extension, R.string.sheets_patched_apps_need_context, R.string.sheets_morphe_builds_modify_original_app_code),
    Step(Sym.Warning, R.string.sheets_false_positives_can_happen, R.string.sheets_heuristic_detections_may_react_to_patches),
    Step(Sym.Build, R.string.sheets_best_verification_path, R.string.sheets_if_flags_look_suspicious_patch_the),
)
private val MOD_SHAPES = listOf(MaterialShapes.Cookie4Sided, MaterialShapes.SoftBurst, MaterialShapes.Gem)
private val VT_TIPS = listOf(R.string.sheets_tip_scan_only_apps_you_plan, R.string.sheets_orion_trick_check_the_patches_tag,
    R.string.sheets_fun_fact_1_4_detections_on, R.string.sheets_pro_move_open_the_full_virustotal)
private const val VT_KEY_TUTORIAL = "https://www.youtube.com/shorts/Ynqw4dgH5ko"

private data class VtRisk(@StringRes val labelRes: Int, val color: Color, val icon: ImageVector, @StringRes val note: Int)
private fun risk(flagged: Int) = when {
    flagged == 0 -> VtRisk(R.string.sheets_clean, Safe, Sym.VerifiedUser, R.string.sheets_0_detections_this_is_the_best)
    flagged == 1 -> VtRisk(R.string.sheets_low_signal, Blue, Sym.Info, R.string.sheets_1_detection_often_a_false_positive)
    flagged < 5 -> VtRisk(R.string.sheets_review, Caution, Sym.QueryStats, R.string.sheets_2_4_detections_check_engine_names)
    else -> VtRisk(R.string.sheets_high_caution, Red, Sym.GppBad, R.string.sheets_5_detections_treat_as_risky_until)
}

/** Downloaded APK first, then the installed APK, then a direct remote APK link. Android only. */
fun scanTargetFor(app: AppItem, s: Settings, d: DataState): ScanTarget? {
    if (!app.installsHere()) return null
    (d.readyToInstall[app.id] ?: d.pendingCleanup[app.id]?.fileName)?.let { return ScanTarget(ScanTargetType.DOWNLOADED_APK, "Downloaded APK", it) }
    if (!app.packageName.isNullOrEmpty() && !s.installedVersions[app.id].isNullOrEmpty()) return ScanTarget(ScanTargetType.APK, "Installed APK", app.packageName)
    val variant = (app.availableVersions.firstOrNull()?.variants?.firstOrNull()?.url ?: app.variants.firstOrNull()?.url)?.takeIf { it.isNotEmpty() }
    val url = variant ?: app.downloadUrl
    val direct = variant != null || Regex("\\.(apk|apks|xapk|zip)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)
    return if (url.startsWith("http") && url != "#" && direct) ScanTarget(ScanTargetType.REMOTE_APK, "APK File", url) else null
}

@Composable
fun VirusTotalSheet(vm: MainViewModel, app: AppItem, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val d = LocalD.current; val close = LocalSheetClose.current
    val scope = rememberCoroutineScope(); val t = rememberSheetToast()
    val shaCopiedMsg = stringResource(R.string.sheets_sha_256_copied)
    val vt = remember { VtScan(vm.tracker, app.id, app.name, app.packageName, s.virusTotalApiKey) }
    val target = scanTargetFor(app, s, d)
    val modded = remember { listOf(app.name, app.author, app.id).any { it.contains("revanced", true) || it.contains("morphe", true) } }
    var page by rememberSaveable { mutableIntStateOf(if (modded) 0 else MOD_PAGES.size) }
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    fun revoke() { vm.set { it.copy(virusTotalApiKey = "") }; vt.reset(); vt.view = VtView.KEY; vm.haptic(H.SUCCESS) }
    fun scan() { scope.launch { vt.start(target, s.virusTotalApiKey, vm::hapticStr) } }
    SheetTitle(stringResource(R.string.sheets_virustotal), Sym.Security, app.name)
    SheetToastHost(t)
    AnimatedContent(page.coerceAtMost(MOD_PAGES.size) to vt.view, Modifier.fillMaxWidth(), transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "vt") { (pg, view) ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pg < MOD_PAGES.size) ModWarning(pg) { page++; vm.haptic(H.SELECTION) }
            else when (view) {
                VtView.KEY -> VtKey(vt, s.virusTotalApiKey, ::revoke, { k -> vm.set { it.copy(virusTotalApiKey = k) } }, vm::hapticStr, vm.tracker::open)
                VtView.READY -> VtReady(target, s.virusTotalApiKey, ::scan, ::revoke)
                VtView.SCANNING -> VtScanning(vt)
                VtView.RESULTS -> vt.result?.let { r ->
                    VtResults(r, onCopy = { t.show(shaCopiedMsg) }, onReport = { r.permalink?.let(vm.tracker::open) }, onAgain = { vt.reset(); vt.view = VtView.READY }, onClose = close)
                }
                VtView.ERROR -> {
                    Lead(stringResource(R.string.sheets_needs_attention), vt.error.ifEmpty { stringResource(R.string.sheets_something_went_wrong) }, stringResource(R.string.sheets_scan_stopped), cs.error) { Blob(Sym.ErrorOutline, cs.error, 72, MaterialShapes.Cookie7Sided) }
                    CtaButton(stringResource(R.string.sheets_scan_again), { vt.reset(); vt.view = if (s.virusTotalApiKey.isNotEmpty()) VtView.READY else VtView.KEY }, icon = Sym.Refresh, tonal = true)
                    TextButton(::revoke, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text(stringResource(R.string.sheets_revoke_key)) }
                }
            }
        }
    }
}

/** The VirusTotal flow without a sheet around it, for the APK installer: key, ready, scanning, results and errors. */
@Composable
fun VtPanel(vt: VtScan, target: ScanTarget, key: String, save: (String) -> Unit, revoke: () -> Unit, open: (String) -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    fun scan() { scope.launch { vt.start(target, key, {}) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (vt.view) {
            VtView.KEY -> VtKey(vt, key, revoke, save, {}, open)
            VtView.READY -> VtReady(target, key, ::scan, revoke)
            VtView.SCANNING -> VtScanning(vt)
            VtView.RESULTS -> vt.result?.let { r -> VtResults(r, onCopy = {}, onReport = { r.permalink?.let(open) }, onAgain = { vt.reset(); vt.view = VtView.READY }, onClose = onDone) }
            VtView.ERROR -> {
                Lead(stringResource(R.string.sheets_needs_attention), vt.error.ifEmpty { stringResource(R.string.sheets_something_went_wrong) }, stringResource(R.string.sheets_scan_stopped), cs.error) { Blob(Sym.ErrorOutline, cs.error, 72, MaterialShapes.Cookie7Sided) }
                CtaButton(stringResource(R.string.sheets_scan_again), { vt.reset(); vt.view = if (key.isNotEmpty()) VtView.READY else VtView.KEY }, icon = Sym.Refresh, tonal = true)
            }
        }
    }
}

@Composable
private fun ModWarning(page: Int, onNext: () -> Unit) {
    var left by remember { mutableIntStateOf(10) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
    val p = MOD_PAGES[page]
    Lead(stringResource(p.title), stringResource(p.body), stringResource(R.string.sheets_notice_of, page + 1, MOD_PAGES.size), Caution, center = true) { Blob(p.icon, Caution, 88, MOD_SHAPES[page]) }
    LinearWavyProgressIndicator({ (10 - left) / 10f }, Modifier.fillMaxWidth(), color = Caution, trackColor = Caution.copy(.2f))
    CtaButton(if (left > 0) stringResource(R.string.sheets_continue_in_s, left) else if (page < MOD_PAGES.lastIndex) stringResource(R.string.sheets_continue) else stringResource(R.string.sheets_i_understand), onNext, enabled = left == 0)
}

@Composable
private fun VtKey(vt: VtScan, saved: String, revoke: () -> Unit, save: (String) -> Unit, haptic: (String) -> Unit, open: (String) -> Unit) {
    var key by rememberSaveable { mutableStateOf(saved) }; var show by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }
    val pasteFirstMsg = stringResource(R.string.sheets_paste_your_virustotal_api_key_first)
    Lead(stringResource(R.string.sheets_connect_virustotal), stringResource(R.string.sheets_your_key_stays_on_this_device), stringResource(R.string.sheets_byok_setup))
    OutlinedTextField(key, { key = it; err = "" }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sheets_api_key)) }, placeholder = { Text(stringResource(R.string.sheets_paste_your_virustotal_api_key)) },
        singleLine = true, isError = err.isNotEmpty(), supportingText = if (err.isNotEmpty()) ({ Text(err) }) else null, shape = RoundedCornerShape(16.dp),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        leadingIcon = { Icon(Sym.Key, null) },
        trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Sym.VisibilityOff else Sym.Visibility, if (show) stringResource(R.string.sheets_hide_key) else stringResource(R.string.sheets_show_key)) } })
    CtaButton(stringResource(R.string.sheets_save_key), {
        if (key.isBlank()) { err = pasteFirstMsg; haptic("error") }
        else { save(key.trim()); vt.view = VtView.READY; haptic("success") }
    }, icon = Sym.Save)
    if (saved.isNotEmpty()) TextButton(revoke, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text(stringResource(R.string.sheets_remove_saved_key)) }
    Column {
        GroupItem(0, 2, onClick = { open(VT_KEY_TUTORIAL) }) {
            ListItem({ Text(stringResource(R.string.sheets_watch_key_tutorial), fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(stringResource(R.string.sheets_quick_help_for_finding_your_virustotal)) },
                leadingContent = { Blob(Sym.PlayCircle, Red, 40) }, trailingContent = { Icon(Sym.OpenInNew, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        GroupItem(1, 2) {
            ListItem({ Text(stringResource(R.string.sheets_quota_tip), fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(stringResource(R.string.sheets_free_virustotal_keys_are_commonly_limited)) },
                leadingContent = { Blob(Sym.Speed, Caution, 40) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
    }
}

@Composable
private fun VtReady(target: ScanTarget?, key: String, scan: () -> Unit, revoke: () -> Unit) {
    val masked = if (key.length > 8) key.take(4) + "••••" + key.takeLast(4) else "••••"
    Lead(stringResource(R.string.detail_scan_with_virustotal), stringResource(R.string.sheets_hash_lookup_first_upload_only_happens), stringResource(R.string.sheets_ready))
    Column {
        GroupItem(0, 2) {
            ListItem({ Text(target?.let { when (it.label) { "Downloaded APK" -> stringResource(R.string.sheets_downloaded_apk); "Installed APK" -> stringResource(R.string.sheets_installed_apk); "APK File" -> stringResource(R.string.sheets_apk_file); else -> it.label } } ?: stringResource(R.string.sheets_no_scan_target_found), fontWeight = FontWeight.SemiBold) },
                supportingContent = target?.let { { Text(it.detail, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                leadingContent = { Blob(if (target == null) Sym.Block else Sym.Android, if (target == null) cs.error else Safe, 40) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        GroupItem(1, 2) {
            ListItem({ Text(masked, fontFamily = FontFamily.Monospace) }, supportingContent = { Text(stringResource(R.string.sheets_limit_tip)) }, leadingContent = { Blob(Sym.Key, cs.primary, 40) },
                trailingContent = { Tag(stringResource(R.string.sheets_use_sparingly), Caution) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
    }
    CtaButton(stringResource(R.string.sheets_start_virustotal_scan), scan, icon = Sym.Radar, enabled = target != null)
    TextButton(revoke, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text(stringResource(R.string.sheets_revoke_key)) }
    Text(stringResource(R.string.sheets_tip_one_scan_may_need_report), style = ty.bodySmall, color = cs.onSurfaceVariant)
}

@Composable
private fun VtScanning(vt: VtScan) {
    var tip by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2600); tip = (tip + 1) % VT_TIPS.size } }
    val p by animateFloatAsState(vt.progress, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "vtp")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LoadingIndicator(Modifier.size(112.dp))
        Text(stringResource(R.string.sheets_scanning_softly), style = ty.titleLargeEmphasized)
        Text(vt.note, style = ty.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth())
        Surface(shape = RoundedCornerShape(16.dp), color = cs.secondaryContainer) {
            AnimatedContent(tip, Modifier.fillMaxWidth().padding(14.dp), label = "tip") { Text(stringResource(VT_TIPS[it]), style = ty.bodyMedium, textAlign = TextAlign.Center) }
        }
        Text(stringResource(R.string.sheets_keep_this_open_virustotal_can_take), style = ty.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VtResults(r: VtResult, onCopy: () -> Unit, onReport: () -> Unit, onAgain: () -> Unit, onClose: () -> Unit) {
    val clip = LocalClipboardManager.current
    val st = r.stats; val total = st.total; val rate = if (total > 0) (st.flagged * 100f / total).roundToInt() else 0
    val k = risk(st.flagged)
    val detections = r.engines.filter { it.category == "malicious" || it.category == "suspicious" }.take(8)
    Lead(stringResource(k.labelRes), stringResource(k.note), stringResource(R.string.sheets_virustotal_result), k.color, center = true) { Blob(k.icon, k.color, 88, MaterialShapes.Cookie9Sided) }
    Surface(shape = RoundedCornerShape(28.dp), color = k.color.copy(.12f)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.sheets_harmful_rate), style = ty.labelLarge, color = cs.onSurfaceVariant)
            Text("$rate%", style = ty.displayMediumEmphasized, color = k.color)
            LinearProgressIndicator({ rate / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp), color = k.color, trackColor = k.color.copy(.2f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(Triple(R.string.sheets_flagged, st.flagged, Red), Triple(R.string.sheets_clean, st.harmless, Safe), Triple(R.string.sheets_engines, total, cs.onSurface)).forEachIndexed { i, (l, v, col) ->
            Surface(Modifier.weight(1f), shape = rowShape(i, 3), color = cs.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$v", style = ty.titleLargeEmphasized, color = col)
                    Text(stringResource(l).uppercase(), style = ty.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow(stringResource(R.string.sheets_how_to_read_it), cs.onSurfaceVariant)
            Text(stringResource(R.string.sheets_0_detections_generally_safe), style = ty.bodyMedium)
            Text(stringResource(R.string.sheets_1_4_detections_often_false_positives), style = ty.bodyMedium)
            Text(stringResource(R.string.sheets_5_detections_high_caution), style = ty.bodyMedium)
        }
    }
    if (detections.isNotEmpty()) Column {
        Eyebrow(stringResource(R.string.sheets_flagging_engines), cs.onSurfaceVariant, Modifier.padding(start = 4.dp, bottom = 8.dp))
        detections.forEachIndexed { i, e ->
            GroupItem(i, detections.size) {
                ListItem({ Text(e.engine, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(e.result ?: e.category, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Tag(e.category, if (e.category == "malicious") Red else Caution) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
    if (r.hash != null) CtaButton(stringResource(R.string.sheets_copy_sha_256), { clip.setText(AnnotatedString(r.hash)); onCopy() }, icon = Sym.ContentCopy, tonal = true)
    if (r.permalink != null) CtaButton(stringResource(R.string.sheets_open_virustotal_report), onReport, icon = Sym.OpenInNew)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onAgain, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.settings_scan_again)) }
        TextButton(onClose, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.xtra_close)) }
    }
}

// ---------------- App change request ----------------

/** Display resources for a [ChangeField] (the type and its English strings live in Logic.kt; 0 = unknown key, fall back to the English field). */
@StringRes
fun changeFieldLabelRes(key: String): Int = when (key) {
    "name" -> R.string.field_name_label; "author" -> R.string.field_author_label; "description" -> R.string.field_description_label
    "icon" -> R.string.field_icon_label; "screenshots" -> R.string.field_screenshots_label; "downloadUrl" -> R.string.field_downloadurl_label
    "repoUrl" -> R.string.field_repourl_label; "githubRepo" -> R.string.field_githubrepo_label; "gitlabRepo" -> R.string.field_gitlabrepo_label
    "codebergRepo" -> R.string.field_codebergrepo_label; "officialSite" -> R.string.field_officialsite_label; "category" -> R.string.field_category_label
    "releaseKeyword" -> R.string.field_releasekeyword_label; "packageName" -> R.string.field_packagename_label; else -> 0
}

@StringRes
fun changeFieldHintRes(key: String): Int = when (key) {
    "name" -> R.string.field_name_hint; "author" -> R.string.field_author_hint; "description" -> R.string.field_description_hint
    "icon" -> R.string.field_icon_hint; "screenshots" -> R.string.field_screenshots_hint; "downloadUrl" -> R.string.field_downloadurl_hint
    "repoUrl" -> R.string.field_repourl_hint; "githubRepo" -> R.string.field_githubrepo_hint; "gitlabRepo" -> R.string.field_gitlabrepo_hint
    "codebergRepo" -> R.string.field_codebergrepo_hint; "officialSite" -> R.string.field_officialsite_hint; "category" -> R.string.field_category_hint
    "releaseKeyword" -> R.string.field_releasekeyword_hint; "packageName" -> R.string.field_packagename_hint; else -> 0
}

@StringRes
fun changeFieldGroupRes(group: String): Int = when (group) {
    "Basics" -> R.string.field_group_basics; "Media" -> R.string.field_group_media; "Source" -> R.string.field_group_source
    "Catalog" -> R.string.field_group_catalog; "Android" -> R.string.field_group_android; else -> 0
}

/** The label written into the generated GitHub issue. Issue payloads must stay English exactly as before,
 *  so this always returns the field's original English label, never the localized display string. */
fun englishFieldLabel(f: ChangeField): String = f.label

@Composable
fun ChangeRequestSheet(vm: MainViewModel, app: AppItem, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val close = LocalSheetClose.current
    val ctx = LocalContext.current
    val fields = remember(app.platform) { changeFields(app.platform) }
    var key by rememberSaveable { mutableStateOf("name") }
    val f = fields.firstOrNull { it.key == key } ?: fields.first()
    var value by rememberSaveable { mutableStateOf("") }; var reason by rememberSaveable { mutableStateOf("") }; var ref by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable { mutableStateOf("form") }; var issueUrl by rememberSaveable { mutableStateOf("") }
    var err by remember { mutableStateOf("") }; var showCurrent by remember { mutableStateOf(false) }
    val current = remember(key) { currentFieldValue(ctx, app, key) }
    fun submit() {
        vm.refreshCooldown()
        vm.submissionCooldown?.let { err = ctx.getString(R.string.sheets_next_request_available_in, it); vm.haptic(H.WARNING); return }
        validateChange(ctx, f, value).takeIf { it.isNotEmpty() }?.let { err = it; vm.haptic(H.ERROR); return }
        if (ref.isNotBlank()) urlError(ctx, ref).takeIf { it.isNotEmpty() }?.let { err = ctx.getString(R.string.sheets_evidence_link, it); vm.haptic(H.ERROR); return }
        val v: Any = if (f.kind == "urls") splitUrls(value) else value.trim()
        issueUrl = appUpdateIssueUrl(app, englishFieldLabel(f), current, value, reason, ref, appUpdatePayloadJson(app, f.key, v))
        err = ""; view = "ready"; vm.haptic(H.SUCCESS)
    }
    if (view != "pick") SheetTitle(stringResource(R.string.sheets_app_update, app.platform), Sym.EditNote, app.name)
    AnimatedContent(view, label = "cr") { v ->
        when (v) {
            "pick" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                PickerHeader(stringResource(R.string.sheets_choose_one_field), stringResource(R.string.sheets_correct_an_app_detail)) { view = "form" }
                fields.groupBy { it.group }.forEach { (g, list) ->
                    Eyebrow(if (changeFieldGroupRes(g) != 0) stringResource(changeFieldGroupRes(g)) else g, modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 8.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        list.forEachIndexed { i, x ->
                            GroupItem(i, list.size, selected = x.key == key, onClick = { if (x.key != key) { key = x.key; value = "" }; err = ""; view = "form"; vm.haptic(H.SELECTION) }) {
                                ListItem({ Text(if (changeFieldLabelRes(x.key) != 0) stringResource(changeFieldLabelRes(x.key)) else x.label, style = if (x.key == key) ty.titleMediumEmphasized else ty.titleMedium) }, supportingContent = { Text(if (changeFieldHintRes(x.key) != 0) stringResource(changeFieldHintRes(x.key)) else x.hint) },
                                    trailingContent = { if (x.key == key) Icon(Sym.CheckCircle, null, tint = cs.primary) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                            }
                        }
                    }
                }
            }
            "ready" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Lead(stringResource(R.string.sheets_update_issue_ready), stringResource(R.string.sheets_the_issue_contains_the_exact_json), if (changeFieldLabelRes(f.key) != 0) stringResource(changeFieldLabelRes(f.key)) else f.label, center = true) {
                    Blob(Sym.TaskAlt, Safe, 88, MaterialShapes.Cookie9Sided)
                }
                CtaButton(stringResource(R.string.community_open_github_issue), {
                    vm.tracker.open(issueUrl); vm.set { it.registerChangeRequest(System.currentTimeMillis()) }; vm.haptic(H.SUCCESS); close()
                }, icon = Sym.OpenInNew)
                CtaButton(stringResource(R.string.sheets_edit_request), { view = "form" }, icon = Sym.Edit, tonal = true)
            }
            else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Eyebrow(stringResource(R.string.sheets_what_to_correct), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    GroupItem(0, 2, onClick = { view = "pick" }) {
                        ListItem({ Text(if (changeFieldLabelRes(f.key) != 0) stringResource(changeFieldLabelRes(f.key)) else f.label, style = ty.titleMediumEmphasized) }, supportingContent = { Text(if (changeFieldHintRes(f.key) != 0) stringResource(changeFieldHintRes(f.key)) else f.hint) }, trailingContent = { Icon(Sym.UnfoldMore, stringResource(R.string.sheets_change_field)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                    GroupItem(1, 2, onClick = { showCurrent = !showCurrent }) {
                        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.sheets_current_value), Modifier.weight(1f), style = ty.labelLarge, color = cs.onSurfaceVariant)
                                Icon(if (showCurrent) Sym.ExpandLess else Sym.ExpandMore, null, tint = cs.onSurfaceVariant)
                            }
                            if (showCurrent) Text(current.take(1000), Modifier.padding(top = 6.dp), style = ty.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                val multi = f.kind == "textarea" || f.kind == "urls"; val urlKind = f.kind == "url" || f.kind == "urls"
                OutlinedTextField(value, { value = it.take(4000); err = "" }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sheets_correct_value)) }, shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(if (f.kind == "urls") "https://image.example/one.png\nhttps://image.example/two.png" else if (changeFieldHintRes(f.key) != 0) stringResource(changeFieldHintRes(f.key)) else f.hint) },
                    singleLine = !multi, minLines = if (multi) 4 else 1, keyboardOptions = KeyboardOptions(keyboardType = if (urlKind) KeyboardType.Uri else KeyboardType.Text))
                OutlinedTextField(reason, { reason = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sheets_reason)) }, placeholder = { Text(stringResource(R.string.sheets_why_is_this_correction_needed)) },
                    minLines = 3, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(ref, { ref = it; err = "" }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sheets_evidence_link_optional)) }, placeholder = { Text(stringResource(R.string.sheets_release_page_or_official_source)) },
                    singleLine = true, shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Sym.Link, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                AnimatedVisibility(err.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = cs.errorContainer, contentColor = cs.onErrorContainer) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Sym.ErrorOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(err, style = ty.bodyMedium)
                        }
                    }
                }
                CtaButton(stringResource(R.string.sheets_prepare_app_update_issue), ::submit, icon = Sym.Description)
                Text(stringResource(R.string.sheets_50_xp_toward_leaderboard), Modifier.fillMaxWidth(), style = ty.labelLarge, color = cs.tertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

// ---------------- 2.0 welcome ----------------

private val WELCOME_POINTS = listOf(
    Triple(Sym.Speed, R.string.sheets_native_speed, R.string.sheets_built_with_jetpack_compose_no_web),
    Triple(Sym.Palette, R.string.sheets_material_3_expressive, R.string.sheets_springy_motion_shape_morphs_and_material),
    Triple(Sym.Bolt, R.string.guide_skip_the_24_hour_wait, R.string.sheets_built_in_wireless_install_set_up),
    Triple(Sym.MilitaryTech, R.string.sheets_levels_and_pilot_cards, R.string.sheets_earn_xp_and_climb_bronze_silver),
)

/** One-time hero for the native switch, shown after updating over the web app and on a fresh install alike. */
@Composable
fun WelcomeSheet(vm: MainViewModel) = OrionSheet(vm::finishWelcome) {
    val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography; val close = LocalSheetClose.current
    val still = LocalS.current.calm
    var k by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) { delay(250); k = 1; while (!still) { delay(2200); k++ } }
    val pulse by rememberInfiniteTransition(label = "re").animateFloat(1f, if (still) 1f else 1.08f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomCenter) {
            MorphBlob(listOf(MaterialShapes.Circle, MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst, MaterialShapes.Flower), k, 168.dp, cs.primaryContainer) {
                Icon(androidx.compose.ui.res.painterResource(com.orion.store.R.drawable.ic_splash_logo), null, Modifier.size(84.dp), tint = cs.onPrimaryContainer)
            }
            Surface(Modifier.offset(y = 10.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }, shape = Pill, color = cs.tertiary, contentColor = cs.onTertiary) {
                Text("RE", Modifier.padding(horizontal = 18.dp, vertical = 4.dp), style = ty.labelLargeEmphasized, letterSpacing = 3.sp)
            }
        }
        Text(stringResource(R.string.sheets_orion_store_2_0_native), Modifier.padding(top = 28.dp).reveal(0), style = ty.labelLarge, color = cs.primary)
        Text(stringResource(R.string.sheets_native_at_last), Modifier.reveal(1), style = ty.displaySmallEmphasized, textAlign = TextAlign.Center)
        Text(stringResource(R.string.sheets_the_web_app_you_knew_has),
            Modifier.padding(top = 8.dp).reveal(2), style = ty.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Column(Modifier.padding(top = 20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            WELCOME_POINTS.forEachIndexed { i, (icon, title, desc) ->
                ListItem({ Text(stringResource(title), style = ty.titleMedium) }, Modifier.reveal(3 + i).clip(colShape(i, WELCOME_POINTS.size)),
                    supportingContent = { Text(stringResource(desc)) }, leadingContent = { TonalIcon(icon, cs.primary) },
                    colors = ListItemDefaults.colors(containerColor = cs.surfaceContainer))
            }
        }
        AnimatedVisibility(vm.migratedFromWeb) {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.tertiaryContainer).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.CheckCircle, null, tint = cs.onTertiaryContainer); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.sheets_your_profile_badges_and_settings_came), style = ty.bodyMedium, color = cs.onTertiaryContainer)
            }
        }
        CtaButton(stringResource(R.string.sheets_let_s_go), close, Modifier.padding(top = 20.dp).reveal(8), icon = Sym.RocketLaunch)
    }
}

/** Every catalog app by one developer: a shape avatar, the name, the count, then the apps. */
@Composable
fun DevSheet(vm: MainViewModel, dev: String, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val d = LocalD.current; val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography
    val name = dev.trim()
    val apps = remember(d.apps, d.importedApps, name) {
        (d.apps + d.importedApps).filter { it.author.trim().equals(name, true) }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }
    val letter = name.takeIf { it.isNotEmpty() }?.let { String(Character.toChars(it.codePointAt(0))) }.orEmpty().uppercase().ifBlank { "?" }
    val cap = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = cap), contentPadding = PaddingValues(bottom = 32.dp)) {
        item("dev") {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(letter, style = ty.displaySmallEmphasized, color = cs.onPrimaryContainer)
                }
                Text(dev, Modifier.padding(top = 14.dp, start = 24.dp, end = 24.dp), style = ty.headlineSmallEmphasized, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.sheets_app, apps.size, if (apps.size == 1) "" else "s"), style = ty.labelLarge, color = cs.onSurfaceVariant)
            }
        }
        itemsIndexed(apps, key = { _, a -> a.id }) { i, a -> AppRow(vm, a, i, apps.size, Modifier.animateItem()) }
    }
}

/** Display resources for an install option (the [InstallOption] type and its English strings live in Logic.kt; 0 = unknown id, fall back to the English option). */
@StringRes
fun installOptionLabelRes(id: String): Int = when (id) {
    "downgrade" -> R.string.instopt_downgrade_label; "grant" -> R.string.instopt_grant_label; "restricted" -> R.string.instopt_restricted_label
    "test" -> R.string.instopt_test_label; "lowsdk" -> R.string.instopt_lowsdk_label; "forcesdk" -> R.string.instopt_forcesdk_label
    "dontkill" -> R.string.instopt_dontkill_label; "currentuser" -> R.string.instopt_currentuser_label; "internal" -> R.string.instopt_internal_label
    "external" -> R.string.instopt_external_label; "rollback" -> R.string.instopt_rollback_label; "noverify" -> R.string.instopt_noverify_label
    "queryable" -> R.string.instopt_queryable_label; "nodexopt" -> R.string.instopt_nodexopt_label; "instant" -> R.string.instopt_instant_label
    "full" -> R.string.instopt_full_label; "preload" -> R.string.instopt_preload_label; "play" -> R.string.instopt_play_label
    "installer" -> R.string.instopt_installer_label; else -> 0
}

@StringRes
fun installOptionDescRes(id: String): Int = when (id) {
    "downgrade" -> R.string.instopt_downgrade_desc; "grant" -> R.string.instopt_grant_desc; "restricted" -> R.string.instopt_restricted_desc
    "test" -> R.string.instopt_test_desc; "lowsdk" -> R.string.instopt_lowsdk_desc; "forcesdk" -> R.string.instopt_forcesdk_desc
    "dontkill" -> R.string.instopt_dontkill_desc; "currentuser" -> R.string.instopt_currentuser_desc; "internal" -> R.string.instopt_internal_desc
    "external" -> R.string.instopt_external_desc; "rollback" -> R.string.instopt_rollback_desc; "noverify" -> R.string.instopt_noverify_desc
    "queryable" -> R.string.instopt_queryable_desc; "nodexopt" -> R.string.instopt_nodexopt_desc; "instant" -> R.string.instopt_instant_desc
    "full" -> R.string.instopt_full_desc; "preload" -> R.string.instopt_preload_desc; "play" -> R.string.instopt_play_desc
    "installer" -> R.string.instopt_installer_desc; else -> 0
}

/** The pm install switches this Android version supports, shared by the store's options sheet and the APK installer sheet.
 *  [suggested] ones carry a "Needed" tag: the APK was read and won't install without them. */
@Composable
fun InstallOptionsList(on: Set<String>, installer: String, onChange: (Set<String>) -> Unit, onInstaller: (String) -> Unit, suggested: Set<String> = emptySet(), haptic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shown = remember { INSTALL_OPTIONS.filter { android.os.Build.VERSION.SDK_INT >= it.minSdk } }
    shown.forEachIndexed { i, o ->
        val c = o.id in on
        fun flip() { haptic(); onChange(if (c) on - o.id else on + o.id - setOfNotNull(o.excludes)) }
        SegmentedListItem(::flip, ListItemDefaults.segmentedShapes(i, shown.size),
            colors = ListItemDefaults.segmentedColors(containerColor = if (c) cs.secondaryContainer else cs.surfaceContainerHigh),
            supportingContent = { Text(if (installOptionDescRes(o.id) != 0) stringResource(installOptionDescRes(o.id)) else o.desc) },
            trailingContent = { Switch(c, { flip() }, thumbContent = { Icon(if (c) Sym.Check else Sym.Close, null, Modifier.size(SwitchDefaults.IconSize)) }) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (installOptionLabelRes(o.id) != 0) stringResource(installOptionLabelRes(o.id)) else o.label, Modifier.weight(1f, false), style = MaterialTheme.typography.titleMedium)
                if (o.id in suggested) Text(stringResource(R.string.sheets_needed), Modifier.padding(start = 8.dp).clip(Pill).background(cs.tertiaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = cs.onTertiaryContainer)
            }
        }
        if (o.id == "installer" && c) OutlinedTextField(installer, { onInstaller(it.trim()) }, Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true,
            label = { Text(stringResource(R.string.sheets_installer_package)) }, placeholder = { Text("com.sec.android.app.samsungapps") }, shape = RoundedCornerShape(16.dp),
            isError = installer.isNotEmpty() && installArgs(listOf("installer", "installer=$installer"), android.os.Build.VERSION.SDK_INT, "").contains(" -i ").not(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    }
}

/** The picks as saved and passed to pm: switch ids, plus "installer=<package>" when a custom installer is on. */
fun pickedOptions(on: Set<String>, installer: String) =
    on.filter { id -> INSTALL_OPTIONS.any { it.id == id && android.os.Build.VERSION.SDK_INT >= it.minSdk } } + listOfNotNull(installer.takeIf { "installer" in on && it.isNotEmpty() }?.let { "installer=$it" })

/** Beyond install: pm install switches for one APK, last picks preselected. Installs through root or Shizuku. */
@Composable
fun InstallOptionsSheet(vm: MainViewModel, app: AppItem, file: String, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val close = LocalSheetClose.current; val cs = MaterialTheme.colorScheme
    var on by remember { mutableStateOf(s.installFlags.filterNot { it.startsWith("installer=") }.toSet()) }
    var installer by remember { mutableStateOf(s.installFlags.firstOrNull { it.startsWith("installer=") }?.substringAfter('=').orEmpty()) }
    SheetTitle(stringResource(R.string.guide_beyond_install), Sym.Tune, app.name)
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        InstallOptionsList(on, installer, { on = it }, { installer = it }) { vm.haptic(H.SELECTION) }
        Text(if (s.rootMode) stringResource(R.string.sheets_installs_with_root) else stringResource(R.string.sheets_installs_through_shizuku), Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Button({
            val picked = pickedOptions(on, installer)
            vm.set { it.copy(installFlags = picked) }
            vm.installFile(app, file, opts = picked); close()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight)) {
            Icon(Sym.InstallMobile, null); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.sheets_install), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
    }
}
