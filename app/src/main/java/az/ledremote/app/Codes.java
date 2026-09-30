package az.ledremote.app;

/** NEC 32-bit frames (address, ~address, command, ~command) of the common 24-key LED remotes. */
final class Codes {
    static final int COUNT = 24;

    static final int PRESET_B = 0;
    static final int PRESET_A = 1;

    /** Row-major, 6 rows x 4 columns, exactly like the on-screen layout. */
    private static final int[][] PRESETS = {
        {   // Standard B
            0xFF00FF, 0xFF807F, 0xFF40BF, 0xFFC03F,
            0xFF20DF, 0xFFA05F, 0xFF609F, 0xFFE01F,
            0xFF10EF, 0xFF906F, 0xFF50AF, 0xFFD02F,
            0xFF30CF, 0xFFB04F, 0xFF708F, 0xFFF00F,
            0xFF08F7, 0xFF8877, 0xFF48B7, 0xFFC837,
            0xFF28D7, 0xFFA857, 0xFF6897, 0xFFE817,
        },
        {   // Standard A
            0xFF3AC5, 0xFFBA45, 0xFF827D, 0xFF02FD,
            0xFF1AE5, 0xFF9A65, 0xFFA25D, 0xFF22DD,
            0xFF2AD5, 0xFFAA55, 0xFF926D, 0xFF12ED,
            0xFF0AF5, 0xFF8A75, 0xFFB24D, 0xFF32CD,
            0xFF38C7, 0xFFB847, 0xFF7887, 0xFF28D7,
            0xFF18E7, 0xFF9867, 0xFF58A7, 0xFF08F7,
        },
    };

    private Codes() {}

    static int preset(int preset, int index) {
        return PRESETS[preset][index];
    }

    static String format(int code) {
        return (code >= 0 && code <= 0xFFFFFF) ? String.format("%06X", code) : String.format("%08X", code);
    }

    /** Parses 1-8 hex digits, returns -1 (as Long) when invalid. */
    static long parse(String text) {
        String s = text.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.isEmpty() || s.length() > 8) return -1;
        try {
            return Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
