package com.orion.store.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.orion.store.data.*
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import android.os.Build
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalVm = staticCompositionLocalOf<MainViewModel> { error("vm") }
val LocalS = compositionLocalOf { Settings() }
val LocalD = compositionLocalOf { DataState() }

fun hex(s: String?, fb: Color = Color(0xFF64748B)) = runCatching { Color(android.graphics.Color.parseColor(s!!.trim())) }.getOrDefault(fb)

fun hue(seed: String): Float { var h = 0; seed.forEach { h = (h shl 5) - h + it.code }; return (kotlin.math.abs(h) % 360).toFloat() }
fun hsl(h: Float, s: Float, l: Float, a: Float = 1f) = Color.hsl(h, s, l, a)

private val FA: Map<String, ImageVector> = mapOf(
    "arrow-up" to Sym.ArrowUpward, "bolt" to Sym.Bolt, "book-open" to Sym.MenuBook, "box-archive" to Sym.Inventory2,
    "briefcase" to Sym.Work, "bug" to Sym.BugReport, "check-double" to Sym.DoneAll, "circle-check" to Sym.CheckCircle,
    "code" to Sym.Code, "comments" to Sym.Forum, "download" to Sym.Download, "dragon" to Sym.SportsEsports,
    "file-signature" to Sym.Draw, "film" to Sym.Movie, "headphones" to Sym.Headphones, "heart" to Sym.Favorite,
    "heart-pulse" to Sym.MonitorHeart, "layer-group" to Sym.Layers, "photo-film" to Sym.PermMedia, "robot" to Sym.SmartToy,
    "rocket" to Sym.RocketLaunch, "shield-cat" to Sym.Shield, "terminal" to Sym.Terminal, "toolbox" to Sym.HomeRepairService,
    "user-secret" to Sym.VisibilityOff, "user-shield" to Sym.AdminPanelSettings, "wand-magic-sparkles" to Sym.AutoAwesome,
    "question" to Sym.HelpOutline, "circle-question" to Sym.HelpOutline, "shield-halved" to Sym.Security, "lock" to Sym.Lock,
    "gamepad" to Sym.SportsEsports, "music" to Sym.MusicNote, "camera" to Sym.PhotoCamera, "globe" to Sym.Public,
    "star" to Sym.Star, "gear" to Sym.Settings, "cog" to Sym.Settings, "mobile" to Sym.PhoneAndroid,
    "mobile-screen" to Sym.PhoneAndroid, "desktop" to Sym.Computer, "tv" to Sym.Tv, "user" to Sym.Person,
    "users" to Sym.Group, "info" to Sym.Info, "circle-info" to Sym.Info, "triangle-exclamation" to Sym.Warning,
    "wifi" to Sym.Wifi, "key" to Sym.Key, "trash" to Sym.Delete, "box" to Sym.Inventory2, "fire" to Sym.LocalFireDepartment,
    "github" to Sym.Code, "palette" to Sym.Palette, "brain" to Sym.Psychology, "newspaper" to Sym.Newspaper,
    "wallet" to Sym.AccountBalanceWallet, "message" to Sym.Chat, "video" to Sym.Videocam, "image" to Sym.Image,
    "bell" to Sym.Notifications, "cloud" to Sym.Cloud, "folder" to Sym.Folder, "magnifying-glass" to Sym.Search,
    "search" to Sym.Search, "crown" to Sym.EmojiEvents, "trophy" to Sym.EmojiEvents, "medal" to Sym.MilitaryTech,
    "gift" to Sym.CardGiftcard, "mug-hot" to Sym.Coffee, "coffee" to Sym.Coffee, "cubes" to Sym.ViewInAr, "graduation-cap" to Sym.School, "sparkles" to Sym.AutoAwesome,
)
/** Font Awesome class names from remote config ("fas fa-bolt", "fa-bolt") mapped onto Material Symbols. */
fun faIcon(name: String?, fb: ImageVector = Sym.Apps): ImageVector =
    name?.split(' ')?.map { it.removePrefix("fa-") }?.firstNotNullOfOrNull { FA[it] } ?: fb

/** Row padding while compact mode is on: about half the M3 list item default. Rows always pass centered alignment:
 *  M3 top-aligns taller rows, which left the icon and download button above the middle. */
val CompactRowPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
/** Compact rows size to their content instead of M3's fixed list-item minimum, which left empty space under short rows on some
 *  screens. M3 only applies its minimum when the row has none of its own, so this sets one just above the padding. */
val CompactRowHeight = Modifier.heightIn(min = CompactRowPadding.calculateTopPadding() + CompactRowPadding.calculateBottomPadding() + 1.dp)

private val CATEGORY_ICONS: List<Pair<List<String>, () -> ImageVector>> = listOf(
    listOf("video", "stream", "movie", "film") to { Sym.Movie }, listOf("music", "audio") to { Sym.MusicNote },
    listOf("photo", "camera") to { Sym.PhotoCamera }, listOf("graphic", "creative", "design") to { Sym.Draw },
    listOf("media") to { Sym.PermMedia }, listOf("social", "communicat", "chat", "messag") to { Sym.Forum },
    listOf("educat", "learn", "study") to { Sym.School }, listOf("privacy") to { Sym.Shield },
    listOf("security", "antivirus") to { Sym.Security }, listOf("activat", "licen") to { Sym.Key },
    listOf("develop", "code", "programm") to { Sym.Code }, listOf("entertain") to { Sym.Celebration },
    listOf("productiv", "office", "notes") to { Sym.Work }, listOf("wallpaper") to { Sym.Wallpaper },
    listOf("custom", "theme", "launcher") to { Sym.Palette }, listOf("health", "fitness") to { Sym.MonitorHeart },
    listOf("emulat", "gam") to { Sym.SportsEsports }, listOf("sport") to { Sym.EmojiEvents },
    listOf("monitor") to { Sym.QueryStats }, listOf("optimi", "clean", "speed") to { Sym.Speed },
    listOf("novel", "read", "book", "comic", "manga") to { Sym.MenuBook }, listOf("browser", "web") to { Sym.Public },
    listOf("automat") to { Sym.SmartToy }, listOf("network", "vpn", "proxy") to { Sym.Lan },
    listOf("file", "recover", "storage") to { Sym.Folder }, listOf("install") to { Sym.InstallMobile },
    listOf("system", "root", "mod") to { Sym.Memory }, listOf("util", "tool") to { Sym.HomeRepairService },
)
private val categoryIcons = HashMap<String, ImageVector>()

/** Glyph for the catalog's free-form categories ("Utility/Networking", "Health & Fitness"): the first part that names something known wins. */
fun categoryIcon(category: String): ImageVector = categoryIcons.getOrPut(category) {
    category.lowercase().split('/', '&', ',').firstNotNullOfOrNull { part -> CATEGORY_ICONS.firstOrNull { (keys, _) -> keys.any { it in part } }?.second?.invoke() } ?: Sym.Category
}

/** Images whose proxy and direct fetch both failed this session: later screens go straight to the fallback instead of waiting out two timeouts again. */
val deadImages: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

/** Remote icon, drawn as the image itself (no backing box). Proxy at a fixed 256px so every size shares one cache entry, then raw URL, then a hued monogram. */
@Composable
fun RemoteIcon(url: String, name: String, size: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(size * .24f)) {
    var stage by remember(url) { mutableIntStateOf(if (url.isEmpty() || url in deadImages) 2 else 0) }
    val proxied = remember(url) { optimizedImageUrl(url, 256, 256) }
    val sk = MaterialTheme.colorScheme.surfaceContainerHighest
    val skeleton = remember(sk) { androidx.compose.ui.graphics.painter.ColorPainter(sk) }
    if (stage < 2) AsyncImage(if (stage == 0) proxied else url, name, modifier.size(size).clip(shape), placeholder = skeleton, contentScale = ContentScale.Crop, onError = { if (++stage == 2) deadImages += url })
    else {
        val h = remember(name) { hue(name) }; val light = MaterialTheme.colorScheme.isLight
        Box(modifier.size(size).clip(shape).background(hsl(h, .5f, if (light) .87f else .28f)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), fontSize = (size.value * .42f).sp, fontWeight = FontWeight.Black, color = hsl(h, .6f, if (light) .36f else .8f))
        }
    }
}

// region motion
/** Shared-element scope for icon hand-off between store cards and the detail page. */
val LocalShared = staticCompositionLocalOf<SharedTransitionScope?> { null }

@Composable
private fun Modifier.sharedIcon(key: String?, visible: Boolean): Modifier {
    val sc = LocalShared.current
    if (sc == null || key == null) return this
    return with(sc) { this@sharedIcon.sharedElementWithCallerManagedVisibility(rememberSharedContentState(key), visible) }
}

/** Tablets and landscape: centre the page in a readable column instead of stretching rows across the whole screen. No-op on phones. */
fun Modifier.readableWidth() = wrapContentWidth().widthIn(max = 840.dp)

/** Squash-on-press with an expressive spatial spring, plus ripple, clipped to [shape]. */
@Composable
fun Modifier.springClick(shape: Shape = RoundedCornerShape(20.dp), onLongClick: (() -> Unit)? = null, src: MutableInteractionSource = remember { MutableInteractionSource() }, onClick: () -> Unit): Modifier {
    val pressed by src.collectIsPressedAsState(); val vm = LocalVm.current
    val focused by src.collectIsFocusedAsState()
    // D-pad and keyboard focus (TV remotes) lifts the card a little; touch never focuses, so phones never see it
    val k by animateFloatAsState(if (pressed) .94f else if (focused) 1.04f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    // Every card and tile in the app routes through here, so this is the one place that gives taps their tick.
    return graphicsLayer { scaleX = k; scaleY = k }.focusRing(src, shape).clip(shape).combinedClickable(src, ripple(), onLongClick = onLongClick, onClick = { vm.haptic(H.SELECTION); onClick() })
}

/** A primary ring around whatever has D-pad or keyboard focus, so a TV remote always shows where it is. */
@Composable
fun Modifier.focusRing(src: androidx.compose.foundation.interaction.InteractionSource, shape: Shape): Modifier {
    val focused by src.collectIsFocusedAsState()
    val w by animateDpAsState(if (focused) 3.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "ring")
    return if (w > 0.dp) border(w, MaterialTheme.colorScheme.primary, shape) else this
}

/** Rise-and-fade entrance for the first screenful, once per item (survives lazy recycling), staggered by [index].
 *  Items further down appear as-is, so flinging never shows rows sliding in. */
@Composable
fun Modifier.reveal(index: Int = 0): Modifier {
    if (index > 8) return this
    var done by rememberSaveable { mutableStateOf(false) }
    val p = remember { Animatable(if (done) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { if (!done) { kotlinx.coroutines.delay(index.coerceIn(0, 8) * 45L); p.animateTo(1f, spec); done = true } }
    return graphicsLayer { alpha = p.value.coerceIn(0f, 1f); translationY = (1f - p.value) * 28.dp.toPx() }
}
// endregion

private val iconColors = HashMap<String, Color>()

/** Signature color of an app icon: the dominant saturated hue, averaged inside its bucket. Null for monochrome icons. */
@Composable
fun iconColor(app: AppItem): Color? {
    val ctx = LocalContext.current
    val c by produceState(iconColors[app.icon], app.icon) {
        if (value != null || app.icon.isEmpty()) return@produceState
        val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(optimizedImageUrl(app.icon, 256, 256)).allowHardware(false).build())
        val bmp = (r as? SuccessResult)?.image?.toBitmap() ?: return@produceState
        value = withContext(Dispatchers.Default) { dominant(bmp) }?.also { iconColors[app.icon] = it }
    }
    return c
}

private fun dominant(src: android.graphics.Bitmap): Color? {
    val soft = if (Build.VERSION.SDK_INT >= 26 && src.config == android.graphics.Bitmap.Config.HARDWARE) src.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else src
    val b = android.graphics.Bitmap.createScaledBitmap(soft, 24, 24, true)
    val w = FloatArray(12); val rgb = Array(12) { FloatArray(3) }; val hsv = FloatArray(3)
    for (x in 0 until 24) for (y in 0 until 24) {
        val p = b.getPixel(x, y); if (android.graphics.Color.alpha(p) < 160) continue
        android.graphics.Color.colorToHSV(p, hsv); val k = hsv[1] * hsv[2]; if (k < .18f) continue
        val i = (hsv[0] / 30f).toInt().coerceIn(0, 11)
        w[i] += k; rgb[i][0] += android.graphics.Color.red(p) * k; rgb[i][1] += android.graphics.Color.green(p) * k; rgb[i][2] += android.graphics.Color.blue(p) * k
    }
    val i = w.indices.maxBy { w[it] }; if (w[i] < 4f) return null
    return Color(rgb[i][0] / w[i] / 255f, rgb[i][1] / w[i] / 255f, rgb[i][2] / w[i] / 255f)
}

enum class CardState { NONE, INSTALLED, UPDATE, DOWNLOADING, READY }

@Composable
fun cardState(app: AppItem): Pair<CardState, Float> {
    val s = LocalS.current; val d = LocalD.current
    val local = s.installedVersions[app.id]
    return when {
        app.id in d.activeDownloads -> CardState.DOWNLOADING to (d.downloadProgress[app.id] ?: 0f)
        app.id in d.readyToInstall -> CardState.READY to 1f
        hasAvailableUpdate(app, local, s.streamOf(app.id)) -> CardState.UPDATE to 0f
        !local.isNullOrEmpty() -> CardState.INSTALLED to 0f
        else -> CardState.NONE to 0f
    }
}

/** App icon. With [state], download progress wraps the icon as a wavy ring and install state sits in a cut-out corner badge. */
@Composable
fun AppIcon(app: AppItem, size: Dp, modifier: Modifier = Modifier, state: Boolean = false, from: String? = null, target: Boolean = false) {
    val vm = LocalVm.current
    val visible = if (target) vm.selectedApp?.id == app.id else !(vm.selectedApp?.id == app.id && vm.openFrom == from)
    val shared = Modifier.sharedIcon(from?.let { "$it|${app.id}" }, visible)
    if (!state) return RemoteIcon(app.icon, app.name, size, modifier.then(shared))
    val (st, p) = cardState(app)
    val cs = MaterialTheme.colorScheme
    val inset by animateDpAsState(if (st == CardState.DOWNLOADING) size * .2f else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "inset")
    val round by animateIntAsState(if (st == CardState.DOWNLOADING) 50 else 24, MaterialTheme.motionScheme.fastSpatialSpec(), label = "round")
    Box(modifier.size(size)) {
        RemoteIcon(app.icon, app.name, size - inset * 2, Modifier.align(Alignment.Center).then(shared), RoundedCornerShape(round))
        if (st == CardState.DOWNLOADING) {
            val prog by animateFloatAsState(p / 100f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dl")
            if (p <= 0f) CircularWavyProgressIndicator(Modifier.fillMaxSize()) else CircularWavyProgressIndicator({ prog }, Modifier.fillMaxSize())
        }
        val badge = when (st) {
            CardState.READY -> Triple(Sym.InstallMobile, cs.primary, cs.onPrimary)
            CardState.UPDATE -> Triple(Sym.ArrowUpward, cs.tertiaryContainer, cs.onTertiaryContainer)
            CardState.INSTALLED -> Triple(Sym.Check, cs.secondaryContainer, cs.onSecondaryContainer)
            else -> null
        }
        badge?.let { (ic, bg, fg) ->
            Box(Modifier.align(Alignment.BottomEnd).offset(size * .08f, size * .08f).size((size * .36f).coerceIn(16.dp, 28.dp)).clip(CircleShape)
                .background(cs.surface).padding(2.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Icon(ic, null, Modifier.fillMaxSize(.7f), tint = fg)
            }
        }
    }
}

/** One-line install/update status for list rows; null when the app is not on the device. */
@Composable
fun statusText(app: AppItem): Pair<String, Color>? {
    val (st, p) = cardState(app); val cs = MaterialTheme.colorScheme
    val local = LocalS.current.installedVersions[app.id]
    return when (st) {
        CardState.DOWNLOADING -> (if (p > 0f) "正在下载 ${p.toInt()}%" else "正在准备下载") to cs.primary
        CardState.READY -> "待安装" to cs.primary
        CardState.UPDATE -> "更新 · v${preferredVersion(app, LocalS.current.streamOf(app.id)).removePrefix("v")}" to cs.tertiary
        CardState.INSTALLED -> "已安装 · v${local.orEmpty().removePrefix("v")}" to cs.onSurfaceVariant
        CardState.NONE -> null
    }
}

/** Section heading with an optional trailing arrow action (Play-style "see all"). */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onMore: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) =
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        action?.invoke()
        onMore?.let { FilledTonalIconButton(it, shapes = IconButtonDefaults.shapes()) { Icon(Sym.ArrowForward, "查看全部 $title") } }
    }

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String? = null, modifier: Modifier = Modifier) =
    Column(modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
        body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
    }

fun gradient(colors: List<String>, fb: List<Color>) = Brush.linearGradient(colors.map { hex(it) }.ifEmpty { fb }.let { if (it.size == 1) it + it else it })

@Composable
fun SheetTitle(title: String, icon: ImageVector? = null, subtitle: String? = null) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Box(Modifier.size(44.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(it, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }

/** Animated close for in-sheet buttons: slides the sheet down, then runs the sheet's onDismiss. */
val LocalSheetClose = staticCompositionLocalOf<() -> Unit> { {} }

/** Standard sheet: skip partial state, edge-to-edge, scrollable content supplied by caller. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrionSheet(onDismiss: () -> Unit, dismissible: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val canHide by rememberUpdatedState(dismissible)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || canHide }); val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val close: () -> Unit = remember { { scope.launch { state.hide() }.invokeOnCompletion { dismiss() } } }
    ModalBottomSheet(onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        CompositionLocalProvider(LocalSheetClose provides close) { content() }
    }
}

val Pill = CircleShape

/** Segmented list corners: 20dp outer, 4dp inner; a selected row morphs fully round. */
@Composable
fun GroupItem(i: Int, n: Int, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val top by animateDpAsState(if (i == 0 || selected) 20.dp else 4.dp, spec, label = "gt")
    val bottom by animateDpAsState(if (i == n - 1 || selected) 20.dp else 4.dp, spec, label = "gb")
    val shape = RoundedCornerShape(top, top, bottom, bottom)
    val color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val m = modifier.fillMaxWidth().padding(bottom = 2.dp)
    if (onClick != null) Surface(onClick, m, shape = shape, color = color) { content() } else Surface(m, shape = shape, color = color) { content() }
}

/** Side-by-side segments: the first rounds its left end, the last its right end. Stacked lists use colShape. */
fun rowShape(i: Int, n: Int) = RoundedCornerShape(if (i == 0) 20.dp else 4.dp, if (i == n - 1) 20.dp else 4.dp, if (i == n - 1) 20.dp else 4.dp, if (i == 0) 20.dp else 4.dp)

/** Medium (56dp) M3E button: the screen's completion action. */
@Composable
fun CtaButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, tonal: Boolean = false, loading: Boolean = false) {
    val h = ButtonDefaults.MediumContainerHeight
    val content: @Composable RowScope.() -> Unit = {
        if (loading) LoadingIndicator(Modifier.size(ButtonDefaults.iconSizeFor(h) + 8.dp), color = LocalContentColor.current)
        else icon?.let { Icon(it, null, Modifier.size(ButtonDefaults.iconSizeFor(h))) }
        if (loading || icon != null) Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(h)))
        Text(text, style = ButtonDefaults.textStyleFor(h), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val m = modifier.fillMaxWidth().heightIn(min = h)
    if (tonal) FilledTonalButton(onClick, modifier = m, enabled = enabled && !loading, shapes = ButtonDefaults.shapesFor(h), contentPadding = ButtonDefaults.contentPaddingFor(h), content = content)
    else Button(onClick, modifier = m, enabled = enabled && !loading, shapes = ButtonDefaults.shapesFor(h), contentPadding = ButtonDefaults.contentPaddingFor(h), content = content)
}

/** Live morph between two normalized MaterialShapes polygons. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = morph.toPath(progress); p.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(p.asComposePath())
    }
}

/** Hero blob: morphs to shapes[index] with the expressive slow spatial spring and a quarter turn. */
@Composable
fun MorphBlob(shapes: List<RoundedPolygon>, index: Int, size: Dp, color: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var from by remember { mutableIntStateOf(index) }; var to by remember { mutableIntStateOf(index) }
    val prog = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(index) { if (index != to) { from = to; to = index; prog.snapTo(0f); prog.animateTo(1f, spec) } }
    val morph = remember(from, to) { Morph(shapes[from % shapes.size], shapes[to % shapes.size]) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = (to + prog.value - 1) * 90f }.clip(MorphShape(morph, prog.value)).background(color))
        content()
    }
}

/** Pastel container + legible icon tone of any accent, so fixed hues still read as M3 tonal pairs in light and dark. */
@Composable
fun tonal(c: Color): Pair<Color, Color> {
    val hsl = FloatArray(3).also { androidx.core.graphics.ColorUtils.colorToHSL(c.toArgb(), it) }
    val light = MaterialTheme.colorScheme.isLight
    return Color.hsl(hsl[0], (hsl[1] * .7f).coerceAtMost(.75f), if (light) .88f else .26f) to Color.hsl(hsl[0], hsl[1].coerceAtMost(.8f), if (light) .3f else .82f)
}

/** Settings-style 40dp circle in the accent's tonal pair. */
@Composable
fun TonalIcon(icon: ImageVector, c: Color) {
    val (bg, fg) = tonal(c)
    Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = fg) }
}

/** Device tilt from the gravity sensor, low-pass filtered, in roughly -1..1 per axis. Read it inside graphicsLayer/draw lambdas so it only redraws. */
@Composable
fun rememberTilt(): State<Offset> {
    val ctx = LocalContext.current; val off = LocalS.current.disableAnimations
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(off) {
        val sm = ctx.getSystemService(android.hardware.SensorManager::class.java)
        val g = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
        if (off || sm == null || g == null) return@DisposableEffect onDispose { }
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val t = tilt.value; val x = (-e.values[0] / 9.81f).coerceIn(-1f, 1f); val y = (e.values[1] / 9.81f - .65f).coerceIn(-1f, 1f)
                tilt.value = Offset(t.x + (x - t.x) * .12f, t.y + (y - t.y) * .12f)
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) = Unit
        }
        sm.registerListener(l, g, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(l) }
    }
    return tilt
}

/** Theme switches as a circular reveal: the old frame is snapshotted and wiped away from the tap point. */
class ThemeReveal(internal val layer: androidx.compose.ui.graphics.layer.GraphicsLayer, private val scope: kotlinx.coroutines.CoroutineScope,
                  private val spec: androidx.compose.animation.core.AnimationSpec<Float>, private val instant: Boolean) {
    internal var snap by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    internal var origin by mutableStateOf(Offset.Zero)
    internal val progress = Animatable(0f)
    fun run(at: Offset, change: () -> Unit) {
        if (instant || snap != null) return change()
        scope.launch {
            // A hung or failing capture (seen on some Samsung and Xiaomi GPUs) used to swallow the change itself; the change now always lands.
            snap = runCatching { kotlinx.coroutines.withTimeoutOrNull(120) { layer.toImageBitmap() } }.getOrNull()
            origin = at; change(); progress.snapTo(0f)
            if (snap != null) progress.animateTo(1f, spec)
            snap = null
        }
    }
}

val LocalThemeReveal = staticCompositionLocalOf<ThemeReveal?> { null }

@Composable
fun ThemeRevealHost(content: @Composable () -> Unit) {
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer(); val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>(); val instant = LocalS.current.disableAnimations
    val reveal = remember(layer, instant) { ThemeReveal(layer, scope, spec, instant) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }) { CompositionLocalProvider(LocalThemeReveal provides reveal, content = content) }
        reveal.snap?.let { img ->
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val r = reveal.progress.value * kotlin.math.hypot(size.width, size.height) * 1.05f
                val hole = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(reveal.origin, r)) }
                clipPath(hole, androidx.compose.ui.graphics.ClipOp.Difference) { drawImage(img) }
            }
        }
    }
}

/** Center of this element in root coordinates, for reveal origins. */
fun Modifier.trackCenter(into: MutableState<Offset>) = onGloballyPositioned { into.value = it.boundsInRoot().center }
