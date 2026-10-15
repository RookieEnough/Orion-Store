package com.orion.store.data

import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Collator

const val ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000
val collator: Collator = Collator.getInstance().apply { strength = Collator.SECONDARY }

fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%21", "!").replace("%27", "'")
    .replace("%28", "(").replace("%29", ")").replace("%7E", "~")

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

// region versioning
fun normalizeVersion(v: String?): String =
    (v ?: "").lowercase().trim().removePrefix("v").substringBefore('-').substringBefore('+').replace(Regex("[^0-9.]"), "").trim()

fun isComparableVersion(v: String?) = normalizeVersion(v).isNotEmpty()
fun isSameVersion(a: String?, b: String?) = !a.isNullOrEmpty() && !b.isNullOrEmpty() && normalizeVersion(a).let { it.isNotEmpty() && it == normalizeVersion(b) }

fun compareVersions(a: String?, b: String?): Int {
    if (a.isNullOrEmpty() || b.isNullOrEmpty()) return 0
    val x = normalizeVersion(a); val y = normalizeVersion(b)
    if (x.isEmpty() || y.isEmpty() || x == y) return 0
    val p = x.split('.'); val q = y.split('.')
    for (i in 0 until maxOf(p.size, q.size)) {
        val m = p.getOrNull(i)?.toLongOrNull() ?: 0; val n = q.getOrNull(i)?.toLongOrNull() ?: 0
        if (m != n) return m.compareTo(n)
    }
    return 0
}

fun preferredVersion(app: AppItem, stream: String = "Stable"): String {
    if (app.availableVersions.isEmpty()) return app.latestVersion
    app.availableVersions.firstOrNull { it.type == stream }?.let { return it.version }
    if (stream != "Stable") app.availableVersions.firstOrNull { it.type == "Stable" }?.let { return it.version }
    return app.availableVersions.firstOrNull()?.version ?: app.latestVersion
}

fun hasStable(app: AppItem) = app.availableVersions.any { it.type == "Stable" }

fun isPrereleaseOnly(app: AppItem) = app.availableVersions.isNotEmpty() && app.availableVersions.none { it.type == "Stable" }

/** Stream used for automatic selection. The global flag wins over a saved per-app stream; saved values are kept. */
fun effectiveStreamFor(app: AppItem, s: Settings): String =
    if (s.excludePrereleases) "Stable" else s.appStreams[app.id] ?: "Stable"

/** Stable version eligible for automatic use, or null when the app has no stable release. */
fun eligibleStableVersion(app: AppItem): String? =
    app.availableVersions.firstOrNull { it.type == "Stable" }?.version

fun hasAvailableUpdate(app: AppItem, installed: String?, stream: String = "Stable") = !installed.isNullOrEmpty() && compareVersions(preferredVersion(app, stream), installed) > 0

fun hasEligibleUpdate(app: AppItem, installed: String?, s: Settings): Boolean {
    if (installed.isNullOrEmpty()) return false
    if (s.excludePrereleases && !hasStable(app)) return false
    return hasAvailableUpdate(app, installed, effectiveStreamFor(app, s))
}

fun isUpdateIgnored(app: AppItem, s: Settings, now: Long): Boolean {
    val ig = s.ignoredUpdates[app.id] ?: return false
    return when (ig.type) {
        "never" -> true
        "week" -> ig.timestamp != null && now - ig.timestamp < ONE_WEEK_MS
        "version" -> ig.version == preferredVersion(app, effectiveStreamFor(app, s))
        else -> false
    }
}

fun availableUpdates(apps: List<AppItem>, s: Settings, now: Long) = apps.filter { a ->
    val inst = s.installedVersions[a.id]
    !inst.isNullOrEmpty() && hasEligibleUpdate(a, inst, s) && !isUpdateIgnored(a, s, now)
}
// endregion

fun sanitizeDownloadUrl(url: String?): String {
    val t = url?.trim().orEmpty()
    if (t.isEmpty()) return "#"
    return runCatching { URI(t).takeIf { it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrEmpty() }?.let { t } }.getOrNull() ?: "#"
}

// region deep links
object Share {
    const val BADGE_BASE = "https://rookieenough.github.io/Orion-Data"
    const val BADGE_IMG = "https://raw.githubusercontent.com/RookieEnough/Orion-Store/refs/heads/main/assets/orion-badge.png"
    fun deepLink(id: String) = "orionstore://app/${enc(id)}"
    fun webLink(id: String, icon: String?) = "$BADGE_BASE/redirect.html?id=${enc(id)}" + (icon?.trim()?.takeIf { it.isNotEmpty() }?.let { "&icon=${enc(it)}" } ?: "")
    fun markdown(web: String) = "[![Get it on Orion Store]($BADGE_IMG)]($web)"
    fun html(web: String) = "<a href=\"$web\">\n  <img src=\"$BADGE_IMG\" alt=\"Get it on Orion Store\" height=\"60\">\n</a>"
}
// endregion

// region discovery
private val NON_ALNUM = Regex("[^a-z0-9\\s]+"); private val SPACES = Regex("\\s+")
private fun norm(v: String) = v.lowercase().replace(NON_ALNUM, " ").replace(SPACES, " ").trim()

private class Preset(val id: String, val label: String, val icon: String, val accent: String, val gradient: List<String>, raw: List<String>, keywords: List<String>) {
    val names = setOf(norm(label), norm(id)); val raw = raw.map(::norm).toSet(); val keys = keywords.map(::norm)
}
/** An app's normalized category, tags and search text, built once per app instance and cached, so category filters stay cheap enough for the main thread. */
private class Normed(val src: AppItem) {
    private val a get() = src
    val cat = norm(a.category); val tags = a.tags.map(::norm)
    val hay by lazy { norm((listOf(a.name, a.description, a.author, a.category) + a.tags).joinToString(" ")) }
}

private val PRESETS = listOf(
    Preset("music", "Music", "fa-headphones", "#f472b6", listOf("#ec4899", "#8b5cf6"), emptyList(), listOf("music", "audio", "song", "songs", "album", "albums", "listen", "radio", "deezer", "track", "tracks", "scrobble")),
    Preset("movies", "Movies", "fa-film", "#fb923c", listOf("#f97316", "#ef4444"), emptyList(), listOf("movie", "movies", "video", "videos", "watch", "stream", "streaming", "netflix", "prime video", "hulu", "youtube")),
    Preset("anime", "Anime", "fa-dragon", "#a78bfa", listOf("#8b5cf6", "#ec4899"), emptyList(), listOf("anime", "manga", "anilist", "komikku")),
    Preset("health", "Health", "fa-heart-pulse", "#34d399", listOf("#10b981", "#14b8a6"), emptyList(), listOf("health", "fitness", "workout", "sleep", "step", "steps", "habit", "meditation", "wellness")),
    Preset("privacy", "Privacy", "fa-user-shield", "#2dd4bf", listOf("#14b8a6", "#06b6d4"), listOf("Privacy"), listOf("privacy", "private", "security", "secure", "encrypt", "encrypted", "authenticator", "2fa", "totp", "backup")),
    Preset("social", "Social", "fa-comments", "#60a5fa", listOf("#3b82f6", "#6366f1"), listOf("Social"), listOf("social", "community", "chat", "friends", "messaging", "anilist")),
    Preset("developer", "Developer", "fa-code", "#f59e0b", listOf("#f59e0b", "#f97316"), listOf("Development"), listOf("developer", "development", "code", "coding", "ide", "studio", "terminal", "debug")),
    Preset("reading", "Reading", "fa-book-open", "#22c55e", listOf("#22c55e", "#84cc16"), emptyList(), listOf("reader", "reading", "read", "book", "books", "comic", "manga", "novel")),
    Preset("productivity", "Productivity", "fa-briefcase", "#06b6d4", listOf("#06b6d4", "#3b82f6"), emptyList(), listOf("productivity", "workflow", "organize", "organise", "notes", "tasks", "todo", "calendar")),
    Preset("utilities", "Utilities", "fa-toolbox", "#94a3b8", listOf("#475569", "#0f172a"), listOf("Utility", "Utilities"), listOf("utility", "tools", "tool", "wallpaper", "download", "manager", "launcher", "offline")),
)

private fun findPreset(label: String) = norm(label).let { l -> PRESETS.firstOrNull { l in it.names } }
private fun matches(n: Normed, p: Preset) = n.cat in p.raw || n.tags.any { it in p.names } || n.hay.let { h -> p.keys.any { it in h } }

private val normCache = java.util.concurrent.ConcurrentHashMap<String, Normed>()
private fun normed(a: AppItem) = normCache[a.id]?.takeIf { it.src === a } ?: Normed(a).also { normCache[a.id] = it }

/** Category test with the label normalized and its preset looked up once, not once per app. */
fun categoryFilter(category: String): (AppItem) -> Boolean {
    if (category.isEmpty() || category == "All") return { true }
    val c = norm(category); val preset = findPreset(category)
    return { a -> val n = normed(a); n.cat == c || c in n.tags || (preset != null && matches(n, preset)) }
}

fun appMatchesCategoryFilter(a: AppItem, category: String) = categoryFilter(category)(a)

fun homepageCategoryCards(apps: List<AppItem>, limit: Int = 10): List<CategoryCard> {
    val normed = apps.map(::normed)
    val preset = PRESETS.map { p -> CategoryCard(p.id, p.label, p.icon, normed.count { matches(it, p) }, p.accent, p.gradient) }
        .filter { it.count > 0 }.sortedWith(compareByDescending<CategoryCard> { it.count }.thenComparator { a, b -> collator.compare(a.label, b.label) })
    val existing = preset.map { norm(it.label) }.toSet()
    val counts = normed.groupingBy { it.cat }.eachCount()
    val raw = apps.map { it.category }.distinct().map { c -> c to (counts[norm(c)] ?: 0) }
        .filter { (c, n) -> n > 0 && norm(c) !in existing }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenComparator { a, b -> collator.compare(a.first, b.first) })
        .map { (c, n) ->
            val k = norm(c)
            CategoryCard("raw-${k.replace(SPACES, "-")}", c, if ("social" in k) "fa-comments" else if ("media" in k) "fa-photo-film" else "fa-layer-group", n, "#64748b", listOf("#334155", "#0f172a"))
        }
    return (preset + raw).take(limit)
}

fun availableFilterCategories(apps: List<AppItem>): List<String> {
    val all = listOf("All") + homepageCategoryCards(apps, 12).map { it.label } + apps.map { it.category }.distinct().sortedWith(collator)
    return all.distinctBy(::norm)
}
// endregion

// region device arch
enum class Arch { ARM64, ARMv7, x64, x86, Universal, Unknown }

fun normalizeArch(arch: String?): Arch {
    val l = arch?.lowercase()?.trim() ?: return Arch.Unknown
    return when {
        "arm64" in l || "aarch64" in l || "v8a" in l -> Arch.ARM64
        "armeabi" in l || "armv7" in l || "v7a" in l || l == "arm" -> Arch.ARMv7
        "x86_64" in l || "x64" in l || "amd64" in l -> Arch.x64
        "x86" in l || "i386" in l || "i686" in l -> Arch.x86
        "universal" in l || "all" in l || l == "any" -> Arch.Universal
        else -> Arch.Unknown
    }
}

fun friendlyArch(arch: String) = when (normalizeArch(arch)) {
    Arch.ARM64 -> "ARM64 (64-bit)"; Arch.ARMv7 -> "ARMv7 (32-bit)"; Arch.x64 -> "x86_64 (64-bit)"; Arch.x86 -> "x86 (32-bit)"
    Arch.Universal -> "Universal"; Arch.Unknown -> arch.uppercase().ifEmpty { "Unknown" }
}

/** @return compatible to recommended */
fun archCompat(variant: String, device: String, abis: List<String>): Pair<Boolean, Boolean> {
    val v = normalizeArch(variant); val d = normalizeArch(device)
    if (v == d && d != Arch.Unknown) return true to true
    if (v == Arch.Universal) return true to (d == Arch.Unknown)
    val lv = variant.lowercase().trim()
    abis.forEach { abi -> if (v == normalizeArch(abi) || lv.contains(abi.lowercase())) return true to (abi == abis.first()) }
    if ((d == Arch.ARM64 && v == Arch.ARMv7) || (d == Arch.x64 && v == Arch.x86)) return true to false
    return false to false
}
// endregion

// region images
private val IMAGE_EXT = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".avif", ".svg")

fun explainRejectedImageLink(url: String?): String? {
    val t = url?.trim().orEmpty()
    if (t.isEmpty()) return null
    if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(t)) return "Link must start with http:// or https://"
    val u = runCatching { URI(t) }.getOrNull()?.takeIf { !it.host.isNullOrEmpty() } ?: return "Link is not a valid URL."
    val host = u.host.lowercase(); val path = u.rawPath.orEmpty()
    if (host.startsWith("private-user")) return "Private-user links are not accepted. Use a public image link."
    val ok = IMAGE_EXT.any { path.lowercase().endsWith(it) } ||
        (host.endsWith("googleusercontent.com") && path.startsWith("/play-lh")) ||
        host == "raw.githubusercontent.com" || host == "camo.githubusercontent.com" ||
        ((host.endsWith("gitlab.com") || host.endsWith("gitlab.io")) && "/-/raw/" in path) ||
        (host == "codeberg.org" && "/raw/branch/" in path)
    return if (ok) null else "Only image links are accepted (jpg/png/webp/gif/bmp/avif/svg, Play Store scraped links, or raw GitHub/GitLab/Codeberg/Camo links)."
}

private val PLAY_SIZE = Regex("=[swh]\\d+"); private val PLAY_SIZE_TAIL = Regex("=[swh]\\d+[^/]*$")
fun optimizedImageUrl(url: String?, width: Int? = null, height: Int? = null): String {
    if (url.isNullOrEmpty()) return ""
    if (url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("file:") || url.startsWith("/")) return url
    // Play images resize on Google's own CDN: ask for the display size directly instead of a full-size fetch transcoded by the proxy.
    if ("googleusercontent.com" in url || "ggpht.com" in url) {
        val base = if (PLAY_SIZE.containsMatchIn(url)) url.replace(PLAY_SIZE_TAIL, "") else url
        return base + "=" + (listOfNotNull(width?.let { "w$it" }, height?.let { "h$it" }).ifEmpty { listOf("w2560", "h1440") }).joinToString("-") + "-rw"
    }
    val src = url
    val q = when { width != null && width <= 112 -> 60; width != null && width < 200 -> 68; else -> 95 }
    return buildString {
        append("https://wsrv.nl/?url=${enc(src)}&output=webp&q=$q&l=1&il=${if (width != null && width < 200) 0 else 1}&maxage=31d&n=-1")
        width?.let { append("&w=$it") }; height?.let { append("&h=$it") }
    }
}
// endregion

fun generateSignature(data: Map<String, Any?>): String =
    sha256Hex((data.keys.sorted().joinToString("|") { "$it:${data[it]}" } + "ORION_OMEGA_PROTOCOL_X9_SECURE_HASH_V1").toByteArray())

// region change requests
data class ChangeField(val key: String, val label: String, val hint: String, val group: String, val kind: String)

private val COMMON_FIELDS = listOf(
    ChangeField("name", "App name", "Visible title", "Basics", "text"), ChangeField("author", "Developer or author", "Creator credit", "Basics", "text"),
    ChangeField("description", "Description", "Store description", "Basics", "textarea"), ChangeField("icon", "Icon URL", "Direct image URL", "Media", "url"),
    ChangeField("screenshots", "Screenshot URLs", "One URL per line", "Media", "urls"), ChangeField("downloadUrl", "Download or source URL", "Working project page", "Source", "url"),
    ChangeField("repoUrl", "Repository URL", "Main repository or website", "Source", "url"), ChangeField("githubRepo", "GitHub repository", "owner/repo or URL", "Source", "text"),
    ChangeField("gitlabRepo", "GitLab project", "Group/project or URL", "Source", "text"), ChangeField("codebergRepo", "Codeberg repository", "owner/repo or URL", "Source", "text"),
    ChangeField("officialSite", "Official website", "Product page", "Source", "url"), ChangeField("category", "Category", "Store category", "Catalog", "text"),
)
fun changeFields(platform: String) = if (platform == Platform.ANDROID) COMMON_FIELDS + listOf(
    ChangeField("releaseKeyword", "Release asset keyword", "Unique APK filename word", "Android", "text"),
    ChangeField("packageName", "Android package name", "com.example.app", "Android", "text"),
) else COMMON_FIELDS

fun currentFieldValue(app: AppItem, key: String): String = when (key) {
    "screenshots" -> app.screenshots.joinToString("\n")
    else -> when (key) {
        "name" -> app.name; "author" -> app.author; "description" -> app.description; "icon" -> app.icon; "downloadUrl" -> app.downloadUrl
        "repoUrl" -> app.repoUrl; "githubRepo" -> app.githubRepo; "gitlabRepo" -> app.gitlabRepo; "codebergRepo" -> app.codebergRepo
        "officialSite" -> app.officialSite; "category" -> app.category; "releaseKeyword" -> app.releaseKeyword; "packageName" -> app.packageName; else -> null
    }?.takeIf { it.isNotEmpty() && it != "#" } ?: "None"
}

fun urlError(v: String): String {
    val t = v.trim()
    if (t.isEmpty()) return "Enter a valid URL."
    val u = runCatching { URI(t) }.getOrNull()?.takeIf { it.scheme != null } ?: return "Use a valid http or https URL."
    return if (u.scheme.lowercase() !in setOf("http", "https")) "Use an http or https URL." else if (u.host.isNullOrEmpty()) "Use a valid http or https URL." else ""
}

fun splitUrls(v: String) = v.trim().split(Regex("\n|,")).map { it.trim() }.filter { it.isNotEmpty() }

fun validateChange(f: ChangeField, v: String): String {
    val t = v.trim()
    if (t.isEmpty()) return "Enter a corrected value."
    if (f.kind == "url") return urlError(t)
    if (f.kind == "urls") { val u = splitUrls(t); return if (u.isEmpty()) "Add at least one screenshot URL." else u.firstOrNull { urlError(it).isNotEmpty() }?.let(::urlError) ?: "" }
    if (f.key == "packageName" && !Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(t)) return "Use a valid Android package name."
    return ""
}

fun appUpdatePayloadJson(app: AppItem, field: String, value: Any): String {
    val v = if (value is List<*>) value.joinToString(",", "[", "]") { "\"${jsonEsc(it.toString())}\"" } else "\"${jsonEsc(value.toString())}\""
    return "{\n  \"id\": \"${jsonEsc(app.id)}\",\n  \"platform\": \"${jsonEsc(app.platform)}\",\n  \"$field\": $v\n}"
}
private fun jsonEsc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

fun appUpdateIssueUrl(app: AppItem, fieldLabel: String, current: String, suggested: String, reason: String, reference: String, payloadJson: String): String {
    val title = "App Update - ${app.name} (${app.platform})"
    val body = listOf(
        "### App Update Request", "", "- App ID: `${app.id}`", "- Platform: **${app.platform}**", "- Field: **$fieldLabel**", "- Current value: ${current.take(500)}", "",
        "### Suggested value", "```text", suggested.trim().take(4000).ifEmpty { "(described in reason)" }, "```", "",
        "### Reason", reason.trim().ifEmpty { "No additional details provided." }, "",
        reference.trim().let { if (it.isNotEmpty()) "**Evidence:** $it" else "" }, "",
        "### Automation payload", "```json", payloadJson, "```", "",
        "*Apply the `approved` label and run the App Update workflow to merge this change.*",
    ).joinToString("\n")
    return "https://github.com/RookieEnough/Orion-Data/issues/new?title=${enc(title)}&body=${enc(body)}"
}
// endregion

// region forced store update
private val SEMVER = Regex("(\\d+)\\.(\\d+)\\.(\\d+)")
fun compareSemver(a: String, b: String): Int {
    val x = SEMVER.find(a); val y = SEMVER.find(b)
    if (x == null || y == null) return naturalCompare(a, b)
    for (i in 1..3) { val c = x.groupValues[i].toInt().compareTo(y.groupValues[i].toInt()); if (c != 0) return c }
    return 0
}
private fun naturalCompare(a: String, b: String): Int {
    val ta = Regex("\\d+|\\D+").findAll(a).map { it.value }.toList(); val tb = Regex("\\d+|\\D+").findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(ta.size, tb.size)) {
        val p = ta[i]; val q = tb[i]
        val c = if (p[0].isDigit() && q[0].isDigit()) p.toBigInteger().compareTo(q.toBigInteger()) else collator.compare(p, q)
        if (c != 0) return c
    }
    return ta.size.compareTo(tb.size)
}

fun isStoreUpdateAvailable(cur: String, latest: String?) = !latest.isNullOrEmpty() && compareSemver(cur, latest) < 0
fun shouldForceStoreUpdate(cur: String, min: String?) = !min.isNullOrEmpty() && compareSemver(cur, min) < 0
fun forcedStoreFileName(v: String) = "OrionStore_$v.apk"
fun canResumeForcedUpdate(snap: ForcedSnapshot?, cur: String, target: String?, min: String?): Boolean =
    snap != null && snap.schemaVersion == 1 && !target.isNullOrEmpty() && (shouldForceStoreUpdate(cur, min) || isStoreUpdateAvailable(cur, target)) &&
        snap.downloadCompleted && snap.targetVersion == target

data class VtStats(val harmless: Int = 0, val malicious: Int = 0, val suspicious: Int = 0, val undetected: Int = 0, val timeout: Int = 0) {
    val flagged get() = malicious + suspicious
    val total get() = harmless + malicious + suspicious + undetected + timeout
    val verdict get() = if (flagged == 0 && total > 0) "safe" else "caution"
}

data class ForcedAction(val id: String, val label: String, val style: String, val enabled: Boolean = true)
data class ForcedVm(
    val eyebrow: String, val title: String, val body: String, val tone: String, val actions: List<ForcedAction>,
    val showProgress: Boolean, val showVersionRow: Boolean, val showSupportBlock: Boolean, val showExportPath: Boolean, val scanProgress: Float, val verdict: String?,
)

private val FORCED_COPY = mapOf(
    "inactive" to listOf("Standby", "", "", "neutral"),
    "checking" to listOf("Checking", "Checking for the latest Orion build", "Reaching Orion Store to confirm the newest version.", "progress"),
    "downloading" to listOf("Downloading", "Downloading the latest Orion build", "The update is being downloaded in the background.", "progress"),
    "download_failed" to listOf("Retry required", "The download needs another try", "Check your connection and retry the download.", "warning"),
    "ready_to_scan_or_install" to listOf("Ready", "The update is ready to install", "Review the next step, then install when you are ready.", "success"),
    "byok_required" to listOf("Scan setup", "A VirusTotal API key is required to scan", "Add your API key in settings to enable scanning before install.", "warning"),
    "scanning" to listOf("Scanning", "Scanning the update before install", "Verifying the APK with VirusTotal before installation.", "progress"),
    "scan_complete" to listOf("Scan complete", "The update is ready for install", "The scan is complete and the build is ready to install.", "success"),
    "installing" to listOf("Installing", "Android is taking over the install", "Continue with the system installer to finish the update.", "progress"),
    "install_failed" to listOf("Install failed", "The install needs a manual fallback", "Save the APK to Downloads and install it manually from Files.", "warning"),
    "export_ready" to listOf("Saved", "The APK is saved to your device", "Open the saved APK from Files to continue installation.", "success"),
    "installed" to listOf("Complete", "Orion Store is up to date", "Reopen Orion Store to continue with the new version.", "success"),
)

fun forcedVm(status: String, errorText: String?, statusText: String?, canScan: Boolean, canInstall: Boolean, exported: Boolean, exportPath: String?, scanProgress: Float, stats: VtStats?): ForcedVm {
    val c = FORCED_COPY[status] ?: FORCED_COPY.getValue("inactive")
    val a = buildList {
        if (status == "download_failed") add(ForcedAction("retry", "Retry download", "primary"))
        if (status == "byok_required") add(ForcedAction("install", "Skip scan & install", "secondary"))
        if (status == "ready_to_scan_or_install" && canScan) add(ForcedAction("scan", "Scan before install", "secondary"))
        if (status == "scan_complete" && stats != null) add(ForcedAction("open-report", "Open VirusTotal report", "secondary"))
        if (status in setOf("ready_to_scan_or_install", "scan_complete", "export_ready") && canInstall) add(ForcedAction("install", "Install now", "primary"))
        if (status == "install_failed") add(if (exported) ForcedAction("export", "Saved to downloads", "outline", false) else ForcedAction("export", "Save APK to downloads", "outline"))
    }
    return ForcedVm(
        c[0], c[1], errorText?.ifEmpty { null } ?: statusText?.ifEmpty { null } ?: c[2], c[3], a,
        status in setOf("checking", "downloading", "scanning"), status != "inactive", status in setOf("install_failed", "download_failed", "export_ready"),
        status == "export_ready" && !exportPath.isNullOrEmpty(), scanProgress.coerceIn(0f, 100f), stats?.verdict,
    )
}
// endregion

// region my apps
enum class MyAppsFilter(val label: String, val icon: String) { ALL("All", "fa-box-archive"), INSTALLED("Installed", "fa-circle-check"), DOWNLOADED("Downloaded", "fa-download"), UPDATES("Updates", "fa-arrow-up") }

data class MyAppRow(
    val app: AppItem, val status: String, val installedVersion: String?, val lastRemoteVersion: String?, val readyFile: String?, val cleanupFile: String?,
    val libraryFile: String?, val downloadKey: String?, val hasSavedApk: Boolean, val hasUpdate: Boolean, val isInstalledNow: Boolean,
    val libraryStatus: String?, val progress: Float, val downloadedAt: Long, val installedAt: Long?, val stream: String = "Stable",
) {
    val savedFile get() = readyFile ?: cleanupFile ?: libraryFile
    val primaryAction get() = when {
        status == "downloading" -> "view"; status == "ready" -> "install"; hasUpdate -> "update"
        isInstalledNow && hasSavedApk -> "delete"; libraryStatus == "downloaded" -> if (hasSavedApk) "install" else "download"; else -> "view"
    }
    val statusLabel get() = when (status) {
        "downloading" -> "${Math.round(progress)}%"; "ready" -> "Ready to install"; "update" -> "Update available"
        "installed" -> if (isInstalledNow) "Installed v$installedVersion" else "Installed previously"
        else -> if (hasSavedApk) "APK saved" else "Downloaded"
    }
    val versionDetail get() = when {
        hasUpdate -> preferredVersion(app, stream).let { l -> if (installedVersion != null) "v$installedVersion → v$l" else "v$l" }
        installedVersion != null -> "v$installedVersion"; lastRemoteVersion != null -> "v$lastRemoteVersion"; else -> "Saved"
    }
}

fun buildMyAppsRows(apps: List<AppItem>, s: Settings, d: DataState, updateIds: Set<String>): List<MyAppRow> = apps.mapNotNull { a ->
    val lib = d.appLibrary[a.id]; val inst = s.installedVersions[a.id]?.ifEmpty { null }
    val ready = d.readyToInstall[a.id]; val cleanup = d.pendingCleanup[a.id]; val key = d.activeDownloads[a.id]
    val saved = ready != null || cleanup != null || lib?.fileName != null
    val upd = a.id in updateIds; val dl = key != null; val now = inst != null
    if (lib == null && !now && !saved && !dl && !upd) return@mapNotNull null
    val status = when { dl -> "downloading"; ready != null -> "ready"; upd -> "update"; now || lib?.status == "installed" -> "installed"; else -> "downloaded" }
    MyAppRow(a, status, inst, s.lastRemoteVersions[a.id], ready, cleanup?.fileName, lib?.fileName, key, saved, upd, now, lib?.status,
        d.downloadProgress[a.id] ?: 0f, lib?.downloadedAt ?: cleanup?.timestamp ?: 0, lib?.installedAt, effectiveStreamFor(a, s))
}.sortedByDescending { it.downloadedAt }

fun MyAppRow.matches(f: MyAppsFilter) = when (f) {
    MyAppsFilter.ALL -> true; MyAppsFilter.INSTALLED -> status == "installed" || hasUpdate
    MyAppsFilter.DOWNLOADED -> status in setOf("downloading", "ready", "downloaded"); MyAppsFilter.UPDATES -> hasUpdate
}

fun MyAppRow.matchesQuery(q: String) = q.isBlank() || q.trim().lowercase().let { t -> listOfNotNull(app.name, app.author, app.category, app.packageName).any { t in it.lowercase() } }
// endregion

// region modern storefront helpers
fun modernSortTitle(sort: SortOption) = when (sort) {
    SortOption.HOME -> "Home Page" to "More Picks For You"; SortOption.OLDEST -> "Oldest First" to "More Classics"
    SortOption.NAME_ASC -> "A to Z" to "More A to Z"; SortOption.NAME_DESC -> "Z to A" to "More Z to A"
    SortOption.SIZE_ASC -> "Smallest Footprint" to "More Lightweight Picks"; SortOption.SIZE_DESC -> "Largest Packages" to "More Heavyweights"
    else -> "Recently Added" to "More Fresh Picks"
}

fun sortedModernCollections(apps: List<AppItem>, sort: SortOption, showAll: Boolean = false): List<StoreCollection> {
    if (apps.isEmpty()) return emptyList()
    val t = modernSortTitle(sort).first; val sfx = if (showAll) "-all" else ""
    return listOf(
        StoreCollection("sorted-hero-$sort$sfx", t, CollectionType.HERO, apps = apps.take(5)),
        StoreCollection("sorted-grid-$sort$sfx", t, CollectionType.SORTED_GRID, apps = if (showAll) apps else apps.take(12), totalAppCount = apps.size),
    )
}

fun isModernHome(t: TabViewState) = t.category == "All" && t.query.isEmpty() && !t.filterFavorites
fun displaySort(layout: String, t: TabViewState) = when {
    layout != "modern" -> if (t.sort == SortOption.HOME) SortOption.NEWEST else t.sort
    isModernHome(t) && (t.sort == SortOption.NEWEST || t.sort == SortOption.HOME) -> SortOption.HOME
    else -> if (t.sort == SortOption.HOME) SortOption.NEWEST else t.sort
}

fun updatesCollection(apps: List<AppItem>) = if (apps.isEmpty()) null else StoreCollection("updates-available", "Updates Available", CollectionType.SWIMLANE, apps = apps.take(12), totalAppCount = apps.size)

fun injectAfterHero(cols: List<StoreCollection>, inj: StoreCollection?): List<StoreCollection> {
    inj ?: return cols
    val rest = cols.filter { it.id != inj.id }; val h = rest.indexOfFirst { it.type == CollectionType.HERO }
    return if (h == -1) listOf(inj) + rest else rest.take(h + 1) + inj + rest.drop(h + 1)
}
// endregion

// region downloads & ownership
fun sanitizeFileVersionToken(v: String?) = (v?.ifEmpty { null } ?: "latest").replace(Regex("[^a-zA-Z0-9._-]"), "_")

fun downloadVersionMeta(app: AppItem, url: String?, stream: String = "Stable"): Pair<String, String> {
    if (url != null) app.availableVersions.firstOrNull { v -> v.variants.any { it.url == url } }?.let { return it.version to it.type }
    return preferredVersion(app, stream) to stream
}

fun streamOfUrl(app: AppItem, url: String?): String? =
    url?.let { u -> app.availableVersions.firstOrNull { v -> v.variants.any { it.url == u } }?.type }

/** True when exclusion is on and this download would resolve to a non-stable release (or to nothing stable). */
fun isExcludedDownload(app: AppItem, url: String?, s: Settings): Boolean {
    if (!s.excludePrereleases) return false
    if (url != null) {
        val explicit = app.availableVersions.firstOrNull { v -> v.variants.any { it.url == url } }?.type
        if (explicit != null) return explicit != "Stable"
    }
    return !hasStable(app)
}

fun versionMetaFromFileName(app: AppItem, file: String?, stream: String = "Stable"): Pair<String, String> {
    val f = file?.lowercase() ?: return downloadVersionMeta(app, null, stream)
    app.availableVersions.firstOrNull { v -> sanitizeFileVersionToken(v.version).lowercase().let { it.isNotEmpty() && f.contains(it) } }?.let { return it.version to it.type }
    return downloadVersionMeta(app, null, stream)
}

/** True when exclusion is on and installing this file would record a non-stable stream. */
fun isExcludedInstall(app: AppItem, file: String?, s: Settings): Boolean {
    if (!s.excludePrereleases) return false
    val stream = versionMetaFromFileName(app, file, "Stable").second
    return stream != "Stable"
}

fun normalizePackageName(p: String?) = p?.trim()?.lowercase().orEmpty()

fun packageCandidates(app: AppItem, resolved: String?): List<String> {
    val p = app.packageName?.ifEmpty { null } ?: return emptyList()
    return (listOfNotNull(resolved?.ifEmpty { null }, p) + Defaults.PACKAGE_SUFFIXES.map { p + it }).distinct()
}

data class InstallExpectation(val appId: String, val version: String?)

fun resolveOwnedAppId(group: List<AppItem>, pkgKey: String, installed: String, s: Settings, expectation: InstallExpectation?): String? {
    if (group.isEmpty()) return null
    if (group.size == 1) return group[0].id.ifEmpty { null }
    val ids = group.map { it.id }.toSet()
    if (expectation != null && expectation.appId in ids &&
        (expectation.version.isNullOrEmpty() || !isComparableVersion(expectation.version) || compareVersions(installed, expectation.version) >= 0)) return expectation.appId
    val owner = s.packageOwners[pkgKey]?.takeIf { it in ids }
    if (owner != null && isSameVersion(s.lastRemoteVersions[owner], installed)) return owner
    group.filter { isSameVersion(s.lastRemoteVersions[it.id], installed) }.singleOrNull()?.let { return it.id }
    group.filter { isSameVersion(preferredVersion(it, effectiveStreamFor(it, s)), installed) }.singleOrNull()?.let { return it.id }
    if (owner != null && group.none { isComparableVersion(s.lastRemoteVersions[it.id]) || isComparableVersion(preferredVersion(it, effectiveStreamFor(it, s))) }) return owner
    return null
}
// endregion

// ---------------- Submission helpers ----------------

data class RepoSource(val provider: String, val repoPath: String, val owner: String, val repo: String, val domain: String? = null)

/** github / gitlab (any host containing "gitlab", nested groups) / codeberg; stops at tree, blob, raw, releases. */
fun parseRepoSource(repoUrl: String): RepoSource? = runCatching {
    val u = URI(repoUrl.trim()); val host = u.host ?: return null
    val raw = (u.path ?: "").split('/').filter { it.isNotEmpty() }
    val stop = raw.indexOfFirst { it in setOf("tree", "blob", "raw", "releases") }
    val parts = (if (stop >= 0) raw.take(stop) else raw).filter { it != "-" }
    if (parts.size < 2) return null
    val gitlab = "gitlab" in host; val owner = parts[0]
    val repo = (if (gitlab) parts.last() else parts[1]).replace(GIT_SUFFIX, "")
    val path = (if (gitlab) parts.joinToString("/") else "$owner/$repo").replace(GIT_SUFFIX, "")
    when {
        host.endsWith("github.com") -> RepoSource("github", path, owner, repo)
        gitlab -> RepoSource("gitlab", path, owner, repo, host)
        host.endsWith("codeberg.org") -> RepoSource("codeberg", path, owner, repo)
        else -> null
    }
}.getOrNull()

val ANDROID_META_PATHS = listOf("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml", "android/app/src/main/AndroidManifest.xml",
    "app/build.gradle", "app/build.gradle.kts", "android/app/build.gradle", "android/app/build.gradle.kts", "build.gradle", "build.gradle.kts")
private val PACKAGE_PATTERNS = listOf("""applicationId\s*(?:=)?\s*["']([a-zA-Z0-9_.]+)["']""", """namespace\s*(?:=)?\s*["']([a-zA-Z0-9_.]+)["']""", """package\s*=\s*["']([a-zA-Z0-9_.]+)["']""").map(::Regex)

fun extractPackageName(content: String): String? = PACKAGE_PATTERNS.firstNotNullOfOrNull { it.find(content)?.groupValues?.get(1) }

private val GIT_SUFFIX = Regex("""\.git$""", RegexOption.IGNORE_CASE)

fun humanizeRepoName(repo: String) = repo.replace(GIT_SUFFIX, "").replace(Regex("[-_]+"), " ").replace(Regex("""\s+"""), " ").trim()
    .replace(Regex("""\b\w""")) { it.value.uppercase() }

/** github.com/o/r/blob/x → raw.githubusercontent.com/o/r/x; anything else unchanged. */
fun sanitizeGitHubImageUrl(url: String): String {
    val t = url.trim(); if (t.isEmpty()) return ""; if (!t.startsWith("http")) return t
    return runCatching {
        val u = URI(t); val seg = (u.path ?: "").split('/').filter { it.isNotEmpty() }.toMutableList()
        if (u.host.orEmpty().endsWith("github.com") && seg.size >= 4 && seg[2] == "blob") { seg.removeAt(2); "https://raw.githubusercontent.com/${seg.joinToString("/")}" } else url
    }.getOrDefault(url)
}

fun slugId(name: String) = name.lowercase().replace(Regex("[^a-z0-9]"), "-").replace(Regex("-+"), "-").trim('-')

fun submissionIssueUrl(apps: String, count: Int, firstName: String?, version: String): String {
    val title = "App Submission [$count App${if (count > 1) "s" else ""}] - ${firstName ?: "New app"}"
    val body = "### App Submission Request\n\nI would like to submit the following apps to Orion Store.\n\n```json\n$apps\n```\n\n*Generated by Orion Store v$version*"
    return "https://github.com/RookieEnough/Orion-Data/issues/new?title=${enc(title)}&body=${enc(body)}"
}

/** Every XP source in one place: the pilot card, rank badges, submission page and leaderboard all read this. */
object Xp {
    const val AD = 40; const val SUBMISSION = 100; const val APP = 10
    /** [apps] is the library size: each app counts once, so re-downloading the same APK cannot farm XP. */
    fun total(s: Settings, apps: Int) = s.adWatchCount * AD.toLong() + s.submissionCount * SUBMISSION.toLong() + apps * APP.toLong() + s.gameXP
    /** XP from level n to n + 1: 60, 70, 80... Bronze (10) takes 900 XP, Silver (30) 5,800, Gold (50) 14,700. */
    fun step(n: Int) = 50L + 10 * n
    fun start(level: Int) = (1 until level).sumOf { step(it) }
    fun level(xp: Long): Int { var l = 1; var left = xp; while (left >= step(l)) { left -= step(l); l++ }; return l }
    fun title(level: Int) = when { level >= 50 -> "Gold pilot"; level >= 30 -> "Silver pilot"; level >= 10 -> "Bronze pilot"; else -> "Cadet" }
}
/** Pilot card tiers and the badge each one awards. */
val TIER_BADGES = listOf(10 to "Bronze Wings", 30 to "Silver Wings", 50 to "Gold Wings")
