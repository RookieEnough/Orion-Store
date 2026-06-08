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
    private boolean refreshEligibleSurface = true;
    private float pullStartX = 0f;
    private float pullStartY = 0f;
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
        FrameLayout.LayoutParams catParams = new FrameLayout.LayoutParams(dp(54), dp(54));
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
        swipeRefreshLayout.setDistanceToTriggerSync(dp(96));
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

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (pixelCatRefreshView != null) {
                pixelCatRefreshView.setRefreshing(true);
            }
            hideNativeRefreshIndicator();
            webView.reload();
            // Faster refresh cycle — 800ms instead of 1200ms
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
        });

        // Drive only the custom cat progress here. The actual refresh intercept is
        // handled by HomeOnlySwipeRefreshLayout so horizontal carousel gestures are not stolen.
        webView.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                pullStartX = event.getRawX();
                pullStartY = event.getRawY();
                pullGestureAllowed = isInTopPullStrip(event.getRawY());
                updateTopStateIfNeeded(webView, true);
            } else if (action == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(event.getRawX() - pullStartX);
                float dy = event.getRawY() - pullStartY;
                if (dx > dp(14) && dx > Math.abs(dy) * 0.75f) {
                    pullGestureAllowed = false;
                }
                updateTopStateIfNeeded(webView, false);
                if (pixelCatRefreshView != null && pullGestureAllowed && canPullToRefresh(webView) && dy > 0) {
                    pixelCatRefreshView.setPullProgress(Math.min(1f, dy / dp(120)));
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                pullGestureAllowed = false;
                if (pixelCatRefreshView != null && !swipeRefreshLayout.isRefreshing()) {
                    pixelCatRefreshView.setPullProgress(0f);
                }
            }
            return false;
        });

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
            child.setVisibility(View.GONE);
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
                updateWebContentTopState(webView);
            }
        }
    }

    private boolean canPullToRefresh(WebView webView) {
        return refreshEligibleSurface
            && pullGestureAllowed
            && webContentAtTop
            && webView.getScrollY() <= 2;
    }

    private boolean isInTopPullStrip(float rawY) {
        // This is the main fix for the hero carousel/update area: a pull-to-refresh
        // gesture must begin near the physical top, not halfway down the home page.
        return rawY <= getStatusBarHeight() + dp(156);
    }

    private void updateWebContentTopState(WebView webView) {
        // Fast native check first — avoids JS bridge entirely for scrolled state
        if (webView.getScrollY() > 2) {
            webContentAtTop = false;
            return;
        }

        // Keep this JS lightweight but smarter than a plain document scroll check.
        // It blocks refresh whenever a fixed modal/sheet/dialog is visible, which covers
        // Settings, app details, and other overlays. It also checks common scroll containers.
        webView.evaluateJavascript(
            "(function(){" +
                "var eps=2;" +
                "var root=document.documentElement;" +
                "var top=Math.max(document.documentElement.scrollTop||0,document.body.scrollTop||0,window.scrollY||0);" +
                "var modal=false;" +
                "var nodes=document.querySelectorAll('[role=dialog],.modal,[class*=Modal],[class*=modal],[class*=fixed],.fixed');" +
                "for(var i=0;i<nodes.length;i++){" +
                    "var el=nodes[i],st=getComputedStyle(el),r=el.getBoundingClientRect();" +
                    "if(st.display!=='none'&&st.visibility!=='hidden'&&+st.opacity!==0&&r.width>80&&r.height>80&&st.position==='fixed'){modal=true;break;}" +
                "}" +
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
                pullStartX = startX;
                pullStartY = startY;
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
            float px = Math.min(w, h) / 18f;
            float cx = w / 2f;
            float top = h * 0.16f;
            boolean blink = refreshing && ((System.currentTimeMillis() - refreshStartedAt) / 180) % 6 == 0;
            float bob = refreshing ? (float) Math.sin((System.currentTimeMillis() - refreshStartedAt) / 110f) * px * 0.55f : 0f;

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
            if (blink) {
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
