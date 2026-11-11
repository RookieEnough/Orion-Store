package com.orion.store.ui

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import com.orion.store.R
import com.orion.store.data.*
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun ErrorNote(msg: String) = AnimatedVisibility(msg.isNotEmpty()) {
    Surface(shape = RoundedCornerShape(16.dp), color = cs.errorContainer, contentColor = cs.onErrorContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.ErrorOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(msg, style = ty.bodyMedium)
        }
    }
}

/** Choice tile whose container morphs Circle → [to] when selected. */
@Composable
private fun MorphPick(selected: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 56.dp, to: RoundedPolygon = MaterialShapes.Cookie9Sided, color: Color = cs.primaryContainer, content: @Composable BoxScope.() -> Unit) {
    val morph = remember(to) { Morph(MaterialShapes.Circle, to) }
    val p by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pick")
    val bg by animateColorAsState(if (selected) color else cs.surfaceContainerHighest, MaterialTheme.motionScheme.fastEffectsSpec(), label = "pickc")
    Box(Modifier.size(size).clip(MorphShape(morph, p)).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun Section(step: Int, title: String, subtitle: String? = null, done: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Numbered cookie per step; it fills and turns into a check once the step has what it needs.
            Box(Modifier.size(36.dp).background(if (done) cs.primary else cs.primaryContainer, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) {
                if (done) Icon(Sym.Check, stringResource(R.string.xtra_done), Modifier.size(18.dp), tint = cs.onPrimary) else Text("$step", style = ty.titleSmallEmphasized, color = cs.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column { Text(title, style = ty.titleMediumEmphasized); subtitle?.let { Text(it, style = ty.bodySmall, color = cs.onSurfaceVariant) } }
        }
        content()
    }

@Composable
private fun fieldColors() = TextFieldDefaults.colors(focusedContainerColor = cs.surfaceContainerHighest, unfocusedContainerColor = cs.surfaceContainerHighest,
    disabledContainerColor = cs.surfaceContainerHigh, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent)

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, placeholder: String, modifier: Modifier = Modifier, lines: Int = 1, mono: Boolean = false,
                  enabled: Boolean = true, readOnly: Boolean = false, uri: Boolean = false, supporting: String? = null, trailing: (@Composable () -> Unit)? = null, onDone: (() -> Unit)? = null,
                  leading: (@Composable () -> Unit)? = null) =
    TextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = lines == 1, minLines = lines,
        enabled = enabled, readOnly = readOnly, shape = RoundedCornerShape(16.dp), colors = fieldColors(), leadingIcon = leading, trailingIcon = trailing, supportingText = supporting?.let { { Text(it) } },
        textStyle = if (mono) ty.bodySmall.copy(fontFamily = FontFamily.Monospace) else LocalTextStyle.current,
        keyboardOptions = KeyboardOptions(keyboardType = if (uri) KeyboardType.Uri else KeyboardType.Text, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }))

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

// ---------------- Ad donation quest ----------------

private const val PLACEMENT = "Rewarded_Android"
private const val AD_PROBE = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"
private val LOADING_MESSAGES = listOf(R.string.community_summoning_the_hero, R.string.community_sharpening_swords, R.string.community_establishing_uplink, R.string.community_buffering_video_stream, R.string.community_generating_terrain, R.string.community_loading_boss_assets)
private data class Card(val lvl: Int, @StringRes val title: Int, @StringRes val msg: Int, val icon: ImageVector, val color: Color)
private val Indigo = Color(0xFF6366F1)
private val STORY = listOf(
    Card(1, R.string.community_hero_awakens, R.string.community_you_open_your_eyes_the_digital, Sym.Shield, Blue),
    Card(2, R.string.community_slime_defeated, R.string.community_a_wild_glitch_slime_appeared_you, Sym.BugReport, Green),
    Card(3, R.string.community_golem_defeated, R.string.community_boss_battle_the_code_golem_crumbled, Sym.Diamond, Cyan),
    Card(4, R.string.community_loot_found, R.string.community_you_found_the_shield_of_syntax, Sym.Shield, Yellow),
    Card(5, R.string.community_troll_banished, R.string.community_a_forum_troll_blocked_the_bridge, Sym.Gavel, Red),
    Card(6, R.string.community_swarm_cleared, R.string.community_boss_battle_you_exterminated_the_bug, Sym.AutoAwesome, Pink),
    Card(7, R.string.community_level_up, R.string.community_you_learned_dark_mode_vision_you, Sym.Contrast, Gray),
    Card(8, R.string.community_dungeon_run, R.string.community_entered_the_server_room_dungeon_it, Sym.Dns, Indigo),
    Card(9, R.string.community_firewall_built, R.string.community_you_reinforced_the_defenses_intruders_will, Sym.GppGood, Orange),
    Card(10, R.string.community_rare_artifact, R.string.community_obtained_the_crystal_of_uptime_stability, Sym.ViewInAr, Purple),
    Card(11, R.string.community_approaching_boss, R.string.community_the_ground_shakes_something_massive_approaches, Sym.Warning, Red),
    Card(12, R.string.community_void_dragon_slayed, R.string.community_boss_battle_you_compiled_the_dragon, Sym.DarkMode, Purple),
    Card(13, R.string.community_respite, R.string.community_you_rest_by_the_digital_bonfire, Sym.LocalFireDepartment, Orange),
    Card(14, R.string.community_village_saved, R.string.community_the_open_source_village_is_safe, Sym.Home, Safe),
    Card(15, R.string.community_new_quest, R.string.community_the_kernel_king_has_requested_your, Sym.HistoryEdu, Yellow),
    Card(16, R.string.community_memory_leak, R.string.community_you_patched_a_massive_leak_in, Sym.FormatColorFill, Blue),
    Card(17, R.string.community_cloud_city, R.string.community_you_ascended_to_the_cloud_layer, Sym.Cloud, SkyC),
    Card(18, R.string.community_lightning_bolt, R.string.community_you_harnessed_the_power_of_raw, Sym.Bolt, Yellow),
    Card(19, R.string.community_time_travel, R.string.community_you_reverted_a_bad_commit_history, Sym.History, Pink),
    Card(20, R.string.community_max_level, R.string.community_you_reached_level_99_but_the, Sym.ArrowUpward, Green),
    Card(21, R.string.community_the_gate, R.string.community_standing_before_the_gateway_of_entropy, Sym.Fort, Gray),
    Card(22, R.string.community_first_seal, R.string.community_you_broke_the_seal_of_neglect, Sym.LinkOff, Red),
    Card(23, R.string.community_second_seal, R.string.community_you_broke_the_seal_of_greed, Sym.Paid, Yellow),
    Card(24, R.string.community_final_prep, R.string.community_potions_drunk_armor_polished_this_is, Sym.Science, Purple),
    Card(25, R.string.community_entropy_defeated, R.string.community_final_boss_you_saved_the_entire, Sym.RocketLaunch, Fuchsia),
    // Chapter 2 · The Forkening: a long road, beats spaced further apart the deeper it goes
    Card(26, R.string.community_the_forkening, R.string.community_entropy_s_last_breath_split_orion, Sym.AccountTree, Indigo),
    Card(27, R.string.community_fork_scout, R.string.community_a_tiny_fork_named_orion_final, Sym.Star, Yellow),
    Card(28, R.string.community_branch_maze, R.string.community_every_branch_looks_like_main_none, Sym.Explore, Green),
    Card(30, R.string.community_readme_oracle, R.string.community_an_ancient_readme_speaks_installation_it, Sym.MenuBook, Blue),
    Card(33, R.string.community_dependency_swamp, R.string.community_you_wade_through_4_000_node, Sym.WaterDrop, Cyan),
    Card(36, R.string.community_semicolon_smuggler, R.string.community_caught_a_goblin_sneaking_semicolons_into, Sym.Gavel, Red),
    Card(40, R.string.community_hydra_tracks, R.string.community_footprints_with_seven_heads_each_stamped, Sym.Warning, Orange),
    Card(44, R.string.community_rebase_ritual, R.string.community_you_rewrote_history_so_cleanly_that, Sym.History, Purple),
    Card(48, R.string.community_the_diff_gate, R.string.community_a_gate_that_only_opens_for, Sym.Fort, Gray),
    Card(50, R.string.community_hydra_merged, R.string.community_boss_battle_cut_one_conflict_and, Sym.CallMerge, Teal),
    Card(55, R.string.community_camp_of_forks, R.string.community_the_forks_you_united_cook_dinner, Sym.LocalFireDepartment, Orange),
    Card(60, R.string.community_overflow_cliffs, R.string.community_you_climb_using_answers_marked_duplicate, Sym.TrendingUp, Yellow),
    Card(66, R.string.community_cache_miss_canyon, R.string.community_you_shout_into_the_canyon_the, Sym.Speed, SkyC),
    Card(72, R.string.community_ancient_comment, R.string.community_todo_fix_later_2009_you_fixed, Sym.HistoryEdu, Indigo),
    Card(80, R.string.community_abyss_of_legacy, R.string.community_the_map_ends_here_below_lies, Sym.Tsunami, Blue),
    Card(88, R.string.community_noodle_tentacles, R.string.community_something_long_tangled_and_global_wraps, Sym.Warning, Red),
    Card(95, R.string.community_deep_breath, R.string.community_you_open_the_file_the_scrollbar, Sym.Psychology, Purple),
    Card(100, R.string.community_leviathan_untangled, R.string.community_boss_battle_you_refactored_the_spaghetti, Sym.RamenDining, Caution),
    Card(110, R.string.community_test_garden, R.string.community_you_plant_unit_tests_green_checks, Sym.TaskAlt, Green),
    Card(125, R.string.community_throne_rumors, R.string.community_whispers_in_the_logs_someone_seized, Sym.Campaign, Yellow),
    Card(140, R.string.community_permission_denied, R.string.community_the_emperor_s_guards_block_every, Sym.Lock, Red),
    Card(155, R.string.community_shell_storm, R.string.community_you_type_faster_than_the_guards, Sym.Terminal, Cyan),
    Card(170, R.string.community_kernel_panic_plains, R.string.community_the_ground_flashes_red_and_reboots, Sym.Bolt, Orange),
    Card(185, R.string.community_old_friends, R.string.community_the_kernel_king_the_village_and, Sym.Group, Indigo),
    Card(195, R.string.community_null_throne, R.string.community_the_null_emperor_rises_his_crown, Sym.Castle, Gray),
    Card(200, R.string.community_root_reclaimed, R.string.community_final_boss_you_dethroned_the_null, Sym.Crown, Color(0xFFE11D48)),
)
/** Between Chapter 2 beats the road still answers every ad, so the grind never shows a blank card. */
private val MARCH = listOf(
    Card(0, R.string.community_wandering_bug, R.string.community_a_stray_bug_crossed_your_path, Sym.BugReport, Green),
    Card(0, R.string.community_supply_drop, R.string.community_a_crate_of_coffee_fell_from, Sym.Coffee, Orange),
    Card(0, R.string.community_side_quest, R.string.community_you_helped_a_lost_pull_request, Sym.Forum, Blue),
    Card(0, R.string.community_night_patrol, R.string.community_you_patrolled_the_forks_all_quiet, Sym.Shield, Indigo),
    Card(0, R.string.community_training_arc, R.string.community_you_practiced_shortcuts_until_your_fingers, Sym.Bolt, Yellow),
    Card(0, R.string.community_lucky_find, R.string.community_a_regex_worked_on_the_first, Sym.Stars, Pink),
)
private val END_CARD = Card(999, R.string.community_sovereign_duty, R.string.community_root_is_safe_and_the_realm, Sym.Star, Indigo)
private fun storyCard(lvl: Int) = STORY.firstOrNull { it.lvl == lvl } ?: if (lvl <= QUEST_BOSSES.last().lvl) MARCH[lvl % MARCH.size] else END_CARD

/** Ad-quest bosses: the level that beats each one and the badge it drops. Shared by the quest, the About rank card and the badge archive. */
data class QuestBoss(val lvl: Int, val name: String, val badge: String, val rank: String = badge)
val QUEST_BOSSES = listOf(
    QuestBoss(3, "Code Golem", "Backer"), QuestBoss(6, "Bug Swarm Queen", "Supernova"), QuestBoss(12, "Void Dragon", "Void Walker"),
    QuestBoss(25, "Entropy", "Guardian", "Cosmic Guardian"),
    QuestBoss(50, "Merge Conflict Hydra", "Merge Tamer"), QuestBoss(100, "Spaghetti Leviathan", "Spaghetti Slayer"), QuestBoss(200, "Null Emperor", "Sudo Sovereign"),
)
/** Chapter 1 ends with Entropy; everything after it is Chapter 2. */
const val CHAPTER_ONE_END = 25
fun questChapter(ads: Int) = if (ads >= CHAPTER_ONE_END) 2 else 1

/** Progress from the last boss beaten to the next one, for the camp screen and victory cards. */
@Composable
private fun NextBoss(ads: Int) {
    val next = QUEST_BOSSES.firstOrNull { ads < it.lvl } ?: return
    val from = QUEST_BOSSES.lastOrNull { ads >= it.lvl }?.lvl ?: 0
    val target = (ads - from).toFloat() / (next.lvl - from)
    var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(if (shown) target else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "boss")
    val (bg, fg) = tonal(BADGES[next.badge]?.second ?: cs.primary)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(fg.copy(.14f), MaterialShapes.Cookie7Sided.toShape()), contentAlignment = Alignment.Center) {
                Icon(BADGES[next.badge]?.first ?: Sym.Lock, null, Modifier.size(24.dp), tint = fg)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(stringResource(R.string.community_next_boss), style = ty.labelMedium, color = fg.copy(.8f))
                Text(next.name, style = ty.titleMediumEmphasized, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(stringResource(R.string.community_to_go, next.lvl - ads), style = ty.labelLargeEmphasized, color = fg)
        }
        LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth(), color = fg, trackColor = fg.copy(.2f))
        Text(stringResource(R.string.community_drops_the_badge, next.badge), style = ty.bodySmall, color = fg.copy(.85f))
    }
}

/** Unity starts on first use: initializing at boot spun up its WebView on the main thread and hitched the first seconds. */
private suspend fun unityLoad(ctx: Context) {
    // Its startup hook is removed from the manifest so Unity never runs at app start; it runs here, on the first ad
    if (!UnityAds.isInitialized) androidx.startup.AppInitializer.getInstance(ctx.applicationContext).initializeComponent(com.unity3d.services.core.configuration.AdsSdkInitializer::class.java)
    if (!UnityAds.isInitialized) suspendCancellableCoroutine { c ->
        UnityAds.initialize(ctx.applicationContext, Defaults.UNITY_GAME_ID, false, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() { if (c.isActive) c.resume(Unit) }
            override fun onInitializationFailed(e: UnityAds.UnityAdsInitializationError?, m: String?) { if (c.isActive) c.resumeWithException(Exception(m ?: e?.name ?: "init failed")) }
        })
    }
    unityLoadAd()
}

private suspend fun unityLoadAd() = suspendCancellableCoroutine { c ->
    UnityAds.load(PLACEMENT, object : IUnityAdsLoadListener {
        override fun onUnityAdsAdLoaded(p: String?) { if (c.isActive) c.resume(Unit) }
        override fun onUnityAdsFailedToLoad(p: String?, e: UnityAds.UnityAdsLoadError?, m: String?) { if (c.isActive) c.resumeWithException(Exception(m ?: e?.name ?: "load failed")) }
    })
}

private class AdSkipped : Exception("skipped")

private suspend fun unityShow(a: Activity) = suspendCancellableCoroutine { c ->
    UnityAds.show(a, PLACEMENT, UnityAdsShowOptions(), object : IUnityAdsShowListener {
        override fun onUnityAdsShowFailure(p: String?, e: UnityAds.UnityAdsShowError?, m: String?) { if (c.isActive) c.resumeWithException(Exception(m ?: e?.name ?: "show failed")) }
        override fun onUnityAdsShowStart(p: String?) = Unit
        override fun onUnityAdsShowClick(p: String?) = Unit
        // A skipped ad funds nothing, so it earns nothing
        override fun onUnityAdsShowComplete(p: String?, s: UnityAds.UnityAdsShowCompletionState?) {
            if (!c.isActive) return
            if (s == UnityAds.UnityAdsShowCompletionState.COMPLETED) c.resume(Unit) else c.resumeWithException(AdSkipped())
        }
    })
}

@Composable
fun AdDonationSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val s = LocalS.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf("checking") }; var reason by remember { mutableStateOf("") }
    var msg by remember { mutableIntStateOf(0) }
    val streak = s.adWatchCount; val result = maxOf(streak, 1)
    val card = storyCard(result); val prev = if (streak > 0) storyCard(streak) else null
    val boss = QUEST_BOSSES.any { it.lvl == result }
    LaunchedEffect(Unit) {
        reason = when {
            ctx.getSystemService(ConnectivityManager::class.java)?.activeNetwork == null -> ctx.getString(R.string.community_no_internet_connection)
            else -> {
                val sec = withContext(Dispatchers.IO) { runCatching { vm.tracker.networkSecurity() }.getOrNull() }
                when { sec?.isVpnActive == true -> ctx.getString(R.string.community_vpn_detected); sec?.hasProxy == true -> ctx.getString(R.string.community_proxy_filter_detected); !Net.alive(AD_PROBE, 3000) -> ctx.getString(R.string.community_ad_blocker_detected); else -> "" }
            }
        }
        val limit = vm.s.adBlockReason(System.currentTimeMillis())
        when {
            reason.isNotEmpty() -> view = "violation"
            limit != null -> { reason = limit; view = "noad" }
            else -> { launch { runCatching { unityLoad(ctx) } }; view = "intro" }
        }
    }
    LaunchedEffect(view) { if (view == "loading") { msg = 0; while (true) { delay(1200); msg = (msg + 1) % LOADING_MESSAGES.size } } }
    // Reaching Chapter 2 in the sheet counts as being told about it, so no "new mission" notice follows.
    // Only a fully watched ad pays out, and only within the daily limits: no ad, a skipped ad or a failed load earns nothing.
    fun complete() {
        val before = vm.s.adWatchCount
        vm.set { it.incrementAdWatch(System.currentTimeMillis()).let { n -> if (questChapter(n.adWatchCount) > n.questChapterSeen) n.copy(questChapterSeen = questChapter(n.adWatchCount)) else n } }
        if (vm.s.adWatchCount > before) { vm.haptic(H.SUCCESS); view = "success" } else { reason = vm.s.adBlockReason(System.currentTimeMillis()) ?: ctx.getString(R.string.community_try_again_later); view = "noad" }
    }
    fun noAd(e: Exception) {
        reason = if (e is AdSkipped) ctx.getString(R.string.community_the_ad_was_skipped_watch_it) else ctx.getString(R.string.community_no_ad_is_available_right_now)
        view = "noad"; vm.haptic(H.ERROR)
    }
    fun play() { scope.launch {
        val a = ctx.activity() ?: return@launch noAd(Exception())
        try { unityShow(a); complete() } catch (e: AdSkipped) { noAd(e) } catch (_: Exception) {
            view = "loading"
            try { unityLoad(ctx); delay(6000); unityShow(a); complete() } catch (e: Exception) { noAd(e) }
        }
    } }
    OrionSheet(onDismiss) {
        val close = LocalSheetClose.current
        val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(view to streak, transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "quest") { (v, _) ->
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (v) {
                    "checking" -> { LoadingIndicator(Modifier.size(96.dp)); Text(stringResource(R.string.community_verifying_integrity), style = ty.titleMediumEmphasized) }
                    "violation" -> {
                        Lead(stringResource(R.string.community_signal_jammed), stringResource(R.string.community_to_protect_the_economy_from_farming), reason, cs.error, center = true) {
                            Blob(Sym.GppBad, cs.error, 88, MaterialShapes.Cookie7Sided)
                        }
                        CtaButton(stringResource(R.string.community_understand_return), close, tonal = true)
                    }
                    "intro" -> if (streak == 0) {
                        Lead(stringResource(R.string.community_the_developer_s_quest), stringResource(R.string.community_you_are_the_reason_this_project), stringResource(R.string.community_chapter_1), center = true) {
                            Blob(Sym.VolunteerActivism, cs.primary, 88, MaterialShapes.Sunny)
                        }
                        CtaButton(stringResource(R.string.community_i_m_a_hero), { view = "urgent"; vm.haptic(H.MEDIUM) }, icon = Sym.Bolt)
                        TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_cancel)) }
                    } else if (streak == CHAPTER_ONE_END) {
                        var k by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) { delay(150); k = 1 }
                        MorphBlob(listOf(MaterialShapes.Circle, MaterialShapes.Cookie12Sided), k, 128.dp, tonal(Indigo).first) {
                            Icon(Sym.AccountTree, null, Modifier.size(56.dp), tint = tonal(Indigo).second)
                        }
                        Lead(stringResource(R.string.community_the_forkening), stringResource(R.string.community_entropy_is_gone_but_its_last),
                            stringResource(R.string.community_chapter_2_new_mission), Fuchsia, center = true)
                        NextBoss(streak)
                        CtaButton(stringResource(R.string.community_begin_chapter_2), ::play, icon = Sym.Swords)
                        TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.community_not_yet)) }
                    } else {
                        Lead(stringResource(R.string.community_resting_at_camp), stringResource(R.string.community_you_ve_restored_your_health_by),
                            stringResource(R.string.community_chapter_level, questChapter(streak), streak), Orange, center = true) {
                            Blob(Sym.LocalFireDepartment, Orange, 88, MaterialShapes.Cookie12Sided)
                        }
                        NextBoss(streak)
                        prev?.let { p ->
                            Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
                                ListItem({ Text(stringResource(p.title), fontWeight = FontWeight.SemiBold) }, overlineContent = { Text(stringResource(R.string.community_previously)) }, leadingContent = { Blob(p.icon, p.color, 40) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                            }
                        }
                        CtaButton(stringResource(R.string.community_continue_journey), ::play, icon = Sym.PlayArrow)
                        TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.community_stay_at_camp_close)) }
                    }
                    "urgent" -> {
                        RemoteIcon(Defaults.DEV_PROFILE.image.orEmpty(), "R", 96.dp, shape = MaterialShapes.Cookie9Sided.toShape())
                        Text(stringResource(R.string.community_orion_is_in_danger), style = ty.headlineSmallEmphasized, color = cs.error, textAlign = TextAlign.Center)
                        Text(buildAnnotatedString {
                            append(stringResource(R.string.community_listen_to_me_the_entropy_legion))
                            withStyle(SpanStyle(color = cs.error, fontWeight = FontWeight.Black)) { append(stringResource(R.string.community_right_now)) }; append("”")
                        }, style = ty.bodyLarge, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.community_rookiez_lead_dev), style = ty.labelLarge, color = cs.onSurfaceVariant)
                        CtaButton(stringResource(R.string.community_accept_the_quest), ::play, icon = Sym.Bolt)
                        TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.community_i_need_time)) }
                    }
                    "loading" -> {
                        LoadingIndicator(Modifier.size(112.dp))
                        Text(stringResource(R.string.community_engaging_enemy), style = ty.headlineSmallEmphasized)
                        AnimatedContent(msg, label = "lm") { Text(stringResource(LOADING_MESSAGES[it]), style = ty.bodyLarge, color = cs.onSurfaceVariant) }
                        Text(stringResource(R.string.community_connecting_to_unity_network), style = ty.bodySmall, color = cs.onSurfaceVariant)
                    }
                    "noad" -> {
                        Lead(stringResource(R.string.community_mission_aborted), reason, stringResource(R.string.community_no_xp_this_time), Caution, center = true) { Blob(Sym.Movie, Caution, 88, MaterialShapes.Cookie6Sided) }
                        CtaButton(stringResource(R.string.community_back_to_camp), close, tonal = true)
                    }
                    else -> {
                        var k by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) { delay(120); k = 1 }
                        Box(contentAlignment = Alignment.BottomCenter) {
                            MorphBlob(listOf(MaterialShapes.Circle, if (boss) MaterialShapes.SoftBurst else MaterialShapes.Cookie9Sided), k, 136.dp, card.color.copy(.18f)) {
                                Icon(card.icon, null, Modifier.size(56.dp), tint = card.color)
                            }
                            if (boss) Surface(Modifier.offset(y = 10.dp), shape = Pill, color = cs.tertiary, contentColor = cs.onTertiary) {
                                Text(stringResource(R.string.community_victory), Modifier.padding(horizontal = 14.dp, vertical = 4.dp), style = ty.labelLargeEmphasized)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(card.title), style = ty.headlineMediumEmphasized, textAlign = TextAlign.Center)
                        Text(stringResource(card.msg), style = ty.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                        NextBoss(streak)
                        CtaButton(stringResource(R.string.community_continue_quest), ::play, icon = Sym.PlayArrow)
                        TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.community_rest_at_camp_close)) }
                    }
                }
            }
        }
    }
}

// ---------------- Profile & badges ----------------

private val AVATAR_CHOICES = CHARACTERS
private val DINO_BADGES = listOf(200L to "Dino Rookie", 600L to "Dino Master", 1500L to "Dino Legend")
/** Archive sections in display order; quest badges follow the boss order so the grind reads left to right. */
private val BADGE_GROUPS: List<Pair<Int, List<String>>> = listOf(
    R.string.about_fuel_the_code to QUEST_BOSSES.map { it.badge },
    R.string.community_dino_run to DINO_BADGES.map { it.second },
    R.string.community_pilot_ranks to TIER_BADGES.map { it.second },
    R.string.community_collections to COSMETIC_BADGES.map { it.first },
    R.string.guide_secrets to listOf("Legend", "Dev Mode"),
)
private val PROFILE_BADGES = BADGE_GROUPS.flatMap { it.second }

private fun badgeUnlocked(id: String, s: Settings) = COSMETIC_BADGES.firstOrNull { it.first == id }?.second?.all { it.unlocked(s) }
    ?: QUEST_BOSSES.firstOrNull { it.badge == id }?.let { s.adWatchCount >= it.lvl } ?: when (id) {
    "Legend" -> s.isLegend; "Dev Mode" -> s.isDevUnlocked; else -> id in s.unlockedBadges
}

/** Counted badges report (now, goal); secrets have none. */
private fun badgeProgress(id: String, s: Settings): Pair<Long, Long>? =
    QUEST_BOSSES.firstOrNull { it.badge == id }?.let { s.adWatchCount.toLong() to it.lvl.toLong() }
        ?: DINO_BADGES.firstOrNull { it.second == id }?.let { s.dinoHighScore to it.first }
        ?: COSMETIC_BADGES.firstOrNull { it.first == id }?.second?.let { set -> set.sumOf { it.progress(s) }.toLong() to set.sumOf { it.ads }.toLong() }

@Composable
private fun badgeHint(id: String) = QUEST_BOSSES.firstOrNull { it.badge == id }?.let { stringResource(R.string.community_defeat_the_in_fuel_the_code_takes, it.name, it.lvl) }
    ?: DINO_BADGES.firstOrNull { it.second == id }?.let { stringResource(R.string.community_score_in_a_single_dino_run, it.first) }
    ?: when (id) { "Icon Collector" -> stringResource(R.string.community_unlock_the_neon_and_gold_app_icons); "Dock Master" -> stringResource(R.string.community_unlock_the_vibrant_and_shape_shift_docks); else -> null }
    ?: TIER_BADGES.firstOrNull { it.second == id }?.let { stringResource(R.string.community_reach_level_every_ad_app_and, it.first) }
    ?: when (id) { "Legend" -> stringResource(R.string.community_finish_chapter_1_of_fuel_the_code); "Dev Mode" -> stringResource(R.string.community_some_version_numbers_love_being_tapped); else -> "" }


@Composable
fun ProfileStatsSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val d = LocalD.current; val close = LocalSheetClose.current
    val select = remember { vm.profileView == "badges" }; val index = vm.badgeIndex
    var badges by rememberSaveable { mutableStateOf(select) }
    val sp = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>(); val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(badges, transitionSpec = {
        val dir = if (targetState) 1 else -1
        (slideInHorizontally(sp) { it * dir } + fadeIn(fx)) togetherWith (slideOutHorizontally(sp) { -it * dir } + fadeOut(fx))
    }, label = "profile") { b ->
        if (b) BadgeArchive(vm, s, select, index, onBack = { if (select) close() else badges = false }, onPicked = close)
        else ProfileView(vm, s, d) { badges = true }
    }
}


/** Holographic pilot ID: tilts with the phone and under the finger, a rainbow foil and a specular glint follow the light. */
@Composable
private fun PilotCard(s: Settings, name: String, avatarUrl: String, onAvatar: () -> Unit) {
    val cs = MaterialTheme.colorScheme; val tilt = rememberTilt(); val scope = rememberCoroutineScope()
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }; val back = MaterialTheme.motionScheme.slowSpatialSpec<Offset>()
    val still = s.calm
    val foil by rememberInfiniteTransition(label = "foil").animateFloat(0f, if (still) 0f else 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "f")
    val xp = Xp.total(s, LocalD.current.appLibrary.size); val level = Xp.level(xp)
    val target = ((xp - Xp.start(level)).toFloat() / Xp.step(level)).coerceIn(0f, 1f)
    // Metal skins from the tier levels on; below Bronze the card wears the theme
    val skin = when {
        level >= 50 -> listOf(Color(0xFFFFE89A), Color(0xFFF2C14E), Color(0xFFC8962E)) to Color(0xFF2E2205)
        level >= 30 -> listOf(Color(0xFFF4F6F8), Color(0xFFC3C9D0), Color(0xFF8F979F)) to Color(0xFF1C2127)
        level >= 10 -> listOf(Color(0xFFF0B27A), Color(0xFFC07A3E), Color(0xFF8A5226)) to Color(0xFF2A1406)
        else -> listOf(cs.primaryContainer, androidx.compose.ui.graphics.lerp(cs.primaryContainer, cs.secondaryContainer, .55f), cs.primaryContainer) to cs.onPrimaryContainer
    }
    val on = skin.second
    var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
    val ring by animateFloatAsState(if (shown) target else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "ring")
    val shape = RoundedCornerShape(32.dp)
    val since = s.userProfile?.createdAt?.takeIf { it > 0 }?.let { java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: stringResource(R.string.community_today)
    val vm = LocalVm.current
    var flipped by remember { mutableStateOf(false) }
    val flip by animateFloatAsState(if (flipped) 180f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "flip")
    // Signing needs a still card: no sensor tilt or drag tilt while the back is up
    val tiltNow = { if (flipped) Offset.Zero else tilt.value }
    val serial = remember(name, s.userProfile?.createdAt) { (name + s.userProfile?.createdAt).hashCode().toUInt().toString().padStart(10, '0').chunked(4).joinToString(" · ") }
    // Turn of the card in degrees: past edge-on it swings back toward the viewer instead of showing a mirrored back face
    fun turnY(): Float { val t = tiltNow(); val face = if (flip > 90f) flip - 180f else flip; return face + (t.x * 12f + drag.value.x / 10f).coerceIn(-22f, 22f) }
    fun turnX(): Float = (-tiltNow().y * 10f - drag.value.y / 10f).coerceIn(-22f, 22f)
    Box(Modifier.fillMaxWidth().aspectRatio(1.586f)) {
    // The shadow lives on its own flat layer that only narrows with the turn. A shadow cast by the 3D-turned card itself is projected
    // by the renderer and smears into stretched black streaks below the card, worst in light mode.
    Box(Modifier.matchParentSize().graphicsLayer {
        scaleX = kotlin.math.abs(kotlin.math.cos(Math.toRadians(turnY().toDouble()))).toFloat()
        scaleY = kotlin.math.abs(kotlin.math.cos(Math.toRadians(turnX().toDouble()))).toFloat()
        shadowElevation = 18.dp.toPx() * scaleX; this.shape = shape; clip = false
    })
    Box(Modifier.matchParentSize()
        .pointerInput(Unit) { detectTapGestures { vm.haptic(H.LIGHT); flipped = !flipped } }
        .pointerInput(flipped) {
            if (!flipped) detectDragGestures(onDragEnd = { scope.launch { drag.animateTo(Offset.Zero, back) } }, onDragCancel = { scope.launch { drag.animateTo(Offset.Zero, back) } }) { c, dv ->
                c.consume(); scope.launch { drag.snapTo(drag.value + dv) }
            }
        }
        .graphicsLayer {
            rotationY = turnY(); rotationX = turnX()
            // Offscreen keeps the foil's overlay blend inside the card and gives the turned edges clean antialiasing
            cameraDistance = 14 * density; this.shape = shape; clip = true; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
        }
        .background(Brush.linearGradient(skin.first))
        .drawWithContent {
            drawContent()
            val t = tiltNow(); val dr = drag.value
            val shift = (t.x + dr.x / 300f + foil * 2f) * size.width
            drawRect(Brush.linearGradient(listOf(Color(0x00FF6EC7), Color(0x66FF6EC7), Color(0x6600E5FF), Color(0x66B8FF5C), Color(0x66FFD25C), Color(0x00FFD25C)),
                start = Offset(shift - size.width, 0f), end = Offset(shift, size.height), tileMode = androidx.compose.ui.graphics.TileMode.Mirror),
                blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(.35f), Color.Transparent), center = Offset(size.width * (.5f - t.x * .6f), size.height * (.3f + t.y * .6f)), radius = size.width * .55f),
                radius = size.width, center = Offset(size.width * (.5f - t.x * .6f), size.height * (.3f + t.y * .6f)))
        }) {
        Box(Modifier.align(Alignment.TopEnd).offset(60.dp, (-50).dp).size(200.dp).background(on.copy(.06f), MaterialShapes.Cookie12Sided.toShape()))
        CompositionLocalProvider(LocalContentColor provides on) {
            if (flip > 90f) SignatureSide(s, Modifier.fillMaxSize().padding(16.dp)) { vm.set(it) }
            else Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(com.orion.store.R.drawable.ic_splash_logo), null, Modifier.size(28.dp))
                    Text(if (level >= 10) stringResource(R.string.community_orion_pilot, Xp.title(level).substringBefore(' ').uppercase()) else stringResource(R.string.community_orion_pilot_2), Modifier.padding(start = 6.dp), style = ty.labelLarge, letterSpacing = 2.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Surface(shape = Pill, color = on, contentColor = skin.first[0]) { Text(stringResource(R.string.community_lv, level), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = ty.labelLargeEmphasized) }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(78.dp).clip(CircleShape).clickable(onClick = onAvatar), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator({ ring }, Modifier.fillMaxSize(), color = on, trackColor = on.copy(.18f))
                        AsyncImage(pngAvatar(avatarUrl), stringResource(R.string.community_change_avatar), Modifier.size(60.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.surface.copy(.6f)), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name.ifBlank { stringResource(R.string.community_unnamed_pilot) }, style = ty.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.community_xp, Xp.title(level), xp), style = ty.labelLarge, color = LocalContentColor.current.copy(.8f))
                    }
                }
                Spacer(Modifier.weight(1f))
                Row {
                    Text(serial, Modifier.weight(1f), style = ty.labelMedium, fontFamily = FontFamily.Monospace, color = LocalContentColor.current.copy(.75f))
                    Text(stringResource(R.string.community_since, since), style = ty.labelMedium, color = LocalContentColor.current.copy(.75f))
                }
            }
        }
    }
}
}

private val INKS = listOf(Color(0xFF1B1B1F), Color(0xFF1D4ED8), Color(0xFFDC2626), Color(0xFF15803D), Color(0xFF7C3AED), Color(0xFFB45309))

/** Back of the pilot card: a signature strip on paper-white so every ink reads on every skin. */
@Composable
private fun SignatureSide(s: Settings, modifier: Modifier, update: ((Settings) -> Settings) -> Unit) = Column(modifier) {
    val ink = INKS[s.signatureInk.coerceIn(0, INKS.lastIndex)]
    // Every stroke, clear and ink change is one step: undo walks back through them, redo forward again until a new step is made
    val past = remember { mutableStateListOf<Pair<String, Int>>() }; val future = remember { mutableStateListOf<Pair<String, Int>>() }
    fun apply(to: Pair<String, Int>) = update { it.copy(pilotSignature = to.first, signatureInk = to.second) }
    fun step(to: Pair<String, Int>) { past += s.pilotSignature to s.signatureInk; future.clear(); apply(to) }
    fun undo() { future += s.pilotSignature to s.signatureInk; apply(past.removeAt(past.lastIndex)) }
    fun redo() { past += s.pilotSignature to s.signatureInk; apply(future.removeAt(future.lastIndex)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.community_pilot_signature), Modifier.weight(1f), style = ty.labelLarge, letterSpacing = 2.sp, fontWeight = FontWeight.Black)
        IconButton(::undo, Modifier.size(32.dp), enabled = past.isNotEmpty()) { Icon(Sym.Undo, stringResource(R.string.detail_undo), Modifier.size(20.dp)) }
        IconButton(::redo, Modifier.size(32.dp), enabled = future.isNotEmpty()) { Icon(Sym.Redo, stringResource(R.string.community_redo), Modifier.size(20.dp)) }
        if (s.pilotSignature.isNotEmpty()) Text(stringResource(R.string.common_clear), Modifier.clip(Pill).clickable { step("" to s.signatureInk) }.padding(horizontal = 10.dp, vertical = 4.dp),
            style = ty.labelLargeEmphasized)
    }
    SignaturePad(s.pilotSignature, ink, Modifier.padding(vertical = 10.dp).weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFAFAF7))) { sig ->
        step(sig to s.signatureInk)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        INKS.forEachIndexed { i, c ->
            val on = i == s.signatureInk
            val inkLabel = stringResource(R.string.community_ink, i + 1)
            Box(Modifier.size(28.dp).then(if (on) Modifier.border(2.dp, LocalContentColor.current, CircleShape) else Modifier).padding(if (on) 4.dp else 0.dp)
                .clip(CircleShape).background(c).clickable { if (!on) step(s.pilotSignature to i) }.semantics { contentDescription = inkLabel; selected = on })
        }
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.community_tap_to_flip), style = ty.labelSmall, color = LocalContentColor.current.copy(.7f))
    }
}

/** Finger-drawn strokes kept as normalized points; saved once per lifted stroke. */
@Composable
private fun SignaturePad(saved: String, ink: Color, modifier: Modifier, onSave: (String) -> Unit) {
    val strokes = remember(saved) { saved.split('|').filter { it.isNotBlank() }.map { st -> st.split(' ').mapNotNull { p -> p.split(',').takeIf { it.size == 2 }?.let { Offset(it[0].toFloat(), it[1].toFloat()) } } } }
    var live by remember { mutableStateOf(emptyList<Offset>()) }
    fun enc(all: List<List<Offset>>) = all.takeLast(40).joinToString("|") { st -> st.joinToString(" ") { "%.3f,%.3f".format(java.util.Locale.US, it.x, it.y) } }
    Canvas(modifier
        .pointerInput(saved) {
            fun n(p: Offset) = Offset((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
            detectDragGestures(onDragStart = { live = listOf(n(it)) }, onDragEnd = { if (live.size > 1) onSave(enc(strokes + listOf(live))); live = emptyList() },
                onDragCancel = { live = emptyList() }) { c, _ -> c.consume(); val p = n(c.position); if ((p - live.last()).getDistance() > .004f) live = live + p }
        }
        // Swallows taps so a stray dot on the strip does not flip the card
        .pointerInput(Unit) { detectTapGestures { } }) {
        val w = 3.dp.toPx()
        (strokes + listOf(live)).forEach { st ->
            if (st.size < 2) return@forEach
            val path = Path().apply { moveTo(st[0].x * size.width, st[0].y * size.height); st.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) } }
            drawPath(path, ink, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (strokes.isEmpty() && live.isEmpty()) drawLine(ink.copy(.25f), Offset(16.dp.toPx(), size.height * .72f), Offset(size.width - 16.dp.toPx(), size.height * .72f), 1.dp.toPx())
    }
}

/** A stat that counts up when the sheet opens. */
@Composable
private fun CountStat(icon: androidx.compose.ui.graphics.vector.ImageVector, c: Color, value: Int, label: String, modifier: Modifier, suffix: String = "", prefix: String = "") {
    val (bg, fg) = tonal(c)
    var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
    val n by animateIntAsState(if (shown) value else 0, tween(1100, easing = FastOutSlowInEasing), label = "count")
    Row(modifier.clip(RoundedCornerShape(24.dp)).background(bg).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(fg.copy(.14f), MaterialShapes.Cookie6Sided.toShape()), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = fg) }
        Spacer(Modifier.width(12.dp))
        // Narrow screens and large font scales shrink the text to fit instead of cutting "Downloaded" short
        Column(Modifier.weight(1f)) {
            Text("$prefix$n$suffix", style = ty.titleLargeEmphasized, color = fg, maxLines = 1, autoSize = TextAutoSize.StepBased(12.sp, ty.titleLargeEmphasized.fontSize))
            Text(label, style = ty.labelMedium, color = fg.copy(.8f), maxLines = 1, autoSize = TextAutoSize.StepBased(8.sp, ty.labelMedium.fontSize))
        }
    }
}

@Composable
private fun ProfileView(vm: MainViewModel, s: Settings, d: DataState, openBadges: () -> Unit) {
    val profile = s.userProfile
    var picker by remember { mutableStateOf(false) }; var temp by remember { mutableStateOf(AVATAR_CHOICES[0]) }; var name by rememberSaveable { mutableStateOf("") }
    val showcased = PROFILE_BADGES.filter { it in s.showcasedBadges && badgeUnlocked(it, s) }.take(3)
    val unlocked = PROFILE_BADGES.count { badgeUnlocked(it, s) }
    fun pick(a: Avatar) {
        if (profile != null) vm.set { it.withProfile(profile.copy(avatarId = a.id, avatarUrl = a.url), System.currentTimeMillis()) } else temp = a
        picker = false; vm.haptic(H.SELECTION)
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PilotCard(s, profile?.name ?: name, profile?.avatarUrl ?: temp.url) { picker = !picker; vm.haptic(H.LIGHT) }
        Text(stringResource(R.string.community_tilt_or_drag_the_card_tap), Modifier.fillMaxWidth(), style = ty.labelMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        AnimatedVisibility(picker, enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(), exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(AVATAR_CHOICES, key = { it.id }) { a ->
                    MorphPick((profile?.avatarId ?: temp.id) == a.id, { pick(a) }, size = 64.dp) {
                        AsyncImage(pngAvatar(a.url), stringResource(a.name), Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                    }
                }
            }
        }
        // Top, not center: the field's supporting text would otherwise push the button below the field's line
        if (profile == null) Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(15) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.community_set_your_alien_alias)) }, singleLine = true, shape = RoundedCornerShape(28.dp),
                supportingText = { Text(stringResource(R.string.community_15_at_least_3, name.trim().length)) })
            Button({ vm.set { it.withProfile(UserProfile(name.trim(), temp.id, temp.url, System.currentTimeMillis()), System.currentTimeMillis()) }; picker = false; vm.haptic(H.SUCCESS) },
                modifier = Modifier.height(56.dp), enabled = name.trim().length >= 3, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.community_secure_identity)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountStat(Sym.Favorite, Pink, d.favorites.size, stringResource(R.string.guide_favorites), Modifier.weight(1f))
            CountStat(Sym.Download, Safe, d.appLibrary.size, stringResource(R.string.community_downloaded), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountStat(Sym.Bolt, Caution, Xp.total(s, d.appLibrary.size).toInt(), stringResource(R.string.community_xp_earned), Modifier.weight(1f))
            CountStat(Sym.EmojiEvents, Purple, Xp.level(Xp.total(s, d.appLibrary.size)), stringResource(R.string.community_rank), Modifier.weight(1f), prefix = stringResource(R.string.community_lvl_prefix))
        }
        Column(Modifier.fillMaxWidth().springClick(RoundedCornerShape(28.dp)) { openBadges(); vm.haptic(H.LIGHT) }.background(cs.surfaceContainerHigh).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.community_badge_collection), style = ty.titleLargeEmphasized)
                    Text(stringResource(R.string.community_of_unlocked, unlocked, PROFILE_BADGES.size), style = ty.bodySmall, color = cs.onSurfaceVariant)
                }
                FilledTonalIconButton({ openBadges() }, shapes = IconButtonDefaults.shapes()) { Icon(Sym.ArrowForward, stringResource(R.string.community_open_badge_archive)) }
            }
            var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
            val p by animateFloatAsState(if (shown) unlocked / PROFILE_BADGES.size.toFloat() else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "badges")
            LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { i ->
                    val id = showcased.getOrNull(i)
                    val (ic, c) = id?.let { BADGES[it] } ?: (Sym.Lock to cs.onSurfaceVariant)
                    val (bg, fg) = tonal(c)
                    Box(Modifier.size(56.dp).background(if (id != null) bg else cs.surfaceContainerHighest, MaterialShapes.Cookie12Sided.toShape()), contentAlignment = Alignment.Center) {
                        Icon(ic, id, Modifier.size(26.dp), tint = if (id != null) fg else c.copy(.45f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeArchive(vm: MainViewModel, s: Settings, select: Boolean, index: Int?, onBack: () -> Unit, onPicked: () -> Unit) {
    val swap = select && index != null && index < s.showcasedBadges.size
    // Opens on the next badge worth chasing, so the grind is the first thing in view
    var focus by remember { mutableStateOf(PROFILE_BADGES.firstOrNull { !badgeUnlocked(it, s) && badgeProgress(it, s) != null } ?: PROFILE_BADGES.first()) }
    BackHandler { onBack() }
    val unlocked = PROFILE_BADGES.count { badgeUnlocked(it, s) }
    fun tap(id: String) {
        if (!select) { focus = id; vm.haptic(H.SELECTION); return }
        if (!badgeUnlocked(id, s) || id in s.showcasedBadges) { vm.haptic(H.ERROR); return }
        when { index != null -> vm.set { it.setShowcasedBadgeAt(index, id) }; s.showcasedBadges.size < 3 -> vm.set { it.toggleShowcasedBadge(id) }; else -> return }
        vm.haptic(H.SUCCESS); onPicked()
    }
    var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
    val total by animateFloatAsState(if (shown) unlocked / PROFILE_BADGES.size.toFloat() else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "all")
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (select && s.showcasedBadges.isNotEmpty()) TextButton({ vm.set { it.copy(showcasedBadges = emptyList()) }; vm.haptic(H.MEDIUM) }) { Text(stringResource(R.string.common_reset)) }
            Surface(Modifier.padding(end = 12.dp), shape = Pill, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                Text("$unlocked/${PROFILE_BADGES.size}", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = ty.labelLargeEmphasized)
            }
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(if (select) (if (swap) stringResource(R.string.community_swap_badge) else stringResource(R.string.community_pick_a_badge)) else stringResource(R.string.community_badge_archive), style = ty.displaySmallEmphasized)
            Text(if (select) (if (swap) stringResource(R.string.community_tap_any_unlocked_badge_to_replace) else stringResource(R.string.community_tap_any_unlocked_badge_and_it))
                else stringResource(R.string.community_every_mark_you_have_earned_and), Modifier.padding(top = 6.dp, bottom = 16.dp), style = ty.bodyLarge, color = cs.onSurfaceVariant)
            LinearWavyProgressIndicator({ total }, Modifier.fillMaxWidth())
        }
        if (!select) BadgeSpotlight(focus, s, Modifier.padding(horizontal = 16.dp).padding(top = 20.dp))
        var n = 0
        BADGE_GROUPS.forEach { (title, ids) ->
            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), Modifier.weight(1f), style = ty.titleMediumEmphasized)
                Text("${ids.count { badgeUnlocked(it, s) }}/${ids.size}", style = ty.labelLarge, color = cs.onSurfaceVariant)
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 4) {
                ids.forEach { id ->
                    Medal(id, s, focused = !select && focus == id, ring = select && id in s.showcasedBadges, Modifier.reveal(n++)) { tap(id) }
                }
            }
        }
    }
}

/** The focused badge, large: what it is, how to earn it and how far along the pilot is. */
@Composable
private fun BadgeSpotlight(id: String, s: Settings, modifier: Modifier = Modifier) {
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(id, modifier, transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "spot") { b ->
        val (icon, c) = BADGES[b] ?: return@AnimatedContent
        val on = badgeUnlocked(b, s); val (bg, fg) = tonal(c)
        val prog = badgeProgress(b, s)?.takeIf { !on }
        var k by remember { mutableIntStateOf(0) }; LaunchedEffect(Unit) { delay(80); k = 1 }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(if (on) bg else cs.surfaceContainerHigh).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            MorphBlob(listOf(MaterialShapes.Circle, if (on) MaterialShapes.SoftBurst else MaterialShapes.Cookie7Sided), k, 88.dp, if (on) fg.copy(.16f) else cs.surfaceContainerHighest) {
                Icon(icon, null, Modifier.size(40.dp), tint = if (on) fg else cs.onSurfaceVariant.copy(.6f))
            }
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                Text(if (on) stringResource(R.string.community_unlocked) else stringResource(R.string.community_locked), style = ty.labelLargeEmphasized, color = if (on) fg else cs.onSurfaceVariant)
                Text(b, style = ty.headlineSmallEmphasized, color = if (on) fg else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(badgeHint(b), Modifier.padding(top = 4.dp), style = ty.bodyMedium, color = if (on) fg.copy(.85f) else cs.onSurfaceVariant)
                prog?.let { (now, goal) ->
                    val p by animateFloatAsState((now.toFloat() / goal).coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "spotp")
                    LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth().padding(top = 14.dp))
                    Text("${now.coerceAtMost(goal)} / $goal", Modifier.padding(top = 6.dp), style = ty.labelLarge, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** Earned medals are cookies in their own color; locked ones stay round and grey, ringed by their progress. Focus morphs to a burst. */
@Composable
private fun Medal(id: String, s: Settings, focused: Boolean, ring: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (icon, c) = BADGES[id] ?: return
    val on = badgeUnlocked(id, s); val (bg, fg) = tonal(c)
    val prog = badgeProgress(id, s)?.takeIf { !on }?.let { (now, goal) -> (now.toFloat() / goal).coerceIn(0f, 1f) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "mscale")
    Column(modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(80.dp).graphicsLayer { scaleX = scale; scaleY = scale }.then(if (ring) Modifier.border(3.dp, cs.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center) {
            prog?.let { CircularWavyProgressIndicator({ it }, Modifier.fillMaxSize(), trackColor = cs.surfaceContainerHighest) }
            MorphBlob(listOf(if (on) MaterialShapes.Cookie12Sided else MaterialShapes.Circle, MaterialShapes.SoftBurst), if (focused) 1 else 0, if (prog != null) 62.dp else 72.dp,
                if (on) bg else cs.surfaceContainerHighest, Modifier.springClick(CircleShape, onClick = onClick)) {
                Icon(icon, id, Modifier.size(30.dp), tint = if (on) fg else cs.onSurfaceVariant.copy(.45f))
            }
            if (!on) Box(Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(cs.surfaceContainerLow).padding(3.dp).clip(CircleShape)
                .background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) { Icon(Sym.Lock, null, Modifier.size(12.dp), tint = cs.onSurfaceVariant) }
        }
        Text(id, Modifier.padding(top = 8.dp), style = if (focused) ty.labelMediumEmphasized else ty.labelMedium, color = if (on) cs.onSurface else cs.onSurfaceVariant,
            maxLines = 2, textAlign = TextAlign.Center, minLines = 2)
    }
}

// ---------------- Custom bundle creator ----------------

private val ICON_CHOICES = listOf("fa-layer-group" to "Layers", "fa-cubes" to "Cubes", "fa-rocket" to "Rocket", "fa-bolt" to "Bolt", "fa-star" to "Star", "fa-heart" to "Heart",
    "fa-gamepad" to "Gaming", "fa-music" to "Music", "fa-palette" to "Creative", "fa-shield-halved" to "Privacy", "fa-graduation-cap" to "Learn", "fa-briefcase" to "Work")
private val COLOR_CHOICES = listOf("linear-gradient(135deg, #6366f1, #ec4899)", "linear-gradient(135deg, #0ea5e9, #6366f1)", "linear-gradient(135deg, #10b981, #0ea5e9)",
    "linear-gradient(135deg, #f59e0b, #ef4444)", "linear-gradient(135deg, #8b5cf6, #ec4899)", "linear-gradient(135deg, #111827, #4f46e5)")

fun bundleIssueBody(title: String, description: String, icon: String, color: String, appIds: List<String>, apps: List<AppItem>, submitter: String?): String {
    val lines = appIds.joinToString("\n") { id ->
        apps.firstOrNull { it.id == id }?.let { a -> "- **${a.name}** (`${a.id}`)${a.packageName?.takeIf { it.isNotEmpty() }?.let { " · `$it`" } ?: ""}" } ?: "- $id"
    }
    return listOf("## Custom Bundle Submission", "", "**Title:** $title", "**Description:** $description", "**Icon:** `$icon`", "**Color:** `$color`",
        submitter?.takeIf { it.isNotEmpty() }?.let { "**Submitted by:** $it" } ?: "", "", "### Apps (${appIds.size})", lines, "", "---",
        "_Submitted via Orion Store Custom Bundle Creator. Add the `approve` label to publish._").filter { it.isNotEmpty() }.joinToString("\n")
}

@Composable
fun CustomBundleSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val close = LocalSheetClose.current; val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    val platform = vm.tab.targetPlatform(); val all = remember { vm.allApps() }
    val pool = remember(all) { all.filter { it.platform.trim().equals(platform, true) } }
    var step by rememberSaveable { mutableIntStateOf(1) }
    var title by rememberSaveable { mutableStateOf("") }; var desc by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(ICON_CHOICES[0].first) }; var color by rememberSaveable { mutableIntStateOf(0) }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    var saved by remember { mutableStateOf<String?>(null) }; var err by remember { mutableStateOf("") }
    val can = when (step) { 1 -> title.trim().length >= 3 && desc.trim().length >= 8; 2 -> picked.size in 2..24; else -> true }
    fun save(submitted: Boolean): String {
        val now = System.currentTimeMillis(); val id = "cb_${now.toString(36)}"
        vm.set { it.copy(customBundles = it.customBundles + CustomBundle(id, title.trim(), desc.trim(), icon, picked, now, if (submitted) now else null)) }
        return id
    }
    val stops = remember(color) { Regex("#[0-9a-fA-F]{6}").findAll(COLOR_CHOICES[color]).map { hex(it.value) }.toList().let { if (it.size == 1) it + it else it } }
    val pickedApps = remember(picked, pool) { val m = pool.associateBy { it.id }; picked.mapNotNull(m::get) }
    BundleSteps(step)
    AnimatedContent(step, Modifier.weight(1f, fill = false), label = "cb") { st ->
        when (st) {
            1 -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BundleCard(title, desc, icon, stops, pickedApps)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(title, { title = it.take(48) }, stringResource(R.string.community_title), stringResource(R.string.community_e_g_privacy_essentials), supporting = if (title.isNotEmpty() && title.trim().length < 3) stringResource(R.string.community_at_least_3_characters) else null)
                    Field(desc, { desc = it.take(180) }, stringResource(R.string.community_description), stringResource(R.string.community_a_short_pitch_for_this_bundle), lines = 3, supporting = "${desc.length}/180")
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.community_icon), style = ty.titleMediumEmphasized)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ICON_CHOICES.forEach { (id, label) ->
                            MorphPick(icon == id, { icon = id; vm.haptic(H.SELECTION) }, to = MaterialShapes.Cookie6Sided) {
                                Icon(faIcon(id), label, tint = if (icon == id) cs.onPrimaryContainer else cs.onSurfaceVariant)
                            }
                        }
                    }
                    Text(stringResource(R.string.community_color), Modifier.padding(top = 4.dp), style = ty.titleMediumEmphasized)
                    Row(Modifier.fillMaxWidth().bleed(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        COLOR_CHOICES.forEachIndexed { i, g ->
                            val sw = Regex("#[0-9a-fA-F]{6}").findAll(g).map { hex(it.value) }.toList().let { if (it.size == 1) it + it else it }
                            val on = color == i
                            val k by animateFloatAsState(if (on) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "sw")
                            val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
                            Box(Modifier.size(52.dp).springClick(MorphShape(morph, k)) { color = i }.graphicsLayer { rotationZ = k * 20f }.background(Brush.linearGradient(sw)),
                                contentAlignment = Alignment.Center) {
                                if (on) Icon(Sym.Check, stringResource(R.string.community_selected_color), Modifier.graphicsLayer { rotationZ = -k * 20f }, tint = Color.White)
                            }
                        }
                    }
                }
            }
            2 -> Column(Modifier.fillMaxWidth()) {
                // Picked tray: what the bundle holds so far, tap one to drop it.
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (picked.size < 2) stringResource(R.string.community_pick_at_least_2_apps) else stringResource(R.string.community_apps_in_your_bundle, picked.size), Modifier.weight(1f), style = ty.titleSmallEmphasized)
                    Text("${picked.size}/24", style = ty.labelLargeEmphasized, color = if (picked.size in 2..24) cs.primary else cs.onSurfaceVariant)
                }
                androidx.compose.animation.AnimatedVisibility(pickedApps.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pickedApps, key = { it.id }) { a ->
                            Box(Modifier.animateItem().springClick(RoundedCornerShape(16.dp)) { picked = picked - a.id }) {
                                AppIcon(a, 52.dp)
                                Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(cs.surface).padding(2.dp).clip(CircleShape).background(cs.errorContainer),
                                    contentAlignment = Alignment.Center) { Icon(Sym.Close, stringResource(R.string.community_remove, a.name), Modifier.size(12.dp), tint = cs.onErrorContainer) }
                            }
                        }
                    }
                }
                AppPickerList(pool, platform, selected = picked.toSet(), status = { v, f -> ctx.getString(R.string.community_showing_of_max_24, v, f) }) { a ->
                    picked = if (a.id in picked) picked - a.id else if (picked.size < 24) picked + a.id else picked; vm.haptic(H.SELECTION)
                }
            }
            else -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BundleCard(title, desc, icon, stops, pickedApps)
                Lead(stringResource(R.string.community_submitted), stringResource(R.string.community_your_bundle_was_saved_locally_and), center = true) {
                    Blob(Sym.TaskAlt, Safe, 72, MaterialShapes.Cookie9Sided)
                }
                Text(stringResource(R.string.community_your_saved_bundles_show_up_in), style = ty.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ErrorNote(err)
        saved?.let { Tag(it, Safe, Sym.CheckCircle) }
        when (step) {
            1 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(close, modifier = Modifier.weight(1f).height(56.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_cancel)) }
                Button({ step = 2 }, modifier = Modifier.weight(1f).height(56.dp), enabled = can, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.common_continue)); Spacer(Modifier.width(6.dp)); Icon(Sym.ArrowForward, null, Modifier.size(18.dp))
                }
            }
            2 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BackHandler { step = 1 }
                OutlinedButton({
                    err = ""; runCatching { save(false) }.onSuccess { saved = ctx.getString(R.string.community_saved_to_your_bundles); vm.haptic(H.SUCCESS); scope.launch { delay(650); close() } }
                        .onFailure { err = it.message ?: ctx.getString(R.string.community_could_not_save_bundle) }
                }, modifier = Modifier.weight(1f).height(56.dp), enabled = can, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.community_keep_local)) }
                Button({
                    err = ""
                    runCatching {
                        save(true)
                        val body = bundleIssueBody(title.trim(), desc.trim(), icon, COLOR_CHOICES[color], picked, all, s.userProfile?.name)
                        vm.tracker.open("https://github.com/RookieEnough/Orion-Data/issues/new?title=${enc("[Bundle] ${title.trim()}")}&body=${enc(body)}&labels=bundle-submission")
                    }.onSuccess { step = 3; vm.haptic(H.SUCCESS) }.onFailure { err = it.message ?: ctx.getString(R.string.community_could_not_submit_bundle_try_again) }
                }, modifier = Modifier.weight(1f).height(56.dp), enabled = can, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_submit)) }
            }
            else -> CtaButton(stringResource(R.string.xtra_done), close, icon = Sym.Check)
        }
    }
}

/** Three labelled step cookies joined by a track that fills as you go. */
@Composable
private fun BundleSteps(step: Int) = Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    listOf(stringResource(R.string.community_details), stringResource(R.string.community_apps), stringResource(R.string.xtra_done)).forEachIndexed { i, label ->
        val n = i + 1; val on = n <= step
        val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "step")
        Box(Modifier.size(30.dp).background(bg, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) {
            if (n < step) Icon(Sym.Check, stringResource(R.string.xtra_done), Modifier.size(16.dp), tint = cs.onPrimary)
            else Text("$n", style = ty.labelLargeEmphasized, color = if (on) cs.onPrimary else cs.onSurfaceVariant)
        }
        Text(label, Modifier.padding(start = 6.dp), style = if (n == step) ty.labelLargeEmphasized else ty.labelLarge, color = if (on) cs.onSurface else cs.onSurfaceVariant)
        if (i < 2) {
            val track by animateColorAsState(if (n < step) cs.primary else cs.surfaceContainerHighest, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "track")
            Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(4.dp).clip(Pill).background(track))
        }
    }
}

private val TILT = listOf(-8f, 6f, -3f, 9f)

/** Live preview of the bundle as the store will show it: its colour, icon, words and the first apps fanned out. */
@Composable
private fun BundleCard(title: String, desc: String, icon: String, stops: List<Color>, apps: List<AppItem>) =
    Box(Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(stops))) {
        Box(Modifier.align(Alignment.TopEnd).offset(48.dp, (-40).dp).size(170.dp).background(Color.White.copy(.12f), MaterialShapes.Cookie9Sided.toShape()))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(if (apps.isEmpty()) 1f else .58f).padding(20.dp)) {
            Box(Modifier.size(44.dp).background(Color.White.copy(.22f), MaterialShapes.Cookie6Sided.toShape()), contentAlignment = Alignment.Center) {
                Icon(faIcon(icon), null, tint = Color.White)
            }
            Text(title.trim().ifEmpty { stringResource(R.string.community_your_bundle) }, Modifier.padding(top = 12.dp), style = ty.titleLargeEmphasized, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(desc.trim().ifEmpty { stringResource(R.string.community_what_ties_these_apps_together) }, style = ty.bodySmall, color = Color.White.copy(.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) {
            apps.take(4).forEachIndexed { i, a ->
                AppIcon(a, 52.dp, Modifier.offset(x = (-(i % 2) * 44).dp, y = ((i / 2) * 44 - 22).dp).graphicsLayer { rotationZ = TILT[i]; shadowElevation = 8.dp.toPx(); shape = RoundedCornerShape(14.dp) })
            }
        }
    }

// ---------------- App submission ----------------

private val KEYWORD_HELP = R.string.community_1_tap_check_releases_page_below
internal val pretty = Json { prettyPrint = true; @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class) prettyPrintIndent = "  " }

private data class SubDraft(
    val mode: String, val json: String = "", val shots: List<String> = emptyList(), val oIcon: String = "", val oKeyword: String = "", val oDesc: String = "",
    val manualKeyword: Boolean = false, val name: String = "", val description: String = "", val icon: String = "", val repoUrl: String = "", val keyword: String = "apk",
    val packageName: String = "", val category: String = "Utility", val manualCategory: Boolean = false, val topics: List<String> = emptyList(), val author: String = "", val downloadUrl: String = "",
    val autoName: String = "", val autoDesc: String = "", val autoPkg: String = "", val autoStatus: String = "idle", val autoMsg: String = "",
    val autoIcon: String = "", val autoShots: List<String> = emptyList(),
)

// Note: draft lives for the process like the web's sessionStorage; persist to disk if users lose long drafts to process death.
private var draft: SubDraft? = null


private fun autofillMessage(ctx: Context, d: Detected): String {
    val base = ctx.getString(when {
        d.name != null && d.packageName != null && d.description != null -> R.string.community_app_name_package_name_and_description
        d.name != null && d.packageName != null -> R.string.community_app_name_and_package_name_detected
        d.packageName != null -> R.string.community_package_name_detected_add_a_custom
        d.name != null -> R.string.community_app_name_detected_orion_still_needs
        d.description != null -> R.string.community_description_detected_package_name_still_needs
        else -> R.string.community_repo_found_but_orion_could_not
    })
    val found = listOfNotNull(ctx.getString(R.string.community_the_icon).takeIf { d.icon != null },
        d.shots.size.takeIf { it > 0 }?.let { if (it == 1) ctx.getString(R.string.community_1_screenshot) else ctx.getString(R.string.community_screenshots_count, it) })
    val joined = when (found.size) {
        2 -> ctx.getString(R.string.community_and_join, found[0], found[1])
        1 -> found[0]
        else -> ""
    }
    return if (joined.isEmpty()) base else base + ctx.getString(R.string.community_found_too_check_they_fit, joined)
}

@Composable
fun SubmissionSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val close = LocalSheetClose.current; val scope = rememberCoroutineScope(); val clip = LocalClipboardManager.current; val t = rememberSheetToast(); val ctx = LocalContext.current
    val platform = vm.tab.targetPlatform(); val android = platform == Platform.ANDROID
    var f by remember { mutableStateOf(draft ?: SubDraft("manual")) }
    SideEffect { draft = f }
    var err by remember { mutableStateOf("") }; var shot by remember { mutableStateOf("") }; var issue by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf(false) }; var badge by remember { mutableStateOf(false) }
    val obtainium = android && f.mode == "obtainium"
    val id = slugId(f.name)
    val cooldown = 180 - min(s.submissionCount * 15, 150)
    val lvl = Xp.level(Xp.total(s, LocalD.current.appLibrary.size)); val rank = stringResource(R.string.community_lvl, lvl, Xp.title(lvl)); val rankIcon = Sym.MilitaryTech
    val source = remember(f.repoUrl, f.mode) { if (android && f.mode == "manual" && f.repoUrl.isNotBlank()) parseRepoSource(f.repoUrl) else null }
    var lastKey by remember { mutableStateOf("") }
    LaunchedEffect(source) {
        val src = source ?: run { lastKey = ""; f = f.copy(autoStatus = "idle", autoMsg = ""); return@LaunchedEffect }
        val key = "${src.provider}:${src.domain.orEmpty()}:${src.repoPath}"; if (key == lastKey) return@LaunchedEffect
        f = f.copy(autoStatus = "loading", autoMsg = ctx.getString(R.string.community_detecting_description_package_name_icon_and_screenshots))
        delay(650)
        runCatching { detectRepo(src, s.orbitTokens()) }.onSuccess { d ->
            lastKey = key
            val p = f
            f = p.copy(
                author = if (d.author != null && p.author.isEmpty()) d.author else p.author,
                name = if (d.name != null && (p.name.isEmpty() || p.name == p.autoName)) d.name else p.name,
                description = if (d.description != null && (p.description.isEmpty() || p.description == p.autoDesc)) d.description else p.description,
                packageName = if (d.packageName != null && (p.packageName.isEmpty() || p.packageName == p.autoPkg)) d.packageName else p.packageName,
                // Fetched art only fills what the user hasn't set, and follows the repo while the field still holds the last fetch
                icon = if (d.icon != null && (p.icon.isEmpty() || p.icon == p.autoIcon)) d.icon else p.icon,
                shots = if (d.shots.isNotEmpty() && (p.shots.isEmpty() || p.shots == p.autoShots)) d.shots else p.shots,
                autoIcon = d.icon ?: p.autoIcon, autoShots = d.shots.ifEmpty { p.autoShots },
                topics = d.topics, autoName = d.name ?: p.autoName, autoDesc = d.description ?: p.autoDesc, autoPkg = d.packageName ?: p.autoPkg,
                autoStatus = if (d.packageName != null || d.description != null || d.icon != null || d.shots.isNotEmpty()) "success" else "error", autoMsg = autofillMessage(ctx, d))
        }.onFailure { f = f.copy(autoStatus = "error", autoMsg = ctx.getString(R.string.community_could_not_auto_detect_repo_metadata)) }
    }
    // Category follows the name, description and repo topics, learned from how the catalog files its apps, until the user picks one
    val apps = vm.allApps()
    val model by produceState<CategoryModel?>(null, apps) { value = withContext(Dispatchers.Default) { CategoryModel(apps) } }
    LaunchedEffect(model, f.name, f.description, f.topics, f.manualCategory) {
        val m = model ?: return@LaunchedEffect; if (f.manualCategory) return@LaunchedEffect
        delay(350)
        withContext(Dispatchers.Default) { m.guess("${f.name} ${f.description}", f.topics) }?.let { if (it != f.category) f = f.copy(category = it) }
    }
    val releases = f.repoUrl.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.let { u ->
        when { "github.com" in u -> "$u/releases"; "gitlab" in u -> "$u/-/releases"; "codeberg.org" in u -> "$u/releases"; else -> null }
    }
    fun fail(m: String) { err = m; vm.haptic(H.ERROR) }
    fun addShot() {
        val u = shot.trim(); if (u.isEmpty()) return
        if (!u.startsWith("http")) { fail(ctx.getString(R.string.community_screenshot_must_be_a_valid_url)); scope.launch { delay(3000); err = "" }; return }
        f = f.copy(shots = f.shots + sanitizeGitHubImageUrl(u)); shot = ""
    }
    fun switchMode(m: String) {
        if (m == f.mode) return
        err = ""; issue = null; draft = null
        f = f.copy(mode = m, shots = emptyList(), oDesc = "", oIcon = "", oKeyword = "", json = "", autoName = "")
    }
    fun submit() {
        err = ""
        if (f.shots.size < 3) return fail(ctx.getString(R.string.community_at_least_3_screenshots_are_required))
        f.shots.forEachIndexed { i, u -> explainRejectedImageLink(u)?.let { return fail(ctx.getString(R.string.community_screenshot, i + 1, it)) } }
        val shots = JsonArray(f.shots.map(::JsonPrimitive))
        val app: JsonObject = if (obtainium) {
            if (f.json.isBlank()) return fail(ctx.getString(R.string.community_please_paste_json_content))
            if (f.oIcon.isBlank()) return fail(ctx.getString(R.string.community_icon_url_is_required))
            explainRejectedImageLink(f.oIcon)?.let { return fail(ctx.getString(R.string.community_icon_2, it)) }
            val parsed = runCatching { json.parseToJsonElement(f.json).jsonObject }.getOrElse { return fail(ctx.getString(R.string.community_failed_to_parse_data_please_check)) }
            JsonObject(parsed + mapOf("icon" to JsonPrimitive(sanitizeGitHubImageUrl(f.oIcon)), "releaseKeyword" to JsonPrimitive(f.oKeyword.trim().ifEmpty { "apk" }),
                "description" to JsonPrimitive(f.oDesc.trim()), "screenshots" to shots, "platform" to JsonPrimitive(Platform.ANDROID)))
        } else {
            if (f.name.isBlank()) return fail(ctx.getString(R.string.community_app_name_is_required))
            if (f.description.isBlank()) return fail(ctx.getString(R.string.community_description_is_required))
            if (f.icon.isBlank()) return fail(ctx.getString(R.string.community_icon_url_is_required))
            explainRejectedImageLink(f.icon)?.let { return fail(ctx.getString(R.string.community_icon_2, it)) }
            if (android) {
                if (f.repoUrl.isBlank()) return fail(ctx.getString(R.string.community_repo_url_is_required))
                if (f.packageName.isBlank()) return fail(ctx.getString(R.string.community_package_name_is_required))
            } else if (f.downloadUrl.isBlank()) return fail(ctx.getString(R.string.community_direct_download_link_is_required))
            val url = f.repoUrl; val parts = runCatching { URI(url.trim()).path.split('/').filter { it.isNotEmpty() } }.getOrDefault(emptyList())
            val host = runCatching { URI(url.trim()).host.orEmpty() }.getOrDefault("")
            val gh = if (android && "github.com" in url) url.substringAfter("github.com/").split('/').takeIf { it.size >= 2 }?.let { "${it[0]}/${it[1].replace(".git", "").trimEnd('/')}" } else null
            val gl = android && gh == null && "gitlab" in url && parts.size >= 2; val cb = android && gh == null && !gl && "codeberg.org" in url && parts.size >= 2
            val owner = gh?.substringBefore('/') ?: parts.firstOrNull().takeIf { gl || cb }
            buildJsonObject {
                put("name", f.name); put("id", id); put("description", f.description); put("icon", sanitizeGitHubImageUrl(f.icon)); put("repoUrl", f.repoUrl)
                put("githubRepo", gh.orEmpty()); put("gitlabRepo", if (gl) parts.joinToString("/") else ""); put("gitlabDomain", if (gl) host else "")
                put("codebergRepo", if (cb) parts.joinToString("/") else "")
                put("releaseKeyword", if (android) (if (f.manualKeyword) f.keyword.trim().ifEmpty { "apk" } else "apk") else f.keyword)
                put("packageName", f.packageName); put("category", f.category); put("author", f.author.ifEmpty { owner.orEmpty() }); put("downloadUrl", f.downloadUrl)
                put("screenshots", shots); put("platform", platform)
            }
        }
        issue = submissionIssueUrl(pretty.encodeToString(JsonArray.serializer(), JsonArray(listOf(app))), 1, app["name"]?.jsonPrimitive?.contentOrNull, STORE_VERSION)
        vm.set { it.registerSubmission(System.currentTimeMillis()) }; vm.haptic(H.SUCCESS)
    }
    issue?.let { url ->
        AlertDialog({ issue = null }, icon = { Icon(Sym.Warning, null) }, title = { Text(stringResource(R.string.community_before_you_continue)) },
            text = { Text(stringResource(R.string.community_orion_already_generated_the_correct_json)) },
            confirmButton = { TextButton({ vm.tracker.open(url); draft = null; issue = null; close() }) { Text(stringResource(R.string.community_open_github_issue)) } },
            dismissButton = { TextButton({ issue = null }) { Text(stringResource(R.string.community_go_back)) } })
    }
    SheetToastHost(t)
    val sectionsDone = if (obtainium) listOf(f.json.isNotBlank(), f.oIcon.isNotBlank(), f.shots.size >= 3)
        else listOf(if (android) f.repoUrl.isNotBlank() else f.downloadUrl.isNotBlank(), f.name.isNotBlank() && f.author.isNotBlank(), f.description.isNotBlank() && f.icon.isNotBlank()) +
            (if (android) listOf(f.packageName.isNotBlank()) else emptyList()) + (f.shots.size >= 3)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SubmitHero(platform, rank, rankIcon, cooldown, sectionsDone.count { it }, sectionsDone.size)
        if (android) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeCard(stringResource(R.string.community_from_repo), stringResource(R.string.community_github_gitlab_or_codeberg), Sym.Code, MaterialShapes.Clover4Leaf, f.mode == "manual", Modifier.weight(1f)) { switchMode("manual") }
            ModeCard("Obtainium", stringResource(R.string.community_paste_an_export), Sym.FileDownload, MaterialShapes.Cookie9Sided, f.mode == "obtainium", Modifier.weight(1f)) { switchMode("obtainium") }
        }
        ErrorNote(err)
        val screenshots: @Composable (Int) -> Unit = { n ->
            Section(n, stringResource(R.string.guide_screenshots), stringResource(R.string.community_at_least_3_direct_image_links), f.shots.size >= 3) {
                Field(shot, { shot = it }, stringResource(R.string.community_screenshot_url), "https://image.url/screenshot.jpg", uri = true, onDone = ::addShot,
                    trailing = { IconButton(::addShot, enabled = shot.isNotBlank()) { Icon(Sym.AddPhotoAlternate, stringResource(R.string.community_add_screenshot)) } })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val p by animateFloatAsState((f.shots.size / 3f).coerceAtMost(1f), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "shots")
                    LinearWavyProgressIndicator({ p }, Modifier.weight(1f), color = if (f.shots.size >= 3) Safe else cs.primary)
                    Text("${f.shots.size}/3", Modifier.padding(start = 12.dp), style = ty.labelLargeEmphasized, color = if (f.shots.size >= 3) Safe else cs.onSurfaceVariant)
                }
                var viewing by remember { mutableStateOf<Int?>(null) }
                viewing?.let { Lightbox(f.shots, it) { viewing = null } }
                Row(Modifier.fillMaxWidth().bleed(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    f.shots.forEachIndexed { i, u ->
                        Box(Modifier.size(84.dp, 150.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHighest).clickable(onClickLabel = stringResource(R.string.community_view_screenshot, i + 1)) { viewing = i }) {
                            AsyncImage(u, stringResource(R.string.community_screenshot_2, i + 1), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Text("#${i + 1}", Modifier.align(Alignment.BottomStart).padding(6.dp).clip(Pill).background(cs.scrim.copy(.6f)).padding(horizontal = 8.dp, vertical = 2.dp), style = ty.labelSmall, color = Color.White)
                            FilledTonalIconButton({ f = f.copy(shots = f.shots.filterIndexed { j, _ -> j != i }) }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp)) {
                                Icon(Sym.Close, stringResource(R.string.community_remove_screenshot, i + 1), Modifier.size(16.dp))
                            }
                        }
                    }
                    repeat(maxOf(3 - f.shots.size, 1)) { k ->
                        val line = cs.outlineVariant
                        Box(Modifier.size(84.dp, 150.dp).drawBehind {
                            drawRoundRect(line, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                        }, contentAlignment = Alignment.Center) {
                            if (k == 0) Icon(Sym.AddPhotoAlternate, null, tint = cs.onSurfaceVariant) else Text("${f.shots.size + k + 1}", style = ty.titleLarge, color = cs.onSurfaceVariant.copy(.5f))
                        }
                    }
                }
            }
        }
        if (obtainium) {
            Section(1, stringResource(R.string.community_obtainium_export), stringResource(R.string.community_paste_the_json_obtainium_exports_for), f.json.isNotBlank()) {
                Field(f.json, { f = f.copy(json = it) }, stringResource(R.string.community_obtainium_json), "{\"apps\": [{\"url\": \"https://gitlab.com/...\"}]}", lines = 6, mono = true)
            }
            Section(2, stringResource(R.string.community_how_it_appears), stringResource(R.string.community_the_icon_is_required_the_rest), f.oIcon.isNotBlank()) {
                Field(f.oIcon, { f = f.copy(oIcon = it) }, stringResource(R.string.community_icon_url), "https://...", uri = true, leading = iconPreview(f.oIcon))
                Field(f.oKeyword, { f = f.copy(oKeyword = it) }, stringResource(R.string.community_release_keyword), stringResource(R.string.community_e_g_app_release))
                Field(f.oDesc, { f = f.copy(oDesc = it) }, stringResource(R.string.community_description), stringResource(R.string.community_what_does_this_app_do), lines = 3)
            }
            screenshots(3)
        } else {
            Section(1, stringResource(R.string.community_source), if (android) stringResource(R.string.community_where_the_releases_live_orion_reads) else stringResource(R.string.community_where_people_download_it), sectionsDone[0]) {
                if (android) {
                    Field(f.repoUrl, { f = f.copy(repoUrl = it) }, stringResource(R.string.community_repo_url), "https://github.com/owner/repo", uri = true)
                    if (f.repoUrl.isNotBlank()) {
                        val (c, ic) = when (f.autoStatus) { "error" -> cs.error to Sym.Warning; "success" -> Safe to Sym.CheckCircle; else -> cs.onSurfaceVariant to Sym.AutoFixHigh }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.copy(.1f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (f.autoStatus == "loading") LoadingIndicator(Modifier.size(20.dp)) else Icon(ic, null, Modifier.size(18.dp), tint = c)
                            Spacer(Modifier.width(10.dp))
                            Text(f.autoMsg.ifEmpty { stringResource(R.string.community_orion_will_try_to_detect_the) }, style = ty.bodySmall, color = c)
                        }
                    }
                } else Field(f.downloadUrl, { f = f.copy(downloadUrl = it) }, stringResource(R.string.community_direct_download_link), "https://example.com/download", uri = true)
            }
            Section(2, stringResource(R.string.community_details), null, sectionsDone[1]) {
                Field(f.name, { f = f.copy(name = it) }, stringResource(R.string.community_name), stringResource(R.string.logic_app_name))
                var open by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(open, { open = it }) {
                    TextField(f.category, {}, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), readOnly = true, label = { Text(stringResource(R.string.community_category)) },
                        supportingText = { Text(when { f.manualCategory -> stringResource(R.string.community_picked_by_you); f.name.isBlank() && f.description.isBlank() -> stringResource(R.string.community_fills_in_from_the_details); else -> stringResource(R.string.community_detected_from_the_details_tap_to_change) }) },
                        shape = RoundedCornerShape(16.dp), colors = fieldColors(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) })
                    ExposedDropdownMenu(open, { open = false }, shape = RoundedCornerShape(20.dp)) {
                        SUBMIT_CATEGORIES.forEach { c -> DropdownMenuItem({ Text(c) }, { f = f.copy(category = c, manualCategory = true); open = false; vm.haptic(H.SELECTION) }) }
                    }
                }
                Field(f.author, { f = f.copy(author = it) }, stringResource(R.string.community_author), stringResource(R.string.community_developer_or_publisher))
                Field(id, {}, stringResource(R.string.community_app_id), stringResource(R.string.community_auto_generated), readOnly = true, mono = true)
            }
            Section(3, stringResource(R.string.community_store_listing), stringResource(R.string.community_what_people_see_first), sectionsDone[2]) {
                Field(f.description, { f = f.copy(description = it) }, stringResource(R.string.community_description_2), stringResource(R.string.community_a_short_summary_users_will_read), lines = 3)
                Field(f.icon, { f = f.copy(icon = it) }, stringResource(R.string.community_icon_url), "https://...", uri = true, leading = iconPreview(f.icon))
            }
            if (android) Section(4, stringResource(R.string.community_release), stringResource(R.string.community_how_orion_picks_the_right_apk), sectionsDone[3]) {
                Field(f.packageName, { f = f.copy(packageName = it) }, stringResource(R.string.community_package_name), "com.example.app", mono = true)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHighest).padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.community_release_keyword), style = ty.titleSmall)
                        Text(if (f.manualKeyword) stringResource(R.string.community_manual_matches_your_keyword) else stringResource(R.string.community_auto_first_apk_in_the_release), style = ty.bodySmall, color = cs.onSurfaceVariant)
                    }
                    IconButton({ help = !help }) { Icon(Sym.Info, stringResource(R.string.community_help_release_keyword)) }
                    Switch(f.manualKeyword, { f = f.copy(manualKeyword = it); vm.haptic(H.SELECTION) })
                }
                AnimatedVisibility(help) {
                    Surface(shape = RoundedCornerShape(16.dp), color = cs.secondaryContainer) { Text(stringResource(KEYWORD_HELP), Modifier.padding(14.dp), style = ty.bodySmall) }
                }
                AnimatedVisibility(f.manualKeyword) { Field(f.keyword, { f = f.copy(keyword = it) }, stringResource(R.string.community_keyword), "apk") }
                releases?.let { r ->
                    FilledTonalButton({ vm.tracker.open(r) }, shapes = ButtonDefaults.shapes()) { Icon(Sym.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.community_check_releases_page)) }
                }
            }
            screenshots(if (android) 5 else 4)
        }
        Column(Modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CtaButton(stringResource(R.string.community_generate_github_issue), ::submit, icon = Sym.OpenInNew)
            Text(stringResource(R.string.community_opens_github_issues_in_your_browser), style = ty.bodySmall, color = cs.onSurfaceVariant)
        }
        val md = Share.markdown(Share.webLink(id))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).clickable { badge = !badge; vm.haptic(H.SELECTION) }
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(cs.tertiaryContainer, MaterialShapes.Sunny.toShape()), contentAlignment = Alignment.Center) {
                    Icon(Sym.Verified, null, Modifier.size(18.dp), tint = cs.onTertiaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.community_badge_for_your_readme), style = ty.titleMediumEmphasized)
                    Text(stringResource(R.string.community_link_people_straight_to_this_app), style = ty.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(if (badge) Sym.ExpandLess else Sym.ExpandMore, null)
            }
            if (badge) {
                Eyebrow("Markdown", cs.onSurfaceVariant)
                Text(md, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHighest).padding(12.dp), style = ty.bodySmall, fontFamily = FontFamily.Monospace)
                FilledTonalButton({ clip.setText(AnnotatedString(md)); t.show(ctx.getString(R.string.community_markdown_copied)); vm.haptic(H.SUCCESS) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Sym.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.community_copy_markdown))
                }
                Eyebrow(stringResource(R.string.community_deep_link), cs.onSurfaceVariant)
                Text("orionstore://app/${enc(id)}", style = ty.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Slim submission header: title, contributor rank, cooldown and live step progress. No hero box, the form is the point. */
@Composable
private fun SubmitHero(platform: String, rank: String, rankIcon: ImageVector, cooldown: Int, done: Int, total: Int) = Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
    Text(stringResource(when (platform) { Platform.ANDROID -> R.string.community_submit_an_app; Platform.TV -> R.string.community_submit_a_tv_app; else -> R.string.community_submit_software }), style = ty.headlineSmallEmphasized)
    Text(stringResource(R.string.community_reviewed_on_github_then_it_lands), style = ty.bodyMedium, color = cs.onSurfaceVariant)
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Tag(rank, cs.tertiary, rankIcon); Tag(stringResource(R.string.community_h_m_cooldown, cooldown / 60, cooldown % 60), cs.onSurfaceVariant, Sym.Timer)
        Spacer(Modifier.weight(1f))
        Text("$done/$total", style = ty.labelLargeEmphasized, color = cs.primary)
    }
    val p by animateFloatAsState(done / total.toFloat().coerceAtLeast(1f), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "steps")
    LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth().padding(top = 10.dp))
}

/** Submission mode as a pair of selectable tiles: the selected one fills and its shape turns primary. */
@Composable
private fun ModeCard(title: String, desc: String, icon: ImageVector, shape: RoundedPolygon, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val bg by animateColorAsState(if (on) cs.secondaryContainer else cs.surfaceContainer, fx, label = "bg")
    Column(modifier.springClick(RoundedCornerShape(24.dp), onClick = onClick).background(bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(if (on) cs.primary else cs.surfaceContainerHighest, shape.toShape()), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(on, enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(), exit = scaleOut() + fadeOut()) { Icon(Sym.CheckCircle, stringResource(R.string.community_selected), tint = cs.primary) }
        }
        Text(title, Modifier.padding(top = 12.dp), style = ty.titleSmallEmphasized)
        Text(desc, style = ty.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Live preview of a pasted icon link, shown inside the field. */
private fun iconPreview(url: String): (@Composable () -> Unit)? = url.trim().takeIf { it.startsWith("http") }?.let { u ->
    { AsyncImage(sanitizeGitHubImageUrl(u), stringResource(R.string.community_icon_preview), Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
}
