package az.kickdl.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** User settings and small persisted state. */
public final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp;
    private static SharedPreferences pos;

    public static void init(Context c) {
        sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        pos = c.getSharedPreferences("positions", Context.MODE_PRIVATE);
    }

    public static SharedPreferences raw() { return sp; }

    // quality: "ask", "best", "1080", "720", "480", "360", "160", "audio"
    public static String quality() { return sp.getString("quality", "ask"); }
    public static void quality(String v) { sp.edit().putString("quality", v).apply(); }

    public static int concurrent() { return sp.getInt("concurrent", 2); }
    public static void concurrent(int v) { sp.edit().putInt("concurrent", v).apply(); }

    public static int connections() { return sp.getInt("connections", 6); }
    public static void connections(int v) { sp.edit().putInt("connections", v).apply(); }

    /** "mp4" = remux to MP4 (fallback TS on failure / huge files), "ts" = keep original stream. */
    public static String format() { return sp.getString("format", "mp4"); }
    public static void format(String v) { sp.edit().putString("format", v).apply(); }

    public static boolean gallery() { return sp.getBoolean("gallery", true); }
    public static void gallery(boolean v) { sp.edit().putBoolean("gallery", v).apply(); }

    public static boolean wifiOnly() { return sp.getBoolean("wifi_only", false); }
    public static void wifiOnly(boolean v) { sp.edit().putBoolean("wifi_only", v).apply(); }

    /** "internal", "external", "ask" */
    public static String player() { return sp.getString("player", "internal"); }
    public static void player(String v) { sp.edit().putString("player", v).apply(); }

    public static boolean animatedBg() { return sp.getBoolean("anim_bg", true); }
    public static void animatedBg(boolean v) { sp.edit().putBoolean("anim_bg", v).apply(); }

    public static boolean haptics() { return sp == null || sp.getBoolean("haptics", true); }
    public static void haptics(boolean v) { sp.edit().putBoolean("haptics", v).apply(); }

    public static boolean autoResume() { return sp.getBoolean("auto_resume", true); }
    public static void autoResume(boolean v) { sp.edit().putBoolean("auto_resume", v).apply(); }

    public static boolean notifyDone() { return sp.getBoolean("notify_done", true); }
    public static void notifyDone(boolean v) { sp.edit().putBoolean("notify_done", v).apply(); }

    public static int seekStep() { return sp.getInt("seek_step", 10); }
    public static void seekStep(int v) { sp.edit().putInt("seek_step", v).apply(); }

    public static boolean resumePlayback() { return sp.getBoolean("resume_play", true); }
    public static void resumePlayback(boolean v) { sp.edit().putBoolean("resume_play", v).apply(); }

    public static boolean autoRotate() { return sp.getBoolean("auto_rotate", true); }
    public static void autoRotate(boolean v) { sp.edit().putBoolean("auto_rotate", v).apply(); }

    public static boolean gestures() { return sp.getBoolean("gestures", true); }
    public static void gestures(boolean v) { sp.edit().putBoolean("gestures", v).apply(); }

    public static float defaultSpeed() { return sp.getFloat("speed", 1f); }
    public static void defaultSpeed(float v) { sp.edit().putFloat("speed", v).apply(); }

    public static String clipSort() { return sp.getString("clip_sort", "view"); }
    public static void clipSort(String v) { sp.edit().putString("clip_sort", v).apply(); }

    // ---------------------------------------------------------------- search history

    public static List<String> searches() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("searches", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    public static void addSearch(String q) {
        if (q == null) return;
        q = q.trim();
        if (q.isEmpty()) return;
        List<String> l = searches();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).equalsIgnoreCase(q)) l.remove(i);
        l.add(0, q);
        while (l.size() > 12) l.remove(l.size() - 1);
        sp.edit().putString("searches", new JSONArray(l).toString()).apply();
    }

    public static void removeSearch(String q) {
        List<String> l = searches();
        l.remove(q);
        sp.edit().putString("searches", new JSONArray(l).toString()).apply();
    }

    public static void clearSearches() { sp.edit().remove("searches").apply(); }

    // ---------------------------------------------------------------- playback positions

    public static long position(String key) { return pos.getLong(key, 0); }

    public static void position(String key, long ms) {
        if (ms <= 0) pos.edit().remove(key).apply();
        else pos.edit().putLong(key, ms).apply();
    }

    public static void clearPositions() { pos.edit().clear().apply(); }
}
