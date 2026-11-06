package com.orion.store

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.Surface
import com.orion.store.data.Device
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.orion.store.platform.ResultHost
import com.orion.store.ui.MainViewModel
import com.orion.store.ui.OrionRoot
import com.orion.store.ui.Store
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OrionActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var pending: ((Int, Intent?) -> Unit)? = null
    private val results = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> pending?.also { pending = null }?.invoke(r.resultCode, r.data) }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        Store.init(applicationContext)
        installSplashScreen().run {
            // Hold the splash until the cached catalog has painted (capped), so the first frame is the store, not a skeleton.
            val t0 = SystemClock.uptimeMillis()
            setKeepOnScreenCondition { vm.loading && SystemClock.uptimeMillis() - t0 < 1200 }
            setOnExitAnimationListener { p ->
                val emphasized = PathInterpolator(.3f, 0f, .8f, .15f) // M3 emphasized accelerate
                runCatching { p.iconView.animate().scaleX(1.6f).scaleY(1.6f).setInterpolator(emphasized).setDuration(320).start() }
                p.view.animate().alpha(0f).setInterpolator(emphasized).setDuration(320).withEndAction(p::remove).start()
            }
        }
        // Fully transparent bars on every Android version: the default scrim behind three-button navigation showed as a dark band
        // under the page (most visible in Recents on Android 12 tablets). Orion paints behind both bars and pads with insets.
        val clear = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(clear, clear)
        if (Build.VERSION.SDK_INT >= 29) { window.isNavigationBarContrastEnforced = false; window.isStatusBarContrastEnforced = false }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        // Phones stay portrait; tablets, foldables and TVs follow their screen. A manifest lock forced tablets into a letterboxed portrait window.
        lockOrientation()
        ResultHost.launch = { i, cb -> pending = cb; results.launch(i) }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { Store.settings.flow.map { it.highRefreshRate || it.perf == "boost" }.distinctUntilChanged().collect { vm.tracker.setHighRefreshRate(this@OrionActivity, it) } }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { Store.settings.flow.map { it.immersive }.distinctUntilChanged().collect { applyBars(it) } }
        }
        if (savedInstanceState == null) handle(intent)
        setContent { OrionRoot(vm) }
        // Compose paints every pixel from the first frame on, so the window background underneath is never seen: drop it to save a full-screen fill per frame
        window.decorView.post { window.setBackgroundDrawable(null) }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }

    private fun lockOrientation() {
        requestedOrientation = if (phoneShaped()) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig); lockOrientation()
    }

    override fun onResume() { super.onResume(); vm.onResume() }
    override fun onStart() { super.onStart(); AutoUpdate.uiVisible = true }
    override fun onStop() { AutoUpdate.uiVisible = false; super.onStop() }

    // Full screen (Settings, on by default): status bar hidden, a swipe from the top peeks it. Re-applied on focus since old APIs drop the flag.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyBars(Store.settings.flow.value.immersive)
    }

    private fun applyBars(immersive: Boolean) = WindowCompat.getInsetsController(window, window.decorView).run {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersive) hide(WindowInsetsCompat.Type.statusBars()) else show(WindowInsetsCompat.Type.statusBars())
    }

    override fun onDestroy() { if (ResultHost.launch != null && isFinishing) ResultHost.launch = null; super.onDestroy() }

    /** A small touch screen that is portrait at its natural rotation. TVs report about 540dp as their smallest width, so the
     *  width check alone locked them sideways; boxes that skip the leanback flag still have a landscape natural rotation.
     *  Split screen and floating windows never lock: a tablet's half window reads as a phone, and some skins (Huawei) refuse
     *  to multitask an app that asks for portrait. The width is the whole display's, not the window's, for the same reason. */
    private fun phoneShaped(): Boolean {
        val c = resources.configuration
        if (Build.VERSION.SDK_INT >= 24 && isInMultiWindowMode) return false
        val smallest = if (Build.VERSION.SDK_INT >= 30) windowManager.maximumWindowMetrics.bounds.let { minOf(it.width(), it.height()) / resources.displayMetrics.density }.toInt()
            else c.smallestScreenWidthDp
        if (Device.isTv || !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) || smallest >= 600) return false
        @Suppress("DEPRECATION") val rot = (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay)?.rotation ?: Surface.ROTATION_0
        val sideways = rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270
        return (c.orientation == Configuration.ORIENTATION_LANDSCAPE) == sideways
    }

    private fun handle(i: Intent?) {
        vm.deepLink(i?.data); vm.onNotificationIntent(i)
        if (i?.action == Intent.ACTION_SHOW_APP_INFO) i.getStringExtra(Intent.EXTRA_PACKAGE_NAME)?.let(vm::showAppInfo)
    }
}
