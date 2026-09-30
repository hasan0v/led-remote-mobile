package az.ledremote.app;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final int SIZE_COMPACT = 0, SIZE_NORMAL = 1, SIZE_LARGE = 2;

    private static final String CUSTOM = "code2_";
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("led_remote", Context.MODE_PRIVATE);
    }

    /** Feedback.VIB_* ; migrates the old on/off switch. */
    int vibration() { return sp.getInt("vibration", sp.getBoolean("haptics", true) ? Feedback.VIB_MEDIUM : Feedback.VIB_OFF); }
    void setVibration(int v) { sp.edit().putInt("vibration", v).apply(); }

    boolean sound() { return sp.getBoolean("sound", true); }
    void setSound(boolean v) { sp.edit().putBoolean("sound", v).apply(); }

    boolean indicator() { return sp.getBoolean("indicator", true); }
    void setIndicator(boolean v) { sp.edit().putBoolean("indicator", v).apply(); }

    boolean holdRepeat() { return sp.getBoolean("hold_repeat", true); }
    void setHoldRepeat(boolean v) { sp.edit().putBoolean("hold_repeat", v).apply(); }

    boolean showCode() { return sp.getBoolean("show_code", false); }
    void setShowCode(boolean v) { sp.edit().putBoolean("show_code", v).apply(); }

    boolean keepScreenOn() { return sp.getBoolean("keep_screen_on", false); }
    void setKeepScreenOn(boolean v) { sp.edit().putBoolean("keep_screen_on", v).apply(); }

    boolean fullscreen() { return sp.getBoolean("fullscreen", false); }
    void setFullscreen(boolean v) { sp.edit().putBoolean("fullscreen", v).apply(); }

    int size() { return sp.getInt("size", SIZE_NORMAL); }
    void setSize(int v) { sp.edit().putInt("size", v).apply(); }

    int preset() { return sp.getInt("preset", Codes.PRESET_B); }
    void setPreset(int v) { sp.edit().putInt("preset", v).apply(); }

    int code(int index) {
        return sp.getInt(CUSTOM + preset() + "_" + index, Codes.preset(preset(), index));
    }

    boolean isCustom(int index) {
        return sp.contains(CUSTOM + preset() + "_" + index);
    }

    void setCode(int index, int code) { sp.edit().putInt(CUSTOM + preset() + "_" + index, code).apply(); }
    void clearCode(int index) { sp.edit().remove(CUSTOM + preset() + "_" + index).apply(); }

    void clearAllCodes() {
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet()) if (k.startsWith(CUSTOM)) e.remove(k);
        e.apply();
    }
}
