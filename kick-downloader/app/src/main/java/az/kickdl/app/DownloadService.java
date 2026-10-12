package az.kickdl.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;

import az.kickdl.app.data.Prefs;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.dl.DownloadTask;
import az.kickdl.app.ui.Fmt;

/** Foreground service that keeps downloads alive and shows live progress in the notification shade. */
public class DownloadService extends Service implements DownloadManager.Listener {
    public static final String CH_PROGRESS = "progress";
    public static final String CH_DONE = "done";
    private static final int NOTIF_ID = 42;
    private static final String ACTION_PAUSE_ALL = "az.kickdl.app.PAUSE_ALL";
    private static final String ACTION_RESUME_ALL = "az.kickdl.app.RESUME_ALL";

    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastUpdate;
    private boolean foreground;

    public static void createChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel p = new NotificationChannel(CH_PROGRESS, c.getString(R.string.ch_progress), NotificationManager.IMPORTANCE_LOW);
        p.setShowBadge(false);
        nm.createNotificationChannel(p);
        NotificationChannel d = new NotificationChannel(CH_DONE, c.getString(R.string.ch_done), NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(d);
    }

    public static void ensureRunning(Context c) {
        try {
            Intent i = new Intent(c, DownloadService.class);
            c.startForegroundService(i);
        } catch (Exception ignored) {
            // Background start restrictions: downloads keep running while the app is open.
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        DownloadManager.get().addListener(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIF_ID, n);
            foreground = true;
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        checkStop();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        DownloadManager.get().removeListener(this);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDownloadsChanged() {
        long now = System.currentTimeMillis();
        if (now - lastUpdate > 900) {
            lastUpdate = now;
            if (foreground) getSystemService(NotificationManager.class).notify(NOTIF_ID, build());
        }
        checkStop();
    }

    private void checkStop() {
        main.removeCallbacks(stopper);
        if (!DownloadManager.get().hasWork()) main.postDelayed(stopper, 1500);
    }

    private final Runnable stopper = new Runnable() {
        @Override public void run() {
            if (!DownloadManager.get().hasWork()) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
    };

    private Notification build() {
        List<DownloadTask> all = DownloadManager.get().all();
        int active = 0, merging = 0, live = 0;
        long done = 0, total = 0;
        double speed = 0;
        DownloadTask first = null;
        boolean indeterminate = false;
        for (DownloadTask t : all) {
            if (!t.isActive()) continue;
            active++;
            if (first == null) first = t;
            if (t.status == DownloadTask.MERGING) merging++;
            if (t.live) { live++; indeterminate = true; }
            speed += t.speed;
            if (t.totalBytes > 0) {
                done += t.bytesDone;
                total += t.totalBytes;
            } else {
                indeterminate = true;
            }
        }
        Notification.Builder b = new Notification.Builder(this, CH_PROGRESS)
                .setSmallIcon(R.drawable.ic_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setColor(0xFF53FC18)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(openApp(this));
        if (active == 0) {
            b.setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.notif_idle));
            return b.build();
        }
        String title = active == 1 && first != null ? first.title
                : getResources().getQuantityString(R.plurals.notif_active, active, active);
        StringBuilder text = new StringBuilder();
        if (merging == active) text.append(getString(R.string.status_merging));
        else {
            if (total > 0) text.append(Fmt.percent(done / (double) total)).append(" · ").append(Fmt.size(done)).append(" / ").append(Fmt.size(total));
            else text.append(Fmt.size(done));
            if (speed > 0) text.append(" · ").append(Fmt.speed(speed));
            if (live > 0) text.append(" · ").append(getString(R.string.recording));
        }
        b.setContentTitle(title).setContentText(text.toString());
        int pct = total > 0 ? (int) (done * 1000 / total) : 0;
        b.setProgress(1000, pct, indeterminate && total == 0 || merging == active);
        b.addAction(new Notification.Action.Builder(null, getString(R.string.pause_all),
                PendingIntent.getBroadcast(this, 1, new Intent(this, Actions.class).setAction(ACTION_PAUSE_ALL),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        return b.build();
    }

    static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_DOWNLOADS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 2, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void notifyFinished(Context c, DownloadTask t, boolean ok) {
        if (!Prefs.notifyDone()) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        Notification.Builder b = new Notification.Builder(c, CH_DONE)
                .setSmallIcon(ok ? R.drawable.ic_check : R.drawable.ic_error)
                .setColor(ok ? 0xFF53FC18 : 0xFFFF3B5C)
                .setAutoCancel(true)
                .setContentTitle(c.getString(ok ? R.string.notif_done : R.string.notif_failed))
                .setContentText(t.title);
        if (ok && t.outputUri != null) {
            Intent play = new Intent(c, PlayerActivity.class)
                    .setData(android.net.Uri.parse(t.outputUri))
                    .putExtra(PlayerActivity.EXTRA_TITLE, t.title)
                    .putExtra(PlayerActivity.EXTRA_TASK, t.id)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            b.setContentIntent(PendingIntent.getActivity(c, t.id.hashCode(), play,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            b.setSubText(Fmt.size(t.outputSize));
        } else {
            b.setContentIntent(openApp(c));
            if (t.error != null) b.setStyle(new Notification.BigTextStyle().bigText(t.title + "\n" + t.error));
        }
        try { nm.notify(t.id.hashCode(), b.build()); } catch (Exception ignored) {}
    }

    /** Notification action receiver. */
    public static class Actions extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (DownloadManager.get() == null) return;
            if (ACTION_PAUSE_ALL.equals(intent.getAction())) DownloadManager.get().pauseAll();
            else if (ACTION_RESUME_ALL.equals(intent.getAction())) DownloadManager.get().resumeAll();
        }
    }
}
