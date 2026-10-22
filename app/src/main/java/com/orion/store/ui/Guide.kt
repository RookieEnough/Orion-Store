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
    Chapter("store", "商店", "三个目录、两种布局，以及大量找到心仪应用的方式。", Sym.Apps, Color(0xFF6366F1), MaterialShapes.Cookie9Sided, listOf(
        Feature(Sym.Android, "应用、电脑与电视", "安卓应用、电脑软件和电视应用各自独立成目录，都有专属的标签页、搜索、排序和筛选。", { navTo(Tab.ANDROID) }),
        Feature(Sym.ViewQuilt, "现代与经典布局", "现代布局是带主视觉、横道和货架的店面；经典布局是干净的分组列表。可在 设置 › 界面 随时切换。", settings("interface")),
        Feature(Sym.Star, "精选主视觉", "编辑精选的应用在现代化首页顶部缓缓滑过，穿着自己图标的颜色，截图呈扇形展开。"),
        Feature(Sym.AutoAwesome, "为你推荐", "可左右滑动的每日精选轮播，外加一个编号的精选应用聚光灯榜单。"),
        Feature(Sym.NewReleases, "最新与更新", "最新上架和刚焕新的应用占据专属横道，一键即可查看完整列表。"),
        Feature(Sym.Category, "分类货架", "形态各异的分类色块与横道：媒体、社交、隐私、工具等，每个都带应用数量。"),
        Feature(Sym.Layers, "应用合集", "精心策划的应用文件夹。打开即可看到全部应用，可增删应用并逐个安装。"),
        Feature(Sym.LibraryAdd, "创建自己的合集", "三步打造合集：挑选应用，起名并配色加图标，然后保存在本地或提交给所有人。", { open(Overlay.CUSTOM_BUNDLE) }),
        Feature(Sym.AutoAwesome, "Orion AI", "点一下星星，用一句话描述需求，比如“找一个免费的音乐应用”。可用你自己的 Gemini、ChatGPT 或 Claude 密钥，也可用手机本地模型。星星可以随意拖动。", { open(Overlay.AI) }),
        Feature(Sym.Search, "搜索", "顶部的搜索胶囊按名称、开发者、分类或描述查找应用，还能容忍重音符号和空格拼写差异。"),
        Feature(Sym.SwapVert, "排序与筛选", "按最新、最早、名称或大小排序，还能用筛选按钮把列表缩小到单一分类。"),
        Feature(Sym.Favorite, "收藏", "长按任意卡片、左滑经典列表行，或在应用页点爱心。爱心筛选器只显示你的收藏。"),
        Feature(Sym.Description, "关于此应用预览", "经典列表行显示描述的前几行，点“展开更多”原地阅读全文。可在 设置 › 界面 关闭。", settings("interface")),
        Feature(Sym.Sell, "分类图标", "经典列表行上每个分类都有自己的图标，长列表也能一眼扫清。"),
        Feature(Sym.SystemUpdate, "更新卡组", "有更新待处理时，应用首页会出现一条细条展示待更新应用，附一键“全部更新”。"),
        Feature(Sym.Campaign, "公告", "Orion 团队的消息从搜索胶囊旁的喇叭里送达。未读之前它会一直跳动。"),
        Feature(Sym.AutoFixHigh, "下拉刷新角色", "下拉刷新目录时，会跑出像素猫、狗、猫头鹰、幽灵等小伙伴，或一个灵动的形状。可在 Orion 特性 里选择。", settings("installer")),
        Feature(Sym.KeyboardArrowUp, "回到顶部", "滚动很远后会出现一个悬浮箭头，轻点一下就回到顶部。"),
        Feature(Sym.AddCircleOutline, "提交应用", "推荐 GitHub、GitLab 或 Codeberg 上的应用。Orion 会自动识别名称、包名和描述，支持 Obtainium 导出，并生成一份现成的 GitHub issue。", { open(Overlay.SUBMISSION) }),
    )),
    Chapter("page", "应用详情页", "关于一个应用的一切，以及获取它的所有方式。", Sym.Info, Color(0xFF0EA5E9), MaterialShapes.Sunny, listOf(
        Feature(Sym.Download, "一键安装", "在应用页下载、安装、更新或打开应用。下载时进度条会环绕图标。"),
        Feature(Sym.Image, "截图", "以真实比例浏览截图，点按即可全屏查看。"),
        Feature(Sym.History, "版本与渠道", "按应用选择稳定版、测试版、内测版或夜间版，也可以从版本列表中挑选确切的旧版本。"),
        Feature(Sym.Memory, "适合你手机的构建", "Orion 会挑选与手机处理器匹配的 APK，也允许你自行选择其他架构。"),
        Feature(Sym.Info, "应用信息", "开发者、大小、包名、来源和补丁信息，折叠收纳，想看时再展开。"),
        Feature(Sym.Policy, "VirusTotal 扫描", "用你自己的免费密钥，把下载、已安装的 APK 或发布文件送到 70+ 杀毒引擎检查。"),
        Feature(Sym.Code, "来源与安全性", "一键跳转到源代码、官方发布页或开发者网站。"),
        Feature(Sym.NotificationsOff, "忽略更新", "把某个更新隐藏一周、隐藏到下个版本，或永久隐藏。被忽略的更新会留在“我的应用”里，带“取消忽略”按钮。"),
        Feature(Sym.SaveAlt, "导出与重新下载", "把 APK 存到下载文件夹、重新下载，或删除已保存的副本腾出空间。"),
        Feature(Sym.Share, "分享链接与徽章", "复制能直接在 Orion 打开该应用的深链、网页链接，或 README 用的 Markdown/HTML 徽章。"),
        Feature(Sym.EditNote, "申请更正", "发现图标错误、链接失效或描述过时？针对那个字段提交更正。"),
        Feature(Sym.Report, "反馈问题", "下载挂了？在应用页直接反馈，相关信息会自动填好。"),
        Feature(Sym.Extension, "MicroG 助手", "需要 MicroG 的应用会明确提示，并附安装链接和了解更多指南。"),
        Feature(Sym.Link, "深链", "orionstore:// 链接和安卓系统的“在商店中查看应用详情”都会在 Orion 中打开正确页面。"),
    )),
    Chapter("library", "我的应用与下载", "你的应用库、下载队列，以及打理自己的更新。", Sym.GridView, Color(0xFF22C55E), MaterialShapes.Clover4Leaf, listOf(
        Feature(Sym.Inventory2, "应用库", "你通过 Orion 下载或安装过的每个应用，可按全部、已安装、已下载和更新筛选，还有专属搜索。", { navTo(Tab.MYAPPS) }),
        Feature(Sym.Downloading, "下载队列", "进行中的下载、就绪的安装和待处理的更新齐聚一处；在可以静默安装时还有“全部更新”。", settings("queue")),
        Feature(Sym.Notifications, "后台下载", "离开 Orion 下载仍会继续，通知栏实时显示进度，完成后轻点即可安装。"),
        Feature(Sym.Autorenew, "自动更新", "Orion 每隔几小时检查一次更新，即使应用已关闭或设备重启过，并替你下载。", settings("network")),
        Feature(Sym.SystemUpdate, "自动安装", "让 Orion 自动安装它下载的更新。未能完成的项目会在你下次打开应用时报告。", settings("network")),
        Feature(Sym.Verified, "仅稳定版", "只要存在稳定版，就跳过测试版、内测版和夜间版构建。", settings("network")),
        Feature(Sym.Wifi, "仅限 Wi-Fi", "不让下载消耗移动数据。", settings("network")),
        Feature(Sym.CleaningServices, "清理工", "残留 APK 会在安装一周后清理；开启“自动清理”则立即清理。文件堆积时还会出现一键清理的胶囊按钮。", settings("storage")),
    )),
    Chapter("install", "按你的方式安装", "从一键安装到完全静默的更新。", Sym.InstallMobile, Color(0xFFF97316), MaterialShapes.Gem, listOf(
        Feature(Sym.Android, "选择你的安装器", "使用系统安装器、每次询问，或始终把 APK 交给指定的安装器应用。", settings("installer")),
        Feature(Sym.Speed, "跳过 24 小时等待", "内置无线调试以 shell 用户身份极速安装应用，跟随引导设置一次即可。", settings("installer")),
        Feature(Sym.Bolt, "用 Shizuku 静默安装", "只要 Shizuku 在运行，安装和更新都无需每次确认。", settings("installer")),
        Feature(Sym.Terminal, "Root 模式", "通过 root 进行静默安装、更新和所有守护者工具，无需 Shizuku 或无线调试。", settings("installer")),
        Feature(Sym.Sync, "接管已安装应用", "在 root 下，Orion 接管你从别处安装的目录应用的更新。应用数据原封不动。", settings("installer")),
        Feature(Sym.Lock, "更新锁", "在 Android 14 及以上并拥有 root 时，其他商店更新 Orion 安装的应用前必须先征得同意。", settings("installer")),
        Feature(Sym.DeleteSweep, "清理所有应用缓存", "在 root 下，一键释放所有应用的缓存空间。", settings("installer")),
        Feature(Sym.Refresh, "强制重新扫描", "当应用列表看起来过期时，随时重新加载。", settings("installer")),
    )),
    Chapter("xtra", "守护者与哨兵", "内建的安全与掌控工具。", Sym.Shield, Color(0xFFEF4444), MaterialShapes.Cookie6Sided, listOf(
        Feature(Sym.Shield, "守护者：权限", "找出持有定位、麦克风、通讯录、存储等高危权限的应用，并撤销这些权限。", settings("installer")),
        Feature(Sym.Unarchive, "守护者：提取 APK", "从任意已安装应用中提取 APK 并保存。", settings("installer")),
        Feature(Sym.DeleteForever, "守护者：精简系统", "可逆地移除预装系统应用，社区清单会标注哪些可以安全移除。", settings("installer")),
        Feature(Sym.MonitorHeart, "哨兵：快速与深度扫描", "用威胁特征库检查已安装应用，并全盘扫查被感染的文件。", settings("installer")),
        Feature(Sym.Lan, "哨兵：网络哨兵", "实时审查你的网络连接：VPN、DNS、代理、强制门户与 Wi-Fi 加密。", settings("installer")),
        Feature(Sym.DeveloperMode, "ADB 看门狗", "一眼看清手机上的调试桥是否处于开启状态。"),
    )),
    Chapter("look", "外观与手感", "让 Orion 的外观与动效合你心意。", Sym.Palette, Color(0xFFA855F7), MaterialShapes.Flower, listOf(
        Feature(Sym.LightMode, "浅色、黄昏、深色与 OLED", "四套主题。搜索胶囊里的按钮可在任何页面循环切换，并伴随圆形揭示动效。", settings("visuals")),
        Feature(Sym.Palette, "配色方案", "从 Orion 到 Cocoa 共十四套配色，每一套都为全部主题调校过。", settings("visuals")),
        Feature(Sym.FormatColorFill, "制作专属调色板", "在调色板行点“制作”，选一个主色加可选的辅色和第三色，选择柔和、均衡或鲜艳，并在所有主题中预览。长按自建调色板即可编辑。", settings("visuals")),
        Feature(Sym.AutoAwesome, "Material You", "在 Android 12 及以上，从壁纸中提取配色。", settings("visuals")),
        Feature(Sym.FontDownload, "字体", "内置 11 款字体，也可以导入你自己的 TTF、OTF 或 WOFF2 文件。", settings("visuals")),
        Feature(Sym.Compress, "紧凑模式", "更小的卡片、更窄的行和额外一列，每屏都能装下更多。", settings("visuals")),
        Feature(Sym.BlurOn, "毛玻璃底栏", "悬浮、微透明的导航栏，向下滚动时自动滑走。", settings("visuals")),
        Feature(Sym.OpenInFull, "底栏大小", "在紧凑与大号之间缩放底栏，带实时预览。默认从紧凑开始。", settings("visuals")),
        Feature(Sym.TouchApp, "触感反馈", "点按、切换和成功操作都有触觉反馈。", settings("visuals")),
        Feature(Sym.Speed, "流畅动效", "优先使用屏幕支持的最高刷新率。", settings("visuals")),
        Feature(Sym.MotionPhotosOff, "关闭动画", "所有切换瞬间完成。", settings("visuals")),
        Feature(Sym.ViewAgenda, "隐藏标签页", "可以隐藏应用、电脑、电视或我的应用标签页。开发者标签页始终保留。", settings("interface")),
        Feature(Sym.Tv, "安卓电视（Beta）", "Orion 可以在 Android TV 上运行：打开即电视目录，直接在电视上安装电视应用，遥控器移动时卡片逐个点亮。点电视 Beta 胶囊即可反馈问题。"),
        Feature(Sym.DesktopWindows, "平板与横屏", "在平板和折叠屏上自由旋转，配侧边栏和居中易读的版面。"),
    )),
    Chapter("settings", "设置与备份", "按你的方式整理设置，进度永不丢失。", Sym.Settings, Color(0xFFEC4899), MaterialShapes.Pentagon, listOf(
        Feature(Sym.ToggleOn, "快捷开关", "把最常用的开关固定到设置页顶部，并拖成你自己的顺序。", { openSettings() }),
        Feature(Sym.DragHandle, "整理设置中心", "长按设置卡片拖到新位置。“重置”可恢复原始顺序。", { openSettings() }),
        Feature(Sym.Backup, "资料备份", "把个人资料、经验值、徽章、收藏和设置保存为签名的 .osf 文件，在任意手机上恢复。", settings("identity")),
        Feature(Sym.Cloud, "自动云备份", "连接 Google Drive、OneDrive、Proton Drive 或任意存储应用，Orion 按每天、每周或每月保持备份新鲜。", settings("identity")),
        Feature(Sym.CloudOff, "无服务器设计", "没有 Orion 账号，也没有 Orion 服务器。数据只留在你的手机和你选择的网盘里。"),
        Feature(Sym.VerifiedUser, "完整性校验", "进度不合理的备份会被拒绝，确保每个等级和徽章都名副其实。"),
    )),
    Chapter("profile", "资料、经验与徽章", "每个应用、每次看广告和每场跑酷都助你晋级。", Sym.EmojiEvents, Color(0xFFEAB308), MaterialShapes.Cookie12Sided, listOf(
        Feature(Sym.Badge, "飞行员卡", "一张全息证件，随手机倾斜、随指尖而动。点按即可翻面。", { openProfileStats() }),
        Feature(Sym.Draw, "签名", "用六种墨水在飞行员卡背面签名，每一笔都可撤销和重做。"),
        Feature(Sym.Person, "代号与头像", "选择一个外星代号和头像。点卡片上的头像即可随时更换。", { openProfileStats() }),
        Feature(Sym.Bolt, "经验值", "应用库里每个应用 10 XP，提交应用每个 100 XP，《为代码加油》任务每个 40 XP，还有小游戏经验。"),
        Feature(Sym.TrendingUp, "等级与军衔", "每一级所需经验都比上一级略多。从见习学员起步：10 级青铜、30 级白银、50 级黄金。"),
        Feature(Sym.Diamond, "金属卡面", "青铜、白银、黄金飞行员会获得对应金属质感的卡面，正面显示军衔。"),
        Feature(Sym.MilitaryTech, "徽章档案", "所有可获得的徽章、获取方式与你的进度。可在资料页和开发者标签页展示其中三枚。", { profileClick("badges", 0) }),
        Feature(Sym.QueryStats, "统计", "收藏、下载、经验值与军衔，每次打开资料页都在增长。", { openProfileStats() }),
    )),
    Chapter("play", "游戏与竞技", "任务、小游戏和全球名人堂。", Sym.SportsEsports, Color(0xFF14B8A6), MaterialShapes.SoftBurst, listOf(
        Feature(Sym.Swords, "为代码加油", "贯穿两章的自愿任务。每看完一条广告都是对 Orion 的支持，赚取经验值，并推进到下一个 Boss 和它的徽章。", { open(Overlay.AD_DONATION) }),
        Feature(Sym.Castle, "Boss 与徽章", "击败代码魔像、虫群女王、合并冲突九头蛇等，每个 Boss 都会掉落专属徽章。"),
        Feature(Sym.Egg, "Orion 跑酷", "商店维护时陪你解闷的恐龙跑酷，每天最多 300 XP，还有三枚恐龙徽章。"),
        Feature(Sym.EmojiEvents, "名人堂", "在开发者标签页点开发者金币，再点一次即可打开全球排行榜。攒满五次贡献就能认领你的排名。", { navTo(Tab.ABOUT) }),
        Feature(Sym.Gavel, "公平竞技", "任务和小游戏有每日上限，排行榜认领都有完整性校验，晋级之路堂堂正正。"),
    )),
    Chapter("dev", "开发者标签页与更多", "帮助、支持，以及一些故意藏起来的东西。", Sym.Code, Color(0xFF64748B), MaterialShapes.Pill, listOf(
        Feature(Sym.MenuBook, "了解 Orion", "就是本页。所有功能汇聚一处，入口在开发者标签页顶部的书本按钮。"),
        Feature(Sym.HelpOutline, "常见问题", "关于安全性、ReVanced 构建、安装失败、跟踪器等问题的解答。", { open(Overlay.FAQ) }),
        Feature(Sym.NewReleases, "更新日志", "来自 Orion GitHub 发布页的更新说明，一键查看完整日志。", { open(Overlay.RELEASE_NOTES) }),
        Feature(Sym.Forum, "联系与社区", "GitHub、X、Discord 和 Orion 官网，就在开发者标签页。", { navTo(Tab.ABOUT) }),
        Feature(Sym.Coffee, "支持 Orion", "通过 Ko-fi、UPI 或 PayPal 请开发者喝杯咖啡。", { navTo(Tab.ABOUT) }),
        Feature(Sym.Public, "镜像与离线", "目录从 GitHub、GitLab、Codeberg 或 JSDelivr 加载（谁快用谁），并缓存供离线使用。"),
        Feature(Sym.Construction, "维护与商店更新", "商店维护时 Orion 会告诉你；它还会自行检查、扫描并安装自己的更新。"),
        Feature(Sym.Terminal, "开发者选项", "连点 Orion Logo 七次，解锁用于测试目录、配置和镜像的工具。"),
        Feature(Sym.QuestionMark, "小秘密", "应用里藏着一些彩蛋和秘密徽章。FAQ 里有一条谜语可以带你入门。"),
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
        if (shown.isEmpty()) item("empty") { EmptyState(Sym.SearchOff, "没有与“$query”匹配的内容", "试试更短的词，比如备份、主题。") }
        shown.forEach { c ->
            item("h-${c.id}", contentType = "header") { ChapterHeader(c, CHAPTERS.indexOfFirst { it.id == c.id }) }
            itemsIndexed(c.features, key = { _, f -> "${c.id}-${f.title}" }, contentType = { _, _ -> "feature" }) { i, f -> FeatureRow(c, f, i, c.features.size) { go(f) } }
        }
        item("end") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(painterResource(com.orion.store.R.drawable.ic_splash_logo), null, Modifier.size(40.dp), tint = cs.onPrimaryContainer)
                }
                Text("Orion 的全部就在这里", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleLargeEmphasized)
                Text("共 ${CHAPTERS.size} 个章节、$total 项功能，还会持续增加。", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
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
                IconButton({ scope.launch { list.animateScrollToItem(0) } }, Modifier.padding(start = 4.dp)) { Icon(Sym.KeyboardArrowUp, "Back to top") }
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
            Text("使用指南", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.labelLargeEmphasized, color = cs.primary)
            Text("了解 Orion", style = MaterialTheme.typography.displayMediumEmphasized)
            Text("Orion 的全部 $total 项功能都在这里：从备份、紧凑模式，到徽章、名人堂和恐龙跑酷。点带箭头的功能项可直接跳转。",
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
        if (f.go != null) { Spacer(Modifier.width(12.dp)); Icon(Sym.ArrowForward, "打开 ${f.title}", tint = fg) }
    }
}

/** A MaterialShape turning slowly in place; holds still when animations are off. */
@Composable
private fun Turning(shape: RoundedPolygon, color: Color, size: Dp, modifier: Modifier = Modifier, periodMs: Int = 20_000) {
    val still = LocalS.current.disableAnimations
    val r by rememberInfiniteTransition(label = "turn").animateFloat(0f, if (still) 0f else 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "r")
    Box(modifier.size(size).graphicsLayer { rotationZ = r }.clip(shape.toShape()).background(color))
}
