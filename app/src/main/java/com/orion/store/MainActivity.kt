package com.orion.store

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var pending: ((Int, Intent?) -> Unit)? = null
    private val results = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> pending?.also { pending = null }?.invoke(r.resultCode, r.data) }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ResultHost.launch = { i, cb -> pending = cb; results.launch(i) }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { Store.settings.flow.map { it.highRefreshRate }.distinctUntilChanged().collect { vm.tracker.setHighRefreshRate(this@MainActivity, it) } }
        }
        if (savedInstanceState == null) handle(intent)
        setContent { OrionRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }

    override fun onResume() { super.onResume(); vm.onResume() }

    // Full screen: status bar stays hidden, a swipe from the top peeks it. Re-applied on focus since old APIs drop the flag.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onDestroy() { if (ResultHost.launch != null && isFinishing) ResultHost.launch = null; super.onDestroy() }

    private fun handle(i: Intent?) { vm.deepLink(i?.data); vm.showAppInfo(i); vm.onNotificationIntent(i) }
}
