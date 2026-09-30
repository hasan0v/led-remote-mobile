package az.ledremote.app;

/** NEC 32-bit frames (address, ~address, command, ~command) of the common 24-key LED remotes. */
final class Codes {
    static final int COUNT = 24;

    static final int PRESET_B = 0;
    static final int PRESET_A = 1;

    /**
     * 24-key RGB LED remote: NEC commands 0x00..0x17 in row-major order, 6 rows x 4 columns,
     * exactly like the on-screen layout. Only the address differs between remote batches.
     */
    private static final int[] ADDRESS = {0x00FF, 0x00F7};   // NEC address 0x00 (most common) / 0xEF00

    private static int nec(int address, int command) {
        int cmd = Integer.reverse(command) >>> 24;             // LSB-first on air -> MSB-first value
        return (address << 16) | (cmd << 8) | (~cmd & 0xFF);
    }

    private Codes() {}

    static int preset(int preset, int index) {
        return nec(ADDRESS[preset], index);
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
