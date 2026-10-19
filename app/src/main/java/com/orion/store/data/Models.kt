package com.orion.store.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true; encodeDefaults = true }

object Platform { const val ANDROID = "Android"; const val PC = "PC"; const val TV = "TV" }

enum class SortOption(val label: String) {
    HOME("Home Page"), NEWEST("Recently Added"), OLDEST("Oldest Added"), NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"), SIZE_ASC("Size (Smallest)"), SIZE_DESC("Size (Largest)")
}

enum class Tab(val platform: String?) { ANDROID(Platform.ANDROID), PC(Platform.PC), TV(Platform.TV), MYAPPS(null), ABOUT(null), UPDATES(null) }

val STREAMS = listOf("Stable", "Beta", "Alpha", "Nightly")

@Serializable data class AppVariant(val arch: String = "", val url: String = "", val size: Long? = null)

@Serializable data class VersionOption(
    val type: String = "Stable",
    val version: String = "",
    val date: String = "",
    val variants: List<AppVariant> = emptyList(),
    val tag: String = "",
    val releaseUrl: String = "",
)

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
        FAQItem("Why do scanner apps say Orion has trackers?", "Scanner apps like AppManager flag the Unity Ads SDK inside Orion. That SDK exists for exactly one feature: the optional ad-watching in the About tab (Fuel the Code), where watching ads supports me and earns you XP and unlocks badges. No analytics, no data mining, no data selling. The app is open source so anyone can verify it, and ads are 100% optional with nothing running in the background.", "fa-magnifying-glass"),
        FAQItem("Is Orion Store safe?", "Absolutely. Orion Store is completely open-source. This means our code is public on GitHub for anyone to audit. We believe in transparency: no hidden trackers, no data mining, just a clean gateway to apps.", "fa-shield-cat"),
        FAQItem("Are apps on Orion safe?", "Yes. I personally review and mod them using tools available on their official repositories to ensure they are safe, functional, and privacy-respecting before they land here.", "fa-check-double"),
        FAQItem("Are ReVanced builds safe?", "Yes. These builds are automated via GitHub Actions bots, not manually patched. They use the official ReVanced CLI and patches on APKs sourced from APKMirror, Uptodown, and APKPure. The process runs daily at 6 AM. The entire build pipeline is transparent and open-source: https://github.com/RookieEnough/Revanced-AutoBuilds", "fa-robot"),
        FAQItem("Installation failed?", "If you see 'App not installed' or 'Package conflicts', it's likely a signature mismatch. You cannot install a modded app over the original Play Store version or a different mod. Uninstall the existing app first, then install the one from Orion.", "fa-file-signature"),
        FAQItem("Download not working?", "Don't panic! Just head to the app's detail page and click the 'Report' icon (⚠️) in the top right corner. It will pre-fill an email so I can fix it ASAP.", "fa-bug"),
        FAQItem("Will there be more apps?", "Yes, if there'll be more interesting apps to add on. As long as I find open-source or useful tools that deserve a spotlight, the library will keep growing.", "fa-layer-group"),
        FAQItem("How can I support?", "By donation through ko-fi. Code fuels the store, but coffee fuels the dev! You can find the link in the socials section.", "fa-heart"),
        FAQItem("Is there any hidden easter egg?", "Where the Architect stares, the secret sleeps.\n\nCount the legs of a spider. Count the vertices of a cube.\n\nStrike the Visage that many times.\n\nThe Golden Truth awaits those who know the rules... and so do I.", "fa-user-secret"),
    )
}
