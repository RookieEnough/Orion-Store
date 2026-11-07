package com.orion.store

import com.orion.store.data.*
import com.orion.store.platform.ApkInsights
import com.orion.store.platform.Verifier
import com.orion.store.ui.bundleIssueBody
import com.orion.store.ui.cleanPermName
import com.orion.store.ui.isProfane
import com.orion.store.ui.webSettingsImport

import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    @Test fun verifierCommands() {
        // Every destructive command must stay scoped to the current user: a bare pm disable/uninstall would hit all users and can brick.
        (listOf(Verifier.DISABLE_CMD, Verifier.UNINSTALL_CMD) + Verifier.RESTORE_CMDS).forEach { assertTrue(it, "--user 0" in it && it.endsWith(Verifier.PKG)) }
        // Uninstall keeps the APK (-k), so Revert's install-existing can bring the component back
        assertTrue(" -k " in Verifier.UNINSTALL_CMD)
        assertTrue(Verifier.RESTORE_CMDS.first().contains("install-existing") && Verifier.RESTORE_CMDS.last().startsWith("pm enable"))
        // Documented global keys, set to their off values
        assertEquals("0", Verifier.GLOBALS.toMap()["package_verifier_enable"])
        assertEquals("0", Verifier.GLOBALS.toMap()["verifier_verify_adb_installs"])
        assertEquals("-1", Verifier.GLOBALS.toMap()["package_verifier_user_consent"])
    }

    @Test fun verifierRefusal() {
        // Success lines are not refusals
        assertNull(Verifier.refusal("Package com.google.android.verifier new state: disabled-user\n"))
        assertNull(Verifier.refusal("Success\n\n"))
        // pm prints why it refused; the popup shows that reason
        assertEquals("Cannot disable a protected package: com.google.android.verifier",
            Verifier.refusal("Exception occurred while executing 'disable-user':\njava.lang.SecurityException: Cannot disable a protected package: com.google.android.verifier\n"))
        assertEquals("Failure [DELETE_FAILED_INTERNAL_ERROR]", Verifier.refusal("Failure [DELETE_FAILED_INTERNAL_ERROR]"))
        assertEquals("Shell cannot change component state", Verifier.refusal("Error: Shell cannot change component state."))
    }

    @Test fun verifierScope() {
        val data = "Package [com.google.android.verifier]\n    codePath=/data/app/~~x/com.google.android.verifier-y"
        val system = "Package [com.google.android.verifier]\n    codePath=/system/priv-app/Verifier"
        val gone = "Unable to find package: com.google.android.verifier"
        assertEquals(Verifier.Home.DATA, Verifier.home(data))
        assertEquals(Verifier.Home.SYSTEM, Verifier.home(system))
        assertEquals(Verifier.Home.NONE, Verifier.home(gone))
        val dataSteps = Verifier.secureSteps(data, gone)
        assertTrue(dataSteps.first().startsWith("cmd appops set ${Verifier.PKG}"))
        assertTrue(dataSteps.contains(Verifier.DISABLE_CMD) && dataSteps.contains(Verifier.UNINSTALL_CMD))
        assertTrue(Verifier.secureSteps(system, gone).none { it == Verifier.UNINSTALL_CMD })
        val absent = Verifier.secureSteps(gone, gone)
        assertTrue(absent.none { it.startsWith("pm ") || it.startsWith("cmd appops") })
        assertEquals(3, absent.size)
        (dataSteps + absent).forEach {
            assertFalse(it.contains("adb shell"))
            assertFalse(it.contains("safetycore"))
            assertFalse(it.contains("set_sync_disabled"))
        }
        val files = Orbit.channelFiles("""<a href="https://cdn.example/files/App-1.2.apk">x</a>""")
        assertEquals("App-1.2.apk", files.single().second)
    }

    @Test fun apkFrameworksAndTrackers() {
        val flutter = listOf("lib/arm64-v8a/libflutter.so", "lib/arm64-v8a/libapp.so", "classes.dex")
        assertEquals(listOf("Flutter"), ApkInsights.frameworks(flutter, emptySet()))
        val rn = listOf("lib/arm64-v8a/libreactnativejni.so", "lib/arm64-v8a/libhermes.so")
        assertEquals(listOf("React Native (Hermes)", "Kotlin"), ApkInsights.frameworks(rn, setOf("kotlin/collections")))
        assertEquals(listOf("Unity (IL2CPP)"), ApkInsights.frameworks(listOf("lib/armeabi-v7a/libunity.so", "lib/armeabi-v7a/libil2cpp.so"), emptySet()))
        assertEquals(listOf("Jetpack Compose", "Kotlin"), ApkInsights.frameworks(listOf("classes.dex"), setOf("androidx/compose/runtime/internal", "kotlin")))
        // A package that merely starts with the same letters is not the framework
        assertTrue(ApkInsights.frameworks(listOf("classes.dex"), setOf("kotlinx/coroutines")).isEmpty())
        assertEquals(setOf("AppsFlyer" to "Attribution", "Google AdMob" to "Ads"),
            ApkInsights.trackers(setOf("com/appsflyer/internal", "com/google/android/gms/ads/rewarded", "com/example/app")).toSet())
        assertTrue(ApkInsights.trackers(setOf("com/appsflyerclone", "io/branchy")).isEmpty())
        assertEquals("FFmpeg", ApkInsights.knownLib("libavcodec.so")); assertNull(ApkInsights.knownLib("libzzz.so"))
        assertEquals("Code", ApkInsights.sizeGroup("classes2.dex")); assertEquals("Resources", ApkInsights.sizeGroup("resources.arsc"))
        assertEquals("Native libraries", ApkInsights.sizeGroup("lib/x86_64/liba.so")); assertEquals("Other", ApkInsights.sizeGroup("META-INF/CERT.RSA"))
        assertEquals("14", ApkInsights.androidName(34)); assertEquals("12L", ApkInsights.androidName(32)); assertEquals("10", ApkInsights.androidName(29)); assertEquals("16", ApkInsights.androidName(36))
        assertEquals("Read contacts", ApkInsights.permLabel("android.permission.READ_CONTACTS"))
    }

    @Test fun elfAlignment() {
        fun elf64(vararg aligns: Long): ByteArray {
            val b = java.nio.ByteBuffer.allocate(64 + 56 * aligns.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            b.put(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, 1))
            b.putLong(0x20, 64); b.putShort(0x36, 56); b.putShort(0x38, aligns.size.toShort())
            aligns.forEachIndexed { i, a -> b.putInt(64 + 56 * i, 1); b.putLong(64 + 56 * i + 0x30, a) }
            return b.array()
        }
        assertEquals(16_384L, ApkInsights.elfLoadAlign(elf64(65_536, 16_384)))
        assertEquals(4_096L, ApkInsights.elfLoadAlign(elf64(16_384, 4_096)))
        assertNull(ApkInsights.elfLoadAlign(ByteArray(80)))
        // Program headers cut off by the read limit: what was read still counts, nothing crashes
        assertEquals(65_536L, ApkInsights.elfLoadAlign(elf64(65_536, 4_096).copyOf(64 + 56 + 10)))
    }

    @Test fun dexTypeTable() {
        val names = listOf("Lcom/appsflyer/AppsFlyerLib;", "I", "Lkotlin/Unit;", "LTopLevel;")
        val data = java.io.ByteArrayOutputStream(); val offs = ArrayList<Int>()
        val dataStart = 0x70 + names.size * 8
        names.forEach { n -> offs += dataStart + data.size(); data.write(n.length); data.write(n.toByteArray()); data.write(0) }
        val b = java.nio.ByteBuffer.allocate(dataStart + data.size()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("dex\n035\u0000".toByteArray())
        b.putInt(0x38, names.size); b.putInt(0x3C, 0x70); b.putInt(0x40, names.size); b.putInt(0x44, 0x70 + names.size * 4)
        offs.forEachIndexed { i, o -> b.putInt(0x70 + 4 * i, o); b.putInt(0x70 + names.size * 4 + 4 * i, i) }
        b.position(dataStart); b.put(data.toByteArray())
        val pk = HashSet<String>()
        assertEquals(3, ApkInsights.dexPackages(b.array(), pk))
        assertEquals(setOf("com/appsflyer", "kotlin"), pk)
        assertEquals(0, ApkInsights.dexPackages(ByteArray(200), HashSet()))
    }

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

    @Test fun githubAssetLinks() {
        assertEquals(GithubAsset("o", "r", "v1.0", "app-release.apk"), parseGithubAsset("https://github.com/o/r/releases/download/v1.0/app-release.apk"))
        // Escapes decode, and a plus in a path stays a plus
        assertEquals("a b+c.apk", parseGithubAsset("https://github.com/o/r/releases/download/t/a%20b+c.apk")?.name)
        assertEquals("rel/1", parseGithubAsset("https://github.com/o/r/releases/download/rel%2F1/x.apk")?.tag)
        // Not a release asset: another host, the release page, the repo itself
        assertNull(parseGithubAsset("https://example.com/o/r/releases/download/v1/x.apk"))
        assertNull(parseGithubAsset("https://github.com/o/r/releases/tag/v1"))
        assertNull(parseGithubAsset("https://github.com/o/r"))
        assertEquals("Bearer t", githubAssetHeaders("t")["Authorization"])
        assertEquals("application/octet-stream", githubAssetHeaders("t")["Accept"])
    }

    @Test fun githubAssetApiLookup() = kotlinx.coroutines.runBlocking {
        val release = """{"assets":[{"name":"a.apk","url":"https://api.github.com/repos/o/r/releases/assets/1"},{"name":"b.apk","url":"https://api.github.com/repos/o/r/releases/assets/2"}]}"""
        val asked = mutableListOf<String>()
        val get: suspend (String, Map<String, String>) -> String = { u, h -> asked += u; assertEquals("Bearer t", h["Authorization"]); release }
        val link = "https://github.com/o/r/releases/download/v1/b.apk"
        assertEquals("https://api.github.com/repos/o/r/releases/assets/2", githubAssetApiUrl(link, "t", get))
        assertEquals(listOf("https://api.github.com/repos/o/r/releases/tags/v1"), asked)
        // A tag with a slash keeps it in the API path
        githubAssetApiUrl("https://github.com/o/r/releases/download/rel%2F1/b.apk", "t", get)
        assertEquals("https://api.github.com/repos/o/r/releases/tags/rel/1", asked.last())
        // Nothing to retry with: no token, not an asset link, a file the release does not list, or the API refusing
        asked.clear()
        assertNull(githubAssetApiUrl(link, "", get))
        assertNull(githubAssetApiUrl("https://github.com/o/r", "t", get))
        assertTrue(asked.isEmpty())
        assertNull(githubAssetApiUrl("https://github.com/o/r/releases/download/v1/zzz.apk", "t", get))
        assertNull(githubAssetApiUrl(link, "t") { _, _ -> throw HttpError(404, "") })
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

    @Test fun cleanNotes() {
        val md = "## What's Changed\r\n* **Fix** crash on `start` by @dev in https://github.com/a/b/pull/3 :tada:\r\n- [x] See [docs](https://x.y)\r\n![shot](https://i.png)\r\n" +
            "| a | b |\r\n```\r\ncode\r\n```\r\n## Fixes\r\n- Faster boot\r\n\r\n**Full Changelog**: https://github.com/a/b/compare/v1...v2\r\n## New Contributors\r\n* @x made it\r\nsha256 0123456789abcdef0123456789abcdef"
        assertEquals("• Fix crash on start\n• See docs\n\n## Fixes\n• Faster boot", cleanNotes(md))
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

    @Test fun channelPackages() {
        val cs = AppItem(id = "cs", name = "CloudStream", packageName = "com.lagradost.cloudstream3")
        val installed = listOf("com.lagradost.cloudstream3.prerelease", "com.lagradost.cloudstream3.plugins.manager", "com.lagradost.cloudstream3x", "com.lagradost.cloudstream3.beta2")
        val c = packageCandidates(cs, null, installed)
        assertTrue("com.lagradost.cloudstream3.prerelease" in c); assertTrue("com.lagradost.cloudstream3.beta2" in c)
        assertFalse("com.lagradost.cloudstream3.plugins.manager" in c); assertFalse("com.lagradost.cloudstream3x" in c)
        assertEquals("com.lagradost.cloudstream3", c[0]); assertEquals("x.y", packageCandidates(cs, "x.y").first())
    }

    @Test fun aiAnswers() {
        val a = com.orion.store.platform.OrionAi.parse("<think>hmm</think>Try Metrolist [3] or ViMusic [7, 9]. **Both** are free.")
        assertEquals("Try Metrolist or ViMusic. Both are free.", a.reply); assertEquals(listOf(3, 7, 9), a.picks)
        val apps = listOf(AppItem(id = "m", name = "Metrolist", category = "Music", description = "YouTube Music client"), AppItem(id = "k", name = "Keyboard", category = "Tools"))
        assertEquals(listOf(0), com.orion.store.platform.OrionAi.matches(apps, "I want to listen to songs for free", 5))
        assertTrue(com.orion.store.platform.OrionAi.matches(apps, "hey whats orion", 5).isEmpty())
    }

    @Test fun managedAndIgnored() {
        val a = AppItem(id = "a", name = "A", latestVersion = "2.0")
        val s = Settings(installedVersions = mapOf("a" to "1.0"), installSources = mapOf("a" to "com.android.vending"))
        assertEquals(listOf(a), availableUpdates(listOf(a), s, 0))
        val skip = s.copy(skipOtherStores = true)
        assertTrue(availableUpdates(listOf(a), skip, 0).isEmpty())
        assertTrue(isManaged("a", skip.withManaged("a", true)))
        assertFalse(isManaged("a", s.withManaged("a", false)))
        assertTrue(s.withManaged("a", false).withManaged("a", true).appManage.isEmpty())
        assertTrue(isManaged("a", skip.copy(installSources = mapOf("a" to "com.google.android.packageinstaller"))))
        val now = 10L * ONE_WEEK_MS
        assertTrue(isUpdateIgnored(a, s.ignoreUpdate("a", "week", null, now), now + 1000))
        assertFalse(isUpdateIgnored(a, s.ignoreUpdate("a", "week", null, now), now + ONE_WEEK_MS))
        assertTrue(isUpdateIgnored(a, s.ignoreUpdate("a", "version", "v2.0", now), now))
        assertFalse(isUpdateIgnored(a.copy(latestVersion = "2.1"), s.ignoreUpdate("a", "version", "2.0", now), now))
    }

    @Test fun categoryWholeWords() {
        val budget = AppItem(id = "b", name = "Pocket Budget", description = "Track expenses with a simple tracker", category = "Utility")
        val player = AppItem(id = "p", name = "Tunes", description = "Offline music player", category = "Media")
        assertFalse(appMatchesCategoryFilter(budget, "Music"))
        assertTrue(appMatchesCategoryFilter(player, "Music"))
    }

    @Test fun installFailures() {
        assertTrue("uninstall" in installFailureText("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package x signatures do not match]", "X").second)
        assertTrue("Secure Folder" in installFailureText("INSTALL_FAILED_UPDATE_INCOMPATIBLE", "X").second)
        assertEquals("Phone storage is full", installFailureText("INSTALL_FAILED_INSUFFICIENT_STORAGE", "X").first)
        assertTrue("INSTALL_FAILED_WEIRD" in installFailureText("Failure [INSTALL_FAILED_WEIRD]", "X").second)
    }

    @Test fun misc() {
        assertEquals(1, Xp.level(60)); assertEquals(2, Xp.level(61)); assertEquals(10, Xp.level(1185)); assertEquals(9, Xp.level(1184))
        assertEquals(30, Xp.level(14355)); assertEquals(50, Xp.level(55125)); assertEquals(Xp.start(50), 55125L)
        assertTrue(isProfane("b4d guy", listOf("bad")))
        assertFalse(isProfane("good", listOf("bad")))
        assertEquals("FINE LOCATION", cleanPermName("android.permission.ACCESS_FINE_LOCATION"))
    }

    @Test fun betaInstallIsNotAnUpdateFromStable() {
        val beta = VersionOption("Beta", "20261020", variants = listOf(AppVariant("Universal", "u")))
        val stable = VersionOption("Stable", "1.5.0", variants = listOf(AppVariant("Universal", "s")))
        val app = AppItem(id = "x", availableVersions = listOf(stable, beta))
        // Installed the Beta (names itself 1.4.2) through Orion: no update on its own channel, a new Beta is one
        assertFalse(hasAvailableUpdate(app, "1.4.2", "Beta", InstalledBuild("1.4.2", "20261020")))
        assertTrue(hasAvailableUpdate(app.copy(availableVersions = listOf(stable, beta.copy(version = "20261101"))), "1.4.2", "Beta", InstalledBuild("1.4.2", "20261020")))
        // No record: an exact catalog name still counts as that build
        assertFalse(hasAvailableUpdate(app, "20261020", "Beta"))
        assertTrue(hasAvailableUpdate(app, "1.4.0", "Stable"))
    }

    @Test fun readmeScreenshotsSkipBadgesAndResolvePaths() {
        val md = """
            <img src="fastlane/metadata/android/en-US/images/icon.png" alt="logo" width="96">
            [![Build](https://github.com/o/r/actions/workflows/ci.yml/badge.svg)](x) ![Downloads](https://img.shields.io/github/downloads/o/r/total)
            [<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="80">](https://f-droid.org)
            ![Home screen](./docs/home.png) ![](https://github.com/o/r/blob/main/art/settings.jpg)
            <img src="docs/screenshot_player.webp">
        """.trimIndent()
        val raw = "https://raw.githubusercontent.com/o/r/main"
        val imgs = readmeImages(md, raw)
        assertEquals(listOf("$raw/docs/home.png", "$raw/docs/screenshot_player.webp", "$raw/art/settings.jpg"), pickScreenshots(imgs))
        assertEquals("$raw/fastlane/metadata/android/en-US/images/icon.png", pickReadmeIcon(imgs))
    }

    @Test fun orbitReadsObtainiumAndSources() {
        val extra = "{" + "\"apkFilterRegEx\": \"web-release\", \"includePrereleases\": true" + "}"
        val export = kotlinx.serialization.json.buildJsonObject {
            put("apps", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.buildJsonObject {
                put("id", kotlinx.serialization.json.JsonPrimitive("app.organicmaps")); put("url", kotlinx.serialization.json.JsonPrimitive("https://github.com/organicmaps/organicmaps"))
                put("name", kotlinx.serialization.json.JsonPrimitive("Organic Maps")); put("additionalSettings", kotlinx.serialization.json.JsonPrimitive(extra))
            })))
        }.toString()
        val o = Orbit.parseImport(export, 0).single()
        assertEquals("app.organicmaps", o.packageName); assertEquals("web-release", o.apkFilter); assertTrue(o.includePre)
        val link = "obtainium://app/%7B%22id%22%3A%22com.example.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fexample%2Fapp%22%2C%22author%22%3A%22example%22%2C%22name%22%3A%22Example%22%7D"
        assertEquals("https://github.com/example/app", Orbit.parseImport(link, 0).single().url)
        assertEquals(OrbitSource.FDROID, Orbit.sourceOf("https://f-droid.org/en/packages/org.fossify.gallery/"))
        assertEquals(OrbitSource.IZZY, Orbit.sourceOf("https://apt.izzysoft.de/fdroid/index/apk/com.foo.bar"))
        assertEquals(OrbitSource.APK, Orbit.sourceOf("https://example.com/files/app-1.2.apk?x=1"))
        assertEquals(OrbitSource.WEB, Orbit.sourceOf("https://example.com/download"))
        assertEquals(OrbitSource.UPTODOWN, Orbit.sourceOf("https://facebook.en.uptodown.com/android"))
        assertEquals(OrbitSource.APKMIRROR, Orbit.sourceOf("https://www.apkmirror.com/apk/google-inc/youtube/"))
        assertEquals(OrbitSource.APK, Orbit.sourceOf("https://example.com/files/app.xapk"))
        assertEquals(OrbitSource.APK, Orbit.sourceOf("https://example.com/files/app.apkm"))
        assertEquals(OrbitSource.APKPURE, Orbit.sourceOf("https://apkpure.com/youtube/com.google.android.youtube"))
        assertEquals("com.google.android.youtube", Orbit.apkPurePackage("https://apkpure.com/youtube/com.google.android.youtube"))
        assertEquals(OrbitSource.APTOIDE, Orbit.sourceOf("https://youtube.en.aptoide.com/app"))
        assertEquals(OrbitSource.SOURCEFORGE, Orbit.sourceOf("https://sourceforge.net/projects/example/files"))
        assertEquals(OrbitSource.ITCH, Orbit.sourceOf("https://nkmusic.itch.io/bharat-apps-store"))
        assertEquals(OrbitSource.FARSROID, Orbit.sourceOf("https://www.farsroid.com/example"))
        assertEquals(OrbitSource.LITEAPKS, Orbit.sourceOf("https://liteapks.com/example"))
        assertEquals(OrbitSource.APK4FREE, Orbit.sourceOf("https://apk4free.net/example"))
        assertEquals(OrbitSource.COOLAPK, Orbit.sourceOf("https://www.coolapk.com/apk/com.example"))
        assertEquals(OrbitSource.SOURCEHUT, Orbit.sourceOf("https://git.sr.ht/~user/app"))
        val itch = """<div class="upload"><a data-upload_id="16053471" class="button download_btn">Download</a><strong title="Bharat_apps_store.apk" class="name">Bharat_apps_store.apk</strong> <span class="file_size"><span>31 MB</span></span></div>"""
        val row = Orbit.itchUploads(itch).single()
        assertEquals("Bharat_apps_store.apk", row.first)
        assertEquals("16053471", row.second)
        assertEquals(31L * (1L shl 20), row.third)
        val mirror = """<span class="infoSlide-name">Version:</span><span class="infoSlide-value">1.9.44                    </span><span class="infoSlide-name">File size:</span><span class="infoSlide-value">31.94 MB</span>"""
        assertEquals(Orbit.humanBytes("31.94 MB"), Orbit.apkMirrorVersionSizes(mirror)["1.9.44"])
        assertEquals(Orbit.humanBytes("31.94 MB"), Orbit.apkMirrorSize(mirror))
        assertEquals("Orbit can't maintain ReVanced apps. Follow Morphe instead.", Orbit.refused("https://github.com/revanced/revanced-manager"))
        assertEquals("Orbit can't maintain Metrolist. Follow ArchiveTune or BiTChord instead.", Orbit.refused("https://github.com/mostafaalagamy/Metrolist", "Metrolist"))
        assertEquals(null, Orbit.refused("https://github.com/MorpheApp/morphe", "Morphe"))
        assertEquals(OrbitSource.JENKINS, Orbit.sourceOf("https://ci.example.com/job/myapp/"))
        assertEquals(OrbitSource.TELEGRAM, Orbit.sourceOf("https://t.me/s/SomeChannel"))
        assertEquals(OrbitSource.DISCORD, Orbit.sourceOf("https://discord.com/app"))
        assertEquals(OrbitSource.DISCORD, Orbit.sourceOf("https://cdn.discordapp.com/attachments/1/2/app.apk"))
        assertEquals(OrbitSource.FDROID, Orbit.sourceOf("https://mirror.example.org/packages/org.foo.bar"))
        val bundle = Orbit.versionsOf(o.copy(apkFilter = ""), listOf(com.orion.store.data.CatalogProcessor.Release("1.4", "", false, "", listOf(Triple("app.xapk", "https://x/app.xapk", 9L), Triple("app.apkm", "https://x/app.apkm", 8L)))))
        assertEquals(listOf("https://x/app.xapk", "https://x/app.apkm"), bundle.single().variants.map { it.url })
        assertEquals(listOf("base.apk", "split_config.arm64_v8a.apk", "split_config.xxhdpi.apk"), com.orion.store.platform.Bundles.choose(
            listOf("split_config.armeabi_v7a.apk", "split_config.arm64_v8a.apk", "split_config.hdpi.apk", "split_config.xxhdpi.apk", "base.apk"), listOf("arm64-v8a"), 480))
        assertEquals(12L * (1L shl 20), Orbit.apkMirrorSize("File size: 12 MB"))
        val tagged = Orbit.parseImport("""{"apps":[{"id":"com.foo","url":"https://github.com/a/b","name":"B","categories":["Tools"]}]}""", 0).single()
        assertEquals(listOf("Tools"), tagged.tags)
        val rel = listOf(CatalogProcessor.Release("v2.0-beta", "", true, "2026-10-02T00:00:00Z", listOf(Triple("app-2.0-web-release.apk", "u3", 1L))),
            CatalogProcessor.Release("v1.9", "", false, "2026-09-01T00:00:00Z", listOf(Triple("app-1.9-web-release.apk", "u1", 1L), Triple("app-1.9-google-release.apk", "u2", 1L))))
        val v = Orbit.versionsOf(o, rel)
        assertEquals(listOf("2.0", "1.9"), v.map { it.version }); assertEquals(listOf("u1"), v[1].variants.map { it.url })
        assertEquals(listOf("1.9"), Orbit.versionsOf(o.copy(includePre = false), rel).map { it.version })
    }
}
