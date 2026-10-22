package com.orion.store.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true; encodeDefaults = true }

object Platform { const val ANDROID = "Android"; const val PC = "PC"; const val TV = "TV" }

/** What this device runs, read once at process start before any screen or background job asks. */
object Device { @Volatile var isTv = false }

/** Android apps install everywhere; TV apps install natively too when Orion runs on a TV (beta). */
fun AppItem.installsHere() = platform == Platform.ANDROID || (platform == Platform.TV && Device.isTv)

enum class SortOption(val label: String) {
    HOME("首页"), NEWEST("最新添加"), OLDEST("最早添加"), NAME_ASC("名称 (A-Z)"),
    NAME_DESC("名称 (Z-A)"), SIZE_ASC("大小 (从小到大)"), SIZE_DESC("大小 (从大到小)")
}

enum class Tab(val platform: String?) { ANDROID(Platform.ANDROID), PC(Platform.PC), TV(Platform.TV), MYAPPS(null), ABOUT(null), UPDATES(null) }

val STREAMS = listOf("Stable", "Beta", "Alpha", "Nightly")

@Serializable data class AppVariant(val arch: String = "", val url: String = "", val size: Long? = null)

@Serializable data class VersionOption(val type: String = "Stable", val version: String = "", val date: String = "", val variants: List<AppVariant> = emptyList())

@Serializable data class AppItem(
    val id: String = "",
    val name: String = "Unknown App",
    val description: String = "",
    val icon: String = "",
    val version: String = "Latest",
    val latestVersion: String = "Latest",
    val downloadUrl: String = "#",
    val variants: List<AppVariant> = emptyList(),
    val availableVersions: List<VersionOption> = emptyList(),
    val repoUrl: String? = null,
    val githubRepo: String? = null,
    val gitlabRepo: String? = null,
    val codebergRepo: String? = null,
    val gitlabDomain: String? = null,
    val releaseKeyword: String? = null,
    val packageName: String? = null,
    val category: String = "Utility",
    val tags: List<String> = emptyList(),
    val platform: String = Platform.ANDROID,
    val size: String = "",
    val author: String = "Unknown",
    val screenshots: List<String> = emptyList(),
    val officialSite: String? = null,
    val patches: List<String> = emptyList(),
)

@Serializable data class SocialLinks(val github: String = "", val x: String = "", val discord: String = "", val coffee: String = "")
@Serializable data class DonationConfig(val kofiUrl: String? = null, val upiId: String? = null, val paypalUrl: String? = null)
@Serializable data class DevProfile(val name: String = "", val image: String? = null, val bio: String = "")
@Serializable data class FAQItem(val question: String = "", val answer: String = "", val icon: String = "")
@Serializable data class Notice(val id: String = "", val title: String = "", val message: String = "", val show: Boolean = false)
@Serializable data class PillConfig(val id: String? = null, val label: String? = null, val appId: String? = null, val tone: String? = null)
@Serializable data class ModuleBundleConfig(val id: String = "", val title: String = "", val description: String? = null, val appIds: List<String> = emptyList(), val color: String? = null, val badge: String? = null, val icon: String? = null, val monogram: String? = null)
@Serializable data class ModuleConfig(
    val id: String = "", val type: String = "", val title: String = "", val subtitle: String? = null, val platform: String? = null,
    val insertAfterCategory: Double? = null, val animation: String? = null, val appIds: List<String>? = null,
    val pills: List<PillConfig>? = null, val bundles: List<ModuleBundleConfig>? = null,
)

@Serializable data class StoreConfig(
    val appsJsonUrl: String = "",
    val mirrorJsonUrl: String? = null,
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String? = null,
    val announcement: String? = null,
    val notice: Notice? = null,
    val minStoreVersion: String? = null,
    val latestStoreVersion: String? = null,
    val storeDownloadUrl: String? = null,
    val socials: SocialLinks? = null,
    val donation: DonationConfig? = null,
    val devProfile: DevProfile? = null,
    val faqs: List<FAQItem>? = null,
    val supportEmail: String? = null,
    val easterEggUrl: String? = null,
    val leaderboardUrl: String? = null,
    val storefrontModules: List<ModuleConfig>? = null,
)

@Serializable data class LeaderboardEntry(val username: String = "", val xp: Long = 0, val level: Int = 0, val title: String = "", val `class`: String = "Hybrid", val avatar_url: String = "", val rank: Int? = null)

data class BundleItem(val id: String, val title: String, val description: String, val appIds: List<String>, val apps: List<AppItem> = emptyList(), val color: String? = null, val badge: String? = null, val icon: String? = null, val monogram: String? = null)
data class CategoryCard(val id: String, val label: String, val icon: String, val count: Int, val accent: String, val gradient: List<String>)

enum class CollectionType { HERO, SWIMLANE, BUNDLE, RECOMMENDATION_BUNDLES, CATEGORY_CARDS, SORTED_GRID }

data class StoreCollection(
    val id: String, val title: String, val type: CollectionType, val subtitle: String? = null, val filter: String? = null,
    val animation: String? = null, val apps: List<AppItem> = emptyList(), val bundles: List<BundleItem> = emptyList(),
    val categoryCards: List<CategoryCard> = emptyList(), val totalAppCount: Int? = null,
)

object Defaults {
    const val CACHE_VERSION = "v1_6"
    const val NETWORK_TIMEOUT_MS = 8000L
    const val RELEASES_API = "https://api.github.com/repos/RookieEnough/Orion-Store/releases?per_page=8"
    const val MIRROR_JSON = "https://raw.githubusercontent.com/RookieEnough/Orion-Data/data/mirror.json"
    val CONFIG_URLS = listOf(
        "https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/config.json",
        "https://gitlab.com/RookieEnough/Orion-Data/-/raw/main/config.json",
        "https://codeberg.org/RookieEnough/Orion-Data/raw/branch/main/config.json",
        "https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@main/config.json",
    )
    val APPS_URLS = CONFIG_URLS.map { it.replace("config.json", "apps.json") }
    val MIRROR_LABELS = listOf("GitHub", "GitLab", "Codeberg", "JSDelivr")
    val MICROG_DEPENDENT_APPS = setOf("youtube-revanced", "yt-music-revanced", "google-photos-revanced")
    const val MICROG_INFO_URL = "https://github.com/microg/GmsCore/wiki"
    val DEV_SOCIALS = SocialLinks("https://github.com/RookieEnough", "https://x.com/_Rookie_Z", "https://discord.com/invite/CrM6y4ujnq", "https://www.buymeacoffee.com/rookiez")
    val DONATION = DonationConfig("https://ko-fi.com/rookie_z", "rookiez@ptyes", "paypal.me/RookieEnough")
    val DEV_PROFILE = DevProfile("RookieZ", "https://i.pinimg.com/originals/12/79/48/127948a3253396796874286570740594.jpg", "Building the open web, one commit at a time. No ads, no tracking, just code.")
    const val SUPPORT_EMAIL = "orionstoredev@gmail.com"
    const val EASTER_EGG = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    const val SELF_PACKAGE = "com.orion.store"
    const val UNITY_GAME_ID = "5996387"
    val PACKAGE_SUFFIXES = listOf(".preview", ".debug", ".test", ".beta", ".canary", ".dev", ".alpha", ".nightly", ".staging", ".release")
    val FONTS = listOf(
        "spaceGrotesk" to "Space Grotesk", "inter" to "Inter", "poppins" to "Poppins", "manrope" to "Manrope", "outfit" to "Outfit",
        "dmSans" to "DM Sans", "plusJakartaSans" to "Plus Jakarta Sans", "rubik" to "Rubik", "nunitoSans" to "Nunito Sans",
        "publicSans" to "Public Sans", "systemDefault" to "System Default",
    )
    val PTR_CHARACTERS = listOf("cat", "dog", "pokeball", "shield", "owl", "robot", "ghost", "kitty", "bunny", "batman")
    val FAQS = listOf(
        FAQItem("为什么扫描类应用说 Orion 有跟踪器？", "AppManager 等扫描应用会标记 Orion 内置的 Unity Ads SDK。该 SDK 只为一个功能存在：「关于」页里的可选看广告（为代码加油），看广告可以支持我，还能为你赚取经验值并解锁徽章。没有分析统计，没有数据挖掘，不出售数据。应用完全开源，任何人都可以验证；广告 100% 可选，后台不会有任何东西运行。", "fa-magnifying-glass"),
        FAQItem("Orion Store 安全吗？", "当然。Orion Store 完全开源，代码公开在 GitHub 上供任何人审查。我们坚持透明：没有隐藏跟踪器，没有数据挖掘，只是一个干净的应用入口。", "fa-shield-cat"),
        FAQItem("Orion 上的应用安全吗？", "安全。每个应用上架前，我都会使用其官方仓库提供的工具亲自审查和修改，确保安全、可用且尊重隐私。", "fa-check-double"),
        FAQItem("ReVanced 构建版安全吗？", "安全。这些构建由 GitHub Actions 机器人自动完成，并非人工打补丁。它们使用官方 ReVanced CLI 和补丁，作用于来自 APKMirror、Uptodown 和 APKPure 的 APK。整个流程每天早上 6 点运行，构建管线完全透明开源：https://github.com/RookieEnough/Revanced-AutoBuilds", "fa-robot"),
        FAQItem("安装失败？", "如果看到“应用未安装”或“软件包冲突”，多半是签名不一致：修改版应用不能直接覆盖安装官方 Play 商店版本或另一个修改版。请先卸载已有的应用，再从 Orion 安装。", "fa-file-signature"),
        FAQItem("下载不了？", "别慌！前往该应用的详情页，点击右上角的“反馈”图标（⚠️），会自动预填一封邮件，方便我尽快修复。", "fa-bug"),
        FAQItem("以后会有更多应用吗？", "会的，只要还有值得推荐的开源或实用工具被发现，应用库就会持续扩充。", "fa-layer-group"),
        FAQItem("如何支持 Orion？", "可以通过 ko-fi 捐赠。代码支撑着商店，咖啡支撑着开发者！链接在社交板块里。", "fa-heart"),
        FAQItem("有隐藏彩蛋吗？", "建造者凝视之处，秘密沉睡。\n\n数一数蜘蛛的腿，数一数立方体的角。\n\n用同样的次数，敲击那张面孔。\n\n黄金真相等待懂得规则的人……我也一样。", "fa-user-secret"),
    )
}
