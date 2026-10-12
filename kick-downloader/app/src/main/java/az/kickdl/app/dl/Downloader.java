package az.kickdl.app.dl;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import az.kickdl.app.R;
import az.kickdl.app.data.Prefs;
import az.kickdl.app.hls.Hls;
import az.kickdl.app.net.Http;

/** Worker for a single task: fetches segments (in parallel), writes them in order, then finalizes. */
final class Downloader implements Runnable {

    private static class Stop extends Exception {}

    private final Context ctx;
    private final DownloadManager mgr;
    private final DownloadTask t;
    private final Map<String, byte[]> keys = new HashMap<>();
    private long speedMark;
    private long speedBytes;

    Downloader(Context ctx, DownloadManager mgr, DownloadTask t) {
        this.ctx = ctx;
        this.mgr = mgr;
        this.t = t;
    }

    @Override public void run() {
        PowerManager.WakeLock wl = null;
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kickdl:" + t.id);
            wl.acquire(12 * 60 * 60 * 1000L);
        } catch (Exception ignored) {}
        try {
            t.error = null;
            boolean needFetch = !(t.segTotal > 0 && t.segDone >= t.segTotal && !t.live && new File(t.tempPath).exists());
            if (needFetch) {
                if (t.direct) runDirect();
                else if (t.live) runLive();
                else runHls();
            }
            check(false);
            t.status = DownloadTask.MERGING;
            t.speed = 0;
            mgr.changed(true);
            Finisher.finish(ctx, t);
            t.status = DownloadTask.DONE;
            t.finishedAt = System.currentTimeMillis();
        } catch (Stop s) {
            if (t.cancelRequested) t.status = DownloadTask.CANCELED;
            else t.status = DownloadTask.PAUSED;
            t.speed = 0;
        } catch (Throwable e) {
            if (t.cancelRequested) {
                t.status = DownloadTask.CANCELED;
            } else if (t.pauseRequested) {
                t.status = DownloadTask.PAUSED;
            } else {
                t.status = DownloadTask.FAILED;
                t.error = describe(e);
            }
            t.speed = 0;
        } finally {
            if (wl != null && wl.isHeld()) wl.release();
            mgr.onWorkerFinished(t);
        }
    }

    private String describe(Throwable e) {
        if (e instanceof Http.HttpException) {
            int code = ((Http.HttpException) e).code;
            if (code == 403) return ctx.getString(R.string.err_forbidden);
            if (code == 404 || code == 410) return ctx.getString(R.string.err_gone);
            return e.getMessage();
        }
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.ConnectException) return ctx.getString(R.string.err_network);
        if (e instanceof IOException && e.getMessage() != null && e.getMessage().contains("ENOSPC"))
            return ctx.getString(R.string.err_space);
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    private void check(boolean allowStopFlag) throws Stop {
        if (t.cancelRequested || t.pauseRequested) throw new Stop();
    }

    // ------------------------------------------------------------------ HLS VOD / clip

    private Hls.MediaPlaylist loadMediaPlaylist() throws IOException {
        String text = Http.text(t.url);
        String base = t.url;
        if (Hls.isMaster(text)) {
            List<Hls.Variant> vs = Hls.parseMaster(text, t.url);
            Hls.Variant pick = vs.get(0);
            for (Hls.Variant v : vs) if (v.label().equals(t.quality)) { pick = v; break; }
            if (t.audioOnly) {
                // Audio is identical across renditions: take the smallest one (or a dedicated audio rendition).
                pick = vs.get(vs.size() - 1);
                for (Hls.Variant v : vs) if (v.label().toLowerCase(Locale.US).contains("audio")) { pick = v; break; }
            }
            base = pick.url;
            text = Http.text(base);
            if (t.quality == null || t.quality.isEmpty()) t.quality = pick.label();
        }
        return Hls.parseMedia(text, base);
    }

    private void runHls() throws Exception {
        Hls.MediaPlaylist pl = loadMediaPlaylist();
        List<Hls.Segment> segs = new ArrayList<>();
        double from = t.trimStart > 0 ? t.trimStart : 0;
        double to = t.trimEnd > 0 ? t.trimEnd : Double.MAX_VALUE;
        for (Hls.Segment s : pl.segments) {
            if (s.start + s.duration > from && s.start < to) segs.add(s);
        }
        if (segs.isEmpty()) throw new IOException(ctx.getString(R.string.err_empty));
        if (t.durationSec <= 0) t.durationSec = pl.totalDuration;
        t.container = pl.isFragmentedMp4() ? "mp4" : "ts";
        int total = segs.size();
        if (t.segTotal != total) { // playlist changed or first run
            t.segDone = 0;
            t.bytesDone = 0;
        }
        t.segTotal = total;
        File tmp = new File(t.tempPath);
        prepareTemp(tmp);
        double clipDur = 0;
        for (Hls.Segment s : segs) clipDur += s.duration;

        int conns = Math.max(1, Math.min(10, Prefs.connections()));
        ExecutorService pool = Executors.newFixedThreadPool(conns);
        ArrayDeque<Future<byte[]>> window = new ArrayDeque<>();
        try (FileOutputStream out = new FileOutputStream(tmp, true)) {
            if (pl.isFragmentedMp4() && t.segDone == 0) {
                byte[] init = fetch(pl.initUrl, pl.initOffset, pl.initLength);
                out.write(init);
                t.bytesDone += init.length;
            }
            int next = t.segDone;
            while (next < total && window.size() < conns * 2) window.add(submit(pool, segs.get(next++)));
            resetSpeed();
            double doneDur = 0;
            for (int i = 0; i < t.segDone; i++) doneDur += segs.get(i).duration;
            for (int i = t.segDone; i < total; i++) {
                check(false);
                byte[] data;
                try {
                    data = window.poll().get();
                } catch (ExecutionException ee) {
                    Throwable c = ee.getCause();
                    if (c instanceof Exception) throw (Exception) c;
                    throw new IOException(c);
                }
                Hls.Segment s = segs.get(i);
                data = decrypt(data, s);
                out.write(data);
                t.bytesDone += data.length;
                t.segDone = i + 1;
                doneDur += s.duration;
                if (doneDur > 0) {
                    long est = (long) (t.bytesDone / doneDur * clipDur);
                    // Early on blend with the bitrate estimate; after a few segments the measurement wins.
                    t.totalBytes = t.totalBytes <= 0 || t.segDone >= 3 ? est : (long) (t.totalBytes * 0.5 + est * 0.5);
                }
                if (t.segDone == total) t.totalBytes = t.bytesDone;
                meter(data.length);
                if (next < total) window.add(submit(pool, segs.get(next++)));
                mgr.progress(t);
            }
            out.getFD().sync();
        } finally {
            for (Future<byte[]> f : window) f.cancel(true);
            pool.shutdownNow();
        }
    }

    private Future<byte[]> submit(ExecutorService pool, final Hls.Segment s) {
        return pool.submit(new Callable<byte[]>() {
            @Override public byte[] call() throws Exception { return fetch(s.url, s.byteOffset, s.byteLength); }
        });
    }

    /** Truncates the temp file to the last persisted good offset (drops a half-written segment). */
    private void prepareTemp(File tmp) throws IOException {
        File dir = tmp.getParentFile();
        if (dir != null && !dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        if (t.segDone == 0) t.bytesDone = 0;
        try (RandomAccessFile raf = new RandomAccessFile(tmp, "rw")) {
            if (raf.length() < t.bytesDone) { // temp lost: start over
                t.segDone = 0;
                t.bytesDone = 0;
            }
            raf.setLength(t.bytesDone);
        }
    }

    private byte[] fetch(String url, long offset, long length) throws Exception {
        int attempt = 0;
        while (true) {
            check(false);
            try {
                Map<String, String> h = null;
                if (offset >= 0 && length > 0) {
                    h = new HashMap<>();
                    h.put("Range", "bytes=" + offset + "-" + (offset + length - 1));
                }
                return Http.bytes(url, h, 30000);
            } catch (IOException e) {
                attempt++;
                boolean fatal = e instanceof Http.HttpException && (((Http.HttpException) e).code == 404
                        || ((Http.HttpException) e).code == 410) && attempt >= 3;
                if (fatal || attempt >= 7) throw e;
                sleep(Math.min(16000, 800L << attempt));
            }
        }
    }

    private void sleep(long ms) throws Stop {
        long end = SystemClock.elapsedRealtime() + ms;
        while (SystemClock.elapsedRealtime() < end) {
            check(false);
            if (t.live && t.stopRequested) return;
            try { Thread.sleep(Math.min(250, ms)); } catch (InterruptedException e) { throw new Stop(); }
        }
    }

    private byte[] decrypt(byte[] data, Hls.Segment s) throws Exception {
        if (s.key == null || !"AES-128".equals(s.key.method) || s.key.uri == null) return data;
        byte[] key = keys.get(s.key.uri);
        if (key == null) {
            key = Http.bytes(s.key.uri, null, 20000);
            keys.put(s.key.uri, key);
        }
        byte[] iv = s.key.iv != null ? s.key.iv : Hls.ivForSequence(s.sequence);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return c.doFinal(data);
    }

    // ------------------------------------------------------------------ live recording

    private void runLive() throws Exception {
        File tmp = new File(t.tempPath);
        if (t.stopRequested && tmp.exists() && tmp.length() > 0) {
            t.bytesDone = tmp.length();
            return; // resumed after process death: just finalize
        }
        t.container = "ts";
        t.segDone = 0;
        t.bytesDone = 0;
        t.segTotal = 0;
        t.recordedSec = 0;
        prepareTemp(tmp);
        long lastSeq = -1;
        int failures = 0;
        boolean wroteInit = false;
        resetSpeed();
        String mediaUrl = null;
        try (FileOutputStream out = new FileOutputStream(tmp, true)) {
            while (!t.stopRequested) {
                check(false);
                Hls.MediaPlaylist pl;
                try {
                    if (mediaUrl == null) {
                        String text = Http.text(t.url);
                        mediaUrl = t.url;
                        if (Hls.isMaster(text)) {
                            List<Hls.Variant> vs = Hls.parseMaster(text, t.url);
                            Hls.Variant pick = vs.get(0);
                            for (Hls.Variant v : vs) if (v.label().equals(t.quality)) { pick = v; break; }
                            if (t.quality == null || t.quality.isEmpty()) t.quality = pick.label();
                            mediaUrl = pick.url;
                        }
                    }
                    pl = Hls.parseMedia(Http.text(mediaUrl), mediaUrl);
                    failures = 0;
                } catch (IOException e) {
                    failures++;
                    if (failures >= 8) {
                        if (t.bytesDone > 0) break; // stream ended / went offline
                        throw e;
                    }
                    sleep(2000);
                    continue;
                }
                if (pl.isFragmentedMp4()) t.container = "mp4";
                if (pl.isFragmentedMp4() && !wroteInit) {
                    byte[] init = fetch(pl.initUrl, pl.initOffset, pl.initLength);
                    out.write(init);
                    t.bytesDone += init.length;
                    wroteInit = true;
                }
                for (Hls.Segment s : pl.segments) {
                    if (s.sequence <= lastSeq) continue;
                    if (t.stopRequested) break;
                    check(false);
                    byte[] data;
                    try {
                        data = decrypt(fetch(s.url, s.byteOffset, s.byteLength), s);
                    } catch (IOException e) {
                        lastSeq = s.sequence; // skip a lost segment rather than stall the recording
                        continue;
                    }
                    out.write(data);
                    lastSeq = s.sequence;
                    t.bytesDone += data.length;
                    t.segDone++;
                    t.recordedSec += s.duration;
                    t.durationSec = t.recordedSec;
                    meter(data.length);
                    mgr.progress(t);
                }
                if (pl.endList) break;
                sleep((long) (Math.max(1.0, pl.targetDuration / 2) * 1000));
            }
            out.getFD().sync();
        }
        if (t.bytesDone == 0) throw new IOException(ctx.getString(R.string.err_empty));
        t.segTotal = t.segDone;
        t.totalBytes = t.bytesDone;
    }

    // ------------------------------------------------------------------ direct file (mp4 clips)

    private void runDirect() throws Exception {
        File tmp = new File(t.tempPath);
        File dir = tmp.getParentFile();
        if (dir != null && !dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        String path = t.url.toLowerCase(Locale.US);
        t.container = path.contains(".webm") ? "webm" : "mp4";
        int attempt = 0;
        while (true) {
            check(false);
            long have = tmp.exists() ? tmp.length() : 0;
            HttpURLConnection c = null;
            try {
                Map<String, String> h = new HashMap<>();
                if (have > 0) h.put("Range", "bytes=" + have + "-");
                c = Http.open(t.url, h, 30000);
                int code = c.getResponseCode();
                if (code == 416) { t.bytesDone = have; t.totalBytes = have; return; }
                if (code >= 400) throw new Http.HttpException(code, null);
                boolean append = code == 206;
                long len = c.getContentLengthLong();
                if (!append) have = 0;
                t.totalBytes = len > 0 ? have + len : -1;
                t.bytesDone = have;
                resetSpeed();
                byte[] buf = new byte[256 * 1024];
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp, append)) {
                    int n;
                    long lastTick = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        t.bytesDone += n;
                        meter(n);
                        long now = SystemClock.elapsedRealtime();
                        if (now - lastTick > 300) {
                            lastTick = now;
                            mgr.progress(t);
                            check(false);
                        }
                    }
                }
                if (t.totalBytes > 0 && t.bytesDone < t.totalBytes) throw new IOException("Incomplete");
                t.totalBytes = t.bytesDone;
                return;
            } catch (IOException e) {
                attempt++;
                if (attempt >= 6) throw e;
                sleep(Math.min(16000, 1000L << attempt));
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }

    // ------------------------------------------------------------------ speed

    private void resetSpeed() {
        speedMark = SystemClock.elapsedRealtime();
        speedBytes = 0;
    }

    private void meter(int n) {
        speedBytes += n;
        long now = SystemClock.elapsedRealtime();
        long dt = now - speedMark;
        if (dt >= 700) {
            double inst = speedBytes * 1000.0 / dt;
            t.speed = t.speed <= 0 ? inst : t.speed * 0.6 + inst * 0.4;
            speedMark = now;
            speedBytes = 0;
        }
    }
}
