package com.orion.store.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

object CatalogProcessor {
    private val STRING_KEYS = setOf("id", "name", "description", "icon", "version", "latestVersion", "downloadUrl", "repoUrl", "githubRepo", "gitlabRepo",
        "codebergRepo", "gitlabDomain", "releaseKeyword", "packageName", "category", "platform", "size", "author", "officialSite")
    private val LIST_KEYS = setOf("tags", "screenshots", "patches")
    private val PC_TAGS = setOf("windows", "pc", "desktop", "win32", "win64")

    fun determineArch(name: String): String = name.lowercase().let {
        when { "arm64" in it || "v8a" in it -> "ARM64"; "armeabi" in it || "v7a" in it -> "ARMv7"; "x86_64" in it || "x64" in it -> "x64"; "x86" in it -> "x86"; else -> "Universal" }
    }

    private val VERSION_NOISE = listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86", "v7a", "v8a", "-all", "_all", "-universal", "_universal", "universal", ".apk", "release", "mod", "beta", "alpha", "nightly", "debug")
    private val VERSION = Regex("v?(\\d+(?:\\.\\d+)+)")
    private val REPO_HOST = Regex("^https?://(www\\.)?(github\\.com|gitlab\\.com|codeberg\\.org)/")
    fun extractVersion(s: String?): String? {
        var c = s?.lowercase()?.trim() ?: return null
        VERSION_NOISE.forEach { c = c.replace(it, "") }
        return VERSION.find(c)?.groupValues?.get(1)?.replace('-', '.')
    }

    fun formatSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return "Varies"
        val units = listOf("B", "KB", "MB", "GB", "TB"); val i = floor(ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, 4)
        val v = "%.1f".format(java.util.Locale.US, bytes / 1024.0.pow(i)).toDouble()
        return "${if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()} ${units[i]}"
    }

    fun detectStream(file: String, tag: String, prerelease: Boolean): String {
        val t = "$file $tag".lowercase()
        return when {
            listOf("nightly", "daily", "snapshot").any { it in t } -> "Nightly"
            listOf("alpha", "canary", "debug").any { it in t } -> "Alpha"
            listOf("beta", "preview", "rc", "pre").any { it in t } || prerelease -> "Beta"
            else -> "Stable"
        }
    }

    private fun sanitizeUrl(u: String?): String = u?.trim().let { if (it.isNullOrEmpty() || it.lowercase().startsWith("javascript:")) "#" else it }

    fun parseSize(s: String?): Double {
        val l = s?.lowercase() ?: return 0.0
        if ("varies" in l) return 0.0
        val n = l.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0.0
        return when { "gb" in l -> n * 1024; "kb" in l -> n / 1024; else -> n }
    }

    private fun normalizePlatform(p: String?) = when (p?.lowercase()?.trim()) { "pc", "desktop", "windows" -> Platform.PC; "tv" -> Platform.TV; else -> Platform.ANDROID }

    private fun coerce(o: JsonObject) = JsonObject(o.mapNotNull { (k, v) ->
        when {
            k in STRING_KEYS -> (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { k to JsonPrimitive(it.content) }
            k in LIST_KEYS -> (v as? JsonArray)?.let { a -> k to JsonArray(a.mapNotNull { e -> (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { JsonPrimitive(it.content.trim()) } }) }
            else -> k to v
        }
    }.toMap())

    fun sanitizeApp(raw: JsonElement): AppItem? {
        val o = raw as? JsonObject ?: return null
        val a = runCatching { json.decodeFromJsonElement(AppItem.serializer(), coerce(o)) }.getOrNull() ?: return null
        val plat = normalizePlatform(a.platform).let { if (it == Platform.ANDROID && a.tags.any { t -> t.lowercase() in PC_TAGS }) Platform.PC else it }
        val cat = a.category.trim().ifEmpty { "Utility" }.let { it.first().uppercase() + it.drop(1).lowercase() }
        return a.copy(
            id = a.id.ifEmpty { a.packageName?.ifEmpty { null } ?: a.name }, category = cat, platform = plat, tags = a.tags.filter { it.isNotEmpty() },
            icon = sanitizeUrl(a.icon).let { if (it == "#") "" else it }, version = a.version.ifEmpty { "Latest" }, latestVersion = a.latestVersion.ifEmpty { "Latest" },
            downloadUrl = sanitizeUrl(a.downloadUrl), screenshots = a.screenshots.map(::sanitizeUrl).filter { it != "#" }, availableVersions = emptyList(),
        )
    }

    // region mirror
    class Release(val tag: String, val name: String, val prerelease: Boolean, val date: String, val assets: List<Triple<String, String, Long?>>)

    fun parseMirror(root: JsonElement?): Map<String, List<Release>> = (root as? JsonObject)?.mapNotNull { (k, v) ->
        val arr = when (v) { is JsonArray -> v; is JsonObject -> JsonArray(listOf(v)); else -> return@mapNotNull null }
        k.lowercase() to arr.mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            fun s(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            Release(s("tag_name"), s("name"), (o["prerelease"] as? JsonPrimitive)?.booleanOrNull ?: false, s("published_at").ifEmpty { s("created_at") },
                (o["assets"] as? JsonArray).orEmpty().mapNotNull { e ->
                    val ao = e as? JsonObject ?: return@mapNotNull null
                    Triple((ao["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(), (ao["browser_download_url"] as? JsonPrimitive)?.contentOrNull.orEmpty(), (ao["size"] as? JsonPrimitive)?.longOrNull)
                })
        }
    }?.toMap().orEmpty()

    private fun dateMillis(d: String) = runCatching { Instant.parse(d).toEpochMilli() }.getOrElse { runCatching { LocalDate.parse(d.take(10)).toEpochDay() * 86_400_000 }.getOrDefault(0) }
    private val ARCH_ORDER = mapOf("Universal" to 1, "ARM64" to 2, "ARMv7" to 3)

    fun processItem(app: AppItem, mirror: Map<String, List<Release>>): AppItem {
        val url = app.repoUrl.orEmpty()
        val gh = !app.githubRepo.isNullOrEmpty() || "github.com" in url
        val gl = !app.gitlabRepo.isNullOrEmpty() || "gitlab" in url
        val cb = !app.codebergRepo.isNullOrEmpty() || "codeberg.org" in url
        if (!gh && !gl && !cb) return app
        val key = when {
            !app.githubRepo.isNullOrEmpty() -> "github::github.com::${app.githubRepo.lowercase()}"
            !app.gitlabRepo.isNullOrEmpty() -> "gitlab::${app.gitlabDomain?.ifEmpty { null } ?: "gitlab.com"}::${app.gitlabRepo.lowercase()}"
            !app.codebergRepo.isNullOrEmpty() -> "codeberg::codeberg.org::${app.codebergRepo.lowercase()}"
            else -> null
        }
        val clean = url.replace(REPO_HOST, "").removeSuffix("/").removeSuffix(".git").removeSuffix("/").lowercase()
        val bareGithub = app.githubRepo?.lowercase()
        val bareGitlab = app.gitlabRepo?.lowercase()
        val bareCodeberg = app.codebergRepo?.lowercase()
        val releases = key?.let { mirror[it.lowercase()] }
            ?: mirror[clean]
            ?: bareGithub?.let { mirror[it] }
            ?: bareGitlab?.let { mirror[it] }
            ?: bareCodeberg?.let { mirror[it] }
            ?: return app
        val kw = app.releaseKeyword?.lowercase()?.ifEmpty { null }
        val now = Instant.now().toString()
        val versions = releases.mapNotNull { r ->
            val cands = r.assets.filter { (n, u) -> n.lowercase().endsWith(".apk") || ".apk" in u.lowercase() || n.lowercase() == "apk" || "apk" in n.lowercase() }
            val assets = if (kw == null) cands else cands.filter { (n, u) -> kw in n.lowercase() || kw in u.lowercase() }
                .ifEmpty { if (kw in r.name.lowercase() || kw in r.tag.lowercase()) cands else emptyList() }
            if (assets.isEmpty()) return@mapNotNull null
            val stream = detectStream(assets[0].first, r.tag, r.prerelease)
            val tagV = r.tag.takeIf { it.lowercase() !in setOf("latest", "all") }?.let(::extractVersion)
            val ver = extractVersion(assets[0].first) ?: tagV ?: extractVersion(r.name) ?: r.date.ifEmpty { now }.substringBefore('T')
            val variants = assets.map { (n, u, s) -> AppVariant(determineArch(n), u, s) }.sortedBy { ARCH_ORDER[it.arch] ?: 4 }
            VersionOption(stream, ver, r.date.substringBefore('T'), variants) to dateMillis(r.date)
        }
        if (versions.isEmpty()) return app
        val latest = STREAMS.mapNotNull { st -> versions.filter { it.first.type == st }.maxByOrNull { it.second }?.first }
        val def = latest.firstOrNull { it.type == "Stable" } ?: latest.first()
        val v0 = def.variants.firstOrNull()
        return app.copy(version = def.version, latestVersion = def.version, downloadUrl = v0?.url?.ifEmpty { null } ?: "#", variants = def.variants,
            availableVersions = latest, size = v0?.size?.let(::formatSize) ?: app.size)
    }
    // endregion

    fun process(rawApps: JsonElement?, mirror: Map<String, List<Release>>): List<AppItem> =
        (rawApps as? JsonArray).orEmpty().mapNotNull(::sanitizeApp).map { processItem(it, mirror) }

    fun search(q: String, category: String, sort: SortOption, apps: List<AppItem>): List<AppItem> {
        val t = q.trim().lowercase()
        var out = if (t.isEmpty()) apps else apps.map { a ->
            val n = a.name.lowercase()
            a to (if (t in n) 10 else 0) + (if (n.startsWith(t)) 5 else 0) + (if (t in a.author.lowercase()) 5 else 0) + (if (t in a.description.lowercase()) 1 else 0) + (if (t in a.id.lowercase()) 2 else 0)
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        if (category != "All") out = out.filter(categoryFilter(category))
        return when (sort) {
            SortOption.NAME_ASC -> out.sortedWith { a, b -> collator.compare(a.name, b.name) }
            SortOption.NAME_DESC -> out.sortedWith { a, b -> collator.compare(b.name, a.name) }
            SortOption.SIZE_ASC -> out.sortedBy { parseSize(it.size) }
            SortOption.SIZE_DESC -> out.sortedByDescending { parseSize(it.size) }
            SortOption.NEWEST -> if (t.isEmpty()) out.reversed() else out
            else -> out
        }
    }

    // region storefront
    fun dailySeed(d: LocalDate = LocalDate.now()) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth

    fun <T> shuffle(list: List<T>, seed: Int): List<T> {
        var a = seed
        fun rng(): Double { a += 0x6D2B79F5; var t = a; t = (t xor (t ushr 15)) * (t or 1); t = t xor (t + (t xor (t ushr 7)) * (t or 61)); return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0 }
        val r = list.toMutableList()
        for (i in r.size - 1 downTo 1) { val j = floor(rng() * (i + 1)).toInt(); val tmp = r[i]; r[i] = r[j]; r[j] = tmp }
        return r
    }

    fun monogram(title: String): String {
        val w = title.split(Regex("\\s+")).map { it.replace(Regex("[^A-Za-z0-9]"), "") }.filter { it.isNotEmpty() }
        return when (w.size) { 0 -> "B"; 1 -> w[0].take(1).uppercase(); else -> (w[0].take(1) + w[1].take(1)).uppercase() }
    }

    private fun anim(a: String?) = if (a in setOf("snowfall", "confetti", "spark", "none")) a!! else "snowfall"
    private fun resolve(ids: List<String>?, map: Map<String, AppItem>) = ids.orEmpty().distinct().mapNotNull(map::get)
    private fun forPlatform(m: ModuleConfig, p: String) = (m.platform ?: "all").lowercase().let { it == "all" || it == p.lowercase() }

    private fun defaultModules(apps: List<AppItem>, seed: Int): List<ModuleConfig> {
        val s = shuffle(apps, seed + 17).map { it.id }
        return listOf(
            ModuleConfig("default_category_cards", "category_cards", "按分类浏览", "滑动卡片，点一个即可只看匹配的应用", insertAfterCategory = 0.0, animation = "confetti"),
            ModuleConfig("default_curved_showcase", "curved_apps", "精选聚光", "一份不容错过的 6 应用速览", insertAfterCategory = 2.0, animation = "snowfall", appIds = s.take(6)),
            ModuleConfig("default_recommended_bundles", "recommendation_bundles", "推荐应用合集", "打包安装，一步到位", insertAfterCategory = 4.0, animation = "spark", bundles = listOf(
                ModuleBundleConfig("default_bundle_daily", "日常主力套件", "日常工作流的核心应用", s.subList(0, minOf(3, s.size)), badge = "入门", icon = "fa-bolt"),
                ModuleBundleConfig("default_bundle_media", "媒体休闲包", "快速观影、听歌、放松一下", s.slice(minOf(3, s.size) until minOf(6, s.size)), badge = "热门", icon = "fa-photo-film"),
                ModuleBundleConfig("default_bundle_power", "高级玩家工具箱", "为爱折腾的进阶用户准备的高级工具", s.slice(minOf(6, s.size) until minOf(9, s.size)), badge = "专业", icon = "fa-terminal"),
            )),
        )
    }

    private fun interlude(m: ModuleConfig, index: Int, apps: List<AppItem>, map: Map<String, AppItem>, seed: Int): Pair<Int, StoreCollection>? {
        val ins = maxOf(0, floor(m.insertAfterCategory ?: (2.0 + index * 2)).toInt()); val an = anim(m.animation)
        return when (m.type) {
            "category_cards" -> homepageCategoryCards(resolve(m.appIds, map).ifEmpty { apps }).takeIf { it.isNotEmpty() }
                ?.let { ins to StoreCollection(m.id, m.title, CollectionType.CATEGORY_CARDS, m.subtitle, animation = an, categoryCards = it) }
            "curved_apps" -> {
                val sel = resolve(m.appIds, map).toMutableList(); val taken = sel.map { it.id }.toMutableSet()
                for (a in shuffle(apps, seed + index + 41)) { if (sel.size >= 6) break; if (taken.add(a.id)) sel += a }
                sel.takeIf { it.isNotEmpty() }?.let { ins to StoreCollection(m.id, m.title, CollectionType.BUNDLE, m.subtitle, animation = an, apps = it.take(6)) }
            }
            "recommendation_bundles" -> {
                val b = m.bundles.orEmpty().mapIndexedNotNull { i, c ->
                    val ap = resolve(c.appIds, map).ifEmpty { return@mapIndexedNotNull null }
                    val title = c.title.ifEmpty { "Bundle ${i + 1}" }
                    BundleItem(c.id.ifEmpty { "${m.id}-bundle-$i" }, title, c.description?.ifEmpty { null } ?: "Recommended apps", c.appIds, ap, c.color, c.badge, c.icon, c.monogram?.ifEmpty { null } ?: monogram(title))
                }.ifEmpty {
                    val src = resolve(m.appIds, map).ifEmpty { shuffle(apps, seed + index + 67).take(9) }
                    (0..2).mapNotNull { i ->
                        val ch = src.drop(i * 3).take(3).takeIf { it.size >= 2 } ?: return@mapNotNull null
                        BundleItem("${m.id}-bundle-$i", "推荐套装 ${i + 1}", "精心挑选、彼此合拍的应用", ch.map { it.id }, ch, null,
                            listOf("Hot", "Fresh", "Classic")[i], listOf("fa-wand-magic-sparkles", "fa-headphones", "fa-rocket")[i], monogram("Recommended Pack ${i + 1}"))
                    }
                }
                b.takeIf { it.isNotEmpty() }?.let { ins to StoreCollection(m.id, m.title, CollectionType.RECOMMENDATION_BUNDLES, m.subtitle, animation = an, bundles = it) }
            }
            else -> null
        }
    }

    fun storefront(all: List<AppItem>, platform: String, modules: List<ModuleConfig> = emptyList(), seed: Int = dailySeed()): List<StoreCollection> {
        val apps = all.filter { it.platform.equals(platform, true) }
        if (apps.isEmpty()) return emptyList()
        val map = apps.associateBy { it.id }
        val byCat = LinkedHashMap<String, MutableList<AppItem>>(); val byTag = LinkedHashMap<String, MutableList<AppItem>>()
        apps.forEach { a -> if (a.category != "All") byCat.getOrPut(a.category) { mutableListOf() } += a; a.tags.forEach { byTag.getOrPut(it) { mutableListOf() } += a } }
        val out = mutableListOf(
            StoreCollection("hero_daily", "Featured Today", CollectionType.HERO, apps = shuffle(apps, seed).take(5)),
            StoreCollection("recommended_daily", "Recommended For You", CollectionType.SWIMLANE, apps = shuffle(apps, seed + 1).take(10)),
            StoreCollection("new_updated", "New & Updated", CollectionType.SWIMLANE, apps = apps.reversed().take(12), totalAppCount = minOf(apps.size, 18)),
        )
        val rows = byCat.filter { it.value.size >= 3 }.map { (c, l) -> StoreCollection("cat_${c.lowercase()}", "「$c」精选", CollectionType.SWIMLANE, filter = c, apps = l.take(12)) }
        var active = modules.filter { forPlatform(it, platform) && it.type != "update_pills" }.ifEmpty { defaultModules(apps, seed) }
        if (active.none { it.type == "category_cards" }) active = listOf(ModuleConfig("default_category_cards_${platform.lowercase()}", "category_cards", "Browse by Category",
            "Swipe the cards, then tap one to show only matching apps", insertAfterCategory = 0.0, animation = "confetti")) + active
        val inter = active.filter { forPlatform(it, platform) }.mapIndexedNotNull { i, m -> interlude(m, i, apps, map, seed) }.sortedBy { it.first }
        var ii = 0
        while (ii < inter.size && inter[ii].first <= 0) out += inter[ii++].second
        rows.forEachIndexed { n, r -> out += r; while (ii < inter.size && n + 1 >= inter[ii].first) out += inter[ii++].second }
        while (ii < inter.size) out += inter[ii++].second
        shuffle(byTag.keys.toList(), seed + 2).take(2).forEach { t -> byTag.getValue(t).takeIf { it.size >= 3 }?.let { out += StoreCollection("tag_${t.lowercase()}", "Curated: $t", CollectionType.SWIMLANE, filter = t, apps = it.take(12)) } }
        return out
    }
    // endregion

    fun parseConfig(text: String): StoreConfig = json.decodeFromString(StoreConfig.serializer(), text)
    fun parseRoot(text: String): JsonElement = json.parseToJsonElement(text)
    fun str(e: JsonElement?) = runCatching { e?.jsonPrimitive?.contentOrNull }.getOrNull()
}
