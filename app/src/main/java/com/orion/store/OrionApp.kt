package com.orion.store

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.orion.store.data.Device
import com.orion.store.data.Net
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.orion.store.ui.Store
import java.util.concurrent.TimeUnit

class OrionApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Store.settings is a lateinit: OrionActivity reads it in onStart, which can run before MainViewModel exists
        // (activity restored after process death), so it must be ready before any component starts.
        Store.init(this)
        Net.init(this)
        Device.isTv = getSystemService(android.app.UiModeManager::class.java)?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        val am = getSystemService(android.app.ActivityManager::class.java)
        Device.lowEnd = am.isLowRamDevice || android.app.ActivityManager.MemoryInfo().also(am::getMemoryInfo).totalMem < 4L * 1024 * 1024 * 1024
    }

    /** Remote images fade in, except in Lite. Boost keeps more decoded images in memory, so scrolling back never reloads them. Applies on the next start. */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        Store.init(this); val perf = Store.settings.value.perf
        return ImageLoader.Builder(context).crossfade(if (perf == "lite") 0 else 150)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, when (perf) { "lite" -> .15; "boost" -> .35; else -> .25 }).build() }
            // Images go through Orion's DNS fallback too, without the 8 second whole-call cap that big screenshots on slow data would hit
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Net.client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).readTimeout(20, TimeUnit.SECONDS).build() })) }.build()
    }
}
