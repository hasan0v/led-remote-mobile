package az.ledremote.app;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String CUSTOM = "code2_";
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("led_remote", Context.MODE_PRIVATE);
    }

    boolean haptics() { return sp.getBoolean("haptics", true); }
    void setHaptics(boolean v) { sp.edit().putBoolean("haptics", v).apply(); }

    boolean holdRepeat() { return sp.getBoolean("hold_repeat", true); }
    void setHoldRepeat(boolean v) { sp.edit().putBoolean("hold_repeat", v).apply(); }

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
