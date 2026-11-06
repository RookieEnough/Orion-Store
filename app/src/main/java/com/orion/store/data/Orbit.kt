package com.orion.store.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Request
import java.net.URI

/** An app the user keeps in Orbit: tracked straight from its source (a repo, F-Droid, IzzyOnDroid, a web page or an APK link),
 *  installed and updated like any catalog app, on this phone only until they share it with the Orion library. */
@Serializable data class OrbitApp(
    val id: String,
    val url: String,
    val name: String = "", val author: String = "", val icon: String = "", val about: String = "",
    val packageName: String? = null,
    /** Obtainium's apkFilterRegEx: only APK files whose names match. */
    val apkFilter: String = "",
    val includePre: Boolean = false,
    /** Obtainium's versionExtractionRegEx: the version is read from the release tag with it (first group, or the whole match). */
    val versionRegex: String = "",
    val screenshots: List<String> = emptyList(),
    val category: String = "Utility",
    /** Labels the user adds, the same idea as Obtainium's categories. */
    val tags: List<String> = emptyList(),
    val addedAt: Long = 0, val checkedAt: Long = 0, val error: String? = null,
    /** Newest first, every channel: the version picker can install any of them, not just the latest. */
    val versions: List<VersionOption> = emptyList(),
    val paused: Boolean = false,
    val published: Boolean = false,
)

/** Tokens Orbit sends to GitHub, GitLab and Codeberg. GitHub is the token already saved in the app. */
data class HostTokens(val github: String = "", val gitlab: String = "", val codeberg: String = "")

fun Settings.orbitTokens() = HostTokens(githubToken, gitlabToken, codebergToken)

enum class OrbitSource(val label: String) {
    GITHUB("GitHub"), GITLAB("GitLab"), CODEBERG("Codeberg"), FORGEJO("Forgejo"), FDROID("F-Droid"), IZZY("IzzyOnDroid"),
    UPTODOWN("Uptodown"), APKMIRROR("APKMirror"), APKPURE("APKPure"), APTOIDE("Aptoide"), SOURCEFORGE("SourceForge"),
    ITCH("itch.io"), FARSROID("Farsroid"), LITEAPKS("LiteAPKs"), APK4FREE("APK4Free"), COOLAPK("CoolApk"), SOURCEHUT("SourceHut"),
    JENKINS("Jenkins"), TELEGRAM("Telegram"), DISCORD("Discord"), APK("APK link"), WEB("Web page")
}

/** A ready-made config from the Obtainium apps directory. */
data class DirectoryApp(val name: String, val author: String, val icon: String, val about: String, val categories: List<String>, val configs: List<OrbitApp>)

object Orbit {
    const val PREFIX = "orbit:"
    fun owns(id: String?) = id?.startsWith(PREFIX) == true
    private val FDROID = Regex("""f-droid\.org/(?:[a-z_-]+/)?packages/([A-Za-z0-9._]+)""")
    private val IZZY = Regex("""apt\.izzysoft\.de/fdroid/index/apk/([A-Za-z0-9._]+)""")
    private val ANY_PKG = Regex("""/packages/([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/?""")
    private val PKG = Regex("""\.(?:apks|xapk|apkm|apk)$""")
    private val JOB = Regex("""https?://[^/\s]+(?:/[^/\s]+)*/job/[^/\s]+""", RegexOption.IGNORE_CASE)

    fun sourceOf(url: String): OrbitSource {
        val u = url.trim().lowercase(); val host = runCatching { URI(url.trim()).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            FDROID.containsMatchIn(u) -> OrbitSource.FDROID
            IZZY.containsMatchIn(u) -> OrbitSource.IZZY
            host.endsWith("uptodown.com") -> OrbitSource.UPTODOWN
            host.endsWith("apkmirror.com") -> OrbitSource.APKMIRROR
            host.endsWith("apkpure.com") || host.endsWith("apkpure.net") -> OrbitSource.APKPURE
            host.endsWith("aptoide.com") -> OrbitSource.APTOIDE
            host.endsWith("sourceforge.net") -> OrbitSource.SOURCEFORGE
            host.endsWith("itch.io") -> OrbitSource.ITCH
            host.endsWith("farsroid.com") -> OrbitSource.FARSROID
            host.endsWith("liteapks.com") -> OrbitSource.LITEAPKS
            host.endsWith("apk4free.net") || host.endsWith("apk4free.com") || host.endsWith("ap4free.com") -> OrbitSource.APK4FREE
            host.endsWith("coolapk.com") -> OrbitSource.COOLAPK
            host == "sr.ht" || host.endsWith(".sr.ht") -> OrbitSource.SOURCEHUT
            host == "telegram.org" || host == "www.telegram.org" || host == "t.me" || host == "telegram.me" -> OrbitSource.TELEGRAM
            host == "discord.com" || host == "www.discord.com" || host == "discordapp.com" || host.endsWith("discordapp.com") || host.endsWith("discordapp.net") -> OrbitSource.DISCORD
            JOB.containsMatchIn(url) -> OrbitSource.JENKINS
            PKG.containsMatchIn(u.substringBefore('?').substringBefore('#')) -> OrbitSource.APK
            host.endsWith("github.com") && repoPath(url) != null -> OrbitSource.GITHUB
            "gitlab" in host && repoPath(url) != null -> OrbitSource.GITLAB
            host == "codeberg.org" && repoPath(url) != null -> OrbitSource.CODEBERG
            ANY_PKG.containsMatchIn(u) -> OrbitSource.FDROID
            else -> OrbitSource.WEB
        }
    }

    private fun repoPath(url: String) = parseRepoSource(url)

    /** Stable id for a source, so adding the same link twice finds the existing entry. */
    fun idFor(url: String): String = PREFIX + url.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/').removeSuffix(".git")
        .replace(Regex("[^a-z0-9._/-]"), "").take(120)

    private fun bearer(token: String) = if (token.isNotEmpty()) mapOf("Authorization" to "Bearer $token") else emptyMap()
    private fun codebergAuth(token: String) = if (token.isNotEmpty()) mapOf("Authorization" to "token $token") else emptyMap()
    private fun gitlabAuth(token: String) = if (token.isNotEmpty()) mapOf("PRIVATE-TOKEN" to token) else emptyMap()

    // region releases, per source
    private suspend fun releases(o: OrbitApp, tokens: HostTokens): List<CatalogProcessor.Release> = when (sourceOf(o.url)) {
        OrbitSource.GITHUB -> repoPath(o.url)!!.let { gitReleases(json.parseToJsonElement(Net.text("https://api.github.com/repos/${it.repoPath}/releases?per_page=30", bearer(tokens.github)))) }
        OrbitSource.CODEBERG, OrbitSource.FORGEJO -> repoPath(o.url)!!.let { gitReleases(json.parseToJsonElement(Net.text("https://codeberg.org/api/v1/repos/${it.repoPath}/releases?limit=30", codebergAuth(tokens.codeberg)))) }
        OrbitSource.GITLAB -> repoPath(o.url)!!.let { r -> gitlab(json.parseToJsonElement(GitlabPace.text("https://${r.domain}/api/v4/projects/${enc(r.repoPath)}/releases?per_page=10", gitlabAuth(tokens.gitlab)))) }
        OrbitSource.FDROID -> {
            val official = FDROID.find(o.url)
            if (official != null) fdroid("https://f-droid.org", official.groupValues[1])
            else { val uri = URI(o.url.trim()); fdroid(uri.scheme + "://" + uri.host, ANY_PKG.find(uri.path.orEmpty())!!.groupValues[1]) }
        }
        OrbitSource.IZZY -> fdroid("https://apt.izzysoft.de/fdroid", IZZY.find(o.url)!!.groupValues[1])
        OrbitSource.UPTODOWN -> uptodown(o.url)
        OrbitSource.APKMIRROR -> apkmirror(o.url)
        OrbitSource.APKPURE -> apkpure(o.url)
        OrbitSource.APTOIDE -> aptoide(o.url)
        OrbitSource.SOURCEFORGE -> sourceforge(o.url)
        OrbitSource.ITCH -> itch(o.url)
        OrbitSource.FARSROID, OrbitSource.LITEAPKS, OrbitSource.APK4FREE, OrbitSource.COOLAPK -> page(o.url)
        OrbitSource.SOURCEHUT -> sourcehut(o.url)
        OrbitSource.JENKINS -> jenkins(o.url)
        OrbitSource.TELEGRAM -> telegram(o.url)
        OrbitSource.DISCORD -> discord(o.url)
        OrbitSource.APK -> listOf(direct(o.url))
        OrbitSource.WEB -> page(o.url)
    }

    private fun JsonObject.str(k: String) = (get(k) as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** GitLab keeps release files as links, often pointing at the package registry or job artifacts. */
    private fun gitlab(root: JsonElement) = (root as? JsonArray).orEmpty().mapNotNull { r ->
        val o = r as? JsonObject ?: return@mapNotNull null
        val links = ((o["assets"] as? JsonObject)?.get("links") as? JsonArray).orEmpty().mapNotNull { l ->
            val lo = l as? JsonObject ?: return@mapNotNull null
            val u = lo.str("direct_asset_url").ifEmpty { lo.str("url") }
            Triple(lo.str("name"), u, null as Long?).takeIf { pkgFile(it.first) || pkgFile(u) }
        }.ifEmpty { bodyFiles(o.str("description")) }
        if (links.isEmpty()) null else CatalogProcessor.Release(o.str("tag_name"), o.str("name"), (o["upcoming_release"] as? JsonPrimitive)?.booleanOrNull == true, o.str("released_at"), links)
    }

    /** F-Droid style repos (F-Droid, IzzyOnDroid): every published build, newest first, straight from the repo's package API.
     *  The public list often omits the file name and size, so the APK is `{package}_{versionCode}.apk` and the size of the build Orion shows is read from the file. */
    private suspend fun fdroid(base: String, pkg: String): List<CatalogProcessor.Release> {
        val o = json.parseToJsonElement(Net.text("$base/api/v1/packages/$pkg", mapOf("Accept" to "application/json"), c = Net.browser)) as JsonObject
        val suggested = (o["suggestedVersionCode"] as? JsonPrimitive)?.longOrNull
        val built = (o["packages"] as? JsonArray).orEmpty().mapNotNull { p ->
            val po = p as? JsonObject ?: return@mapNotNull null
            val vc = (po["versionCode"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val apk = po.str("apkName").ifEmpty { "${pkg}_$vc.apk" }
            val bytes = (po["size"] as? JsonPrimitive)?.content?.toLongOrNull()?.takeIf { it > 0 }
            // Builds above the suggested one are F-Droid's unstable channel
            CatalogProcessor.Release(po.str("versionName"), po.str("versionName"), suggested != null && vc > suggested, "", listOf(Triple(apk, "$base/repo/$apk", bytes)))
        }
        val show = built.indexOfFirst { !it.prerelease }.let { if (it >= 0) it else 0 }
        val asset = built.getOrNull(show)?.assets?.firstOrNull()
        if (asset == null || asset.third != null) return built
        val len = runCatching { Net.length(asset.second) }.getOrNull() ?: return built
        return built.mapIndexed { i, r ->
            if (i != show) r else CatalogProcessor.Release(r.tag, r.name, r.prerelease, r.date, listOf(Triple(asset.first, asset.second, len)) + r.assets.drop(1))
        }
    }

    /** A plain APK link: the file is the release, versioned by its name or by when the server last changed it. */
    private suspend fun direct(url: String) = withContext(Dispatchers.IO) {
        val (mod, headLen) = runCatching {
            Net.browser.newCall(Request.Builder().url(url).head().build()).execute().use { (it.header("Last-Modified") ?: it.header("ETag").orEmpty()) to it.header("Content-Length")?.toLongOrNull()?.takeIf { n -> n > 0 } }
        }.getOrDefault("" to null)
        val len = headLen ?: runCatching { Net.length(url) }.getOrNull()
        val file = url.substringBefore('?').substringAfterLast('/')
        val ver = CatalogProcessor.extractVersion(file) ?: mod.let { m -> runCatching { java.time.ZonedDateTime.parse(m, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate().toString().replace('-', '.') }.getOrNull() }
            ?: mod.filter(Char::isLetterOrDigit).take(10).ifEmpty { "latest" }
        CatalogProcessor.Release(ver, file, false, "", listOf(Triple(file, url, len)))
    }

    private val HREF = Regex("""href\s*=\s*["']([^"']+?\.(?:apks|xapk|apkm|apk)(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE)
    private val ANY_HREF = Regex("""href\s*=\s*["']([^"'#]+)["']""", RegexOption.IGNORE_CASE)
    private val FILE_URL = Regex("""https?://[^\s"'<>]+?\.(?:apks|xapk|apkm|apk)(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
    private val HOP = Regex("""download|apk|release|artifact|files""", RegexOption.IGNORE_CASE)

    /** Package links written in a release note, when the release has no uploaded file. */
    private fun bodyFiles(text: String) = FILE_URL.findAll(text).map { it.value.trimEnd('.', ',', ')') }.distinct().take(8).map { u ->
        Triple(u.substringBefore('?').substringAfterLast('/'), u, null as Long?)
    }.toList()

    /** GitHub and Codeberg releases. An APK linked from the release note counts when nothing was uploaded as an asset. */
    private fun gitReleases(root: JsonElement): List<CatalogProcessor.Release> {
        val arr = root as? JsonArray ?: return CatalogProcessor.releasesFrom(root)
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val have = CatalogProcessor.releasesFrom(JsonArray(listOf(o)))
            if (have.isNotEmpty()) have.first()
            else bodyFiles(o.str("body")).takeIf { it.isNotEmpty() }?.let { files ->
                CatalogProcessor.Release(o.str("tag_name"), o.str("name"), (o["prerelease"] as? JsonPrimitive)?.booleanOrNull == true, o.str("published_at").ifEmpty { o.str("created_at") }, files)
            }
        }
    }

    /** Any web page that links to APKs. If the page only links to a download step, that step is read too. */
    private suspend fun page(url: String): List<CatalogProcessor.Release> {
        val links = linkedPackages(url, hop = true)
        val releases = ArrayList<CatalogProcessor.Release>(links.size)
        for (u in links) {
            val f = u.substringBefore('?').substringAfterLast('/')
            val len = runCatching { Net.length(u) }.getOrNull()
            releases += CatalogProcessor.Release(CatalogProcessor.extractVersion(f) ?: f, f, false, "", listOf(Triple(f, u, len)))
        }
        return releases.sortedWith { a, b -> compareVersions(b.tag, a.tag) }
    }

    private suspend fun linkedPackages(url: String, hop: Boolean): List<String> {
        val html = Net.text(url, c = Net.browser); val base = URI(url)
        fun abs(h: String) = runCatching { base.resolve(h.replace("&amp;", "&")).toString() }.getOrNull()
        val direct = (HREF.findAll(html).map { it.groupValues[1] }.mapNotNull(::abs) + FILE_URL.findAll(html).map { it.value }).distinct().filter(::pkgFile).take(8).toList()
        if (direct.isNotEmpty() || !hop) return direct
        val next = ANY_HREF.findAll(html).map { it.groupValues[1] }.mapNotNull(::abs).distinct().filter { link ->
            val uri = runCatching { URI(link) }.getOrNull() ?: return@filter false
            uri.host.equals(base.host, true) && HOP.containsMatchIn(uri.path.orEmpty()) && !pkgFile(link)
        }.take(4).toList()
        return next.flatMap { runCatching { linkedPackages(it, hop = false) }.getOrDefault(emptyList()) }.distinct().take(8)
    }

    /** "31 MB" and the same shapes the stores print next to a file. */
    fun humanBytes(text: String): Long? {
        val m = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*(bytes|[KMGT]?B)\b""", RegexOption.IGNORE_CASE).find(text) ?: return null
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val mul = when (m.groupValues[2].uppercase()) { "TB" -> 1L shl 40; "GB" -> 1L shl 30; "MB" -> 1L shl 20; "KB" -> 1L shl 10; else -> 1L }
        return (n * mul).toLong().takeIf { it > 0 }
    }

    /** One itch.io upload row: file name, upload id, and the size printed on the page. The download button is not a link. */
    fun itchUploads(html: String): List<Triple<String, String, Long?>> {
        return Regex("""class="upload"""").split(html).drop(1).mapNotNull { block ->
            val id = Regex("""data-upload_id\s*=\s*["'](\d+)["']""").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            val raw = Regex("""<strong[^>]*title\s*=\s*["']([^"']+)["']""").find(block)?.groupValues?.get(1)
                ?: Regex("""class="name"[^>]*>([^<]+)""").find(block)?.groupValues?.get(1)?.trim().orEmpty()
            if (raw.isEmpty()) return@mapNotNull null
            val android = "icon-android" in block
            if (!pkgFile(raw) && !android) return@mapNotNull null
            val name = if (pkgFile(raw)) raw else "$raw.apk"
            val size = Regex("""class="file_size"[\s\S]{0,120}?([0-9]+(?:[.,][0-9]+)?\s*[KMGT]?B)""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.let(::humanBytes)
            Triple(name, id, size)
        }
    }

    private fun itchGame(url: String): String {
        val uri = URI(url.trim())
        val game = uri.path.orEmpty().trim('/').substringBefore('/')
        return "${uri.scheme}://${uri.host}/$game"
    }

    /** itch.io answers 429 until the visitor has a cookie from the front page. */
    private suspend fun itchHtml(url: String): String = runCatching { Net.text(url, c = Net.browser) }.getOrElse { e ->
        if (e !is HttpError || e.code != 429) throw e
        runCatching { Net.text("https://itch.io/", c = Net.browser) }
        Net.text(url, c = Net.browser)
    }

    private suspend fun itch(url: String): List<CatalogProcessor.Release> {
        val game = itchGame(url)
        val rows = itchUploads(itchHtml(game))
        return rows.map { (name, id, size) ->
            val ver = CatalogProcessor.extractVersion(name) ?: "latest"
            CatalogProcessor.Release(ver, name, false, "", listOf(Triple(name, "$game/file/$id", size)))
        }
    }

    /** The file link itch.io returns only lasts about a minute, so it is fetched when the download starts. */
    private suspend fun itchReal(fileUrl: String): String {
        val game = fileUrl.substringBefore("/file/")
        val html = itchHtml(game)
        val csrf = Regex("""name="csrf_token"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1) ?: throw HttpError(403, "no token")
        val (code, body) = Net.postForm(fileUrl, "csrf_token=${enc(csrf)}", mapOf(
            "X-Requested-With" to "XMLHttpRequest", "Referer" to game, "Accept" to "application/json", "Origin" to "https://${URI(game).host}"))
        if (code != 200) throw HttpError(code, body.take(180))
        return (json.parseToJsonElement(body) as? JsonObject)?.str("url")?.takeIf { it.startsWith("http") } ?: throw HttpError(502, body.take(180))
    }

    /** SourceHut keeps release files on the refs page, or linked from a build. */
    private suspend fun sourcehut(url: String): List<CatalogProcessor.Release> {
        val root = url.trim().substringBefore('?').trimEnd('/')
        val refs = if ("/refs" in root || "/job/" in root) root else "$root/refs"
        val found = runCatching { page(refs) }.getOrDefault(emptyList())
        return found.ifEmpty { runCatching { page(root) }.getOrDefault(emptyList()) }
    }

    /** A path piece that looks like a package id. APKPure puts it after the app name, and sometimes after a two-letter language. */
    fun apkPurePackage(url: String) = runCatching { URI(url.trim()).path }.getOrNull().orEmpty().trim('/').split('/')
        .filter { it.isNotEmpty() }.lastOrNull { Regex("""^[A-Za-z][\w]*(\.[A-Za-z0-9_]+)+$""").matches(it) }

    private fun JsonElement.apkPureRows(): List<JsonObject> = when (this) {
        is JsonObject -> if (this["asset"] is JsonObject && str("version_name").isNotEmpty()) listOf(this) else values.flatMap { it.apkPureRows() }
        is JsonArray -> flatMap { it.apkPureRows() }
        else -> emptyList()
    }

    private suspend fun apkpure(url: String): List<CatalogProcessor.Release> {
        val pkg = apkPurePackage(url) ?: apkPureFromPage(url) ?: return emptyList()
        val body = Net.text("https://tapi.pureapk.com/v3/get_app_his_version?package_name=${enc(pkg)}&hl=en", mapOf(
            "Accept" to "application/json", "Ual-Access-Businessid" to "projecta", "Ual-Access-ProjectA" to """{"device_info":{"os_ver":"34"}}"""), c = Net.browser)
        val list = ((json.parseToJsonElement(body) as? JsonObject)?.get("version_list") as? JsonArray).orEmpty().ifEmpty { JsonArray(json.parseToJsonElement(body).apkPureRows()) }
        val byVer = LinkedHashMap<String, MutableList<Triple<String, String, Long?>>>()
        for (el in list) {
            val row = el as? JsonObject ?: continue
            val ver = row.str("version_name"); if (ver.isEmpty() || byVer.size >= 8 && ver !in byVer) continue
            val asset = row["asset"] as? JsonObject ?: continue
            val u = asset.str("url"); if (!u.startsWith("http")) continue
            val kind = asset.str("type").lowercase()
            val ext = when { "xapk" in kind -> "xapk"; "apks" in kind -> "apks"; else -> "apk" }
            val bytes = asset.str("size").toLongOrNull()?.takeIf { it > 0 }
            byVer.getOrPut(ver) { ArrayList() }.add(Triple("$pkg-${row.str("version_code").ifEmpty { "0" }}.$ext", u, bytes))
        }
        return byVer.map { (ver, files) -> CatalogProcessor.Release(ver, ver, false, "", files.distinctBy { it.second }.take(4)) }
    }

    private suspend fun apkPureFromPage(url: String) = runCatching {
        val html = Net.text(url, c = Net.browser)
        Regex("""(?:package_name|packageName)["'\s:=]+([A-Za-z][\w]*(?:\.[A-Za-z0-9_]+)+)""").find(html)?.groupValues?.get(1)
    }.getOrNull()

    private suspend fun aptoide(url: String): List<CatalogProcessor.Release> {
        val html = Net.text(url, c = Net.browser)
        val id = Regex(""""app"\s*:\s*\{\s*"id"\s*:\s*(\d+)""").find(html)?.groupValues?.get(1) ?: return emptyList()
        val root = json.parseToJsonElement(Net.text("https://ws2.aptoide.com/api/7/getApp/app_id/$id", c = Net.browser)) as? JsonObject ?: return emptyList()
        val data = ((root["nodes"] as? JsonObject)?.get("meta") as? JsonObject)?.get("data") as? JsonObject ?: return emptyList()
        val file = data["file"] as? JsonObject ?: return emptyList()
        val ver = file.str("vername").ifEmpty { return emptyList() }
        val path = file.str("path").ifEmpty { return emptyList() }
        val name = data.str("package").ifEmpty { "app" } + ".apk"
        return listOf(CatalogProcessor.Release(ver, ver, false, data.str("updated"), listOf(Triple(name, path, (file["filesize"] as? JsonPrimitive)?.longOrNull))))
    }

    private suspend fun sourceforge(url: String): List<CatalogProcessor.Release> {
        val project = Regex("""/projects/([^/?#]+)""").find(url)?.groupValues?.get(1) ?: return emptyList()
        val rss = Net.text("https://sourceforge.net/projects/$project/rss?path=/", mapOf("Accept" to "application/rss+xml, application/xml, text/xml"), c = Net.browser)
        // The feed's link is the file page, ending in /download. The real name is the piece before that.
        val guids = Regex("""<guid[^>]*>([^<]+)""").findAll(rss).map { it.groupValues[1].trim().replace("&amp;", "&") }.filter(::pkgFile).distinct().take(12).toList()
        return guids.groupBy { u ->
            val segs = u.substringBefore('?').trimEnd('/').split('/').filter { it.isNotEmpty() }
            val fileAt = segs.indexOfLast { pkgFile(it) }
            segs.getOrNull(fileAt - 1).orEmpty().ifEmpty { segs.getOrNull(fileAt).orEmpty() }
        }.entries.take(8).map { (ver, urls) ->
            CatalogProcessor.Release(ver, ver, false, "", urls.map { u -> Triple(sfName(u), u, null) })
        }
    }

    private fun sfName(url: String): String {
        val segs = url.substringBefore('?').trimEnd('/').split('/').filter { it.isNotEmpty() }
        return segs.lastOrNull { pkgFile(it) } ?: segs.lastOrNull().orEmpty()
    }

    private suspend fun jenkins(url: String): List<CatalogProcessor.Release> {
        val m = JOB.find(url) ?: return emptyList()
        val job = m.value.trimEnd('/')
        val root = json.parseToJsonElement(Net.text("$job/lastSuccessfulBuild/api/json", c = Net.browser)) as? JsonObject ?: return emptyList()
        val num = (root["number"] as? JsonPrimitive)?.contentOrNull ?: return emptyList()
        val files = (root["artifacts"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val rel = o.str("relativePath"); val name = o.str("fileName").ifEmpty { rel.substringAfterLast('/') }
            if (rel.isEmpty() || !pkgFile(name)) null else Triple(name, "$job/lastSuccessfulBuild/artifact/$rel", null as Long?)
        }
        val label = job.substringAfterLast('/')
        return if (files.isEmpty()) emptyList() else listOf(CatalogProcessor.Release(num, label, false, "", files))
    }

    /** Official Telegram, or the APK files posted in a public channel (t.me/name). */
    private suspend fun telegram(url: String): List<CatalogProcessor.Release> {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        val host = uri?.host.orEmpty().lowercase()
        val segs = uri?.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
        val channel = if (host == "t.me" || host == "telegram.me") (if (segs.firstOrNull() == "s") segs.getOrNull(1) else segs.firstOrNull()) else null
        if (channel.isNullOrBlank() || channel in setOf("android", "dl", "share", "s")) {
            val len = runCatching { Net.length("https://telegram.org/dl/android/apk") }.getOrNull()
            return listOf(CatalogProcessor.Release("latest", "Telegram.apk", false, "", listOf(Triple("Telegram.apk", "https://telegram.org/dl/android/apk", len))))
        }
        val html = Net.text("https://t.me/s/$channel", c = Net.browser)
        var files = channelFiles(html)
        if (files.none { pkgFile(it.first) }) {
            val pages = files.map { it.first }.filter { it.startsWith("https://t.me/") }.distinct().take(4)
            files = pages.flatMap { link -> runCatching { channelFiles(Net.text(link, c = Net.browser)) }.getOrDefault(emptyList()) }
        }
        val apks = files.filter { pkgFile(it.first) || pkgFile(it.second) }.distinctBy { it.first }.take(8)
        if (apks.isEmpty()) return emptyList()
        return apks.map { (u, name) ->
            val ver = CatalogProcessor.extractVersion(name) ?: CatalogProcessor.extractVersion(u) ?: "latest"
            CatalogProcessor.Release(ver, name, false, "", listOf(Triple(name, u, null)))
        }
    }

    /** APK links in a Telegram preview: a real file URL, or the message that holds a document whose name ends in apk. */
    fun channelFiles(html: String): List<Pair<String, String>> {
        val direct = FILE_URL.findAll(html).map { it.value.trimEnd('.', ',', ')') }.distinct().map { u -> u to u.substringBefore('?').substringAfterLast('/') }.toList()
        if (direct.isNotEmpty()) return direct.take(8)
        return Regex("""href="(https://t\.me/[^"]+)"[\s\S]{0,500}?tgme_widget_message_document_title[^>]*>([^<]+)""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues[1] to it.groupValues[2].trim() }.filter { pkgFile(it.second) }.distinctBy { it.first }.take(8).toList()
    }

    /** Discord does not publish an APK on its own site. An attachment link is the file. discord.com itself uses the APKMirror listing. */
    private suspend fun discord(url: String): List<CatalogProcessor.Release> {
        if (pkgFile(url)) return listOf(direct(url))
        val html = runCatching { Net.text(url, c = Net.browser) }.getOrNull().orEmpty()
        val files = FILE_URL.findAll(html).map { it.value.trimEnd('.', ',', ')') }.distinct().take(8).toList()
        if (files.isNotEmpty()) return files.map { u ->
            val name = u.substringBefore('?').substringAfterLast('/')
            CatalogProcessor.Release(CatalogProcessor.extractVersion(name) ?: "latest", name, false, "", listOf(Triple(name, u, null)))
        }
        val host = runCatching { URI(url.trim()).host.orEmpty().lowercase() }.getOrDefault("")
        return if (host == "discord.com" || host == "www.discord.com") apkmirror("https://www.apkmirror.com/apk/discord-inc/discord-chat-for-gamers/") else emptyList()
    }

    private fun pkgFile(name: String): Boolean {
        var path = name.substringBefore('?').substringBefore('#').lowercase().trimEnd('/')
        if (path.endsWith("/download")) path = path.removeSuffix("/download")
        return PKG.containsMatchIn(path.substringAfterLast('/'))
    }

    // region Uptodown and APKMirror
    private const val UP_UA = "Dalvik/2.1.0 (Linux; U; Android 16; Pixel 8 Pro Build/BP4A.260205.001)"
    private const val UP_KEY = "MDGMXUMdvHJBG/vjdFgmqX6LUdy7ecfwvYNd0gyfOCs="
    @Volatile private var upToken: String? = null
    @Volatile private var upExp = 0L

    private fun attr(tag: String, name: String) = Regex("""$name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
    private fun tagById(html: String, id: String) = Regex("""<[^>]*id\s*=\s*["']$id["'][^>]*>""", RegexOption.IGNORE_CASE).find(html)?.value

    /** Package name from an Uptodown "Technical information" table. */
    fun uptodownPackage(html: String) = Regex("""<th[^>]*>\s*package name\s*</th>\s*<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()?.takeIf { '.' in it }

    private fun upPage(url: String): String {
        val host = runCatching { URI(url.trim()).host.orEmpty().lowercase() }.getOrDefault("")
        val slug = host.removeSuffix(".uptodown.com").removeSuffix(".en").substringBefore('.')
        return "https://$slug.en.uptodown.com/android/download"
    }

    /** Uptodown's download button is not an APK link. The file id on the page, plus their app API, is. */
    private suspend fun uptodown(url: String): List<CatalogProcessor.Release> {
        val page = upPage(url)
        val html = Net.text(page, c = Net.browser)
        val ver = Regex("""<div[^>]*class\s*=\s*["'][^"']*\bversion\b[^"']*["'][^>]*>\s*([^<]+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()?.substringBefore(" ") ?: "latest"
        val fileId = tagById(html, "detail-download-button")?.let { attr(it, "data-file-id") }
            ?: tagById(html, "detail-app-name")?.let { attr(it, "data-file-id") }
            ?: Regex("""data-file-id\s*=\s*["'](\d+)["']""").find(html)?.groupValues?.get(1)
            ?: return emptyList()
        val ext = (Regex("""<th[^>]*>\s*file type\s*</th>\s*<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")?.trim() ?: "apk").lowercase().let { if (it == "xapk") "xapk" else "apk" }
        val filePage = "$page/$fileId-x"
        val len = humanBytes(Regex("""class="size"[^>]*>\s*([^<]+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1).orEmpty())
            ?: humanBytes(Regex("""<th[^>]*>\s*Size\s*</th>\s*<td[^>]*>\s*([^<]+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1).orEmpty())
        return listOf(CatalogProcessor.Release(ver, "$fileId.$ext", false, "", listOf(Triple("$fileId.$ext", filePage, len))))
    }

    private suspend fun uptodownToken(): String {
        val now = System.currentTimeMillis() / 1000
        upToken?.takeIf { now < upExp - 60 }?.let { return it }
        val id = ByteArray(8).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val ts = now.toString()
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(UP_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sig = mac.doFinal(ts.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val (code, text) = Net.postForm("https://www.uptodown.app/eapi/auth/token?identifier=$id",
            "identifier=$id&id_plataforma=13&lang=en&unixtime=$ts&hmac=$sig",
            mapOf("User-Agent" to UP_UA, "Identificador" to "Uptodown_Android", "Identificador-Version" to "739"))
        if (code != 200) throw HttpError(code, text)
        val token = (json.parseToJsonElement(text) as JsonObject).str("token")
        val payload = token.split('.').getOrNull(1).orEmpty()
        val exp = runCatching {
            val pad = payload.replace('-', '+').replace('_', '/') + "=".repeat((4 - payload.length % 4) % 4)
            val body = String(android.util.Base64.decode(pad, android.util.Base64.DEFAULT))
            ((json.parseToJsonElement(body) as JsonObject)["exp"] as? JsonPrimitive)?.longOrNull
        }.getOrNull() ?: now + 3600
        upToken = token; upExp = exp
        return token
    }

    private suspend fun uptodownReal(filePage: String): String {
        val html = Net.text(filePage, c = Net.browser)
        val button = tagById(html, "detail-download-button").orEmpty()
        val heading = tagById(html, "detail-app-name").orEmpty()
        val appId = attr(button, "data-app-id") ?: attr(heading, "data-code") ?: throw HttpError(404, "no app")
        val fileId = attr(button, "data-file-id") ?: attr(heading, "data-file-id") ?: throw HttpError(404, "no file")
        val body = Net.text("https://www.uptodown.app/eapi/apps/$appId/file/$fileId/downloadUrl", mapOf(
            "User-Agent" to UP_UA, "Identificador" to "Uptodown_Android", "Identificador-Version" to "739", "Authorization" to "Bearer ${uptodownToken()}"
        ), c = Net.browser)
        val data = (json.parseToJsonElement(body) as? JsonObject)?.get("data") as? JsonObject
        return data?.str("downloadURL")?.takeIf { it.startsWith("http") } ?: throw HttpError(502, body.take(180))
    }

    private fun apkmirrorApp(url: String): String {
        val parts = runCatching { URI(url.trim()).path }.getOrDefault("").split('/').filter { it.isNotEmpty() }
        val i = parts.indexOf("apk")
        val keep = if (i >= 0 && parts.size >= i + 3) parts.subList(0, i + 3) else parts.take(3)
        return "https://www.apkmirror.com/${keep.joinToString("/")}/"
    }

    /** Links to an APKMirror release's download pages. */
    fun apkMirrorDownloadPages(html: String, releaseUrl: String): List<String> {
        val base = runCatching { URI(releaseUrl) }.getOrNull() ?: return emptyList()
        return Regex("""href\s*=\s*["']([^"']+-apk-download/?)["']""", RegexOption.IGNORE_CASE).findAll(html).mapNotNull { m ->
            runCatching { base.resolve(m.groupValues[1].replace("&amp;", "&")).toString() }.getOrNull()
        }.distinct().toList()
    }

    fun apkMirrorSize(text: String): Long? {
        val m = Regex("""File size:[\s\S]{0,160}?([0-9][0-9.]*)\s*(B|KB|MB|GB)\b""", RegexOption.IGNORE_CASE).find(text) ?: return null
        return humanBytes("${m.groupValues[1]} ${m.groupValues[2]}")
    }

    /** The app page lists each version and its size in separate tags, before any release page is opened. */
    fun apkMirrorVersionSizes(html: String): Map<String, Long> {
        val re = Regex("""infoSlide-name">Version:</span>\s*<span class="infoSlide-value">\s*([^<]+)</span>[\s\S]{0,600}?File size:</span>\s*<span class="infoSlide-value">\s*([0-9][0-9.]*)\s*(B|KB|MB|GB)""", RegexOption.IGNORE_CASE)
        return re.findAll(html).mapNotNull { m ->
            val bytes = humanBytes("${m.groupValues[2]} ${m.groupValues[3]}") ?: return@mapNotNull null
            m.groupValues[1].trim() to bytes
        }.toMap()
    }

    /** APKMirror's HTML answers 403 to a bare client. The RSS feed, with a browser identity, is the listing. */
    private suspend fun apkmirror(url: String): List<CatalogProcessor.Release> {
        val appPage = apkmirrorApp(url)
        val listed = runCatching { apkMirrorVersionSizes(Net.text(appPage, c = Net.browser)) }.getOrDefault(emptyMap())
        val feed = Net.text("${appPage}feed/", c = Net.browser)
        val items = Regex("(?s)<item>(.*?)</item>").findAll(feed).map { it.groupValues[1] }.take(5).toList()
        return items.mapNotNull { block ->
            val title = Regex("<title>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:]]>)?</title>").find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val link = Regex("<link>([^<]+)</link>").find(block)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            val ver = CatalogProcessor.extractVersion(title) ?: title.substringBefore(" by ").trim().ifBlank { return@mapNotNull null }
            val html = runCatching { Net.text(link, c = Net.browser) }.getOrNull()
            val pages = html?.let { apkMirrorDownloadPages(it, link) }.orEmpty()
            val size = html?.let { apkMirrorSize(it) } ?: listed[ver] ?: listed.entries.firstOrNull { it.key.startsWith(ver) || ver.startsWith(it.key) }?.value
            val assets = if (pages.isEmpty()) listOf(Triple("$ver.apk", link, size)) else pages.map { p ->
                val low = p.lowercase()
                val ext = when { "apkm" in low || "bundle" in low -> "apkm"; "xapk" in low -> "xapk"; else -> "apk" }
                Triple("$ver.$ext", p, size)
            }
            CatalogProcessor.Release(ver, title, false, "", assets)
        }
    }

    private suspend fun apkmirrorReal(url: String): String {
        var page = url
        repeat(3) {
            val got = Net.open(page)
            if (got.code == 403 || got.code == 429) throw HttpError(got.code, got.body)
            if (got.code >= 400) throw HttpError(got.code, got.body.take(180))
            if (got.body.isEmpty()) return got.url
            val next = Regex("""href\s*=\s*["']([^"']*(?:download\.php|\.(?:apks|xapk|apkm|apk))[^"']*)["']""", RegexOption.IGNORE_CASE).find(got.body)?.groupValues?.get(1) ?: return got.url
            val abs = runCatching { URI(got.url).resolve(next.replace("&amp;", "&")).toString() }.getOrDefault(next)
            if (abs == page) return abs
            page = abs
        }
        return page
    }

    /** Turns a listing page into the file the downloader should request. Other links pass through. */
    suspend fun prepareDownload(url: String): String {
        val path = url.substringBefore('?').lowercase()
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        val file = path.substringAfterLast('/')
        return when {
            host.endsWith("uptodown.com") && !pkgFile(file) && "/download/" in path -> uptodownReal(url)
            host.endsWith("itch.io") && "/file/" in path && !pkgFile(file) -> itchReal(url)
            host.endsWith("apkmirror.com") && !pkgFile(file) -> apkmirrorReal(url)
            host.endsWith("telegram.org") && "/dl/" in path -> runCatching { Net.open(url).url }.getOrNull()?.takeIf { pkgFile(it) } ?: url
            else -> url
        }
    }
    // endregion
    // endregion

    /** Releases into versions: APK assets that pass the filter, pre-releases only when asked, version read from the tag. */
    fun versionsOf(o: OrbitApp, rel: List<CatalogProcessor.Release>): List<VersionOption> {
        val filter = o.apkFilter.takeIf { it.isNotBlank() }?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }
        val vre = o.versionRegex.takeIf { it.isNotBlank() }?.let { runCatching { Regex(it) }.getOrNull() }
        return rel.filter { o.includePre || !it.prerelease }.mapNotNull { r ->
            val apks = r.assets.filter { (n, u) -> pkgFile(n) || pkgFile(u) }
                .filter { (n, _) -> filter == null || filter.containsMatchIn(n) }
            if (apks.isEmpty()) return@mapNotNull null
            val fromTag = vre?.find(r.tag)?.let { m -> m.groupValues.getOrNull(1)?.ifEmpty { null } ?: m.value }
            val ver = fromTag ?: CatalogProcessor.extractVersion(r.tag) ?: r.tag.removePrefix("v").ifBlank { null } ?: CatalogProcessor.extractVersion(apks[0].first) ?: r.date.substringBefore('T')
            val variants = apks.map { (n, u, s) -> AppVariant(CatalogProcessor.determineArch(n), u, s) }.distinctBy { it.url }
            VersionOption(CatalogProcessor.detectStream(apks[0].first, r.tag, r.prerelease), ver, r.date.substringBefore('T'), variants, r.tag)
        }.take(15)
    }

    /** Checks the source again. Errors are kept on the entry, so the Orbit list can say why an app stopped updating. */
    suspend fun refresh(o: OrbitApp, tokens: HostTokens, now: Long): OrbitApp = runCatching { versionsOf(o, releases(o, tokens)) }.fold(
        { v -> o.copy(versions = v.ifEmpty { o.versions }, checkedAt = now, error = if (v.isEmpty()) "No APK matched in the latest releases" else null) },
        { e -> o.copy(checkedAt = now, error = (e as? HttpError)?.let {
            when {
                it.code == 429 && sourceOf(o.url) == OrbitSource.GITLAB -> "GitLab is limiting this network. Wait a few minutes, then check again."
                it.code == 403 || it.code == 429 -> "Rate limited by the host, try later"
                else -> "The source answered ${it.code}"
            }
        } ?: "Couldn't reach the source") })

    suspend fun refreshAll(list: List<OrbitApp>, tokens: HostTokens, now: Long): List<OrbitApp> = coroutineScope {
        list.chunked(4).flatMap { batch -> batch.map { o -> async { if (o.paused) o else refresh(o, tokens, now) } }.awaitAll() }
    }

    private val META = { html: String, prop: String ->
        Regex("""<meta[^>]+(?:property|name)\s*=\s*["']$prop["'][^>]*content\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("""<meta[^>]+content\s*=\s*["']([^"']*)["'][^>]*(?:property|name)\s*=\s*["']$prop["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
    }

    /** ReVanced and Metrolist are not kept in Orbit. The text is what the user sees. */
    fun refused(url: String, name: String = "", pkg: String = "", author: String = ""): String? {
        val hay = listOf(url, name, pkg, author).joinToString(" ").lowercase()
        return when {
            "revanced" in hay -> "Orbit can't maintain ReVanced apps. Follow Morphe instead."
            "metrolist" in hay -> "Orbit can't maintain Metrolist. Follow ArchiveTune or BiTChord instead."
            else -> null
        }
    }

    /** A new entry for a link: what the source says about the app (name, author, icon, about, screenshots, package) plus its releases. */
    suspend fun probe(url: String, tokens: HostTokens, now: Long, base: OrbitApp? = null): OrbitApp {
        val clean = url.trim()
        var o = base ?: OrbitApp(idFor(clean), clean, addedAt = now)
        when (val src = sourceOf(clean)) {
            OrbitSource.GITHUB, OrbitSource.GITLAB, OrbitSource.CODEBERG, OrbitSource.FORGEJO -> runCatching { detectRepo(repoPath(clean)!!, tokens) }.getOrNull()?.let { d ->
                o = o.copy(name = o.name.ifBlank { d.name.orEmpty() }, author = o.author.ifBlank { d.author.orEmpty() }, about = o.about.ifBlank { d.description.orEmpty() },
                    icon = o.icon.ifBlank { d.icon.orEmpty() }, packageName = o.packageName ?: d.packageName, screenshots = o.screenshots.ifEmpty { d.shots })
                if (src == OrbitSource.GITHUB && o.icon.isBlank()) o = o.copy(icon = "https://github.com/${repoPath(clean)!!.owner}.png")
            }
            else -> runCatching { Net.text(clean.substringBefore('#'), c = Net.browser) }.getOrNull()?.takeIf { src != OrbitSource.APK }?.let { html ->
                val pkg = FDROID.find(clean)?.groupValues?.get(1) ?: IZZY.find(clean)?.groupValues?.get(1) ?: if (src == OrbitSource.UPTODOWN) uptodownPackage(html) else null
                val title = META(html, "og:title") ?: Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                val shots = Regex("""<img[^>]+src\s*=\s*["']([^"']*(?:phoneScreenshots|screenshot)[^"']*)["']""", RegexOption.IGNORE_CASE).findAll(html)
                    .map { runCatching { URI(clean).resolve(it.groupValues[1]).toString() }.getOrDefault(it.groupValues[1]) }.distinct().take(8).toList()
                val fdroidIcon = pkg?.let { if (src == OrbitSource.IZZY) "https://apt.izzysoft.de/fdroid/repo/$it/en-US/icon.png" else if (src == OrbitSource.FDROID) "https://f-droid.org/repo/$it/en-US/icon.png" else null }
                o = o.copy(name = o.name.ifBlank { title?.substringBefore(" | ")?.substringBefore(" - F-Droid")?.substringBefore(" - Download")?.trim().orEmpty() }, about = o.about.ifBlank { META(html, "og:description") ?: META(html, "description") ?: "" },
                    icon = o.icon.ifBlank { fdroidIcon?.takeIf { Net.alive(it, 5000) } ?: META(html, "og:image").orEmpty() }, packageName = o.packageName ?: pkg, screenshots = o.screenshots.ifEmpty { shots })
            }
        }
        if (o.name.isBlank()) o = o.copy(name = humanizeRepoName(clean.substringBefore('?').trimEnd('/').substringAfterLast('/').removeSuffix(".apk")))
        return refresh(o, tokens, now)
    }

    // region Obtainium
    /** Obtainium's per-app JSON (export entries, directory configs and obtainium://app/ links all share it) as an Orbit entry. */
    fun fromObtainium(o: JsonObject, now: Long, icon: String = "", about: String = ""): OrbitApp? {
        val url = o.str("url").takeIf { it.startsWith("http") } ?: return null
        val extra = (o["additionalSettings"] as? JsonPrimitive)?.contentOrNull?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: o["additionalSettings"] as? JsonObject
        fun x(k: String) = (extra?.get(k) as? JsonPrimitive)?.contentOrNull.orEmpty()
        val id = o.str("id")
        val tags = ((o["categories"] as? JsonArray) ?: (extra?.get("categories") as? JsonArray)).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.filter { it.isNotEmpty() }
        return OrbitApp(idFor(url), url, name = o.str("name").ifBlank { x("appName") }, author = o.str("author").ifBlank { x("appAuthor") },
            icon = icon, about = about.ifBlank { x("about") }, packageName = id.takeIf { '.' in it && ' ' !in it },
            apkFilter = x("apkFilterRegEx"), includePre = x("includePrereleases") == "true", versionRegex = x("versionExtractionRegEx"), tags = tags, addedAt = now)
    }

    /** Whatever the user brings: an Obtainium export file, a JSON list, one app, or obtainium://app/ and directory redirect links. */
    fun parseImport(text: String, now: Long): List<OrbitApp> {
        val t = text.trim()
        // The JSON rides URL-encoded after obtainium://app/ (alone, or inside the directory's redirect link); names can hold spaces once decoded
        val link = t.indexOf("obtainium://app/").takeIf { it >= 0 }?.let { i ->
            runCatching { java.net.URLDecoder.decode(t.substring(i + 16).substringBefore(' ').substringBefore('\n').replace("+", "%2B"), "UTF-8") }.getOrNull()
                ?.let { d -> d.substring(0, d.lastIndexOf('}') + 1).takeIf { it.startsWith("{") } }
        }
        if (link != null) return listOfNotNull(runCatching { json.parseToJsonElement(link) as JsonObject }.getOrNull()?.let { fromObtainium(it, now) })
        val root = runCatching { json.parseToJsonElement(t) }.getOrNull() ?: return t.lines().map { it.trim() }.filter { it.startsWith("http") }.map { OrbitApp(idFor(it), it, addedAt = now) }
        val arr = when (root) { is JsonArray -> root; is JsonObject -> (root["apps"] as? JsonArray) ?: JsonArray(listOf(root)); else -> JsonArray(emptyList()) }
        return arr.mapNotNull { (it as? JsonObject)?.let { o -> fromObtainium(o, now) } }.distinctBy { it.id }
    }

    /** One page of the Obtainium apps directory (apps.obtainium.imranr.dev), searchable. */
    suspend fun directory(query: String, page: Int, now: Long): Pair<List<DirectoryApp>, Boolean> {
        val r = json.parseToJsonElement(Net.text("https://apps.obtainium.imranr.dev/api/apps?limit=30&page=$page&q=${enc(query.trim())}")) as JsonObject
        val apps = (r["apps"] as? JsonArray).orEmpty().mapNotNull { e ->
            val a = e as? JsonObject ?: return@mapNotNull null
            val about = ((a["description"] as? JsonObject)?.get("en") as? JsonPrimitive)?.contentOrNull.orEmpty()
            val icon = a.str("icon")
            val cfgs = ((a["configs"] as? JsonArray).orEmpty() + listOfNotNull(a["config"])).mapNotNull { (it as? JsonObject)?.let { c -> fromObtainium(c, now, icon, about) } }
            val first = cfgs.firstOrNull() ?: return@mapNotNull null
            DirectoryApp(first.name, first.author, icon, about, (a["categories"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }, cfgs)
        }
        val pg = r["pagination"] as? JsonObject
        val more = ((pg?.get("page") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: page) < ((pg?.get("totalPages") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: page)
        return apps to more
    }

    /** Back out as an Obtainium export, so nothing kept in Orbit is ever locked in. */
    fun export(list: List<OrbitApp>): String = json.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("apps", JsonArray(list.map { o ->
            buildJsonObject {
                put("id", o.packageName ?: o.id.removePrefix(PREFIX)); put("url", o.url); put("author", o.author); put("name", o.name)
                put("additionalSettings", json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    if (o.apkFilter.isNotBlank()) put("apkFilterRegEx", o.apkFilter); if (o.includePre) put("includePrereleases", true)
                    if (o.versionRegex.isNotBlank()) put("versionExtractionRegEx", o.versionRegex); if (o.about.isNotBlank()) put("about", o.about)
                }))
            }
        }))
    })
    // endregion

    /** Sharing needs a source the catalog's mirror bot can follow, and enough to fill a store page. Empty when ready. */
    fun publishGaps(o: OrbitApp): List<String> = buildList {
        val src = sourceOf(o.url)
        if (src !in setOf(OrbitSource.GITHUB, OrbitSource.GITLAB, OrbitSource.CODEBERG)) add("a GitHub, GitLab or Codeberg source")
        if (o.packageName.isNullOrBlank()) add("its package name (install it once)")
        if (o.icon.isBlank()) add("an icon")
        if (o.about.isBlank()) add("a description")
        if (o.screenshots.size < 3) add("${3 - o.screenshots.size} more screenshot${if (3 - o.screenshots.size == 1) "" else "s"}")
    }

    /** The catalog entry an Orbit app becomes when shared: the same JSON the submission form makes. */
    fun catalogJson(o: OrbitApp): JsonObject {
        val r = repoPath(o.url); val src = sourceOf(o.url)
        // The catalog filters assets by a plain keyword, so only a filter that is plain text carries over
        val kw = o.apkFilter.takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c in "-_." } } ?: "apk"
        return buildJsonObject {
            put("name", o.name); put("id", slugId(o.name)); put("description", o.about); put("icon", sanitizeGitHubImageUrl(o.icon)); put("repoUrl", o.url)
            put("githubRepo", if (src == OrbitSource.GITHUB) r?.repoPath.orEmpty() else ""); put("gitlabRepo", if (src == OrbitSource.GITLAB) r?.repoPath.orEmpty() else "")
            put("gitlabDomain", if (src == OrbitSource.GITLAB) r?.domain.orEmpty() else ""); put("codebergRepo", if (src == OrbitSource.CODEBERG) r?.repoPath.orEmpty() else "")
            put("releaseKeyword", kw); put("packageName", o.packageName.orEmpty()); put("category", o.category); put("author", o.author.ifBlank { r?.owner.orEmpty() })
            put("downloadUrl", ""); put("screenshots", JsonArray(o.screenshots.map(::JsonPrimitive))); put("platform", Platform.ANDROID)
        }
    }
}

/** The catalog-shaped item every Orion screen understands: detail page, versions, downloads, installs, updates, inspector. */
fun OrbitApp.toItem(): AppItem {
    val r = parseRepoSource(url); val src = Orbit.sourceOf(url)
    val def = versions.firstOrNull { it.type == "Stable" } ?: versions.firstOrNull()
    val v0 = def?.variants?.firstOrNull()
    return AppItem(id = id, name = name.ifBlank { "Untitled app" }, description = about, icon = icon, version = def?.version ?: "Latest", latestVersion = def?.version ?: "Latest",
        downloadUrl = v0?.url ?: "#", variants = def?.variants.orEmpty(), availableVersions = versions, repoUrl = url,
        githubRepo = r?.repoPath?.takeIf { src == OrbitSource.GITHUB }, gitlabRepo = r?.repoPath?.takeIf { src == OrbitSource.GITLAB }, gitlabDomain = r?.domain?.takeIf { src == OrbitSource.GITLAB },
        codebergRepo = r?.repoPath?.takeIf { src == OrbitSource.CODEBERG }, packageName = packageName, category = category, tags = listOf(ORBIT_TAG), platform = Platform.ANDROID,
        size = v0?.size?.let(CatalogProcessor::formatSize) ?: "Varies", author = author.ifBlank { src.label }, screenshots = screenshots)
}

const val ORBIT_TAG = "Orbit"
