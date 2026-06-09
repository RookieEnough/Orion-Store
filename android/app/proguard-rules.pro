# =============================================================================
# Orion Store — ProGuard / R8 Keep Rules
# =============================================================================
# Capacitor uses reflection to invoke @PluginMethod-annotated methods on Plugin
# subclasses. Without these rules R8 renames/removes those methods, causing
# NullPointerException in CapacitorPlugins thread on startup.
# =============================================================================

# --- Capacitor Core -----------------------------------------------------------
# Keep all public Plugin subclasses and their annotated methods intact.
-keep public class com.getcapacitor.** { *; }
-keepclassmembers class com.getcapacitor.** { *; }
-dontwarn com.getcapacitor.**

# Keep the Bridge and its reflective plugin dispatch
-keep class com.getcapacitor.Bridge { *; }
-keep class com.getcapacitor.BridgeActivity { *; }
-keep class com.getcapacitor.Plugin { *; }
-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }
-keepclassmembers class * extends com.getcapacitor.Plugin {
    @com.getcapacitor.annotation.PluginMethod public *;
    # Legacy annotation (older Capacitor versions)
    @com.getcapacitor.PluginMethod public *;
}

# --- Capacitor Official Plugins -----------------------------------------------
-keep class com.capacitorjs.plugins.** { *; }
-keepclassmembers class com.capacitorjs.plugins.** { *; }
-dontwarn com.capacitorjs.plugins.**

# --- Local Notifications (crashed plugin) ------------------------------------
-keep class com.capacitorjs.plugins.localnotifications.** { *; }
-keepclassmembers class com.capacitorjs.plugins.localnotifications.** { *; }

# --- App / StatusBar / SplashScreen / Haptics --------------------------------
-keep class com.capacitorjs.plugins.app.** { *; }
-keep class com.capacitorjs.plugins.statusbar.** { *; }
-keep class com.capacitorjs.plugins.splashscreen.** { *; }
-keep class com.capacitorjs.plugins.haptics.** { *; }

# --- Custom AppTrackerPlugin --------------------------------------------------
-keep class com.orion.store.AppTrackerPlugin { *; }
-keepclassmembers class com.orion.store.AppTrackerPlugin { *; }
-keep class com.orion.store.** { *; }

# --- Shizuku ------------------------------------------------------------------
-keep class rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn dev.rikka.shizuku.**

# --- Unity Ads ----------------------------------------------------------------
-keep class com.unity3d.** { *; }
-dontwarn com.unity3d.**

# --- AndroidX / AppCompat -----------------------------------------------------
-keep class androidx.appcompat.** { *; }
-keep class androidx.core.** { *; }
-dontwarn androidx.**

# --- Coroutines / Kotlin stdlib -----------------------------------------------
-dontwarn kotlin.**
-dontwarn kotlinx.**

# --- WebView JS Bridge --------------------------------------------------------
# Prevent R8 from removing the @JavascriptInterface methods that Capacitor's
# WebView bridge calls from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- General safety -----------------------------------------------------------
# Keep all Parcelable implementations (used by Capacitor plugin results)
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep enums used in plugin APIs
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep R classes (needed by resource lookups in plugins)
-keepclassmembers class **.R$* {
    public static <fields>;
}
