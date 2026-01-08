package com.orion.store;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(
    name = "AppTracker",
    permissions = {
        @Permission(alias = "storage", strings = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}),
        @Permission(alias = "install", strings = {Manifest.permission.REQUEST_INSTALL_PACKAGES})
    }
)
public class AppTrackerPlugin extends Plugin {

    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private final ConcurrentHashMap<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();
    private PowerManager.WakeLock wakeLock;

    @PluginMethod
    public void getAppInfo(PluginCall call) {
        String pkg = call.getString("packageName");
        JSObject ret = new JSObject();
        try {
            PackageManager pm = getContext().getPackageManager();
            PackageInfo p = pm.getPackageInfo(pkg, 0);
            ret.put("installed", true);
            ret.put("version", p.versionName);
            call.resolve(ret);
        } catch (Exception e) {
            ret.put("installed", false);
            call.resolve(ret);
        }
    }

    @PluginMethod
    public void downloadFile(PluginCall call) {
        String url = call.getString("url");
        String fileName = call.getString("fileName");
        if (activeTasks.containsKey(fileName)) {
            JSObject r = new JSObject(); r.put("downloadId", fileName);
            call.resolve(r); return;
        }
        
        // 10. Background Power Lock
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Orion:DownloadLock");
        }
        if (!wakeLock.isHeld()) wakeLock.acquire(15 * 60 * 1000L); // 15m limit

        // 6. Media Scanner Exclusion (.nomedia) - Ensure file exists
        File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir != null && !dir.exists()) dir.mkdirs();
        File noMedia = new File(dir, ".nomedia");
        try { if (!noMedia.exists()) noMedia.createNewFile(); } catch(Exception e){}

        DownloadTask t = new DownloadTask(url, fileName);
        activeTasks.put(fileName, t);
        executorService.execute(t);
        JSObject r = new JSObject(); r.put("downloadId", fileName);
        call.resolve(r);
    }

    @PluginMethod
    public void getDownloadProgress(PluginCall call) {
        String id = call.getString("downloadId");
        DownloadTask t = activeTasks.get(id);
        JSObject r = new JSObject();
        if (t != null) {
            r.put("status", t.isCancelled ? "FAILED" : "RUNNING");
            r.put("progress", t.progress);
            call.resolve(r);
        } else {
            File f = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), id);
            if (f.exists() && f.length() > 0) {
                r.put("status", "SUCCESSFUL"); r.put("progress", 100);
            } else { r.put("status", "FAILED"); }
            call.resolve(r);
        }
    }

    @PluginMethod
    public void cancelDownload(PluginCall call) {
        String id = call.getString("downloadId");
        DownloadTask t = activeTasks.get(id);
        if (t != null) t.cancel();
        if (activeTasks.isEmpty() && wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        call.resolve();
    }

    @PluginMethod
    public void installPackage(PluginCall call) {
        String fileName = call.getString("fileName");
        try {
            File baseFile = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
            
            // 16. Scanner Wait / File Lock Check
            int checks = 0;
            while (checks < 5) {
                if (baseFile.exists() && baseFile.canRead()) {
                    // Try to rename to itself to check for write locks
                    if (baseFile.renameTo(baseFile)) break;
                }
                Thread.sleep(200);
                checks++;
            }

            if (!baseFile.exists()) { call.reject("FILE_MISSING"); return; }
            
            // 12. Path Canonicalization
            File f = baseFile.getCanonicalFile();
            f.setReadable(true, false);

            // 3. ZIP Signature Check
            if (!isValidApk(f)) { 
                f.delete(); 
                call.reject("CORRUPT_APK"); 
                return; 
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getContext().getPackageManager().canRequestPackageInstalls()) {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
                call.reject("INSTALL_PERMISSION_REQUIRED");
                return;
            }

            String mimeType = "application/vnd.android.package-archive";
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            // 9. Strict Intent URI Permission
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // 11. Explicit Package Permission Grant + 13. Sledgehammer Targeting
            List<ResolveInfo> resInfoList = getContext().getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            boolean targeted = false;
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                getContext().grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                if (!targeted && (packageName.contains("packageinstaller") || packageName.contains("google.android.packageinstaller"))) {
                    intent.setPackage(packageName);
                    targeted = true;
                }
            }

            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    private boolean isValidApk(File f) {
        if (f.length() < 100) return false;
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] h = new byte[4];
            if (fis.read(h) != 4) return false;
            return h[0] == 0x50 && h[1] == 0x4B && h[2] == 0x03 && h[3] == 0x04;
        } catch (Exception e) { return false; }
    }

    @PluginMethod
    public void deleteFile(PluginCall call) {
        String name = call.getString("fileName");
        File f = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
        if (f.exists()) f.delete();
        call.resolve();
    }

    private class DownloadTask implements Runnable {
        String urlStr, fileName;
        volatile int progress = 0;
        volatile boolean isCancelled = false;
        private HttpURLConnection conn;

        DownloadTask(String u, String f) { this.urlStr = u; this.fileName = f; }
        void cancel() { isCancelled = true; if (conn != null) conn.disconnect(); }

        @Override
        public void run() {
            // 14. Max Thread Priority
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
            
            // 8. Exponential Backoff Retry
            int retries = 0;
            boolean success = false;
            while (retries < 3 && !isCancelled && !success) {
                success = performDownload();
                if (!success && !isCancelled) {
                    retries++;
                    try { Thread.sleep(retries * 2000); } catch(Exception e){}
                }
            }
            activeTasks.remove(fileName);
            if (activeTasks.isEmpty() && wakeLock != null && wakeLock.isHeld()) {
                try { wakeLock.release(); } catch(Exception e){}
            }
        }

        private boolean performDownload() {
            File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File temp = new File(dir, fileName + ".tmp");
            File fin = new File(dir, fileName);
            try {
                long existingSize = temp.exists() ? temp.length() : 0;
                
                // 15. Recursive Redirect Follower
                String currentUrlStr = urlStr;
                int redirects = 0;
                conn = null;
                
                while (redirects < 10) {
                    URL url = new URL(currentUrlStr);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(false); // Manual handling
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    
                    // 1. Browser Spoofing
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/110.0.0.0");
                    
                    // 5. Range Support (Resuming)
                    if (existingSize > 0) conn.setRequestProperty("Range", "bytes=" + existingSize + "-");
                    
                    conn.connect();
                    int status = conn.getResponseCode();
                    
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        String newUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (newUrl == null) return false;
                        currentUrlStr = newUrl;
                        redirects++;
                        continue;
                    }
                    
                    if (status >= 400) return false;
                    
                    // 2. Header Validation (Content-Type Guard)
                    String contentType = conn.getContentType();
                    if (contentType != null && contentType.contains("text/html")) {
                        conn.disconnect();
                        return false;
                    }
                    
                    long total = conn.getContentLength();
                    boolean isResuming = false;

                    // SMART RESUME CHECK:
                    // If we asked for a Range (existingSize > 0) but server sent 200 OK, it refused to resume.
                    // We must truncate existing file and start fresh to avoid corruption.
                    if (status == 206) {
                        total += existingSize;
                        isResuming = true;
                    } else if (existingSize > 0) {
                        // Server ignored range, restart download
                        existingSize = 0;
                    }

                    InputStream in = conn.getInputStream();
                    RandomAccessFile out = new RandomAccessFile(temp, "rw");
                    
                    if (isResuming) {
                        out.seek(existingSize);
                    } else {
                        out.setLength(0); // Truncate if not resuming
                    }

                    byte[] buffer = new byte[16384];
                    int count; long dl = existingSize;
                    while ((count = in.read(buffer)) != -1) {
                        if (isCancelled) break;
                        out.write(buffer, 0, count);
                        dl += count;
                        if (total > 0) progress = (int) (dl * 100 / total);
                    }
                    
                    // 4. Atomic Buffer Flushing
                    out.getFD().sync();
                    out.close(); in.close();
                    
                    if (!isCancelled) {
                        // 7. Content-Length Integrity Check
                        if (total > 0 && temp.length() != total) return false;
                        
                        if (fin.exists()) fin.delete();
                        temp.renameTo(fin);
                        return true;
                    }
                    // Break out of redirect loop on success/cancel
                    return !isCancelled;
                }
            } catch (Exception e) { return false; }
            return false;
        }
    }
    
    @PluginMethod public void setHighRefreshRate(PluginCall call) { call.resolve(); }
    @PluginMethod public void shareApp(PluginCall call) { call.resolve(); }
    @PluginMethod public void launchApp(PluginCall call) { call.resolve(); }
    @PluginMethod public void uninstallApp(PluginCall call) { call.resolve(); }
    @PluginMethod public void requestPermissions(PluginCall call) { call.resolve(); }
}
