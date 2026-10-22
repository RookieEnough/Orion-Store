package com.orion.store.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.orion.store.data.Tab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One thing Orion does. [go] jumps straight to it, when it has a home of its own. */
class Feature(val icon: ImageVector, val title: String, val body: String, val go: (MainViewModel.() -> Unit)? = null)

private class Chapter(val id: String, val title: String, val blurb: String, val icon: ImageVector, val color: Color, val shape: RoundedPolygon, val features: List<Feature>)

/** Every feature with its chapter, in reading order: Orion AI answers "how do I..." from this and can open the right screen. */
val GUIDE: List<Pair<String, Feature>> by lazy { CHAPTERS.flatMap { c -> c.features.map { c.title to it } } }

private fun settings(page: String): MainViewModel.() -> Unit = { openSettings(page) }

/** Every feature in the app, chapter by chapter. Anything new that users can see or do gets a line here. */
private val CHAPTERS = listOf(
    Chapter("store", "商店", "三个目录、两种布局，以及众多找到心仪应用的方式。", Sym.Apps, Color(0xFF6366F1), MaterialShapes.Cookie9Sided, listOf(
        Feature(Sym.Android, "应用、PC 与电视", "Android 应用、PC 软件和电视应用各有独立目录，配有专属的标签页、搜索、排序和筛选。", { navTo(Tab.ANDROID) }),
        Feature(Sym.ViewQuilt, "现代与经典布局", "现代布局是带焦点大图、频道与货架的店面，经典布局是简洁的分组列表。可随时在“设置 → 界面”中切换。", settings("interface")),
        Feature(Sym.Star, "精选焦点位", "人工精选的应用在现代主页顶部轮播滑过，带着各自的图标配色，截图呈扇形展开。"),
        Feature(Sym.AutoAwesome, "为你推荐", "可滑动的每日精选轮播，外加编号排位的精选应用榜单。"),
        Feature(Sym.NewReleases, "最新与更新", "最新上架和刚更新的应用拥有专属频道，点一下即可查看完整列表。"),
        Feature(Sym.Category, "分类货架", "富有表现力的分类色块与频道，如媒体、社交、隐私和工具，每个都标注应用数量。"),
        Feature(Sym.Layers, "应用合集", "精心策划的应用合集文件夹。打开即可查看其中所有应用，添加或移除应用，并逐个安装。"),
        Feature(Sym.LibraryAdd, "自建合集", "三步打造合集：挑选应用，设置名称、颜色和图标，然后保存在本地或提交给所有人。", { open(Overlay.CUSTOM_BUNDLE) }),
        Feature(Sym.AutoAwesome, "Orion AI", "点按星形图标，描述你的需求，比如一款免费的音乐应用。可使用自己的 Gemini、ChatGPT 或 Claude 密钥，也可使用在手机上运行的模型。星形图标可随意拖动。", { open(Overlay.AI) }),
        Feature(Sym.Search, "搜索", "顶部的搜索胶囊可按名称、开发者、分类或描述查找应用，并能容忍重音符号和空格拼写误差。"),
        Feature(Sym.SwapVert, "排序与筛选", "按最新、最早、名称或大小排序，也可用筛选按钮把列表收窄到单一分类。"),
        Feature(Sym.Favorite, "收藏", "长按任意卡片、滑动经典列表行，或在应用页面点按心形图标。心形筛选器只显示你的收藏。"),
        Feature(Sym.Description, "“关于此应用”预览", "经典列表行会显示每条描述的开头几行，点按“查看更多”即可就地展开阅读。可在“设置 → 界面”中关闭。", settings("interface")),
        Feature(Sym.Sell, "分类图标", "经典列表行中每个分类都有自己的图标，再长的列表也能一眼扫清。"),
        Feature(Sym.SystemUpdate, "更新面板", "有待更新的应用时，应用主页会出现一条纤细横条列出现状，并支持一键“全部更新”。"),
        Feature(Sym.Campaign, "公告", "Orion 团队的新闻会从搜索胶囊中的扩音器图标送达。在你阅读之前它会一直脉动提醒。"),
        Feature(Sym.AutoFixHigh, "下拉刷新角色", "下拉重新加载目录时，会看到像素猫、狗、猫头鹰、幽灵等小伙伴或一个灵动的形状。可在 Orion Xtra 中挑选你的角色。", settings("installer")),
        Feature(Sym.KeyboardArrowUp, "回到顶部", "向下滚动较远后会出现一枚悬浮箭头，轻点一下即可回到顶部。"),
        Feature(Sym.AddCircleOutline, "提交应用", "推荐来自 GitHub、GitLab 或 Codeberg 的应用。Orion 会自动识别名称、包名和描述，支持导入 Obtainium 导出文件，并生成现成的 GitHub issue。", { open(Overlay.SUBMISSION) }),
    )),
    Chapter("page", "应用页面", "关于单个应用的一切，以及获取它的所有方式。", Sym.Info, Color(0xFF0EA5E9), MaterialShapes.Sunny, listOf(
        Feature(Sym.Download, "一键安装", "在应用页面即可下载、安装、更新或打开应用。下载时进度会环绕图标显示。"),
        Feature(Sym.Image, "屏幕截图", "按原始比例浏览截图，点按即可全屏查看。"),
        Feature(Sym.History, "版本与频道", "可为每个应用选择稳定版、测试版、内测版或每夜版，也可从版本列表中挑选确切的历史版本。"),
        Feature(Sym.Memory, "匹配你手机的安装包", "Orion 会自动挑选与手机处理器匹配的 APK，也允许你自行选择其他架构。"),
        Feature(Sym.Info, "应用信息", "开发者、大小、包名、来源和补丁信息，折叠收纳，需要时再展开。"),
        Feature(Sym.Policy, "VirusTotal 扫描", "使用自己的免费密钥，把下载文件、已安装的 APK 或发布文件交给 70 多款杀毒引擎检测。"),
        Feature(Sym.Code, "来源与安全", "一键跳转到源代码、官方发布页或开发者网站。"),
        Feature(Sym.NotificationsOff, "忽略更新", "可将某个更新隐藏一周、隐藏到下一个版本或永久隐藏。被忽略的更新会留在“我的应用”里，并带有“取消忽略”按钮。"),
        Feature(Sym.SaveAlt, "导出与重新下载", "把 APK 保存到“下载”文件夹、重新获取，或删除已保存的副本以释放空间。"),
        Feature(Sym.Share, "分享链接与徽章", "复制可在 Orion 中直接打开该应用的深链、网页链接，或用于 README 的 Markdown / HTML 徽章。"),
        Feature(Sym.EditNote, "申请修改", "发现图标有误、链接失效或描述过时？针对该字段提交一条修正即可。"),
        Feature(Sym.Report, "报告问题", "下载出问题了？在应用页面报告，详细信息会自动为你填好。"),
        Feature(Sym.Extension, "MicroG 助手", "需要 MicroG 的应用会明确标注，并提供安装链接和了解更多的指南。"),
        Feature(Sym.Link, "深链", "orionstore:// 链接和 Android 的“商店内应用详情”都能在 Orion 中打开对应页面。"),
    )),
    Chapter("library", "我的应用与下载", "你的资料库、下载队列，以及自动打理的更新。", Sym.GridView, Color(0xFF22C55E), MaterialShapes.Clover4Leaf, listOf(
        Feature(Sym.Inventory2, "资料库", "通过 Orion 下载或安装的每个应用都在这里，可按全部、已安装、已下载和待更新筛选，还配有专属搜索。", { navTo(Tab.MYAPPS) }),
        Feature(Sym.Downloading, "下载队列", "进行中的下载、待安装和待更新集中在一处，当安装可以静默执行时还会提供“全部更新”。", settings("queue")),
        Feature(Sym.Notifications, "后台下载", "离开 Orion 后下载仍会继续，通知栏实时显示进度，下载完成后点一下即可安装。"),
        Feature(Sym.Autorenew, "自动更新", "Orion 每隔几小时检查一次更新，即使应用已关闭或重启后也不例外，并自动为你下载。", settings("network")),
        Feature(Sym.SystemUpdate, "自动安装", "让 Orion 自动安装它下载好的更新。未能完成的安装会在你下次打开应用时报告。", settings("network")),
        Feature(Sym.Verified, "仅限稳定版", "只要存在稳定版，就跳过测试版、内测版和每夜版构建。", settings("network")),
        Feature(Sym.Wifi, "仅限 Wi-Fi", "不让下载消耗移动数据流量。", settings("network")),
        Feature(Sym.CleaningServices, "清洁工", "残留的 APK 会在安装一周后清理，开启“自动清理”后更是随装随清。文件堆积时会出现一个胶囊按钮，一键即可清理。", settings("storage")),
    )),
    Chapter("install", "安装，由你做主", "从一键安装到完全静默的更新。", Sym.InstallMobile, Color(0xFFF97316), MaterialShapes.Gem, listOf(
        Feature(Sym.Android, "选择安装器", "使用系统安装器、每次手动挑选，或始终把 APK 交给指定的安装器应用。", settings("installer")),
        Feature(Sym.Speed, "跳过 24 小时等待", "内置的无线调试能以 shell 用户身份即时安装应用，只需按引导设置一次。", settings("installer")),
        Feature(Sym.Bolt, "用 Shizuku 静默安装", "Shizuku 运行时，安装和更新无需每次确认即可完成。", settings("installer")),
        Feature(Sym.Terminal, "Root 模式", "通过 root 实现静默安装、更新以及全部 Guardian 工具，无需 Shizuku 或无线调试。", settings("installer")),
        Feature(Sym.Sync, "接管已装应用", "拥有 root 后，Orion 可接管你从别处安装的目录内应用的更新，其数据保持原样。", settings("installer")),
        Feature(Sym.Lock, "更新锁定", "在 Android 14 及以上并拥有 root 时，其他商店在更新 Orion 安装的应用前必须先征得你的同意。", settings("installer")),
        Feature(Sym.DeleteSweep, "清理所有应用缓存", "拥有 root 后，一键释放所有应用的缓存空间。", settings("installer")),
        Feature(Sym.Refresh, "强制重新扫描", "发现已装应用列表不对劲时，随时重新加载。", settings("installer")),
    )),
    Chapter("xtra", "Guardian 与 Sentinel", "内置的安全与管控工具。", Sym.Shield, Color(0xFFEF4444), MaterialShapes.Cookie6Sided, listOf(
        Feature(Sym.Shield, "Guardian：权限", "找出持有位置、麦克风、通讯录、存储等高危权限的应用，并收回这些权限。", settings("installer")),
        Feature(Sym.Unarchive, "Guardian：提取 APK", "从任意已安装应用中提取 APK 并保存。", settings("installer")),
        Feature(Sym.DeleteForever, "Guardian：精简系统", "可逆地移除预装系统应用，并有社区清单标注哪些可以安全移除。", settings("installer")),
        Feature(Sym.MonitorHeart, "Sentinel：快速与深度扫描", "对照威胁特征库检查已安装的应用，并扫描存储中可能受感染的文件。", settings("installer")),
        Feature(Sym.Lan, "Sentinel：网络哨兵", "实时审计你的网络连接：VPN、DNS、代理、强制门户和 Wi-Fi 加密。", settings("installer")),
        Feature(Sym.DeveloperMode, "ADB 看门狗", "一眼看清手机上的开发者桥接是否处于开启状态。"),
    )),
    Chapter("look", "外观与手感", "让 Orion 的外观与动效随心而动。", Sym.Palette, Color(0xFFA855F7), MaterialShapes.Flower, listOf(
        Feature(Sym.LightMode, "浅色、黄昏、深色与 OLED", "四套主题。搜索胶囊中的按钮可在任何位置循环切换，并伴有圆形揭示动画。", settings("visuals")),
        Feature(Sym.Palette, "配色方案", "从 Orion 到 Cocoa 共十四套配色，每套都为所有主题精心调校。", settings("visuals")),
        Feature(Sym.FormatColorFill, "打造专属配色", "在配色行点按“制作”，选择主色以及可选的次色和第三色，再挑选柔和、均衡或鲜艳风格，即可在每套主题中预览。长按你的配色可随时编辑。", settings("visuals")),
        Feature(Sym.AutoAwesome, "Material You", "在 Android 12 及以上系统中，从你的壁纸提取配色。", settings("visuals")),
        Feature(Sym.FontDownload, "字体", "内置十一种字体，也可导入自己的 TTF、OTF 或 WOFF2 文件。", settings("visuals")),
        Feature(Sym.Compress, "紧凑模式", "更小的卡片、更纤细的行和额外一列，让每一屏都容纳更多内容。", settings("visuals")),
        Feature(Sym.BlurOn, "玻璃底栏", "悬浮的半透明导航底栏，向下滚动时会自动滑走。", settings("visuals")),
        Feature(Sym.OpenInFull, "底栏大小", "在紧凑与超大之间缩放底栏，可实时预览。默认为紧凑。", settings("visuals")),
        Feature(Sym.TouchApp, "触感反馈", "点按、切换和成功操作时伴有轻微振动。", settings("visuals")),
        Feature(Sym.Speed, "流畅动效", "优先使用屏幕支持的最高刷新率。", settings("visuals")),
        Feature(Sym.MotionPhotosOff, "禁用动画", "所有切换瞬间完成。", settings("visuals")),
        Feature(Sym.ViewAgenda, "隐藏标签页", "可隐藏应用、PC、电视或我的应用标签页。开发者标签页始终保留。", settings("interface")),
        Feature(Sym.Tv, "Android TV（测试版）", "Orion 可运行在 Android TV 上：启动即进入电视目录，直接在电视上安装电视应用，遥控器移动时每张卡片都会亮起。点按 TV Beta 胶囊即可反馈问题。"),
        Feature(Sym.DesktopWindows, "平板与横屏", "Orion 在平板和折叠屏上可自由旋转，提供侧边导航栏和居中的易读栏宽。"),
    )),
    Chapter("settings", "设置与备份", "按你的方式整理的设置，以及永不丢失的进度。", Sym.Settings, Color(0xFFEC4899), MaterialShapes.Pentagon, listOf(
        Feature(Sym.ToggleOn, "快捷开关", "把最常切换的开关固定到设置顶部，并拖动成你自己的顺序。", { openSettings() }),
        Feature(Sym.DragHandle, "整理设置中心", "长按设置卡片并拖动到新位置。点按重置可恢复原始顺序。", { openSettings() }),
        Feature(Sym.Backup, "身份备份", "把你的个人资料、XP、徽章、收藏和设置保存为带签名的 .osf 文件，可在任意手机上恢复。", settings("identity")),
        Feature(Sym.Cloud, "自动云备份", "连接 Google Drive、OneDrive、Proton Drive 或任意存储应用，Orion 会按每天、每周或每月为你保持备份最新。", settings("identity")),
        Feature(Sym.CloudOff, "天生无服务器", "没有 Orion 账号，也没有 Orion 服务器。你的数据只留在手机和你自己选择的云端。"),
        Feature(Sym.VerifiedUser, "完整性校验", "包含不可能进度的备份会被拒绝，确保每个等级和徽章都货真价实。"),
    )),
    Chapter("profile", "个人资料、XP 与徽章", "每个应用、每次广告和每局游戏都助你晋升。", Sym.EmojiEvents, Color(0xFFEAB308), MaterialShapes.Cookie12Sided, listOf(
        Feature(Sym.Badge, "飞行员卡片", "一张会随手机和指尖倾斜的全息 ID 卡。点按即可翻面。", { openProfileStats() }),
        Feature(Sym.Draw, "签名", "用六种墨水在飞行员卡片背面签名，每一笔都支持撤销与重做。"),
        Feature(Sym.Person, "别名与头像", "挑选一个外星风别名和头像。点按卡片上的头像即可随时更换。", { openProfileStats() }),
        Feature(Sym.Bolt, "XP", "资料库中每个应用可获 10 XP，提交每个应用可获 100 XP，完成每个 Fuel the Code 任务可获 40 XP，另有小游戏 XP。"),
        Feature(Sym.TrendingUp, "等级与军衔", "每升一级所需的 XP 都比上一级略多。从学员起步，10 级成为青铜飞行员，30 级白银，50 级黄金。"),
        Feature(Sym.Diamond, "金属卡面", "青铜、白银和黄金飞行员可获得对应金属质感的卡面，正面印有军衔。"),
        Feature(Sym.MilitaryTech, "徽章档案", "收录所有可获得的徽章、获取方式以及你的完成进度。可在个人资料和开发者标签页展示其中三枚。", { profileClick("badges", 0) }),
        Feature(Sym.QueryStats, "统计", "收藏、下载、XP 和军衔数据，每次打开个人资料都会逐项计数呈现。", { openProfileStats() }),
    )),
    Chapter("play", "游戏与竞技", "任务、小游戏和全球名人堂。", Sym.SportsEsports, Color(0xFF14B8A6), MaterialShapes.SoftBurst, listOf(
        Feature(Sym.Swords, "Fuel the Code", "一条横跨两章的支线任务。每看完一条完整广告都是对 Orion 的支持，可获得 XP，并推动你挑战下一个 Boss 及其徽章。", { open(Overlay.AD_DONATION) }),
        Feature(Sym.Castle, "Boss 与徽章", "击败代码魔像、虫群女王、合并冲突九头蛇等 Boss，每个都会掉落专属徽章。"),
        Feature(Sym.Egg, "Orion Run", "一款在商店维护期间陪伴你的恐龙跑酷小游戏，每天最多可获 300 XP 和三枚 Dino 徽章。"),
        Feature(Sym.EmojiEvents, "名人堂", "点按开发者标签页上的开发者硬币，再次点按即可打开全球排行榜。贡献满五次即可认领你自己的名次。", { navTo(Tab.ABOUT) }),
        Feature(Sym.Gavel, "公平竞技", "任务和小游戏设有每日上限，每次排行榜认领都会经过完整性校验，让攀登之路堂堂正正。"),
    )),
    Chapter("dev", "开发者标签页与更多", "帮助、支持，以及一些有意隐藏的小惊喜。", Sym.Code, Color(0xFF64748B), MaterialShapes.Pill, listOf(
        Feature(Sym.MenuBook, "认识 Orion", "就是本页。所有功能汇集一处，入口在开发者标签页顶部的书本按钮。"),
        Feature(Sym.HelpOutline, "常见问题", "解答安全性、ReVanced 构建、安装失败、跟踪器等各类疑问。", { open(Overlay.FAQ) }),
        Feature(Sym.NewReleases, "更新内容", "更新说明直接取自 Orion 的 GitHub releases，一键即可查看完整内容。", { open(Overlay.RELEASE_NOTES) }),
        Feature(Sym.Forum, "社区与联系", "GitHub、X、Discord 和 Orion 网站，尽在开发者标签页。", { navTo(Tab.ABOUT) }),
        Feature(Sym.Coffee, "支持 Orion", "通过 Ko-fi、UPI 或 PayPal 请开发者喝杯咖啡。", { navTo(Tab.ABOUT) }),
        Feature(Sym.Public, "镜像与离线", "目录会从 GitHub、GitLab、Codeberg 或 JSDelivr 加载——谁先响应就用谁，并保留缓存供离线使用。"),
        Feature(Sym.Construction, "维护与商店更新", "商店维护时 Orion 会及时告知，还会检查、扫描并安装自身的更新。"),
        Feature(Sym.Terminal, "开发者选项", "连点 Orion 标志七次，解锁用于测试目录、配置和镜像的工具。"),
        Feature(Sym.QuestionMark, "秘密", "应用各处藏着一些彩蛋和秘密徽章。常见问题里有一条谜题，帮你迈出第一步。"),
    )),
)

/** Orion hides the status bar, so the plain insets read zero and the floating chips slid under the camera cutout. Its slot (and any cutout) is kept clear instead. */
private val GuideTop @Composable get() = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))

/** Know Orion: the whole app as an expressive, searchable field guide. */
@Composable
fun KnowOrionScreen(vm: MainViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val cs = MaterialTheme.colorScheme; val list = rememberLazyListState(); val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = remember(q) {
        if (q.isEmpty()) CHAPTERS else CHAPTERS.map { c -> Chapter(c.id, c.title, c.blurb, c.icon, c.color, c.shape, c.features.filter { q in it.title.lowercase() || q in it.body.lowercase() }) }
            .filter { it.features.isNotEmpty() }
    }
    val total = remember { CHAPTERS.sumOf { it.features.size } }
    fun go(f: Feature) { vm.haptic(H.SELECTION); onClose(); scope.launch { delay(120); f.go?.invoke(vm) } }
    // Hero, search and chips come first; each chapter is a header plus its rows
    val starts = remember(shown) { shown.runningFold(3) { at, c -> at + c.features.size + 1 }.dropLast(1) }
    val current by remember(starts) { derivedStateOf { if (list.firstVisibleItemIndex < 3) -1 else starts.indexOfLast { it <= list.firstVisibleItemIndex + 1 } } }
    val chips = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Rows land just below the floating chip bar instead of under it
    val pin = GuideTop.getTop(density) + with(density) { 84.dp.roundToPx() }
    fun jump(ci: Int) {
        // Far chapters snap straight there: an animated fling across dozens of rows composes every one of them on the way
        val at = starts[ci]
        scope.launch { if (kotlin.math.abs(at - list.firstVisibleItemIndex) <= 8) list.animateScrollToItem(at, -pin) else list.scrollToItem(at, -pin) }
    }
    val floating by remember { derivedStateOf { list.firstVisibleItemIndex >= 3 } }
    LaunchedEffect(current) { if (current >= 0) { if (floating) chips.animateScrollToItem(current) else chips.requestScrollToItem(current) } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().background(cs.surface), list, contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
        item("hero") { GuideHero(total) }
        item("search") { GuideSearch(query) { query = it } }
        item("chips") { ChapterChips(shown, -1, rememberLazyListState(), ::jump) }
        if (shown.isEmpty()) item("empty") { EmptyState(Sym.SearchOff, "没有与“$query”匹配的内容", "试试更短的词，比如备份或主题。") }
        shown.forEach { c ->
            item("h-${c.id}", contentType = "header") { ChapterHeader(c, CHAPTERS.indexOfFirst { it.id == c.id }) }
            itemsIndexed(c.features, key = { _, f -> "${c.id}-${f.title}" }, contentType = { _, _ -> "feature" }) { i, f -> FeatureRow(c, f, i, c.features.size) { go(f) } }
        }
        item("end") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(painterResource(com.orion.store.R.drawable.ic_splash_logo), null, Modifier.size(40.dp), tint = cs.onPrimaryContainer)
                }
                Text("Orion 的全部内容到此为止", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleLargeEmphasized)
                Text("${CHAPTERS.size} 个章节、$total 项功能，更多内容正在路上。", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                Surface(Modifier.padding(top = 14.dp), shape = Pill, color = cs.surfaceContainerHigh) {
                    Text("v$STORE_VERSION", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    // Past the top, chapters float in a rounded bar under the status bar, marking where you are
    androidx.compose.animation.AnimatedVisibility(floating, Modifier.align(Alignment.TopCenter).windowInsetsPadding(GuideTop).padding(horizontal = 12.dp, vertical = 8.dp),
        enter = androidx.compose.animation.slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut()) {
        Surface(shape = RoundedCornerShape(32.dp), color = cs.surfaceContainerHigh, shadowElevation = 6.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ scope.launch { list.animateScrollToItem(0) } }, Modifier.padding(start = 4.dp)) { Icon(Sym.KeyboardArrowUp, "回到顶部") }
                ChapterChips(shown, current, chips, ::jump, start = 0.dp)
            }
        }
    }
    }
}

@Composable
private fun ChapterChips(shown: List<Chapter>, current: Int, state: androidx.compose.foundation.lazy.LazyListState, onJump: (Int) -> Unit, start: Dp = 16.dp) =
    LazyRow(Modifier.fillMaxWidth(), state, contentPadding = PaddingValues(start = start, end = 16.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(shown, key = { _, c -> c.id }) { ci, c -> Choice(ci == current, c.title, c.icon) { onJump(ci) } }
    }

private val HERO_SHAPES = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.SoftBurst)

@Composable
private fun GuideHero(total: Int) {
    val cs = MaterialTheme.colorScheme; val still = LocalS.current.disableAnimations
    var shape by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) { if (!still) while (true) { delay(2600); shape++ } }
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surface)))) {
        Turning(MaterialShapes.Cookie12Sided, cs.primary.copy(.08f), 280.dp, Modifier.align(Alignment.TopEnd).offset(90.dp, (-40).dp), 50_000)
        Column(Modifier.windowInsetsPadding(GuideTop).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp)) {
            MorphBlob(HERO_SHAPES, shape, 104.dp, cs.primary, Modifier.padding(top = 12.dp)) {
                Icon(painterResource(com.orion.store.R.drawable.ic_splash_logo), null, Modifier.size(72.dp), tint = cs.onPrimary)
            }
            Text("功能指南", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.labelLargeEmphasized, color = cs.primary)
            Text("认识 Orion", style = MaterialTheme.typography.displayMediumEmphasized)
            Text("Orion 的全部 $total 项功能尽在于此——从备份、紧凑模式到徽章、名人堂和恐龙跑酷。点按带箭头的功能即可直达。",
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuideSearch(query: String, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(56.dp).clip(Pill).background(cs.surfaceContainerHigh).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Sym.Search, null, tint = cs.onSurfaceVariant)
        BasicTextField(query, onChange, Modifier.weight(1f).padding(horizontal = 12.dp), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
            decorationBox = { field -> Box { if (query.isEmpty()) Text("搜索功能", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant); field() } })
        if (query.isNotEmpty()) IconButton({ onChange("") }) { Icon(Sym.Close, "清除搜索") }
    }
}

@Composable
private fun ChapterHeader(c: Chapter, index: Int) {
    val (bg, fg) = tonal(c.color)
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 10.dp).fillMaxWidth().background(bg, RoundedCornerShape(32.dp)).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Drawn shapes, not clipped layers or endless spins: cheap to bring on screen when jumping between chapters
        Box(Modifier.size(64.dp).background(fg.copy(.16f), c.shape.toShape()), contentAlignment = Alignment.Center) {
            Icon(c.icon, null, Modifier.size(30.dp), tint = fg)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("第 ${index + 1} 章", style = MaterialTheme.typography.labelMedium, color = fg.copy(.75f))
            Text(c.title, style = MaterialTheme.typography.headlineSmallEmphasized, color = fg)
            Text(c.blurb, style = MaterialTheme.typography.bodyMedium, color = fg.copy(.85f))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.background(fg, Pill).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("${c.features.size}", style = MaterialTheme.typography.labelLargeEmphasized, color = bg) }
    }
}

/** One feature as a plain segmented row: a much lighter build than the interactive list item, so long jumps stay smooth. */
@Composable
private fun FeatureRow(c: Chapter, f: Feature, i: Int, n: Int, onGo: () -> Unit) {
    val (bg, fg) = tonal(c.color); val cs = MaterialTheme.colorScheme
    val shape = colShape(i, n)
    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 2.dp).fillMaxWidth().clip(shape).background(cs.surfaceContainer)
        .then(if (f.go != null) Modifier.clickable(onClick = onGo) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(bg, MaterialShapes.Cookie6Sided.toShape()), contentAlignment = Alignment.Center) { Icon(f.icon, null, Modifier.size(22.dp), tint = fg) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(f.title, style = MaterialTheme.typography.titleMedium)
            Text(f.body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (f.go != null) { Spacer(Modifier.width(12.dp)); Icon(Sym.ArrowForward, "打开${f.title}", tint = fg) }
    }
}

/** A MaterialShape turning slowly in place; holds still when animations are off. */
@Composable
private fun Turning(shape: RoundedPolygon, color: Color, size: Dp, modifier: Modifier = Modifier, periodMs: Int = 20_000) {
    val still = LocalS.current.disableAnimations
    val r by rememberInfiniteTransition(label = "turn").animateFloat(0f, if (still) 0f else 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "r")
    Box(modifier.size(size).graphicsLayer { rotationZ = r }.clip(shape.toShape()).background(color))
}
