package com.orion.store;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
                webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
                webView.getSettings().setRenderPriority(
                    android.webkit.WebSettings.RenderPriority.HIGH
                );
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
