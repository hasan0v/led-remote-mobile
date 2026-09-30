package az.ledremote.app;

import android.content.Context;
import android.hardware.ConsumerIrManager;

/** Encodes NEC frames and sends them through the phone's IR blaster. */
final class IrController {
    private static final int CARRIER_HZ = 38000;
    private static final int UNIT = 560;
    private static final int[] REPEAT_FRAME = {9000, 2250, UNIT};

    private final ConsumerIrManager manager;

    IrController(Context context) {
        manager = (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE);
    }

    boolean isAvailable() {
        return manager != null && manager.hasIrEmitter();
    }

    void send(int code) {
        transmit(frame(code));
    }

    /** Short NEC "repeat" burst, sent every ~108 ms while a key is held. */
    void repeat() {
        transmit(REPEAT_FRAME);
    }

    private void transmit(int[] pattern) {
        if (!isAvailable()) return;
        try {
            manager.transmit(CARRIER_HZ, pattern);
        } catch (RuntimeException ignored) {
            // Some devices throw when the emitter is busy; the next press simply retries.
        }
    }

    static int[] frame(int code) {
        int[] p = new int[2 + 64 + 1];
        int n = 0;
        p[n++] = 9000;
        p[n++] = 4500;
        for (int byteIdx = 3; byteIdx >= 0; byteIdx--) {
            int b = (code >>> (byteIdx * 8)) & 0xFF;
            for (int bit = 0; bit < 8; bit++) {          // LSB first
                p[n++] = UNIT;
                p[n++] = ((b >> bit) & 1) == 1 ? 3 * UNIT : UNIT;
            }
        }
        p[n] = UNIT;
        return p;
    }
}
