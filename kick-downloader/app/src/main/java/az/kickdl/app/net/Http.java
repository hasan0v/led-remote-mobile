package az.kickdl.app.net;

import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Tiny HttpURLConnection wrapper with browser-like headers. */
public final class Http {
    private Http() {}

    public static volatile String UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";

    public static class Resp {
        public int code;
        public byte[] body;
        public String contentType;

        public String text() { return body == null ? "" : new String(body, StandardCharsets.UTF_8); }

        public boolean ok() { return code >= 200 && code < 300; }
    }

    public static class HttpException extends IOException {
        public final int code;

        public HttpException(int code, String msg) {
            super("HTTP " + code + (msg == null ? "" : ": " + msg));
            this.code = code;
        }
    }

    public static HttpURLConnection open(String url, Map<String, String> headers, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
        return c;
    }

    public static Resp get(String url, Map<String, String> headers, int timeoutMs) throws IOException {
        HttpURLConnection c = open(url, headers, timeoutMs);
        try {
            Resp r = new Resp();
            r.code = c.getResponseCode();
            r.contentType = c.getContentType();
            InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
                r.body = readAll(in);
            }
            return r;
        } finally {
            c.disconnect();
        }
    }

    public static String text(String url) throws IOException {
        Resp r = get(url, null, 20000);
        if (!r.ok()) throw new HttpException(r.code, null);
        return r.text();
    }

    public static byte[] bytes(String url, Map<String, String> headers, int timeoutMs) throws IOException {
        Resp r = get(url, headers, timeoutMs);
        if (!r.ok()) throw new HttpException(r.code, null);
        return r.body == null ? new byte[0] : r.body;
    }

    /** Content length via HEAD (falls back to a 1-byte range GET). -1 if unknown. */
    public static long contentLength(String url) {
        try {
            HttpURLConnection c = open(url, null, 10000);
            c.setRequestMethod("HEAD");
            int code = c.getResponseCode();
            long len = c.getContentLengthLong();
            c.disconnect();
            if (code >= 200 && code < 300 && len > 0) return len;
        } catch (IOException ignored) {}
        try {
            HttpURLConnection c = open(url, null, 10000);
            c.setRequestProperty("Range", "bytes=0-0");
            int code = c.getResponseCode();
            String cr = c.getHeaderField("Content-Range");
            c.disconnect();
            if (code == 206 && cr != null && cr.contains("/")) {
                return Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    public static String kickCookies() {
        try {
            return CookieManager.getInstance().getCookie("https://kick.com");
        } catch (Throwable t) {
            return null;
        }
    }
}
