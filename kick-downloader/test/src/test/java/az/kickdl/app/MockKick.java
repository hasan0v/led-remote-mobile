package az.kickdl.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Local fake of kick.com API + HLS CDN. */
public class MockKick {
    public HttpServer server;
    public String root;
    public final AtomicInteger segHits = new AtomicInteger();

    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        root = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.start();
    }

    public void stop() { server.stop(0); }

    private void handle(HttpExchange ex) throws java.io.IOException {
        String path = ex.getRequestURI().getPath();
        String q = ex.getRequestURI().getQuery();
        byte[] body;
        String type = "application/json";
        int code = 200;
        if (path.equals("/api/v2/channels/streamerone")) body = channel().getBytes(StandardCharsets.UTF_8);
        else if (path.equals("/api/v2/channels/streamerone/videos")) body = videos().getBytes(StandardCharsets.UTF_8);
        else if (path.equals("/api/v2/channels/streamerone/clips")) body = clips(q).getBytes(StandardCharsets.UTF_8);
        else if (path.equals("/api/search")) body = search().getBytes(StandardCharsets.UTF_8);
        else if (path.startsWith("/api/v2/channels/")) { code = 404; body = "{\"message\":\"Not found\"}".getBytes(); }
        else if (path.equals("/live/master.m3u8")) { type = "application/vnd.apple.mpegurl"; body = ("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720,VIDEO=\"720p30\"\nlv/playlist.m3u8\n").getBytes(); }
        else if (path.equals("/live/lv/playlist.m3u8")) {
            type = "application/vnd.apple.mpegurl";
            long seq = System.currentTimeMillis() / 500;
            StringBuilder b = new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXT-X-MEDIA-SEQUENCE:" + seq + "\n");
            for (int i = 0; i < 3; i++) b.append("#EXTINF:0.5,\nseg").append(seq + i).append(".ts\n");
            body = b.toString().getBytes();
        }
        else if (path.equals("/c/video.mp4")) {
            byte[] full = new byte[300_000];
            for (int i = 0; i < full.length; i++) full[i] = (byte) i;
            String range = ex.getRequestHeaders().getFirst("Range");
            if (range != null && range.startsWith("bytes=")) {
                long from = Long.parseLong(range.substring(6, range.indexOf('-')));
                byte[] part = java.util.Arrays.copyOfRange(full, (int) from, full.length);
                ex.getResponseHeaders().add("Content-Range", "bytes " + from + "-" + (full.length - 1) + "/" + full.length);
                ex.getResponseHeaders().add("Content-Type", "video/mp4");
                ex.sendResponseHeaders(206, part.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(part); }
                return;
            }
            type = "video/mp4"; body = full;
        }
        else if (path.equals("/vod/master.m3u8")) { type = "application/vnd.apple.mpegurl"; body = master().getBytes(); }
        else if (path.endsWith("/playlist.m3u8")) { type = "application/vnd.apple.mpegurl"; body = media().getBytes(); }
        else if (path.endsWith(".ts")) {
            segHits.incrementAndGet();
            if (path.contains("slow")) { try { Thread.sleep(slowMs); } catch (InterruptedException ignored) {} }
            type = "video/mp2t";
            body = new byte[40_000 + path.length()];
            for (int i = 0; i < body.length; i += 188) body[i] = 0x47;
        } else if (path.startsWith("/img/")) { type = "image/png"; body = image(path); }
        else { code = 404; body = "nope".getBytes(); }
        ex.getResponseHeaders().add("Content-Type", type);
        if ("HEAD".equals(ex.getRequestMethod())) {
            ex.getResponseHeaders().add("Content-Length", String.valueOf(body.length));
            ex.sendResponseHeaders(code, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    static byte[] image(String path) {
        try {
            boolean avatar = path.contains("/p.");
            int w = avatar ? 256 : 640, h = avatar ? 256 : 360;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            int seed = Math.abs(path.hashCode());
            java.awt.Color c1 = java.awt.Color.getHSBColor((seed % 360) / 360f, 0.65f, 0.55f);
            java.awt.Color c2 = java.awt.Color.getHSBColor(((seed / 7) % 360) / 360f, 0.8f, 0.25f);
            g.setPaint(new java.awt.GradientPaint(0, 0, c1, w, h, c2));
            g.fillRect(0, 0, w, h);
            g.setColor(new java.awt.Color(255, 255, 255, 60));
            for (int i = 0; i < 6; i++) g.fillOval((seed >> i) % w, (seed >> (i + 3)) % h, w / 3, w / 3);
            g.setColor(new java.awt.Color(83, 252, 24));
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, avatar ? 120 : 64));
            g.drawString(avatar ? "X" : "LIVE GAMEPLAY", avatar ? 85 : 60, avatar ? 170 : 200);
            g.dispose();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    String channel() {
        return "{\"id\":668,\"user_id\":676,\"slug\":\"streamerone\",\"is_banned\":false,\"playback_url\":\"" + root + "/vod/master.m3u8\","
                + "\"vod_enabled\":true,\"followers_count\":812345,\"banner_image\":{\"url\":\"" + root + "/img/banner.webp\"},"
                + "\"livestream\":{\"id\":1,\"slug\":\"x\",\"channel_id\":668,\"created_at\":\"2025-08-25 00:40:43\",\"session_title\":\"LIVE NOW\",\"is_live\":true,"
                + "\"start_time\":\"2025-08-25 00:40:41\",\"viewer_count\":25000,\"thumbnail\":{\"url\":\"" + root + "/img/t.webp\"},\"categories\":[{\"id\":1,\"name\":\"Just Chatting\"}]},"
                + "\"verified\":true,\"user\":{\"id\":676,\"username\":\"StreamerOne\",\"bio\":\"THE BEST AT ABSOLUTELY EVERYTHING\",\"profile_pic\":\"" + root + "/img/p.webp\"}}";
    }

    String videos() {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < 6; i++) {
            if (i > 0) b.append(',');
            b.append("{\"id\":").append(i).append(",\"slug\":\"s").append(i).append("\",\"channel_id\":668,\"created_at\":\"2025-08-2").append(i)
                    .append(" 00:40:43\",\"session_title\":\"Stream number ").append(i).append(" with a pretty long title to wrap lines\",\"is_live\":")
                    .append(i == 0 ? "true" : "false").append(",\"start_time\":\"2025-08-2").append(i).append(" 00:40:41\",\"source\":\"")
                    .append(root).append("/vod/master.m3u8\",\"duration\":").append(3600000 + i * 1000).append(",\"language\":\"English\",\"is_mature\":true,")
                    .append("\"viewer_count\":0,\"thumbnail\":{\"src\":\"").append(root).append("/img/v").append(i).append(".webp\",\"srcset\":\"x 1x\"},")
                    .append("\"views\":").append(1000 * (i + 1)).append(",\"categories\":[{\"id\":15,\"name\":\"Deadlock\",\"slug\":\"deadlock\"}],")
                    .append("\"video\":{\"id\":5,\"uuid\":\"uuid-").append(i).append("\",\"views\":").append(1000 * (i + 1)).append("}}");
        }
        return b.append(']').toString();
    }

    String clips(String q) {
        return "{\"clips\":[{\"id\":\"clip_01J8\",\"livestream_id\":\"1\",\"title\":\"funny clip\",\"clip_url\":\"" + root + "/vod/master.m3u8\","
                + "\"thumbnail_url\":\"" + root + "/img/c.webp\",\"views\":1000,\"duration\":43,\"created_at\":\"2024-09-27T01:19:47.391Z\","
                + "\"video_url\":\"" + root + "/c/video.mp4\",\"category\":{\"id\":4,\"name\":\"Minecraft\"},\"creator\":{\"id\":2,\"username\":\"Anormal\"},"
                + "\"channel\":{\"id\":668,\"username\":\"StreamerOne\",\"slug\":\"streamerone\"}}],\"nextCursor\":null}";
    }

    String search() {
        return "{\"channels\":[{\"id\":668,\"user_id\":676,\"slug\":\"streamerone\",\"playback_url\":null,\"followersCount\":812345,"
                + "\"user\":{\"username\":\"StreamerOne\",\"profilePic\":\"" + root + "/img/p.webp\"},\"isLive\":true,\"verified\":{\"id\":1}},"
                + "{\"id\":2,\"slug\":\"streamfan\",\"followersCount\":12,\"user\":{\"username\":\"streamfan\"},\"isLive\":false,\"verified\":null}],"
                + "\"categories\":[{\"id\":1,\"slug\":\"just-chatting\",\"name\":\"Just Chatting\"}]}";
    }

    String master() {
        return "#EXTM3U\n"
                + "#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID=\"1080p60\",NAME=\"1080p60\"\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=8500000,RESOLUTION=1920x1080,FRAME-RATE=60.000,VIDEO=\"1080p60\"\n1080p60/playlist.m3u8\n"
                + "#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID=\"720p60\",NAME=\"720p60\"\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=3500000,RESOLUTION=1280x720,FRAME-RATE=60.000,VIDEO=\"720p60\"\n720p60/playlist.m3u8\n";
    }

    public volatile boolean slow;
    public volatile long slowMs = 120;

    String media() {
        StringBuilder b = new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:0\n");
        for (int i = 0; i < 12; i++) b.append("#EXTINF:10.0,\n").append(slow ? "slow" : "").append(i).append(".ts\n");
        return b.append("#EXT-X-ENDLIST\n").toString();
    }
}
