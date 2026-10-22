package com.orion.store.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.orion.store.data.*
import com.orion.store.platform.AiError
import com.orion.store.platform.AiProvider
import com.orion.store.platform.DlStatus
import com.orion.store.platform.Downloads
import com.orion.store.platform.LocalLlm
import com.orion.store.platform.OrionAi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** One chat bubble. Replies stream in, so their text and apps are state; [raw] keeps the [12] markers for the next turn's context. */
class AiMsg(val user: Boolean, text: String, apps: List<AppItem> = emptyList(), val error: Boolean = false) {
    var text by mutableStateOf(text)
    var apps by mutableStateOf(apps)
    var features by mutableStateOf(emptyList<Feature>())
    var stopped by mutableStateOf(false)
    var raw = text
    /** Set once the entrance has played, so rows scrolled back into view do not pop in again. */
    var shown = false
}

/** One chat with Orion AI. Lives on the view model, so closing the chat screen keeps the conversation. */
class AiSession {
    val messages = mutableStateListOf<AiMsg>()
    var busy by mutableStateOf(false); private set
    private var job: Job? = null
    private var catalog: List<AppItem> = emptyList(); private var sig = 0; private var system = ""; private var forLocal = false

    fun clear() { stop(); messages.clear() }
    fun stop() { job?.cancel(); job = null }

    /** Whether the chosen provider can answer: a key for cloud ones, an address for Custom, a downloaded model for On device. */
    fun ready(s: Settings, p: AiProvider = OrionAi.provider(s.aiProvider)) = when (p) {
        AiProvider.LOCAL -> LocalLlm.model(s.aiLocalModel)?.let(LocalLlm::ready) == true
        AiProvider.CUSTOM -> s.aiBaseUrl.isNotBlank()
        else -> s.aiKeys[p.id].orEmpty().isNotBlank()
    }

    fun send(vm: MainViewModel, text: String) {
        val q = text.trim(); if (q.isEmpty() || busy) return
        val s = vm.s; val p = OrionAi.provider(s.aiProvider); val local = p == AiProvider.LOCAL
        val history = messages.filter { !it.error && it.raw.isNotBlank() }.takeLast(if (local) 4 else 10)
        messages += AiMsg(true, q); val reply = AiMsg(false, ""); messages += reply; busy = true
        if (s.aiSounds) AiSound.send(); vm.haptic(H.LIGHT)
        job = vm.viewModelScope.launch {
            try {
                val all = vm.allApps()
                val now = all.size * 31 + all.sumOf { it.id.hashCode() }
                if (now != sig || system.isEmpty() || local != forLocal) { catalog = all; sig = now; forLocal = local; system = OrionAi.system(Device.isTv, all, GUIDE_TEXT, full = !local) }
                // This message's local matches, plus what was already suggested so follow-ups like "something lighter" still have it
                val earlier = history.flatMap { m -> m.apps.map { catalog.indexOf(it) } }.filter { it >= 0 }.takeLast(5)
                val ids = (OrionAi.matches(catalog, q, if (local) 8 else 20) + earlier).distinct()
                val turns = history.map { OrionAi.Turn(it.user, it.raw) } + OrionAi.Turn(true, q + "\n\n" + OrionAi.describe(catalog, ids, if (local) 60 else 110) +
                    if (local) OrionAi.guideMatches(GUIDE_TEXT, q, 3).joinToString("") { "\nOrion guide g$it ${GUIDE_TEXT[it].first}: ${GUIDE_TEXT[it].second}" } else "")
                val show: (String) -> Unit = { t -> reply.text = OrionAi.parse(t).reply }
                val raw = if (local) LocalLlm.chat(vm.getApplication(), LocalLlm.model(s.aiLocalModel) ?: throw AiError("请先在 AI 设置中选择一个本地模型。"), system, turns, show)
                    else OrionAi.stream(p, s.aiKeys[p.id].orEmpty(), s.aiModels[p.id].orEmpty(), s.aiBaseUrl, system, turns, show)
                val ans = OrionAi.parse(raw)
                reply.raw = raw; reply.text = ans.reply; reply.apps = ans.picks.mapNotNull { catalog.getOrNull(it) }.distinctBy { it.id }.take(6)
                reply.features = ans.features.mapNotNull { GUIDE.getOrNull(it)?.second }.filter { it.go != null }.take(3)
                if (vm.s.aiSounds) AiSound.receive(); vm.haptic(H.SUCCESS)
            } catch (e: CancellationException) {
                if (reply.text.isBlank()) messages.remove(reply) else { reply.raw = reply.text; reply.stopped = true }
                throw e
            } catch (e: Exception) {
                messages.remove(reply)
                messages += AiMsg(false, (e as? AiError)?.message ?: "出了点问题：${e.message ?: e.javaClass.simpleName}", error = true)
                if (vm.s.aiSounds) AiSound.error(); vm.haptic(H.ERROR)
            } finally { busy = false }
        }
    }
}

private val GUIDE_TEXT by lazy { GUIDE.map { (c, f) -> "$c / ${f.title}" to f.body } }

/** Short synthesized chimes for the chat: a rising blip on send, a bright arpeggio on reply, a soft drop on errors. */
object AiSound {
    private const val RATE = 44_100
    private val player = Executors.newSingleThreadExecutor()
    fun send() = play(listOf(659.25 to 45, 987.77 to 70))
    fun receive() = play(listOf(523.25 to 60, 659.25 to 60, 783.99 to 60, 1046.5 to 150))
    fun error() = play(listOf(392.0 to 80, 311.13 to 170))

    private fun play(notes: List<Pair<Double, Int>>) = player.execute {
        runCatching {
            val pcm = ShortArray(notes.sumOf { RATE * it.second / 1000 } + RATE / 20); var at = 0
            notes.forEachIndexed { i, (f, ms) ->
                val n = RATE * ms / 1000; val tail = if (i == notes.lastIndex) RATE / 20 else 0
                for (k in 0 until n + tail) {
                    val t = k.toDouble() / RATE
                    val env = minOf(1.0, k / (RATE * .004)) * exp(-3.2 * k / (n + tail))
                    val v = (sin(2 * PI * f * t) + .22 * sin(4 * PI * f * t)) * env * .2
                    if (at + k < pcm.size) pcm[at + k] = (pcm[at + k] + v * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
                }
                at += n
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            try { track.write(pcm, 0, pcm.size); track.play(); Thread.sleep(pcm.size * 1000L / RATE + 40) } finally { track.release() }
        }
    }
}

private val AI_SHAPES = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.SoftBurst)
/** Starter prompts, a few per topic: one row of topics, three prompts for the picked one. */
private val STARTERS = listOf(
    Triple(Sym.MusicNote, "音乐", listOf("免费听音乐", "下载歌曲离线听", "更好的 YouTube Music")),
    Triple(Sym.Movie, "影视", listOf("看电影和剧集", "无广告的 YouTube", "看动漫")),
    Triple(Sym.Shield, "隐私", listOf("屏蔽广告和跟踪器", "注重隐私的浏览器", "安全通讯")),
    Triple(Sym.SportsEsports, "游戏", listOf("玩复古主机游戏", "离线也能玩的游戏", "模拟 PlayStation 游戏")),
    Triple(Sym.Lightbulb, "Orion", listOf("怎么制作自己的调色板？", "自动更新是怎么工作的？", "守护者能做什么？")),
)
private val AiTop @Composable get() = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))

/** A shape that keeps morphing through the expressive set; [fast] while Orion AI is answering. Holds still with animations off. */
@Composable
private fun AiOrb(size: androidx.compose.ui.unit.Dp, fast: Boolean, icon: ImageVector = Sym.AutoAwesome) {
    val cs = MaterialTheme.colorScheme; val still = LocalS.current.disableAnimations
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(fast, still) { if (!still) while (true) { delay(if (fast) 650 else 2400); i++ } }
    MorphBlob(AI_SHAPES, i, size, cs.primaryContainer) { Icon(icon, null, Modifier.size(size * .5f), tint = cs.onPrimaryContainer) }
}

/** Orion AI: describe an app, get matches from the catalog with one-tap downloads. Back closes it, like every other screen. */
@Composable
fun AiScreen(vm: MainViewModel, onClose: () -> Unit) {
    BackHandler(vm.selectedApp == null, onBack = onClose)
    val s = LocalS.current; val cs = MaterialTheme.colorScheme; val ai = vm.ai
    val p = OrionAi.provider(s.aiProvider)
    val dl by Downloads.state.collectAsState()
    val ready = remember(s, dl) { ai.ready(s, p) }
    var settings by remember { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val last = ai.messages.lastOrNull()?.text
    LaunchedEffect(ai.messages.size, last) { if (ai.messages.isNotEmpty()) list.animateScrollToItem(ai.messages.lastIndex) }
    fun send(t: String) { if (ready) { ai.send(vm, t); input = "" } else settings = true }
    val label = when { p == AiProvider.LOCAL -> LocalLlm.model(s.aiLocalModel)?.name ?: "本地模型"; else -> "${p.label} · ${s.aiModels[p.id].orEmpty().ifBlank { p.model }}" }
    Column(Modifier.fillMaxSize().background(cs.surface).windowInsetsPadding(AiTop).windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AiOrb(40.dp, ai.busy); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Orion AI", style = MaterialTheme.typography.titleLargeEmphasized)
                Text(if (ready) label else "未连接", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AnimatedVisibility(ai.messages.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                IconButton({ vm.haptic(H.LIGHT); ai.clear() }) { Icon(Sym.RestartAlt, "新对话") }
            }
            IconButton({ settings = true }) { Icon(Sym.Tune, "AI settings") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !ready -> AiIntro("连接一个 AI", "使用你自己的 Gemini、ChatGPT、Claude 等密钥，或下载一个直接跑在这台手机上的模型，离线也能用。") {
                    CtaButton("设置 Orion AI", { settings = true }, Modifier.padding(horizontal = 28.dp).padding(top = 20.dp), icon = Sym.Key)
                }
                ai.messages.isEmpty() -> AiIntro("找任何应用", "描述你的需求，或随便问点关于 Orion 的事。") {
                    Starters { send(it) }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(ai.messages, key = { i, _ -> i }) { _, m -> Entrance(m) { if (m.user) UserBubble(m.text) else AiBubble(vm, m) } }
                }
            }
        }
        if (ready) AiInput(input, { input = it }, ai.busy, ai::stop) { send(input) }
    }
    if (settings) AiSettingsSheet(vm) { settings = false }
}

@Composable
private fun Starters(onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var topic by rememberSaveable { mutableIntStateOf(0) }
    Row(Modifier.padding(top = 24.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        STARTERS.forEachIndexed { i, (ic, label, _) -> Choice(topic == i, label, ic) { topic = i } }
    }
    AnimatedContent(topic, Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "topic") { t ->
        Column {
            STARTERS[t].third.forEachIndexed { i, q ->
                Row(Modifier.padding(bottom = 2.dp).fillMaxWidth().springClick(colShape(i, 3)) { onPick(q) }.background(cs.surfaceContainer).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(q, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(Sym.NorthEast, null, Modifier.size(18.dp), tint = cs.primary)
                }
            }
        }
    }
}

@Composable
private fun AiIntro(title: String, body: String, extra: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        AiOrb(88.dp, false)
        Text(title, Modifier.padding(horizontal = 28.dp).padding(top = 24.dp), style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
        Text(body, Modifier.padding(horizontal = 28.dp).padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        extra()
    }

/** New messages spring up into place once; the same row scrolled back into view just appears. */
@Composable
private fun Entrance(m: AiMsg, content: @Composable () -> Unit) {
    val k = remember { Animatable(if (m.shown) 1f else 0f) }; val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) { m.shown = true; k.animateTo(1f, spec) }
    Box(Modifier.fillMaxWidth().graphicsLayer {
        alpha = k.value.coerceIn(0f, 1f); translationY = (1 - k.value) * 32.dp.toPx(); scaleX = .92f + .08f * k.value; scaleY = scaleX
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (m.user) 1f else 0f, 1f)
    }) { content() }
}

@Composable
private fun UserBubble(text: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    val cs = MaterialTheme.colorScheme
    Text(text, Modifier.padding(start = 48.dp).clip(RoundedCornerShape(24.dp, 24.dp, 6.dp, 24.dp)).background(cs.primaryContainer).padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge, color = cs.onPrimaryContainer)
}

@Composable
private fun AiBubble(vm: MainViewModel, m: AiMsg) = Row(Modifier.fillMaxWidth()) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(32.dp).background(cs.secondaryContainer, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) {
        Icon(Sym.AutoAwesome, null, Modifier.size(18.dp), tint = cs.onSecondaryContainer)
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
        Box(Modifier.padding(end = 24.dp).clip(RoundedCornerShape(6.dp, 24.dp, 24.dp, 24.dp)).background(if (m.error) cs.errorContainer else cs.surfaceContainerHigh)
            .animateContentSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (m.text.isEmpty()) Waiting()
            else Text(m.text, style = MaterialTheme.typography.bodyLarge, color = if (m.error) cs.onErrorContainer else cs.onSurface)
        }
        if (m.stopped) Text("已停止", Modifier.padding(start = 12.dp, top = 4.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        // Features Orion AI pointed at, one tap away: the chat closes and the screen or setting opens
        AnimatedVisibility(m.features.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                m.features.forEach { f ->
                    Row(Modifier.springClick(Pill) { vm.close(Overlay.AI); vm.viewModelScope.launch { delay(120); f.go?.invoke(vm) } }.background(cs.secondaryContainer)
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(f.icon, null, Modifier.size(18.dp), tint = cs.onSecondaryContainer); Spacer(Modifier.width(6.dp))
                        Text(f.title, style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
                        Icon(Sym.ArrowForward, null, Modifier.padding(start = 4.dp).size(16.dp), tint = cs.onSecondaryContainer)
                    }
                }
            }
        }
        AnimatedVisibility(m.apps.isNotEmpty()) { Column(Modifier.padding(top = 8.dp)) { m.apps.forEachIndexed { i, a -> AiAppCard(vm, a, i, m.apps.size) } } }
    }
}

/** While the first words are on their way: a morphing indicator and a rotating line about what Orion AI is up to. */
@Composable
private fun Waiting() = Row(verticalAlignment = Alignment.CenterVertically) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1400); step++ } }
    LoadingIndicator(Modifier.size(28.dp))
    AnimatedContent(WAITING[step % WAITING.size], transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "wait") {
        Text(it, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val WAITING = listOf("正在翻目录", "正在匹配应用", "正在读简介", "正在挑最合适的", "马上就好")

@Composable
private fun AiAppCard(vm: MainViewModel, app: AppItem, i: Int, n: Int) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(bottom = 2.dp).fillMaxWidth().springClick(colShape(i, n)) { vm.selectApp(app, "ai") }.background(cs.surfaceContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, 48.dp)
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (app.platform == Platform.ANDROID) app.category else "${app.category} · ${app.platform}", style = MaterialTheme.typography.labelMedium, color = cs.primary, maxLines = 1)
            if (app.description.isNotBlank()) Text(app.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        AiAction(vm, app)
    }
}

/** Get, update, install or open right from the chat; apps that need a choice (variants, microG) open their page instead. */
@Composable
private fun AiAction(vm: MainViewModel, app: AppItem) {
    val (st, _) = cardState(app); val s = LocalS.current
    val pad = ButtonDefaults.SmallContentPadding
    when (st) {
        CardState.DOWNLOADING -> CircularWavyProgressIndicator(Modifier.size(36.dp))
        CardState.INSTALLED -> FilledTonalButton({
            runCatching { vm.tracker.launch(s.resolvedPackageNames[app.id] ?: app.packageName.orEmpty()) }.onFailure { vm.selectApp(app, "ai") }
        }, shapes = ButtonDefaults.shapes(), contentPadding = pad) { Text("打开") }
        CardState.READY -> Button({ vm.download(app) }, shapes = ButtonDefaults.shapes(), contentPadding = pad) { Text("安装") }
        CardState.UPDATE -> Button({ vm.download(app) }, shapes = ButtonDefaults.shapes(), contentPadding = pad) { Text("更新") }
        else -> {
            val url = app.variants.firstOrNull()?.url ?: app.downloadUrl
            val direct = app.installsHere() && app.variants.size <= 1 && url.isNotEmpty() && url != "#" && app.id !in Defaults.MICROG_DEPENDENT_APPS
            if (direct) Button({ vm.download(app) }, shapes = ButtonDefaults.shapes(), contentPadding = pad) { Icon(Sym.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Get") }
            else FilledTonalButton({ vm.selectApp(app, "ai") }, shapes = ButtonDefaults.shapes(), contentPadding = pad) { Text("查看") }
        }
    }
}

/** Text field plus one round button that sends, and turns into Stop while a reply is coming in. */
@Composable
private fun AiInput(text: String, onText: (String) -> Unit, busy: Boolean, onStop: () -> Unit, onSend: () -> Unit) {
    val cs = MaterialTheme.colorScheme; val vm = LocalVm.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
        BasicTextField(text, onText, Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 20.dp, vertical = 16.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary), maxLines = 5,
            keyboardOptions = KeyboardOptions(KeyboardCapitalization.Sentences, imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (!busy && text.isNotBlank()) onSend() }),
            decorationBox = { f -> Box { if (text.isEmpty()) Text("描述你想要的应用", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant); f() } })
        Spacer(Modifier.width(8.dp))
        FilledIconButton({ if (busy) { vm.haptic(H.LIGHT); onStop() } else onSend() }, modifier = Modifier.size(56.dp), enabled = busy || text.isNotBlank(), shapes = IconButtonDefaults.shapes(),
            colors = if (busy) IconButtonDefaults.filledIconButtonColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer) else IconButtonDefaults.filledIconButtonColors()) {
            AnimatedContent(busy, label = "send") { b ->
                if (b) Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(LocalContentColor.current)) else Icon(Sym.ArrowUpward, "发送")
            }
        }
    }
}

/** Provider, key, model and sounds. Keys are saved per provider, so switching back and forth keeps each one. */
@Composable
fun AiSettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val close = LocalSheetClose.current; val cs = MaterialTheme.colorScheme; val scope = rememberCoroutineScope()
    var prov by rememberSaveable { mutableStateOf(OrionAi.provider(s.aiProvider).id) }
    val p = OrionAi.provider(prov)
    var key by remember(prov) { mutableStateOf(s.aiKeys[prov].orEmpty()) }
    var model by remember(prov) { mutableStateOf(s.aiModels[prov].orEmpty()) }
    var base by rememberSaveable { mutableStateOf(s.aiBaseUrl) }
    var local by rememberSaveable { mutableStateOf(s.aiLocalModel) }
    var sounds by rememberSaveable { mutableStateOf(s.aiSounds) }
    var reveal by remember { mutableStateOf(false) }
    var models by remember(prov) { mutableStateOf<List<String>?>(null) }; var loading by remember { mutableStateOf(false) }; var err by remember(prov) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SheetTitle("AI 设置", Sym.Key, "密钥只保存在本机，只发给你选择的服务商")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProviderPicker(p) { prov = it.id }
            if (p == AiProvider.LOCAL) LocalModels(local) { local = it }
            else {
                if (p == AiProvider.CUSTOM) OutlinedTextField(base, { base = it.trim() }, Modifier.fillMaxWidth(), label = { Text("服务器地址") },
                    placeholder = { Text("https://example.com/v1") }, supportingText = { Text("任何兼容 OpenAI 对话格式的服务器") }, singleLine = true, shape = RoundedCornerShape(20.dp))
                OutlinedTextField(key, { key = it.trim() }, Modifier.fillMaxWidth(), label = { Text("API 密钥") }, placeholder = { Text(p.keyHint) }, singleLine = true,
                    shape = RoundedCornerShape(20.dp), visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton({ reveal = !reveal }) { Icon(if (reveal) Sym.VisibilityOff else Sym.Visibility, if (reveal) "隐藏密钥" else "显示密钥") } })
                if (p.keyUrl.isNotEmpty()) TextButton({ vm.tracker.open(p.keyUrl) }) { Icon(Sym.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("获取 ${p.label} key") }
                OutlinedTextField(model, { model = it.trim() }, Modifier.fillMaxWidth(), label = { Text("模型") }, placeholder = { Text(p.model.ifEmpty { "模型名称" }) }, singleLine = true,
                    shape = RoundedCornerShape(20.dp), supportingText = { Text(if (p.model.isNotEmpty()) "留空即使用 ${p.model}" else "你的服务器所用的模型名称") },
                    trailingIcon = {
                        IconButton({
                            loading = true; err = null
                            scope.launch { runCatching { OrionAi.models(p, key, base) }.onSuccess { models = it; if (it.isEmpty()) err = "这个密钥没有返回任何对话模型。" }.onFailure { err = it.message }; loading = false }
                        }, enabled = !loading && (key.isNotBlank() || p == AiProvider.CUSTOM)) { if (loading) LoadingIndicator(Modifier.size(24.dp)) else Icon(Sym.FilterList, "浏览模型") }
                    })
                err?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
                models?.takeIf { it.isNotEmpty() }?.let { list ->
                    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer).verticalScroll(rememberScrollState())) {
                        list.forEach { id ->
                            Text(id, Modifier.fillMaxWidth().springClick(RoundedCornerShape(4.dp)) { model = id; models = null }.background(if (id == model) cs.secondaryContainer else cs.surfaceContainer)
                                .padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = if (id == model) cs.onSecondaryContainer else cs.onSurface)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TonalIcon(if (sounds) Sym.Notifications else Sym.NotificationsOff, cs.primary); Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("音效", style = MaterialTheme.typography.titleMedium)
                    Text("发送和收到回复时的清脆提示音", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Switch(sounds, { sounds = it; vm.haptic(H.SELECTION); if (it) AiSound.receive() })
            }
            CtaButton("保存", {
                vm.set { st ->
                    st.copy(aiProvider = prov, aiKeys = if (key.isBlank()) st.aiKeys - prov else st.aiKeys + (prov to key),
                        aiModels = if (model.isBlank()) st.aiModels - prov else st.aiModels + (prov to model), aiBaseUrl = base, aiLocalModel = local, aiSounds = sounds)
                }
                vm.haptic(H.SUCCESS); close()
            }, icon = Sym.Check)
        }
    }
}

private val CLOUD = AiProvider.entries - AiProvider.LOCAL
private val PROVIDER_NOTES = mapOf(AiProvider.GEMINI to "有免费额度", AiProvider.OPENAI to "GPT 系列模型", AiProvider.CLAUDE to "Claude 系列模型",
    AiProvider.OPENROUTER to "一个密钥、数百种模型", AiProvider.GROQ to "响应极快", AiProvider.DEEPSEEK to "低成本",
    AiProvider.MISTRAL to "来自欧洲", AiProvider.CUSTOM to "任意 OpenAI 兼容服务器")

/** Where Orion AI runs: two big choices, then one clean dropdown for the cloud provider. */
@Composable
private fun ProviderPicker(p: AiProvider, onPick: (AiProvider) -> Unit) {
    val cs = MaterialTheme.colorScheme; val local = p == AiProvider.LOCAL
    var lastCloud by rememberSaveable { mutableStateOf(if (local) AiProvider.GEMINI.id else p.id) }
    var open by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to (Sym.Cloud to "云端"), true to (Sym.PhoneAndroid to "本地")).forEach { (dev, look) ->
            val on = local == dev
            ToggleButton(on, { if (dev) onPick(AiProvider.LOCAL) else onPick(OrionAi.provider(lastCloud)) }, Modifier.weight(1f).heightIn(min = 52.dp),
                shapes = ToggleButtonDefaults.shapesFor(52.dp)) {
                Icon(look.first, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(look.second, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    if (!local) ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(p.label, {}, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), readOnly = true, singleLine = true,
            label = { Text("服务商") }, supportingText = { Text(PROVIDER_NOTES[p].orEmpty()) }, shape = RoundedCornerShape(20.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) })
        ExposedDropdownMenu(open, { open = false }, shape = RoundedCornerShape(20.dp), containerColor = cs.surfaceContainerHigh) {
            CLOUD.forEach { e ->
                DropdownMenuItem({ Column { Text(e.label, style = MaterialTheme.typography.titleSmall); Text(PROVIDER_NOTES[e].orEmpty(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) } },
                    { lastCloud = e.id; onPick(e); open = false },
                    trailingIcon = { if (e == p) Icon(Sym.Check, null, tint = cs.primary) })
            }
        }
    }
}

/** On-device models sized against this phone: download (resumable, in the background), pick one, or delete it to free space. */
@Composable
private fun LocalModels(picked: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current; val cs = MaterialTheme.colorScheme; val vm = LocalVm.current
    val ram = remember { LocalLlm.ramGb(ctx) }; val best = remember { LocalLlm.recommended(ctx) }
    val dl by Downloads.state.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val free = remember(dl, tick) { LocalLlm.freeMb() }
    Text(if (LocalLlm.supported) "这台手机：内存 $ram GB，可用 ${"%.1f".format(free / 1000.0)} GB。模型下载后可离线运行。手机越好回答越快，效果比云端模型简单一些。"
        else "On-device models need a 64-bit ARM phone. Pick a cloud provider instead.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
    if (!LocalLlm.supported) return
    val order = remember(best) { listOf(best) + (LocalLlm.MODELS - best) }
    val engine = remember(dl, tick) { LocalLlm.engineReady(ctx) }
    if (!engine && dl[LocalLlm.ENGINE_FILE]?.status == DlStatus.RUNNING) Text("先获取本地引擎（${LocalLlm.ENGINE_MB} MB） · ${dl[LocalLlm.ENGINE_FILE]?.progress ?: 0}%",
        style = MaterialTheme.typography.labelMedium, color = cs.primary)
    order.forEachIndexed { i, m ->
        val state = dl[m.fileName]; val running = state?.status == DlStatus.RUNNING
        val ready = remember(dl, tick) { LocalLlm.ready(m) }; val on = picked == m.id && ready
        Column(Modifier.padding(bottom = 2.dp).fillMaxWidth().clip(colShape(i, order.size)).background(if (on) cs.secondaryContainer else cs.surfaceContainer)
            .then(if (ready) Modifier.springClick(colShape(i, order.size)) { onPick(m.id) } else Modifier).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m == best) Box(Modifier.padding(end = 8.dp).size(22.dp).background(cs.primary, MaterialShapes.Sunny.toShape()), contentAlignment = Alignment.Center) {
                            Icon(Sym.StarFilled, "Best for this phone", Modifier.size(13.dp), tint = cs.onPrimary)
                        }
                        Text(m.name, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(if (m == best) "Best for this phone. ${m.blurb}" else m.blurb, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text((if (m.mb >= 1000) "%.1f GB".format(m.mb / 1000.0) else "${m.mb} MB") + " · for ${m.ramGb} GB+ phones" +
                        if (m.ramGb > ram) " · may be too big here" else "", style = MaterialTheme.typography.labelMedium, color = if (m.ramGb > ram) cs.error else cs.primary)
                }
                when {
                    running -> IconButton({ LocalLlm.cancel(m); tick++ }) { Icon(Sym.Close, "Cancel download") }
                    ready -> {
                        if (on) Icon(Sym.CheckCircleFilled, "In use", tint = cs.primary)
                        IconButton({ LocalLlm.delete(m); if (picked == m.id) onPick(""); tick++; vm.haptic(H.WARNING) }) { Icon(Sym.DeleteOutline, "Delete ${m.name}") }
                    }
                    else -> FilledTonalButton({
                        runCatching { LocalLlm.download(ctx, m); if (picked.isEmpty()) onPick(m.id); vm.haptic(H.MEDIUM) }.onFailure { vm.error("下载失败：无法开始。") }
                    }, enabled = m.mb + 200 < free, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) {
                        Icon(Sym.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (state?.status == DlStatus.FAILED) "Retry" else "Get")
                    }
                }
            }
            if (running) {
                val p = (state?.progress ?: 0) / 100f
                LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth().padding(top = 10.dp))
                Text("${state?.progress ?: 0}% · keeps going in the background", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}
