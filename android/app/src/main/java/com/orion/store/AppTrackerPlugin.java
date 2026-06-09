package com.orion.store;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import androidx.activity.result.ActivityResult;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

@CapacitorPlugin(name = "AppTracker", permissions = {
        @Permission(alias = "storage", strings = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO"
        }),
        @Permission(alias = "install", strings = { Manifest.permission.REQUEST_INSTALL_PACKAGES }),
        @Permission(alias = "notify", strings = { Manifest.permission.POST_NOTIFICATIONS })
})
public class AppTrackerPlugin extends Plugin {

    // ── Optimized thread pool: 2 core threads with dynamic scaling ──
    // Using a cached pool with a core of 2 avoids the overhead of
    // maintaining 4 idle threads. Threads are created on-demand and
    // recycled after 60s of inactivity.
    private final ExecutorService executorService = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    private final ConcurrentHashMap<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();
    private PowerManager.WakeLock wakeLock;
    private static final String CHANNEL_ID = "orion_downloads";

    private static final class ApkInstallerEntry {
        final String packageName;
        final String label;
        final boolean isSystemInstaller;

        ApkInstallerEntry(String packageName, String label, boolean isSystemInstaller) {
            this.packageName = packageName;
            this.label = label;
            this.isSystemInstaller = isSystemInstaller;
        }
    }

    private PluginCall savedPermissionCall;
    private final Shizuku.OnRequestPermissionResultListener shizukuListener = new Shizuku.OnRequestPermissionResultListener() {
        @Override
        public void onRequestPermissionResult(int requestCode, int grantResult) {
            if (savedPermissionCall != null) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    savedPermissionCall.resolve();
                } else {
                    savedPermissionCall.reject("User denied Shizuku permission.");
                }
                savedPermissionCall = null;
            }
        }
    };

    @Override
    public void load() {
        createNotificationChannel();
        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener);
        } catch (Exception e) {
            // Shizuku might not be present, ignore
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Download Progress";
            String description = "Shows active download progress";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null)
            return "";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0)
            baos.write(buf, 0, n);
        return baos.toString("UTF-8");
    }

    private Process newShizukuProcess(String cmd) throws Exception {
        Method newProcessMethod = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class,
                String.class);
        newProcessMethod.setAccessible(true);
        return (Process) newProcessMethod.invoke(null, new String[] { "sh", "-c", cmd }, null, null);
    }

    // --- PERMISSION & STATUS METHODS ---

    @PluginMethod
    public void checkPermissionsStatus(PluginCall call) {
        JSObject ret = new JSObject();
        boolean storage = false;
        boolean media = false;
        boolean location = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storage = Environment.isExternalStorageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                media = getContext().checkSelfPermission(
                        Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
            } else {
                media = getContext().checkSelfPermission(
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            }
        } else {
            storage = getContext().checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            media = storage;
        }
        location = getContext()
                .checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        ret.put("storage", storage);
        ret.put("media", media);
        ret.put("location", location);
        ret.put("isLegacy", Build.VERSION.SDK_INT < Build.VERSION_CODES.R);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestUniversalStorage(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                call.resolve();
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                    call.resolve();
                } catch (Exception ex) {
                    call.reject("Could not open settings");
                }
            }
        } else {
            requestPermissionForAlias("storage", call, "storagePermissionCallback");
        }
    }

    // --- APP INFO METHODS ---

    @PluginMethod
    public void getInstalledPackages(PluginCall call) {
        executorService.execute(() -> {
            try {
                PackageManager pm = getContext().getPackageManager();
                // Use GET_META_DATA flag — faster than default (0) because it
                // skips permission resolution for each package.
                List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_META_DATA);
                JSArray apps = new JSArray();
                for (PackageInfo packageInfo : packages) {
                    JSObject app = new JSObject();
                    app.put("name", packageInfo.applicationInfo.loadLabel(pm).toString());
                    app.put("packageName", packageInfo.packageName);
                    apps.put(app);
                }
                JSObject ret = new JSObject();
                ret.put("apps", apps);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to get packages", e);
            }
        });
    }

    @PluginMethod
    public void getAppInfo(PluginCall call) {
        String pkg = call.getString("packageName");
        if (pkg == null) {
            call.reject("Package name required");
            return;
        }
        JSObject ret = new JSObject();
        try {
            PackageManager pm = getContext().getPackageManager();
            PackageInfo p = pm.getPackageInfo(pkg, 0);
            ret.put("installed", true);
            ret.put("version", p.versionName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ret.put("versionCode", p.getLongVersionCode());
            } else {
                ret.put("versionCode", p.versionCode);
            }
            call.resolve(ret);
        } catch (PackageManager.NameNotFoundException e) {
            ret.put("installed", false);
            ret.put("version", "");
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Error fetching info", e);
        }
    }

    @PluginMethod
    public void getMultipleAppInfo(PluginCall call) {
        JSArray packageNames;
        try {
            packageNames = call.getArray("packageNames");
        } catch (Exception e) {
            call.reject("packageNames array is required");
            return;
        }

        if (packageNames == null) {
            call.reject("packageNames array is required");
            return;
        }

        executorService.execute(() -> {
            PackageManager pm = getContext().getPackageManager();
            JSObject results = new JSObject();

            try {
                List<String> packages = packageNames.toList();
                for (String pkg : packages) {
                    JSObject info = new JSObject();
                    try {
                        PackageInfo p = pm.getPackageInfo(pkg, 0);
                        info.put("installed", true);
                        info.put("version", p.versionName);
                    } catch (PackageManager.NameNotFoundException e) {
                        info.put("installed", false);
                        info.put("version", "");
                    }
                    results.put(pkg, info);
                }
                call.resolve(results);
            } catch (Exception e) {
                call.reject("Failed to process package list", e);
            }
        });
    }

    // --- DOWNLOADER METHODS ---

    @PluginMethod
    public void checkActiveDownloads(PluginCall call) {
        JSObject ret = new JSObject();
        for (String key : activeTasks.keySet()) {
            ret.put(key, true);
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void downloadFile(PluginCall call) {
        String url = call.getString("url");
        String fileName = call.getString("fileName");
        if (activeTasks.containsKey(fileName)) {
            JSObject r = new JSObject();
            r.put("downloadId", fileName);
            call.resolve(r);
            return;
        }

        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Orion:DownloadLock");
        }
        if (!wakeLock.isHeld())
            wakeLock.acquire(30 * 60 * 1000L);

        File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir != null && !dir.exists())
            dir.mkdirs();
        File noMedia = new File(dir, ".nomedia");
        try {
            if (!noMedia.exists())
                noMedia.createNewFile();
        } catch (Exception e) {
        }

        DownloadTask t = new DownloadTask(url, fileName);
        activeTasks.put(fileName, t);
        executorService.execute(t);
        // Spin up the foreground service so this download survives backgrounding.
        try {
            DownloadForegroundService.start(getContext(), activeTasks.size(), computeAverageProgress());
        } catch (Exception ignored) {
        }
        JSObject r = new JSObject();
        r.put("downloadId", fileName);
        call.resolve(r);
    }

    private int computeAverageProgress() {
        if (activeTasks.isEmpty())
            return 0;
        int total = 0;
        int count = 0;
        for (DownloadTask t : activeTasks.values()) {
            total += Math.max(0, Math.min(100, t.progress));
            count++;
        }
        return count == 0 ? 0 : total / count;
    }

    private void refreshForegroundNotification() {
        try {
            int active = activeTasks.size();
            if (active <= 0) {
                DownloadForegroundService.stop(getContext());
            } else {
                DownloadForegroundService.update(getContext(), active, computeAverageProgress());
            }
        } catch (Exception ignored) {
        }
    }

    @PluginMethod
    public void getDownloadProgress(PluginCall call) {
        String id = call.getString("downloadId");
        DownloadTask t = activeTasks.get(id);
        JSObject r = new JSObject();
        if (t != null) {
            r.put("status", (t.isCancelled || t.hasFailed) ? "FAILED" : "RUNNING");
            r.put("progress", t.progress);
            call.resolve(r);
        } else {
            File f = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), id);
            if (f.exists() && f.length() > 0) {
                r.put("status", "SUCCESSFUL");
                r.put("progress", 100);
            } else {
                r.put("status", "FAILED");
            }
            call.resolve(r);
        }
    }

    @PluginMethod
    public void cancelDownload(PluginCall call) {
        String id = call.getString("downloadId");
        DownloadTask t = activeTasks.remove(id);
        if (t != null)
            t.cancel();
        refreshForegroundNotification();
        if (activeTasks.isEmpty() && wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
        call.resolve();
    }

    @PluginMethod
    public void resolveDownloadFile(PluginCall call) {
        String fileName = call.getString("fileName");
        if (fileName == null || fileName.isEmpty()) {
            call.reject("FileName required");
            return;
        }

        File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            call.reject("FILE_MISSING");
            return;
        }

        File f = new File(dir, fileName);
        if (!f.exists()) {
            call.reject("FILE_MISSING");
            return;
        }

        try {
            File canonical = f.getCanonicalFile();
            JSObject ret = new JSObject();
            ret.put("path", canonical.getAbsolutePath());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to resolve file", e);
        }
    }

    // --- INSTALLER METHODS ---

    @PluginMethod
    public void canRequestPackageInstalls(PluginCall call) {
        JSObject ret = new JSObject();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ret.put("value", getContext().getPackageManager().canRequestPackageInstalls());
        } else {
            ret.put("value", true);
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void openInstallPermissionSettings(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                call.resolve();
            } catch (Exception e) {
                call.reject("Could not open settings activity: " + e.getMessage());
            }
        } else {
            call.resolve();
        }
    }

    @PluginMethod
    public void installPackage(PluginCall call) {
        String fileName = call.getString("fileName");
        try {
            File baseFile = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);

            int checks = 0;
            while (checks < 5) {
                if (baseFile.exists() && baseFile.canRead()) {
                    if (baseFile.renameTo(baseFile))
                        break;
                }
                Thread.sleep(200);
                checks++;
            }

            if (!baseFile.exists()) {
                call.reject("FILE_MISSING");
                return;
            }

            File f = baseFile.getCanonicalFile();
            f.setReadable(true, false);

            if (!isValidApk(f)) {
                f.delete();
                call.reject("CORRUPT_APK");
                return;
            }

            String mimeType = "application/vnd.android.package-archive";
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            List<ResolveInfo> resInfoList = getContext().getPackageManager().queryIntentActivities(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                getContext().grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            if (resInfoList.isEmpty()) {
                call.reject("NO_INSTALLER");
                return;
            }

            String installerPreference = call.getString("installerPreference");
            String installerPackage = call.getString("installerPackage");

            boolean forceChooser = "chooser".equals(installerPreference);
            String targetPackage = null;

            if (!forceChooser && "package".equals(installerPreference) && installerPackage != null
                    && !installerPackage.isEmpty()) {
                boolean resolvable = false;
                for (ResolveInfo resolveInfo : resInfoList) {
                    if (installerPackage.equals(resolveInfo.activityInfo.packageName)) {
                        resolvable = true;
                        break;
                    }
                }
                if (resolvable) {
                    targetPackage = installerPackage;
                } else {
                    forceChooser = true;
                }
            }

            if (!forceChooser && targetPackage == null) {
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    boolean isSystem = (resolveInfo.activityInfo.applicationInfo.flags
                            & ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (isSystem && (packageName.contains("packageinstaller")
                            || packageName.contains("google.android.packageinstaller"))) {
                        targetPackage = packageName;
                        break;
                    }
                }
                if (targetPackage == null) {
                    for (ResolveInfo resolveInfo : resInfoList) {
                        String packageName = resolveInfo.activityInfo.packageName;
                        if (packageName.contains("packageinstaller")
                                || packageName.contains("google.android.packageinstaller")) {
                            targetPackage = packageName;
                            break;
                        }
                    }
                }
                if (targetPackage == null) {
                    for (ResolveInfo resolveInfo : resInfoList) {
                        boolean isSystem = (resolveInfo.activityInfo.applicationInfo.flags
                                & ApplicationInfo.FLAG_SYSTEM) != 0;
                        if (isSystem) {
                            targetPackage = resolveInfo.activityInfo.packageName;
                            break;
                        }
                    }
                }
                if (targetPackage == null) {
                    forceChooser = true;
                }
            }

            if (!forceChooser && targetPackage != null) {
                intent.setPackage(targetPackage);
                getContext().grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getContext().startActivity(intent);
            } else {
                Intent chooser = Intent.createChooser(intent, "Install App");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(chooser);
            }

            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void getApkInstallers(PluginCall call) {
        executorService.execute(() -> {
            try {
                PackageManager pm = getContext().getPackageManager();
                // Use a set to deduplicate by package name across multiple queries
                java.util.LinkedHashMap<String, ResolveInfo> foundMap = new java.util.LinkedHashMap<>();

                // Strategy 1: ACTION_VIEW with content:// URI + APK MIME type
                // This is how Orion actually sends the install intent (via FileProvider)
                Intent intent1 = new Intent(Intent.ACTION_VIEW);
                intent1.setDataAndType(Uri.parse("content://fake/file.apk"),
                        "application/vnd.android.package-archive");
                for (ResolveInfo ri : pm.queryIntentActivities(intent1, PackageManager.MATCH_ALL)) {
                    foundMap.putIfAbsent(ri.activityInfo.packageName, ri);
                }

                // Strategy 2: ACTION_VIEW with file:// URI + APK MIME type
                // Some installers only register for file:// scheme
                Intent intent2 = new Intent(Intent.ACTION_VIEW);
                intent2.setDataAndType(Uri.parse("file:///sdcard/fake.apk"),
                        "application/vnd.android.package-archive");
                for (ResolveInfo ri : pm.queryIntentActivities(intent2, PackageManager.MATCH_ALL)) {
                    foundMap.putIfAbsent(ri.activityInfo.packageName, ri);
                }

                // Strategy 3: ACTION_INSTALL_PACKAGE (deprecated but still used by many apps)
                Intent intent3 = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                intent3.setDataAndType(Uri.parse("content://fake/file.apk"),
                        "application/vnd.android.package-archive");
                for (ResolveInfo ri : pm.queryIntentActivities(intent3, PackageManager.MATCH_ALL)) {
                    foundMap.putIfAbsent(ri.activityInfo.packageName, ri);
                }

                // Strategy 4: Fallback — type-only query (original approach, catches edge cases)
                Intent intent4 = new Intent(Intent.ACTION_VIEW);
                intent4.setType("application/vnd.android.package-archive");
                for (ResolveInfo ri : pm.queryIntentActivities(intent4, PackageManager.MATCH_ALL)) {
                    foundMap.putIfAbsent(ri.activityInfo.packageName, ri);
                }

                // Build entries from deduplicated results, skip self
                String selfPackage = getContext().getPackageName();
                ArrayList<ApkInstallerEntry> entries = new ArrayList<>();
                for (java.util.Map.Entry<String, ResolveInfo> mapEntry : foundMap.entrySet()) {
                    String packageName = mapEntry.getKey();
                    if (packageName.equals(selfPackage)) continue;
                    ResolveInfo resolveInfo = mapEntry.getValue();
                    String label;
                    try {
                        CharSequence cs = resolveInfo.loadLabel(pm);
                        label = cs != null ? cs.toString() : packageName;
                    } catch (Exception e) {
                        label = packageName;
                    }
                    boolean isSystemInstaller = (resolveInfo.activityInfo.applicationInfo.flags
                            & ApplicationInfo.FLAG_SYSTEM) != 0;
                    entries.add(new ApkInstallerEntry(packageName, label, isSystemInstaller));
                }

                Collections.sort(entries, (a, b) -> {
                    if (a.isSystemInstaller != b.isSystemInstaller)
                        return a.isSystemInstaller ? -1 : 1;
                    int cmp = a.label.compareToIgnoreCase(b.label);
                    if (cmp != 0)
                        return cmp;
                    return a.packageName.compareToIgnoreCase(b.packageName);
                });

                JSArray installers = new JSArray();
                for (ApkInstallerEntry entry : entries) {
                    JSObject installer = new JSObject();
                    installer.put("packageName", entry.packageName);
                    installer.put("label", entry.label);
                    installer.put("isSystemInstaller", entry.isSystemInstaller);
                    installers.put(installer);
                }

                JSObject ret = new JSObject();
                ret.put("installers", installers);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to get APK installers", e);
            }
        });
    }

    @PluginMethod
    public void getAppIcon(PluginCall call) {
        String pkg = call.getString("packageName");
        if (pkg == null) {
            call.reject("Package name required");
            return;
        }
        executorService.execute(() -> {
            try {
                PackageManager pm = getContext().getPackageManager();
                Drawable d = pm.getApplicationIcon(pkg);
                Bitmap bmp;
                if (d instanceof BitmapDrawable) {
                    bmp = ((BitmapDrawable) d).getBitmap();
                } else {
                    int w = Math.max(1, d.getIntrinsicWidth());
                    int h = Math.max(1, d.getIntrinsicHeight());
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bmp);
                    d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    d.draw(canvas);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                JSObject ret = new JSObject();
                ret.put("icon", "data:image/png;base64," + b64);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to get icon", e);
            }
        });
    }

    // --- ORION GUARDIAN & SHIZUKU METHODS ---

    @PluginMethod
    public void requestShizukuPermission(PluginCall call) {
        try {
            if (!Shizuku.pingBinder()) {
                call.reject("Shizuku is NOT running. Start Shizuku app first.");
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                call.resolve();
            } else {
                this.savedPermissionCall = call;
                Shizuku.requestPermission(0);
            }
        } catch (Exception e) {
            call.reject("Shizuku Error (Check Manifest/Provider): " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void installPackageShizuku(PluginCall call) {
        String fileName = call.getString("fileName");
        try {
            if (!Shizuku.pingBinder()) {
                call.reject("Shizuku is NOT running.");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                call.reject("PERMISSION_DENIED");
                return;
            }

            File baseFile = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
            if (!baseFile.exists()) {
                call.reject("FILE_MISSING");
                return;
            }

            long fileSize = baseFile.length();
            String cmd = "pm install -r --restrict-permissions -S " + fileSize;

            Process process = newShizukuProcess(cmd);
            OutputStream os = process.getOutputStream();
            FileInputStream fis = new FileInputStream(baseFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0)
                os.write(buf, 0, len);
            os.flush();
            os.close();
            fis.close();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                call.resolve();
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null)
                    sb.append(line).append("\n");
                call.reject("Install failed (Code " + exitCode + "): " + sb.toString());
            }
        } catch (Exception e) {
            call.reject("Shizuku Error: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getDangerousApps(PluginCall call) {
        executorService.execute(() -> {
            try {
                PackageManager pm = getContext().getPackageManager();
                List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
                JSArray dangerousApps = new JSArray();
                List<String> dangerousPerms = Arrays.asList(
                        "android.permission.CAMERA", "android.permission.RECORD_AUDIO",
                        "android.permission.ACCESS_FINE_LOCATION",
                        "android.permission.ACCESS_COARSE_LOCATION", "android.permission.READ_CONTACTS",
                        "android.permission.WRITE_CONTACTS",
                        "android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS",
                        "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG",
                        "android.permission.READ_EXTERNAL_STORAGE",
                        "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.MANAGE_EXTERNAL_STORAGE");

                for (PackageInfo pi : packages) {
                    if (pi.requestedPermissions != null && pi.requestedPermissionsFlags != null) {
                        JSArray perms = new JSArray();
                        for (int i = 0; i < pi.requestedPermissions.length; i++) {
                            if ((pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                                if (dangerousPerms.contains(pi.requestedPermissions[i])) {
                                    perms.put(pi.requestedPermissions[i]);
                                }
                            }
                        }
                        if (perms.length() > 0) {
                            JSObject app = new JSObject();
                            app.put("name", pi.applicationInfo.loadLabel(pm).toString());
                            app.put("packageName", pi.packageName);
                            app.put("isSystem", (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                            app.put("permissions", perms);
                            dangerousApps.put(app);
                        }
                    }
                }
                JSObject ret = new JSObject();
                ret.put("apps", dangerousApps);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to get dangerous apps: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void getSystemApps(PluginCall call) {
        executorService.execute(() -> {
            try {
                PackageManager pm = getContext().getPackageManager();
                // GET_META_DATA is faster than MATCH_UNINSTALLED_PACKAGES
                // because it skips permission resolution.
                List<PackageInfo> packages = pm.getInstalledPackages(
                    PackageManager.GET_META_DATA | PackageManager.MATCH_UNINSTALLED_PACKAGES);
                JSArray allApps = new JSArray();
                String selfPkg = getContext().getPackageName();
                for (PackageInfo p : packages) {
                    // Don't show the store itself in the list
                    if (p.packageName.equals(selfPkg))
                        continue;

                    JSObject appObj = new JSObject();
                    appObj.put("packageName", p.packageName);
                    try {
                        appObj.put("name", pm.getApplicationLabel(p.applicationInfo).toString());
                    } catch (Exception e) {
                        appObj.put("name", p.packageName); // Fallback to package name
                    }
                    // Check if the app is currently installed for the user
                    boolean isInstalled = (p.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
                    appObj.put("isInstalled", isInstalled);
                    // Add isSystem flag
                    boolean isSystem = (p.applicationInfo.flags
                            & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                    appObj.put("isSystem", isSystem);
                    allApps.put(appObj);
                }
                JSObject ret = new JSObject();
                ret.put("apps", allApps);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Failed to fetch system apps: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void toggleSystemApp(PluginCall call) {
        String pkg = call.getString("packageName");
        boolean enable = call.getBoolean("enable", true);
        try {
            if (!Shizuku.pingBinder()) {
                call.reject("Shizuku not running");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                call.reject("Shizuku permission missing");
                return;
            }
            String cmd = enable ? "cmd package install-existing " + pkg : "pm uninstall -k --user 0 " + pkg;
            Process p = newShizukuProcess(cmd);
            int exit = p.waitFor();
            if (exit == 0) {
                call.resolve();
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null)
                    sb.append(line).append("\n");
                call.reject("Operation failed (Code " + exit + "): " + sb.toString());
            }
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void revokePermission(PluginCall call) {
        String pkg = call.getString("packageName");
        String perm = call.getString("permission");
        try {
            if (!Shizuku.pingBinder()) {
                call.reject("Shizuku not running");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                call.reject("Shizuku permission missing");
                return;
            }
            String cmd = "pm revoke " + pkg + " " + perm;
            Process p = newShizukuProcess(cmd);
            int exit = p.waitFor();
            if (exit == 0)
                call.resolve();
            else
                call.reject("Revoke failed. App might crash or restart.");
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void extractApk(PluginCall call) {
        String pkg = call.getString("packageName");
        executorService.execute(() -> {
            try {
                PackageManager pm = getContext().getPackageManager();
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                String sourceDir = pi.applicationInfo.sourceDir;
                String label = pm.getApplicationLabel(pi.applicationInfo).toString().replaceAll("[^a-zA-Z0-9]", "_");
                String destName = label + "_" + pi.versionName + ".apk";
                File destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File destFile = new File(destDir, destName);
                boolean shizukuSuccess = false;
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    try {
                        String cmd = "cp \"" + sourceDir + "\" \"" + destFile.getAbsolutePath() + "\"";
                        Process p = newShizukuProcess(cmd);
                        if (p.waitFor() == 0)
                            shizukuSuccess = true;
                    } catch (Exception e) {
                    }
                }
                if (!shizukuSuccess) {
                    try (FileInputStream in = new FileInputStream(new File(sourceDir));
                            FileOutputStream out = new FileOutputStream(destFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0)
                            out.write(buf, 0, len);
                    }
                }
                JSObject ret = new JSObject();
                ret.put("path", destFile.getAbsolutePath());
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Extraction failed: " + e.getMessage());
            }
        });
    }

    // --- ORION SENTINEL METHODS ---

    private String calculateFileHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file);
                    FileChannel channel = fis.getChannel()) {
                ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    digest.update(buffer);
                    buffer.clear();
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes)
                hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @PluginMethod
    public void calculateHash(PluginCall call) {
        String path = call.getString("filePath");
        if (path == null) {
            call.reject("Path required");
            return;
        }

        executorService.execute(() -> {
            File file = new File(path);
            if (!file.exists()) {
                call.reject("File not found");
                return;
            }
            String hash = calculateFileHash(file);
            if (hash != null) {
                JSObject ret = new JSObject();
                ret.put("hash", hash);
                call.resolve(ret);
            } else {
                call.reject("Hash failed");
            }
        });
    }

    @PluginMethod
    public void uploadVirusTotalFile(PluginCall call) {
        String filePath = call.getString("filePath");
        String apiKey = call.getString("apiKey");
        if (filePath == null || filePath.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            call.reject("Missing args");
            return;
        }

        executorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                File f = new File(filePath).getCanonicalFile();
                if (!f.exists() || !f.canRead()) {
                    call.reject("FILE_MISSING");
                    return;
                }

                String boundary = "----orionvt" + System.currentTimeMillis();
                URL url = new URL("https://www.virustotal.com/api/v3/files");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-apikey", apiKey);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("Accept", "application/json");

                try (OutputStream out = new BufferedOutputStream(conn.getOutputStream());
                        FileInputStream fis = new FileInputStream(f)) {
                    String header = "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; filename=\"" + f.getName() + "\"\r\n" +
                            "Content-Type: application/vnd.android.package-archive\r\n\r\n";
                    out.write(header.getBytes("UTF-8"));

                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = fis.read(buf)) > 0)
                        out.write(buf, 0, n);

                    out.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
                    out.flush();
                }

                int status = conn.getResponseCode();
                InputStream bodyStream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String body = readAll(bodyStream);

                JSObject ret = new JSObject();
                ret.put("status", status);
                ret.put("body", body);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject(e.getMessage());
            } finally {
                if (conn != null)
                    conn.disconnect();
            }
        });
    }

    @PluginMethod
    public void scanDirectory(PluginCall call) {
        executorService.execute(() -> {
            File root = Environment.getExternalStorageDirectory();
            List<JSObject> batch = new ArrayList<>();
            walk(root, batch, 0);

            if (!batch.isEmpty()) {
                sendScanBatch(batch);
            }
            notifyListeners("scanComplete", new JSObject());
        });
        call.resolve();
    }

    private void walk(File root, List<JSObject> batch, int depth) {
        if (depth > 15)
            return;
        File[] files = root.listFiles();
        if (files == null)
            return;

        for (File f : files) {
            if (f.isDirectory()) {
                String name = f.getName();
                if (name.startsWith(".") || name.equals("Android") || name.equals("data"))
                    continue;
                walk(f, batch, depth + 1);
            } else if (f.isFile() && f.length() > 1000) {
                String hash = calculateFileHash(f);
                if (hash != null) {
                    JSObject fileData = new JSObject();
                    fileData.put("path", f.getAbsolutePath());
                    fileData.put("hash", hash);
                    batch.add(fileData);
                    if (batch.size() >= 50) {
                        sendScanBatch(new ArrayList<>(batch));
                        batch.clear();
                    }
                }
            }
        }
    }

    private void sendScanBatch(List<JSObject> files) {
        JSObject data = new JSObject();
        JSArray filesArray = new JSArray();
        for (JSObject file : files) {
            filesArray.put(file);
        }
        data.put("files", filesArray);
        notifyListeners("scanResultBatch", data);
    }

    private String getWifiEncryptionType(WifiManager wifiManager, WifiInfo wifiInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                switch (wifiInfo.getCurrentSecurityType()) {
                    case WifiInfo.SECURITY_TYPE_SAE:
                        return "WPA3";
                    case WifiInfo.SECURITY_TYPE_OWE:
                        return "OWE";
                    case WifiInfo.SECURITY_TYPE_EAP:
                    case WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE:
                        return "WPA2-EAP";
                    case WifiInfo.SECURITY_TYPE_PSK:
                        return "WPA2";
                    case WifiInfo.SECURITY_TYPE_WEP:
                        return "WEP";
                    case WifiInfo.SECURITY_TYPE_OPEN:
                        return "OPEN";
                    default:
                        return "UNKNOWN";
                }
            } catch (Exception e) {
                return "UNKNOWN";
            }
        } else {
            // Legacy method - requires location permission
            try {
                List<ScanResult> scanResults = wifiManager.getScanResults();
                for (ScanResult result : scanResults) {
                    if (result.BSSID.equals(wifiInfo.getBSSID())) {
                        String capabilities = result.capabilities;
                        if (capabilities.contains("WPA3"))
                            return "WPA3";
                        if (capabilities.contains("WPA2"))
                            return "WPA2";
                        if (capabilities.contains("WPA"))
                            return "WPA";
                        if (capabilities.contains("WEP"))
                            return "WEP";
                        return "OPEN";
                    }
                }
            } catch (Exception e) {
                return "UNKNOWN";
            }
        }
        return "UNKNOWN";
    }

    @PluginMethod
    public void checkNetworkSecurity(PluginCall call) {
        JSObject ret = new JSObject();
        Context context = getContext();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        // --- Standard Checks ---
        boolean adb = Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        boolean adbWifi = Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", 0) == 1;
        ret.put("adbEnabled", adb);
        ret.put("adbWifiEnabled", adbWifi);

        String proxyHost = System.getProperty("http.proxyHost");
        String proxyPort = System.getProperty("http.proxyPort");
        ret.put("hasProxy", proxyHost != null && !proxyHost.isEmpty() && proxyPort != null && !proxyPort.isEmpty());

        // --- WiFi Audit (Requires Network & WiFi State) ---
        Network activeNetwork = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activeNetwork = cm.getActiveNetwork();
        }

        if (activeNetwork != null) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
            LinkProperties linkProps = cm.getLinkProperties(activeNetwork);

            if (caps != null) {
                ret.put("isVpnActive", caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
                ret.put("isCaptivePortal", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL));

                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    WifiInfo wifiInfo = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps.getTransportInfo() instanceof WifiInfo) {
                        wifiInfo = (WifiInfo) caps.getTransportInfo();
                    } else if (wifiManager != null) {
                        wifiInfo = wifiManager.getConnectionInfo();
                    }

                    if (wifiInfo != null) {
                        ret.put("isHiddenSsid", wifiInfo.getHiddenSSID());
                        ret.put("encryptionType", getWifiEncryptionType(wifiManager, wifiInfo));
                    }
                }
            }

            if (linkProps != null) {
                List<InetAddress> dnsServers = linkProps.getDnsServers();
                JSArray dnsArray = new JSArray();
                for (InetAddress dns : dnsServers) {
                    dnsArray.put(dns.getHostAddress());
                }
                ret.put("dnsServers", dnsArray);
            }
        } else {
            // Defaults for no active network
            ret.put("isVpnActive", false);
            ret.put("isCaptivePortal", false);
            ret.put("isHiddenSsid", false);
            ret.put("encryptionType", "UNKNOWN");
            ret.put("dnsServers", new JSArray());
        }

        call.resolve(ret);
    }

    // --- UTILITY METHODS ---

    private boolean isValidApk(File f) {
        if (f.length() < 100)
            return false;
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] h = new byte[4];
            if (fis.read(h) != 4)
                return false;
            boolean zipHeader = h[0] == 0x50 && h[1] == 0x4B && h[2] == 0x03 && h[3] == 0x04;
            if (!zipHeader)
                return false;
            try {
                PackageManager pm = getContext().getPackageManager();
                PackageInfo pi = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0);
                return pi != null && pi.packageName != null && !pi.packageName.isEmpty();
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @PluginMethod
    public void deleteFile(PluginCall call) {
        String name = call.getString("fileName");
        if (name == null) {
            call.reject("FileName required");
            return;
        }
        call.resolve(); // Optimistic
        executorService.execute(() -> {
            File f = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
            if (!f.exists())
                return;
            f.delete();
        });
    }

    @PluginMethod
    public void exportFile(PluginCall call) {
        String fileName = call.getString("fileName");
        if (fileName == null) {
            call.reject("FileName required");
            return;
        }
        try {
            File privateDir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File sourceFile = new File(privateDir, fileName);
            if (!sourceFile.exists()) {
                call.reject("File not found");
                return;
            }
            File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!publicDir.exists())
                publicDir.mkdirs();
            File destFile = new File(publicDir, fileName);
            int i = 1;
            while (destFile.exists()) {
                String name = fileName.substring(0, fileName.lastIndexOf("."));
                String ext = fileName.substring(fileName.lastIndexOf("."));
                destFile = new File(publicDir, name + "_" + i + ext);
                i++;
            }
            try (InputStream in = new FileInputStream(sourceFile);
                    OutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0)
                    out.write(buffer, 0, length);
            }
            if (destFile.exists() && destFile.length() == sourceFile.length()) {
                sourceFile.delete();
                JSObject ret = new JSObject();
                ret.put("path", destFile.getAbsolutePath());
                call.resolve(ret);
            } else {
                call.reject("Verification failed");
            }
        } catch (Exception e) {
            call.reject("Export failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void saveFile(PluginCall call) {
        String content = call.getString("content");
        String fileName = call.getString("fileName");
        if (content == null || fileName == null) {
            call.reject("Missing args");
            return;
        }
        saveCall(call);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(call, intent, "saveFileResult");
    }

    @ActivityCallback
    private void saveFileResult(PluginCall call, ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            String content = call.getString("content");
            try {
                OutputStream os = getContext().getContentResolver().openOutputStream(uri);
                if (os != null) {
                    os.write(content.getBytes());
                    os.close();
                    call.resolve();
                } else {
                    call.reject("Stream error");
                }
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        } else {
            call.reject("Cancelled");
        }
    }

    @PluginMethod
    public void setHighRefreshRate(PluginCall call) {
        Activity activity = getActivity();
        boolean enable = call.getBoolean("enable", false);

        if (activity == null) {
            call.resolve();
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                Window window = activity.getWindow();
                WindowManager.LayoutParams params = window.getAttributes();

                if (enable) {
                    float preferredRefreshRate = 0f;
                    Display display = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            ? activity.getDisplay()
                            : activity.getWindowManager().getDefaultDisplay();

                    if (display == null) {
                        display = activity.getWindowManager().getDefaultDisplay();
                    }

                    if (display != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            for (Display.Mode mode : display.getSupportedModes()) {
                                preferredRefreshRate = Math.max(preferredRefreshRate, mode.getRefreshRate());
                            }
                        }

                        if (preferredRefreshRate <= 0f) {
                            preferredRefreshRate = display.getRefreshRate();
                        }
                    }

                    if (preferredRefreshRate > 0f) {
                        params.preferredRefreshRate = preferredRefreshRate;
                    }
                } else {
                    params.preferredRefreshRate = 0f;
                }

                window.setAttributes(params);
                call.resolve();
            } catch (Exception e) {
                call.reject("Failed to update refresh rate: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void shareApp(PluginCall call) {
        try {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, call.getString("text", "") + " " + call.getString("url", ""));
            sendIntent.setType("text/plain");
            Intent shareIntent = Intent.createChooser(sendIntent, call.getString("title", "Share"));
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(shareIntent);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void launchApp(PluginCall call) {
        try {
            Intent launchIntent = getContext().getPackageManager()
                    .getLaunchIntentForPackage(call.getString("packageName"));
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(launchIntent);
                call.resolve();
            } else {
                call.reject("App not found");
            }
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void uninstallApp(PluginCall call) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + call.getString("packageName")));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        call.resolve();
    }

    // --- DOWNLOADER TASK ---
    private class DownloadTask implements Runnable {
        String urlStr, fileName;
        volatile int progress = 0;
        volatile boolean isCancelled = false;
        volatile boolean hasFailed = false;
        private HttpURLConnection conn;
        private final int notificationId;
        private final NotificationManager notificationManager;
        private final NotificationCompat.Builder notificationBuilder;

        DownloadTask(String u, String f) {
            this.urlStr = u;
            this.fileName = f;
            this.notificationId = fileName.hashCode();
            this.notificationManager = (NotificationManager) getContext()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            this.notificationBuilder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Downloading " + fileName)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setProgress(100, 0, true);
        }

        void updateNotification(int progress, boolean indeterminate) {
            notificationBuilder.setProgress(100, progress, indeterminate);
            notificationBuilder.setContentText(progress + "%");
            notificationManager.notify(notificationId, notificationBuilder.build());
        }

        void clearNotification() {
            notificationManager.cancel(notificationId);
        }

        void cancel() {
            isCancelled = true;
            clearNotification();
            if (conn != null)
                conn.disconnect();
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
            updateNotification(0, true);
            int retries = 0;
            boolean success = false;
            while (retries < 3 && !isCancelled && !success && !hasFailed) {
                success = performDownload();
                if (!success && !isCancelled && !hasFailed) {
                    retries++;
                    try {
                        Thread.sleep(retries * 2000);
                    } catch (Exception e) {
                    }
                }
            }
            activeTasks.remove(fileName);
            clearNotification();
            // Update / dismiss the persistent foreground notification now that
            // this task is gone from the active set.
            refreshForegroundNotification();
            if (activeTasks.isEmpty() && wakeLock != null && wakeLock.isHeld())
                try {
                    wakeLock.release();
                } catch (Exception e) {
                }
        }

        private boolean performDownload() {
            File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File temp = new File(dir, fileName + ".tmp");
            File fin = new File(dir, fileName);
            InputStream in = null;
            RandomAccessFile out = null;
            try {
                long existingSize = temp.exists() ? temp.length() : 0;
                String currentUrlStr = urlStr;
                int redirects = 0;
                conn = null;
                while (redirects < 10) {
                    URL url = new URL(currentUrlStr);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/110.0.0.0");
                    conn.setRequestProperty("Accept-Encoding", "identity");
                    if (existingSize > 0)
                        conn.setRequestProperty("Range", "bytes=" + existingSize + "-");
                    conn.connect();
                    int status = conn.getResponseCode();
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        String newUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (newUrl == null) {
                            hasFailed = true;
                            return false;
                        }
                        URL base = new URL(currentUrlStr);
                        URL next = new URL(base, newUrl);
                        currentUrlStr = next.toString();
                        redirects++;
                        continue;
                    }
                    if (status >= 400) {
                        conn.disconnect();
                        hasFailed = true;
                        return false;
                    }
                    String contentType = conn.getContentType();
                    if (contentType != null && contentType.contains("text/html")) {
                        conn.disconnect();
                        hasFailed = true;
                        return false;
                    }
                    long total = conn.getContentLength();
                    boolean isResuming = false;
                    if (status == 206) {
                        total += existingSize;
                        isResuming = true;
                    } else if (existingSize > 0) {
                        existingSize = 0;
                    }
                    in = conn.getInputStream();
                    out = new RandomAccessFile(temp, "rw");
                    if (isResuming)
                        out.seek(existingSize);
                    else
                        out.setLength(0);
                    byte[] buffer = new byte[16384];
                    int count;
                    long dl = existingSize;
                    long lastUpdate = 0;
                    while ((count = in.read(buffer)) != -1) {
                        if (isCancelled)
                            break;
                        out.write(buffer, 0, count);
                        dl += count;
                        if (total > 0)
                            progress = (int) (dl * 100 / total);
                        else
                            progress = (int) (dl % 100);
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 500) {
                            updateNotification(progress, total <= 0);
                            // Refresh aggregate background notification once per ~500ms cadence
                            refreshForegroundNotification();
                            lastUpdate = now;
                        }
                    }
                    out.getFD().sync();
                    try {
                        out.close();
                        out = null;
                    } catch (Exception e) {
                    }
                    try {
                        in.close();
                        in = null;
                    } catch (Exception e) {
                    }
                    if (!isCancelled) {
                        if (total > 0 && temp.length() < total) {
                            conn.disconnect();
                            return false;
                        }
                        if (fin.exists())
                            fin.delete();
                        temp.renameTo(fin);
                        conn.disconnect();
                        return true;
                    } else {
                        conn.disconnect();
                        return false;
                    }
                }
            } catch (Exception e) {
                return false;
            } finally {
                try {
                    if (out != null)
                        out.close();
                } catch (Exception e) {
                }
                try {
                    if (in != null)
                        in.close();
                } catch (Exception e) {
                }
                if (conn != null)
                    conn.disconnect();
                if (isCancelled)
                    try {
                        if (temp != null && temp.exists())
                            temp.delete();
                    } catch (Exception e) {
                    }
            }
            return false;
        }
    }
}
