package az.ledremote.app;

import android.content.Context;
import android.widget.LinearLayout;

/** The 24 keys of the remote (6 rows x 4), shared by the app screen and the Quick Settings panel. */
final class Keypad {
    private static final int DARK = 0xFF1E1E1E;
    private static final int LIGHT = 0xFFF4F4F4;
    private static final int MODE = 0xFFF1F3F5;

    static final String[] NAMES = {
        "☀ +", "☀ −", "OFF", "ON", "R", "G", "B", "W",
        "Color 9", "Color 10", "Color 11", "FLASH", "Color 13", "Color 14", "Color 15", "STROBE",
        "Color 17", "Color 18", "Color 19", "FADE", "Color 21", "Color 22", "Color 23", "SMOOTH",
    };

    // label, kind, top, bottom, text color
    private static final Object[][] KEYS = {
        {"", RemoteButton.KIND_SUN_BIG, 0xFFFFFFFF, 0xFFC4C4C4, DARK},
        {"", RemoteButton.KIND_SUN_SMALL, 0xFFFFFFFF, 0xFFC4C4C4, DARK},
        {"OFF", RemoteButton.KIND_TEXT, 0xFF363636, 0xFF0A0A0A, LIGHT},
        {"ON", RemoteButton.KIND_TEXT, 0xFFEA6666, 0xFFAE2A2A, LIGHT},

        {"R", RemoteButton.KIND_TEXT, 0xFFE35656, 0xFFAE2B2B, LIGHT},
        {"G", RemoteButton.KIND_TEXT, 0xFF66B274, 0xFF387B48, LIGHT},
        {"B", RemoteButton.KIND_TEXT, 0xFF4A7BC2, 0xFF22467C, LIGHT},
        {"W", RemoteButton.KIND_TEXT, 0xFFFFFFFF, 0xFFC4C4C4, DARK},

        {"", RemoteButton.KIND_COLOR, 0xFFEFB47B, 0xFFC5864E, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF8AC88C, 0xFF5AA064, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF6DA8DC, 0xFF477BAF, 0},
        {"FLASH", RemoteButton.KIND_TEXT, 0xFF97A4B1, 0xFF566472, MODE},

        {"", RemoteButton.KIND_COLOR, 0xFFF09F6A, 0xFFBC652B, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF9ED7C6, 0xFF6EAA99, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF7964B2, 0xFF36286E, 0},
        {"STROBE", RemoteButton.KIND_TEXT, 0xFF97A4B1, 0xFF566472, MODE},

        {"", RemoteButton.KIND_COLOR, 0xFFF8D266, 0xFFD29D3B, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF57B1A7, 0xFF2B7E77, 0},
        {"", RemoteButton.KIND_COLOR, 0xFFA46CB2, 0xFF703D80, 0},
        {"FADE", RemoteButton.KIND_TEXT, 0xFF97A4B1, 0xFF566472, MODE},

        {"", RemoteButton.KIND_COLOR, 0xFFFCF872, 0xFFD9D141, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF559DAF, 0xFF2B6A7C, 0},
        {"", RemoteButton.KIND_COLOR, 0xFFDD99C3, 0xFFB46F99, 0},
        {"SMOOTH", RemoteButton.KIND_TEXT, 0xFF97A4B1, 0xFF566472, MODE},
    };

    private Keypad() {}

    static void addRows(Context c, LinearLayout parent, RemoteButton.Listener listener) {
        for (int row = 0; row < 6; row++) {
            LinearLayout line = new LinearLayout(c);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setClipChildren(false);
            for (int col = 0; col < 4; col++) {
                int i = row * 4 + col;
                Object[] s = KEYS[i];
                RemoteButton b = new RemoteButton(c, i, (Integer) s[1], (String) s[0],
                        (Integer) s[2], (Integer) s[3], (Integer) s[4]);
                b.setContentDescription(NAMES[i]);
                b.setListener(listener);
                line.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            parent.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
    }
}
