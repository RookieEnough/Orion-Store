package com.orion.store.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

/** The catalog version the installed build stands for. A Beta or Nightly often names itself differently from its release
 *  (1.4.2 on the phone, 20261020 or 1.5.0-beta3 in the catalog), so the build Orion installed is matched by its recorded offer first, then by an exact name. */
fun installedAs(app: AppItem, installed: String, build: InstalledBuild?): VersionOption? {
    build?.takeIf { it.versionName == installed }?.let { b -> app.availableVersions.firstOrNull { it.version == b.offered }?.let { return it } }
    val raw = installed.lowercase().removePrefix("v")
    return app.availableVersions.firstOrNull { it.version.lowercase().removePrefix("v") == raw }
}

fun hasAvailableUpdate(app: AppItem, installed: String?, stream: String = "Stable", build: InstalledBuild? = null): Boolean {
    if (installed.isNullOrEmpty()) return false
    val offer = preferredVersion(app, stream)
    val known = installedAs(app, installed, build) ?: build?.takeIf { it.versionName == installed }?.let { VersionOption(stream, it.offered, "", emptyList()) }
    if (known != null) {
        if (known.version == offer) return false
        // Same catalog naming on both sides: newer only when it really is; otherwise a different release of the same channel is an update
        val c = compareVersions(offer, known.version)
        return if (isComparableVersion(offer) && isComparableVersion(known.version) && normalizeVersion(offer) != normalizeVersion(known.version)) c > 0
            else app.availableVersions.firstOrNull { it.version == offer }?.type == known.type
    }
    return compareVersions(offer, installed) > 0
}

fun isUpdateIgnored(app: AppItem, s: Settings, now: Long): Boolean {
    val ig = s.ignoredUpdates[app.id] ?: return false
    return when (ig.type) {
        "never" -> true
        "week" -> ig.timestamp != null && now - ig.timestamp < ONE_WEEK_MS
        // Older builds stored latestVersion, which differs from the offered build when an app lists several versions
        "version" -> isSameVersion(ig.version, preferredVersion(app, s.streamOf(app.id))) || isSameVersion(ig.version, app.latestVersion)
        else -> false
    }
}

/** The installer, when it is another store. Orion, the system installer screen, adb/Shizuku and unrecorded installs don't count. */
fun otherStore(id: String, s: Settings): String? = s.installSources[id]?.takeIf { p ->
    p.isNotEmpty() && p != com.orion.store.BuildConfig.APPLICATION_ID && p != "com.android.shell" && "packageinstaller" !in p && p != s.installerPackage
}

/** Orbit apps are ones the user added on purpose, so the other-stores filter never drops them. */
fun isManaged(id: String, s: Settings) = s.appManage[id] ?: (Orbit.owns(id) || !(s.skipOtherStores && otherStore(id, s) != null))

fun availableUpdates(apps: List<AppItem>, s: Settings, now: Long) = apps.filter { a ->
    val inst = s.installedVersions[a.id]
    !inst.isNullOrEmpty() && isManaged(a.id, s) && hasAvailableUpdate(a, inst, s.streamOf(a.id), s.installedBuilds[a.id]) && !isUpdateIgnored(a, s, now)
}
// endregion

// region install problems, in words anyone gets
/** Why an APK can't go over what is already on the phone: a different signer installed here, hidden in another profile, or built in. */
enum class Clash { INSTALLED, HIDDEN, SYSTEM }

private const val HIDDEN_SPOTS = "Secure Folder, a Work profile, Dual apps or Second space"

fun clashText(c: Clash, name: String): Pair<String, String> = when (c) {
    Clash.INSTALLED -> "This won't install over your $name" to
        "The $name on your phone came from somewhere else, like Play Store or a different mod. Android only lets an app be replaced by a copy from the same maker, and this one is from a different maker.\n\n" +
        "Fix: uninstall your current $name, then install this one. Back up anything important first: uninstalling deletes the app's data."
    Clash.HIDDEN -> "A hidden copy of $name is blocking this" to
        "$name is gone from your home screen, but a copy is still on this phone: in $HIDDEN_SPOTS, or left behind by an uninstall that kept its data. That copy is from a different maker, so Android refuses this one.\n\n" +
        "Fix: uninstall $name from there too (open Secure Folder or the other profile, then Settings, Apps, $name, Uninstall). Then try again."
    Clash.SYSTEM -> "$name came with your phone" to
        "$name is built into your phone, and Android only accepts updates for it from the phone maker. A modded copy can't replace it, even after removing it with Debloat.\n\n" +
        "Fix: pick a version of the mod that installs as a separate app (often called non-root), or use root."
}

/** Turns pm, adb and PackageInstaller failures into a title and a plain explanation. */
fun installFailureText(msg: String?, name: String): Pair<String, String> {
    val m = msg.orEmpty(); val l = m.lowercase()
    fun has(vararg k: String) = k.any { it.lowercase() in l }
    return when {
        has("UPDATE_INCOMPATIBLE", "signatures do not match", "SHARED_USER_INCOMPATIBLE", "INCONSISTENT_CERTIFICATES") -> "$name could not be installed" to
            "Another copy of $name on this phone is from a different maker (like Play Store or another mod), and Android won't put this one over it.\n\n" +
            "Fix: uninstall the current $name. Already did? A hidden copy may still be in $HIDDEN_SPOTS. Remove it there too, then try again."
        has("VERSION_DOWNGRADE") -> "Your $name is newer" to "The $name on your phone is a newer version than this one, and Android won't go back to an older version. Uninstall it first if you really want this one."
        has("INSUFFICIENT_STORAGE", "not enough space") -> "Phone storage is full" to "There isn't enough free space to install $name. Delete a few things and try again."
        has("OLDER_SDK", "requires newer sdk") -> "Your Android is too old for this" to "This $name needs a newer Android version than your phone has."
        has("NO_MATCHING_ABIS", "CPU_ABI") -> "Wrong build for your phone" to "This $name was built for a different phone processor. Pick another architecture on the app page."
        has("DUPLICATE_PERMISSION", "CONFLICTING_PROVIDER", "DUPLICATE_PACKAGE") -> "Another app is in the way" to
            "Another installed app already uses a part $name needs, usually a different version of the same mod. Uninstall that one first."
        has("USER_RESTRICTED") -> "Your phone blocked the install" to
            "A phone setting stopped it. On Xiaomi, turn on \"Install via USB\" in Developer options. Work or parental rules can block installs too."
        has("VERIFICATION_FAILURE", "ABORTED", "VERIFICATION_TIMEOUT") -> "Android's security check stopped it" to "Play Protect or your phone's own scanner blocked $name. You can try again and choose Install anyway if it asks."
        has("PARSE", "CORRUPT", "invalid", "INVALID_APK") -> "The file is damaged" to "The downloaded file isn't a working app. Delete it and download again."
        has("Shizuku") -> "Shizuku isn't ready" to "The silent install needs Shizuku running and allowed. Open Shizuku, start it, then try again."
        else -> "$name could not be installed" to (Regex("INSTALL_[A-Z_]+").find(m)?.value?.let { "Android said: $it" } ?: m.ifBlank { "Android did not say why." }).take(200)
    }
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
    /** Short and clean: the page reads only the id. redirect.html stays up for old links. */
    fun webLink(id: String) = "$BADGE_BASE/app/?id=${enc(id)}"
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
    /** Padded with spaces so keywords match whole words: "track" must not pull in every "tracker". */
    val hay by lazy { " " + norm((listOf(a.name, a.description, a.author, a.category) + a.tags).joinToString(" ")) + " " }
}

private val PRESETS = listOf(
    Preset("music", "Music", "fa-headphones", "#f472b6", listOf("#ec4899", "#8b5cf6"), emptyList(), listOf("music", "song", "songs", "album", "albums", "listen", "radio", "deezer", "scrobble")),
    Preset("movies", "Movies", "fa-film", "#fb923c", listOf("#f97316", "#ef4444"), emptyList(), listOf("movie", "movies", "video", "videos", "watch", "stream", "streaming", "netflix", "prime video", "hulu", "youtube")),
    Preset("anime", "Anime", "fa-dragon", "#a78bfa", listOf("#8b5cf6", "#ec4899"), emptyList(), listOf("anime", "manga", "anilist", "komikku")),
    Preset("health", "Health", "fa-heart-pulse", "#34d399", listOf("#10b981", "#14b8a6"), emptyList(), listOf("health", "fitness", "workout", "habit", "meditation", "wellness")),
    Preset("privacy", "Privacy", "fa-user-shield", "#2dd4bf", listOf("#14b8a6", "#06b6d4"), listOf("Privacy"), listOf("privacy", "private", "security", "secure", "encrypt", "encrypted", "authenticator", "2fa", "totp", "backup")),
    Preset("social", "Social", "fa-comments", "#60a5fa", listOf("#3b82f6", "#6366f1"), listOf("Social"), listOf("social", "community", "chat", "friends", "messaging", "anilist")),
    Preset("developer", "Developer", "fa-code", "#f59e0b", listOf("#f59e0b", "#f97316"), listOf("Development"), listOf("developer", "development", "code", "coding", "ide", "studio", "terminal", "debug")),
    Preset("reading", "Reading", "fa-book-open", "#22c55e", listOf("#22c55e", "#84cc16"), emptyList(), listOf("reader", "reading", "book", "books", "comic", "manga", "novel")),
    Preset("productivity", "Productivity", "fa-briefcase", "#06b6d4", listOf("#06b6d4", "#3b82f6"), emptyList(), listOf("productivity", "workflow", "organize", "organise", "notes", "tasks", "todo", "calendar")),
    Preset("utilities", "Utilities", "fa-toolbox", "#94a3b8", listOf("#475569", "#0f172a"), listOf("Utility", "Utilities"), listOf("utility", "tools", "tool", "wallpaper", "download", "manager", "launcher", "offline")),
)

private fun findPreset(label: String) = norm(label).let { l -> PRESETS.firstOrNull { l in it.names } }
private fun matches(n: Normed, p: Preset) = n.cat in p.raw || n.tags.any { it in p.names } || n.hay.let { h -> p.keys.any { " $it " in h } }

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
val IMAGE_EXT = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".avif", ".svg")

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
    val libraryStatus: String?, val progress: Float, val downloadedAt: Long, val installedAt: Long?,
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
        hasUpdate -> preferredVersion(app).let { l -> if (installedVersion != null) "v$installedVersion → v$l" else "v$l" }
        installedVersion != null -> "v$installedVersion"; lastRemoteVersion != null -> "v$lastRemoteVersion"; else -> "Saved"
    }
}

fun buildMyAppsRows(apps: List<AppItem>, s: Settings, d: DataState, updateIds: Set<String>): List<MyAppRow> = apps.mapNotNull { a ->
    val lib = d.appLibrary[a.id]; val inst = s.installedVersions[a.id]?.ifEmpty { null }
    val ready = d.readyToInstall[a.id]; val cleanup = d.pendingCleanup[a.id]; val key = d.activeDownloads[a.id]
    val saved = ready != null || cleanup != null || lib?.fileName != null
    val upd = a.id in updateIds; val dl = key != null; val now = inst != null
    if (lib == null && !now && !saved && !dl && !upd) return@mapNotNull null
    if (!dl && ready == null && !isManaged(a.id, s)) return@mapNotNull null
    val status = when { dl -> "downloading"; ready != null -> "ready"; upd -> "update"; now || lib?.status == "installed" -> "installed"; else -> "downloaded" }
    MyAppRow(a, status, inst, s.lastRemoteVersions[a.id], ready, cleanup?.fileName, lib?.fileName, key, saved, upd, now, lib?.status,
        d.downloadProgress[a.id] ?: 0f, lib?.downloadedAt ?: cleanup?.timestamp ?: 0, lib?.installedAt)
}.sortedByDescending { it.downloadedAt }

fun myAppsFilterOrder(saved: List<String>) = saved.mapNotNull { n -> MyAppsFilter.entries.firstOrNull { it.name == n } }.distinct().let { it + (MyAppsFilter.entries - it.toSet()) }

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
    fun fileExt(url: String?): String {
        val path = url?.substringBefore('?')?.substringBefore('#')?.lowercase().orEmpty()
        return when {
            path.endsWith(".apks") -> "apks"; path.endsWith(".xapk") -> "xapk"; path.endsWith(".apkm") -> "apkm"
            path.endsWith(".zip") -> "zip"; else -> "apk"
        }
    }
/** One name per app version: the store and the background updater find each other's downloads by it. */
fun apkFileName(app: AppItem, version: String, url: String? = null) =
    "${app.name.replace(Regex("[^a-zA-Z0-9]"), "_")}_${sanitizeFileVersionToken(version.ifEmpty { app.latestVersion })}.${fileExt(url ?: app.variants.firstOrNull()?.url ?: app.downloadUrl)}"

fun downloadVersionMeta(app: AppItem, url: String?, stream: String = "Stable"): Pair<String, String> {
    if (url != null) app.availableVersions.firstOrNull { v -> v.variants.any { it.url == url } }?.let { return it.version to it.type }
    return preferredVersion(app, stream) to stream
}

fun versionMetaFromFileName(app: AppItem, file: String?, stream: String = "Stable"): Pair<String, String> {
    val f = file?.lowercase() ?: return downloadVersionMeta(app, null, stream)
    app.availableVersions.firstOrNull { v -> sanitizeFileVersionToken(v.version).lowercase().let { it.isNotEmpty() && f.contains(it) } }?.let { return it.version to it.type }
    return downloadVersionMeta(app, null, stream)
}

fun normalizePackageName(p: String?) = p?.trim()?.lowercase().orEmpty()

/** One extra name segment that marks a release channel: ".prerelease", ".beta2", ".nightly_build", ".fdroid". */
private val CHANNEL_SEGMENT = Regex("(pre|prerelease|beta|alpha|nightly|debug|dev|canary|preview|test|testing|staging|snapshot|rc|insider|next|edge|unstable|experimental|release|internal|fdroid|foss|play|gplay|github|libre)([0-9]+|_[a-z0-9_]+)?", RegexOption.IGNORE_CASE)

/** Packages that count as this app being installed. Channel builds install beside the stable app under an extra segment, so any
 *  installed package shaped like that is matched too, not just the suffixes Orion knows by name. */
fun packageCandidates(app: AppItem, resolved: String?, installed: Collection<String> = emptyList()): List<String> {
    val p = app.packageName?.ifEmpty { null } ?: return emptyList()
    val prefix = "$p."
    val channels = installed.filter { it.length > prefix.length && it.startsWith(prefix, true) && CHANNEL_SEGMENT.matches(it.substring(prefix.length)) }.sorted()
    return (listOfNotNull(resolved?.ifEmpty { null }, p) + Defaults.PACKAGE_SUFFIXES.map { p + it } + channels).distinct()
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
    group.filter { isSameVersion(preferredVersion(it, s.streamOf(it.id)), installed) }.singleOrNull()?.let { return it.id }
    if (owner != null && group.none { isComparableVersion(s.lastRemoteVersions[it.id]) || isComparableVersion(preferredVersion(it, s.streamOf(it.id))) }) return owner
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

/** Where repos keep their launcher art, most reliable first: F-Droid/fastlane metadata, the Play Store export, then the launcher mipmaps. */
val ICON_PATHS = listOf("fastlane/metadata/android/en-US/images/icon.png", "metadata/en-US/images/icon.png", "app/src/main/ic_launcher-playstore.png",
    "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png", "app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp", "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
    "app/src/main/res/mipmap-xxhdpi/ic_launcher.png", "app/src/main/res/mipmap-xxhdpi/ic_launcher.webp", "android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
    "fastlane/metadata/android/en-GB/images/icon.png")
/** Folders of store screenshots in F-Droid/fastlane metadata. */
val SCREENSHOT_DIRS = listOf("fastlane/metadata/android/en-US/images/phoneScreenshots", "metadata/en-US/images/phoneScreenshots", "fastlane/metadata/android/en-GB/images/phoneScreenshots")

private val MD_IMAGE = Regex("""!\[([^\]]*)]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)""")
private val HTML_IMAGE = Regex("""<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
private val HTML_ALT = Regex("""\balt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
/** Badges, store buttons and CI shields: images in a README that are never screenshots. */
private val NOT_A_SHOT = Regex("""shields\.io|badgen|badge|/actions/workflows|get-?it-?on|google-?play|play_store|fdroid|f-droid|obtainium|izzy|apt\.izzysoft|liberapay|ko-?fi|buymeacoffee|paypal|patreon|opencollective|discord|telegram|matrix|weblate|crowdin|contributors|star-?history|license|codecov|sonarcloud|\.svg(\?|$)""", RegexOption.IGNORE_CASE)
private val LOOKS_LIKE_SHOT = Regex("""screen|shot|preview|demo|showcase|phone|mockup|ui|light|dark""", RegexOption.IGNORE_CASE)
private val LOOKS_LIKE_ICON = Regex("""icon|logo|launcher|banner""", RegexOption.IGNORE_CASE)

/** Images in a README as absolute links with their alt text, relative paths resolved against the repo's raw file base. */
fun readmeImages(md: String, rawBase: String): List<Pair<String, String>> {
    val found = MD_IMAGE.findAll(md).map { it.groupValues[2] to it.groupValues[1] } +
        HTML_IMAGE.findAll(md).map { it.groupValues[1] to (HTML_ALT.find(it.value)?.groupValues?.get(1).orEmpty()) }
    return found.mapNotNull { (u, alt) ->
        val t = u.trim()
        val abs = when {
            t.startsWith("http://") || t.startsWith("https://") -> sanitizeGitHubImageUrl(t)
            t.startsWith("//") -> "https:$t"
            t.startsWith("data:") || t.startsWith("#") -> return@mapNotNull null
            else -> rawBase.trimEnd('/') + "/" + t.removePrefix("./").trimStart('/')
        }
        abs to alt
    }.distinctBy { it.first }.toList()
}

/** Screenshot links out of README images: badges and store buttons dropped, ones that say they are screens first, at most [max]. */
fun pickScreenshots(images: List<Pair<String, String>>, max: Int = 8): List<String> {
    val usable = images.filter { (u, alt) -> !NOT_A_SHOT.containsMatchIn(u) && !NOT_A_SHOT.containsMatchIn(alt) && !LOOKS_LIKE_ICON.containsMatchIn(u.substringAfterLast('/')) && explainRejectedImageLink(u) == null }
    val (named, rest) = usable.partition { (u, alt) -> LOOKS_LIKE_SHOT.containsMatchIn(u.substringAfterLast('/')) || LOOKS_LIKE_SHOT.containsMatchIn(alt) }
    return (named + rest).map { it.first }.take(max)
}

/** A README image that says it is the app's icon or logo, when the repo keeps none in the usual places. */
fun pickReadmeIcon(images: List<Pair<String, String>>): String? = images.firstOrNull { (u, alt) ->
    (LOOKS_LIKE_ICON.containsMatchIn(u.substringAfterLast('/')) || LOOKS_LIKE_ICON.containsMatchIn(alt)) && !NOT_A_SHOT.containsMatchIn(u) && explainRejectedImageLink(u) == null && !u.contains("banner", true)
}?.first

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
    const val AD = 40; const val SUBMISSION = 100; const val APP = 10; const val COSMETIC = 150
    /** [apps] is the library size: each app counts once, so re-downloading the same APK cannot farm XP. */
    fun total(s: Settings, apps: Int) = s.adWatchCount * AD.toLong() + s.submissionCount * SUBMISSION.toLong() + apps * APP.toLong() + s.gameXP +
        COSMETIC_BADGES.count { (_, set) -> set.all { it.unlocked(s) } } * COSMETIC.toLong()
    /** XP from level n to n + 1 grows quadratically: 61, 74, 89... 250 at 10, 1,250 at 30. Bronze (10) takes 1,185 XP, Silver (30) 14,355, Gold (50) 55,125. */
    fun step(n: Int) = 50L + 10L * n + n.toLong() * n

    // Anti-farming limits. Fuel the Code pays out at most AD_DAILY times a day, AD_GAP_MS apart; the mini game at most GAME_DAILY XP a day.
    const val DAY_MS = 86_400_000L; const val AD_DAILY = 20; const val AD_GAP_MS = 30_000L; const val GAME_DAILY = 300L
    private const val SUBMISSION_DAILY = 5
    /** 2026-01-01, before Fuel the Code shipped: no honest pilot can be ahead of a perfect daily grind since then. */
    private const val EPOCH = 1_767_225_600_000L
    /** False when progress is impossible to have earned honestly, e.g. a hand-edited backup. Checked on import and before leaderboard submits. */
    fun plausible(s: Settings, now: Long): Boolean {
        val days = maxOf(1L, (now - EPOCH) / DAY_MS + 1)
        return s.adWatchCount in 0..AD_DAILY * days && s.gameXP in 0..GAME_DAILY * days && s.submissionCount in 0..SUBMISSION_DAILY * days && s.changeRequestCount in 0..s.submissionCount
    }
    fun start(level: Int) = (1 until level).sumOf { step(it) }
    fun level(xp: Long): Int { var l = 1; var left = xp; while (left >= step(l)) { left -= step(l); l++ }; return l }
    fun title(level: Int) = when { level >= 50 -> "Gold pilot"; level >= 30 -> "Silver pilot"; level >= 10 -> "Bronze pilot"; else -> "Cadet" }
}
/** Pilot card tiers and the badge each one awards. */
val TIER_BADGES = listOf(10 to "Bronze Wings", 30 to "Silver Wings", 50 to "Gold Wings")

// region release notes, straight from GitHub Releases
/** One release boiled down for the What's new sheet: its section titles and one short headline per bullet. */
data class Release(val version: String, val name: String, val publishedAt: Long, val url: String, val sections: List<ReleaseSection>)
data class ReleaseSection(val title: String, val items: List<String>)

private val BOLD_LEAD = Regex("""^\*\*(.+?)\*\*""")
private val MD_LINK = Regex("""\[([^\]]+)]\([^)]*\)""")

/** Markdown body → headlines. A bullet's bold lead is its headline; without one, its first sentence, trimmed to fit a line or two. */
fun releaseHighlights(body: String): List<ReleaseSection> {
    val out = mutableListOf<ReleaseSection>(); var title = ""; var items = mutableListOf<String>()
    fun flush() { if (items.isNotEmpty()) out += ReleaseSection(title, items); items = mutableListOf() }
    body.lineSequence().map { it.trim() }.forEach { l ->
        when {
            l.startsWith("#") -> { flush(); title = l.trimStart('#').trim().replace("**", "") }
            l.startsWith("- ") || l.startsWith("* ") -> {
                val t = l.drop(2).trim()
                val head = BOLD_LEAD.find(t)?.groupValues?.get(1) ?: t.substringBefore(". ")
                val line = head.replace(MD_LINK, "$1").replace("**", "").replace("`", "").trim().trimEnd(':', '.')
                if (line.isNotEmpty()) items += if (line.length <= 90) line else line.take(88).substringBeforeLast(' ') + "…"
            }
        }
    }
    flush(); return out
}

/** GitHub's `/releases` JSON, newest first, drafts skipped. */
fun parseReleases(raw: String): List<Release> {
    val time = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    return json.parseToJsonElement(raw).jsonArray.mapNotNull { e ->
        val o = e.jsonObject; fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if ((o["draft"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
        // Release names read like "Orion-RE : The Labyrinth Update"; the part after the colon is the title
        val name = str("name").substringAfter(':').trim().ifEmpty { str("name") }
        Release(str("tag_name").removePrefix("v"), name, runCatching { time.parse(str("published_at"))!!.time }.getOrDefault(0), str("html_url"), releaseHighlights(str("body")))
    }
}
// endregion

// region app release notes
/** The repo an app's releases come from: the catalog's repo fields first, then its repo URL. */
fun appRepo(app: AppItem): RepoSource? {
    fun path(s: String?) = s?.trim()?.substringAfter("://")?.substringAfter(".com/")?.substringAfter(".org/")?.trim('/')?.removeSuffix(".git")?.takeIf { '/' in it }
    path(app.githubRepo)?.let { return RepoSource("github", it, it.substringBefore('/'), it.substringAfterLast('/')) }
    path(app.gitlabRepo)?.let { return RepoSource("gitlab", it, it.substringBefore('/'), it.substringAfterLast('/'), app.gitlabDomain?.ifBlank { null } ?: "gitlab.com") }
    path(app.codebergRepo)?.let { return RepoSource("codeberg", it, it.substringBefore('/'), it.substringAfterLast('/')) }
    return app.repoUrl?.let(::parseRepoSource)
}

/** API URL for one release of [app] and the JSON field holding its notes. */
fun releaseNotesApi(app: AppItem, tag: String): Pair<String, String>? {
    if (tag.isBlank()) return null
    val r = appRepo(app) ?: return null; val t = enc(tag)
    return when (r.provider) {
        "github" -> "https://api.github.com/repos/${r.repoPath}/releases/tags/$t" to "body"
        "gitlab" -> "https://${r.domain}/api/v4/projects/${enc(r.repoPath)}/releases/$t" to "description"
        else -> "https://codeberg.org/api/v1/repos/${r.repoPath}/releases/tags/$t" to "body"
    }
}

/** Release notes as plain readable lines. Kept: headings (still marked with #) and bullets. Dropped: HTML, images, badges, code blocks,
 *  tables, link targets and bare URLs, emphasis markers, emoji shortcodes, "by @user in #12" credits, checksums, and GitHub's
 *  Full Changelog / New Contributors / Assets boilerplate. Capped at 40 lines. */
fun cleanNotes(md: String): String {
    val drop = Regex("""(?i)^[#*_\s]*(full changelog|new contributors|assets|checksums?|sha-?256|md5|download|installation)\b.*""")
    return md.replace("\r", "")
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""<details>.*?</details>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
        .replace(MD_LINK, "$1")
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .lines().asSequence().map { it.trim() }
        // A boilerplate heading drops its whole section up to the next heading
        .fold(mutableListOf<String>() to false) { (out, skip), l ->
            val h = l.startsWith("#"); val off = if (h) drop.matches(l) else skip
            if (!off && !(drop.matches(l) && !h)) out += l
            out to off
        }.first.asSequence()
        .filterNot { it.startsWith("|") || it.matches(Regex("[-*_=]{3,}")) || it.matches(Regex("""(?i).*\b[0-9a-f]{32,}\b.*""")) }
        .map { l ->
            l.replace(Regex("""\s+by @[\w-]+( in \S+)?"""), "").replace(Regex("""https?://\S+"""), "").replace(Regex("""(?<!\w):[a-z0-9_+-]+:(?!\w)"""), "")
                .replace(Regex("""(\*\*|__|~~|`)"""), "").replace(Regex("""(?<![\w*])\*(?!\s)([^*]+)\*"""), "$1")
                .replace(Regex("""\(\s*\)"""), "").trimEnd(' ', ':', ',')
        }
        .map { l -> Regex("""^[-*+]\s+(\[[ xX]]\s+)?""").find(l)?.let { "• " + l.substring(it.range.last + 1).trim() } ?: l }
        .filterNot { it == "•" || it.removePrefix("•").isBlank() && it.isNotEmpty() || it.startsWith("#") && it.trimStart('#').trim().let { h -> h.isEmpty() || h.lowercase().matches(Regex("what.?s changed|changelog|changes")) } }
        .toList().joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        .lines().take(40).joinToString("\n")
}
// endregion

// region category guess
/** The catalog's own categories, the ones a submission can pick. */
val SUBMIT_CATEGORIES = listOf("Utility", "Media", "Productivity", "Music", "Customization", "Privacy", "Social", "Development", "Reading", "Games", "Anime", "Health", "Education", "Browsers")

private val STOP = ("the and for with your you that this app apps android from are can all not but has have its into more one use using also any via based open source free " +
    "simple fast lightweight client new best easy made support supports").split(' ').toSet()
private fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in STOP && !it.all(Char::isDigit) }

/** Word counts per category, learned from how the live catalog files its apps. */
class CategoryModel(catalog: List<AppItem>, private val choices: List<String> = SUBMIT_CATEGORIES) {
    private val docs = HashMap<String, Int>(); private val counts = HashMap<String, HashMap<String, Int>>(); private val totals = HashMap<String, Int>()
    private val vocab = HashSet<String>()
    init {
        catalog.forEach { a ->
            val c = choices.firstOrNull { it.equals(a.category, true) } ?: return@forEach
            docs[c] = (docs[c] ?: 0) + 1
            val m = counts.getOrPut(c) { HashMap() }
            words("${a.name} ${a.description}").forEach { w -> m[w] = (m[w] ?: 0) + 1; totals[c] = (totals[c] ?: 0) + 1; vocab += w }
        }
    }

    /** Naive Bayes over the name, description and repo topics (topics count double); null when no word is known to the catalog. */
    fun guess(text: String, topics: List<String> = emptyList()): String? {
        val ws = (words(text) + topics.flatMap { t -> words(t.replace('-', ' ')).let { it + it } }).filter { it in vocab }
        if (ws.isEmpty() || docs.isEmpty()) return null
        val n = docs.values.sum().toDouble(); val v = vocab.size
        return docs.keys.maxByOrNull { c ->
            val m = counts[c].orEmpty(); val t = totals[c] ?: 0
            .5 * kotlin.math.ln(docs.getValue(c) / n) + ws.sumOf { kotlin.math.ln(((m[it] ?: 0) + .5) / (t + .5 * v)) }
        }
    }
}
// endregion

// region cosmetics
/** A launcher icon or dock style. [ads]: lifetime Fuel the Code ads that unlock it, 0 when free. */
data class Cosmetic(val id: String, val label: String, val ads: Int = 0) {
    fun progress(s: Settings) = s.unlockProgress[id] ?: 0
    fun unlocked(s: Settings) = progress(s) >= ads
}

val APP_ICONS = listOf(Cosmetic("default", "Orion"), Cosmetic("midnight", "Midnight"), Cosmetic("sunset", "Sunset"), Cosmetic("ocean", "Ocean"),
    Cosmetic("mint", "Mint"), Cosmetic("neon", "Neon", 50), Cosmetic("gold", "Gold", 100))
val DOCKS = listOf(Cosmetic("pill", "Pill"), Cosmetic("bar", "Classic"), Cosmetic("icons", "Icons"), Cosmetic("labels", "Labeled"),
    Cosmetic("vibrant", "Expressive", 25), Cosmetic("shapes", "Shape shift", 50))
/** Earned once every locked item of the set is unlocked. */
val COSMETIC_BADGES = listOf("Icon Collector" to APP_ICONS.filter { it.ads > 0 }, "Dock Master" to DOCKS.filter { it.ads > 0 })
// endregion

// region install options
/** One `pm install` switch offered before a root or Shizuku install. [flag] null: handled in [installArgs]. [excludes]: the option it can't be combined with. */
data class InstallOption(val id: String, val label: String, val desc: String, val flag: String?, val minSdk: Int = 0, val excludes: String? = null)

val INSTALL_OPTIONS = listOf(
    InstallOption("downgrade", "Allow downgrade", "Install an older version over a newer one, keeping its data", "-d"),
    InstallOption("grant", "Grant all permissions", "Every runtime permission the app asks for, granted on install. Special access like All files stays manual.", "-g"),
    InstallOption("restricted", "Allow restricted settings", "SMS, call log and accessibility work without Android's restricted-settings block", null),
    InstallOption("test", "Allow test builds", "Debug and test-only APKs install too", "-t"),
    InstallOption("lowsdk", "Bypass target SDK block", "Install apps built for old Android versions", "--bypass-low-target-sdk-block", 34),
    InstallOption("forcesdk", "Allow preview SDK builds", "Install apps built against an unreleased Android preview", "--force-sdk"),
    InstallOption("dontkill", "Keep the app running", "Don't stop the app while it updates", "--dont-kill"),
    InstallOption("currentuser", "Only this user", "Skip other profiles like Work, Secure Folder and Dual apps", "--user current"),
    InstallOption("internal", "Internal storage", "Force the app onto internal storage", "--install-location 1", excludes = "external"),
    InstallOption("external", "Prefer SD card", "Put the app on adopted or external storage when there is room", "--install-location 2", excludes = "internal"),
    InstallOption("rollback", "Enable rollback", "Let Android roll this update back later", "--enable-rollback", 29),
    InstallOption("noverify", "Skip package verification", "No Play Protect style verifier check. Signatures are still checked.", "--skip-verification", 31),
    InstallOption("queryable", "Visible to every app", "Other apps can see this app even when Android's package visibility would hide it", "--force-queryable", 30),
    InstallOption("nodexopt", "Ignore dexopt profile", "Skip the shipped startup profile while compiling the app", "--ignore-dexopt-profile", 35),
    InstallOption("instant", "Instant app", "Install as an instant app that runs without a full install", "--instant", 26, excludes = "full"),
    InstallOption("full", "Full app", "Install as a full app, also turning an instant app into a regular one", "--full", 26, excludes = "instant"),
    InstallOption("preload", "Virtual preload", "Mark the app as preloaded, like the ones that ship with the phone", "--preload", 27),
    InstallOption("play", "Show as installed by Play Store", "For apps that refuse to run unless Google Play installed them", "-i com.android.vending", excludes = "installer"),
    InstallOption("installer", "Custom installer", "Report any package as the app's installer", null, excludes = "play"),
)

private val PACKAGE = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")

/** The pm install arguments for [opts] on [sdk]; [owner] is Orion's own installer tag, replaced by an installer spoof.
 *  A custom installer rides along as "installer=<package>". */
fun installArgs(opts: Collection<String>, sdk: Int, owner: String): String {
    val on = INSTALL_OPTIONS.filter { it.id in opts && sdk >= it.minSdk }.map { it.id }.toSet()
    val custom = opts.firstOrNull { it.startsWith("installer=") }?.substringAfter('=')?.takeIf { "installer" in on && it.matches(PACKAGE) }
    return buildString {
        if ("restricted" !in on) append(" --restrict-permissions")
        if (custom != null) append(" -i $custom") else if ("play" !in on) append(owner)
        INSTALL_OPTIONS.filter { it.id in on }.forEach { o -> o.flag?.let { append(" $it") } }
    }
}
// endregion
