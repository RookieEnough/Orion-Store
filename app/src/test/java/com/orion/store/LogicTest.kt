package com.orion.store

import com.orion.store.data.*
import com.orion.store.ui.bundleIssueBody
import com.orion.store.ui.cleanPermName
import com.orion.store.ui.isProfane
import com.orion.store.ui.webSettingsImport

import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    @Test fun versions() {
        assertEquals("1.2.3", normalizeVersion("v1.2.3-beta+5"))
        assertTrue(compareVersions("1.10", "1.9") > 0)
        assertEquals(0, compareVersions("1.0", "1.0.0"))
        assertEquals(0, compareVersions(null, "1.0"))
        assertTrue(isSameVersion("v2.0", "2.0-rc"))
        assertTrue(isStoreUpdateAvailable("1.5.5", "1.6.0"))
        assertFalse(shouldForceStoreUpdate("1.5.5", "1.5.5"))
    }

    @Test fun imageLinks() {
        assertNull(explainRejectedImageLink("https://example.com/a.png"))
        assertNull(explainRejectedImageLink("https://raw.githubusercontent.com/o/r/main/x"))
        assertEquals("Link must start with http:// or https://", explainRejectedImageLink("ftp://x/a.png"))
        assertNotNull(explainRejectedImageLink("https://example.com/page"))
        assertEquals("https://raw.githubusercontent.com/o/r/main/s.png", sanitizeGitHubImageUrl("https://github.com/o/r/blob/main/s.png"))
        assertEquals("https://x.com/a.png", sanitizeGitHubImageUrl("https://x.com/a.png"))
        assertEquals("https://play-lh.googleusercontent.com/abc=h720-rw", optimizedImageUrl("https://play-lh.googleusercontent.com/abc=w526-h296", null, 720))
        assertEquals("https://play-lh.googleusercontent.com/abc=w256-h256-rw", optimizedImageUrl("https://play-lh.googleusercontent.com/abc", 256, 256))
        assertTrue(optimizedImageUrl("https://raw.githubusercontent.com/o/r/main/s.png", null, 720).startsWith("https://wsrv.nl/?url="))
    }

    @Test fun repoParsing() {
        assertEquals(RepoSource("github", "o/app", "o", "app"), parseRepoSource("https://github.com/o/app.git"))
        assertEquals(RepoSource("github", "o/app", "o", "app"), parseRepoSource("https://github.com/o/app/tree/main/src"))
        assertEquals(RepoSource("gitlab", "g/sub/app", "g", "app", "gitlab.com"), parseRepoSource("https://gitlab.com/g/sub/app/-/releases"))
        assertEquals("codeberg", parseRepoSource("https://codeberg.org/o/r")?.provider)
        assertNull(parseRepoSource("https://example.com/o/r"))
        assertNull(parseRepoSource("https://github.com/o"))
        assertEquals("com.foo.bar", extractPackageName("android {\n  namespace = \"x.y\"\n  defaultConfig { applicationId \"com.foo.bar\" }\n}"))
        assertEquals("org.x", extractPackageName("<manifest package=\"org.x\">"))
        assertEquals("My Cool App", humanizeRepoName("my-cool_app.git"))
        assertEquals("my-app-2", slugId("  My App 2!! "))
    }

    @Test fun submissionUrls() {
        val u = submissionIssueUrl("[]", 2, "Foo", "1.5.5")
        assertTrue(u.startsWith("https://github.com/RookieEnough/Orion-Data/issues/new?title="))
        assertTrue(enc("App Submission [2 Apps] - Foo") in u)
        val body = bundleIssueBody("T", "D", "fa-bolt", "c", listOf("a", "zz"), listOf(AppItem(id = "a", name = "A", packageName = "p.a")), null)
        assertTrue("- **A** (`a`) · `p.a`" in body)
        assertTrue("- zz" in body)
        assertFalse("Submitted by" in body)
        assertFalse("\n\n" in body)
    }

    @Test fun categories() {
        val music = AppItem(id = "m", name = "Tunes", description = "Listen to your album collection", category = "Media")
        val tool = AppItem(id = "t", name = "Box", category = "Utility", tags = listOf("Privacy"))
        assertTrue(appMatchesCategoryFilter(music, "Music")) // preset keyword in description
        assertTrue(appMatchesCategoryFilter(tool, "privacy")) // tag, case-insensitive
        assertTrue(appMatchesCategoryFilter(tool, "Utilities")) // preset raw category
        assertFalse(appMatchesCategoryFilter(tool, "Music"))
        val cards = homepageCategoryCards(listOf(music, tool, tool.copy(id = "t2")))
        assertEquals(1, cards.first { it.label == "Music" }.count)
        assertEquals(2, cards.first { it.label == "Utilities" }.count)
        assertEquals(listOf("All", "Music", "Privacy", "Utilities", "Media", "Utility"), availableFilterCategories(listOf(music, tool)))
    }

    @Test fun catalogCacheRoundTrip() {
        val c = CatalogCache(listOf(AppItem(id = "a", name = "A", variants = listOf(AppVariant("ARM64", "https://x/a.apk", 42)))), null, "GitHub")
        assertEquals(c, json.decodeFromString(CatalogCache.serializer(), json.encodeToString(CatalogCache.serializer(), c)))
    }

    @Test fun webCarryOver() {
        val s = webSettingsImport("""{"state":{"adWatchCount":42,"unlockedBadges":["Dino Rookie"],"theme":"dark","isLegend":true},"version":0}""", Settings())
        assertEquals(42, s.adWatchCount); assertEquals(listOf("Dino Rookie"), s.unlockedBadges); assertEquals("dark", s.theme); assertTrue(s.isLegend)
        // A clashing field type drops to the identity fields, which still carry the profile counters
        val f = webSettingsImport("""{"state":{"adWatchCount":7,"unlockedBadges":{"bad":1}}}""", Settings())
        assertEquals(7, f.adWatchCount)
        assertEquals(Settings(), webSettingsImport("not json", Settings()))
    }

    @Test fun releaseNotes() {
        val body = "# Orion Store 2.1.5\r\n\r\nIntro text.\r\n\r\n> **Updating?** Install over it.\r\n\r\n## 🔄 Hands-Free Updates\r\n\r\n" +
            "* **Updates That Install Themselves:** Turn on **Auto-update apps** in Settings.\r\n* **Safe by Design:** Checks every download.\r\n\r\n---\r\n\r\n" +
            "### Fixes\r\n- Fixed apps showing as unavailable. Your list is kept.\r\n- See [the docs](https://x.y) for `more`.\r\n## Empty section\r\n"
        val s = releaseHighlights(body)
        assertEquals(listOf("🔄 Hands-Free Updates", "Fixes"), s.map { it.title })
        assertEquals(listOf("Updates That Install Themselves", "Safe by Design"), s[0].items)
        assertEquals(listOf("Fixed apps showing as unavailable", "See the docs for more"), s[1].items)
        assertTrue(releaseHighlights("- " + "word ".repeat(40)).single().items.single().let { it.length <= 90 && it.endsWith("…") })
        val r = parseReleases("""[{"tag_name":"v2.0.5","name":"Orion-RE : The Labyrinth Update","published_at":"2026-10-14T06:47:22Z","html_url":"u","body":"- Fix"},{"draft":true,"tag_name":"x"}]""")
        assertEquals(1, r.size); assertEquals("2.0.5", r[0].version); assertEquals("The Labyrinth Update", r[0].name); assertEquals(1_791_960_442_000L, r[0].publishedAt)
    }

    @Test fun releaseNotesApiUrlBuildsGitHubTagEndpoint() {
        assertEquals("https://api.github.com/repos/JunkFood02/Seal/releases/tags/v1.13.1", releaseNotesApiUrl("JunkFood02/Seal", "v1.13.1"))
        // Slash, hash and space must stay inside a single path segment
        assertEquals("https://api.github.com/repos/o/r/releases/tags/v1%2F2", releaseNotesApiUrl("o/r", "v1/2"))
        assertEquals("https://api.github.com/repos/o/r/releases/tags/v1%232", releaseNotesApiUrl("o/r", "v1#2"))
        assertEquals("https://api.github.com/repos/o/r/releases/tags/v1%202", releaseNotesApiUrl("o/r", "v1 2"))
    }

    @Test fun releaseNotesApiUrlRejectsMissingOrMalformedInput() {
        assertNull(releaseNotesApiUrl(null, "v1"))
        assertNull(releaseNotesApiUrl("", "v1"))
        assertNull(releaseNotesApiUrl("   ", "v1"))
        assertNull(releaseNotesApiUrl("o/r", null))
        assertNull(releaseNotesApiUrl("o/r", ""))
        assertNull(releaseNotesApiUrl("o/r", "   "))
        assertNull(releaseNotesApiUrl("https://github.com/o/r", "v1"))
        assertNull(releaseNotesApiUrl("o", "v1"))
        assertNull(releaseNotesApiUrl("o/r/extra", "v1"))
        assertNull(releaseNotesApiUrl("o//r", "v1"))
        assertNull(releaseNotesApiUrl("o r", "v1"))
        assertNull(releaseNotesApiUrl("o/r", "v1\n2"))
        assertNull(releaseNotesApiUrl("o/r", ".."))
        assertNull(releaseNotesApiUrl("o-/r", "v1"))
        assertNull(releaseNotesApiUrl("#", "v1"))
    }

    @Test fun catalogueKeepsReleaseTagAndUrl() {
        val mirror = CatalogProcessor.parseMirror(CatalogProcessor.parseRoot("""
            {"github::github.com::o/app":[{"tag_name":"v1.2.3","name":"1.2.3","published_at":"2026-01-02T03:04:05Z",
            "html_url":"https://github.com/o/app/releases/tag/v1.2.3",
            "assets":[{"name":"app-v1.2.3-arm64-v8a.apk","browser_download_url":"https://x/app.apk","size":10}]}]}
        """.trimIndent()))
        val out = CatalogProcessor.processItem(AppItem(id = "a", name = "App", githubRepo = "o/app"), mirror)
        val v = out.availableVersions.single()
        assertEquals("1.2.3", v.version)
        assertEquals("v1.2.3", v.tag)
        assertEquals("https://github.com/o/app/releases/tag/v1.2.3", v.releaseUrl)
        assertEquals(listOf("ARM64"), v.variants.map { it.arch })
        assertEquals("1.2.3", out.version)
    }

    @Test fun catalogueToleratesMissingReleaseUrl() {
        val mirror = CatalogProcessor.parseMirror(CatalogProcessor.parseRoot("""
            {"github::github.com::o/app":[{"tag_name":"v2.0","name":"2.0","published_at":"2026-01-02T03:04:05Z",
            "assets":[{"name":"app-v2.0.apk","browser_download_url":"https://x/app.apk","size":10}]}]}
        """.trimIndent()))
        val v = CatalogProcessor.processItem(AppItem(id = "a", name = "App", githubRepo = "o/app"), mirror).availableVersions.single()
        assertEquals("v2.0", v.tag)
        assertEquals("", v.releaseUrl)
    }

    @Test fun versionOptionDefaultsAreBackwardCompatible() {
        val old = json.decodeFromString(VersionOption.serializer(), """{"type":"Stable","version":"1.0","date":"2026-01-01","variants":[]}""")
        assertEquals("", old.tag); assertEquals("", old.releaseUrl)
        val round = json.decodeFromString(VersionOption.serializer(), json.encodeToString(VersionOption.serializer(), VersionOption(version = "2.0", tag = "v2.0", releaseUrl = "u")))
        assertEquals("v2.0", round.tag); assertEquals("u", round.releaseUrl)
    }

    @Test fun antiFarming() {
        val t = 1_790_000_000_000L
        // Ads: 30 s apart at least, AD_DAILY per rolling day, then nothing until the oldest falls out of the window
        var s = Settings()
        s = s.incrementAdWatch(t); assertEquals(1, s.adWatchCount)
        s = s.incrementAdWatch(t + 1_000); assertEquals(1, s.adWatchCount)
        repeat(30) { s = s.incrementAdWatch(t + (it + 1) * Xp.AD_GAP_MS) }
        assertEquals(Xp.AD_DAILY, s.adWatchCount); assertNotNull(s.adBlockReason(t + 40 * Xp.AD_GAP_MS))
        assertNull(s.adBlockReason(t + Xp.DAY_MS + 1)); assertEquals(Xp.AD_DAILY + 1, s.incrementAdWatch(t + Xp.DAY_MS + 1).adWatchCount)
        // Game XP: 50 per run, GAME_DAILY per day, resets the next day
        var g = Settings(); repeat(20) { g = g.addGameXP(1e9, t) }
        assertEquals(Xp.GAME_DAILY, g.gameXP); assertEquals(Xp.GAME_DAILY + 50, g.addGameXP(80.0, t + Xp.DAY_MS).gameXP)
        // Imports and leaderboard: a hand-edited save fails, real progress passes
        assertTrue(Xp.plausible(Settings(adWatchCount = 300, gameXP = 2_000, submissionCount = 12), t))
        assertFalse(Xp.plausible(Settings(adWatchCount = 999_999), t)); assertFalse(Xp.plausible(Settings(adWatchCount = -1), t))
        assertFalse(Xp.plausible(Settings(submissionCount = 1, changeRequestCount = 5), t))
    }

    @Test fun misc() {
        assertEquals(1, Xp.level(60)); assertEquals(2, Xp.level(61)); assertEquals(10, Xp.level(1185)); assertEquals(9, Xp.level(1184))
        assertEquals(30, Xp.level(14355)); assertEquals(50, Xp.level(55125)); assertEquals(Xp.start(50), 55125L)
        assertTrue(isProfane("b4d guy", listOf("bad")))
        assertFalse(isProfane("good", listOf("bad")))
        assertEquals("FINE LOCATION", cleanPermName("android.permission.ACCESS_FINE_LOCATION"))
    }
}
