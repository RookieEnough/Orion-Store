package com.orion.store.ui

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.orion.store.R
import com.orion.store.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

const val ORION_SITE = "https://orionstore.pages.dev"
const val WORKER_URL = "https://orion-relay.sarthaksinha5088.workers.dev/"

/** Showcase badge visuals, shared with the profile sheet. */
val BADGES: Map<String, Pair<ImageVector, Color>> = mapOf(
    "Legend" to (Sym.WorkspacePremium to Color(0xFFEAB308)), "Guardian" to (Sym.Shield to Color(0xFFEF4444)),
    "Void Walker" to (Sym.DarkMode to Color(0xFFA855F7)), "Supernova" to (Sym.AutoAwesome to Color(0xFFEC4899)),
    "Backer" to (Sym.Diamond to Color(0xFF06B6D4)), "Dev Mode" to (Sym.Terminal to Color(0xFFD946EF)),
    "Dino Rookie" to (Sym.Egg to Color(0xFF10B981)), "Dino Master" to (Sym.Bolt to Color(0xFF3B82F6)),
    "Dino Legend" to (Sym.LocalFireDepartment to Color(0xFFF97316)),
    "Merge Tamer" to (Sym.CallMerge to Color(0xFF14B8A6)), "Spaghetti Slayer" to (Sym.RamenDining to Color(0xFFF59E0B)),
    "Sudo Sovereign" to (Sym.Crown to Color(0xFFE11D48)),
    "Icon Collector" to (Sym.Palette to Color(0xFF8B5CF6)), "Dock Master" to (Sym.ViewAgenda to Color(0xFF22C55E)),
    "Bronze Wings" to (Sym.MilitaryTech to Color(0xFFC07A3E)), "Silver Wings" to (Sym.MilitaryTech to Color(0xFF8F979F)), "Gold Wings" to (Sym.MilitaryTech to Color(0xFFE0A526)),
)

@Composable
fun BadgeChip(id: String, onClick: () -> Unit) {
    val (icon, c) = BADGES[id] ?: return
    val (bg, fg) = tonal(c)
    AssistChip(onClick, { Text(id) }, leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize), tint = fg) }, shape = Pill, border = null,
        colors = AssistChipDefaults.assistChipColors(containerColor = bg, labelColor = fg))
}

// region about hero
/** Developer coin: flips to the Hall of Fame; a burst halo turns behind it and showcased badges orbit around. */
@Composable
private fun DevCoin(vm: MainViewModel, profile: DevProfile, slots: List<String>, flipped: Boolean, onTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme; val still = LocalS.current.calm
    val rot by animateFloatAsState(if (flipped) 180f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "flip")
    val spin by rememberInfiniteTransition(label = "halo").animateFloat(0f, if (still) 0f else 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "s")
    Box(Modifier.size(260.dp, 200.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(176.dp).graphicsLayer { rotationZ = spin }.background(cs.primary.copy(.28f), MaterialShapes.SoftBurst.toShape()))
        slots.forEachIndexed { i, id ->
            val (ic, c) = BADGES[id] ?: return@forEachIndexed
            val (bg, fg) = tonal(c)
            Box(Modifier.size(38.dp).graphicsLayer {
                val a = Math.toRadians((spin * 2f + i * 120f).toDouble())
                translationX = (kotlin.math.cos(a) * 112.dp.toPx()).toFloat(); translationY = (kotlin.math.sin(a) * 70.dp.toPx()).toFloat()
                val z = kotlin.math.sin(a).toFloat(); scaleX = .85f + .2f * z; scaleY = scaleX; alpha = .7f + .3f * z
            }.clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(ic, id, Modifier.size(20.dp), tint = fg) }
        }
        // Same flip as the pilot card: no mirrored back face, and the shadow fades out edge-on
        Box(Modifier.size(128.dp).graphicsLayer { rotationY = if (rot > 90f) rot - 180f else rot; cameraDistance = 12 * density
                shadowElevation = 16.dp.toPx() * kotlin.math.abs(kotlin.math.cos(Math.toRadians(rot.toDouble()))).toFloat(); shape = CircleShape }
            .springClick(MaterialShapes.Cookie12Sided.toShape(), onClick = onTap), contentAlignment = Alignment.Center) {
            if (rot <= 90f) Box(Modifier.fillMaxSize().background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                // The initial paints at once; the photo fades over it if it ever loads (proxy, then direct), so a slow or dead link never shows as a wait.
                Text(profile.name.take(1), fontSize = 60.sp, fontWeight = FontWeight.Black, color = cs.onPrimaryContainer)
                val img = profile.image.orEmpty()
                var stage by remember(img) { mutableIntStateOf(if (img.isEmpty() || img in deadImages) 2 else 0) }
                if (stage < 2) AsyncImage(if (stage == 0) optimizedImageUrl(img, 256, 256) else img, profile.name, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    onError = { if (++stage == 2) deadImages += img })
            } else Column(Modifier.fillMaxSize().background(cs.tertiaryContainer), Arrangement.Center, Alignment.CenterHorizontally) {
                Icon(Sym.EmojiEventsFilled, null, Modifier.size(40.dp), tint = cs.onTertiaryContainer)
                Text(stringResource(R.string.about_hall_of_fame), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLargeEmphasized, color = cs.onTertiaryContainer)
            }
        }
    }
}


@Composable
fun AboutTab(vm: MainViewModel, padding: PaddingValues) {
    val s = LocalS.current; val d = LocalD.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val clip = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val profile = vm.config?.devProfile ?: Defaults.DEV_PROFILE; val socials = vm.config?.socials ?: Defaults.DEV_SOCIALS; val donation = vm.config?.donation ?: Defaults.DONATION
    var flipped by remember { mutableStateOf(false) }; var leaderboard by remember { mutableStateOf(false) }; var submit by remember { mutableStateOf(false) }
    LaunchedEffect(flipped) { if (flipped) { delay(6000); flipped = false } }
    LaunchedEffect(Unit) { if (s.coinFlipHintCount < 3) { delay(1600); flipped = true; vm.set { it.incrementCoinFlipHint() } } }
    val ads = s.adWatchCount
    val (rankLabel, rankIcon) = QUEST_BOSSES.lastOrNull { ads >= it.lvl }?.let { stringResource(R.string.about_rank, it.rank) to (BADGES[it.badge]?.first ?: Sym.Shield) } ?: (stringResource(R.string.about_start_your_journey) to Sym.RocketLaunch)
    val slots = s.showcasedBadges.take(3)
    // Groups cap at a card's width; centred, so on tablets they line up under the coin instead of hugging the left edge
    val wide = LocalWidthClass.current.wide
    LazyColumn(Modifier.fillMaxSize().adaptiveContentWidth(if (wide) 1040.dp else 840.dp), contentPadding = padding, horizontalAlignment = Alignment.CenterHorizontally) {
        item("header") { Header(vm) }
        item("hero") {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).reveal(0), horizontalAlignment = Alignment.CenterHorizontally) {
                DevCoin(vm, profile, slots, flipped) { if (flipped) { leaderboard = true; vm.haptic(H.MEDIUM) } else { flipped = true; vm.haptic(H.SELECTION) } }
                Text(profile.name, style = MaterialTheme.typography.displaySmallEmphasized)
                Text(if (flipped) stringResource(R.string.about_tap_again_for_the_hall_of) else stringResource(R.string.about_tap_the_coin_to_flip_it), style = MaterialTheme.typography.labelMedium, color = cs.primary)
                Text(profile.bio, Modifier.padding(horizontal = 36.dp, vertical = 10.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
        }
        item("badges") {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp).reveal(1), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                slots.forEachIndexed { i, id -> BadgeChip(id) { if (slots.size == 3) vm.profileClick("badges", i) } }
                if (slots.size < 3) AssistChip({ vm.profileClick("badges", slots.size) }, { Text(stringResource(R.string.about_add_badge)) }, leadingIcon = { Icon(Sym.Add, null, Modifier.size(AssistChipDefaults.IconSize)) }, shape = Pill)
            }
        }
        val connect = @Composable { m: Modifier -> Group(stringResource(R.string.guide_connect), m.reveal(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Triple(Sym.BrandGitHub, "GitHub", Gray) to socials.github, Triple(Sym.BrandX, "X", Color(0xFF64748B)) to socials.x, Triple(Sym.BrandDiscord, "Discord", Color(0xFF5865F2)) to socials.discord,
                    Triple(Sym.Public, stringResource(R.string.about_website), Color(0xFF6366F1)) to ORION_SITE)
                    .forEachIndexed { i, (spec, url) ->
                        val (bg, fg) = tonal(spec.third)
                        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val pressed by src.collectIsPressedAsState()
                        val morph = remember(i) { androidx.graphics.shapes.Morph(listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pentagon)[i], MaterialShapes.Circle) }
                        val p by animateFloatAsState(if (pressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "m")
                        Column(Modifier.springClick(RoundedCornerShape(28.dp), src = src) { vm.haptic(H.LIGHT); vm.tracker.open(url) }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(64.dp).clip(MorphShape(morph, p)).background(bg), contentAlignment = Alignment.Center) { Icon(spec.first, spec.second, Modifier.size(28.dp), tint = fg) }
                            Text(spec.second, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
            }
        } }
        val support = @Composable { m: Modifier -> Group(stringResource(R.string.about_support_orion), m.reveal(3)) {
            val next = QUEST_BOSSES.firstOrNull { ads < it.lvl }?.let { it.lvl to it.rank }
            val (bg, fg) = tonal(Fuchsia)
            var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
            val prog by animateFloatAsState(if (!shown) 0f else next?.let { ads / it.first.toFloat() } ?: 1f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "rank")
            Column(Modifier.fillMaxWidth().springClick(RoundedCornerShape(28.dp)) { vm.haptic(H.LIGHT); vm.open(Overlay.AD_DONATION) }.background(bg).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(fg.copy(.14f), MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) { Icon(rankIcon, null, tint = fg) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.about_fuel_the_code), style = MaterialTheme.typography.titleLargeEmphasized, color = fg)
                        Text(rankLabel, style = MaterialTheme.typography.bodyMedium, color = fg.copy(.85f))
                    }
                    Icon(Sym.PlayArrow, null, tint = fg)
                }
                LinearWavyProgressIndicator({ prog }, Modifier.fillMaxWidth().padding(top = 16.dp), color = fg, trackColor = fg.copy(.2f))
                Text(next?.let { stringResource(R.string.about_watched_more_to, ads, it.first - ads, it.second) } ?: stringResource(R.string.about_watched_every_rank_unlocked, ads), Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = fg.copy(.85f))
            }
            if (donation.kofiUrl != null || donation.upiId != null || donation.paypalUrl != null) Row(Modifier.fillMaxWidth().padding(top = 8.dp)
                .springClick(RoundedCornerShape(28.dp)) { vm.donateOpen = true; vm.haptic(H.SELECTION) }.background(cs.surfaceContainerHigh).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TonalIcon(Sym.Coffee, Caution); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.about_buy_me_a_coffee), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.about_ko_fi_upi_or_paypal), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(Sym.ChevronRight, null)
            }
        } }
        val help = @Composable { m: Modifier -> Group(stringResource(R.string.about_help), m.reveal(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple(Sym.HelpOutline, stringResource(R.string.guide_faqs), stringResource(R.string.about_secrets_safety)) to { vm.open(Overlay.FAQ) }, Triple(Sym.NewReleases, stringResource(R.string.about_what_s_new), stringResource(R.string.about_release_notes)) to { vm.open(Overlay.RELEASE_NOTES) })
                    .forEach { (spec, go) ->
                        Column(Modifier.weight(1f).springClick(RoundedCornerShape(28.dp)) { vm.haptic(H.LIGHT); go() }.background(cs.surfaceContainerHigh).padding(18.dp)) {
                            TonalIcon(spec.first, cs.primary)
                            Text(spec.second, Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
                            Text(spec.third, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
            }
        } }
        // Phones stack the groups; medium and expanded windows set them side by side in an adaptive grid of cards
        if (!wide) { item("connect") { connect(Modifier) }; item("support") { support(Modifier) }; item("help") { help(Modifier) } }
        else item("groups") {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), maxItemsInEachRow = 2, verticalArrangement = Arrangement.Top) {
                support(Modifier.weight(1f)); connect(Modifier.weight(1f)); help(Modifier.weight(1f)); Spacer(Modifier.weight(1f))
            }
        }
        item("footer") {
            Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = Pill, color = cs.surfaceContainerHigh) { Text("v$STORE_VERSION", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge) }
                    // The quiet RE mark: this build is the native rewrite of the web app
                    Surface(shape = Pill, color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer) { Text(stringResource(R.string.about_re_native), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLargeEmphasized) }
                    val c = if (s.useRemoteJson) Safe else Caution
                    Surface(shape = Pill, color = tonal(c).first, contentColor = tonal(c).second) {
                        Text(stringResource(R.string.about_source, if (s.useRemoteJson) stringResource(R.string.about_remote) else stringResource(R.string.about_local)), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(stringResource(R.string.about_made_with_for_geeks), Modifier.padding(top = 12.dp).clip(Pill).clickable { vm.profileClick() }.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
        }
    }
    if (leaderboard) LeaderboardSheet(vm, { leaderboard = false; flipped = false }) { leaderboard = false; submit = true }
    if (submit) LeaderboardSubmitSheet(vm) { submit = false }
    if (vm.donateOpen) DonationSheet(vm, donation) { vm.donateOpen = false }
}
// endregion

@Composable
private fun Group(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Column(modifier.padding(horizontal = 16.dp).padding(top = 28.dp).widthIn(max = 560.dp).fillMaxWidth()) {
    Text(title, Modifier.padding(start = 8.dp, bottom = 12.dp), style = MaterialTheme.typography.titleLargeEmphasized)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Developer tools, shown at the bottom of Settings > Developer once dev mode is unlocked. */
@Composable
fun DevTools(vm: MainViewModel) {
    val s = LocalS.current; val d = LocalD.current; val clip = LocalClipboardManager.current; val ctx = LocalContext.current
    val scope = rememberCoroutineScope(); var nuke by remember { mutableStateOf(false) }
    val checkingMsg = stringResource(R.string.about_checking); val unknownMsg = stringResource(R.string.common_unknown)
    val onNuke = { nuke = true }
    var editing by remember { mutableStateOf(false) }; var draft by remember { mutableStateOf("") }; var show by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf<Triple<Int, Int, Long>?>(null) }
    var pkg by remember { mutableStateOf("") }; var pkgResult by remember { mutableStateOf("") }; var showJson by remember { mutableStateOf(false) }
    LaunchedEffect(s.githubToken) {
        rate = runCatching {
            val core = json.parseToJsonElement(Net.text("https://api.github.com/rate_limit", if (s.githubToken.isNotEmpty()) mapOf("Authorization" to "token ${s.githubToken}") else emptyMap()))
                .jsonObject["resources"]!!.jsonObject["core"]!!.jsonObject
            Triple(core["limit"]!!.jsonPrimitive.int, core["remaining"]!!.jsonPrimitive.int, core["reset"]!!.jsonPrimitive.long)
        }.getOrNull()
    }
    fun save(t: String) { vm.set { it.copy(githubToken = t) }; editing = false; scope.launch { delay(500); vm.reload() } }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.about_developer_tools), Modifier.padding(start = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMediumEmphasized)
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.about_github_access), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.about_unlock_5000_requests_hr_authenticated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ editing = !editing; draft = s.githubToken }) { Text(if (editing) stringResource(R.string.common_cancel) else stringResource(R.string.about_edit_token)) }
                }
                if (editing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("ghp_...") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save(draft) }),
                        trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Sym.VisibilityOff else Sym.Visibility, if (show) stringResource(R.string.about_hide_token) else stringResource(R.string.about_show_token)) } })
                    Button({ save(draft) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_save)) }
                } else Row(Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.githubToken.isNotEmpty()) Safe else MaterialTheme.colorScheme.error))
                    Text(if (s.githubToken.isEmpty()) stringResource(R.string.about_no_token_set_rate_limited) else if (show) s.githubToken else "••••••••••••${s.githubToken.takeLast(4)}",
                        Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 14.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.githubToken.isNotEmpty()) {
                        IconButton({ show = !show }) { Icon(if (show) Sym.VisibilityOff else Sym.Visibility, if (show) stringResource(R.string.about_hide_token) else stringResource(R.string.about_reveal_token)) }
                        IconButton({ clip.setText(AnnotatedString(s.githubToken)) }) { Icon(Sym.ContentCopy, stringResource(R.string.about_copy_token)) }
                        IconButton({ save("") }) { Icon(Sym.DeleteOutline, stringResource(R.string.about_remove_token)) }
                    }
                }
            }
        }
        Text(stringResource(R.string.about_ui_debugger_alignment_check), Modifier.padding(start = 8.dp, top = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.allApps().firstOrNull()?.let { vm.installToast(it, "test.apk") } }) { Text(stringResource(R.string.about_toast_install)) }
            OutlinedButton({ vm.error(ctx.getString(R.string.about_this_is_a_test_error_message)) }) { Text(stringResource(R.string.about_toast_error)) }
            OutlinedButton({ val t = System.currentTimeMillis(); vm.upd { it.copy(pendingCleanup = mapOf("test-1" to CleanupEntry("a", t), "test-2" to CleanupEntry("b", t), "test-3" to CleanupEntry("c", t))) } }) { Text(stringResource(R.string.about_deck_clean)) }
            OutlinedButton({ vm.set { it.copy(hasSeenModernUITutorial = false) }; vm.tour = false; vm.open(Overlay.TUTORIAL) }) { Text(stringResource(R.string.about_trigger_try_new_ui)) }
            Button({ vm.forcedTest = "checking" }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.about_test_forced_update)) }
        }
        rate?.let { (limit, rem, reset) ->
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row { Text(stringResource(R.string.about_github_api_quota), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge); Text("$rem/$limit", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                    LinearProgressIndicator({ if (limit > 0) rem / limit.toFloat() else 0f }, Modifier.fillMaxWidth(), color = if (rem < limit * .2f) MaterialTheme.colorScheme.error else Safe)
                    Row { Text(if (s.githubToken.isNotEmpty()) stringResource(R.string.about_authenticated) else stringResource(R.string.about_public_ip), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.about_resets, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reset * 1000))), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        Text(stringResource(R.string.about_package_detective), Modifier.padding(start = 8.dp, top = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(pkg, { pkg = it }, Modifier.weight(1f), placeholder = { Text("com.example.app") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            FilledIconButton({
                if (pkg.isNotEmpty()) { pkgResult = checkingMsg; scope.launch {
                    pkgResult = runCatching { withContext(Dispatchers.IO) { vm.tracker.appInfo(pkg.trim()) }.let { "{\n  \"installed\": ${it.installed},\n  \"version\": ${it.version?.let { v -> "\"$v\"" }},\n  \"versionCode\": ${it.versionCode}\n}" } }.getOrElse { ctx.getString(R.string.about_error, it.message) }
                } }
            }, shapes = IconButtonDefaults.shapes()) { Icon(Sym.Search, stringResource(R.string.about_check_package)) }
        }
        if (pkgResult.isNotEmpty()) Code(pkgResult)
        OutlinedButton({ showJson = !showJson }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_raw_metadata_explorer), Modifier.weight(1f)); Icon(if (showJson) Sym.ExpandLess else Sym.ExpandMore, null) }
        if (showJson) {
            val raw = remember(d.apps) { if (d.apps.isEmpty()) ctx.getString(R.string.about_cache_empty) else runCatching { Json(json) { prettyPrint = true }.encodeToString(ListSerializer(AppItem.serializer()), d.apps) }.getOrDefault(ctx.getString(R.string.about_error_parsing_cache)) }
            Code(raw, Modifier.heightIn(max = 320.dp))
        }
        Row(Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.about_active_mirror), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(vm.mirrorSource.ifEmpty { unknownMsg }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            FilledTonalButton({ vm.set { it.copy(useRemoteJson = !it.useRemoteJson) } }, shapes = ButtonDefaults.shapes()) { Text(if (s.useRemoteJson) stringResource(R.string.about_remote_mode) else stringResource(R.string.about_local_mode)) }
        }
        OutlinedButton({ vm.set { it.withDevUnlocked(false) }; vm.settingsMenu = "none"; vm.haptic(H.WARNING) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.about_close_developer_options), fontWeight = FontWeight.Black) }
        Button(onNuke, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)) {
            Icon(Sym.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.about_nuclear_reset_wipe_restart), fontWeight = FontWeight.Black)
        }
    }
    if (nuke) AlertDialog({ nuke = false }, icon = { Icon(Sym.DeleteForever, null) }, title = { Text(stringResource(R.string.about_nuclear_reset)) },
        text = { Text(stringResource(R.string.about_clears_every_setting_cache_and_library)) },
        confirmButton = { Button({
            nuke = false; vm.set { Settings() }; vm.upd { DataState() }
            scope.launch { delay(700); ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) }; exitProcess(0) }
        }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.about_wipe_restart)) } },
        dismissButton = { TextButton({ nuke = false }) { Text(stringResource(R.string.common_cancel)) } })
}

@Composable
private fun Code(text: String, modifier: Modifier = Modifier) = SelectionContainer {
    Text(text, modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).verticalScroll(rememberScrollState()).padding(12.dp),
        fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Safe)
}

@Composable
fun DonationSheet(vm: MainViewModel, donation: DonationConfig, onDismiss: () -> Unit) {
    data class DRow(val key: String, val label: String, val helper: String, val value: String, val icon: ImageVector, val c: Color, val copy: String?, val open: String?)
    val rows = listOfNotNull(
        donation.kofiUrl?.let { DRow("kofi", "Ko-fi", stringResource(R.string.about_support_orion_through_ko_fi), it, Sym.Coffee, Color(0xFF29ABE0), null, stringResource(R.string.about_open_ko_fi)) },
        donation.upiId?.let { DRow("upi", "UPI", stringResource(R.string.about_copy_the_upi_id_to_pay), it, Sym.QrCode2, Color(0xFF22C55E), stringResource(R.string.about_copy_upi), null) },
        donation.paypalUrl?.let { DRow("paypal", "PayPal", stringResource(R.string.about_open_paypal_or_copy_the_payment), it, Sym.Payments, Color(0xFF0070BA), stringResource(R.string.about_copy_paypal), stringResource(R.string.about_open_paypal)) },
    )
    if (rows.isEmpty()) return
    val clip = LocalClipboardManager.current; val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<String?>(null) }; var copied by remember { mutableStateOf<String?>(null) }
    OrionSheet(onDismiss) {
        SheetTitle(stringResource(R.string.about_support_orion), Sym.Favorite, stringResource(R.string.about_choose_a_payment_method_to_support))
        rows.forEach { r ->
            Card({ open = if (open == r.key) null else r.key }, Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(if (open == r.key) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                ListItem({ Text(r.label, fontWeight = FontWeight.Bold) }, supportingContent = { Text(r.helper) }, colors = ListItemDefaults.colors(Color.Transparent),
                    leadingContent = { Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(r.c.copy(.15f)), contentAlignment = Alignment.Center) { Icon(r.icon, null, tint = r.c) } },
                    trailingContent = { Icon(if (open == r.key) Sym.ExpandLess else Sym.ExpandMore, null) })
                AnimatedVisibility(open == r.key) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectionContainer { Text(r.value, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            r.open?.let { Button({ vm.haptic(H.SELECTION); vm.tracker.open(if (r.value.startsWith("http://") || r.value.startsWith("https://")) r.value else "https://${r.value}") }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                                Icon(Sym.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(it) } }
                            r.copy?.let { FilledTonalButton({ clip.setText(AnnotatedString(r.value)); copied = r.key; vm.haptic(H.SUCCESS); scope.launch { delay(2000); copied = null } }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                                Icon(if (copied == r.key) Sym.Check else Sym.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (copied == r.key) stringResource(R.string.about_copied) else it) } }
                        }
                    }
                }
            }
        }
    }
}

/** Dicebear serves SVG by default; coil has no SVG decoder here, so render the PNG twin. */
fun pngAvatar(url: String) = url.replace("/svg?", "/png?")

@Composable
fun LeaderboardSheet(vm: MainViewModel, onDismiss: () -> Unit, onSubmit: () -> Unit) {
    val s = LocalS.current
    var entries by remember { mutableStateOf<List<LeaderboardEntry>?>(null) }; var error by remember { mutableStateOf(false) }; var nonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(nonce) {
        error = false; entries = null
        runCatching { json.decodeFromString(ListSerializer(LeaderboardEntry.serializer()), Net.text(WORKER_URL)).sortedByDescending { it.xp } }.onSuccess { entries = it }.onFailure { error = true }
    }
    val cooldown = s.lastSubmissionTime > 0 && System.currentTimeMillis() - s.lastSubmissionTime < 3_600_000
    OrionSheet(onDismiss) {
        SheetTitle(stringResource(R.string.about_hall_of_fame_2), Sym.EmojiEvents, stringResource(R.string.about_global_top_contributors))
        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 560.dp), contentAlignment = Alignment.Center) {
            val list = entries
            when {
                error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Sym.WifiOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.about_signal_lost), Modifier.padding(8.dp), style = MaterialTheme.typography.titleMedium)
                    FilledTonalButton({ nonce++ }) { Text(stringResource(R.string.about_reconnect)) }
                }
                list == null -> ContainedLoadingIndicator()
                list.isEmpty() -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.about_it_s_quiet_too_quiet), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.about_the_hall_of_fame_is_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.about_be_the_first_legend_to_rise), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                else -> LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(list) { i, e ->
                        val rank = listOf(Color(0xFFEAB308), Color(0xFFF97316), Color(0xFF94A3B8)).getOrNull(i)
                        val cls = when (e.`class`) { "Warrior" -> Color(0xFFF87171); "Scribe" -> Color(0xFF60A5FA); else -> Color(0xFFC084FC) }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(rank?.copy(.1f) ?: MaterialTheme.colorScheme.surfaceContainer)
                            .then(if (rank != null) Modifier.border(1.dp, rank, RoundedCornerShape(20.dp)) else Modifier).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", Modifier.width(28.dp), textAlign = TextAlign.Center, style = if (i < 3) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black, color = if (i < 3) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(.5f))
                            // Only the built-in avatars render: the list is public data, and one entry was pushed with an outside image
                            AsyncImage(pngAvatar(e.avatar_url.takeIf { u -> CHARACTERS.any { it.url == u } } ?: CHARACTERS[0].url), e.username, Modifier.padding(horizontal = 10.dp).size(48.dp).clip(CircleShape).border(2.dp, if (i == 0) Color(0xFFFACC15) else MaterialTheme.colorScheme.surface, CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(e.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                                    if (i < 3) Icon(Sym.WorkspacePremium, null, Modifier.padding(start = 4.dp).size(14.dp), tint = rank!!)
                                }
                                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(e.`class`, Modifier.clip(RoundedCornerShape(6.dp)).background(cls.copy(.12f)).border(1.dp, cls.copy(.3f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = cls)
                                    Text(stringResource(R.string.about_lvl, e.level), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(NumberFormat.getInstance().format(e.xp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                Text("XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Button(onSubmit, modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 56.dp), enabled = !cooldown, shapes = ButtonDefaults.shapes()) {
            Icon(if (cooldown) Sym.Lock else Sym.RocketLaunch, null); Spacer(Modifier.width(8.dp)); Text(if (cooldown) stringResource(R.string.about_rank_claimed_wait_1h) else stringResource(R.string.about_claim_global_rank), fontWeight = FontWeight.Bold)
        }
        Text(stringResource(R.string.about_rankings_are_synchronized_every_hour_after), Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class Avatar(val id: String, @StringRes val name: Int, val url: String)
val CHARACTERS = listOf(
    Avatar("ears1", R.string.about_imp, "https://api.dicebear.com/9.x/big-ears/svg?seed=Avery"), Avatar("pixel1", R.string.about_8_bit, "https://api.dicebear.com/9.x/pixel-art/svg?seed=Avery"),
    Avatar("mecha", R.string.about_mecha, "https://api.dicebear.com/9.x/bottts/svg?seed=Mecha&backgroundColor=c0aede"), Avatar("pixel3", R.string.about_voxel, "https://api.dicebear.com/9.x/pixel-art/svg?seed=Kimberly"),
    Avatar("pixel2", R.string.about_retro, "https://api.dicebear.com/9.x/pixel-art/svg?seed=Wyatt"), Avatar("ears2", R.string.about_pixie, "https://api.dicebear.com/9.x/big-ears/svg?seed=Sara"),
    Avatar("smile1", R.string.about_cheery, "https://api.dicebear.com/9.x/big-smile/svg?seed=Jessica"), Avatar("unit", R.string.about_unit_01_name, "https://api.dicebear.com/9.x/bottts/svg?seed=Unit01&backgroundColor=c0aede"),
    Avatar("smile2", R.string.about_glee, "https://api.dicebear.com/9.x/big-smile/svg?seed=Liliana"), Avatar("pixel4", R.string.about_block, "https://api.dicebear.com/9.x/pixel-art/svg?seed=Oliver"),
)
val LOCAL_BANNED_TERMS = listOf(
    "chut", "chutiya", "chooch", "bhosdi", "bhosad", "madarchod", "mc", "bc", "bhenchod", "behenchod", "gand", "gaand", "lund", "lavda", "lauda", "loda", "jhaatu", "jhat", "kutta", "kamina", "harami",
    "saala", "randi", "raand", "bhadwa", "chinaal", "hijra", "chakka", "tatti", "panty", "nipple", "bkl", "bkc", "mkc", "mkl", "tmkc", "tkmkc", "pkmkb", "kbkc", "bsdk", "bhosdk", "mdrchod",
    "चूतिया", "मादरचोद", "भोसड़ी", "गांड", "लंड", "लौड़ा", "रंडी", "हरामी", "साला", "कुत्ता", "भड़वा", "बेशर्म", "कमीना",
    "fuck", "nigger", "nigga", "faggot", "rapist", "pedophile", "pedo", "whore", "slut", "cunt", "dick", "pussy", "cock", "boobs", "vagina", "penis", "anal", "incest", "porn", "xxx", "sex", "hitler", "nazi",
)
private val BLOCKLIST_SOURCES = listOf(
    "https://raw.githubusercontent.com/web-mech/badwords/master/lib/lang.json" to true, "https://raw.githubusercontent.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/master/en" to false,
    "https://raw.githubusercontent.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/master/hi" to false, "https://raw.githubusercontent.com/zautumnz/profane-words/master/words.json" to true,
)
private val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's', '!' to 'i', '(' to 'c', '|' to 'i', 'v' to 'u')
private val ASCII_TERM = Regex("^[a-z]+$")

fun isProfane(text: String, list: Collection<String>): Boolean {
    val norm = text.lowercase().map { LEET[it] ?: it }.filter { it in 'a'..'z' }.joinToString("")
    val raw = text.lowercase()
    return list.any { (ASCII_TERM.matches(it) && it in norm) || it in raw }
}

/** Port of utils/crypto.ts: SHA-256 over sorted `key:value` pairs joined by `|`, plus the shared salt. */
fun leaderboardSignature(data: Map<String, Any>): String =
    MessageDigest.getInstance("SHA-256").digest((data.keys.sorted().joinToString("|") { "$it:${data[it]}" } + "ORION_OMEGA_PROTOCOL_X9_SECURE_HASH_V1").toByteArray())
        .joinToString("") { "%02x".format(it) }

/** Signs and posts one leaderboard entry; (HTTP code, body). Used by the Fame Gate and the daily background refresh. */
suspend fun postLeaderboard(s: Settings, xp: Long, name: String, avatarUrl: String, now: Long): Pair<Int, String> {
    val payload = linkedMapOf<String, Any>("username" to name, "avatarUrl" to avatarUrl, "xp" to xp, "level" to Xp.level(xp), "adWatchCount" to s.adWatchCount,
        "submissionCount" to s.submissionCount, "changeRequestCount" to s.changeRequestCount, "timestamp" to now)
    val body = buildJsonObject {
        putJsonObject("data") { payload.forEach { (k, v) -> if (v is String) put(k, v) else put(k, (v as Number).toLong()) } }
        put("signature", leaderboardSignature(payload))
    }.toString()
    return Net.postJson(WORKER_URL, body)
}

@Composable
fun LeaderboardSubmitSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val s = LocalS.current; val scope = rememberCoroutineScope(); val saved = s.userProfile; val ctx = LocalContext.current
    var status by remember { mutableStateOf("idle") }; var err by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(saved?.name ?: "") }
    var char by remember { mutableStateOf(CHARACTERS.firstOrNull { it.id == saved?.avatarId } ?: CHARACTERS[0]) }
    var blocklist by remember { mutableStateOf(LOCAL_BANNED_TERMS.toSet()) }
    val canSubmit = s.adWatchCount >= 5 || s.isContributor
    val xp = Xp.total(s, LocalD.current.appLibrary.size); val level = Xp.level(xp)
    LaunchedEffect(Unit) {
        val words = coroutineScope { BLOCKLIST_SOURCES.map { (url, isJson) -> async { runCatching {
            val t = Net.text(url)
            if (!isJson) t.lines().map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            else when (val e = json.parseToJsonElement(t)) {
                is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.lowercase() }
                is JsonObject -> (e["words"] as? JsonArray)?.map { it.jsonPrimitive.content.lowercase() }.orEmpty()
                else -> emptyList()
            }
        }.getOrDefault(emptyList()) } }.awaitAll().flatten() }
        blocklist = blocklist + words
    }
    suspend fun network() {
        val now = System.currentTimeMillis()
        runCatching { postLeaderboard(s, xp, name.trim(), char.url, now) }.onFailure { status = "error"; err = it.message ?: ctx.getString(R.string.about_unknown_error) }.onSuccess { (code, res) ->
            if (code !in 200..299) { status = "error"; err = if (code == 429) ctx.getString(R.string.about_whoa_you_can_only_join_once) else runCatching { json.parseToJsonElement(res).jsonObject["error"]!!.jsonPrimitive.content }.getOrDefault(ctx.getString(R.string.about_portal_connection_failed)); return }
            status = "success"
            vm.set { it.registerSubmission(now).copy(lastLeaderboardSubmissionTime = now, leaderboardXp = xp).withProfile(UserProfile(name.trim(), char.id, char.url), now) }
        }
    }
    fun submit(sim: Boolean) {
        err = ""; val n = name.trim()
        err = when {
            n.isEmpty() -> ctx.getString(R.string.about_please_enter_a_cool_name); n.length > 15 -> ctx.getString(R.string.about_name_too_long_max_15_chars); n.length < 3 -> ctx.getString(R.string.about_name_too_short_min_3_chars)
            isProfane(n, blocklist) -> ctx.getString(R.string.about_let_s_keep_it_family_friendly); !canSubmit && !sim -> ctx.getString(R.string.about_you_need_5_contributions_to_join)
            !sim && !Xp.plausible(s, System.currentTimeMillis()) -> ctx.getString(R.string.about_your_progress_failed_the_integrity_check); else -> ""
        }
        if (err.isNotEmpty()) return
        status = "processing"; scope.launch { delay(2500); if (sim) status = "success" else network() }
    }
    OrionSheet(onDismiss) {
        SheetTitle(stringResource(R.string.about_fame_gate), Sym.MilitaryTech, stringResource(R.string.about_join_the_legends))
        AnimatedContent(status, Modifier.fillMaxWidth(), label = "gate") { st ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (st) {
                    "idle" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(stringResource(R.string.about_level) to "$level", "XP" to "$xp").forEach { (k, v) ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(k.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        if (saved != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(pngAvatar(char.url), null, Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(stringResource(R.string.about_submitting_as), style = MaterialTheme.typography.labelSmall); Text(saved.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Sym.Lock, null, Modifier.size(12.dp)); Text(stringResource(R.string.about_alias_secured), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                Text(stringResource(R.string.about_forever_locked_to_this_device), fontSize = 9.sp)
                            }
                        } else {
                            Text(stringResource(R.string.about_choose_your_avatar), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge)
                            LazyVerticalGrid(GridCells.Fixed(5), Modifier.heightIn(max = 160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                                itemsIndexed(CHARACTERS) { _, c ->
                                    val sel = c.id == char.id
                                    val sc by animateFloatAsState(if (sel) 1.1f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "sc")
                                    AsyncImage(pngAvatar(c.url), stringResource(c.name), Modifier.aspectRatio(1f).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape)
                                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest).clickable { char = c; vm.haptic(H.SELECTION) }.padding(4.dp))
                                }
                            }
                            Text(stringResource(char.name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            OutlinedTextField(name, { if (it.length <= 15) name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.about_your_alias_locked_after_first_save)) }, placeholder = { Text(stringResource(R.string.about_captain_code)) },
                                singleLine = true, shape = RoundedCornerShape(16.dp), supportingText = { Text("${name.length}/15") })
                        }
                        Button({ submit(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), enabled = name.isNotEmpty() && canSubmit, shapes = ButtonDefaults.shapes()) {
                            Icon(if (canSubmit) Sym.RocketLaunch else Sym.Lock, null); Spacer(Modifier.width(8.dp))
                            Text(if (canSubmit) stringResource(R.string.about_beam_me_up) else stringResource(R.string.about_need_5_contributions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }
                        if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        if (s.isDevUnlocked) TextButton({ submit(true) }) { Icon(Sym.BugReport, null, Modifier.size(16.dp)); Text(stringResource(R.string.about_simulate_protocol_debug)) }
                    }
                    "processing" -> {
                        Box(contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(140.dp)); AsyncImage(pngAvatar(char.url), null, Modifier.size(64.dp).clip(CircleShape)) }
                        Text(stringResource(R.string.about_warping_to_multiverse), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(stringResource(R.string.about_writing_to_global_ledger), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }
                    "success" -> {
                        Icon(Sym.Verified, null, Modifier.size(72.dp), tint = Safe)
                        Text(stringResource(R.string.about_you_re_in), style = MaterialTheme.typography.headlineMediumEmphasized)
                        Text(stringResource(R.string.about_has_been_immortalized_in_the_hall, name.trim()), textAlign = TextAlign.Center)
                        Text(stringResource(R.string.about_the_leaderboard_refreshes_every_60_minutes), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.about_return_to_base)) }
                    }
                    else -> {
                        Icon(Sym.WifiOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.about_signal_lost), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(err, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton({ status = "idle" }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.about_try_again)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
