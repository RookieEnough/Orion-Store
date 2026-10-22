package com.orion.store.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orion.store.data.CustomPalette
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** Tone math for palettes built on the device. Hue and chroma live in OKLCH, which holds a hue steady from light to dark;
 *  tone is CIELAB L*, the lightness scale M3 tones use, so roles keep the same contrast as the built-in palettes. */
private object Tone {
    private fun linear(c: Double) = if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
    private fun gamma(c: Double) = if (c <= .0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - .055

    /** OKLCH chroma and hue (degrees) of an sRGB color. */
    fun lch(argb: Int): Pair<Double, Double> {
        val r = linear(((argb shr 16) and 255) / 255.0); val g = linear(((argb shr 8) and 255) / 255.0); val b = linear((argb and 255) / 255.0)
        val l = cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b)
        val m = cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b)
        val s = cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b)
        val a = 1.9779984951 * l - 2.4285922050 * m + .4505937099 * s
        val bb = .0259040371 * l + .7827717662 * m - .8086757660 * s
        return hypot(a, bb) to (Math.toDegrees(atan2(bb, a)) + 360) % 360
    }

    private fun rgb(lightness: Double, a: Double, b: Double): DoubleArray {
        val l = (lightness + .3963377774 * a + .2158037573 * b).let { it * it * it }
        val m = (lightness - .1055613458 * a - .0638541728 * b).let { it * it * it }
        val s = (lightness - .0894841775 * a - 1.2914855480 * b).let { it * it * it }
        return doubleArrayOf(4.0767416621 * l - 3.3077115913 * m + .2309699292 * s, -1.2684380046 * l + 2.6097574011 * m - .3413193965 * s,
            -.0041960863 * l - .7034186147 * m + 1.7076147010 * s)
    }

    /** The color at [tone] (L*, 0..100) with this hue, at [chroma] or the most sRGB can show at that tone. */
    fun at(hue: Double, chroma: Double, tone: Double): Int {
        if (tone <= 0) return 0xFF000000.toInt()
        if (tone >= 100) return 0xFFFFFFFF.toInt()
        val y = if (tone > 8) ((tone + 16) / 116).pow(3) else tone / 903.2962962
        val ca = cos(Math.toRadians(hue)); val sa = sin(Math.toRadians(hue))
        fun solve(c: Double): DoubleArray {
            var lo = 0.0; var hi = 1.0; var out = rgb(.5, c * ca, c * sa)
            repeat(22) {
                val mid = (lo + hi) / 2; out = rgb(mid, c * ca, c * sa)
                if (.2126 * out[0] + .7152 * out[1] + .0722 * out[2] < y) lo = mid else hi = mid
            }
            return out
        }
        fun fits(p: DoubleArray) = p.all { it in -1e-4..1.0001 }
        var best = solve(chroma)
        if (!fits(best)) {
            best = solve(0.0); var lo = 0.0; var hi = chroma
            repeat(14) { val mid = (lo + hi) / 2; val p = solve(mid); if (fits(p)) { best = p; lo = mid } else hi = mid }
        }
        fun ch(v: Double) = (gamma(v.coerceIn(0.0, 1.0)) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(best[0]) shl 16) or (ch(best[1]) shl 8) or ch(best[2])
    }
}

private class Tonal(val hue: Double, val chroma: Double) {
    private val memo = HashMap<Int, Color>()
    fun t(n: Int) = memo.getOrPut(n) { Color(Tone.at(hue, chroma, n.toDouble())) }
}

private fun styled(c: Double, style: String) = when (style) {
    "soft" -> c * .55
    "vivid" -> if (c > .03) maxOf(c * 1.3, .15) else c
    else -> if (c > .03) maxOf(c, .1) else c
}.coerceAtMost(.32)

private val ErrorTones = Tonal(27.0, .18)

/** Light, Dark, Dusk and OLED schemes from a custom palette, with the same role tones as the generated ones. */
private fun build(cp: CustomPalette): Palette {
    val (c0, h) = Tone.lch(cp.primary); val pc = styled(c0, cp.style)
    val p = Tonal(h, pc)
    val s = cp.secondary?.let(Tone::lch)?.let { (c, hh) -> Tonal(hh, styled(c, cp.style) * .8) } ?: Tonal(h, (pc * .35).coerceAtMost(.06))
    val t = cp.tertiary?.let(Tone::lch)?.let { (c, hh) -> Tonal(hh, styled(c, cp.style)) } ?: Tonal((h + 60) % 360, pc * .8)
    val nk = when (cp.style) { "soft" -> .006; "vivid" -> .02; else -> .012 }.coerceAtMost(pc)
    val n = Tonal(h, nk); val nv = Tonal(h, nk * 2); val e = ErrorTones
    val light = lightColorScheme(
        primary = p.t(40), onPrimary = p.t(100), primaryContainer = p.t(90), onPrimaryContainer = p.t(10), inversePrimary = p.t(80),
        secondary = s.t(40), onSecondary = s.t(100), secondaryContainer = s.t(90), onSecondaryContainer = s.t(10),
        tertiary = t.t(40), onTertiary = t.t(100), tertiaryContainer = t.t(90), onTertiaryContainer = t.t(10),
        background = n.t(98), onBackground = n.t(10), surface = n.t(98), onSurface = n.t(10), surfaceVariant = nv.t(90), onSurfaceVariant = nv.t(30),
        surfaceTint = p.t(40), inverseSurface = n.t(20), inverseOnSurface = n.t(95), error = e.t(40), onError = e.t(100), errorContainer = e.t(90),
        onErrorContainer = e.t(10), outline = nv.t(50), outlineVariant = nv.t(80), scrim = Color.Black, surfaceBright = n.t(98), surfaceContainer = n.t(94),
        surfaceContainerHigh = n.t(92), surfaceContainerHighest = n.t(90), surfaceContainerLow = n.t(96), surfaceContainerLowest = n.t(100), surfaceDim = n.t(87),
        primaryFixed = p.t(90), primaryFixedDim = p.t(80), onPrimaryFixed = p.t(10), onPrimaryFixedVariant = p.t(30),
        secondaryFixed = s.t(90), secondaryFixedDim = s.t(80), onSecondaryFixed = s.t(10), onSecondaryFixedVariant = s.t(30),
        tertiaryFixed = t.t(90), tertiaryFixedDim = t.t(80), onTertiaryFixed = t.t(10), onTertiaryFixedVariant = t.t(30),
    )
    val dark = darkColorScheme(
        primary = p.t(80), onPrimary = p.t(20), primaryContainer = p.t(30), onPrimaryContainer = p.t(90), inversePrimary = p.t(40),
        secondary = s.t(80), onSecondary = s.t(20), secondaryContainer = s.t(30), onSecondaryContainer = s.t(90),
        tertiary = t.t(80), onTertiary = t.t(20), tertiaryContainer = t.t(30), onTertiaryContainer = t.t(90),
        background = n.t(6), onBackground = n.t(90), surface = n.t(6), onSurface = n.t(90), surfaceVariant = nv.t(30), onSurfaceVariant = nv.t(80),
        surfaceTint = p.t(80), inverseSurface = n.t(90), inverseOnSurface = n.t(20), error = e.t(80), onError = e.t(20), errorContainer = e.t(30),
        onErrorContainer = e.t(90), outline = nv.t(60), outlineVariant = nv.t(30), scrim = Color.Black, surfaceBright = n.t(24), surfaceContainer = n.t(12),
        surfaceContainerHigh = n.t(17), surfaceContainerHighest = n.t(22), surfaceContainerLow = n.t(10), surfaceContainerLowest = n.t(4), surfaceDim = n.t(6),
        primaryFixed = p.t(90), primaryFixedDim = p.t(80), onPrimaryFixed = p.t(10), onPrimaryFixedVariant = p.t(30),
        secondaryFixed = s.t(90), secondaryFixedDim = s.t(80), onSecondaryFixed = s.t(10), onSecondaryFixedVariant = s.t(30),
        tertiaryFixed = t.t(90), tertiaryFixedDim = t.t(80), onTertiaryFixed = t.t(10), onTertiaryFixedVariant = t.t(30),
    )
    val d = Tonal(h, (nk * 3).coerceAtMost(.04)); val dv = Tonal(h, (nk * 4).coerceAtMost(.05))
    val dusk = dark.copy(background = d.t(12), surface = d.t(12), surfaceDim = d.t(12), surfaceBright = d.t(32), surfaceContainerLowest = d.t(8), surfaceContainerLow = d.t(15),
        surfaceContainer = d.t(18), surfaceContainerHigh = d.t(22), surfaceContainerHighest = d.t(26), surfaceVariant = d.t(26), onSurfaceVariant = dv.t(76), outlineVariant = dv.t(34))
    val oled = dark.copy(background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceContainerLowest = Color.Black, surfaceContainerLow = n.t(4),
        surfaceContainer = n.t(7), surfaceContainerHigh = n.t(10), surfaceContainerHighest = n.t(14), surfaceVariant = n.t(14), surfaceBright = n.t(20))
    return Palette(cp.id, cp.name, light, dark, dusk, oled)
}

private val built = HashMap<CustomPalette, Palette>()

/** A built-in palette by id, else one of the user's own, else Orion. */
fun palette(id: String, custom: List<CustomPalette> = emptyList()): Palette =
    PALETTES.firstOrNull { it.id == id } ?: custom.firstOrNull { it.id == id }?.let(::customScheme) ?: PALETTES[0]

fun customScheme(cp: CustomPalette): Palette = synchronized(built) { if (built.size > 24) built.clear(); built.getOrPut(cp) { build(cp) } }

private val SEEDS = listOf(0xFF6366F1, 0xFF0EA5E9, 0xFF14B8A6, 0xFF22C55E, 0xFF84CC16, 0xFFEAB308, 0xFFF97316, 0xFFEF4444, 0xFFEC4899, 0xFFA855F7, 0xFF92400E, 0xFF64748B)
    .map { it.toInt() }
private const val SEED_TONE = 58.0
private const val MAX_CHROMA = .3

/** Palette maker: pick seed colors and a style, preview the result in every theme, save it next to the built-in palettes. */
@Composable
fun PaletteMakerSheet(vm: MainViewModel, editing: CustomPalette?, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val close = LocalSheetClose.current
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "我的调色板") }
    var seeds by remember { mutableStateOf(listOf(editing?.primary ?: SEEDS.random(), editing?.secondary, editing?.tertiary)) }
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var style by rememberSaveable { mutableStateOf(editing?.style ?: "balanced") }
    var theme by rememberSaveable { mutableStateOf(if (s.isOled) "oled" else s.theme) }
    val draft = CustomPalette(editing?.id ?: "draft", name.trim().ifEmpty { "我的调色板" }, seeds[0]!!, seeds[1], seeds[2], style)
    val pal = remember(draft) { build(draft) }
    fun setSeed(i: Int, c: Int?) { seeds = seeds.toMutableList().also { it[i] = c } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SheetTitle(if (editing == null) "制作调色板" else "编辑调色板", Sym.Palette, "选一个颜色，Orion 从它生成全部色调")
        MaterialTheme(colorScheme = pal.scheme(theme)) { PalettePreview(draft.name) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("light" to "浅色", "dusk" to "黄昏", "dark" to "深色", "oled" to "OLED").forEach { (t, l) -> Choice(theme == t, l) { theme = t } }
        }
        // The three seeds: primary always set, secondary and tertiary follow the primary until given their own color
        val shown = listOf(pal.light.primary, pal.light.secondary, pal.light.tertiary)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("主色", "辅色", "第三色").forEachIndexed { i, label ->
                SeedSlot(label, if (i == 0) Color(seeds[0]!!) else seeds[i]?.let(::Color) ?: shown[i], i > 0 && seeds[i] == null, slot == i, Modifier.weight(1f)) { slot = i }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (slot > 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Choice(seeds[slot] == null, "跟随主色", Sym.AutoAwesome) { setSeed(slot, null) }
                Choice(seeds[slot] != null, "自定义颜色", Sym.FormatColorFill) { if (seeds[slot] == null) setSeed(slot, shown[slot].toArgb()) }
            }
            val seed = seeds[slot]
            AnimatedVisibility(seed != null) { if (seed != null) SeedEditor(slot, seed) { setSeed(slot, it) } }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("风格", style = MaterialTheme.typography.titleSmallEmphasized)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("soft" to "柔和", "balanced" to "均衡", "vivid" to "鲜艳").forEach { (id, l) -> Choice(style == id, l) { style = id } }
            }
        }
        OutlinedTextField(name, { name = it.take(24) }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), label = { Text("名称") }, singleLine = true,
            shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(KeyboardCapitalization.Words))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CtaButton(if (editing == null) "保存并应用" else "保存修改", {
                val id = editing?.id ?: "custom-${System.currentTimeMillis().toString(36)}"; val cp = draft.copy(id = id)
                vm.set { st ->
                    val list = if (st.customPalettes.any { it.id == id }) st.customPalettes.map { if (it.id == id) cp else it } else st.customPalettes + cp
                    st.copy(customPalettes = list, palette = id, materialYouEnabled = false)
                }
                vm.haptic(H.SUCCESS); close()
            }, icon = Sym.Check)
            if (editing != null) OutlinedButton({
                vm.set { st -> st.copy(customPalettes = st.customPalettes.filterNot { it.id == editing.id }, palette = if (st.palette == editing.id) "orion" else st.palette) }
                vm.haptic(H.WARNING); close()
            }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Sym.Delete, null); Spacer(Modifier.width(8.dp)); Text("删除调色板")
            }
        }
    }
}

/** A slice of Orion drawn in the draft palette: search pill, hero, chips, an app row and the dock. */
@Composable
private fun PalettePreview(name: String) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(c.surface).border(1.dp, c.outlineVariant, RoundedCornerShape(32.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp).clip(Pill).background(c.surfaceContainerHigh).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(c.primary, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) { Icon(Sym.Search, null, Modifier.size(18.dp), tint = c.onPrimary) }
            Text("Search apps", Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            Icon(Sym.DarkMode, null, Modifier.padding(end = 8.dp).size(20.dp), tint = c.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.primaryContainer).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FEATURED", style = MaterialTheme.typography.labelMedium, color = c.onPrimaryContainer.copy(.75f))
                Text(name, style = MaterialTheme.typography.titleLargeEmphasized, color = c.onPrimaryContainer, maxLines = 1)
            }
            Box(Modifier.size(48.dp).background(c.tertiary, MaterialShapes.Sunny.toShape()), contentAlignment = Alignment.Center) { Icon(Sym.Palette, null, tint = c.onTertiary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "Media", "Tools").forEachIndexed { i, l ->
                Text(l, Modifier.clip(Pill).background(if (i == 0) c.secondaryContainer else c.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge, color = if (i == 0) c.onSecondaryContainer else c.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.tertiaryContainer), contentAlignment = Alignment.Center) { Icon(Sym.MusicNote, null, tint = c.onTertiaryContainer) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Music player", style = MaterialTheme.typography.titleSmall, color = c.onSurface)
                Text("Media · 12 MB", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            Button({}, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text("获取") }
        }
        Row(Modifier.align(Alignment.CenterHorizontally).clip(Pill).background(c.surfaceContainerHigh).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(36.dp).clip(Pill).background(c.secondaryContainer).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.AndroidFilled, null, Modifier.size(18.dp), tint = c.onSecondaryContainer)
                Text("Apps", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge, color = c.onSecondaryContainer)
            }
            listOf(Sym.DesktopWindows, Sym.GridView, Sym.Code).forEach { Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(it, null, Modifier.size(18.dp), tint = c.onSurfaceVariant) } }
        }
    }
}

@Composable
private fun SeedSlot(label: String, color: Color, auto: Boolean, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ring by animateDpAsState(if (on) 3.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "ring")
    Column(modifier.springClick(RoundedCornerShape(24.dp), onClick = onClick).background(if (on) cs.secondaryContainer else cs.surfaceContainer).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).border(ring, cs.primary, CircleShape).padding(ring + 2.dp).background(color, if (on) MaterialShapes.Cookie9Sided.toShape() else CircleShape),
            contentAlignment = Alignment.Center) { if (auto) Icon(Sym.AutoAwesome, "Matches primary", Modifier.size(18.dp), tint = Color.White.copy(.9f)) }
        Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, color = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
    }
}

/** Hue and intensity sliders on their own color tracks, quick swatches and a hex field. */
@Composable
private fun SeedEditor(slot: Int, seed: Int, onSeed: (Int) -> Unit) {
    val start = remember(slot) { Tone.lch(seed) }
    var hue by remember(slot) { mutableFloatStateOf(start.second.toFloat()) }
    var chroma by remember(slot) { mutableFloatStateOf((start.first / MAX_CHROMA).toFloat().coerceIn(0f, 1f)) }
    fun pick(c: Int) { val (cc, hh) = Tone.lch(c); hue = hh.toFloat(); chroma = (cc / MAX_CHROMA).toFloat().coerceIn(0f, 1f); onSeed(c) }
    fun slide() = onSeed(Tone.at(hue.toDouble(), chroma * MAX_CHROMA, SEED_TONE))
    val hueTrack = remember { Brush.horizontalGradient((0..12).map { Color(Tone.at(it * 30.0, .16, 65.0)) }) }
    val chromaTrack = remember(hue) { Brush.horizontalGradient(listOf(Color(Tone.at(hue.toDouble(), 0.0, SEED_TONE)), Color(Tone.at(hue.toDouble(), MAX_CHROMA, SEED_TONE)))) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Hue", style = MaterialTheme.typography.labelLarge)
        TrackSlider(hue / 360f, hueTrack) { hue = it * 360f; slide() }
        Text("Intensity", style = MaterialTheme.typography.labelLarge)
        TrackSlider(chroma, chromaTrack) { chroma = it; slide() }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SEEDS.forEach { c ->
                Box(Modifier.size(36.dp).springClick(CircleShape) { pick(c) }.background(Color(c)))
            }
        }
        var hex by remember(seed) { mutableStateOf("%06X".format(seed and 0xFFFFFF)) }
        OutlinedTextField(hex, { v ->
            hex = v.filter { it.isLetterOrDigit() }.take(6).uppercase()
            if (hex.length == 6) hex.toIntOrNull(16)?.let { pick(it or (0xFF shl 24)) }
        }, Modifier.fillMaxWidth(), label = { Text("色值") }, prefix = { Text("#") }, singleLine = true, shape = RoundedCornerShape(20.dp),
            leadingIcon = { Box(Modifier.size(24.dp).clip(CircleShape).background(Color(seed))) },
            keyboardOptions = KeyboardOptions(KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii))
    }
}

@Composable
private fun TrackSlider(value: Float, track: Brush, onChange: (Float) -> Unit) = Box(contentAlignment = Alignment.Center) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(20.dp).clip(Pill).background(track))
    Slider(value, onChange, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onSurface, activeTrackColor = Color.Transparent,
        inactiveTrackColor = Color.Transparent, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
}
