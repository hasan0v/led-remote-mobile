package az.kickdl.app.hls;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal HLS (m3u8) parser: master playlists, media playlists, keys, maps and byte ranges. */
public final class Hls {
    private Hls() {}

    public static class Variant {
        public String name;
        public int width, height;
        public double fps;
        public long bandwidth, avgBandwidth;
        public String codecs;
        public String url;
        /** Refined size estimate in bytes for the whole VOD, -1 = unknown. */
        public long estimatedBytes = -1;

        public long bitrate() { return avgBandwidth > 0 ? avgBandwidth : (long) (bandwidth * 0.82); }

        public String label() {
            if (name != null && !name.isEmpty()) {
                String n = name.toLowerCase(Locale.US);
                if (n.equals("chunked") || n.equals("source")) return height > 0 ? height + "p" + fpsSuffix() + " (Source)" : "Source";
                return name;
            }
            if (height > 0) return height + "p" + fpsSuffix();
            return bandwidth > 0 ? (bandwidth / 1000) + " kbps" : "Auto";
        }

        private String fpsSuffix() { return fps >= 49 ? String.valueOf(Math.round(fps)) : ""; }
    }

    public static class Key {
        public String method = "NONE";
        public String uri;
        public byte[] iv;
    }

    public static class Segment {
        public String url;
        public double duration;
        public double start;
        public long sequence;
        public long byteOffset = -1, byteLength = -1;
        public Key key;
        public boolean discontinuity;
    }

    public static class MediaPlaylist {
        public final List<Segment> segments = new ArrayList<>();
        public String initUrl;
        public long initOffset = -1, initLength = -1;
        public double targetDuration = 6;
        public long mediaSequence;
        public boolean endList;
        public double totalDuration;

        public boolean isFragmentedMp4() { return initUrl != null; }

        public boolean encrypted() {
            for (Segment s : segments) if (s.key != null && !"NONE".equals(s.key.method)) return true;
            return false;
        }
    }

    public static boolean isMaster(String text) { return text.contains("#EXT-X-STREAM-INF"); }

    public static List<Variant> parseMaster(String text, String baseUrl) {
        List<Variant> out = new ArrayList<>();
        if (!isMaster(text)) {
            Variant v = new Variant();
            v.name = "Source";
            v.url = baseUrl;
            out.add(v);
            return out;
        }
        Map<String, String> mediaNames = new HashMap<>();
        String[] lines = text.split("\r?\n");
        Map<String, String> pending = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-MEDIA:")) {
                Map<String, String> a = attrs(line.substring(13));
                if ("VIDEO".equals(a.get("TYPE")) && a.get("GROUP-ID") != null && a.get("NAME") != null) {
                    mediaNames.put(a.get("GROUP-ID"), a.get("NAME"));
                }
            } else if (line.startsWith("#EXT-X-STREAM-INF:")) {
                pending = attrs(line.substring(18));
            } else if (!line.startsWith("#") && pending != null) {
                Variant v = new Variant();
                v.url = resolve(baseUrl, line);
                v.bandwidth = parseLong(pending.get("BANDWIDTH"));
                v.avgBandwidth = parseLong(pending.get("AVERAGE-BANDWIDTH"));
                v.codecs = pending.get("CODECS");
                String res = pending.get("RESOLUTION");
                if (res != null && res.contains("x")) {
                    String[] wh = res.split("x");
                    v.width = (int) parseLong(wh[0]);
                    v.height = (int) parseLong(wh[1]);
                }
                try { v.fps = Double.parseDouble(pending.get("FRAME-RATE")); } catch (Exception ignored) {}
                String group = pending.get("VIDEO");
                v.name = group != null ? (mediaNames.containsKey(group) ? mediaNames.get(group) : group) : null;
                if (v.name == null) v.name = guessName(line);
                out.add(v);
                pending = null;
            }
        }
        // Dedupe by label keeping the highest bandwidth, then sort best first.
        LinkedHashMap<String, Variant> uniq = new LinkedHashMap<>();
        for (Variant v : out) {
            String k = v.label();
            Variant prev = uniq.get(k);
            if (prev == null || v.bandwidth > prev.bandwidth) uniq.put(k, v);
        }
        List<Variant> list = new ArrayList<>(uniq.values());
        Collections.sort(list, new Comparator<Variant>() {
            @Override public int compare(Variant a, Variant b) {
                if (a.height != b.height) return b.height - a.height;
                if (a.fps != b.fps) return Double.compare(b.fps, a.fps);
                return Long.compare(b.bandwidth, a.bandwidth);
            }
        });
        return list;
    }

    private static String guessName(String uri) {
        Matcher m = Pattern.compile("(\\d{3,4}p\\d{0,2})").matcher(uri);
        return m.find() ? m.group(1) : null;
    }

    public static MediaPlaylist parseMedia(String text, String baseUrl) {
        MediaPlaylist pl = new MediaPlaylist();
        String[] lines = text.split("\r?\n");
        double nextDur = 0;
        long nextOffset = -1, nextLength = -1, lastEnd = 0;
        boolean disc = false;
        Key key = null;
        long seq = 0;
        boolean seqSet = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                String d = line.substring(8);
                int comma = d.indexOf(',');
                if (comma >= 0) d = d.substring(0, comma);
                try { nextDur = Double.parseDouble(d.trim()); } catch (Exception e) { nextDur = 0; }
            } else if (line.startsWith("#EXT-X-TARGETDURATION:")) {
                try { pl.targetDuration = Double.parseDouble(line.substring(22).trim()); } catch (Exception ignored) {}
            } else if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                pl.mediaSequence = parseLong(line.substring(22).trim());
                seq = pl.mediaSequence;
                seqSet = true;
            } else if (line.startsWith("#EXT-X-ENDLIST")) {
                pl.endList = true;
            } else if (line.startsWith("#EXT-X-DISCONTINUITY") && !line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE")) {
                disc = true;
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                String br = line.substring(17).trim();
                int at = br.indexOf('@');
                nextLength = parseLong(at >= 0 ? br.substring(0, at) : br);
                nextOffset = at >= 0 ? parseLong(br.substring(at + 1)) : lastEnd;
            } else if (line.startsWith("#EXT-X-KEY:")) {
                Map<String, String> a = attrs(line.substring(11));
                key = new Key();
                key.method = a.containsKey("METHOD") ? a.get("METHOD") : "NONE";
                if (a.get("URI") != null) key.uri = resolve(baseUrl, a.get("URI"));
                if (a.get("IV") != null) key.iv = hex(a.get("IV"));
                if ("NONE".equals(key.method)) key = null;
            } else if (line.startsWith("#EXT-X-MAP:")) {
                Map<String, String> a = attrs(line.substring(11));
                if (a.get("URI") != null) pl.initUrl = resolve(baseUrl, a.get("URI"));
                String br = a.get("BYTERANGE");
                if (br != null) {
                    int at = br.indexOf('@');
                    pl.initLength = parseLong(at >= 0 ? br.substring(0, at) : br);
                    pl.initOffset = at >= 0 ? parseLong(br.substring(at + 1)) : 0;
                }
            } else if (!line.startsWith("#")) {
                Segment s = new Segment();
                s.url = resolve(baseUrl, line);
                s.duration = nextDur;
                s.start = pl.totalDuration;
                s.sequence = seq++;
                s.key = key;
                s.discontinuity = disc;
                s.byteOffset = nextOffset;
                s.byteLength = nextLength;
                if (nextLength > 0) lastEnd = nextOffset + nextLength;
                pl.totalDuration += nextDur;
                pl.segments.add(s);
                nextDur = 0;
                nextOffset = -1;
                nextLength = -1;
                disc = false;
            }
        }
        if (!seqSet) pl.mediaSequence = 0;
        return pl;
    }

    public static Map<String, String> attrs(String s) {
        Map<String, String> m = new HashMap<>();
        Matcher mt = Pattern.compile("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)").matcher(s);
        while (mt.find()) {
            String v = mt.group(2);
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
            m.put(mt.group(1), v);
        }
        return m;
    }

    public static String resolve(String base, String rel) {
        try {
            if (rel.startsWith("http://") || rel.startsWith("https://")) return rel;
            return URI.create(base).resolve(rel.replace(" ", "%20")).toString();
        } catch (Exception e) {
            int slash = base.lastIndexOf('/');
            return (slash >= 0 ? base.substring(0, slash + 1) : base) + rel;
        }
    }

    private static long parseLong(String s) {
        if (s == null) return 0;
        try { return Long.parseLong(s.trim()); } catch (Exception e) {
            try { return (long) Double.parseDouble(s.trim()); } catch (Exception e2) { return 0; }
        }
    }

    private static byte[] hex(String s) {
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.length() % 2 == 1) s = "0" + s;
        byte[] out = new byte[16];
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        System.arraycopy(b, Math.max(0, b.length - 16), out, Math.max(0, 16 - b.length), Math.min(16, b.length));
        return out;
    }

    public static byte[] ivForSequence(long seq) {
        byte[] iv = new byte[16];
        for (int i = 15; i >= 8; i--) {
            iv[i] = (byte) (seq & 0xFF);
            seq >>= 8;
        }
        return iv;
    }
}
