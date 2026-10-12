package az.kickdl.app.dl;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Lossless container conversion (MPEG-TS / fMP4 -> MP4 or audio-only M4A) using platform APIs.
 *
 * Kick streams carry AAC audio in ADTS framing. Depending on the device, MediaExtractor may hand out
 * an AAC track without codec config (csd-0) and/or samples that still start with ADTS headers;
 * MP4 needs raw AAC frames plus an AudioSpecificConfig, so both are rebuilt here. The result is
 * verified afterwards: if the source had sound and the MP4 does not, the conversion fails and the
 * caller keeps the original TS instead.
 */
final class Remuxer {
    private Remuxer() {}

    interface Progress {
        /** @return false to abort */
        boolean onProgress(double fraction);
    }

    static final int[] AAC_RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};

    static void remux(String inPath, FileDescriptor out, boolean audioOnly, double durationSecHint, Progress cb)
            throws IOException {
        long base = probeStart(inPath);
        MediaExtractor ex = new MediaExtractor();
        MediaMuxer mux = null;
        long audioSamples = 0, videoSamples = 0;
        boolean srcAudio = false, srcVideo = false;
        try {
            ex.setDataSource(inPath);
            mux = new MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int n = ex.getTrackCount();
            int[] map = new int[n];
            boolean[] audioTrack = new boolean[n];
            boolean[] aacTrack = new boolean[n];
            long[] frameUs = new long[n];
            long[] nextPts = new long[n];
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
                if (a) srcAudio = true;
                if (v) srcVideo = true;
                if ((!v && !a) || (v && (audioOnly || hasVideo)) || (a && hasAudio)) continue;
                MediaFormat target = f;
                if (a && isAac(mime)) {
                    target = cleanAacFormat(inPath, i, f);
                    aacTrack[i] = true;
                    frameUs[i] = 1024L * 1_000_000L / target.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    nextPts[i] = -1;
                }
                try {
                    map[i] = mux.addTrack(target);
                } catch (Exception e) {
                    // Never silently drop a track: the caller falls back to the original stream.
                    throw new IOException("Unsupported track " + mime + ": " + e.getMessage());
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
            ByteBuffer frameBuf = ByteBuffer.allocateDirect(16 * 1024);
            int[] frames = new int[2 * 256];
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long[] last = new long[n];
            for (int i = 0; i < n; i++) last[i] = -1;
            long lastReport = 0;
            while (true) {
                buf.clear();
                int size = ex.readSampleData(buf, 0);
                if (size < 0) break;
                int track = ex.getSampleTrackIndex();
                long pts = ex.getSampleTime();
                if (track >= 0 && track < n && map[track] >= 0 && pts >= 0) {
                    long rel = Math.max(0, pts - base);
                    if (aacTrack[track]) {
                        // One extracted sample may hold several ADTS frames: write each raw AAC frame
                        // as its own MP4 sample on a gap-free 1024-sample timeline.
                        int count = splitAdts(buf, size, frames);
                        for (int k = 0; k < count; k++) {
                            int start = frames[2 * k], len = frames[2 * k + 1];
                            if (len <= 0 || len > frameBuf.capacity()) continue;
                            long srcPts = rel + k * frameUs[track];
                            long p = nextPts[track] < 0 || Math.abs(srcPts - nextPts[track]) > 80_000 ? srcPts : nextPts[track];
                            if (p <= last[track]) p = last[track] + 1;
                            frameBuf.clear();
                            ByteBuffer src = buf.duplicate();
                            src.limit(start + len);
                            src.position(start);
                            frameBuf.put(src);
                            frameBuf.flip();
                            info.offset = 0;
                            info.size = len;
                            info.presentationTimeUs = p;
                            info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                            mux.writeSampleData(map[track], frameBuf, info);
                            last[track] = p;
                            nextPts[track] = p + frameUs[track];
                            audioSamples++;
                        }
                    } else {
                        info.offset = 0;
                        info.size = size;
                        info.presentationTimeUs = rel;
                        info.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                        if (!audioTrack[track] || rel > last[track]) {
                            if (audioTrack[track]) info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                            buf.position(0);
                            buf.limit(size);
                            mux.writeSampleData(map[track], buf, info);
                            last[track] = rel;
                            if (audioTrack[track]) audioSamples++;
                            else videoSamples++;
                        }
                    }
                    if (cb != null && durUs > 0 && rel - lastReport > 2_000_000) {
                        lastReport = rel;
                        if (!cb.onProgress(Math.min(1, rel / (double) durUs))) throw new IOException("Canceled");
                    }
                }
                if (!ex.advance()) break;
            }
            if (hasAudio && audioSamples == 0) throw new IOException("No audio samples written");
            if (hasVideo && videoSamples == 0) throw new IOException("No video samples written");
            mux.stop();
        } catch (RuntimeException e) {
            throw new IOException("Remux failed: " + e.getMessage());
        } finally {
            try { if (mux != null) mux.release(); } catch (Exception ignored) {}
            ex.release();
        }
        verify(out, srcAudio, srcVideo && !audioOnly);
    }

    static boolean isAac(String mime) {
        return "audio/mp4a-latm".equals(mime) || "audio/aac".equals(mime) || "audio/aac-adts".equals(mime);
    }

    /** AAC format the MP4 writer accepts: mime, rate, channels and an AudioSpecificConfig. */
    private static MediaFormat cleanAacFormat(String path, int track, MediaFormat src) {
        byte[] adts = firstAdtsHeader(path, track);
        int rate = src.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? src.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 0;
        int channels = src.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? src.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 0;
        byte[] asc = null;
        if (src.containsKey("csd-0")) {
            ByteBuffer csd = src.getByteBuffer("csd-0");
            if (csd != null && csd.remaining() >= 2) {
                asc = new byte[csd.remaining()];
                csd.duplicate().get(asc);
            }
        }
        if (adts != null) {
            if (asc == null) asc = ascFromAdts(adts);
            if (rate <= 0) rate = rateFromAdts(adts);
            if (channels <= 0) channels = channelsFromAdts(adts);
        }
        if (rate <= 0) rate = 48000;
        if (channels <= 0) channels = 2;
        if (asc == null) asc = ascFor(2, rate, channels); // AAC-LC
        MediaFormat f = MediaFormat.createAudioFormat("audio/mp4a-latm", rate, channels);
        f.setByteBuffer("csd-0", ByteBuffer.wrap(asc));
        if (src.containsKey(MediaFormat.KEY_DURATION)) f.setLong(MediaFormat.KEY_DURATION, src.getLong(MediaFormat.KEY_DURATION));
        if (src.containsKey(MediaFormat.KEY_BIT_RATE)) f.setInteger(MediaFormat.KEY_BIT_RATE, src.getInteger(MediaFormat.KEY_BIT_RATE));
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024);
        return f;
    }

    private static byte[] firstAdtsHeader(String path, int track) {
        MediaExtractor e = new MediaExtractor();
        try {
            e.setDataSource(path);
            e.selectTrack(track);
            ByteBuffer b = ByteBuffer.allocate(256 * 1024);
            int size = e.readSampleData(b, 0);
            if (size >= 7 && adtsHeaderLength(b, size) > 0) {
                byte[] h = new byte[7];
                for (int i = 0; i < 7; i++) h[i] = b.get(i);
                return h;
            }
        } catch (Exception ignored) {
        } finally {
            e.release();
        }
        return null;
    }

    /** 7 or 9 when the sample starts with an ADTS header, otherwise 0. */
    static int adtsHeaderLength(ByteBuffer b, int size) {
        if (size < 7) return 0;
        int b0 = b.get(0) & 0xFF, b1 = b.get(1) & 0xFF;
        if (b0 != 0xFF || (b1 & 0xF6) != 0xF0) return 0; // sync word + layer 00
        return (b1 & 0x01) == 1 ? 7 : 9;
    }

    /**
     * Splits a sample into raw AAC frames. Writes (start, length) pairs of the payloads (ADTS headers
     * removed) into out and returns the frame count. A sample without ADTS framing is one raw frame.
     */
    static int splitAdts(ByteBuffer b, int size, int[] out) {
        int count = 0, off = 0;
        if (adtsHeaderLength(at(b, 0), size) == 0) {
            out[0] = 0;
            out[1] = size;
            return 1;
        }
        while (off + 7 <= size && count < out.length / 2) {
            int b0 = b.get(off) & 0xFF, b1 = b.get(off + 1) & 0xFF;
            if (b0 != 0xFF || (b1 & 0xF6) != 0xF0) break; // lost sync: ignore the rest
            int header = (b1 & 0x01) == 1 ? 7 : 9;
            int frameLen = ((b.get(off + 3) & 0x03) << 11) | ((b.get(off + 4) & 0xFF) << 3) | ((b.get(off + 5) & 0xE0) >> 5);
            if (frameLen <= header) break;
            int end = Math.min(size, off + frameLen);
            out[2 * count] = off + header;
            out[2 * count + 1] = end - (off + header);
            count++;
            off += frameLen;
        }
        return count;
    }

    private static ByteBuffer at(ByteBuffer b, int index) {
        ByteBuffer d = b.duplicate();
        d.position(index);
        return d.slice();
    }

    /** ADTS header -> 2-byte AudioSpecificConfig. */
    static byte[] ascFromAdts(byte[] h) {
        int objectType = ((h[2] & 0xFF) >> 6) + 1;
        int freqIndex = ((h[2] & 0xFF) >> 2) & 0x0F;
        int channels = channelsFromAdts(h);
        return new byte[]{(byte) ((objectType << 3) | (freqIndex >> 1)), (byte) (((freqIndex & 1) << 7) | (channels << 3))};
    }

    static byte[] ascFor(int objectType, int rate, int channels) {
        int idx = 3;
        for (int i = 0; i < AAC_RATES.length; i++) if (AAC_RATES[i] == rate) idx = i;
        return new byte[]{(byte) ((objectType << 3) | (idx >> 1)), (byte) (((idx & 1) << 7) | (channels << 3))};
    }

    static int rateFromAdts(byte[] h) {
        int idx = ((h[2] & 0xFF) >> 2) & 0x0F;
        return idx < AAC_RATES.length ? AAC_RATES[idx] : 0;
    }

    static int channelsFromAdts(byte[] h) { return ((h[2] & 0x01) << 2) | ((h[3] & 0xFF) >> 6); }

    /** Reads the written MP4 back; fails if a track that existed in the source is missing or truncated. */
    private static void verify(FileDescriptor fd, boolean needAudio, boolean needVideo) throws IOException {
        MediaExtractor e = new MediaExtractor();
        try {
            e.setDataSource(fd);
            boolean audio = false, video = false;
            long aDur = -1, vDur = -1;
            for (int i = 0; i < e.getTrackCount(); i++) {
                MediaFormat f = e.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                long d = f.containsKey(MediaFormat.KEY_DURATION) ? f.getLong(MediaFormat.KEY_DURATION) : -1;
                if (mime.startsWith("audio/")) { audio = true; aDur = d; }
                if (mime.startsWith("video/")) { video = true; vDur = d; }
            }
            if (needAudio && !audio) throw new IOException("MP4 has no audio track");
            if (needVideo && !video) throw new IOException("MP4 has no video track");
            if (needAudio && needVideo && aDur > 0 && vDur > 60_000_000L && aDur < vDur * 0.9)
                throw new IOException("MP4 audio is truncated");
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Cannot verify MP4: " + ex.getMessage());
        } finally {
            e.release();
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
