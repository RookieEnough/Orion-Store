package com.orion.store.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.CircleShape

/** A refresh indicator: a pixel character ([frames]; eyes close on pull via the [closed] row swap) or an M3 Expressive [shape] ([loader] = the M3 LoadingIndicator). */
class PtrCharacter(val id: String, val name: String, val caption: String, val frames: List<String>, val palette: Map<Char, Long>, val tint: Pair<Long, Long>,
                   val closed: Pair<Int, String>? = null, val shape: RoundedPolygon? = null, val loader: Boolean = false) {
    val pixel get() = frames.isNotEmpty()
}

val PTR = listOf(
    PtrCharacter("morph", "变形", "M3 形状变形", emptyList(), emptyMap(), 0L to 0L, loader = true),
    PtrCharacter("sunny", "阳光", "旋转着刷新", emptyList(), emptyMap(), 0L to 0L, shape = MaterialShapes.Sunny),
    PtrCharacter("cookie", "曲奇", "滚到位", emptyList(), emptyMap(), 0L to 0L, shape = MaterialShapes.Cookie9Sided),
    PtrCharacter("flower", "花朵", "下拉即绽放", emptyList(), emptyMap(), 0L to 0L, shape = MaterialShapes.Flower),
    PtrCharacter("cat", "像素猫", "经典 Orion 眨眼", listOf("CAT"), mapOf('a' to 0xFFC2410C, 'b' to 0xFFFB923C, 'c' to 0xFFF8FAFC, 'd' to 0xFF1F2937, 'e' to 0xFFF9A8D4), 0x3DFB923CL to 0x24EC4899L, closed = 5 to ".bbbbddbbddbbb."),
    PtrCharacter("dog", "像素狗", "快乐小帮手", listOf("DOG"), mapOf('a' to 0xFF7C2D12, 'b' to 0xFFD97706, 'c' to 0xFFFEF3C7, 'd' to 0xFF111827, 'e' to 0xFFEF4444), 0x3DF59E0BL to 0x2122C55EL, closed = 4 to ".abbbbbbbba."),
    PtrCharacter("pokeball", "精灵球", "准备捕捉的刷新", listOf("POKEBALL_REST", "POKEBALL_SPARK"), mapOf('a' to 0xFF111827, 'b' to 0xFFB91C1C, 'c' to 0xFFEF4444, 'd' to 0xFF111827, 'e' to 0xFFF8FAFC, 'f' to 0xFFE5E7EB, 'g' to 0xFFFDE68A, 'h' to 0xFFFFFFFF), 0x3DF87171L to 0x29FFFFFFL),
    PtrCharacter("shield", "队长盾牌", "掷出即刷新", listOf("SHIELD", "SHIELD_REST"), mapOf('b' to 0xFFDC2626, 'c' to 0xFFF1F5F9, 'd' to 0xFF1D4ED8, 'e' to 0xFFFFFFFF, 'x' to 0xFF94A3B8), 0x3DDC2626L to 0x2E1D4ED8L),
    PtrCharacter("batman", "蝙蝠侠", "面罩警报刷新", listOf("BATMAN_FACE_OPEN", "BATMAN_FACE_CLOSED"), mapOf('b' to 0xFF05070B, 'c' to 0xFFF8FAFC, 'd' to 0xFFF1E4C9, 'e' to 0xFF111827), 0x3DFACC15L to 0x42111827L),
    PtrCharacter("owl", "像素猫头鹰", "夜间刷新", listOf("OWL"), mapOf('b' to 0xFF0EA5E9, 'c' to 0xFFFEF3C7, 'd' to 0xFF111827, 'e' to 0xFFF59E0B), 0x3D0EA5E9L to 0x24A855F7L, closed = 4 to ".bcccbbcccb."),
    PtrCharacter("robot", "像素机器人", "机甲刷新", listOf("ROBOT"), mapOf('a' to 0xFF334155, 'b' to 0xFF94A3B8, 'c' to 0xFF22D3EE, 'e' to 0xFFF59E0B), 0x3B06B6D4L to 0x296366F1L, closed = 4 to ".abbbbbbbba."),
    PtrCharacter("ghost", "像素幽灵", "吓你一下但很实用", listOf("GHOST"), mapOf('b' to 0xFFF8FAFC, 'c' to 0xFF111827, 'e' to 0xFFF472B6), 0x388B5CF6L to 0x24F472B6L, closed = 3 to ".bbbbbbbbbb."),
    PtrCharacter("kitty", "像素小猫", "蝴蝶结与胡须", listOf("KITTY"), mapOf('a' to 0xFF111827, 'b' to 0xFFF8FAFC, 'c' to 0xFFEF4444, 'd' to 0xFFFACC15), 0x38F43F5EL to 0x2EFBCFE8L, closed = 5 to "abbbbbbbbbba"),
    PtrCharacter("bunny", "像素兔", "蹦蹦跳跳地刷新", listOf("BUNNY"), mapOf('a' to 0xFF111827, 'b' to 0xFFF8FAFC, 'c' to 0xFFF9A8D4, 'd' to 0xFFEC4899, 'e' to 0xFFFDA4AF), 0x33EC4899L to 0x24A78BFAL, closed = 6 to ".bbbbbbbbbb."),
)
fun ptr(id: String) = PTR.firstOrNull { it.id == id } ?: PTR[0]
fun PtrCharacter.brush() = Brush.linearGradient(listOf(Color(tint.first), Color(tint.second)))

/** Each face is baked once into a 1px-per-cell bitmap: the picker shows eleven faces, and a drawRect per cell made its first frame stutter. */
private val faces = HashMap<String, ImageBitmap>()

private fun bake(c: PtrCharacter, grid: String, closed: Boolean): ImageBitmap {
    val base = PIXEL_GRIDS[grid].orEmpty()
    val rows = c.closed?.takeIf { closed && it.first < base.size }?.let { (i, r) -> base.toMutableList().also { it[i] = r } } ?: base
    val bmp = Bitmap.createBitmap(rows.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1, rows.size.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> c.palette[ch]?.let { bmp.setPixel(x, y, it.toInt()) } } }
    return bmp.asImageBitmap()
}

@Composable
fun PixelFace(c: PtrCharacter, size: Dp, frame: Int = 0, modifier: Modifier = Modifier, closed: Boolean = false) {
    val grid = c.frames[frame % c.frames.size]
    val img = faces.getOrPut("${c.id}/$grid/$closed") { bake(c, grid, closed) }
    Canvas(modifier.size(size)) {
        val px = this.size.minDimension / maxOf(img.width, img.height); val w = img.width * px; val h = img.height * px
        drawImage(img, dstOffset = IntOffset(((this.size.width - w) / 2).roundToInt(), ((this.size.height - h) / 2).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()), filterQuality = FilterQuality.None)
    }
}

/** The indicator art at rest: pixel face on its tint, or the expressive shape. Used by the picker and settings. */
@Composable
fun PtrBadge(c: PtrCharacter, size: Dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    when {
        c.loader -> Box(modifier.size(size), contentAlignment = Alignment.Center) { ContainedLoadingIndicator({ .7f }, Modifier.size(size)) }
        c.shape != null -> Box(modifier.size(size).clip(c.shape.toShape()).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Sym.Refresh, null, Modifier.size(size * .42f), tint = cs.onPrimaryContainer)
        }
        else -> Box(modifier.size(size).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.surfaceContainerHigh).background(c.brush()), contentAlignment = Alignment.Center) {
            PixelFace(c, size * .64f)
        }
    }
}

/**
 * Pull indicator, ported from the original native view: slides down under the status-bar slot with the pull, eyes close while pulling,
 * and while refreshing the character bobs, the Poke Ball shakes on its spark frame and the shield whips a 540° spin.
 * Expressive shapes spin with the pull; "Morph" is the M3 LoadingIndicator, determinate while pulling.
 * Motion reads a frame clock inside graphicsLayer, so it never recomposes per frame.
 */
@Composable
fun PtrIndicator(state: PullToRefreshState, refreshing: Boolean, characterId: String, modifier: Modifier = Modifier) {
    val c = ptr(characterId); val cs = MaterialTheme.colorScheme
    val frac = state.distanceFraction
    if (frac <= 0f && !refreshing) return
    val top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { now = it } }
    var spinFrom by remember { mutableLongStateOf(0L) }
    LaunchedEffect(refreshing) { spinFrom = if (refreshing) withFrameMillis { it } else 0L }
    val pulling = !refreshing && frac > .08f
    Box(modifier.padding(top = top + 12.dp).size(60.dp).graphicsLayer {
        val f = if (refreshing) 1f else frac.coerceIn(0f, 1.3f)
        alpha = f.coerceIn(0f, 1f); translationY = (f.coerceAtMost(1f) - 1f) * 48.dp.toPx()
        scaleX = .6f + .4f * f.coerceAtMost(1.1f); scaleY = scaleX
    }, contentAlignment = Alignment.Center) {
        when {
            c.loader -> if (refreshing) ContainedLoadingIndicator(Modifier.fillMaxSize()) else ContainedLoadingIndicator({ frac.coerceIn(0f, 1f) }, Modifier.fillMaxSize())
            c.shape != null -> Box(Modifier.fillMaxSize().graphicsLayer {
                rotationZ = if (refreshing) ((now - spinFrom) % 900L) / 900f * 360f else frac * 270f
                val pulse = if (refreshing) 1f + .06f * kotlin.math.sin((now - spinFrom) / 140f) else 1f; scaleX = pulse; scaleY = pulse
            }.clip(c.shape.toShape()).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Sym.Refresh, null, Modifier.size(24.dp).graphicsLayer { rotationZ = -frac * 270f }, tint = cs.onPrimaryContainer)
            }
            else -> Box(Modifier.fillMaxSize().clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.surfaceContainerHigh).background(c.brush()), contentAlignment = Alignment.Center) {
                val poke = c.id == "pokeball"; val shield = c.id == "shield"
                PixelFace(c, 38.dp, if (poke && refreshing) 1 else 0, Modifier.graphicsLayer {
                    val px = 4.dp.toPx()
                    translationY = if (refreshing) kotlin.math.sin((now - spinFrom) / 110f) * px * .55f else 0f
                    translationX = when {
                        poke && refreshing -> kotlin.math.sin((now - spinFrom) / 38f) * px * .42f
                        poke && pulling -> kotlin.math.sin(now / 55f) * px * (.2f + frac * .25f)
                        else -> 0f
                    }
                    if (shield && spinFrom > 0L) { val e = ((now - spinFrom) / 440f).coerceIn(0f, 1f); rotationZ = 540f * (1f - (1f - e) * (1f - e) * (1f - e)) }
                }, closed = pulling && !poke)
            }
        }
    }
}

/** Refresh style picker: a live preview that loops the refresh, then expressive shapes and pixel characters as M3 tiles. */
@Composable
fun PtrPickerSheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val cs = MaterialTheme.colorScheme; val active = ptr(selected)
    SheetTitle("下拉刷新", Sym.Refresh, "选一个下拉时迎接你的角色")
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(148.dp).clip(RoundedCornerShape(32.dp))
        .background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surfaceContainerLow))), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.TopEnd).offset(40.dp, (-36).dp).size(150.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(cs.onPrimaryContainer.copy(.06f)))
        val demo = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        PtrIndicator(demo, true, active.id, Modifier.offset(y = (-6).dp))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(active.name, style = MaterialTheme.typography.titleLargeEmphasized, color = cs.onPrimaryContainer)
            Text(active.caption, style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(.8f))
        }
    }
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("灵动形状" to PTR.filter { !it.pixel }, "像素角色" to PTR.filter { it.pixel }).forEach { (title, list) ->
            item(title, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(title, Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmallEmphasized, color = cs.primary)
            }
            items(list, key = { it.id }) { c ->
                val on = c.id == selected
                val bg by animateColorAsState(if (on) cs.secondaryContainer else cs.surfaceContainer, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "ptr")
                Column(Modifier.springClick(RoundedCornerShape(if (on) 32.dp else 24.dp)) { onSelect(c.id) }.background(bg).padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        PtrBadge(c, 52.dp)
                        if (on) Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(22.dp).clip(CircleShape).background(cs.surface).padding(2.dp).clip(CircleShape).background(cs.primary),
                            contentAlignment = Alignment.Center) { Icon(Sym.Check, "Selected", Modifier.size(14.dp), tint = cs.onPrimary) }
                    }
                    Text(c.name, Modifier.padding(top = 10.dp), style = if (on) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text(c.caption, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}
