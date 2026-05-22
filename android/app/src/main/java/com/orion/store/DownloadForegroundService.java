package com.orion.store;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

/**
 * Lightweight foreground service that lets Orion's downloads continue
 * running when the user backgrounds the app or locks the screen.
 *
 * The actual download work still happens inside AppTrackerPlugin's
 * ExecutorService — this service exists purely to keep the process
 * alive and visible so Android won't kill us mid-transfer.
 *
 * Lifecycle:
 *   - AppTrackerPlugin calls {@link #start(Context)} when it kicks off
 *     the first concurrent download.
 *   - {@link #updateProgress(Context, int, int)} refreshes the persistent
 *     notification so users see real-time progress on the lockscreen.
 *   - AppTrackerPlugin calls {@link #stop(Context)} once activeTasks is
 *     empty so the notification disappears and the service is released.
 */
public class DownloadForegroundService extends Service {

    public static final String CHANNEL_ID = "orion_downloads_foreground";
    public static final int NOTIFICATION_ID = 7301;

    public static final String ACTION_START = "com.orion.store.action.START_DOWNLOADS";
    public static final String ACTION_STOP = "com.orion.store.action.STOP_DOWNLOADS";
    public static final String ACTION_UPDATE = "com.orion.store.action.UPDATE_DOWNLOADS";

    public static final String EXTRA_ACTIVE_COUNT = "activeCount";
    public static final String EXTRA_AVERAGE_PROGRESS = "averageProgress";

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Service was restarted by the system after process death — bring
            // up a placeholder notification then immediately stop so we don't
            // leak. The plugin will re-attach and re-start us if needed.
            startForegroundCompat(buildNotification(0, 0));
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        int activeCount = intent.getIntExtra(EXTRA_ACTIVE_COUNT, 0);
        int avgProgress = intent.getIntExtra(EXTRA_AVERAGE_PROGRESS, 0);

        if (ACTION_STOP.equals(action) || activeCount <= 0) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundCompat(buildNotification(activeCount, avgProgress));
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Keep the service alive so downloads continue if the user swipes
        // the task away from recents.
        super.onTaskRemoved(rootIntent);
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(int activeCount, int avgProgress) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent contentPi = null;
        if (launch != null) {
            launch.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                piFlags |= PendingIntent.FLAG_IMMUTABLE;
            }
            contentPi = PendingIntent.getActivity(this, 0, launch, piFlags);
        }

        String title = activeCount > 1
                ? activeCount + " downloads in progress"
                : "Downloading…";
        String text = avgProgress > 0 && avgProgress < 100
                ? avgProgress + "% — Orion Store will keep going in the background"
                : "Orion Store will keep going in the background";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, Math.max(0, Math.min(100, avgProgress)), avgProgress <= 0);

        if (contentPi != null) {
            builder.setContentIntent(contentPi);
        }

        return builder.build();
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Background Downloads",
                NotificationManager.IMPORTANCE_LOW
        );
        ch.setDescription("Keeps Orion Store downloads running while the app is in the background.");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    public static void start(Context ctx, int activeCount, int avgProgress) {
        ensureChannel(ctx);
        Intent i = new Intent(ctx, DownloadForegroundService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_ACTIVE_COUNT, activeCount);
        i.putExtra(EXTRA_AVERAGE_PROGRESS, avgProgress);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception ignored) {
            // Some OEMs throw when the app is freshly backgrounded — the
            // download will still run on the executor, just without
            // foreground guarantees.
        }
    }

    public static void update(Context ctx, int activeCount, int avgProgress) {
        if (activeCount <= 0) {
            stop(ctx);
            return;
        }
        Intent i = new Intent(ctx, DownloadForegroundService.class);
        i.setAction(ACTION_UPDATE);
        i.putExtra(EXTRA_ACTIVE_COUNT, activeCount);
        i.putExtra(EXTRA_AVERAGE_PROGRESS, avgProgress);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception ignored) {}
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, DownloadForegroundService.class);
        i.setAction(ACTION_STOP);
        try {
            ctx.stopService(i);
        } catch (Exception ignored) {}
    }
}
