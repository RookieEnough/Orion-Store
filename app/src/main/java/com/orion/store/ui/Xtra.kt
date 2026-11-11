package com.orion.store.ui

import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import android.Manifest
import android.content.Context
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.orion.store.R
import com.orion.store.data.Net
import com.orion.store.data.json
import com.orion.store.platform.AppTracker
import com.orion.store.platform.NetSecurity
import com.orion.store.platform.RiskyApp
import com.orion.store.platform.SysApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

val Red = Color(0xFFEF4444); val Blue = Color(0xFF3B82F6); val Gray = Color(0xFF6B7280)

/** In-sheet toast: sheets are dialog windows, so vm.toast would render underneath them. */
class SheetToast { var msg by mutableStateOf<String?>(null); var error by mutableStateOf(false); fun show(m: String, e: Boolean = false) { msg = m; error = e } }

@Composable fun rememberSheetToast() = remember { SheetToast() }

@Composable
fun SheetToastHost(t: SheetToast) {
    var last by remember { mutableStateOf("") }; t.msg?.let { last = it }
    LaunchedEffect(t.msg) { if (t.msg != null) { delay(3000); t.msg = null } }
    AnimatedVisibility(t.msg != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val c = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp), color = if (t.error) c.errorContainer else c.inverseSurface) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (t.error) Sym.ErrorOutline else Sym.CheckCircle, null, Modifier.size(20.dp), tint = if (t.error) c.onErrorContainer else c.inverseOnSurface)
                Spacer(Modifier.width(10.dp))
                Text(last, style = MaterialTheme.typography.labelLarge, color = if (t.error) c.onErrorContainer else c.inverseOnSurface)
            }
        }
    }
}

@Composable
fun ConfirmDialog(msg: String?, confirm: String? = null, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (msg != null) AlertDialog(onDismiss, confirmButton = { TextButton({ onConfirm(); onDismiss() }) { Text(confirm ?: stringResource(R.string.common_confirm)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } }, icon = { Icon(Sym.WarningAmber, null) }, text = { Text(msg) })
}

@Composable
fun Tag(text: String, c: Color, icon: ImageVector? = null) = Surface(shape = Pill, color = c.copy(.14f), contentColor = c) {
    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Blob(icon: ImageVector, c: Color, size: Int = 44, shape: RoundedPolygon = MaterialShapes.Cookie6Sided) {
    val (bg, fg) = tonal(c)
    Box(Modifier.size(size.dp).background(bg, shape.toShape()), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size((size * .5f).dp), tint = fg) }
}

@Composable
fun PkgIcon(tracker: AppTracker, pkg: String, size: Int = 44) {
    val bmp by produceState<ImageBitmap?>(null, pkg) { value = withContext(Dispatchers.IO) { tracker.icon(pkg)?.asImageBitmap() } }
    bmp?.let { Image(it, null, Modifier.size(size.dp).clip(RoundedCornerShape((size * .28f).dp))) } ?: Blob(Sym.Android, Gray, size)
}

// ---------------- Guardian (ShizukuPowerModal) ----------------

private val LOADING_TEXTS = listOf(R.string.xtra_waking_up_the_hamsters, R.string.xtra_consulting_the_oracle, R.string.xtra_decrypting_matrix_code, R.string.xtra_bribing_the_cpu, R.string.xtra_sweeping_for_bugs,
    R.string.xtra_loading_magic_spells, R.string.xtra_summoning_daemons, R.string.xtra_calculating_pi, R.string.xtra_charging_flux_capacitor, R.string.xtra_reticulating_splines, R.string.xtra_checking_gravity_settings, R.string.xtra_polishing_pixels)
// UAD-ng: the maintained fork. The original 0x192 list stopped updating and rated many services too eagerly (Bluetooth MIDI as Recommended).
private const val UAD_URL = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"

private data class Uad(val removal: String, val description: String, val list: String)

private data class Risk(val label: String, val color: Color)

private fun debloatRisk(ctx: Context, removal: String?) = when (removal?.lowercase()) {
    "recommended" -> Risk(ctx.getString(R.string.xtra_recommended), Safe); "advanced" -> Risk(ctx.getString(R.string.xtra_advanced), Yellow); "expert" -> Risk(ctx.getString(R.string.xtra_expert), Orange)
    "unsafe" -> Risk(ctx.getString(R.string.xtra_unsafe), Red); "untested" -> Risk(ctx.getString(R.string.xtra_untested), Purple); else -> Risk(ctx.getString(R.string.common_unknown), Gray)
}

private fun riskLevel(ctx: Context, n: Int) = when { n >= 3 -> Risk(ctx.getString(R.string.xtra_critical), Red); n >= 1 -> Risk(ctx.getString(R.string.xtra_warning), Yellow); else -> Risk(ctx.getString(R.string.xtra_safe), Safe) }

private fun permIcon(p: String) = when {
    "CAMERA" in p -> Sym.PhotoCamera; "RECORD_AUDIO" in p -> Sym.Mic; "LOCATION" in p -> Sym.LocationOn
    "CONTACTS" in p -> Sym.Contacts; "SMS" in p -> Sym.Sms; "STORAGE" in p -> Sym.Folder; else -> Sym.Shield
}

/** JS String.replace with a string pattern only hits the first occurrence. */
fun cleanPermName(p: String) = p.replaceFirst("android.permission.", "").replaceFirst("ACCESS_", "").replaceFirst("READ_", "").replaceFirst("WRITE_", "").replaceFirst("_", " ")

/** uad_lists.json is an array of {id, removal, ...} in the original list and an object keyed by package in UAD-ng; both accepted. */
private fun parseUad(raw: String): Map<String, Uad> {
    fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
    fun uad(o: JsonObject) = Uad(o.str("removal"), o.str("description").trim(), o.str("list"))
    return when (val e = json.parseToJsonElement(raw)) {
        is JsonArray -> e.mapNotNull { it as? JsonObject }.mapNotNull { o -> o.str("id").takeIf { it.isNotEmpty() }?.let { it to uad(o) } }.toMap()
        is JsonObject -> e.mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to uad(it) } }.toMap()
        else -> emptyMap()
    }
}

@Composable
fun GuardianSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val tracker = vm.tracker; val scope = rememberCoroutineScope(); val clip = LocalClipboardManager.current; val toast = rememberSheetToast()
    val ctx = LocalContext.current
    val loadDbFailMsg = stringResource(R.string.xtra_failed_to_load_debloat_database)
    val copyFailMsg = stringResource(R.string.xtra_failed_to_copy)
    val shizukuDeniedMsg = stringResource(R.string.xtra_shizuku_permission_denied)
    var tab by rememberSaveable { mutableStateOf("permissions") }
    var apps by remember { mutableStateOf(emptyList<RiskyApp>()) }; var sysApps by remember { mutableStateOf(emptyList<SysApp>()) }
    var dCache by remember { mutableStateOf<List<RiskyApp>?>(null) }; var sCache by remember { mutableStateOf<List<SysApp>?>(null) }
    var accepted by remember { mutableStateOf(vm.flag("debloat") != null) }; var loading by remember { mutableStateOf(true) }; var msgIdx by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }; var processing by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }; var filter by rememberSaveable { mutableStateOf("all") }
    var uad by remember { mutableStateOf<Map<String, Uad>>(emptyMap()) }; var info by remember { mutableStateOf<SysApp?>(null) }
    var permApp by remember { mutableStateOf<String?>(null) }; var surgeApp by remember { mutableStateOf<RiskyApp?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    fun ask(msg: String, action: suspend () -> Unit) { confirm = msg to action }

    suspend fun fetchApps() {
        loading = true
        try { tracker.riskyApps().sortedByDescending { it.permissions.size }.let { apps = it; dCache = it } }
        catch (e: Exception) { error = e.message ?: ctx.getString(R.string.xtra_failed_to_scan_packages) } finally { loading = false }
    }
    suspend fun fetchSystemApps() {
        loading = true
        try { tracker.systemApps().sortedWith(compareBy<SysApp> { it.isInstalled }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }).let { sysApps = it; sCache = it } }
        catch (e: Exception) { error = e.message ?: ctx.getString(R.string.xtra_failed_to_fetch_system_apps) } finally { loading = false }
    }

    LaunchedEffect(tab, accepted) {
        if (tab == "debloater") {
            if (uad.isEmpty()) launch { runCatching { parseUad(Net.text(UAD_URL)) }.onSuccess { uad = it }.onFailure { toast.show(loadDbFailMsg, true) } }
            sCache?.let { sysApps = it; loading = false } ?: fetchSystemApps()
        } else dCache?.let { apps = it; loading = false } ?: fetchApps()
    }
    LaunchedEffect(loading) { while (loading) { delay(1500); msgIdx = (msgIdx + 1) % LOADING_TEXTS.size } }

    fun copy(pkg: String) = runCatching { clip.setText(AnnotatedString(pkg)); vm.haptic(H.SELECTION) }.onFailure { toast.show(copyFailMsg, true) }

    fun revoke(pkg: String, perm: String) = ask(ctx.getString(R.string.xtra_revoke_app_might_crash, cleanPermName(perm))) {
        processing = pkg
        try { tracker.requestElevated(); tracker.revokePermission(pkg, perm); vm.haptic(H.SUCCESS); toast.show(ctx.getString(R.string.xtra_permission_revoked)); dCache = null; delay(500); fetchApps() }
        catch (e: Exception) { toast.show(e.message ?: ctx.getString(R.string.xtra_revoke_failed), true) } finally { processing = null }
    }

    fun extract(pkg: String) = scope.launch {
        processing = pkg
        try { toast.show(ctx.getString(R.string.xtra_extracted, tracker.extractApk(pkg).substringAfterLast('/'))) }
        catch (e: Exception) { toast.show(e.message ?: ctx.getString(R.string.xtra_extraction_failed), true) } finally { processing = null }
    }

    // The info dialog is the confirmation: it shows what the app does and how risky removing it is
    fun toggle(a: SysApp) = scope.launch {
        processing = a.packageName
        try {
            tracker.requestElevated(); tracker.toggleSystemApp(a.packageName, !a.isInstalled); vm.haptic(H.SUCCESS)
            toast.show(if (a.isInstalled) ctx.getString(R.string.xtra_app_removed, a.name) else ctx.getString(R.string.xtra_app_restored, a.name)); sCache = null; delay(800); fetchSystemApps()
        } catch (e: Exception) { vm.haptic(H.ERROR); toast.show(e.message ?: ctx.getString(R.string.xtra_operation_failed), true) } finally { processing = null }
    }

    val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography
    var shizuku by remember { mutableStateOf(tracker.elevatedReady()) }
    LaunchedEffect(Unit) { while (true) { delay(2000); shizuku = tracker.elevatedReady() } }
    data class Mode(val id: String, val label: String, val icon: ImageVector, @StringRes val blurb: Int, val shape: RoundedPolygon)
    val modes = listOf(
        Mode("permissions", stringResource(R.string.xtra_perms), Sym.Security, R.string.xtra_spot_and_revoke_risky_permissions, MaterialShapes.Cookie9Sided),
        Mode("surgeon", stringResource(R.string.xtra_surgeon), Sym.MedicalServices, R.string.xtra_pull_the_apk_out_of_any, MaterialShapes.Clover4Leaf),
        Mode("debloater", stringResource(R.string.xtra_debloat), Sym.DeleteSweep, R.string.xtra_remove_apps_you_never_use_reversibly, MaterialShapes.SoftBurst),
    )
    val mi = modes.indexOfFirst { it.id == tab }.coerceAtLeast(0); val mode = modes[mi]

    SheetToastHost(toast)
    val q = query.trim().lowercase()
    fun match(name: String, pkg: String, sys: Boolean) = (filter == "all" || (filter == "system") == sys) && (q.isEmpty() || name.lowercase().contains(q) || pkg.lowercase().contains(q))
    val perms = apps.filter { it.permissions.isNotEmpty() && match(it.name, it.packageName, it.isSystem) }
    val surgeon = apps.filter { match(it.name, it.packageName, it.isSystem) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val bloat = sysApps.filter { match(it.name, it.packageName, it.isSystem) }
    val count = when (tab) { "permissions" -> perms.size; "surgeon" -> surgeon.size; else -> bloat.size }

    // One scrolling list, controls included: nothing is pinned, so nothing clips while the apps scroll
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item("head") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                MorphBlob(modes.map { it.shape }, mi, 48.dp, cs.primaryContainer) { Icon(mode.icon, null, Modifier.size(24.dp), tint = cs.onPrimaryContainer) }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Guardian", style = ty.headlineSmallEmphasized)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (shizuku) Safe else cs.error)); Spacer(Modifier.width(6.dp))
                        Text(if (tracker.root) stringResource(R.string.xtra_root) else if (shizuku) stringResource(R.string.xtra_shizuku_on) else stringResource(R.string.xtra_shizuku_off), style = ty.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        item("modes") {
            // Connected button group: the three tools are one exclusive choice
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                modes.forEachIndexed { i, m ->
                    ToggleButton(tab == m.id, { vm.haptic(H.SELECTION); tab = m.id; error = "" }, Modifier.weight(1f).semantics { role = Role.RadioButton },
                        shapes = when (i) { 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(); modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(); else -> ButtonGroupDefaults.connectedMiddleButtonShapes() }) {
                        Icon(m.icon, null, Modifier.size(ToggleButtonDefaults.IconSize)); Spacer(Modifier.width(8.dp)); Text(m.label, maxLines = 1)
                    }
                }
            }
        }
        item("search") {
            TextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = Pill,
                placeholder = { Text(stringResource(R.string.xtra_search_apps_or_packages)) }, leadingIcon = { Icon(Sym.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Sym.Close, stringResource(R.string.common_clear)) } },
                colors = TextFieldDefaults.colors(focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        }
        item("filters") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                listOf("all" to stringResource(R.string.myapps_select_all), "user" to stringResource(R.string.common_user), "system" to stringResource(R.string.common_system)).forEach { (id, label) -> Choice(filter == id, label) { filter = id } }
                if (!loading && error.isEmpty()) Text(when (tab) { "permissions" -> stringResource(R.string.xtra_flagged_count, count); else -> stringResource(R.string.xtra_apps_count, count) },
                    Modifier.padding(start = 8.dp), style = ty.labelLarge, color = cs.onSurfaceVariant)
            }
        }
        when {
            loading -> item("loading") {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ContainedLoadingIndicator(Modifier.size(64.dp))
                    AnimatedContent(LOADING_TEXTS[msgIdx], transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "load") {
                        Text(stringResource(it), style = ty.titleSmallEmphasized, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
            error.isNotEmpty() -> item("error") { EmptyState(Sym.ErrorOutline, stringResource(R.string.xtra_scan_failed), error, Modifier.padding(top = 32.dp)) }
            count == 0 -> item("empty") { EmptyState(Sym.SearchOff, if (tab == "permissions" && q.isEmpty()) stringResource(R.string.xtra_nothing_risky_here) else stringResource(R.string.xtra_no_apps_found), modifier = Modifier.padding(top = 32.dp)) }
            tab == "debloater" -> itemsIndexed(bloat, key = { _, a -> "d-" + a.packageName }) { i, a ->
                val r = debloatRisk(ctx, uad[a.packageName]?.removal)
                GuardRow(tracker, a.packageName, a.name, i, bloat.size, Modifier.animateItem(), dim = !a.isInstalled, busy = processing == a.packageName, dot = r.color,
                    sub = if (a.isInstalled) r.label else stringResource(R.string.xtra_removed)) { vm.haptic(H.LIGHT); info = a }
            }
            tab == "permissions" -> itemsIndexed(perms, key = { _, a -> "p-" + a.packageName }) { i, a ->
                val n = a.permissions.size
                GuardRow(tracker, a.packageName, a.name, i, perms.size, Modifier.animateItem(), busy = processing == a.packageName, dot = riskLevel(ctx, n).color,
                    sub = stringResource(if (n == 1) R.string.xtra_risky_permission else R.string.xtra_risky_permissions, n)) { vm.haptic(H.LIGHT); permApp = a.packageName }
            }
            else -> itemsIndexed(surgeon, key = { _, a -> "s-" + a.packageName }) { i, a ->
                GuardRow(tracker, a.packageName, a.name, i, surgeon.size, Modifier.animateItem(), busy = processing == a.packageName,
                    sub = if (a.isSystem) stringResource(R.string.xtra_system_app) else a.packageName) { vm.haptic(H.LIGHT); surgeApp = a }
            }
        }
    }
    permApp?.let { pkg ->
        val a = apps.firstOrNull { it.packageName == pkg }
        if (a == null || a.permissions.isEmpty()) { permApp = null; return@let }
        AlertDialog({ permApp = null }, icon = { PkgIcon(tracker, a.packageName, 48) }, title = { Text(a.name, textAlign = TextAlign.Center) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.xtra_allowed_to_use), Modifier.padding(bottom = 6.dp), style = ty.labelLarge, color = cs.onSurfaceVariant)
                    a.permissions.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(permIcon(p), null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                            Text(cleanPermName(p).lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f).padding(horizontal = 12.dp), style = ty.bodyMedium)
                            if (processing == a.packageName) LoadingIndicator(Modifier.size(28.dp))
                            else TextButton({ revoke(a.packageName, p) }, enabled = processing == null, colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text(stringResource(R.string.xtra_revoke)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ permApp = null }) { Text(stringResource(R.string.xtra_done)) } })
    }
    surgeApp?.let { a ->
        AlertDialog({ surgeApp = null }, icon = { PkgIcon(tracker, a.packageName, 48) }, title = { Text(a.name, textAlign = TextAlign.Center) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.xtra_saves_this_apps_apk_to_your_downloads), style = ty.bodyMedium)
                    Text(a.packageName, Modifier.clickable { copy(a.packageName) }, style = ty.labelSmall, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton({ surgeApp = null; extract(a.packageName) }, enabled = processing == null) { Text(stringResource(R.string.xtra_extract_apk)) } },
            dismissButton = { TextButton({ surgeApp = null }) { Text(stringResource(R.string.xtra_close)) } })
    }
    ConfirmDialog(confirm?.first, null, { confirm?.second?.let { a -> scope.launch { a() } } }) { confirm = null }
    if (tab == "debloater" && !accepted) AlertDialog({ tab = "permissions" }, icon = { Icon(Sym.WarningAmber, null) }, title = { Text(stringResource(R.string.xtra_careful_with_system_apps)) },
        text = { Text(stringResource(R.string.xtra_some_keep_your_phone_running_removing_the)) },
        confirmButton = { TextButton({ accepted = true; vm.setFlag("debloat", "1") }) { Text(stringResource(R.string.xtra_i_understand)) } },
        dismissButton = { TextButton({ tab = "permissions" }) { Text(stringResource(R.string.xtra_back)) } })
    info?.let { a ->
        val u = uad[a.packageName]; val r = debloatRisk(ctx, u?.removal)
        AlertDialog({ info = null }, icon = { PkgIcon(tracker, a.packageName, 48) }, title = { Text(a.name, textAlign = TextAlign.Center) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag(r.label, r.color); u?.list?.takeIf { it.isNotEmpty() }?.let { Tag(it, Gray) }; if (!a.isSystem) Tag(stringResource(R.string.xtra_user_app), Gray)
                    }
                    u?.description?.takeIf { it.isNotEmpty() }?.let { UadNotes(it) } ?: Text(stringResource(R.string.xtra_no_notes_for_this_app_yet_if_you), style = ty.bodyMedium)
                    Text(a.packageName, Modifier.clickable { copy(a.packageName) }, style = ty.labelSmall, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton({ info = null; toggle(a) }, enabled = processing == null,
                    colors = if (a.isInstalled) ButtonDefaults.textButtonColors(contentColor = cs.error) else ButtonDefaults.textButtonColors()) { Text(if (a.isInstalled) stringResource(R.string.xtra_remove) else stringResource(R.string.xtra_restore)) }
            },
            dismissButton = { TextButton({ info = null }) { Text(stringResource(R.string.xtra_close)) } })
    }
}

private val URL_RE = Regex("""https?://[^\s)\]>]+""")
private fun site(u: String) = runCatching { java.net.URI(u).host.removePrefix("www.") }.getOrNull() ?: u

/** UAD notes made readable: a short first line becomes the heading, every other line its own paragraph with in-line links shortened to their
 *  site and tappable, and lines that are only a link gathered at the bottom as source rows. */
@Composable
private fun UadNotes(text: String) {
    val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography; val open = LocalUriHandler.current
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val (sources, notes) = lines.partition { URL_RE.containsMatchIn(it) && URL_RE.replace(it, "").trim().trimEnd(':', '.').lowercase() in setOf("", "see", "source", "more info", "more") }
    val head = notes.firstOrNull()?.takeIf { notes.size > 1 && it.length <= 48 && !it.endsWith('.') }
    val link = TextLinkStyles(SpanStyle(color = cs.primary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        head?.let { Text(it, style = ty.titleSmall, fontWeight = FontWeight.Bold) }
        (if (head != null) notes.drop(1) else notes).forEach { l ->
            Text(buildAnnotatedString {
                var at = 0
                URL_RE.findAll(l).forEach { m ->
                    append(l.substring(at, m.range.first))
                    withLink(LinkAnnotation.Url(m.value, link)) { append(site(m.value)) }
                    at = m.range.last + 1
                }
                append(l.substring(at))
            }, style = ty.bodyMedium, color = cs.onSurfaceVariant)
        }
        val urls = sources.flatMap { URL_RE.findAll(it).map { m -> m.value }.toList() }.distinct()
        if (urls.isNotEmpty()) {
            Text(stringResource(R.string.xtra_sources), Modifier.padding(top = 4.dp), style = ty.labelLarge, color = cs.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                urls.forEachIndexed { i, u ->
                    val shape = RoundedCornerShape(if (i == 0) 16.dp else 4.dp, if (i == 0) 16.dp else 4.dp, if (i == urls.lastIndex) 16.dp else 4.dp, if (i == urls.lastIndex) 16.dp else 4.dp)
                    Row(Modifier.fillMaxWidth().clip(shape).background(cs.surfaceContainerHighest).clickable { runCatching { open.openUri(u) } }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym.Link, null, Modifier.size(18.dp), tint = cs.primary); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(site(u), style = ty.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(u.substringAfter("://").substringAfter('/', ""), style = ty.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Sym.OpenInNew, stringResource(R.string.myapps_open), Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One app in a Guardian list: icon, name, and a status line with an optional colored dot. Tapping opens its details. */
@Composable
private fun GuardRow(tracker: AppTracker, pkg: String, name: String, i: Int, n: Int, modifier: Modifier, sub: String, dim: Boolean = false, busy: Boolean = false,
                     dot: Color? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography
    GroupItem(i, n, modifier.padding(horizontal = 16.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).alpha(if (dim) .55f else 1f), verticalAlignment = Alignment.CenterVertically) {
            PkgIcon(tracker, pkg, 40)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(name, style = ty.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    dot?.let { Box(Modifier.size(6.dp).clip(CircleShape).background(it)); Spacer(Modifier.width(6.dp)) }
                    Text(sub, style = ty.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (busy) LoadingIndicator(Modifier.size(32.dp))
        }
    }
}

// ---------------- Sentinel (SentinelModal + workers/sentinel.worker.ts) ----------------

private const val SHARD_URL = "https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@data/sentinel/shard_"
private val BUCKETS = "0123456789abcdef".map { it.toString() }
private val KNOWN_THREATS = mapOf(
    "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f" to "EICAR Test File (Safe)", "131f95c51cc819465fa1797f6ccacf9d494aaaff46fa3eac73ae63bb9ea99a42" to "EICAR Test File (Zip)",
    "44d88612fea8a8f36de82e1278abb02f" to "EICAR Test File (MD5)", "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8" to "Orion-Malware-Sim",
    "d41d8cd98f00b204e9800998ecf8427e" to "Null Byte Payload")
private val KNOWN_BAD_PACKAGES = setOf("com.android.htmlviewer", "com.metasploit.stage", "com.vanced.android.youtube", "org.malicious.trojan", "com.example.malware")
private val TRUSTED_DNS = setOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112", "208.67.222.222", "208.67.220.220")

data class Threat(val path: String, val hash: String, val threat: String)

/** Worker port: the 16 hash shards live in filesDir/sentinel instead of CacheStorage. */
// Note: parsed shards stay in memory for the sheet's lifetime, stream per bucket if the DB outgrows RAM
class SentinelDb(dir: File, private val unknownThreat: String = "Unknown Threat") {
    private val root = File(dir, "sentinel").apply { mkdirs() }
    private val maps = HashMap<String, Map<String, String>>()
    private fun shard(b: String) = File(root, "shard_$b.json")
    private fun load(b: String) = maps.getOrPut(b) {
        runCatching { json.parseToJsonElement(shard(b).readText()).jsonArray.associate { val o = it.jsonObject; o["h"]!!.jsonPrimitive.content to (o["n"]?.jsonPrimitive?.contentOrNull ?: unknownThreat) } }.getOrDefault(emptyMap())
    }
    fun ready() = BUCKETS.all { shard(it).exists() }
    fun count() = BUCKETS.sumOf { load(it).size } + KNOWN_THREATS.size
    suspend fun update(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        BUCKETS.forEachIndexed { i, b -> runCatching { shard(b).writeText(Net.text("$SHARD_URL$b.json", noStore = true)); maps.remove(b) }; onProgress((i + 1) / 16f * 100) }
    }
    fun check(files: List<Pair<String, String>>) = files.filter { it.second.isNotEmpty() }.mapNotNull { (p, h) ->
        (KNOWN_THREATS[h] ?: h.take(1).lowercase().takeIf { it in BUCKETS }?.let { load(it)[h] })?.let { Threat(p, h, it) }
    }
}

private data class Info(val text: String, val color: Color, @StringRes val desc: Int, val icon: ImageVector)

private fun encryptionInfo(ctx: Context, t: String) = t.uppercase().let {
    when {
        "WPA3" in it -> Info(ctx.getString(R.string.xtra_secure), Safe, R.string.xtra_latest_security_standard, Sym.EnhancedEncryption)
        "WPA2" in it -> Info(ctx.getString(R.string.xtra_good), Blue, R.string.xtra_industry_standard_protection, Sym.Lock)
        "WPA" in it -> Info(ctx.getString(R.string.xtra_weak), Caution, R.string.xtra_outdated_and_vulnerable, Sym.LockOpen)
        "WEP" in it -> Info(ctx.getString(R.string.xtra_insecure), Red, R.string.xtra_easily_compromised, Sym.NoEncryption)
        "OPEN" in it -> Info(ctx.getString(R.string.myapps_open), Red, R.string.xtra_no_encryption_high_risk, Sym.LockOpen)
        else -> Info(ctx.getString(R.string.common_unknown), Gray, R.string.xtra_could_not_determine_status, Sym.HelpOutline)
    }
}

private fun dnsInfo(ctx: Context, servers: List<String>) = when {
    servers.isEmpty() -> Info(ctx.getString(R.string.common_unknown), Gray, R.string.xtra_no_dns_servers_detected, Sym.Dns)
    servers.any { it in TRUSTED_DNS } -> Info(ctx.getString(R.string.xtra_trusted), Safe, R.string.xtra_using_a_known_secure_dns, Sym.VerifiedUser)
    else -> Info(ctx.getString(R.string.common_unknown), Caution, R.string.xtra_using_default_or_isp_dns, Sym.Dns)
}

@Composable
private fun SecurityCard(title: String, desc: String, status: String, c: Color, icon: ImageVector) =
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Blob(icon, c, 40)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Tag(status, c)
        }
    }

@Composable
private fun ScanAction(icon: ImageVector, c: Color, title: String, desc: String, shape: RoundedPolygon, onClick: () -> Unit) =
    Card(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Blob(icon, c, 52, shape)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Sym.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

@Composable
private fun Advisory() {
    var open by remember { mutableStateOf<String?>(null) }; val uri = LocalUriHandler.current
    @Composable fun Sec(id: String, title: String, body: @Composable ColumnScope.() -> Unit) = Surface({ open = if (open == id) null else id }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(if (open == id) Sym.ExpandLess else Sym.ExpandMore, null)
            }
            AnimatedVisibility(open == id) { Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = body) }
        }
    }
    val small = MaterialTheme.typography.bodySmall
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(Caution.copy(.1f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Sym.Info, null, tint = Caution); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.xtra_a_note_on_these_findings), style = MaterialTheme.typography.titleSmall) }
            Text(stringResource(R.string.xtra_sentinel_s_analysis_uses_a_global), style = small)
            Text(stringResource(R.string.xtra_how_to_double_check), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Sec("virustotal", stringResource(R.string.xtra_1_use_virustotal)) {
                TextButton({ uri.openUri("https://www.virustotal.com/") }) { Icon(Sym.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.xtra_scan_online)) }
                Text(stringResource(R.string.xtra_this_service_analyzes_files_with_70), style = small)
                Text(stringResource(R.string.xtra_0_detections_generally_safe), style = small, color = Safe)
                Text(stringResource(R.string.xtra_1_5_detections_likely_a_false), style = small, color = Caution)
                Text(stringResource(R.string.xtra_5_detections_high_caution_treat_as), style = small, color = Red)
            }
            Sec("source", stringResource(R.string.xtra_2_consider_the_source)) {
                Text(stringResource(R.string.xtra_official_sources_like_github_f_droid), style = small)
                Text(stringResource(R.string.xtra_why_do_safe_sources_get_flagged), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.xtra_antivirus_scanners_sometimes_use_heuristic_analy), style = small)
            }
            Sec("community", stringResource(R.string.xtra_3_search_the_community)) {
                Text(stringResource(R.string.xtra_search_for_the_file_s_hash), style = small)
            }
        }
    }
}

@Composable
fun SentinelSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val ctx = LocalContext.current; val tracker = vm.tracker; val scope = rememberCoroutineScope(); val clip = LocalClipboardManager.current; val toast = rememberSheetToast()
    val netScanFailMsg = stringResource(R.string.xtra_failed_to_run_network_scan)
    val gettingAppsMsg = stringResource(R.string.xtra_getting_app_list)
    val initScannerMsg = stringResource(R.string.xtra_initializing_scanner)
    val searchingFilesMsg = stringResource(R.string.xtra_searching_for_files)
    val finalizingMsg = stringResource(R.string.xtra_finalizing_results)
    val rapidLabel = stringResource(R.string.xtra_rapid); val deepLabel = stringResource(R.string.xtra_deep); val finalizingShortMsg = stringResource(R.string.xtra_finalizing); val unknownThreatMsg = stringResource(R.string.xtra_unknown_threat)
    val db = remember { SentinelDb(ctx.filesDir, unknownThreatMsg) }
    var view by remember { mutableStateOf("permissions") }; var perm by remember { mutableStateOf(tracker.permissionStatus()) }
    var dbStatus by remember { mutableStateOf("checking") }; var dbProgress by remember { mutableFloatStateOf(0f) }; var sigs by remember { mutableIntStateOf(0) }
    var scanType by remember { mutableStateOf(deepLabel) }; var progress by remember { mutableFloatStateOf(0f) }; var current by remember { mutableStateOf("") }
    var threats by remember { mutableStateOf(emptyList<Threat>()) }; var rapid by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var net by remember { mutableStateOf<NetSecurity?>(null) }; var netScanning by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }; var job by remember { mutableStateOf<Job?>(null) }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perm = tracker.permissionStatus() }

    LaunchedEffect(Unit) { while (true) { perm = tracker.permissionStatus(); delay(2000) } }
    LaunchedEffect(Unit) {
        if (!withContext(Dispatchers.IO) { runCatching { db.ready() }.getOrDefault(true) }) { dbStatus = "updating"; db.update { dbProgress = it } }
        dbStatus = "ready"; sigs = withContext(Dispatchers.IO) { db.count() }
    }
    LaunchedEffect(perm.storage, dbStatus) { view = when { !perm.storage -> "permissions"; dbStatus != "ready" -> "database"; view in setOf("scanning", "results", "network") -> view; else -> "main" } }

    fun netScan() = scope.launch {
        netScanning = true; net = null
        try { net = withContext(Dispatchers.IO) { tracker.networkSecurity() } } catch (_: Exception) { toast.show(netScanFailMsg, true) } finally { netScanning = false }
    }
    LaunchedEffect(view) { if (view == "network" && net == null) netScan() }
    BackHandler(view in setOf("results", "network", "scanning")) { job?.cancel(); view = "main" }

    fun reset() { job?.cancel(); threats = emptyList(); rapid = emptyList(); progress = 0f; expanded = null; view = "scanning" }
    suspend fun finish(t: List<Threat>, r: List<Pair<String, String>>) {
        progress = 100f; delay(500); threats = t; rapid = r; vm.haptic(if (t.isEmpty() && r.isEmpty()) H.SUCCESS else H.ERROR); view = "results"
    }
    fun rapidScan() {
        reset(); scanType = rapidLabel; current = gettingAppsMsg
        job = scope.launch {
            try {
                val apps = withContext(Dispatchers.IO) { tracker.installedPackages() }
                apps.forEachIndexed { i, (n, _) -> current = n; progress = i * 100f / apps.size; delay(50) }
                current = finalizingShortMsg; finish(emptyList(), apps.filter { it.second.lowercase() in KNOWN_BAD_PACKAGES })
            } catch (e: CancellationException) { throw e } catch (_: Exception) { view = "results" }
        }
    }
    fun deepScan() {
        reset(); scanType = deepLabel; current = initScannerMsg
        job = scope.launch {
            val ticker = launch { var p = 0; while (p < 95) { delay(400); p++; progress = p.toFloat() } }
            val names = Channel<String>(64, BufferOverflow.DROP_OLDEST)
            val marquee = launch { current = searchingFilesMsg; for (n in names) { current = n; delay(30) } }
            val found = ArrayList<Threat>()
            runCatching { tracker.scanDirectory { batch -> found += db.check(batch); batch.forEach { names.trySend(it.first.substringAfterLast('/')) } } }
            names.close(); marquee.cancel(); ticker.cancel(); current = finalizingMsg
            finish(found, emptyList())
        }
    }

    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SheetTitle("Sentinel", Sym.GppGood, stringResource(R.string.xtra_threat_intelligence)) }
        Tag(if (perm.storage) stringResource(R.string.xtra_sensors_online) else stringResource(R.string.xtra_sensors_locked), if (perm.storage) Safe else Red, if (perm.storage) Sym.VerifiedUser else Sym.Lock)
        Spacer(Modifier.width(20.dp))
    }
    SheetToastHost(toast)
    AnimatedContent(view, transitionSpec = { (fadeIn() + expandVertically()) togetherWith fadeOut() }, label = "sentinel") { v ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val hero: @Composable (ImageVector, Color, RoundedPolygon) -> Unit = { i, col, s -> Blob(i, col, 112, s) }
            when (v) {
                "permissions" -> {
                    hero(Sym.Lock, Caution, MaterialShapes.Cookie9Sided)
                    Text(stringResource(R.string.xtra_access_required), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(stringResource(R.string.xtra_sentinel_needs_permission_to_scan_your), textAlign = TextAlign.Center, color = c.onSurfaceVariant)
                    Button({
                        tracker.universalStorageIntent()?.let { runCatching { ctx.startActivity(it) } }
                            ?: legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) { Icon(Sym.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.xtra_grant_storage_access)) }
                    Text(stringResource(R.string.xtra_this_is_a_one_time_request), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
                "database" -> {
                    LoadingIndicator(Modifier.size(112.dp))
                    Text(stringResource(R.string.xtra_syncing_threat_intel), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(if (dbStatus == "updating") stringResource(R.string.xtra_downloading_latest_threat_signatures) else stringResource(R.string.xtra_connecting_to_sentinel_network), color = c.onSurfaceVariant)
                    LinearWavyProgressIndicator({ dbProgress / 100f }, Modifier.fillMaxWidth())
                    if (dbStatus == "updating") Text("${dbProgress.toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                "main" -> {
                    hero(Sym.GppGood, Safe, MaterialShapes.SoftBurst)
                    Text(stringResource(R.string.xtra_system_protected), style = MaterialTheme.typography.headlineSmallEmphasized)
                    ScanAction(Sym.Bolt, MaterialTheme.colorScheme.primary, stringResource(R.string.xtra_rapid_scan), stringResource(R.string.xtra_checks_installed_apps_apks), MaterialShapes.Cookie4Sided, ::rapidScan)
                    ScanAction(Sym.Radar, Purple, stringResource(R.string.xtra_deep_scan), stringResource(R.string.xtra_analyzes_file_storage_downloads), MaterialShapes.Clover4Leaf, ::deepScan)
                    ScanAction(Sym.Wifi, SkyC, stringResource(R.string.xtra_network_sentry), stringResource(R.string.xtra_connection_security_audit), MaterialShapes.Pill) { view = "network" }
                }
                "scanning" -> {
                    Box(contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator({ progress / 100f }, Modifier.size(180.dp))
                        Text("${progress.toInt()}%", style = MaterialTheme.typography.displaySmallEmphasized)
                    }
                    Text(stringResource(R.string.xtra_scan, scanType), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(current, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
                "results" -> {
                    if (threats.isEmpty() && rapid.isEmpty()) {
                        hero(Sym.CheckCircle, Safe, MaterialShapes.Sunny)
                        Text(stringResource(R.string.xtra_system_clean), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(stringResource(R.string.xtra_no_threats_detected), color = c.onSurfaceVariant)
                    } else {
                        Advisory()
                        Text(stringResource(R.string.xtra_threat_report), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLargeEmphasized)
                        @Composable fun Item(key: String, title: String, sub: String?, fields: List<Pair<String, String>>, copyLabel: String, copyValue: String?) {
                            val open = expanded == key
                            Card({ expanded = if (open) null else key }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(c.errorContainer.copy(.45f))) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Sym.BugReport, null, tint = c.error)
                                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.error) }
                                        }
                                        Icon(if (open) Sym.ExpandLess else Sym.ExpandMore, null)
                                    }
                                    AnimatedVisibility(open) {
                                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            fields.forEach { (k, value) ->
                                                Text(k, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                                                Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                            }
                                            copyValue?.let { cv -> FilledTonalButton({ clip.setText(AnnotatedString(cv)); vm.haptic(H.SELECTION) }, shapes = ButtonDefaults.shapes()) { Icon(Sym.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(copyLabel) } }
                                        }
                                    }
                                }
                            }
                        }
                        if (threats.isNotEmpty()) {
                            Text(stringResource(R.string.xtra_infected_files_deep_scan), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, color = c.error)
                            threats.forEachIndexed { i, t ->
                                Item("file-$i", t.path.substringAfterLast('/'), t.threat, listOf(stringResource(R.string.xtra_full_path) to t.path) + (if (t.hash.isNotEmpty()) listOf(stringResource(R.string.xtra_sha_256_hash) to t.hash) else emptyList()), stringResource(R.string.xtra_copy_hash), t.hash.ifEmpty { null })
                            }
                        }
                        if (rapid.isNotEmpty()) {
                            Text(stringResource(R.string.xtra_suspicious_apps_rapid_scan), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, color = c.error)
                            rapid.forEachIndexed { i, (n, p) -> Item("app-$i", n, null, listOf(stringResource(R.string.xtra_package_name) to p), stringResource(R.string.xtra_copy_package_name), p) }
                        }
                    }
                    Button({ view = "main" }, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_return)) }
                }
                else -> when {
                    netScanning -> {
                        LoadingIndicator(Modifier.size(112.dp))
                        Text(stringResource(R.string.xtra_auditing_network), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(stringResource(R.string.xtra_checking_connection_integrity), color = c.onSurfaceVariant)
                    }
                    net == null -> {
                        hero(Sym.WifiOff, Red, MaterialShapes.Cookie9Sided)
                        Text(stringResource(R.string.xtra_scan_failed_2), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(stringResource(R.string.xtra_could_not_retrieve_network_information), color = c.onSurfaceVariant)
                        Button({ netScan() }, shapes = ButtonDefaults.shapes()) { Icon(Sym.Refresh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.xtra_rescan)) }
                    }
                    else -> {
                        val n = net!!; val enc = encryptionInfo(ctx, n.encryptionType); val dns = dnsInfo(ctx, n.dnsServers); val adb = n.adbEnabled || n.adbWifiEnabled
                        hero(Sym.Shield, Blue, MaterialShapes.Cookie12Sided)
                        Text(stringResource(R.string.xtra_connection_security), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(stringResource(R.string.xtra_live_analysis_of_your_current_network), color = c.onSurfaceVariant)
                        SecurityCard(stringResource(R.string.xtra_encryption), stringResource(enc.desc), enc.text, enc.color, enc.icon)
                        SecurityCard(stringResource(R.string.xtra_dns_integrity), stringResource(dns.desc), dns.text, dns.color, dns.icon)
                        SecurityCard(stringResource(R.string.xtra_captive_portal), if (n.isCaptivePortal) stringResource(R.string.xtra_web_login_page_detected) else stringResource(R.string.xtra_direct_network_access), if (n.isCaptivePortal) stringResource(R.string.xtra_active) else stringResource(R.string.xtra_none),
                            if (n.isCaptivePortal) Caution else Safe, if (n.isCaptivePortal) Sym.Login else Sym.Lan)
                        SecurityCard(stringResource(R.string.xtra_adb_watchdog), stringResource(R.string.xtra_developer_bridge_status), if (adb) stringResource(R.string.xtra_active) else stringResource(R.string.xtra_inactive), if (adb) Red else Safe, Sym.Terminal)
                        SecurityCard(stringResource(R.string.xtra_proxy_check), stringResource(R.string.xtra_detects_traffic_redirection), if (n.hasProxy) stringResource(R.string.xtra_active) else stringResource(R.string.xtra_inactive), if (n.hasProxy) Red else Safe, Sym.AccountTree)
                        SecurityCard(stringResource(R.string.xtra_vpn_status), stringResource(R.string.xtra_checks_for_active_vpn_tunnel), if (n.isVpnActive) stringResource(R.string.xtra_active) else stringResource(R.string.xtra_inactive), if (n.isVpnActive) Blue else Safe, Sym.VpnLock)
                        Button({ view = "main" }, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_return)) }
                    }
                }
            }
        }
    }
    Text(if (sigs > 0) stringResource(R.string.xtra_d_signatures_live).format(sigs) else "...", Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = c.onSurfaceVariant)
}
