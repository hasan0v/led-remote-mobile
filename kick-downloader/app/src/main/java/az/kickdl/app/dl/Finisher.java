package az.kickdl.app.dl;

import android.content.Context;

import java.io.File;
import java.io.IOException;

import az.kickdl.app.data.Media;

/** Turns the downloaded stream into the final file (remux/copy) and publishes it. */
final class Finisher {
    private Finisher() {}

    private static final long REMUX_LIMIT = 3_900L * 1024 * 1024; // MP4 writer is unreliable past 4 GB

    static void finish(Context c, final DownloadTask t) throws IOException {
        final File src = new File(t.tempPath);
        if (!src.exists() || src.length() == 0) throw new IOException("Download file missing");
        String base = buildName(t);
        Remuxer.Progress cb = new Remuxer.Progress() {
            @Override public boolean onProgress(double f) {
                t.mergeProgress = f;
                DownloadManager.get().changed(false);
                return !t.cancelRequested;
            }
        };
        t.mergeProgress = 0;

        if (t.audioOnly) {
            Storage.Target out = Storage.create(c, base + ".m4a", "audio/mp4", true);
            try {
                Remuxer.remux(src.getAbsolutePath(), out.pfd.getFileDescriptor(), true, t.clipDuration(), cb);
                Storage.commit(c, out);
            } catch (IOException | RuntimeException e) {
                Storage.abort(c, out);
                throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage());
            }
            done(t, out, "audio/mp4", src);
            return;
        }

        boolean wantMp4 = "mp4".equals(t.format) || !"ts".equals(t.container);
        if ("ts".equals(t.container) && wantMp4 && src.length() < REMUX_LIMIT) {
            Storage.Target out = Storage.create(c, base + ".mp4", "video/mp4", false);
            try {
                Remuxer.remux(src.getAbsolutePath(), out.pfd.getFileDescriptor(), false, t.clipDuration(), cb);
                Storage.commit(c, out);
                done(t, out, "video/mp4", src);
                return;
            } catch (IOException | RuntimeException e) {
                Storage.abort(c, out);
                if (t.cancelRequested) throw new IOException("Canceled");
                // fall through: keep the original transport stream
            }
        }
        String ext = "ts".equals(t.container) ? ".ts" : t.kind == Media.CLIP && "webm".equals(t.container) ? ".webm" : ".mp4";
        String mime = ".ts".equals(ext) ? "video/mp2t" : ".webm".equals(ext) ? "video/webm" : "video/mp4";
        Storage.Target out = Storage.create(c, base + ext, mime, false);
        try {
            Storage.copy(src, out, cb);
            Storage.commit(c, out);
        } catch (IOException | RuntimeException e) {
            Storage.abort(c, out);
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage());
        }
        done(t, out, mime, src);
    }

    private static void done(DownloadTask t, Storage.Target out, String mime, File src) {
        t.outputUri = out.uri != null ? out.uri.toString() : null;
        t.outputName = out.name;
        t.mime = mime;
        t.outputSize = out.file != null ? out.file.length() : -1;
        if (t.outputSize <= 0) t.outputSize = sizeOf(out);
        t.mergeProgress = 1;
        //noinspection ResultOfMethodCallIgnored
        src.delete();
    }

    private static long sizeOf(Storage.Target out) {
        try {
            android.database.Cursor cur = DownloadManager.get().context().getContentResolver().query(out.uri,
                    new String[]{android.provider.MediaStore.MediaColumns.SIZE}, null, null, null);
            if (cur != null) {
                try { if (cur.moveToFirst()) return cur.getLong(0); } finally { cur.close(); }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static String buildName(DownloadTask t) {
        StringBuilder b = new StringBuilder();
        if (t.channelName != null && !t.channelName.isEmpty()) b.append(t.channelName).append(" - ");
        b.append(t.title);
        String name = Storage.sanitize(b.toString());
        String tag = t.audioOnly ? "audio" : t.quality;
        if (t.trimStart > 0 || t.trimEnd > 0) tag = (tag == null || tag.isEmpty() ? "" : tag + " ") + "cut";
        if (t.live) tag = (tag == null || tag.isEmpty() ? "" : tag + " ") + "live";
        if (tag != null && !tag.isEmpty()) name += " [" + Storage.sanitize(tag) + "]";
        return name;
    }
}
