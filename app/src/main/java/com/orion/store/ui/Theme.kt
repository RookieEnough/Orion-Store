package com.orion.store.ui

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import com.orion.store.data.CustomPalette
import com.orion.store.data.Defaults
import com.orion.store.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

val Safe = Color(0xFF10B981)
val Caution = Color(0xFFF59E0B)

fun previewScheme(theme: String, palette: String, custom: List<CustomPalette> = emptyList()): ColorScheme = palette(palette, custom).scheme(theme)

/** Material You ships no dusk tones: lift the dynamic dark surfaces toward onSurface so dusk stays a softer, distinct dark. */
private fun ColorScheme.dusk(): ColorScheme {
    fun l(c: Color, k: Float) = lerp(c, onSurface, k)
    return copy(background = l(surface, .07f), surface = l(surface, .07f), surfaceDim = l(surface, .07f), surfaceContainerLowest = l(surfaceContainerLowest, .05f),
        surfaceContainerLow = l(surfaceContainerLow, .08f), surfaceContainer = l(surfaceContainer, .08f), surfaceContainerHigh = l(surfaceContainerHigh, .09f),
        surfaceContainerHighest = l(surfaceContainerHighest, .1f), surfaceVariant = l(surfaceVariant, .1f), surfaceBright = l(surfaceBright, .1f))
}

/** Downloads Google Fonts TTFs once into filesDir/fonts (css2 serves TTF to non-browser agents), then builds a variable-weight family. */
object Fonts {
    private val http by lazy { OkHttpClient() }
    private val weights = listOf(400, 500, 600, 700)

    @OptIn(ExperimentalTextApi::class)
    private fun family(files: List<Pair<Int, File>>) = FontFamily(files.map { (w, f) ->
        if (Build.VERSION.SDK_INT >= 26) Font(f, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) else Font(f, FontWeight(w))
    })

    /** Already-downloaded families resolve synchronously, so the first frame uses the right font instead of reflowing a beat later. */
    fun cached(ctx: Context, id: String, s: Settings): FontFamily? = when {
        id == "systemDefault" -> FontFamily.Default
        id.startsWith("custom:") -> null
        else -> weights.map { it to File(ctx.filesDir, "fonts/${id}_$it.ttf") }.takeIf { l -> l.all { it.second.length() > 0 } }?.let(::family)
    }

    suspend fun load(ctx: Context, id: String, s: Settings): FontFamily? = withContext(Dispatchers.IO) {
        runCatching {
            if (id == "systemDefault") return@runCatching FontFamily.Default
            if (id.startsWith("custom:")) return@runCatching s.customFonts.firstOrNull { "custom:${it.id}" == id }?.let { File(it.path) }?.takeIf { it.exists() }?.let { f -> FontFamily(Font(f)) }
            val name = Defaults.FONTS.firstOrNull { it.first == id }?.second ?: return@runCatching null
            val dir = File(ctx.filesDir, "fonts").apply { mkdirs() }
            val cached = weights.map { it to File(dir, "${id}_$it.ttf") }
            if (cached.all { it.second.length() > 0 }) return@runCatching family(cached)
            val css = http.newCall(Request.Builder().url("https://fonts.googleapis.com/css2?family=${name.replace(' ', '+')}:wght@${weights.joinToString(";")}").build()).execute().use { it.body.string() }
            val urls = Regex("""font-weight:\s*(\d+);[\s\S]*?url\((https://[^)]+\.ttf)\)""").findAll(css).associate { it.groupValues[1].toInt() to it.groupValues[2] }
            val got = weights.mapNotNull { w ->
                val u = urls[w] ?: urls.values.firstOrNull() ?: return@mapNotNull null
                val f = File(dir, "${id}_$w.ttf")
                if (f.length() == 0L) http.newCall(Request.Builder().url(u).build()).execute().use { r -> r.body.byteStream().use { i -> f.outputStream().use { i.copyTo(it) } } }
                w to f
            }
            got.takeIf { it.isNotEmpty() }?.let(::family)
        }.getOrNull()
    }
}

private fun TextStyle.f(ff: FontFamily?) = if (ff == null) this else copy(fontFamily = ff)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun typography(ff: FontFamily?) = Typography().run {
    Typography(
        displayLarge.f(ff), displayMedium.f(ff), displaySmall.f(ff), headlineLarge.f(ff), headlineMedium.f(ff), headlineSmall.f(ff),
        titleLarge.f(ff), titleMedium.f(ff), titleSmall.f(ff), bodyLarge.f(ff), bodyMedium.f(ff), bodySmall.f(ff), labelLarge.f(ff), labelMedium.f(ff), labelSmall.f(ff),
        displayLargeEmphasized.f(ff), displayMediumEmphasized.f(ff), displaySmallEmphasized.f(ff), headlineLargeEmphasized.f(ff), headlineMediumEmphasized.f(ff),
        headlineSmallEmphasized.f(ff), titleLargeEmphasized.f(ff), titleMediumEmphasized.f(ff), titleSmallEmphasized.f(ff), bodyLargeEmphasized.f(ff),
        bodyMediumEmphasized.f(ff), bodySmallEmphasized.f(ff), labelLargeEmphasized.f(ff), labelMediumEmphasized.f(ff), labelSmallEmphasized.f(ff),
    )
}

/** "Disable Animations": every M3 and Orion motion token snaps. */
private object InstantMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrionTheme(s: Settings, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val theme = if (s.isOled) "oled" else s.theme
    val dyn = s.materialYouEnabled && Build.VERSION.SDK_INT >= 31
    // Theme objects are remembered: fresh instances on every Settings change re-ran the whole tree through these static locals.
    val colors = remember(theme, dyn, s.palette, s.customPalettes) {
        if (!dyn) palette(s.palette, s.customPalettes).scheme(theme) else (if (theme == "light") dynamicLightColorScheme(ctx) else dynamicDarkColorScheme(ctx)).let {
            when (theme) {
                "oled" -> it.copy(background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceContainerLowest = Color.Black, surfaceContainerLow = lerp(Color.Black, it.surfaceContainerLow, .5f))
                "dusk" -> it.dusk()
                else -> it
            }
        }
    }
    val first = remember { Fonts.cached(ctx, s.appFont, s) }
    val ff by produceState(first, s.appFont, s.customFonts) { value = Fonts.load(ctx, s.appFont, s) }
    val type = remember(ff) { typography(ff) }
    val motion = remember(s.disableAnimations) { if (s.disableAnimations) InstantMotion else MotionScheme.expressive() }
    // Bar icons follow Orion's own theme, not the system's: a dark Orion on a light-mode phone kept dark icons on a dark page
    val view = androidx.compose.ui.platform.LocalView.current; val light = colors.isLight
    if (!view.isInEditMode) androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).run { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
        }
    }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = motion, typography = type, content = content)
}

val ColorScheme.isLight get() = background.luminanceApprox() > .5f
private fun Color.luminanceApprox() = .299f * red + .587f * green + .114f * blue
