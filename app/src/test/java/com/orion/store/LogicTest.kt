package com.orion.store

import com.orion.store.data.*
import com.orion.store.ui.bundleIssueBody
import com.orion.store.ui.cleanPermName
import com.orion.store.ui.isProfane
import com.orion.store.ui.mergeIdentity
import com.orion.store.ui.webSettingsImport
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    @Test fun excludePrereleasesSelection() {
        val stable = VersionOption("Stable", "2.0", "2026-10-01", listOf(AppVariant("Universal", "https://x/stable.apk", 10)))
        val beta = VersionOption("Beta", "3.0-beta", "2026-10-10", listOf(AppVariant("Universal", "https://x/beta.apk", 10)))
        val app = AppItem(id = "a", name = "A", availableVersions = listOf(stable, beta))
        val off = Settings()
        val on = Settings(excludePrereleases = true)

        assertFalse(off.excludePrereleases)
        assertTrue(on.excludePrereleases)
        // Off preserves existing behavior, including a saved per-app Beta choice.
        assertEquals("Beta", effectiveStreamFor(app, off.copy(appStreams = mapOf("a" to "Beta"))))
        assertEquals("3.0-beta", preferredVersion(app, effectiveStreamFor(app, off.copy(appStreams = mapOf("a" to "Beta")))))
        // On forces Stable for automatic selection but keeps the saved map intact.
        val saved = on.copy(appStreams = mapOf("a" to "Beta"))
        assertEquals("Stable", effectiveStreamFor(app, saved))
        assertEquals(mapOf("a" to "Beta"), saved.appStreams)
        assertEquals("2.0", preferredVersion(app, effectiveStreamFor(app, saved)))
        assertEquals("2.0", eligibleStableVersion(app))
    }

    @Test fun excludePrereleasesStreams() {
        assertEquals("Stable", CatalogProcessor.detectStream("app.apk", "v1.0", false))
        assertEquals("Beta", CatalogProcessor.detectStream("app-beta.apk", "v2.0", false))
        assertEquals("Beta", CatalogProcessor.detectStream("app.apk", "v2.0", true))
        assertEquals("Alpha", CatalogProcessor.detectStream("app-alpha.apk", "v2.0", false))
        assertEquals("Nightly", CatalogProcessor.detectStream("app-nightly.apk", "v2.0", false))
        val app = AppItem(
            id = "m", name = "M",
            availableVersions = listOf(
                VersionOption("Alpha", "4.0-alpha", "2026-10-11", listOf(AppVariant("Universal", "https://x/alpha.apk", 1))),
                VersionOption("Nightly", "5.0-nightly", "2026-10-12", listOf(AppVariant("Universal", "https://x/nightly.apk", 1))),
            ),
        )
        assertTrue(isPrereleaseOnly(app))
        assertFalse(hasStable(app))
        assertEquals(null, eligibleStableVersion(app))
    }

    @Test fun prereleaseOnlyStaysVisibleButBlocked() {
        val betaOnly = AppItem(
            id = "b", name = "B",
            availableVersions = listOf(VersionOption("Beta", "9.0-beta", "2026-10-12", listOf(AppVariant("Universal", "https://x/beta.apk", 1)))),
        )
        assertTrue(isPrereleaseOnly(betaOnly))
        val on = Settings(excludePrereleases = true)
        val off = Settings()
        // Visible either way: the catalog entry itself is untouched.
        assertEquals("b", betaOnly.id)
        // Blocked automatically when on, allowed when off.
        assertTrue(isExcludedDownload(betaOnly, null, on))
        assertFalse(hasEligibleUpdate(betaOnly, "8.0", on))
        assertTrue(hasAvailableUpdate(betaOnly, "8.0", "Beta"))
        assertFalse(isExcludedDownload(betaOnly, null, off))
        // Explicit prerelease URL cannot bypass the flag.
        assertTrue(isExcludedDownload(betaOnly, "https://x/beta.apk", on))
        assertFalse(isExcludedDownload(betaOnly, "https://x/beta.apk", off))
        // Filename-based install resolution also reports the prerelease stream.
        assertTrue(isExcludedInstall(betaOnly, "B_9.0-beta.apk", on))
        assertFalse(isExcludedInstall(betaOnly, "B_9.0-beta.apk", off))
    }

    @Test fun updatesRespectExclusion() {
        val stable = VersionOption("Stable", "2.0", "2026-10-01", listOf(AppVariant("Universal", "https://x/stable.apk", 10)))
        val beta = VersionOption("Beta", "3.0", "2026-10-10", listOf(AppVariant("Universal", "https://x/beta.apk", 10)))
        val app = AppItem(id = "u", name = "U", availableVersions = listOf(stable, beta))
        val on = Settings(excludePrereleases = true, installedVersions = mapOf("u" to "1.0"))
        val off = Settings(installedVersions = mapOf("u" to "1.0"))
        assertEquals(listOf("u"), availableUpdates(listOf(app), on, 0).map { it.id })
        assertEquals(listOf("u"), availableUpdates(listOf(app), off, 0).map { it.id })
        // Installed at latest stable: the newer Beta is not an eligible update when excluded.
        val upToDateStable = Settings(excludePrereleases = true, installedVersions = mapOf("u" to "2.0"))
        assertTrue(availableUpdates(listOf(app), upToDateStable, 0).isEmpty())
        val betaChaser = Settings(installedVersions = mapOf("u" to "2.0"), appStreams = mapOf("u" to "Beta"))
        assertEquals(listOf("u"), availableUpdates(listOf(app), betaChaser, 0).map { it.id })
    }

    @Test fun excludePrereleasesPersistence() {
        assertFalse(Settings().excludePrereleases)
        val s = Settings(excludePrereleases = true)
        assertEquals(s, json.decodeFromString(Settings.serializer(), json.encodeToString(Settings.serializer(), s)))
        // Backup/restore carries the flag through the shared identity fields (pure JVM, no Android Base64).
        val exported = buildJsonObject { put("excludePrereleases", true) }
        assertTrue(mergeIdentity(exported, Settings()).excludePrereleases)
        // Older backups without the field keep the safe default instead of crashing.
        val legacy = mergeIdentity(buildJsonObject { put("theme", "dark") }, Settings())
        assertFalse(legacy.excludePrereleases)
        assertEquals("dark", legacy.theme)
    }

    @Test fun misc() {
        assertEquals(1, Xp.level(0)); assertEquals(2, Xp.level(60)); assertEquals(10, Xp.level(900)); assertEquals(9, Xp.level(899))
        assertEquals(30, Xp.level(5800)); assertEquals(50, Xp.level(14700)); assertEquals(Xp.start(50), 14700L)
        assertTrue(isProfane("b4d guy", listOf("bad")))
        assertFalse(isProfane("good", listOf("bad")))
        assertEquals("FINE LOCATION", cleanPermName("android.permission.ACCESS_FINE_LOCATION"))
    }
}
