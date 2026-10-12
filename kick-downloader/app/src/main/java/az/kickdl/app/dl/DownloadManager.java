package az.kickdl.app.dl;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import az.kickdl.app.DownloadService;
import az.kickdl.app.data.Prefs;

/** Owns the download queue: scheduling, persistence, change notifications. */
public final class DownloadManager {

    public interface Listener { void onDownloadsChanged(); }

    private static DownloadManager instance;

    public static synchronized DownloadManager get() { return instance; }

    public static synchronized void init(Context c) {
        if (instance == null) instance = new DownloadManager(c.getApplicationContext());
    }

    private final Context app;
    private final List<DownloadTask> tasks = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final File store;
    private boolean notifyPosted, savePosted;
    private long lastSave;

    private DownloadManager(Context app) {
        this.app = app;
        store = new File(app.getFilesDir(), "downloads.json");
        load();
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { main.postDelayed(scheduleRunnable, 800); }

                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
                    main.postDelayed(scheduleRunnable, 800);
                }
            });
        } catch (Exception ignored) {}
        main.post(scheduleRunnable);
    }

    private final Runnable scheduleRunnable = new Runnable() {
        @Override public void run() { schedule(); }
    };

    // ------------------------------------------------------------------ queries

    public synchronized List<DownloadTask> all() { return new ArrayList<>(tasks); }

    public synchronized DownloadTask find(String id) {
        for (DownloadTask t : tasks) if (t.id.equals(id)) return t;
        return null;
    }

    /** Latest task for a media item (any state except canceled). */
    public synchronized DownloadTask forMedia(String mediaId) {
        if (mediaId == null) return null;
        for (DownloadTask t : tasks) if (mediaId.equals(t.mediaId) && t.status != DownloadTask.CANCELED) return t;
        return null;
    }

    public synchronized int activeCount() {
        int n = 0;
        for (DownloadTask t : tasks) if (t.isActive() || t.status == DownloadTask.PAUSED) n++;
        return n;
    }

    public synchronized int runningCount() {
        int n = 0;
        for (DownloadTask t : tasks) if (t.status == DownloadTask.RUNNING || t.status == DownloadTask.MERGING) n++;
        return n;
    }

    public synchronized boolean hasWork() {
        for (DownloadTask t : tasks) if (t.isActive()) return true;
        return false;
    }

    // ------------------------------------------------------------------ actions

    public void enqueue(DownloadTask t) {
        synchronized (this) {
            File tmpDir = Storage.tempDir(app);
            t.tempPath = new File(tmpDir, t.id + ".part").getAbsolutePath();
            t.status = DownloadTask.QUEUED;
            tasks.add(0, t);
        }
        changed(true);
        schedule();
    }

    public void pause(String id) {
        DownloadTask t = find(id);
        if (t == null) return;
        if (t.live) { stopRecording(id); return; }
        synchronized (this) {
            if (t.status == DownloadTask.RUNNING) t.pauseRequested = true;
            else if (t.status == DownloadTask.QUEUED || t.status == DownloadTask.WAITING_NET) t.status = DownloadTask.PAUSED;
        }
        changed(true);
    }

    public void resume(String id) {
        DownloadTask t = find(id);
        if (t == null) return;
        synchronized (this) {
            if (t.status == DownloadTask.PAUSED || t.status == DownloadTask.FAILED) {
                t.status = DownloadTask.QUEUED;
                t.error = null;
            }
        }
        changed(true);
        schedule();
    }

    public void retry(String id) { resume(id); }

    /** Stops a live recording and keeps what was captured. */
    public void stopRecording(String id) {
        DownloadTask t = find(id);
        if (t == null) return;
        t.stopRequested = true;
        changed(true);
    }

    public void cancel(String id) {
        DownloadTask t = find(id);
        if (t == null) return;
        boolean running;
        synchronized (this) {
            running = t.status == DownloadTask.RUNNING || t.status == DownloadTask.MERGING;
            t.cancelRequested = true;
            if (!running) {
                t.status = DownloadTask.CANCELED;
                tasks.remove(t);
            }
        }
        if (!running) deleteTemp(t);
        changed(true);
        schedule();
    }

    /** Removes from history; optionally deletes the saved file. */
    public void remove(String id, boolean deleteFile) {
        DownloadTask t = find(id);
        if (t == null) return;
        if (t.isActive()) { cancel(id); return; }
        synchronized (this) { tasks.remove(t); }
        deleteTemp(t);
        if (deleteFile && t.outputUri != null) Storage.delete(app, t.outputUri);
        changed(true);
    }

    public void clearFinished() {
        synchronized (this) {
            for (int i = tasks.size() - 1; i >= 0; i--) {
                int s = tasks.get(i).status;
                if (s == DownloadTask.DONE || s == DownloadTask.FAILED || s == DownloadTask.CANCELED) tasks.remove(i);
            }
        }
        changed(true);
    }

    public void pauseAll() {
        for (DownloadTask t : all()) if (t.isActive() && !t.live) pause(t.id);
    }

    public void resumeAll() {
        synchronized (this) {
            for (DownloadTask t : tasks) if (t.status == DownloadTask.PAUSED) t.status = DownloadTask.QUEUED;
        }
        changed(true);
        schedule();
    }

    // ------------------------------------------------------------------ scheduling

    public void schedule() {
        List<DownloadTask> toStart = new ArrayList<>();
        boolean unmetered = isUnmetered();
        synchronized (this) {
            int running = 0;
            for (DownloadTask t : tasks) if (t.status == DownloadTask.RUNNING || t.status == DownloadTask.MERGING) running++;
            int max = Math.max(1, Prefs.concurrent());
            for (int i = tasks.size() - 1; i >= 0; i--) { // oldest first
                DownloadTask t = tasks.get(i);
                if (t.status == DownloadTask.WAITING_NET && (unmetered || !Prefs.wifiOnly())) t.status = DownloadTask.QUEUED;
                if (t.status != DownloadTask.QUEUED) continue;
                if (Prefs.wifiOnly() && !unmetered) { t.status = DownloadTask.WAITING_NET; continue; }
                // Live recordings never wait in the queue: the broadcast does not wait either.
                if (running >= max && !t.live) continue;
                t.status = DownloadTask.RUNNING;
                t.pauseRequested = t.cancelRequested = t.stopRequested = false;
                running++;
                toStart.add(t);
            }
        }
        for (DownloadTask t : toStart) {
            Thread th = new Thread(new Downloader(app, this, t), "dl-" + t.id.substring(0, 6));
            th.setPriority(Thread.NORM_PRIORITY - 1);
            th.start();
        }
        if (hasWork()) DownloadService.ensureRunning(app);
        changed(!toStart.isEmpty());
    }

    void onWorkerFinished(DownloadTask t) {
        synchronized (this) {
            if (t.status == DownloadTask.CANCELED) tasks.remove(t);
        }
        if (t.status == DownloadTask.CANCELED) deleteTemp(t);
        if (t.status == DownloadTask.DONE) DownloadService.notifyFinished(app, t, true);
        else if (t.status == DownloadTask.FAILED) DownloadService.notifyFinished(app, t, false);
        changed(true);
        main.post(scheduleRunnable);
    }

    private boolean isUnmetered() {
        try {
            ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteTemp(DownloadTask t) {
        if (t.tempPath != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(t.tempPath).delete();
        }
    }

    // ------------------------------------------------------------------ change propagation

    /** Progress tick from a worker: coalesced UI refresh, throttled persistence. */
    void progress(DownloadTask t) { changed(false); }

    public void changed(boolean saveNow) {
        synchronized (this) {
            if (!notifyPosted) {
                notifyPosted = true;
                main.postDelayed(new Runnable() {
                    @Override public void run() {
                        synchronized (DownloadManager.this) { notifyPosted = false; }
                        for (Listener l : listeners) l.onDownloadsChanged();
                    }
                }, 200);
            }
        }
        if (saveNow || System.currentTimeMillis() - lastSave > 2500) save();
    }

    public void addListener(Listener l) { listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    // ------------------------------------------------------------------ persistence

    private void load() {
        try {
            if (!store.exists()) return;
            byte[] b = java.nio.file.Files.readAllBytes(store.toPath());
            JSONArray a = new JSONArray(new String(b, StandardCharsets.UTF_8));
            boolean auto = Prefs.autoResume();
            for (int i = 0; i < a.length(); i++) {
                DownloadTask t = DownloadTask.fromJson(a.getJSONObject(i));
                if (t.status == DownloadTask.RUNNING || t.status == DownloadTask.MERGING
                        || t.status == DownloadTask.QUEUED || t.status == DownloadTask.WAITING_NET) {
                    // Interrupted by process death. Live recordings cannot continue: finalize what we have.
                    if (t.live) t.stopRequested = true;
                    t.status = auto || t.live ? DownloadTask.QUEUED : DownloadTask.PAUSED;
                }
                if (t.status == DownloadTask.CANCELED) continue;
                tasks.add(t);
            }
        } catch (Exception ignored) {}
    }

    private void save() {
        synchronized (this) {
            lastSave = System.currentTimeMillis();
            if (savePosted) return;
            savePosted = true;
        }
        io.execute(new Runnable() {
            @Override public void run() {
                JSONArray a = new JSONArray();
                synchronized (DownloadManager.this) {
                    savePosted = false;
                    for (DownloadTask t : tasks) a.put(t.toJson());
                }
                File tmp = new File(store.getPath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(a.toString().getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                } catch (Exception e) {
                    return;
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.renameTo(store);
            }
        });
    }

    public Context context() { return app; }

    public static Intent shareIntent(DownloadTask t) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(t.mime != null ? t.mime : "video/*");
        i.putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(t.outputUri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(i, t.title);
    }
}
