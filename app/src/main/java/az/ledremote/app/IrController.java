package az.ledremote.app;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * Encodes NEC frames and sends them through the phone's IR blaster.
 * transmit() blocks for the length of the frame (~70 ms), so it runs on its own thread
 * to keep the key animation smooth.
 */
final class IrController {
    private static final int CARRIER_HZ = 38000;
    private static final int UNIT = 560;
    private static final int[] REPEAT_FRAME = {9000, 2250, UNIT};

    private final ConsumerIrManager manager;
    private final HandlerThread thread = new HandlerThread("ir-tx");
    private final Handler tx;

    IrController(Context context) {
        manager = (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE);
        thread.start();
        tx = new Handler(thread.getLooper());
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

    void cancelPending() {
        tx.removeCallbacksAndMessages(null);
    }

    void shutdown() {
        cancelPending();
        thread.quitSafely();
    }

    private void transmit(final int[] pattern) {
        if (!isAvailable()) return;
        tx.post(new Runnable() {
            @Override public void run() {
                try {
                    manager.transmit(CARRIER_HZ, pattern);
                } catch (RuntimeException ignored) {
                    // Some devices throw when the emitter is busy; the next press simply retries.
                }
            }
        });
    }

    /**
     * Codes are written the way IR receivers (Arduino IRremote, LIRC dumps) print them:
     * the 32-bit value is the bit stream in transmission order, most significant bit first.
     * E.g. 00FF807F = address 0x00, command 0x01.
     */
    static int[] frame(int code) {
        int[] p = new int[2 + 64 + 1];
        int n = 0;
        p[n++] = 9000;
        p[n++] = 4500;
        for (int bit = 31; bit >= 0; bit--) {
            p[n++] = UNIT;
            p[n++] = ((code >>> bit) & 1) == 1 ? 1690 : UNIT;
        }
        p[n] = UNIT;
        return p;
    }
}
