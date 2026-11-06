package com.orion.store.data

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Everything Orion can learn about an app from its repo: name, description, package, author, topics, icon and screenshots.
 *  Shared by app submissions and Orbit. */
data class Detected(val name: String?, val description: String?, val packageName: String?, val author: String?, val topics: List<String> = emptyList(),
    val icon: String? = null, val shots: List<String> = emptyList())

/** Screenshots in the repo's fastlane / F-Droid folder, listed through the host's contents API. */
private suspend fun metadataShots(src: RepoSource, raw: String, branch: String, h: Map<String, String>): List<String> {
    for (dir in SCREENSHOT_DIRS) {
        val names = runCatching {
            val api = when (src.provider) {
                "github" -> "https://api.github.com/repos/${src.owner}/${src.repo}/contents/$dir?ref=${enc(branch)}"
                "gitlab" -> "https://${src.domain}/api/v4/projects/${enc(src.repoPath)}/repository/tree?path=${enc(dir)}&ref=${enc(branch)}&per_page=50"
                else -> "https://codeberg.org/api/v1/repos/${src.owner}/${src.repo}/contents/$dir?ref=${enc(branch)}"
            }
            (json.parseToJsonElement(Net.text(api, h)) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
        }.getOrNull().orEmpty().filter { n -> IMAGE_EXT.any { n.lowercase().endsWith(it) } }.sortedWith(compareBy({ it.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        if (names.isNotEmpty()) return names.take(8).map { "$raw/$dir/$it" }
    }
    return emptyList()
}

/** The first launcher icon the repo really has, probed in parallel; else an icon or logo its README shows. */
private suspend fun repoIcon(raw: String, readme: List<Pair<String, String>>): String? = kotlinx.coroutines.coroutineScope<String?> {
    val probes = ICON_PATHS.map { p -> async { "$raw/$p".takeIf { Net.alive(it, 6000) } } }
    probes.firstNotNullOfOrNull { it.await() } ?: pickReadmeIcon(readme)
}

/** GitLab.com counts every API and raw request against the same IP, and a burst bans the browser too. One call at a time, with a pause, and a quiet minute after a 429. */
object GitlabPace {
    private val gate = Mutex()
    @Volatile private var next = 0L
    @Volatile private var quietUntil = 0L
    private val headers = mapOf("User-Agent" to "OrionStore", "Accept" to "application/json")

    suspend fun text(url: String, extra: Map<String, String> = emptyMap()): String = gate.withLock {
        val now = System.currentTimeMillis()
        if (now < quietUntil) throw HttpError(429, "GitLab asked for a pause")
        val wait = next - now
        if (wait > 0) delay(wait)
        try {
            Net.text(url, headers + extra).also { next = System.currentTimeMillis() + 1500 }
        } catch (e: HttpError) {
            if (e.code == 429) quietUntil = System.currentTimeMillis() + 90_000
            throw e
        }
    }
}

suspend fun detectRepo(src: RepoSource, tokens: HostTokens): Detected {
    val h = when (src.provider) {
        "github" -> if (tokens.github.isNotEmpty()) mapOf("Authorization" to "Bearer ${tokens.github}") else emptyMap()
        "gitlab" -> if (tokens.gitlab.isNotEmpty()) mapOf("PRIVATE-TOKEN" to tokens.gitlab) else emptyMap()
        else -> if (tokens.codeberg.isNotEmpty()) mapOf("Authorization" to "token ${tokens.codeberg}") else emptyMap()
    }
    val api = when (src.provider) {
        "github" -> "https://api.github.com/repos/${src.owner}/${src.repo}"
        "gitlab" -> "https://${src.domain}/api/v4/projects/${enc(src.repoPath)}"
        else -> "https://codeberg.org/api/v1/repos/${src.owner}/${src.repo}"
    }
    // GitLab: the project call already has the name, description and avatar. Walking the tree, README and icon paths is what trips the rate limit.
    if (src.provider == "gitlab") {
        val info = json.parseToJsonElement(GitlabPace.text(api, h)).jsonObject
        val desc = info["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val avatar = info["avatar_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") }
        return Detected(humanizeRepoName(src.repo), desc.ifEmpty { null }, null, src.owner.ifEmpty { null }, emptyList(), avatar, emptyList())
    }
    val info = json.parseToJsonElement(Net.text(api, h)).jsonObject
    val desc = info["description"]?.jsonPrimitive?.contentOrNull.orEmpty(); val branch = info["default_branch"]?.jsonPrimitive?.contentOrNull ?: "main"
    val raw = when (src.provider) {
        "github" -> "https://raw.githubusercontent.com/${src.owner}/${src.repo}/$branch"
        "gitlab" -> "https://${src.domain}/${src.repoPath}/-/raw/$branch"
        else -> "https://codeberg.org/${src.owner}/${src.repo}/raw/branch/$branch"
    }
    val pkg = ANDROID_META_PATHS.firstNotNullOfOrNull { p -> runCatching { Net.text("$raw/$p", h) }.getOrNull()?.let(::extractPackageName) }
    val topics = (info["topics"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    // Icon and screenshots: store metadata first, then what the README shows
    val readme = README_NAMES.firstNotNullOfOrNull { n -> runCatching { Net.text("$raw/$n") }.getOrNull() }?.let { readmeImages(it, raw) }.orEmpty()
    val shots = metadataShots(src, raw, branch, h).ifEmpty { pickScreenshots(readme) }
    val icon = repoIcon(raw, readme)
    return Detected(humanizeRepoName(src.repo), desc.ifEmpty { null }, pkg, src.owner.ifEmpty { null }, topics, icon, shots)
}

private val README_NAMES = listOf("README.md", "readme.md", "Readme.md", "README.MD", "README.markdown", "docs/README.md")
