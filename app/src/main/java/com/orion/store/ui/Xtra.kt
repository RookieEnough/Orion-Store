package com.orion.store.ui

import android.Manifest
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.orion.store.data.Net
import com.orion.store.data.json
import com.orion.store.data.riskLabel
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
fun ConfirmDialog(msg: String?, confirm: String = "确认", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (msg != null) AlertDialog(onDismiss, confirmButton = { TextButton({ onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }, icon = { Icon(Sym.WarningAmber, null) }, text = { Text(msg) })
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
    Box(Modifier.size(size.dp).clip(shape.toShape()).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size((size * .5f).dp), tint = fg) }
}

@Composable
fun PkgIcon(tracker: AppTracker, pkg: String, size: Int = 44) {
    val bmp by produceState<ImageBitmap?>(null, pkg) { value = withContext(Dispatchers.IO) { tracker.icon(pkg)?.asImageBitmap() } }
    bmp?.let { Image(it, null, Modifier.size(size.dp).clip(RoundedCornerShape((size * .28f).dp))) } ?: Blob(Sym.Android, Gray, size)
}

// ---------------- Guardian (ShizukuPowerModal) ----------------

private val LOADING_TEXTS = listOf("正在唤醒仓鼠…", "正在咨询先知…", "正在解密矩阵代码…", "正在贿赂 CPU…", "正在清扫 bug…",
    "正在加载魔法咒语…", "正在召唤守护进程…", "正在计算圆周率…", "正在为时空电容充电…", "正在生成样条曲线…", "正在检查重力设置…", "正在打磨像素…")
private const val UAD_URL = "https://raw.githubusercontent.com/0x192/universal-android-debloater/main/resources/assets/uad_lists.json"

private data class Risk(val label: String, val color: Color)

private fun debloatRisk(removal: String?) = when (removal?.lowercase()) {
    "recommended" -> Risk("Recommended", Safe); "advanced" -> Risk("Advanced", Yellow); "expert" -> Risk("Expert", Orange)
    "unsafe" -> Risk("Unsafe", Red); "untested" -> Risk("Untested", Purple); else -> Risk("Unknown", Gray)
}

private fun riskLevel(n: Int) = when { n >= 3 -> Risk("CRITICAL", Red); n >= 1 -> Risk("WARNING", Yellow); else -> Risk("SAFE", Safe) }

private fun permIcon(p: String) = when {
    "CAMERA" in p -> Sym.PhotoCamera; "RECORD_AUDIO" in p -> Sym.Mic; "LOCATION" in p -> Sym.LocationOn
    "CONTACTS" in p -> Sym.Contacts; "SMS" in p -> Sym.Sms; "STORAGE" in p -> Sym.Folder; else -> Sym.Shield
}

/** JS String.replace with a string pattern only hits the first occurrence. */
fun cleanPermName(p: String) = p.replaceFirst("android.permission.", "").replaceFirst("ACCESS_", "").replaceFirst("READ_", "").replaceFirst("WRITE_", "").replaceFirst("_", " ")

/** uad_lists.json is an array of {id, removal}; newer forks ship an object keyed by package, both accepted. */
private fun parseUad(raw: String): Map<String, String> = when (val e = json.parseToJsonElement(raw)) {
    is JsonArray -> e.mapNotNull { it as? JsonObject }.mapNotNull { o -> o["id"]?.jsonPrimitive?.contentOrNull?.let { it to (o["removal"]?.jsonPrimitive?.contentOrNull ?: "") } }.toMap()
    is JsonObject -> e.mapValues { (it.value as? JsonObject)?.get("removal")?.jsonPrimitive?.contentOrNull ?: "" }
    else -> emptyMap()
}

@Composable
fun GuardianSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val tracker = vm.tracker; val scope = rememberCoroutineScope(); val clip = LocalClipboardManager.current; val toast = rememberSheetToast()
    var tab by rememberSaveable { mutableStateOf("permissions") }
    var apps by remember { mutableStateOf(emptyList<RiskyApp>()) }; var sysApps by remember { mutableStateOf(emptyList<SysApp>()) }
    var dCache by remember { mutableStateOf<List<RiskyApp>?>(null) }; var sCache by remember { mutableStateOf<List<SysApp>?>(null) }
    var accepted by remember { mutableStateOf(false) }; var loading by remember { mutableStateOf(true) }; var msgIdx by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }; var expanded by remember { mutableStateOf<String?>(null) }; var processing by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }; var filter by rememberSaveable { mutableStateOf("all") }
    var uad by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    fun ask(msg: String, action: suspend () -> Unit) { confirm = msg to action }

    suspend fun fetchApps() {
        loading = true
        try { tracker.riskyApps().sortedByDescending { it.permissions.size }.let { apps = it; dCache = it } }
        catch (e: Exception) { error = e.message ?: "扫描应用包失败。" } finally { loading = false }
    }
    suspend fun fetchSystemApps() {
        loading = true
        try { tracker.systemApps().sortedWith(compareBy<SysApp> { it.isInstalled }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }).filter { it.isSystem }.let { sysApps = it; sCache = it } }
        catch (e: Exception) { error = e.message ?: "获取系统应用失败。" } finally { loading = false }
    }

    LaunchedEffect(tab, accepted) {
        if (tab == "debloater") {
            if (uad.isEmpty()) launch { runCatching { parseUad(Net.text(UAD_URL)) }.onSuccess { uad = it }.onFailure { toast.show("加载精简数据库失败", true) } }
            if (accepted) sCache?.let { sysApps = it; loading = false } ?: fetchSystemApps()
        } else dCache?.let { apps = it; loading = false } ?: fetchApps()
    }
    LaunchedEffect(loading) { while (loading) { delay(1500); msgIdx = (msgIdx + 1) % LOADING_TEXTS.size } }

    fun copy(pkg: String) = runCatching { clip.setText(AnnotatedString(pkg)); vm.haptic(H.SELECTION) }.onFailure { toast.show("复制失败", true) }

    fun revoke(pkg: String, perm: String) = ask("撤销 ${cleanPermName(perm)}？应用可能会崩溃。") {
        processing = pkg
        try { tracker.requestElevated(); tracker.revokePermission(pkg, perm); vm.haptic(H.SUCCESS); toast.show("权限已撤销"); dCache = null; delay(500); fetchApps() }
        catch (e: Exception) { toast.show(e.message ?: "撤销失败", true) } finally { processing = null }
    }

    fun extract(pkg: String) = scope.launch {
        processing = pkg
        try { toast.show("已提取：${tracker.extractApk(pkg).substringAfterLast('/')}") }
        catch (e: Exception) { toast.show(e.message ?: "提取失败", true) } finally { processing = null }
    }

    fun toggle(a: SysApp) {
        val warn = when (debloatRisk(uad[a.packageName]).label) { "Unsafe" -> "⚠️ 危险：关键系统应用，很可能导致无法开机！"; "Expert", "Advanced" -> "⚠️ 注意：可能导致部分功能异常。"; else -> "" }
        ask("${if (a.isInstalled) "卸载" else "恢复"} ${a.name}？$warn".trim()) {
            processing = a.packageName
            try { tracker.toggleSystemApp(a.packageName, !a.isInstalled); toast.show(if (a.isInstalled) "已卸载（用户 0）" else "已恢复"); sCache = null; delay(800); fetchSystemApps() }
            catch (e: Exception) { toast.show(e.message ?: "操作失败", true) } finally { processing = null }
        }
    }

    val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography
    var shizuku by remember { mutableStateOf(tracker.elevatedReady()) }
    LaunchedEffect(Unit) { while (true) { delay(2000); shizuku = tracker.elevatedReady() } }
    data class Mode(val id: String, val label: String, val icon: ImageVector, val blurb: String, val shape: RoundedPolygon)
    val modes = listOf(
        Mode("permissions", "权限", Sym.Security, "发现并撤销高风险权限", MaterialShapes.Cookie9Sided),
        Mode("surgeon", "提取", Sym.MedicalServices, "从任意应用中提取 APK", MaterialShapes.Clover4Leaf),
        Mode("debloater", "精简", Sym.DeleteSweep, "移除系统应用，可随时恢复", MaterialShapes.SoftBurst),
    )
    val mi = modes.indexOfFirst { it.id == tab }.coerceAtLeast(0); val mode = modes[mi]

    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MorphBlob(modes.map { it.shape }, mi, 60.dp, cs.primaryContainer) { Icon(mode.icon, null, Modifier.size(28.dp), tint = cs.onPrimaryContainer) }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text("守护者", style = ty.headlineMediumEmphasized)
            val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(mode.blurb, transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "blurb") {
                Text(it, style = ty.bodyMedium, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Surface(shape = Pill, color = if (shizuku) cs.tertiaryContainer else cs.errorContainer, contentColor = if (shizuku) cs.onTertiaryContainer else cs.onErrorContainer) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(LocalContentColor.current)); Spacer(Modifier.width(6.dp))
                Text(if (tracker.root) "Root" else if (shizuku) "Shizuku 已开启" else "Shizuku 已关闭", style = ty.labelMedium)
            }
        }
    }
    SheetToastHost(toast)
    // Connected button group: the three tools are one exclusive choice
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        modes.forEachIndexed { i, m ->
            ToggleButton(tab == m.id, { vm.haptic(H.SELECTION); tab = m.id; expanded = null; error = "" }, Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (i) { 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(); modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(); else -> ButtonGroupDefaults.connectedMiddleButtonShapes() }) {
                Icon(m.icon, null, Modifier.size(ToggleButtonDefaults.IconSize)); Spacer(Modifier.width(8.dp)); Text(m.label, maxLines = 1)
            }
        }
    }
    TextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = Pill,
        placeholder = { Text(if (tab == "debloater") "搜索系统应用" else "搜索应用或包名") }, leadingIcon = { Icon(Sym.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Sym.Close, "清除") } },
        colors = TextFieldDefaults.colors(focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
    if (tab != "debloater") Row(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("all" to "全部", "user" to "用户", "system" to "系统").forEach { (id, label) -> Choice(filter == id, label) { filter = id } }
    }

    val q = query.trim().lowercase()
    fun match(name: String, pkg: String, sys: Boolean) = (tab == "debloater" || filter == "all" || (filter == "system") == sys) && (q.isEmpty() || name.lowercase().contains(q) || pkg.lowercase().contains(q))

    @Composable
    fun Stats(vararg items: Triple<Int, String, Color>) = Row(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (n, label, c) ->
            val (bg, fg) = tonal(c)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("$n", style = ty.titleLargeEmphasized, color = fg); Text(label, style = ty.labelMedium, color = fg.copy(.8f), maxLines = 1)
            }
        }
    }
    if (!loading && error.isEmpty()) when {
        tab == "permissions" -> apps.filter { it.permissions.isNotEmpty() }.let { l ->
            Stats(Triple(l.count { it.permissions.size >= 3 }, "严重", Red), Triple(l.count { it.permissions.size in 1..2 }, "警告", Yellow), Triple(l.size, "已标记", Blue))
        }
        tab == "surgeon" -> Stats(Triple(apps.size, "应用", Blue), Triple(apps.count { !it.isSystem }, "用户", Safe), Triple(apps.count { it.isSystem }, "系统", Gray))
        accepted -> Stats(Triple(sysApps.size, "系统应用", Blue), Triple(sysApps.count { !it.isInstalled }, "已移除", Red),
            Triple(sysApps.count { it.isInstalled && debloatRisk(uad[it.packageName]).label == "Recommended" }, "可安全卸载", Safe))
    }

    Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            tab == "debloater" && !accepted -> Column(Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                var k by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) { while (true) { delay(1800); k++ } }
                MorphBlob(listOf(MaterialShapes.Burst, MaterialShapes.SoftBurst), k, 120.dp, cs.errorContainer) { Icon(Sym.Warning, null, Modifier.size(52.dp), tint = cs.onErrorContainer) }
                Text("危险区域", style = ty.displaySmallEmphasized, modifier = Modifier.padding(top = 20.dp))
                Text("严重警告", style = ty.titleMediumEmphasized, color = cs.error, modifier = Modifier.padding(top = 4.dp))
                Text("卸载系统应用可能导致设备变砖。对于由此造成的无法开机或数据丢失，我不承担任何责任。", textAlign = TextAlign.Center,
                    style = ty.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp).widthIn(max = 420.dp))
                val h = ButtonDefaults.MediumContainerHeight
                Button({ scope.launch { try { tracker.requestElevated(); shizuku = true; accepted = true } catch (_: Exception) { toast.show("Shizuku 权限被拒绝", true) } } },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(min = h), shapes = ButtonDefaults.shapesFor(h), contentPadding = ButtonDefaults.contentPaddingFor(h),
                    colors = ButtonDefaults.buttonColors(cs.error, cs.onError)) {
                    Icon(Sym.GppMaybe, null, Modifier.size(ButtonDefaults.iconSizeFor(h))); Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(h)))
                    Text("我接受风险", style = ButtonDefaults.textStyleFor(h))
                }
            }
            loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                ContainedLoadingIndicator(Modifier.size(80.dp))
                AnimatedContent(LOADING_TEXTS[msgIdx], transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "load") {
                    Text(it, style = ty.titleSmallEmphasized, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                }
            }
            error.isNotEmpty() -> EmptyState(Sym.ErrorOutline, "扫描失败", error, Modifier.align(Alignment.Center))
            tab == "debloater" -> {
                val list = sysApps.filter { match(it.name, it.packageName, it.isSystem) }
                if (list.isEmpty()) EmptyState(Sym.SearchOff, "未找到结果", modifier = Modifier.align(Alignment.Center))
                else LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    itemsIndexed(list, key = { _, a -> a.packageName }) { i, a ->
                        val r = debloatRisk(uad[a.packageName])
                        GroupItem(i, list.size, Modifier.animateItem()) {
                            Row(Modifier.padding(14.dp).alpha(if (a.isInstalled) 1f else .6f), verticalAlignment = Alignment.CenterVertically) {
                                PkgIcon(tracker, a.packageName, 44)
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Text(a.name, style = ty.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(a.packageName, Modifier.clickable { copy(a.packageName) }, style = ty.labelSmall, fontFamily = FontFamily.Monospace,
                                        color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Tag(riskLabel(r.label), r.color); if (!a.isInstalled) Tag("已移除", Gray) }
                                }
                                if (processing == a.packageName) LoadingIndicator(Modifier.size(40.dp))
                                else if (a.isInstalled) FilledTonalIconButton({ toggle(a) }, enabled = processing == null, shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(cs.errorContainer, cs.onErrorContainer)) { Icon(Sym.DeleteForever, "卸载") }
                                else FilledTonalIconButton({ toggle(a) }, enabled = processing == null, shapes = IconButtonDefaults.shapes()) { Icon(Sym.Restore, "恢复") }
                            }
                        }
                    }
                }
            }
            else -> {
                val list = (if (tab == "permissions") apps.filter { it.permissions.isNotEmpty() } else apps).filter { match(it.name, it.packageName, it.isSystem) }
                if (list.isEmpty()) EmptyState(Sym.SearchOff, "未找到结果", modifier = Modifier.align(Alignment.Center))
                else LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    itemsIndexed(list, key = { _, a -> a.packageName }) { i, a ->
                        val open = tab == "permissions" && expanded == a.packageName; val r = riskLevel(a.permissions.size)
                        GroupItem(i, list.size, Modifier.animateItem(), selected = open,
                            onClick = if (tab == "permissions") ({ vm.haptic(H.LIGHT); expanded = if (open) null else a.packageName }) else null) {
                            Column {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PkgIcon(tracker, a.packageName, 44)
                                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(a.name, Modifier.weight(1f, false), style = ty.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (a.isSystem) { Spacer(Modifier.width(6.dp)); Tag("系统", Gray) }
                                        }
                                        Text(a.packageName, Modifier.clickable { copy(a.packageName) }, style = ty.labelSmall, fontFamily = FontFamily.Monospace,
                                            color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (tab == "permissions") {
                                        // Risk reads from the shape as well as the hue: calm circle, wary cookie, critical burst
                                        val (bg, fg) = tonal(r.color)
                                        Box(Modifier.size(44.dp).clip((when (r.label) { "CRITICAL" -> MaterialShapes.Burst; "WARNING" -> MaterialShapes.Cookie6Sided; else -> MaterialShapes.Circle }).toShape()).background(bg),
                                            contentAlignment = Alignment.Center) { Text("${a.permissions.size}", style = ty.titleMediumEmphasized, color = fg) }
                                    }
                                    else if (processing == a.packageName) LoadingIndicator(Modifier.size(40.dp))
                                    else FilledTonalIconButton({ extract(a.packageName) }, enabled = processing == null, shapes = IconButtonDefaults.shapes()) { Icon(Sym.Unarchive, "提取 APK") }
                                }
                                AnimatedVisibility(open, enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
                                    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        a.permissions.forEachIndexed { j, p ->
                                            Row(Modifier.fillMaxWidth().clip(colShape(j, a.permissions.size)).background(cs.surfaceContainerLowest).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Icon(permIcon(p), null, Modifier.size(20.dp), tint = r.color)
                                                Text(cleanPermName(p), Modifier.weight(1f).padding(horizontal = 12.dp), style = ty.labelLarge, fontFamily = FontFamily.Monospace)
                                                if (processing == a.packageName) LoadingIndicator(Modifier.size(32.dp))
                                                else FilledTonalButton({ revoke(a.packageName, p) }, enabled = processing == null, shapes = ButtonDefaults.shapes(),
                                                    contentPadding = ButtonDefaults.ExtraSmallContentPadding, modifier = Modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
                                                    colors = ButtonDefaults.filledTonalButtonColors(cs.errorContainer, cs.onErrorContainer)) { Text("撤销", style = ty.labelLarge) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    ConfirmDialog(confirm?.first, "确认", { confirm?.second?.let { a -> scope.launch { a() } } }) { confirm = null }
}

// ---------------- Sentinel (SentinelModal + workers/sentinel.worker.ts) ----------------

private const val SHARD_URL = "https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@data/sentinel/shard_"
private val BUCKETS = "0123456789abcdef".map { it.toString() }
private val KNOWN_THREATS = mapOf(
    "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f" to "EICAR 测试文件（无害）", "131f95c51cc819465fa1797f6ccacf9d494aaaff46fa3eac73ae63bb9ea99a42" to "EICAR 测试文件（Zip）",
    "44d88612fea8a8f36de82e1278abb02f" to "EICAR 测试文件（MD5）", "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8" to "Orion-Malware-Sim",
    "d41d8cd98f00b204e9800998ecf8427e" to "空字节载荷")
private val KNOWN_BAD_PACKAGES = setOf("com.android.htmlviewer", "com.metasploit.stage", "com.vanced.android.youtube", "org.malicious.trojan", "com.example.malware")
private val TRUSTED_DNS = setOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112", "208.67.222.222", "208.67.220.220")

data class Threat(val path: String, val hash: String, val threat: String)

/** Worker port: the 16 hash shards live in filesDir/sentinel instead of CacheStorage. */
// Note: parsed shards stay in memory for the sheet's lifetime, stream per bucket if the DB outgrows RAM
class SentinelDb(dir: File) {
    private val root = File(dir, "sentinel").apply { mkdirs() }
    private val maps = HashMap<String, Map<String, String>>()
    private fun shard(b: String) = File(root, "shard_$b.json")
    private fun load(b: String) = maps.getOrPut(b) {
        runCatching { json.parseToJsonElement(shard(b).readText()).jsonArray.associate { val o = it.jsonObject; o["h"]!!.jsonPrimitive.content to (o["n"]?.jsonPrimitive?.contentOrNull ?: "未知威胁") } }.getOrDefault(emptyMap())
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

private data class Info(val text: String, val color: Color, val desc: String, val icon: ImageVector)

private fun encryptionInfo(t: String) = t.uppercase().let {
    when {
        "WPA3" in it -> Info("安全", Safe, "最新安全标准。", Sym.EnhancedEncryption)
        "WPA2" in it -> Info("良好", Blue, "行业标准防护。", Sym.Lock)
        "WPA" in it -> Info("较弱", Caution, "已过时且存在漏洞。", Sym.LockOpen)
        "WEP" in it -> Info("不安全", Red, "极易被攻破。", Sym.NoEncryption)
        "OPEN" in it -> Info("开放", Red, "无加密，高风险。", Sym.LockOpen)
        else -> Info("未知", Gray, "无法确定状态。", Sym.HelpOutline)
    }
}

private fun dnsInfo(servers: List<String>) = when {
    servers.isEmpty() -> Info("未知", Gray, "未检测到 DNS 服务器。", Sym.Dns)
    servers.any { it in TRUSTED_DNS } -> Info("可信", Safe, "正在使用已知的安全 DNS。", Sym.VerifiedUser)
    else -> Info("未知", Caution, "正在使用默认或运营商 DNS。", Sym.Dns)
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
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Sym.Info, null, tint = Caution); Spacer(Modifier.width(8.dp)); Text("关于这些结果的说明", style = MaterialTheme.typography.titleSmall) }
            Text("哨兵的分析基于全球威胁数据库。自动化系统虽然强大，但有时也会误报，被标记的文件未必真的有害。强烈建议你在采取行动前先核实这些结果。", style = small)
            Text("如何二次核实：", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Sec("virustotal", "1. 使用 VirusTotal") {
                TextButton({ uri.openUri("https://www.virustotal.com/") }) { Icon(Sym.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("在线扫描") }
                Text("该服务使用 70 多个引擎分析文件。参考以下标准：", style = small)
                Text("0 个检出：通常安全。", style = small, color = Safe)
                Text("1-5 个检出：很可能是误报。", style = small, color = Caution)
                Text("5 个以上检出：高度警惕，视为危险文件。", style = small, color = Red)
            }
            Sec("source", "2. 考量来源") {
                Text("GitHub、F-Droid、Play 商店等官方渠道通常值得信赖，非官方网站的风险较高。", style = small)
                Text("为什么安全来源也会被标记？", style = MaterialTheme.typography.labelLarge)
                Text("杀毒引擎有时会采用“启发式”分析，即寻找可疑行为。需要高级权限（如 root）或会修改其他应用（如 ReVanced）的应用可能触发这类警告，即使它们完全无害。", style = small)
            }
            Sec("community", "3. 搜索社区") {
                Text("在网上搜索该文件的哈希值（可点复制按钮）。Reddit 等社区常有针对特定文件的讨论。", style = small)
            }
        }
    }
}

@Composable
fun SentinelSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val ctx = LocalContext.current; val tracker = vm.tracker; val scope = rememberCoroutineScope(); val clip = LocalClipboardManager.current; val toast = rememberSheetToast()
    val db = remember { SentinelDb(ctx.filesDir) }
    var view by remember { mutableStateOf("permissions") }; var perm by remember { mutableStateOf(tracker.permissionStatus()) }
    var dbStatus by remember { mutableStateOf("checking") }; var dbProgress by remember { mutableFloatStateOf(0f) }; var sigs by remember { mutableIntStateOf(0) }
    var scanType by remember { mutableStateOf("深度") }; var progress by remember { mutableFloatStateOf(0f) }; var current by remember { mutableStateOf("") }
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
        try { net = withContext(Dispatchers.IO) { tracker.networkSecurity() } } catch (_: Exception) { toast.show("网络扫描运行失败。", true) } finally { netScanning = false }
    }
    LaunchedEffect(view) { if (view == "network" && net == null) netScan() }
    BackHandler(view in setOf("results", "network", "scanning")) { job?.cancel(); view = "main" }

    fun reset() { job?.cancel(); threats = emptyList(); rapid = emptyList(); progress = 0f; expanded = null; view = "scanning" }
    suspend fun finish(t: List<Threat>, r: List<Pair<String, String>>) {
        progress = 100f; delay(500); threats = t; rapid = r; vm.haptic(if (t.isEmpty() && r.isEmpty()) H.SUCCESS else H.ERROR); view = "results"
    }
    fun rapidScan() {
        reset(); scanType = "快速"; current = "正在获取应用列表…"
        job = scope.launch {
            try {
                val apps = withContext(Dispatchers.IO) { tracker.installedPackages() }
                apps.forEachIndexed { i, (n, _) -> current = n; progress = i * 100f / apps.size; delay(50) }
                current = "正在收尾…"; finish(emptyList(), apps.filter { it.second.lowercase() in KNOWN_BAD_PACKAGES })
            } catch (e: CancellationException) { throw e } catch (_: Exception) { view = "results" }
        }
    }
    fun deepScan() {
        reset(); scanType = "深度"; current = "正在初始化扫描器…"
        job = scope.launch {
            val ticker = launch { var p = 0; while (p < 95) { delay(400); p++; progress = p.toFloat() } }
            val names = Channel<String>(64, BufferOverflow.DROP_OLDEST)
            val marquee = launch { current = "正在搜索文件…"; for (n in names) { current = n; delay(30) } }
            val found = ArrayList<Threat>()
            runCatching { tracker.scanDirectory { batch -> found += db.check(batch); batch.forEach { names.trySend(it.first.substringAfterLast('/')) } } }
            names.close(); marquee.cancel(); ticker.cancel(); current = "正在整理结果…"
            finish(found, emptyList())
        }
    }

    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SheetTitle("哨兵", Sym.GppGood, "威胁情报") }
        Tag(if (perm.storage) "传感器在线" else "传感器已锁定", if (perm.storage) Safe else Red, if (perm.storage) Sym.VerifiedUser else Sym.Lock)
        Spacer(Modifier.width(20.dp))
    }
    SheetToastHost(toast)
    AnimatedContent(view, transitionSpec = { (fadeIn() + expandVertically()) togetherWith fadeOut() }, label = "sentinel") { v ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val hero: @Composable (ImageVector, Color, RoundedPolygon) -> Unit = { i, col, s -> Blob(i, col, 112, s) }
            when (v) {
                "permissions" -> {
                    hero(Sym.Lock, Caution, MaterialShapes.Cookie9Sided)
                    Text("需要权限", style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text("哨兵需要权限扫描设备存储，以检测威胁。", textAlign = TextAlign.Center, color = c.onSurfaceVariant)
                    Button({
                        tracker.universalStorageIntent()?.let { runCatching { ctx.startActivity(it) } }
                            ?: legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) { Icon(Sym.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("授予存储权限") }
                    Text("这是一次性授权。", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
                "database" -> {
                    LoadingIndicator(Modifier.size(112.dp))
                    Text("正在同步威胁情报", style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(if (dbStatus == "updating") "正在下载最新威胁特征库…" else "正在连接哨兵网络…", color = c.onSurfaceVariant)
                    LinearWavyProgressIndicator({ dbProgress / 100f }, Modifier.fillMaxWidth())
                    if (dbStatus == "updating") Text("${dbProgress.toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                "main" -> {
                    hero(Sym.GppGood, Safe, MaterialShapes.SoftBurst)
                    Text("系统已受保护", style = MaterialTheme.typography.headlineSmallEmphasized)
                    ScanAction(Sym.Bolt, MaterialTheme.colorScheme.primary, "快速扫描", "检查已安装应用与 APK", MaterialShapes.Cookie4Sided, ::rapidScan)
                    ScanAction(Sym.Radar, Purple, "深度扫描", "分析文件存储与下载内容", MaterialShapes.Clover4Leaf, ::deepScan)
                    ScanAction(Sym.Wifi, SkyC, "网络哨兵", "连接与安全审计", MaterialShapes.Pill) { view = "network" }
                }
                "scanning" -> {
                    Box(contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator({ progress / 100f }, Modifier.size(180.dp))
                        Text("${progress.toInt()}%", style = MaterialTheme.typography.displaySmallEmphasized)
                    }
                    Text("${scanType}扫描…", style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(current, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
                "results" -> {
                    if (threats.isEmpty() && rapid.isEmpty()) {
                        hero(Sym.CheckCircle, Safe, MaterialShapes.Sunny)
                        Text("系统干净", style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text("未检测到威胁。", color = c.onSurfaceVariant)
                    } else {
                        Advisory()
                        Text("威胁报告", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLargeEmphasized)
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
                            Text("感染文件（深度扫描）", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, color = c.error)
                            threats.forEachIndexed { i, t ->
                                Item("file-$i", t.path.substringAfterLast('/'), t.threat, listOf("完整路径" to t.path) + (if (t.hash.isNotEmpty()) listOf("SHA-256 哈希" to t.hash) else emptyList()), "复制哈希", t.hash.ifEmpty { null })
                            }
                        }
                        if (rapid.isNotEmpty()) {
                            Text("可疑应用（快速扫描）", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, color = c.error)
                            rapid.forEachIndexed { i, (n, p) -> Item("app-$i", n, null, listOf("包名" to p), "复制包名", p) }
                        }
                    }
                    Button({ view = "main" }, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) { Text("返回") }
                }
                else -> when {
                    netScanning -> {
                        LoadingIndicator(Modifier.size(112.dp))
                        Text("正在审计网络…", style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text("正在检查连接完整性", color = c.onSurfaceVariant)
                    }
                    net == null -> {
                        hero(Sym.WifiOff, Red, MaterialShapes.Cookie9Sided)
                        Text("扫描失败", style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text("无法获取网络信息。", color = c.onSurfaceVariant)
                        Button({ netScan() }, shapes = ButtonDefaults.shapes()) { Icon(Sym.Refresh, null); Spacer(Modifier.width(8.dp)); Text("重新扫描") }
                    }
                    else -> {
                        val n = net!!; val enc = encryptionInfo(n.encryptionType); val dns = dnsInfo(n.dnsServers); val adb = n.adbEnabled || n.adbWifiEnabled
                        hero(Sym.Shield, Blue, MaterialShapes.Cookie12Sided)
                        Text("连接安全", style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text("对当前网络的实时分析", color = c.onSurfaceVariant)
                        SecurityCard("加密", enc.desc, enc.text, enc.color, enc.icon)
                        SecurityCard("DNS 完整性", dns.desc, dns.text, dns.color, dns.icon)
                        SecurityCard("强制门户", if (n.isCaptivePortal) "检测到网页登录页。" else "直接访问网络。", if (n.isCaptivePortal) "存在" else "无",
                            if (n.isCaptivePortal) Caution else Safe, if (n.isCaptivePortal) Sym.Login else Sym.Lan)
                        SecurityCard("ADB 看门狗", "开发者桥接状态", if (adb) "开启" else "关闭", if (adb) Red else Safe, Sym.Terminal)
                        SecurityCard("代理检测", "检测流量重定向", if (n.hasProxy) "开启" else "关闭", if (n.hasProxy) Red else Safe, Sym.AccountTree)
                        SecurityCard("VPN 状态", "检查是否有活动的 VPN 隧道", if (n.isVpnActive) "开启" else "关闭", if (n.isVpnActive) Blue else Safe, Sym.VpnLock)
                        Button({ view = "main" }, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) { Text("返回") }
                    }
                }
            }
        }
    }
    Text(if (sigs > 0) "%,d 条威胁特征已就绪".format(sigs) else "...", Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = c.onSurfaceVariant)
}
