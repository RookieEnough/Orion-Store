package com.orion.store;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.core.splashscreen.SplashScreen;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Install the splash screen
        SplashScreen.installSplashScreen(this);

        // Register all plugins before calling super.onCreate()
        registerPlugin(AppTrackerPlugin.class);

        // Now, initialize the Bridge
        super.onCreate(savedInstanceState);

        // Existing performance settings
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getBridge() != null && getBridge().getWebView() != null) {
                WebView webView = getBridge().getWebView();
                WebSettings webSettings = webView.getSettings();
                webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
                webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
                webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
                webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    webSettings.setOffscreenPreRaster(false);
                }
                webView.setVerticalScrollBarEnabled(false);
                webView.setHorizontalScrollBarEnabled(false);
                webView.setBackgroundColor(0x00000000);
            }
        }, 300);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().setLayerType(
                WebView.LAYER_TYPE_HARDWARE, null
            );
        }
    }
}
