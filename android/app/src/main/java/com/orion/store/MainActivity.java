package com.orion.store;

import android.os.Bundle;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 1. Register the AppTracker Plugin
        registerPlugin(AppTrackerPlugin.class);
        
        super.onCreate(savedInstanceState);
        
        // 2. Enable hardware acceleration for WebView programmatically
        // This ensures smooth CSS animations and transitions
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    WebView webView = getBridge().getWebView();
                    
                    // Force hardware layers for smooth animations
                    webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
                    
                    // Additional performance settings
                    webView.getSettings().setRenderPriority(
                        android.webkit.WebSettings.RenderPriority.HIGH
                    );
                    
                    // Enable these for better scrolling performance
                    webView.setVerticalScrollBarEnabled(false);
                    webView.setHorizontalScrollBarEnabled(false);
                    
                    // Optional: Reduce overdraw
                    webView.setBackgroundColor(0x00000000);
                }
            }
        }, 300); // Small delay to ensure WebView is initialized
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Re-enable hardware acceleration if it was disabled
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().setLayerType(
                WebView.LAYER_TYPE_HARDWARE, null
            );
        }
    }
}
