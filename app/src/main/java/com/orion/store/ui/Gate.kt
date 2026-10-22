package com.orion.store.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orion.store.data.*
import com.orion.store.platform.VtView
import kotlinx.coroutines.delay

private val TEST_STATUSES = listOf("checking", "downloading", "download_failed", "ready_to_scan_or_install", "byok_required", "scanning", "scan_complete", "installing", "install_failed", "export_ready", "installed")

@Composable
fun ForcedGate(vm: MainViewModel) {
    val s = LocalS.current
    val test = vm.forcedTest
    val snapScan = s.forcedStoreUpdate?.status == "scan_complete"
    val status = test ?: if (vm.fStatus == "inactive" && (vm.fResult != null || vm.forcedVt.view == VtView.RESULTS || snapScan)) "scan_complete" else vm.fStatus
    val stats = if (test != null) (if (status == "scan_complete") VtStats(harmless = 70, undetected = 2) else null) else (vm.fResult ?: vm.forcedVt.result)?.stats
    val can = status == "ready_to_scan_or_install" || status == "scan_complete"
    val exported = if (test != null) status == "install_failed" else s.forcedStoreUpdate?.exported == true
    val exportPath = if (test != null) "/storage/emulated/0/Download/OrionStore_9.9.9.apk" else vm.fExportPath
    val scanPct = if (test != null) (if (status == "scanning") 42f else 0f) else vm.forcedVt.progress
    val progress = if (test != null) (FORCED_TEST_PROGRESS[status] ?: 0).toFloat() else vm.fProgress.toFloat()
    val text = if (test != null) FORCED_TEST_TEXT[status].orEmpty() else vm.fText
    val v = forcedVm(status, if (test != null) "" else vm.fError, text, can, can, exported, exportPath, scanPct, stats)
    val target = if (test != null) "9.9.9" else s.forcedStoreUpdate?.targetVersion ?: vm.config?.latestStoreVersion ?: vm.config?.minStoreVersion ?: "0.0.0"
    fun act(id: String) {
        if (test != null) { vm.forcedTest = mapOf("retry" to "downloading", "scan" to "scanning", "install" to "installing", "export" to "export_ready")[id] ?: test; return }
        when (id) { "retry" -> vm.forcedRetryDownload(); "scan" -> vm.forcedScan(); "install" -> vm.forcedInstall(); "export" -> vm.forcedExport(); "open-report" -> vm.forcedReport() }
    }
    val tint = when (v.tone) { "warning" -> MaterialTheme.colorScheme.error; "success" -> Safe; else -> MaterialTheme.colorScheme.primary }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding().padding(horizontal = 28.dp, vertical = 40.dp).widthIn(max = 520.dp).align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                if (v.showProgress) ContainedLoadingIndicator(Modifier.size(112.dp))
                else Box(Modifier.fillMaxSize().clip(MaterialShapes.Cookie9Sided.toShape()).background(tint.copy(.16f)), contentAlignment = Alignment.Center) {
                    Icon(when (v.tone) { "warning" -> Sym.ErrorOutline; "success" -> Sym.CheckCircle; else -> Sym.SystemUpdate }, null, Modifier.size(48.dp), tint = tint)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(v.eyebrow.uppercase(), style = MaterialTheme.typography.labelLarge, color = tint, fontWeight = FontWeight.Black)
            Text(v.title, style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            AnimatedContent(v.body, label = "body") { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)) }
            if (v.showVersionRow) Row(Modifier.padding(top = 16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("v$STORE_VERSION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Sym.ArrowForward, null, Modifier.padding(horizontal = 8.dp).size(16.dp))
                Text("v$target", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (v.showProgress) {
                val scanning = status == "scanning"
                val p by animateFloatAsState((if (scanning) v.scanProgress else progress).coerceIn(0f, 100f) / 100f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "p")
                Row(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                    Text(if (scanning) "扫描中" else "进行中", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth().padding(top = 8.dp))
                if (scanning && test == null && vm.forcedVt.note.isNotEmpty()) Text(vm.forcedVt.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            if (status == "scan_complete" && stats != null) Verdict(stats)
            if (status == "byok_required" && test == null) KeyEntry(vm)
            Spacer(Modifier.height(28.dp))
            v.actions.forEach { a ->
                val m = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 56.dp)
                val content: @Composable RowScope.() -> Unit = { Text(a.label, style = MaterialTheme.typography.titleSmall) }
                when (a.style) {
                    "primary" -> Button({ act(a.id) }, modifier = m, enabled = a.enabled, shapes = ButtonDefaults.shapes(), content = content)
                    "secondary" -> FilledTonalButton({ act(a.id) }, modifier = m, enabled = a.enabled, shapes = ButtonDefaults.shapes(), content = content)
                    else -> OutlinedButton({ act(a.id) }, modifier = m, enabled = a.enabled, shapes = ButtonDefaults.shapes(), content = content)
                }
            }
            if (status == "byok_required" && test == null) TextButton(vm::forcedByok) { Text("打开 VirusTotal 设置") }
            if (v.showSupportBlock) Card(Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    if (v.showExportPath) {
                        Text("保存至", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(exportPath, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    } else Text("如果安装器始终失败，可将 APK 保存到下载文件夹，然后在文件管理器中手动安装。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (test == null && s.devConfigEnabled) TextButton(vm::forcedBypass, Modifier.padding(top = 12.dp)) { Text("开发者绕过") }
            Spacer(Modifier.height(if (test != null) 200.dp else 24.dp))
        }
        if (test != null) DevPanel(vm, status, progress, text, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Verdict(st: VtStats) {
    val safe = st.verdict == "safe"; val c = if (safe) Safe else Caution
    Card(Modifier.fillMaxWidth().padding(top = 24.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(c.copy(.12f))) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (safe) Sym.VerifiedUser else Sym.GppMaybe, null, tint = c); Spacer(Modifier.width(10.dp))
                Text(if (safe) "扫描完成" else "扫描完成，但需谨慎", style = MaterialTheme.typography.titleMediumEmphasized, color = c)
            }
            Text(if (safe) "没有引擎标记该 APK" else "安装前请先查看报告", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("安全" to st.harmless + st.undetected, "报毒" to st.flagged, "引擎" to st.total).forEach { (l, n) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface.copy(.6f)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$n", style = MaterialTheme.typography.titleLargeEmphasized, fontWeight = FontWeight.Black)
                        Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyEntry(vm: MainViewModel) {
    val saved = LocalS.current.virusTotalApiKey
    var key by remember { mutableStateOf("") }; var show by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("VirusTotal API 密钥") }, placeholder = { Text("输入你的 VirusTotal API 密钥") }, singleLine = true,
            shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Sym.VisibilityOff else Sym.Visibility, if (show) "隐藏密钥" else "显示密钥") } })
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.forcedSaveKey(key) }, enabled = key.isNotBlank(), shapes = ButtonDefaults.shapes()) { Text("保存并扫描") }
            if (saved.isNotEmpty()) {
                IconButton({ clip.setText(AnnotatedString(saved)) }) { Icon(Sym.ContentCopy, "复制密钥") }
                TextButton({ vm.set { it.copy(virusTotalApiKey = "") } }) { Text("撤销已存密钥") }
            }
        }
    }
}

@Composable
private fun DevPanel(vm: MainViewModel, status: String, progress: Float, text: String, modifier: Modifier) {
    var open by remember { mutableStateOf(true) }
    ElevatedCard(modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth().widthIn(max = 560.dp), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.clickable { open = !open }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("开发", Modifier.padding(2.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("强制更新预览", style = MaterialTheme.typography.labelLarge)
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(bottom = 12.dp)) {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Text("模拟预览", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)); Text("${progress.toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
                Text(text.ifEmpty { "预览当前的强制更新状态。" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TEST_STATUSES) { st -> FilterChip(st == status, { vm.forcedTest = st; vm.haptic(H.SELECTION) }, { Text(st.replace('_', ' ')) }) }
                }
            }
        }
    }
}

@Composable
fun Maintenance(vm: MainViewModel) {
    var game by remember { mutableStateOf(false) }; var donate by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }; var last by remember { mutableLongStateOf(0L) }; var dev by remember { mutableStateOf(false) }
    val socials = vm.config?.socials ?: Defaults.DEV_SOCIALS
    val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography; val still = LocalS.current.disableAnimations
    val inf = rememberInfiniteTransition(label = "bg")
    val spin by inf.animateFloat(0f, if (still) 0f else 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "spin")
    // A slow breathing fill instead of the indeterminate sweep, which reads as frantic on a page meant to calm
    val fill by inf.animateFloat(.35f, if (still) .35f else .85f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fill")
    var k by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) { while (!still) { delay(2600); if (!game) k++ } }
    Box(Modifier.fillMaxSize().background(cs.surface)) {
        Box(Modifier.align(Alignment.TopStart).offset((-90).dp, (-70).dp).size(280.dp).graphicsLayer { rotationZ = spin }
            .clip(MaterialShapes.SoftBurst.toShape()).background(cs.primaryContainer.copy(.5f)))
        Box(Modifier.align(Alignment.BottomEnd).offset(100.dp, 80.dp).size(320.dp).graphicsLayer { rotationZ = -spin }
            .clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.tertiaryContainer.copy(.45f)))
        // One screen, no scroll: the hero takes whatever height is left and shrinks on short phones or long messages
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val size = minOf(maxHeight - 40.dp, 176.dp)
                if (size >= 96.dp) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MorphBlob(listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Sunny, MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst), k, size, cs.primaryContainer,
                        Modifier.springClick(CircleShape) { vm.haptic(H.SELECTION); game = true }) {
                        DinoCirclePreview(size * .6f, paused = game, Modifier.clip(CircleShape).background(cs.surfaceContainerLowest))
                    }
                    Surface(onClick = { vm.haptic(H.SELECTION); game = true }, modifier = Modifier.padding(top = 10.dp), shape = Pill, color = cs.tertiary, contentColor = cs.onTertiary) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Sym.SportsEsports, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("点按开玩", style = ty.labelLargeEmphasized)
                        }
                    }
                }
            }
            Surface(Modifier.padding(top = 12.dp), shape = Pill, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.Construction, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("全面整修中", style = ty.labelLarge)
                }
            }
            Text("正在改进 Orion", Modifier.padding(top = 8.dp), style = ty.headlineLargeEmphasized, textAlign = TextAlign.Center)
            Text(vm.config?.maintenanceMessage ?: "我们正在对商店进行大修打磨。稍安勿躁，值得等待。",
                Modifier.padding(top = 8.dp).widthIn(max = 440.dp), style = ty.bodyMedium, textAlign = TextAlign.Center, color = cs.onSurfaceVariant,
                maxLines = 9, overflow = TextOverflow.Ellipsis)
            Column(Modifier.padding(top = 16.dp).widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ContainedLoadingIndicator(Modifier.size(40.dp), containerColor = cs.primaryContainer, indicatorColor = cs.primary)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("商店状态", style = ty.labelMedium, color = cs.onSurfaceVariant)
                            Text("维护中", style = ty.titleMediumEmphasized, color = cs.primary)
                        }
                    }
                    LinearWavyProgressIndicator({ fill }, Modifier.fillMaxWidth(), waveSpeed = 6.dp)
                }
                CtaButton("支持开发", { vm.haptic(H.SELECTION); donate = true }, icon = Sym.Favorite)
                if (socials.discord.isNotEmpty()) CtaButton("加入社区", { vm.haptic(H.SELECTION); vm.tracker.open(socials.discord) }, icon = Sym.BrandDiscord, tonal = true)
                AnimatedVisibility(dev) {
                    OutlinedButton({ vm.bypassMaintenance = true; vm.haptic(H.SUCCESS) }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                        Icon(Sym.Terminal, null); Spacer(Modifier.width(8.dp)); Text("开发者绕过")
                    }
                }
            }
            Text("Orion Store · $STORE_VERSION", Modifier.padding(top = 8.dp).clip(CircleShape).clickable(indication = null, interactionSource = null) {
                val now = System.currentTimeMillis()
                if (now - last < 500) { taps++; if (taps == 7) { dev = true; vm.haptic(H.SUCCESS) } else if (taps > 3) vm.haptic(H.SELECTION) } else taps = 1
                last = now
            }.padding(8.dp), style = ty.labelSmall, color = cs.onSurfaceVariant)
        }
    }
    if (game) DinoGameSheet(vm) { game = false }
    if (donate) DonationSheet(vm, vm.config?.donation ?: Defaults.DONATION) { donate = false }
}
