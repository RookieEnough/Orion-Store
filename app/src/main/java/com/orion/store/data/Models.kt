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
    HOME("首页精选"), NEWEST("最新添加"), OLDEST("最早添加"), NAME_ASC("名称 (A-Z)"),
    NAME_DESC("名称 (Z-A)"), SIZE_ASC("大小（从小到大）"), SIZE_DESC("大小（从大到小）")
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
    val DEV_PROFILE = DevProfile("RookieZ", "https://i.pinimg.com/originals/12/79/48/127948a3253396796874286570740594.jpg", "构建开放网络，一次一个提交。无广告，无跟踪，只有代码。")
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
        FAQItem("为什么扫描类应用说 Orion 有跟踪器？", "AppManager 之类的扫描应用会标记 Orion 内置的 Unity Ads SDK。这个 SDK 只为一个功能存在：“开发者”标签页里可选的广告任务（为代码加油）——观看广告可以支持开发者，还能获得经验值并解锁徽章。没有数据采集，没有数据挖掘，也不出售数据。应用完全开源，任何人都可以验证，广告完全可选，后台也不会运行任何东西。", "fa-magnifying-glass"),
        FAQItem("Orion Store 安全吗？", "绝对安全。Orion Store 完全开源，代码公开在 GitHub 上，任何人都可以审计。我们坚信透明：没有隐藏的跟踪器，没有数据挖掘，只是一个干净的应用入口。", "fa-shield-cat"),
        FAQItem("商店里的应用安全吗？", "安全。上架前我会亲自逐一审查和修改，使用其官方仓库提供的工具确保它们安全、可用且尊重隐私。", "fa-check-double"),
        FAQItem("ReVanced 构建安全吗？", "安全。这些构建由 GitHub Actions 机器人自动完成，并非人工打补丁，使用官方 ReVanced CLI 和补丁处理来自 APKMirror、Uptodown 和 APKPure 的 APK。流程每天早上 6 点运行，整条构建流水线透明且开源：https://github.com/RookieEnough/Revanced-AutoBuilds", "fa-robot"),
        FAQItem("安装失败了？", "如果看到“应用未安装”或“软件包冲突”，多半是签名不一致。你不能把修改版直接装在原版（Play 商店版）或另一个修改版之上。请先卸载已装的应用，再安装 Orion 提供的版本。", "fa-file-signature"),
        FAQItem("下载不了？", "别慌！前往应用详情页，点右上角的“反馈”图标（⚠️），会预填一封邮件，我会尽快修复。", "fa-bug"),
        FAQItem("以后会有更多应用吗？", "会的，只要还有值得上架的有趣应用。只要我发现优秀的开源项目或实用工具，应用库就会继续增长。", "fa-layer-group"),
        FAQItem("如何支持开发者？", "通过 Ko-fi 捐赠。代码支撑商店，咖啡支撑开发者！链接在社交栏里。", "fa-heart"),
        FAQItem("有没有隐藏彩蛋？", "建造者凝视之处，秘密沉睡。\n\n数一数蜘蛛的腿，数一数立方体的顶点。\n\n以同样的次数，敲击那张面孔。\n\n黄金真相等待知晓规则的人……一如我知道的那样。", "fa-user-secret"),
    )
}
