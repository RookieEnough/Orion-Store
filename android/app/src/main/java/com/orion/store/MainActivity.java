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
import android.view.ViewConfiguration;
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
    private long lastTopStateCheck = 0;
    private static final long TOP_STATE_THROTTLE_MS = 120;
    private static final float VERTICAL_PULL_RATIO = 1.18f;
    private static final float HORIZONTAL_REJECT_RATIO = 0.9f;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        registerPlugin(AppTrackerPlugin.class);
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setNavigationBarDividerColor(0);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(0x00000000);
        getWindow().setNavigationBarColor(0x00000000);

        new Handler(Looper.getMainLooper()).post(() -> {
            if (getBridge() != null && getBridge().getWebView() != null) {
                WebView webView = getBridge().getWebView();
                WebSettings webSettings = webView.getSettings();

                webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
                webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
                webView.setVerticalScrollBarEnabled(false);
                webView.setHorizontalScrollBarEnabled(false);
                webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
                webView.setBackgroundColor(0x00000000);

                webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
                webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
                webSettings.setDomStorageEnabled(true);
                webSettings.setSupportZoom(false);
                webSettings.setBuiltInZoomControls(false);
                webSettings.setMediaPlaybackRequiresUserGesture(false);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    webSettings.setOffscreenPreRaster(false);
                }

                webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webSettings.setLoadWithOverviewMode(false);
                    webSettings.setUseWideViewPort(true);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    webSettings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                }

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

        int progressStart = getSafeRefreshTopOffset();
        int progressEnd = progressStart + dp(58);
        swipeRefreshLayout.setProgressViewOffset(false, progressStart, progressEnd);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.TRANSPARENT);
        swipeRefreshLayout.setColorSchemeColors(Color.TRANSPARENT);
        swipeRefreshLayout.setDistanceToTriggerSync(dp(1000));
        swipeRefreshLayout.setSlingshotDistance(dp(152));

        swipeRefreshLayout.addView(pullContainer,
            new SwipeRefreshLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        hideNativeRefreshIndicator();
        swipeRefreshLayout.setOnChildScrollUpCallback((parentLayout, child) -> !canPullToRefresh(webView));
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
            child.setElevation(0f);
        }
    }

    private void updateTopStateIfNeeded(WebView webView, boolean force) {
        long now = System.currentTimeMillis();
        if (force || now - lastTopStateCheck > TOP_STATE_THROTTLE_MS) {
            lastTopStateCheck = now;
            if (webView.getScrollY() > 2) {
                webContentAtTop = false;
            } else {
                webContentAtTop = true;
                updateWebContentTopState(webView);
            }
        }
    }

    private boolean canPullToRefresh(WebView webView) {
        return refreshEligibleSurface && webContentAtTop && webView.getScrollY() <= 2;
    }

    private void updateWebContentTopState(WebView webView) {
        if (webView.getScrollY() > 2) {
            webContentAtTop = false;
            return;
        }

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
                "var character=(root.dataset.orionRefreshCharacter||'cat').toLowerCase();" +
                "var tabEligible=activeTab==='android'||activeTab==='tv'||activeTab==='pc';" +
                "var eligible=datasetEligible&&tabEligible&&!modal;" +
                "return JSON.stringify({top:top<=eps,eligible:eligible,character:character});" +
            "})()",
            value -> {
                boolean top = value != null && value.contains("\\\"top\\\":true");
                boolean eligible = value != null && value.contains("\\\"eligible\\\":true");
                webContentAtTop = top;
                refreshEligibleSurface = eligible;
                if (pixelCatRefreshView != null && value != null) {
                    pixelCatRefreshView.setCharacter(extractRefreshCharacter(value));
                }
            }
        );
    }

    private String extractRefreshCharacter(String value) {
        String[] known = new String[] {
            "cat", "dog", "pokeball", "shield", "owl", "robot", "ghost", "kitty", "bunny", "batman", "fox", "panda", "pikachu"
        };
        for (String id : known) {
            if (value.contains("\\\"character\\\":\\\"" + id + "\\\"")) {
                return normalizeRefreshCharacter(id);
            }
        }
        return "cat";
    }

    private String normalizeRefreshCharacter(String characterId) {
        if (characterId == null) {
            return "cat";
        }
        switch (characterId.toLowerCase()) {
            case "cat":
            case "dog":
            case "pokeball":
            case "shield":
            case "owl":
            case "robot":
            case "ghost":
            case "kitty":
            case "bunny":
            case "batman":
                return characterId.toLowerCase();
            case "fox":
                return "batman";
            case "panda":
                return "cat";
            case "pikachu":
                return "pokeball";
            default:
                return "cat";
        }
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
        private final int touchSlop;
        private boolean gestureStartedAtTop;
        private boolean gestureMayRefresh;
        private boolean gestureCommitted;
        private boolean gestureRejected;

        HomeOnlySwipeRefreshLayout(android.content.Context context, WebView webView) {
            super(context);
            this.webView = webView;
            this.touchSlop = Math.max(dp(8), ViewConfiguration.get(context).getScaledTouchSlop());
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                startX = event.getRawX();
                startY = event.getRawY();
                gestureCommitted = false;
                gestureRejected = false;
                updateTopStateIfNeeded(webView, true);
                gestureStartedAtTop = canPullToRefresh(webView);
                gestureMayRefresh = gestureStartedAtTop;
                super.onInterceptTouchEvent(event);
                if (!gestureStartedAtTop) {
                    return false;
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(event.getRawX() - startX);
                float dy = event.getRawY() - startY;
                float absDy = Math.abs(dy);

                if (gestureRejected || !gestureStartedAtTop || !canPullToRefresh(webView)) {
                    gestureMayRefresh = false;
                    gestureCommitted = false;
                    return false;
                }

                if (absDy > touchSlop && dy < 0) {
                    gestureMayRefresh = false;
                    gestureCommitted = false;
                    gestureRejected = true;
                    return false;
                }

                if (dx > touchSlop && dx > absDy * HORIZONTAL_REJECT_RATIO) {
                    gestureMayRefresh = false;
                    gestureCommitted = false;
                    gestureRejected = true;
                    return false;
                }

                if (!gestureCommitted) {
                    if (dy > touchSlop && dy > dx * VERTICAL_PULL_RATIO) {
                        gestureCommitted = true;
                    } else {
                        return false;
                    }
                }

                if (dy <= 0) {
                    return false;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gestureStartedAtTop = false;
                gestureMayRefresh = false;
                gestureCommitted = false;
                gestureRejected = false;
            }
            if (!gestureMayRefresh || !gestureCommitted) {
                return false;
            }
            super.onInterceptTouchEvent(event);
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_MOVE) {
                float dy = event.getRawY() - startY;
                if (pixelCatRefreshView != null && gestureCommitted && gestureMayRefresh && dy > 0) {
                    pixelCatRefreshView.setPullProgress(Math.min(1f, dy / dp(120)));
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float dy = event.getRawY() - startY;
                boolean shouldTrigger = (action == MotionEvent.ACTION_UP && dy >= dp(116) && gestureCommitted && gestureMayRefresh);

                gestureStartedAtTop = false;
                gestureMayRefresh = false;
                gestureCommitted = false;
                gestureRejected = false;
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
            lastTopStateCheck = 0;
            updateWebContentTopState(webView);
        }
    }

    private class PixelCatRefreshView extends View {
        private static final String[] CAT_GRID = new String[] {
            ".b............b.",
            ".bb..........bb.",
            ".beb........beb.",
            ".bebb......bbeb.",
            ".bbbbbbbbbbbbbb.",
            ".bbbbabbbbabbbb.",
            ".bbbbbbbbbbbbbb.",
            ".bbedbbbbbbedbb.",
            ".bbddbbbbbbddbb.",
            ".bbbbcceeccbbbb.",
            "dbbbbcdccdcbbbbd",
            "dbbbbccddccbbbbd",
            ".bbbbbccccbbbbb.",
            ".bbbbbbbbbbbbbb.",
            "..bbbbbbbbbbbb..",
            "....bbbbbbbb...."
        };
        private static final String[] DOG_GRID = new String[] {
            ".aa......aa.",
            ".aabbbbbbaa.",
            ".aabbbbbbaa.",
            ".aabbbbbbaa.",
            ".abddbbddba.",
            ".abddbbddba.",
            ".abbccccbba.",
            ".abccddccba.",
            ".abcceeccba.",
            ".abbccccbba.",
            "..abbbbbba..",
            "...aaaaaa..."
        };
        private static final String[] POKEBALL_REST_GRID = new String[] {
            ".....aaaaaa.....",
            "...aaccccccaa...",
            "..acccccccccca..",
            ".acccccccccccca.",
            ".acccccccccccca.",
            "acccccaaaaccccca",
            "abbbbaffffabbbba",
            "aaaaaafhhfaaaaaa",
            "aaaaaafhhfaaaaaa",
            "aeeeeaffffaeeeea",
            "aeeeeeaaaaeeeeea",
            ".aeeeeeeeeeeeea.",
            ".aeeeeeeeeeeeea.",
            "..aeeeeeeeeeea..",
            "...aaeeeeeeaa...",
            ".....aaaaaa....."
        };
        private static final String[] POKEBALL_SPARK_GRID = new String[] {
            ".g...aaaaaa...g.",
            "gggaaccccccaaggg",
            ".gaccccccccccag.",
            ".acccccccccccca.",
            ".acccccccccccca.",
            "acccccaaaaccccca",
            "abbbbaffffabbbba",
            "aaaaaafhhfaaaaaa",
            "aaaaaafhhfaaaaaa",
            "aeeeeaffffaeeeea",
            "aeeeeeaaaaeeeeea",
            ".aeeeeeeeeeeeea.",
            ".aeeeeeeeeeeeea.",
            "g.aeeeeeeeeeea.g",
            "...aaeeeeeeaa...",
            "..g..aaaaaa..g.."
        };
        private static final String[] SHIELD_GRID = new String[] {
            ".....bbbbbb.....",
            "...bbbbbbbbbb...",
            "..bbbccccccbbb..",
            ".bbccccbbccccbb.",
            ".bbccbbbbbbccbb.",
            "bbccbbddddbbccbb",
            "bccbbddeeddbbccb",
            "bccbbeeeeeebbccb",
            "bccbbdeeeedbbccb",
            "bccbbdeddedbbccb",
            "bbccbbddddbbccbb",
            ".bbccbbbbbbccbb.",
            ".bbccccbbccccbb.",
            "..bbbccccccbbb..",
            "...bbbbbbbbbb...",
            ".....bbbbbb....."
        };
        private static final String[] SHIELD_REST_GRID = new String[] {
            ".....bbbbbb.....",
            "...bbbbbbbbbb...",
            "..bbbccccccbbb..",
            ".bbccccbbccccbb.",
            ".bbccbbbbbbccbb.",
            "bbccbbddddbbccbb",
            "bccbbddxxddbbccb",
            "bccbbxxxxxxbbccb",
            "bccbbdxxxxdbbccb",
            "bccbbdxdeddbbccb",
            "bbccbbddddbbccbb",
            ".bbccbbbbbbccbb.",
            ".bbccccbbccccbb.",
            "..bbbccccccbbb..",
            "...bbbbbbbbbb...",
            ".....bbbbbb....."
        };
        private static final String[] OWL_GRID = new String[] {
            ".b........b.",
            ".bb......bb.",
            ".bbbbbbbbbb.",
            ".bcccbbcccb.",
            ".bcdcbbcdcb.",
            ".bcdcbbcdcb.",
            ".bcccbbcccb.",
            ".bbbbeebbbb.",
            ".bbccccccbb.",
            "..bccccccb..",
            "...bbbbbb...",
            "...e....e..."
        };
        private static final String[] ROBOT_GRID = new String[] {
            ".....cc.....",
            ".....aa.....",
            ".aaaaaaaaaa.",
            ".abbbbbbbba.",
            ".abccbbccba.",
            ".abccbbccba.",
            ".abbbbbbbba.",
            ".abeeeeeeba.",
            ".abbbbbbbba.",
            ".aaaaaaaaaa.",
            "....aaaa....",
            "............"
        };
        private static final String[] GHOST_GRID = new String[] {
            "....bbbb....",
            "..bbbbbbbb..",
            ".bbbbbbbbbb.",
            ".bbccbbccbb.",
            ".bbccbbccbb.",
            ".bebbbbbbeb.",
            ".bbbbccbbbb.",
            ".bbbbbbbbbb.",
            ".bbbbbbbbbb.",
            ".bbbbbbbbbb.",
            ".bb..bb..bb.",
            "............"
        };
        private static final String[] KITTY_GRID = new String[] {
            "..b......b..",
            ".bbb....bbb.",
            "ccbbbbbbbbb.",
            "cccbbbbbbbbb",
            "bbbbbbbbbbbb",
            "abbabbbbabba",
            "bbbabbbbabbb",
            "abbbbddbbbba",
            "bbbbbbbbbbbb",
            ".bbbbbbbbbb.",
            "..bbbbbbbb..",
            "............"
        };
        private static final String[] BUNNY_GRID = new String[] {
            "..b......b..",
            ".bcb....bcb.",
            ".bcb....bcb.",
            ".bbb....bbb.",
            "..bbbbbbbb..",
            ".bbbbbbbbbb.",
            ".bbaabbaabb.",
            ".bbaabbaabb.",
            ".bebbddbbeb.",
            ".bbbbbbbbbb.",
            "..bbbbbbbb..",
            "...bbbbbb..."
        };
        private static final String[] BATMAN_FACE_OPEN_GRID = new String[] {
            "................",
            "..b..........b..",
            "..bb........bb..",
            "..bbb......bbb..",
            "..bbbbbbbbbbbb..",
            "..bbbbbbbbbbbb..",
            "..bbcbbbbbbcbb..",
            "..bbccbbbbccbb..",
            "..bbcccbbcccbb..",
            "..bbbbbbbbbbbb..",
            "..bbbbbbbbbbbb..",
            "..bddddddddddb..",
            "..bdddbbbbdddb..",
            "...bddddddddb...",
            "....bbbbbbbb....",
            "................"
        };
        private static final String[] BATMAN_FACE_CLOSED_GRID = new String[] {
            "................",
            "..b..........b..",
            "..bb........bb..",
            "..bbb......bbb..",
            "..bbbbbbbbbbbb..",
            "..bbbbbbbbbbbb..",
            "..bbbbbbbbbbbb..",
            "..bbbbbbbbbbbb..",
            "..bbcccbbcccbb..",
            "..bbbbbbbbbbbb..",
            "..bbbbbbbbbbbb..",
            "..bddddddddddb..",
            "..bdddbbbbdddb..",
            "...bddddddddb...",
            "....bbbbbbbb....",
            "................"
        };
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean refreshing = false;
        private float pullProgress = 0f;
        private long refreshStartedAt = 0L;
        private long shieldSpinStartedAt = 0L;
        private String characterId = "cat";

        PixelCatRefreshView(android.content.Context context) {
            super(context);
            paint.setStyle(Paint.Style.FILL);
            paint.setAntiAlias(false);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setAlpha(0f);
            setScaleX(0.72f);
            setScaleY(0.72f);
            setTranslationY(-dp(14));
        }

        void setCharacter(String nextCharacterId) {
            String normalizedCharacterId = normalizeRefreshCharacter(nextCharacterId);
            if (normalizedCharacterId.equals(characterId)) {
                return;
            }
            characterId = normalizedCharacterId;
            invalidate();
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
            setTranslationY(-dp(14) + dp(10) * progress);
            invalidate();
        }

        void setRefreshing(boolean isRefreshing) {
            refreshing = isRefreshing;
            if (isRefreshing) {
                refreshStartedAt = System.currentTimeMillis();
                shieldSpinStartedAt = "shield".equals(characterId) ? refreshStartedAt : 0L;
                setAlpha(1f);
                setScaleX(1f);
                setScaleY(1f);
                setTranslationY(-dp(8));
                postInvalidateOnAnimation();
            } else {
                refreshStartedAt = 0L;
                shieldSpinStartedAt = 0L;
                animate()
                    .alpha(0f)
                    .scaleX(0.72f)
                    .scaleY(0.72f)
                    .translationY(-dp(14))
                    .setDuration(140)
                    .start();
            }
        }

        private boolean isAnimalCharacter() {
            return "cat".equals(characterId)
                || "dog".equals(characterId)
                || "owl".equals(characterId)
                || "ghost".equals(characterId)
                || "kitty".equals(characterId)
                || "bunny".equals(characterId);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            boolean isPokeball = "pokeball".equals(characterId);
            boolean isShield = "shield".equals(characterId);
            boolean showClosedEyes = !refreshing && pullProgress > 0.08f && !isPokeball;
            String[] grid = getGridForState(showClosedEyes, refreshing);
            int rows = Math.max(1, grid.length);
            int cols = Math.max(1, getGridWidth(grid));
            int spriteContentSize = dp(56);
            float basePx = Math.max(2f, (float) Math.floor(Math.min((spriteContentSize - dp(8)) / cols, (spriteContentSize - dp(8)) / rows)));
            float px = isAnimalCharacter() ? Math.max(2f, basePx * 0.88f) : basePx;
            float bob = refreshing
                ? (float) Math.sin((System.currentTimeMillis() - refreshStartedAt) / 110f) * px * 0.55f
                : 0f;
            float shakeX = 0f;
            float rotation = 0f;

            if (isPokeball && !refreshing && pullProgress > 0.08f) {
                shakeX = (float) Math.sin(System.currentTimeMillis() / 55f) * px * (0.2f + pullProgress * 0.25f);
            } else if (isPokeball && refreshing) {
                shakeX = (float) Math.sin((System.currentTimeMillis() - refreshStartedAt) / 38f) * px * 0.42f;
            }

            if (isShield && shieldSpinStartedAt > 0L) {
                long elapsed = System.currentTimeMillis() - shieldSpinStartedAt;
                if (elapsed < 440L) {
                    float progress = elapsed / 440f;
                    float eased = 1f - (float) Math.pow(1f - progress, 3);
                    rotation = 540f * eased;
                }
            }

            canvas.save();
            canvas.translate(shakeX, bob);
            if (rotation != 0f) {
                canvas.rotate(rotation, w / 2f, h / 2f);
            }
            drawSelectedCharacter(canvas, grid, px);
            canvas.restore();

            if (refreshing || (isPokeball && pullProgress > 0.08f) || (isShield && shieldSpinStartedAt > 0L)) {
                postInvalidateOnAnimation();
            }
        }

        private int getGridWidth(String[] grid) {
            int width = 0;
            for (String row : grid) {
                if (row != null && row.length() > width) {
                    width = row.length();
                }
            }
            return width;
        }

        private String[] cloneWithRow(String[] source, int rowIndex, String row) {
            String[] copy = source.clone();
            copy[rowIndex] = row;
            return copy;
        }

        private String[] getGridForState(boolean closedEyes, boolean isRefreshing) {
            switch (characterId) {
                case "dog":
                    return closedEyes ? cloneWithRow(DOG_GRID, 4, ".abbbbbbbba.") : DOG_GRID;
                case "pokeball":
                    return isRefreshing ? POKEBALL_SPARK_GRID : POKEBALL_REST_GRID;
                case "shield":
                    return SHIELD_GRID;
                case "owl":
                    return closedEyes ? cloneWithRow(OWL_GRID, 4, ".bcccbbcccb.") : OWL_GRID;
                case "robot":
                    return closedEyes ? cloneWithRow(ROBOT_GRID, 4, ".abbbbbbbba.") : ROBOT_GRID;
                case "ghost":
                    return closedEyes ? cloneWithRow(GHOST_GRID, 3, ".bbbbbbbbbb.") : GHOST_GRID;
                case "kitty":
                    return closedEyes ? cloneWithRow(KITTY_GRID, 5, "abbbbbbbbbba") : KITTY_GRID;
                case "bunny":
                    return closedEyes ? cloneWithRow(BUNNY_GRID, 6, ".bbbbbbbbbb.") : BUNNY_GRID;
                case "batman":
                    return closedEyes ? BATMAN_FACE_CLOSED_GRID : BATMAN_FACE_OPEN_GRID;
                case "cat":
                default:
                    return closedEyes ? cloneWithRow(CAT_GRID, 5, ".bbbbddbbddbbb.") : CAT_GRID;
            }
        }

        private int resolveColor(char cell) {
            switch (characterId) {
                case "dog":
                    switch (cell) {
                        case 'a': return Color.parseColor("#7c2d12");
                        case 'b': return Color.parseColor("#d97706");
                        case 'c': return Color.parseColor("#fef3c7");
                        case 'd': return Color.parseColor("#111827");
                        case 'e': return Color.parseColor("#ef4444");
                        default: return Color.TRANSPARENT;
                    }
                case "pokeball":
                    switch (cell) {
                        case 'a': return Color.parseColor("#111827");
                        case 'b': return Color.parseColor("#b91c1c");
                        case 'c': return Color.parseColor("#ef4444");
                        case 'd': return Color.parseColor("#111827");
                        case 'e': return Color.parseColor("#f8fafc");
                        case 'f': return Color.parseColor("#e5e7eb");
                        case 'g': return Color.parseColor("#fde68a");
                        case 'h': return Color.parseColor("#ffffff");
                        default: return Color.TRANSPARENT;
                    }
                case "shield":
                    switch (cell) {
                        case 'b': return Color.parseColor("#dc2626");
                        case 'c': return Color.parseColor("#f1f5f9");
                        case 'd': return Color.parseColor("#1d4ed8");
                        case 'e': return Color.parseColor("#ffffff");
                        case 'x': return Color.parseColor("#94a3b8");
                        default: return Color.TRANSPARENT;
                    }
                case "owl":
                    switch (cell) {
                        case 'b': return Color.parseColor("#0ea5e9");
                        case 'c': return Color.parseColor("#fef3c7");
                        case 'd': return Color.parseColor("#111827");
                        case 'e': return Color.parseColor("#f59e0b");
                        default: return Color.TRANSPARENT;
                    }
                case "robot":
                    switch (cell) {
                        case 'a': return Color.parseColor("#334155");
                        case 'b': return Color.parseColor("#94a3b8");
                        case 'c': return Color.parseColor("#22d3ee");
                        case 'e': return Color.parseColor("#f59e0b");
                        default: return Color.TRANSPARENT;
                    }
                case "ghost":
                    switch (cell) {
                        case 'b': return Color.parseColor("#f8fafc");
                        case 'c': return Color.parseColor("#111827");
                        case 'e': return Color.parseColor("#f472b6");
                        default: return Color.TRANSPARENT;
                    }
                case "kitty":
                    switch (cell) {
                        case 'a': return Color.parseColor("#111827");
                        case 'b': return Color.parseColor("#f8fafc");
                        case 'c': return Color.parseColor("#ef4444");
                        case 'd': return Color.parseColor("#facc15");
                        default: return Color.TRANSPARENT;
                    }
                case "bunny":
                    switch (cell) {
                        case 'a': return Color.parseColor("#111827");
                        case 'b': return Color.parseColor("#f8fafc");
                        case 'c': return Color.parseColor("#f9a8d4");
                        case 'd': return Color.parseColor("#ec4899");
                        case 'e': return Color.parseColor("#fda4af");
                        default: return Color.TRANSPARENT;
                    }
                case "batman":
                    switch (cell) {
                        case 'b': return Color.parseColor("#05070b");
                        case 'c': return Color.parseColor("#f8fafc");
                        case 'd': return Color.parseColor("#f1e4c9");
                        case 'e': return Color.parseColor("#111827");
                        default: return Color.TRANSPARENT;
                    }
                case "cat":
                default:
                    switch (cell) {
                        case 'a': return Color.parseColor("#c2410c");
                        case 'b': return Color.parseColor("#fb923c");
                        case 'c': return Color.parseColor("#f8fafc");
                        case 'd': return Color.parseColor("#1f2937");
                        case 'e': return Color.parseColor("#f9a8d4");
                        default: return Color.TRANSPARENT;
                    }
            }
        }

        private void drawSelectedCharacter(Canvas canvas, String[] grid, float px) {
            int cols = Math.max(1, getGridWidth(grid));
            int rows = Math.max(1, grid.length);
            float startX = (getWidth() - (cols * px)) / 2f;
            float startY = (getHeight() - (rows * px)) / 2f;

            for (int rowIndex = 0; rowIndex < grid.length; rowIndex++) {
                String row = grid[rowIndex];
                if (row == null) {
                    continue;
                }
                for (int colIndex = 0; colIndex < row.length(); colIndex++) {
                    char cell = row.charAt(colIndex);
                    if (cell == '.') {
                        continue;
                    }
                    int color = resolveColor(cell);
                    if (color == Color.TRANSPARENT) {
                        continue;
                    }
                    paint.setColor(color);
                    float x = startX + (colIndex * px);
                    float y = startY + (rowIndex * px);
                    canvas.drawRect(x, y, x + px, y + px, paint);
                }
            }
        }
    }
}
