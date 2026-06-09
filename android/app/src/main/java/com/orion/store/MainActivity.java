package com.orion.store;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.WindowCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private SwipeRefreshLayout swipeRefreshLayout;
    private PixelCatRefreshView pixelCatRefreshView;
    private boolean webContentAtTop = true;
    private boolean refreshEligibleSurface = false;
    private boolean pullGestureAllowed = false;
    // Throttle JS bridge calls — evaluateJavascript is expensive
    private long lastTopStateCheck = 0;
    private static final long TOP_STATE_THROTTLE_MS = 120;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Install the splash screen
        SplashScreen.installSplashScreen(this);

        // Register all plugins before calling super.onCreate()
        registerPlugin(AppTrackerPlugin.class);

        // Now, initialize the Bridge
        super.onCreate(savedInstanceState);

        // Draw under the status / navigation bar (true edge-to-edge).
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setNavigationBarDividerColor(0);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(0x00000000);
        getWindow().setNavigationBarColor(0x00000000);

        // Apply WebView optimizations as early as possible — use a near-zero delay
        // so the Bridge is definitely initialized but we don't waste 300ms.
        new Handler(Looper.getMainLooper()).post(() -> {
            if (getBridge() != null && getBridge().getWebView() != null) {
                WebView webView = getBridge().getWebView();
                WebSettings webSettings = webView.getSettings();

                // ── GPU / Rendering Pipeline ──────────────────────────
                // LAYER_TYPE_HARDWARE offloads rendering to the GPU texture layer.
                // This is the single biggest win for scroll & animation smoothness.
                webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);

                // Disable over-scroll glow — saves a draw pass on every edge bounce.
                webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

                // Hide scrollbars entirely (CSS already hides them). Removing them
                // avoids measure/layout invalidation when they would appear/disappear.
                webView.setVerticalScrollBarEnabled(false);
                webView.setHorizontalScrollBarEnabled(false);
                webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);

                // Transparent background — WebView won't draw a default white bg
                // before the page renders, eliminating a flash.
                webView.setBackgroundColor(0x00000000);

                // ── WebSettings ────────────────────────────────────────
                // HIGH render priority tells Chromium to allocate more resources.
                webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);

                // LOAD_DEFAULT uses HTTP cache properly — unchanged assets are
                // served from disk, not re-fetched.
                webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

                // DOM storage is required for IndexedDB (Zustand idb-keyval).
                webSettings.setDomStorageEnabled(true);

                // Disable zoom controls — avoids extra measure/layout pass.
                webSettings.setSupportZoom(false);
                webSettings.setBuiltInZoomControls(false);

                // Allow media without user gesture.
                webSettings.setMediaPlaybackRequiresUserGesture(false);

                // ── Android 8+ Renderer Priority ──────────────────────
                // IMPORTANT + WAIVE_WHEN_INVISIBLE = top priority when visible,
                // releases resources when backgrounded.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    webView.setRendererPriorityPolicy(
                        WebView.RENDERER_PRIORITY_IMPORTANT, true);
                }

                // ── Android 6+ Offscreen Pre-raster ───────────────────
                // false = don't pre-raster tiles that are offscreen. Saves GPU
                // memory and fill-rate on low-end devices. The trade-off is a
                // tiny flash on very fast scrolls, which is imperceptible.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    webSettings.setOffscreenPreRaster(false);
                }

                // ── Critical: Disable text autosizing ─────────────────
                // Android WebView's font-boosting inflates text on first layout,
                // causing a jarring re-layout jump ~200ms after page load.
                // Our CSS already handles responsive typography.
                webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webSettings.setLoadWithOverviewMode(false);
                    webSettings.setUseWideViewPort(true);
                }

                // ── Mixed content & safe browsing ─────────────────────
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    webSettings.setMixedContentMode(
                        android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                }

                // Disable safe browsing lookups — they add latency to every
                // navigation and we only load local assets.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    webSettings.setSafeBrowsingEnabled(false);
                }

                setupPullToRefresh(webView);
            }
        });
    }

    private void setupPullToRefresh(WebView webView) {
        if (swipeRefreshLayout != null || webView.getParent() == null) {
            return;
        }

        ViewGroup parent = (ViewGroup) webView.getParent();
        int webViewIndex = parent.indexOfChild(webView);
        ViewGroup.LayoutParams webViewLayoutParams = webView.getLayoutParams();

        parent.removeView(webView);

        FrameLayout pullContainer = new FrameLayout(this);
        pullContainer.addView(webView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        pixelCatRefreshView = new PixelCatRefreshView(this);
        FrameLayout.LayoutParams catParams = new FrameLayout.LayoutParams(dp(72), dp(72));
        catParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        catParams.topMargin = getSafeRefreshTopOffset();
        pullContainer.addView(pixelCatRefreshView, catParams);

        swipeRefreshLayout = new HomeOnlySwipeRefreshLayout(this, webView);
        swipeRefreshLayout.setLayoutParams(webViewLayoutParams);
        swipeRefreshLayout.setClipToPadding(false);
        swipeRefreshLayout.setClipChildren(false);

        // Move the native progress drawable below the punch-hole/notch. It is made
        // transparent because the custom pixel-cat is the visible refresh indicator.
        int progressStart = getSafeRefreshTopOffset();
        int progressEnd = progressStart + dp(58);
        swipeRefreshLayout.setProgressViewOffset(false, progressStart, progressEnd);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.TRANSPARENT);
        swipeRefreshLayout.setColorSchemeColors(Color.TRANSPARENT);

        // Keep the gesture quick, but start/end far enough below the status cutout.
        swipeRefreshLayout.setDistanceToTriggerSync(dp(1000)); // We manually trigger it, hide native threshold
        swipeRefreshLayout.setSlingshotDistance(dp(152));

        swipeRefreshLayout.addView(pullContainer,
            new SwipeRefreshLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        hideNativeRefreshIndicator();

        // Pull-to-refresh is intentionally locked down:
        // 1) only the home surface, not settings/details/modals
        // 2) only when content is at the top
        // 3) only from the top gesture strip, so hero carousel/update swipes stay native-smooth
        swipeRefreshLayout.setOnChildScrollUpCallback(
            (parentLayout, child) -> !canPullToRefresh(webView));
            
        // We completely bypass swipeRefreshLayout's internal onRefreshListener sequence
        // because its scale animations are too hostile to custom invisble indicators.
        // It is now purely used as a touch interceptor, and we manually trigger below.
        updateWebContentTopState(webView);
        parent.addView(swipeRefreshLayout, webViewIndex);
    }

    private void hideNativeRefreshIndicator() {
        if (swipeRefreshLayout == null) {
            return;
        }

        for (int i = 0; i < swipeRefreshLayout.getChildCount(); i++) {
            View child = swipeRefreshLayout.getChildAt(i);
            if (child == null || child == pixelCatRefreshView) {
                continue;
            }

            if (child instanceof FrameLayout) {
                continue;
            }

            child.setAlpha(0f);
            // CRITICAL: We MUST NOT set visibility to GONE. SwipeRefreshLayout relies on
            // this view's animation completing to trigger the onRefresh() callback!
            // If it's GONE, the animation aborts and the refresh is never triggered.
            // We just remove the elevation so it casts no shadow, and alpha 0 hides it entirely.
            child.setElevation(0f);
        }
    }

    private void updateTopStateIfNeeded(WebView webView, boolean force) {
        long now = System.currentTimeMillis();
        if (force || now - lastTopStateCheck > TOP_STATE_THROTTLE_MS) {
            lastTopStateCheck = now;
            // Fast path: if WebView scrollY > 0, we're definitely not at top
            if (webView.getScrollY() > 2) {
                webContentAtTop = false;
            } else {
                // Optimistic fast-path: if the native ScrollY is 0, set
                // webContentAtTop = true immediately so the very first
                // ACTION_MOVE in a new touch sequence can be intercepted.
                // The JS bridge will correct it asynchronously if needed.
                webContentAtTop = true;
                updateWebContentTopState(webView);
            }
        }
    }

    private boolean canPullToRefresh(WebView webView) {
        // Synchronous fast-path: trust the native scroll position and the
        // last-known eligibility. The JS bridge keeps refreshEligibleSurface
        // up-to-date on every ACTION_DOWN, so it is never dangerously stale.
        return refreshEligibleSurface
            && pullGestureAllowed
            && webContentAtTop
            && webView.getScrollY() <= 2;
    }

    private boolean isInTopPullStrip(float rawY) {
        // Pull-to-refresh gesture must begin near the physical top of the screen,
        // not halfway down the home page where carousels/swim-lanes live.
        return rawY <= getStatusBarHeight() + dp(156);
    }

    private void updateWebContentTopState(WebView webView) {
        // Fast native check first — avoids JS bridge entirely for scrolled state
        if (webView.getScrollY() > 2) {
            webContentAtTop = false;
            return;
        }

        // The JS check does two things:
        // 1) Reads the REAL scroll position from both window AND #root (the app's
        //    actual scroll container has overflow-y:visible but we check it anyway).
        // 2) Detects full-screen modals. We only flag an element as a "modal" if it
        //    is fixed/absolute AND covers >50% of the viewport height. This avoids
        //    false positives from the fixed header bar and bottom navigation.
        webView.evaluateJavascript(
            "(function(){" +
                "var eps=2;" +
                "var rootEl=document.getElementById('root');" +
                "var rootScroll=rootEl?rootEl.scrollTop:0;" +
                "var top=Math.max(document.documentElement.scrollTop||0,document.body.scrollTop||0,window.scrollY||0,rootScroll);" +
                "var vh=window.innerHeight||document.documentElement.clientHeight;" +
                "var modal=false;" +
                "var nodes=document.querySelectorAll('[role=dialog],[class*=Modal],[class*=modal]');" +
                "for(var i=0;i<nodes.length;i++){" +
                    "var el=nodes[i],st=getComputedStyle(el),r=el.getBoundingClientRect();" +
                    "if(st.display!=='none'&&st.visibility!=='hidden'&&+st.opacity!==0" +
                        "&&(st.position==='fixed'||st.position==='absolute')" +
                        "&&r.height>vh*0.5&&r.width>80){modal=true;break;}" +
                "}" +
                "if(!modal&&document.body.classList.contains('lightbox-open')){modal=true;}" +
                "var root=document.documentElement;" +
                "var activeTab=(root.dataset.orionActiveTab||'').toLowerCase();" +
                "var datasetEligible=root.dataset.orionRefreshEligible==='true';" +
                "var tabEligible=activeTab==='android'||activeTab==='tv'||activeTab==='pc';" +
                "var eligible=datasetEligible&&tabEligible&&!modal;" +
                "return JSON.stringify({top:top<=eps,eligible:eligible});" +
            "})()",
            value -> {
                boolean top = value != null && value.contains("\\\"top\\\":true");
                boolean eligible = value != null && value.contains("\\\"eligible\\\":true");
                webContentAtTop = top;
                refreshEligibleSurface = eligible;
            }
        );
    }

    private int getSafeRefreshTopOffset() {
        int baseOffset = dp(52);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                baseOffset += insets.getStableInsetTop();
            } else {
                baseOffset += getStatusBarHeight();
            }
        } else {
            baseOffset += getStatusBarHeight();
        }
        return baseOffset;
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class HomeOnlySwipeRefreshLayout extends SwipeRefreshLayout {
        private final WebView webView;
        private float startX;
        private float startY;
        private boolean gestureMayRefresh;

        HomeOnlySwipeRefreshLayout(android.content.Context context, WebView webView) {
            super(context);
            this.webView = webView;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                startX = event.getRawX();
                startY = event.getRawY();
                pullGestureAllowed = isInTopPullStrip(startY);
                updateTopStateIfNeeded(webView, true);
                gestureMayRefresh = pullGestureAllowed
                    && refreshEligibleSurface
                    && webContentAtTop
                    && webView.getScrollY() <= 2;
                if (!gestureMayRefresh) {
                    return false;
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(event.getRawX() - startX);
                float dy = event.getRawY() - startY;

                // Horizontal-first or diagonal carousel swipes must never be stolen.
                if (dx > dp(10) && dx > Math.abs(dy) * 0.72f) {
                    gestureMayRefresh = false;
                    pullGestureAllowed = false;
                    return false;
                }

                if (dy < dp(8) || !gestureMayRefresh || !canPullToRefresh(webView)) {
                    return false;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gestureMayRefresh = false;
                pullGestureAllowed = false;
            }
            return gestureMayRefresh && super.onInterceptTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            
            if (action == MotionEvent.ACTION_MOVE) {
                float dy = event.getRawY() - startY;
                if (pixelCatRefreshView != null && gestureMayRefresh && dy > 0) {
                    pixelCatRefreshView.setPullProgress(Math.min(1f, dy / dp(120)));
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float dy = event.getRawY() - startY;
                // Manual explicit trigger: If pulled 116dp physically down
                boolean shouldTrigger = (action == MotionEvent.ACTION_UP && dy >= dp(116) && gestureMayRefresh);
                
                gestureMayRefresh = false;
                pullGestureAllowed = false;
                boolean handled = super.onTouchEvent(event);
                
                if (shouldTrigger) {
                    if (pixelCatRefreshView != null) {
                        pixelCatRefreshView.setRefreshing(true);
                    }
                    hideNativeRefreshIndicator();
                    if (webView != null) {
                        webView.evaluateJavascript("window.dispatchEvent(new Event('orion:trigger-refresh'));", null);
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        hideNativeRefreshIndicator();
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                        if (pixelCatRefreshView != null) {
                            pixelCatRefreshView.setRefreshing(false);
                            pixelCatRefreshView.setPullProgress(0f);
                        }
                    }, 800);
                } else if (pixelCatRefreshView != null && !pixelCatRefreshView.refreshing) {
                    pixelCatRefreshView.setPullProgress(0f);
                }
                return handled;
            }
            
            return super.onTouchEvent(event);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getBridge() != null && getBridge().getWebView() != null) {
            WebView webView = getBridge().getWebView();
            webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
            // Reset throttle so next check fires immediately
            lastTopStateCheck = 0;
            updateWebContentTopState(webView);
        }
    }

    private class PixelCatRefreshView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean refreshing = false;
        private float pullProgress = 0f;
        private long refreshStartedAt = 0L;

        PixelCatRefreshView(android.content.Context context) {
            super(context);
            paint.setStyle(Paint.Style.FILL);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setAlpha(0f);
            setScaleX(0.72f);
            setScaleY(0.72f);
            setTranslationY(-dp(10));
        }

        void setPullProgress(float progress) {
            if (refreshing) {
                return;
            }
            pullProgress = progress;
            setAlpha(progress);
            float scale = 0.72f + (0.28f * progress);
            setScaleX(scale);
            setScaleY(scale);
            setTranslationY(-dp(10) + dp(12) * progress);
            invalidate();
        }

        void setRefreshing(boolean isRefreshing) {
            refreshing = isRefreshing;
            if (isRefreshing) {
                refreshStartedAt = System.currentTimeMillis();
                setAlpha(1f);
                setScaleX(1f);
                setScaleY(1f);
                setTranslationY(dp(2));
                postInvalidateOnAnimation();
            } else {
                refreshStartedAt = 0L;
                animate()
                    .alpha(0f)
                    .scaleX(0.72f)
                    .scaleY(0.72f)
                    .translationY(-dp(10))
                    .setDuration(140)
                    .start();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            // Expanded divisor from 18f to 24f to keep px=3dp since canvas is 72dp instead of 54dp.
            float px = Math.min(w, h) / 24f;
            float cx = w / 2f;
            float top = h * 0.12f; // 72*0.12 = 8.64dp (exactly identical to 54*0.16)
            boolean blink = refreshing && ((System.currentTimeMillis() - refreshStartedAt) / 180) % 6 == 0;
            boolean showClosedEyes = !refreshing && pullProgress > 0.08f;
            float bob = refreshing
                ? (float) Math.sin((System.currentTimeMillis() - refreshStartedAt) / 110f) * px * 0.55f
                : 0f;

            canvas.save();
            canvas.translate(0, bob);

            // Soft neon shadow badge behind the cat.
            paint.setColor(Color.argb(70, 124, 58, 237));
            canvas.drawRoundRect(cx - 8.2f * px, top + 1.6f * px, cx + 8.2f * px, top + 15.7f * px, 5f * px, 5f * px, paint);
            paint.setColor(Color.argb(235, 17, 24, 39));
            canvas.drawRoundRect(cx - 7.5f * px, top + px, cx + 7.5f * px, top + 15f * px, 4.2f * px, 4.2f * px, paint);

            // Pixel ears.
            paint.setColor(Color.rgb(31, 41, 55));
            drawPixel(canvas, cx - 6 * px, top + 0 * px, 2, 4, px);
            drawPixel(canvas, cx + 4 * px, top + 0 * px, 2, 4, px);
            paint.setColor(Color.rgb(236, 72, 153));
            drawPixel(canvas, cx - 5 * px, top + 1 * px, 1, 2, px);
            drawPixel(canvas, cx + 5 * px, top + 1 * px, 1, 2, px);

            // Pixel face.
            paint.setColor(Color.rgb(249, 250, 251));
            drawPixel(canvas, cx - 6 * px, top + 4 * px, 12, 8, px);
            drawPixel(canvas, cx - 5 * px, top + 3 * px, 10, 1, px);
            drawPixel(canvas, cx - 4 * px, top + 12 * px, 8, 1, px);

            // Head outline pixels.
            paint.setColor(Color.rgb(31, 41, 55));
            drawPixel(canvas, cx - 7 * px, top + 5 * px, 1, 6, px);
            drawPixel(canvas, cx + 6 * px, top + 5 * px, 1, 6, px);
            drawPixel(canvas, cx - 5 * px, top + 13 * px, 10, 1, px);

            // Eyes blink while refreshing.
            paint.setColor(Color.rgb(17, 24, 39));
            if (showClosedEyes) {
                drawPixel(canvas, cx - 4 * px, top + 8 * px, 3, 1, px);
                drawPixel(canvas, cx + 1 * px, top + 8 * px, 3, 1, px);
            } else if (blink) {
                drawPixel(canvas, cx - 4 * px, top + 8 * px, 3, 1, px);
                drawPixel(canvas, cx + 1 * px, top + 8 * px, 3, 1, px);
            } else {
                drawPixel(canvas, cx - 4 * px, top + 7 * px, 2, 2, px);
                drawPixel(canvas, cx + 2 * px, top + 7 * px, 2, 2, px);
            }

            // Nose and tiny mouth.
            paint.setColor(Color.rgb(236, 72, 153));
            drawPixel(canvas, cx - 0.5f * px, top + 9.5f * px, 1, 1, px);
            paint.setColor(Color.rgb(17, 24, 39));
            drawPixel(canvas, cx - 1.5f * px, top + 11 * px, 1, 1, px);
            drawPixel(canvas, cx + 0.5f * px, top + 11 * px, 1, 1, px);

            // Whiskers.
            paint.setColor(Color.rgb(6, 182, 212));
            drawPixel(canvas, cx - 7 * px, top + 9 * px, 3, 1, px);
            drawPixel(canvas, cx + 4 * px, top + 9 * px, 3, 1, px);
            paint.setColor(Color.rgb(124, 58, 237));
            drawPixel(canvas, cx - 7 * px, top + 11 * px, 2, 1, px);
            drawPixel(canvas, cx + 5 * px, top + 11 * px, 2, 1, px);

            canvas.restore();

            if (refreshing) {
                postInvalidateOnAnimation();
            }
        }

        private void drawPixel(Canvas canvas, float x, float y, float width, float height, float px) {
            canvas.drawRect(x, y, x + width * px, y + height * px, paint);
        }
    }
}
