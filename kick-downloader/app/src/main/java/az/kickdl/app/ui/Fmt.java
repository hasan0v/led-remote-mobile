package az.kickdl.app.ui;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import az.kickdl.app.R;

/** Human friendly formatting of sizes, durations, counts and dates. */
public final class Fmt {
    private Fmt() {}

    public static String duration(double seconds) {
        long s = Math.max(0, Math.round(seconds));
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%d:%02d", m, sec);
    }

    public static String durationMs(long ms) { return duration(ms / 1000.0); }

    public static String size(long bytes) {
        if (bytes < 0) return "—";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, mb < 10 ? "%.1f MB" : "%.0f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US, gb < 10 ? "%.2f GB" : "%.1f GB", gb);
    }

    public static String speed(double bytesPerSec) {
        if (bytesPerSec <= 0) return "—";
        return size((long) bytesPerSec) + "/s";
    }

    public static String count(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return trim(n / 1000.0) + "K";
        if (n < 1_000_000_000) return trim(n / 1_000_000.0) + "M";
        return trim(n / 1_000_000_000.0) + "B";
    }

    private static String trim(double v) {
        String s = v < 10 ? String.format(Locale.US, "%.1f", v) : String.format(Locale.US, "%.0f", v);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

    public static String eta(Context c, double seconds) {
        if (seconds <= 0 || Double.isInfinite(seconds) || Double.isNaN(seconds)) return "";
        long s = Math.round(seconds);
        if (s < 60) return c.getString(R.string.eta_sec, s);
        if (s < 3600) return c.getString(R.string.eta_min, s / 60);
        return c.getString(R.string.eta_hour, s / 3600, (s % 3600) / 60);
    }

    public static String ago(Context c, long timeMs) {
        if (timeMs <= 0) return "";
        long d = (System.currentTimeMillis() - timeMs) / 1000;
        if (d < 60) return c.getString(R.string.ago_now);
        if (d < 3600) return c.getString(R.string.ago_min, d / 60);
        if (d < 86400) return c.getString(R.string.ago_hour, d / 3600);
        if (d < 86400 * 30) return c.getString(R.string.ago_day, d / 86400);
        if (d < 86400 * 365) return c.getString(R.string.ago_month, d / (86400 * 30));
        return c.getString(R.string.ago_year, d / (86400 * 365));
    }

    public static String date(long timeMs) {
        if (timeMs <= 0) return "";
        return new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(timeMs));
    }

    /** Parses Kick timestamps: "2025-08-25 00:40:43" (UTC) or ISO-8601 with optional fraction/zone. */
    public static long parseTime(String s) {
        if (s == null || s.isEmpty() || "null".equals(s)) return 0;
        try {
            String t = s.trim().replace(' ', 'T');
            String zone = "Z";
            int zi = Math.max(t.lastIndexOf('+'), t.lastIndexOf('-'));
            if (t.endsWith("Z")) {
                t = t.substring(0, t.length() - 1);
            } else if (zi > 10) {
                zone = t.substring(zi);
                t = t.substring(0, zi);
            }
            int dot = t.indexOf('.');
            int millis = 0;
            if (dot > 0) {
                String frac = (t.substring(dot + 1) + "000").substring(0, 3);
                millis = Integer.parseInt(frac.replaceAll("[^0-9]", "0"));
                t = t.substring(0, dot);
            }
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            long base = f.parse(t).getTime() + millis;
            if (!"Z".equals(zone)) {
                int sign = zone.charAt(0) == '-' ? -1 : 1;
                String z = zone.substring(1).replace(":", "");
                int hh = Integer.parseInt(z.substring(0, 2));
                int mm = z.length() >= 4 ? Integer.parseInt(z.substring(2, 4)) : 0;
                base -= sign * (hh * 3600_000L + mm * 60_000L);
            }
            return base;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String percent(double f) {
        return Math.round(Math.max(0, Math.min(1, f)) * 100) + "%";
    }
}
