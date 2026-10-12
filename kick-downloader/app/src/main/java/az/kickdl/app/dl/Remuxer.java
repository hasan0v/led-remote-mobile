package az.kickdl.app.dl;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Lossless container conversion (MPEG-TS / fMP4 -> MP4 or audio-only M4A) using platform APIs. */
final class Remuxer {
    private Remuxer() {}

    interface Progress {
        /** @return false to abort */
        boolean onProgress(double fraction);
    }

    static void remux(String inPath, FileDescriptor out, boolean audioOnly, double durationSecHint, Progress cb)
            throws IOException {
        long base = probeStart(inPath);
        MediaExtractor ex = new MediaExtractor();
        MediaMuxer mux = null;
        try {
            ex.setDataSource(inPath);
            mux = new MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int n = ex.getTrackCount();
            int[] map = new int[n];
            boolean[] audioTrack = new boolean[n];
            long durUs = 0;
            int maxSize = 2 * 1024 * 1024;
            boolean hasVideo = false, hasAudio = false;
            for (int i = 0; i < n; i++) {
                map[i] = -1;
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                boolean v = mime.startsWith("video/");
                boolean a = mime.startsWith("audio/");
                audioTrack[i] = a;
                if ((!v && !a) || (v && (audioOnly || hasVideo)) || (a && hasAudio)) continue;
                try {
                    map[i] = mux.addTrack(f);
                } catch (Exception e) {
                    if (v || audioOnly) throw new IOException("Unsupported track: " + mime);
                    continue;
                }
                ex.selectTrack(i);
                if (v) hasVideo = true;
                if (a) hasAudio = true;
                if (f.containsKey(MediaFormat.KEY_DURATION)) durUs = Math.max(durUs, f.getLong(MediaFormat.KEY_DURATION));
                if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxSize = Math.max(maxSize, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            }
            if (!hasVideo && !hasAudio) throw new IOException("No tracks");
            if (audioOnly && !hasAudio) throw new IOException("No audio track");
            if (durUs <= 0 && durationSecHint > 0) durUs = (long) (durationSecHint * 1_000_000);
            mux.start();
            ByteBuffer buf = ByteBuffer.allocateDirect(Math.max(maxSize, 8 * 1024 * 1024));
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long[] last = new long[n];
            for (int i = 0; i < n; i++) last[i] = -1;
            long lastReport = 0;
            while (true) {
                int size = ex.readSampleData(buf, 0);
                if (size < 0) break;
                int track = ex.getSampleTrackIndex();
                long pts = ex.getSampleTime();
                if (track >= 0 && track < n && map[track] >= 0 && pts >= 0) {
                    long rel = Math.max(0, pts - base);
                    info.offset = 0;
                    info.size = size;
                    info.presentationTimeUs = rel;
                    info.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                    // Audio must be strictly increasing; drop overlapping packets after discontinuities.
                    if (!audioTrack[track] || rel > last[track]) {
                        mux.writeSampleData(map[track], buf, info);
                        last[track] = rel;
                    }
                    if (cb != null && durUs > 0 && rel - lastReport > 2_000_000) {
                        lastReport = rel;
                        if (!cb.onProgress(Math.min(1, rel / (double) durUs))) throw new IOException("Canceled");
                    }
                }
                if (!ex.advance()) break;
            }
            mux.stop();
        } finally {
            try { if (mux != null) mux.release(); } catch (Exception ignored) {}
            ex.release();
        }
    }

    /** Smallest timestamp among the first samples of every track (keeps A/V in sync after rebasing). */
    private static long probeStart(String path) {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(path);
            for (int i = 0; i < ex.getTrackCount(); i++) ex.selectTrack(i);
            long min = Long.MAX_VALUE;
            for (int k = 0; k < 400; k++) {
                long t = ex.getSampleTime();
                if (t >= 0) min = Math.min(min, t);
                if (!ex.advance()) break;
            }
            return min == Long.MAX_VALUE ? 0 : min;
        } catch (Exception e) {
            return 0;
        } finally {
            ex.release();
        }
    }
}
