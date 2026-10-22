package com.orion.store.ui

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.orion.store.data.DonationConfig
import kotlinx.coroutines.delay

/** Canvas port of DinoCirclePreview: sprite dino, scrolling ground and clouds. */
@Composable
fun DinoCirclePreview(size: Dp, paused: Boolean = false, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val sprite = remember { runCatching { ctx.assets.open("dino/assets/default_100_percent/100-offline-sprite.png").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() }
    var now by remember { mutableLongStateOf(0L) }
    val clouds = remember { floatArrayOf(size.value, -45f, size.value + 60, -30f) }
    LaunchedEffect(paused) { while (!paused) { withFrameMillis { now = it }; } }
    Canvas(modifier.size(size)) {
        val img = sprite ?: return@Canvas
        val k = this.size.width / size.value; val unit = size.value
        val groundY = unit * .72f
        fun blit(img: ImageBitmap, sx: Int, sy: Int, sw: Int, sh: Int, dx: Float, dy: Float, dw: Float, dh: Float, a: Float = 1f) =
            drawImage(img, IntOffset(sx, sy), IntSize(sw, sh), IntOffset((dx * k).toInt(), (dy * k).toInt()), IntSize((dw * k).toInt(), (dh * k).toInt()), alpha = a)
        for (i in 0..2 step 2) {
            blit(img, 86, 2, 46, 14, clouds[i], groundY + clouds[i + 1], 46f, 14f)
            clouds[i] -= .5f
            if (clouds[i] < -46f) { clouds[i] = unit + Math.random().toFloat() * 20; clouds[i + 1] = -30f - Math.random().toFloat() * 20 }
        }
        val off = ((now / 1000f) * 60).toInt() % 180
        blit(img, 2 + off, 54, unit.toInt(), 12, 0f, groundY, unit, 12f, .4f)
        blit(img, 848 + listOf(88, 132)[((now / 120) % 2).toInt()], 2, 44, 47, unit * .28f, groundY - 45f, 44f, 47f)
    }
}

private const val RUNNER = "(function(){var r=window.Runner&&Runner.instance_;if(!r)return;"

/** WebView host for the bundled Chrome Dino build (assets/dino), bridged like the web iframe. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DinoWeb(web: MutableState<WebView?>, onStatus: (String) -> Unit, onScore: (Long) -> Unit, onOver: (Long) -> Unit, modifier: Modifier) {
    val cb = rememberUpdatedState(Triple(onStatus, onScore, onOver))
    AndroidView({ c ->
        WebView(c).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
            addJavascriptInterface(object {
                @JavascriptInterface fun post(type: String, score: Double) = post {
                    when {
                        type.startsWith("status:") -> cb.value.first(type.removePrefix("status:"))
                        type == "dino-score" -> cb.value.second(score.toLong())
                        type == "dino-game-over" -> cb.value.third(score.toLong())
                    }
                }
            }, "OrionDino")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String?) = v.evaluateJavascript(
                    "(function(){var l='';window.addEventListener('message',function(e){if(e.data&&e.data.type)OrionDino.post(e.data.type,e.data.score||0)});" +
                        "setInterval(function(){var r=window.Runner&&Runner.instance_;if(!r)return;var s=r.crashed?'crashed':r.playing?'playing':'idle';if(s!==l){l=s;OrionDino.post('status:'+s,0)}},120)})()", null)
            }
            loadUrl("file:///android_asset/dino/game.html"); web.value = this
        }
    }, modifier, onRelease = { it.destroy(); web.value = null })
}

@Composable
fun DinoGameSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current
    val web = remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("idle") }; var score by remember { mutableLongStateOf(0) }
    var over by remember { mutableStateOf<Long?>(null) }; var record by remember { mutableStateOf(false) }
    fun js(body: String) = web.value?.evaluateJavascript("$RUNNER$body})()", null)
    fun action() = when (status) {
        "crashed" -> js("if(r.crashed||!r.playing)r.restart();")
        "idle" -> js("if(!r.playing&&!r.crashed){r.playing=true;r.update();r.tRex&&r.tRex.startJump(r.currentSpeed)}")
        else -> js("if(r.playing&&!r.crashed&&r.tRex&&!r.tRex.jumping&&!r.tRex.ducking)r.tRex.startJump(r.currentSpeed);")
    }
    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("MINI GAME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            Text("Orion Run", style = MaterialTheme.typography.headlineSmallEmphasized)
            Text("商店维护时来玩一局吧。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    DinoWeb(web, { status = it; if (it == "playing") { over = null; record = false } }, { score = it }, { sc ->
        val prevHi = s.dinoHighScore
        over = sc; score = sc; record = sc > prevHi
        val hi = maxOf(prevHi, sc); val xp = sc / 100
        vm.set { st ->
            var n = st.updateDinoHighScore(sc)
            if (xp > 0) n = n.addGameXP(xp.toDouble(), System.currentTimeMillis())
            listOf(200L to "恐龙新兵", 600L to "恐龙大师", 1500L to "恐龙传说").filter { hi >= it.first }.forEach { n = n.unlockBadge(it.second) }
            n
        }
        vm.haptic(if (record) H.SUCCESS else H.ERROR)
    }, Modifier.padding(16.dp).fillMaxWidth().height(160.dp).clip(RoundedCornerShape(24.dp)))
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("本局得分" to "${over ?: score}", "历史最高" to "${maxOf(s.dinoHighScore, over ?: 0)}", "经验值" to "+${(over ?: 0) / 100}").forEach { (l, v) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)) {
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, style = MaterialTheme.typography.titleLargeEmphasized, color = if (l == "XP Earned") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    AnimatedVisibility(record) {
        Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Sym.Star, null, tint = MaterialTheme.colorScheme.tertiary); Spacer(Modifier.width(6.dp)); Text("New record!", fontWeight = FontWeight.Bold) }
    }
    Row(Modifier.padding(16.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Sym.PhoneAndroid, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text("操作", style = MaterialTheme.typography.labelLarge); Text("动作按钮或点按", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Button({ action(); vm.haptic(H.LIGHT) }, modifier = Modifier.heightIn(min = 56.dp), shapes = ButtonDefaults.shapes()) {
            Text(when (status) { "crashed" -> "重新开始"; "idle" -> "开始"; else -> "跳一下" }, style = MaterialTheme.typography.titleMedium)
        }
    }
}
