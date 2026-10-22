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

fun hasAvailableUpdate(app: AppItem, installed: String?, stream: String = "Stable") = !installed.isNullOrEmpty() && compareVersions(preferredVersion(app, stream), installed) > 0

fun isUpdateIgnored(app: AppItem, s: Settings, now: Long): Boolean {
    val ig = s.ignoredUpdates[app.id] ?: return false
    return when (ig.type) {
        "never" -> true
        "week" -> ig.timestamp != null && now - ig.timestamp < ONE_WEEK_MS
        "version" -> ig.version == preferredVersion(app, s.streamOf(app.id))
        else -> false
    }
}

fun availableUpdates(apps: List<AppItem>, s: Settings, now: Long) = apps.filter { a ->
    val inst = s.installedVersions[a.id]
    !inst.isNullOrEmpty() && hasAvailableUpdate(a, inst, s.streamOf(a.id)) && !isUpdateIgnored(a, s, now)
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
    Preset("music", "音乐", "fa-headphones", "#f472b6", listOf("#ec4899", "#8b5cf6"), emptyList(), listOf("music", "audio", "song", "songs", "album", "albums", "listen", "radio", "deezer", "track", "tracks", "scrobble")),
    Preset("movies", "影视", "fa-film", "#fb923c", listOf("#f97316", "#ef4444"), emptyList(), listOf("movie", "movies", "video", "videos", "watch", "stream", "streaming", "netflix", "prime video", "hulu", "youtube")),
    Preset("anime", "动漫", "fa-dragon", "#a78bfa", listOf("#8b5cf6", "#ec4899"), emptyList(), listOf("anime", "manga", "anilist", "komikku")),
    Preset("health", "健康", "fa-heart-pulse", "#34d399", listOf("#10b981", "#14b8a6"), emptyList(), listOf("health", "fitness", "workout", "sleep", "step", "steps", "habit", "meditation", "wellness")),
    Preset("privacy", "隐私", "fa-user-shield", "#2dd4bf", listOf("#14b8a6", "#06b6d4"), listOf("Privacy"), listOf("privacy", "private", "security", "secure", "encrypt", "encrypted", "authenticator", "2fa", "totp", "backup")),
    Preset("social", "社交", "fa-comments", "#60a5fa", listOf("#3b82f6", "#6366f1"), listOf("Social"), listOf("social", "community", "chat", "friends", "messaging", "anilist")),
    Preset("developer", "开发", "fa-code", "#f59e0b", listOf("#f59e0b", "#f97316"), listOf("Development"), listOf("developer", "development", "code", "coding", "ide", "studio", "terminal", "debug")),
    Preset("reading", "阅读", "fa-book-open", "#22c55e", listOf("#22c55e", "#84cc16"), emptyList(), listOf("reader", "reading", "read", "book", "books", "comic", "manga", "novel")),
    Preset("productivity", "效率", "fa-briefcase", "#06b6d4", listOf("#06b6d4", "#3b82f6"), emptyList(), listOf("productivity", "workflow", "organize", "organise", "notes", "tasks", "todo", "calendar")),
    Preset("utilities", "工具", "fa-toolbox", "#94a3b8", listOf("#475569", "#0f172a"), listOf("Utility", "Utilities"), listOf("utility", "tools", "tool", "wallpaper", "download", "manager", "launcher", "offline")),
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
    val u = runCatching { URI(t) }.getOrNull()?.takeIf { !it.host.isNullOrEmpty() } ?: return "链接不是有效的 URL。"
    val host = u.host.lowercase(); val path = u.rawPath.orEmpty()
    if (host.startsWith("private-user")) return "不接受 private-user 链接，请使用公开的图片链接。"
    val ok = IMAGE_EXT.any { path.lowercase().endsWith(it) } ||
        (host.endsWith("googleusercontent.com") && path.startsWith("/play-lh")) ||
        host == "raw.githubusercontent.com" || host == "camo.githubusercontent.com" ||
        ((host.endsWith("gitlab.com") || host.endsWith("gitlab.io")) && "/-/raw/" in path) ||
        (host == "codeberg.org" && "/raw/branch/" in path)
    return if (ok) null else "仅接受图片链接（jpg/png/webp/gif/bmp/avif/svg、Play 商店抓取链接，或 GitHub/GitLab/Codeberg/Camo 原始链接）。"
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
    ChangeField("name", "应用名称", "显示的标题", "基础", "text"), ChangeField("author", "开发者或作者", "创作者署名", "基础", "text"),
    ChangeField("description", "描述", "商店里的描述", "基础", "textarea"), ChangeField("icon", "图标链接", "直链图片地址", "媒体", "url"),
    ChangeField("screenshots", "截图链接", "每行一个链接", "媒体", "urls"), ChangeField("downloadUrl", "下载或来源链接", "可用的项目主页", "来源", "url"),
    ChangeField("repoUrl", "仓库链接", "主仓库或网站", "来源", "url"), ChangeField("githubRepo", "GitHub 仓库", "owner/repo 或完整链接", "来源", "text"),
    ChangeField("gitlabRepo", "GitLab 项目", "组/项目或完整链接", "来源", "text"), ChangeField("codebergRepo", "Codeberg 仓库", "owner/repo 或完整链接", "来源", "text"),
    ChangeField("officialSite", "官方网站", "产品页面", "来源", "url"), ChangeField("category", "分类", "商店分类", "目录", "text"),
)
fun changeFields(platform: String) = if (platform == Platform.ANDROID) COMMON_FIELDS + listOf(
    ChangeField("releaseKeyword", "发布文件关键词", "APK 文件名中的独特词", "安卓", "text"),
    ChangeField("packageName", "安卓包名", "com.example.app", "安卓", "text"),
) else COMMON_FIELDS

fun currentFieldValue(app: AppItem, key: String): String = when (key) {
    "screenshots" -> app.screenshots.joinToString("\n")
    else -> when (key) {
        "name" -> app.name; "author" -> app.author; "description" -> app.description; "icon" -> app.icon; "downloadUrl" -> app.downloadUrl
        "repoUrl" -> app.repoUrl; "githubRepo" -> app.githubRepo; "gitlabRepo" -> app.gitlabRepo; "codebergRepo" -> app.codebergRepo
        "officialSite" -> app.officialSite; "category" -> app.category; "releaseKeyword" -> app.releaseKeyword; "packageName" -> app.packageName; else -> null
    }?.takeIf { it.isNotEmpty() && it != "#" } ?: "无"
}

fun urlError(v: String): String {
    val t = v.trim()
    if (t.isEmpty()) return "请输入有效的 URL。"
    val u = runCatching { URI(t) }.getOrNull()?.takeIf { it.scheme != null } ?: return "请使用有效的 http 或 https 链接。"
    return if (u.scheme.lowercase() !in setOf("http", "https")) "请使用 http 或 https 链接。" else if (u.host.isNullOrEmpty()) "请使用有效的 http 或 https 链接。" else ""
}

fun splitUrls(v: String) = v.trim().split(Regex("\n|,")).map { it.trim() }.filter { it.isNotEmpty() }

fun validateChange(f: ChangeField, v: String): String {
    val t = v.trim()
    if (t.isEmpty()) return "请输入更正后的值。"
    if (f.kind == "url") return urlError(t)
    if (f.kind == "urls") { val u = splitUrls(t); return if (u.isEmpty()) "请至少添加一个截图链接。" else u.firstOrNull { urlError(it).isNotEmpty() }?.let(::urlError) ?: "" }
    if (f.key == "packageName" && !Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(t)) return "请使用有效的安卓包名。"
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
        "### Reason", reason.trim().ifEmpty { "无更多说明。" }, "",
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
    "inactive" to listOf("待命", "", "", "neutral"),
    "checking" to listOf("检查中", "正在检查 Orion 最新版本", "正在联系 Orion Store 确认最新版本。", "progress"),
    "downloading" to listOf("下载中", "正在下载最新版 Orion", "更新正在后台下载。", "progress"),
    "download_failed" to listOf("需要重试", "下载需要再试一次", "请检查网络后重试下载。", "warning"),
    "ready_to_scan_or_install" to listOf("就绪", "更新已可安装", "查看下一步，准备好后即可安装。", "success"),
    "byok_required" to listOf("扫描设置", "扫描需要 VirusTotal API 密钥", "在设置中填入密钥，即可在安装前扫描。", "warning"),
    "scanning" to listOf("扫描中", "安装前先扫描更新", "安装前用 VirusTotal 校验 APK。", "progress"),
    "scan_complete" to listOf("扫描完成", "更新已可安装", "扫描完成，构建随时可以安装。", "success"),
    "installing" to listOf("安装中", "系统正在接管安装", "继续完成系统安装器中的步骤即可完成更新。", "progress"),
    "install_failed" to listOf("安装失败", "安装需要手动兜底", "把 APK 保存到下载目录，再从文件管理器手动安装。", "warning"),
    "export_ready" to listOf("已保存", "APK 已保存到设备", "从文件管理器打开已保存的 APK 继续安装。", "success"),
    "installed" to listOf("完成", "Orion Store 已是最新", "重新打开 Orion Store 即可使用新版本。", "success"),
)

fun forcedVm(status: String, errorText: String?, statusText: String?, canScan: Boolean, canInstall: Boolean, exported: Boolean, exportPath: String?, scanProgress: Float, stats: VtStats?): ForcedVm {
    val c = FORCED_COPY[status] ?: FORCED_COPY.getValue("inactive")
    val a = buildList {
        if (status == "download_failed") add(ForcedAction("retry", "重试下载", "primary"))
        if (status == "byok_required") add(ForcedAction("install", "跳过扫描直接安装", "secondary"))
        if (status == "ready_to_scan_or_install" && canScan) add(ForcedAction("scan", "安装前扫描", "secondary"))
        if (status == "scan_complete" && stats != null) add(ForcedAction("open-report", "Open VirusTotal report", "secondary"))
        if (status in setOf("ready_to_scan_or_install", "scan_complete", "export_ready") && canInstall) add(ForcedAction("install", "立即安装", "primary"))
        if (status == "install_failed") add(if (exported) ForcedAction("export", "已保存到下载目录", "outline", false) else ForcedAction("export", "保存 APK 到下载目录", "outline"))
    }
    return ForcedVm(
        c[0], c[1], errorText?.ifEmpty { null } ?: statusText?.ifEmpty { null } ?: c[2], c[3], a,
        status in setOf("checking", "downloading", "scanning"), status != "inactive", status in setOf("install_failed", "download_failed", "export_ready"),
        status == "export_ready" && !exportPath.isNullOrEmpty(), scanProgress.coerceIn(0f, 100f), stats?.verdict,
    )
}
// endregion

// region my apps
enum class MyAppsFilter(val label: String, val icon: String) { ALL("全部", "fa-box-archive"), INSTALLED("已安装", "fa-circle-check"), DOWNLOADED("已下载", "fa-download"), UPDATES("更新", "fa-arrow-up") }

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
        "downloading" -> "${Math.round(progress)}%"; "ready" -> "已就绪，可安装"; "update" -> "有可用更新"
        "installed" -> if (isInstalledNow) "已安装 v$installedVersion" else "曾安装过"
        else -> if (hasSavedApk) "APK 已保存" else "已下载"
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
    val status = when { dl -> "downloading"; ready != null -> "ready"; upd -> "update"; now || lib?.status == "installed" -> "installed"; else -> "downloaded" }
    MyAppRow(a, status, inst, s.lastRemoteVersions[a.id], ready, cleanup?.fileName, lib?.fileName, key, saved, upd, now, lib?.status,
        d.downloadProgress[a.id] ?: 0f, lib?.downloadedAt ?: cleanup?.timestamp ?: 0, lib?.installedAt)
}.sortedByDescending { it.downloadedAt }

fun MyAppRow.matches(f: MyAppsFilter) = when (f) {
    MyAppsFilter.ALL -> true; MyAppsFilter.INSTALLED -> status == "installed" || hasUpdate
    MyAppsFilter.DOWNLOADED -> status in setOf("downloading", "ready", "downloaded"); MyAppsFilter.UPDATES -> hasUpdate
}

fun MyAppRow.matchesQuery(q: String) = q.isBlank() || q.trim().lowercase().let { t -> listOfNotNull(app.name, app.author, app.category, app.packageName).any { t in it.lowercase() } }
// endregion

// region modern storefront helpers
fun modernSortTitle(sort: SortOption) = when (sort) {
    SortOption.HOME -> "首页" to "更多为你推荐"; SortOption.OLDEST -> "最早优先" to "更多经典"
    SortOption.NAME_ASC -> "A 到 Z" to "按名称 A-Z 排序"; SortOption.NAME_DESC -> "Z 到 A" to "按名称 Z-A 排序"
    SortOption.SIZE_ASC -> "体积最小" to "更多轻量之选"; SortOption.SIZE_DESC -> "体积最大" to "更多重量级应用"
    else -> "最新添加" to "更多新鲜推荐"
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
/** One name per app version: the store and the background updater find each other's downloads by it. */
fun apkFileName(app: AppItem, version: String) = "${app.name.replace(Regex("[^a-zA-Z0-9]"), "_")}_${sanitizeFileVersionToken(version.ifEmpty { app.latestVersion })}.apk"

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
    val title = "应用投稿（$count 个应用）- ${firstName ?: "新应用"}"
    val body = "### App Submission Request\n\nI would like to submit the following apps to Orion Store.\n\n```json\n$apps\n```\n\n*Generated by Orion Store v$version*"
    return "https://github.com/RookieEnough/Orion-Data/issues/new?title=${enc(title)}&body=${enc(body)}"
}

/** Every XP source in one place: the pilot card, rank badges, submission page and leaderboard all read this. */
object Xp {
    const val AD = 40; const val SUBMISSION = 100; const val APP = 10
    /** [apps] is the library size: each app counts once, so re-downloading the same APK cannot farm XP. */
    fun total(s: Settings, apps: Int) = s.adWatchCount * AD.toLong() + s.submissionCount * SUBMISSION.toLong() + apps * APP.toLong() + s.gameXP
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
    fun title(level: Int) = when { level >= 50 -> "金牌飞行员"; level >= 30 -> "银牌飞行员"; level >= 10 -> "铜牌飞行员"; else -> "见习学员" }
}
/** Pilot card tiers and the badge each one awards. */
val TIER_BADGES = listOf(10 to "青铜之翼", 30 to "白银之翼", 50 to "黄金之翼")

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
