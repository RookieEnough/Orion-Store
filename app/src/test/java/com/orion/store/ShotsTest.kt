package com.orion.store

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.allowHardware
import coil3.imageDecoderEnabled
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.orion.store.data.Tab
import com.orion.store.ui.MainViewModel
import com.orion.store.ui.OrionRoot
import com.orion.store.ui.Overlay
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Dev-only visual harness: renders the real app (live catalog) and writes PNGs to app/build/shots. `-Pshots=all` or `-Pshots=home,detail`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class ShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val want = System.getProperty("orion.shots").orEmpty().split(',').filter { it.isNotBlank() }.toSet()

    private fun pump(ms: Long) = repeat((ms / 50).toInt()) {
        Thread.sleep(25); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)); rule.mainClock.advanceTimeBy(50)
    }

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    private fun boot(): MainViewModel {
        java.io.File("build/shots").mkdirs(); org.robolectric.shadows.ShadowLog.stream = java.io.PrintStream(java.io.File("build/shots/log.txt"))
        SingletonImageLoader.setUnsafe { ImageLoader.Builder(it).allowHardware(false).imageDecoderEnabled(false).build() }
        val vm = MainViewModel(ApplicationProvider.getApplicationContext())
        vm.set { it.copy(hasSeenModernUITutorial = true, seenNativeWelcome = true, theme = System.getProperty("orion.theme") ?: "light") }
        if (System.getProperty("orion.shots").orEmpty().contains("ann")) vm.announceNow = true
        rule.mainClock.autoAdvance = false
        rule.setContent { OrionRoot(vm) }
        var waited = 0L
        while ((vm.loading || vm.allApps().isEmpty()) && waited < 60_000) { pump(500); waited += 500 }
        pump(4000); vm.overlays.clear()
        return vm
    }

    /** Fling the visible vertical scroller (topmost overlay with [last]) to its end. */
    private fun end(last: Boolean = false) {
        pump(1500)
        val all = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).filter(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        (if (last) all.onLast() else all.onFirst()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
    }

    private val opts = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = .5))

    /** Long-page captures: -Pshots=tall */
    @Config(sdk = [35], qualifiers = "w400dp-h2600dp-xhdpi")
    @Test fun tall() {
        assumeTrue("tall" in want)
        val vm = boot()
        fun cap(n: String, setup: () -> Unit) { setup(); pump(4000); captureScreenRoboImage("build/shots/tall-$n.png", roborazziOptions = opts) }
        cap("modern") { vm.set { it.copy(storeLayout = "modern") } }
        cap("modern2") {
            rule.onAllNodes(hasScrollToIndexAction()).filter(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).onFirst().performScrollToIndex(9)
        }
        cap("pc") { vm.navTo(Tab.PC) }
        cap("detail") { vm.navTo(Tab.ANDROID); vm.selectApp(vm.allApps().first { it.platform == "Android" && it.screenshots.isNotEmpty() && it.patches.isNotEmpty() }) }
    }

    /** Frames right after tapping a lane arrow on modern home: -Pshots=catnav */
    @Test fun catnav() {
        assumeTrue("catnav" in want)
        val vm = boot()
        vm.set { it.copy(storeLayout = "modern") }; pump(3000)
        vm.categoryNavigate("Media")
        listOf(16L, 100L, 300L, 700L, 1500L).fold(0L) { prev, at -> pump(at - prev); captureScreenRoboImage("build/shots/catnav-$at.png", roborazziOptions = opts); at }
    }

    /** Frames after backing out of a detail opened from the Recommended carousel: -Pshots=recback */
    @Test fun recback() {
        assumeTrue("recback" in want)
        val vm = boot()
        vm.set { it.copy(storeLayout = "modern") }; vm.navTo(Tab.ANDROID); pump(3000)
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToKeyAction()).onFirst().performScrollToKey("recommended_daily"); pump(2000)
        val app = vm.collections.first { it.id == "recommended_daily" }.apps.first()
        captureScreenRoboImage("build/shots/recback-0before.png", roborazziOptions = opts)
        vm.selectApp(app, "recommended_daily"); pump(2500)
        vm.selectedApp = null
        listOf(16L, 50L, 100L, 200L, 350L, 600L, 1200L).fold(0L) { prev, at -> pump(at - prev); captureScreenRoboImage("build/shots/recback-$at.png", roborazziOptions = opts); at }
    }

    /** Splash mark as a PNG: -Pshots=splash */
    @Test fun splash() {
        assumeTrue("splash" in want)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = androidx.core.content.ContextCompat.getDrawable(ctx, com.orion.store.R.drawable.ic_splash)!!
        val b = android.graphics.Bitmap.createBitmap(576, 576, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b); c.drawColor(ctx.getColor(com.orion.store.R.color.splash_bg)); d.setBounds(0, 0, 576, 576); d.draw(c)
        java.io.File("build/shots").mkdirs(); java.io.FileOutputStream("build/shots/splash.png").use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** README gallery: -Pshots=readme, written to ../shots-readme */
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    @Test fun readme() {
        assumeTrue("readme" in want)
        val vm = boot(); java.io.File("../shots-readme").mkdirs()
        val full = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = .6))
        fun cap(n: String, setup: () -> Unit) { setup(); pump(4000); captureScreenRoboImage("../shots-readme/$n.png", roborazziOptions = full) }
        val apps = vm.allApps().filter { it.platform == "Android" }
        val now = System.currentTimeMillis()
        vm.set { it.copy(storeLayout = "modern", adWatchCount = 120, submissionCount = 12, dinoHighScore = 820, unlockedBadges = listOf("Dino Rookie", "Dino Master"),
            showcasedBadges = listOf("Supernova", "Merge Tamer", "Guardian"), userProfile = com.orion.store.data.UserProfile("Orion Pilot", "astro", null, now)) }
        vm.upd { it.copy(appLibrary = apps.take(4).associate { a -> a.id to com.orion.store.data.LibraryEntry("downloaded", null, a.latestVersion, now, null, now) }, favorites = apps.take(3).map { it.id }) }
        cap("01-home") { vm.navTo(Tab.ANDROID) }
        cap("02-detail") { vm.selectApp(apps.first { it.screenshots.size >= 3 }, "list") }
        cap("03-myapps") { vm.selectedApp = null; vm.navTo(Tab.MYAPPS) }
        cap("04-about") { vm.navTo(Tab.ABOUT) }
        cap("05-profile") { vm.openProfileStats() }
        cap("06-badges") { pump(1500); rule.onAllNodesWithText("Badge collection").onFirst().performClick() }
        cap("07-quest") { vm.overlays.clear(); vm.set { it.copy(adWatchCount = 25) }; pump(1000); vm.open(Overlay.AD_DONATION) }
        cap("08-settings") { vm.overlays.clear(); pump(1000); vm.set { it.copy(adWatchCount = 120) }; vm.openSettings() }
        cap("09-xtra") { vm.settingsMenu = "installer" }
        cap("10-welcome") { vm.overlays.clear(); pump(1000); vm.set { it.copy(seenNativeWelcome = false) } }
        cap("11-dark") { vm.set { it.copy(seenNativeWelcome = true, theme = "dark") }; pump(1500); vm.navTo(Tab.ANDROID) }
        cap("12-pc") { vm.navTo(Tab.PC) }
    }

    @Test fun shots() {
        assumeTrue(want.isNotEmpty() && "tall" !in want)
        val vm = boot()
        fun shot(name: String, setup: () -> Unit) {
            if ("all" !in want && name !in want) return
            setup(); pump(3500)
            captureScreenRoboImage("build/shots/$name.png", roborazziOptions = opts)
        }
        shot("home") { vm.navTo(Tab.ANDROID) }
        shot("search") { vm.navTo(Tab.ANDROID); vm.search("youtube") }
        shot("classic") { vm.set { it.copy(storeLayout = "classic") } }
        shot("classicaz") { vm.set { it.copy(storeLayout = "classic") }; vm.sort(com.orion.store.data.SortOption.NAME_ASC) }
        shot("network") { vm.openSettings("network") }
        shot("backup") { vm.openSettings("identity") }
        shot("modern") { vm.set { it.copy(storeLayout = "modern") } }
        shot("pc") { vm.navTo(Tab.PC) }
        shot("tv") { vm.navTo(Tab.TV) }
        shot("detail") { vm.navTo(Tab.ANDROID); vm.selectApp(vm.allApps().first { it.platform == "Android" && it.screenshots.isNotEmpty() }, "list") }
        shot("detailpc") { vm.selectApp(vm.allApps().first { it.platform == "PC" }) }
        shot("myapps") { vm.selectedApp = null; vm.navTo(Tab.MYAPPS) }
        shot("about") { vm.navTo(Tab.ABOUT) }
        shot("aboutbadge") { vm.selectedApp = null; vm.navTo(Tab.ABOUT); vm.set { it.copy(showcasedBadges = listOf("Legend")) } }
        shot("androidend") { vm.selectedApp = null; vm.navTo(Tab.ANDROID); end() }
        shot("pcend") { vm.navTo(Tab.PC); end() }
        shot("tvend") { vm.navTo(Tab.TV); end() }
        shot("aboutend") { vm.navTo(Tab.ABOUT); end() }
        shot("settings") { vm.openSettings() }
        shot("settingsend") { vm.openSettings(); pump(1500); end(last = true) }
        shot("visuals") { vm.openSettings("visuals") }
        shot("palette") { vm.openSettings("visuals"); vm.set { it.copy(palette = "ocean", theme = "dusk") } }
        shot("xtra") { vm.settingsMenu = "installer" }
        shot("vt") { vm.close(Overlay.SETTINGS); vm.vtTarget = vm.allApps().first { it.platform == "Android" } }
        shot("submit") { vm.vtTarget = null; vm.open(Overlay.SUBMISSION) }
        shot("profile") { vm.close(Overlay.SUBMISSION); vm.openProfileStats() }
        shot("bundle") { vm.close(Overlay.PROFILE_STATS); vm.open(Overlay.CUSTOM_BUNDLE) }
        shot("dark") { vm.close(Overlay.CUSTOM_BUNDLE)
 vm.close(Overlay.SETTINGS); vm.navTo(Tab.ANDROID); vm.set { it.copy(theme = "dark") } }
        shot("profiledark") { vm.openProfileStats() }
        shot("darkdetail") { vm.selectApp(vm.allApps().first { it.platform == "Android" && it.screenshots.isNotEmpty() }) }
        // Redesigned screens: -Pshots=maint,badges,guardian,quest
        shot("maint") { vm.selectedApp = null; vm.overlays.clear(); vm.set { it.copy(theme = "light", localMaintenanceMode = true) } }
        shot("badges") {
            vm.set { it.copy(localMaintenanceMode = false, adWatchCount = 37, dinoHighScore = 450, unlockedBadges = listOf("Dino Rookie")) }; pump(1500)
            vm.openProfileStats(); pump(2500); rule.onAllNodesWithText("Badge collection").onFirst().performClick()
        }
        shot("guardian") { vm.overlays.clear(); pump(1000); vm.openSettings("installer"); pump(2500); rule.onAllNodesWithText("Guardian").onFirst().performClick() }
        shot("silver") { vm.overlays.clear(); vm.set { it.copy(adWatchCount = 150) }; pump(1000); vm.openProfileStats() }
        shot("gold") { vm.overlays.clear(); vm.set { it.copy(adWatchCount = 400) }; pump(1000); vm.openProfileStats() }
        shot("devsettings") { vm.overlays.clear(); vm.set { it.copy(isDevUnlocked = true) }; pump(1000); vm.openSettings("developer"); pump(1500); end(last = true) }
        shot("sign") {
            vm.overlays.clear(); vm.set { it.copy(signatureInk = 1, pilotSignature = "0.10,0.60 0.20,0.30 0.30,0.70 0.40,0.35 0.50,0.65|0.55,0.60 0.70,0.40 0.85,0.62") }; pump(800)
            vm.openProfileStats(); pump(2500); rule.onAllNodesWithText("ORION PILOT", substring = true).onFirst().performClick()
        }
        shot("connect") { vm.overlays.clear(); vm.set { it.copy(localMaintenanceMode = false) }; vm.navTo(Tab.ABOUT); pump(1500); pump(1500); rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).filter(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).onFirst().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1100f) } }
        shot("welcome") { vm.overlays.clear(); vm.set { it.copy(localMaintenanceMode = false, seenNativeWelcome = false) }; vm.migratedFromWeb = true }
        shot("ignored") { vm.set { it.copy(seenNativeWelcome = true, ignoredUpdates = vm.allApps().take(2).associate { a -> a.id to com.orion.store.data.IgnoredUpdate("never") }) }; pump(1000); vm.navTo(Tab.MYAPPS) }
        shot("quest") { vm.overlays.clear(); vm.set { it.copy(adWatchCount = 25) }; pump(1000); vm.open(Overlay.AD_DONATION) }
        shot("compact") { vm.overlays.clear(); vm.tour = false; vm.navTo(Tab.ANDROID); vm.set { it.copy(storeLayout = "classic", compactMode = true, adWatchCount = 0) } }
        shot("ai") { vm.set { it.copy(storeLayout = "modern", compactMode = false) }; pump(1000); vm.open(Overlay.AI) }
        shot("aihero") { vm.set { it.copy(aiKeys = mapOf("gemini" to "k")) } }
        shot("aichat") {
            vm.overlays.remove(Overlay.TUTORIAL)
            val apps = vm.allApps().filter { it.category.contains("Music", true) }.take(3)
            vm.ai.messages += com.orion.store.ui.AiMsg(true, "I want an app to listen to music for free")
            vm.ai.messages += com.orion.store.ui.AiMsg(false, "Here are great free music apps from the catalog. All of them stream without ads.", apps)
        }
        shot("maker") { vm.overlays.clear(); vm.ai.clear(); pump(800); vm.openSettings("visuals"); pump(2500); rule.onAllNodesWithText("Make").onFirst().performScrollTo().performClick() }
        shot("aisettings") { vm.overlays.clear(); vm.set { it.copy(storeLayout = "modern", compactMode = false) }; pump(800); vm.openSettings("interface"); pump(2500)
            rule.onAllNodesWithText("Provider and key").onFirst().performScrollTo().performClick() }
        shot("guidechips") { vm.overlays.clear(); pump(800); vm.open(Overlay.GUIDE); pump(2500); end() }
    }

    /** Small phone with a bigger font, like budget HiOS phones: -Pshots=smallcompact */
    @Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.15f)
    @Test fun smallcompact() {
        assumeTrue("smallcompact" in want)
        val vm = boot(); vm.tour = false
        vm.navTo(Tab.ANDROID); vm.set { it.copy(storeLayout = "classic", compactMode = true) }; pump(3500); captureScreenRoboImage("build/shots/smallcompact.png", roborazziOptions = opts)
    }

    /** Android TV in landscape: -Pshots=tvland */
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
    @Test fun tvland() {
        assumeTrue("tvland" in want)
        val vm = boot()
        vm.navTo(Tab.TV); pump(3500); captureScreenRoboImage("build/shots/tvland.png", roborazziOptions = opts)
    }
}
