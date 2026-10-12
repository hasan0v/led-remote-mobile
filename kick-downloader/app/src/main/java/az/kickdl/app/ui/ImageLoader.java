package az.kickdl.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import az.kickdl.app.net.Http;

/** Small async image loader with memory + disk cache and fade-in. */
public final class ImageLoader {
    private ImageLoader() {}

    private static LruCache<String, Bitmap> mem;
    private static File diskDir;
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<String, Boolean> failed = new HashMap<>();

    public static void init(Context c) {
        int max = (int) (Runtime.getRuntime().maxMemory() / 6);
        mem = new LruCache<String, Bitmap>(max) {
            @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
        };
        diskDir = new File(c.getCacheDir(), "img");
        //noinspection ResultOfMethodCallIgnored
        diskDir.mkdirs();
    }

    public static void load(final ImageView iv, final String url, final int targetWidthPx) {
        load(iv, url, targetWidthPx, null);
    }

    public interface Callback { void onLoaded(Bitmap b); }

    public static void load(final ImageView iv, final String url, final int targetWidthPx, final Callback cb) {
        iv.setTag(url);
        if (url == null || url.isEmpty()) {
            iv.setImageDrawable(null);
            return;
        }
        final String key = url + "#" + targetWidthPx;
        Bitmap b = mem.get(key);
        if (b != null) {
            iv.setImageBitmap(b);
            iv.setAlpha(1f);
            if (cb != null) cb.onLoaded(b);
            return;
        }
        iv.setImageDrawable(null);
        synchronized (failed) { if (failed.containsKey(url)) return; }
        pool.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = fetch(url, targetWidthPx);
                if (bmp == null) {
                    synchronized (failed) { failed.put(url, true); }
                    return;
                }
                mem.put(key, bmp);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (!url.equals(iv.getTag())) return;
                        iv.setImageBitmap(bmp);
                        iv.setAlpha(0f);
                        iv.animate().alpha(1f).setDuration(260).start();
                        if (cb != null) cb.onLoaded(bmp);
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String url, int targetW) {
        try {
            File f = new File(diskDir, md5(url));
            if (!f.exists() || f.length() == 0) {
                byte[] data;
                try {
                    data = Http.bytes(url, null, 15000);
                } catch (Exception e) {
                    Map<String, String> h = new HashMap<>();
                    h.put("Referer", "https://kick.com/");
                    String ck = Http.kickCookies();
                    if (ck != null) h.put("Cookie", ck);
                    data = Http.bytes(url, h, 15000);
                }
                if (data.length == 0) return null;
                File tmp = new File(f.getPath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(data); }
                //noinspection ResultOfMethodCallIgnored
                tmp.renameTo(f);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            if (targetW > 0) while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Throwable e) {
            return null;
        }
    }

    public static long diskSize() {
        long s = 0;
        File[] fs = diskDir.listFiles();
        if (fs != null) for (File f : fs) s += f.length();
        return s;
    }

    public static void clear() {
        mem.evictAll();
        synchronized (failed) { failed.clear(); }
        File[] fs = diskDir.listFiles();
        if (fs != null) for (File f : fs) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    private static String md5(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return new BigInteger(1, md.digest(s.getBytes("UTF-8"))).toString(16);
    }
}
