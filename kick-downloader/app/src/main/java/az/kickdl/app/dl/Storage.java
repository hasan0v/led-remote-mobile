package az.kickdl.app.dl;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import az.kickdl.app.FilesProvider;
import az.kickdl.app.data.Prefs;

/** Where finished files go: the shared Movies/Music folder (gallery) or the app folder. */
public final class Storage {
    private Storage() {}

    public static final String FOLDER = "KickDL";

    public static class Target {
        public Uri uri;
        public File file;
        public ParcelFileDescriptor pfd;
        public String name;
        boolean pendingMediaStore;
    }

    public static File tempDir(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        File d = new File(base, "tmp");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    public static File privateDir(Context c, boolean audio) {
        File base = c.getExternalFilesDir(audio ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES);
        if (base == null) base = new File(c.getFilesDir(), audio ? "Music" : "Movies");
        File d = new File(base, FOLDER);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    public static String locationLabel(Context c, boolean audio) {
        if (useGallery(c)) return (audio ? "Music/" : "Movies/") + FOLDER;
        return privateDir(c, audio).getAbsolutePath();
    }

    static boolean useGallery(Context c) {
        if (!Prefs.gallery()) return false;
        if (Build.VERSION.SDK_INT >= 29) return true;
        return c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static Target create(Context c, String displayName, String mime, boolean audio) throws IOException {
        Target t = new Target();
        t.name = displayName;
        if (useGallery(c)) {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver r = c.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, (audio ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES) + "/" + FOLDER);
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri coll = audio ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        : MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri uri;
                try {
                    uri = r.insert(coll, v);
                } catch (Exception e) {
                    uri = null;
                }
                if (uri == null) {
                    // Some devices reject unusual MIME types (e.g. video/mp2t): retry in Downloads.
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
                    uri = r.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                }
                if (uri == null) throw new IOException("MediaStore insert failed");
                t.uri = uri;
                t.pendingMediaStore = true;
                t.pfd = r.openFileDescriptor(uri, "rw");
                if (t.pfd == null) throw new IOException("Cannot open output");
                return t;
            }
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    audio ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES), FOLDER);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            t.file = unique(dir, displayName);
        } else {
            t.file = unique(privateDir(c, audio), displayName);
        }
        t.name = t.file.getName();
        t.pfd = ParcelFileDescriptor.open(t.file, ParcelFileDescriptor.MODE_READ_WRITE
                | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        return t;
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            f = new File(dir, stem + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, stem + " " + System.currentTimeMillis() + ext);
    }

    /** Closes the descriptor, publishes the item and resolves a shareable content:// uri. */
    public static void commit(Context c, Target t) throws IOException {
        if (t.pfd != null) {
            t.pfd.close();
            t.pfd = null;
        }
        if (t.pendingMediaStore && Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            c.getContentResolver().update(t.uri, v, null, null);
            return;
        }
        if (t.file != null) {
            if (useGallery(c) && t.file.getAbsolutePath().startsWith(Environment.getExternalStorageDirectory().getAbsolutePath())
                    && !t.file.getAbsolutePath().contains("/Android/data/")) {
                final Uri[] out = new Uri[1];
                final CountDownLatch latch = new CountDownLatch(1);
                MediaScannerConnection.scanFile(c, new String[]{t.file.getAbsolutePath()}, null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            @Override public void onScanCompleted(String path, Uri uri) {
                                out[0] = uri;
                                latch.countDown();
                            }
                        });
                try { latch.await(8, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                t.uri = out[0] != null ? out[0] : FilesProvider.uriFor(c, t.file);
            } else {
                t.uri = FilesProvider.uriFor(c, t.file);
            }
        }
    }

    public static void abort(Context c, Target t) {
        try { if (t.pfd != null) t.pfd.close(); } catch (Exception ignored) {}
        try {
            if (t.pendingMediaStore && t.uri != null) c.getContentResolver().delete(t.uri, null, null);
        } catch (Exception ignored) {}
        if (t.file != null) //noinspection ResultOfMethodCallIgnored
            t.file.delete();
    }

    public static void copy(File src, Target t, Remuxer.Progress cb) throws IOException {
        long total = Math.max(1, src.length()), done = 0, lastReport = 0;
        byte[] buf = new byte[1024 * 1024];
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(t.pfd.getFileDescriptor())) {
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (cb != null && done - lastReport > 32L * 1024 * 1024) {
                    lastReport = done;
                    if (!cb.onProgress(done / (double) total)) throw new IOException("Canceled");
                }
            }
            out.getFD().sync();
        }
    }

    public static boolean delete(Context c, String uriString) {
        try {
            Uri u = Uri.parse(uriString);
            if ("file".equals(u.getScheme())) return new File(u.getPath()).delete();
            if (FilesProvider.AUTHORITY.equals(u.getAuthority())) {
                File f = FilesProvider.fileFor(c, u);
                return f != null && f.delete();
            }
            return c.getContentResolver().delete(u, null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean exists(Context c, String uriString) {
        if (uriString == null) return false;
        try {
            Uri u = Uri.parse(uriString);
            if (FilesProvider.AUTHORITY.equals(u.getAuthority())) {
                File f = FilesProvider.fileFor(c, u);
                return f != null && f.exists();
            }
            ParcelFileDescriptor p = c.getContentResolver().openFileDescriptor(u, "r");
            if (p == null) return false;
            p.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static long freeBytes(Context c) {
        try {
            File d = tempDir(c);
            return new StatFs(d.getAbsolutePath()).getAvailableBytes();
        } catch (Exception e) {
            return -1;
        }
    }

    public static long totalBytes(Context c) {
        try {
            File d = tempDir(c);
            return new StatFs(d.getAbsolutePath()).getTotalBytes();
        } catch (Exception e) {
            return -1;
        }
    }

    public static String sanitize(String s) {
        String r = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        // Strip emoji/symbols that some file systems and players choke on.
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < r.length(); ) {
            int cp = r.codePointAt(i);
            if (Character.isLetterOrDigit(cp) || " -_.,()[]&'+!#".indexOf(cp) >= 0) b.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        String out = b.toString().replaceAll("\\s+", " ").trim();
        if (out.length() > 90) out = out.substring(0, 90).trim();
        return out.isEmpty() ? "video" : out;
    }
}
