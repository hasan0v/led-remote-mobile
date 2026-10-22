package az.ledremote.app;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;

/** What happens when a key goes down/up: IR frame, NEC repeat while held, haptics, sound, LED blink. */
final class KeySender {
    static final int KEY_OFF = 2;
    static final int KEY_ON = 3;
    private static final long REPEAT_DELAY_MS = 450;
    private static final long REPEAT_PERIOD_MS = 108;

    private final Context context;
    private final Prefs prefs;
    private final IrController ir;
    private final Feedback feedback;
    private final Runnable blink;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable repeater;

    KeySender(Context context, Prefs prefs, IrController ir, Feedback feedback, Runnable blink) {
        this.context = context;
        this.prefs = prefs;
        this.ir = ir;
        this.feedback = feedback;
        this.blink = blink;
    }

    /** @return false when the phone has no IR emitter (nothing was sent). */
    boolean down(int index) {
        feedback.press(prefs.vibration(), prefs.sound());
        if (!ir.isAvailable()) return false;
        ir.send(prefs.code(index));
        flash();
        if (index == KEY_ON || index == KEY_OFF) setPower(context, prefs, index == KEY_ON);
        if (prefs.holdRepeat() && index < 2) {
            stop();
            repeater = new Runnable() {
                @Override public void run() {
                    ir.repeat();
                    flash();
                    handler.postDelayed(this, REPEAT_PERIOD_MS);
                }
            };
            handler.postDelayed(repeater, REPEAT_DELAY_MS);
        }
        return true;
    }

    void up() {
        stop();
        feedback.release(prefs.vibration(), prefs.sound());
    }

    void stop() {
        if (repeater != null) {
            handler.removeCallbacks(repeater);
            repeater = null;
        }
    }

    private void flash() {
        if (prefs.indicator() && blink != null) blink.run();
    }

    /** Remembers the last ON/OFF sent and refreshes the power tile in the control center. */
    static void setPower(Context c, Prefs prefs, boolean on) {
        prefs.setPowerOn(on);
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                TileService.requestListeningState(c, new ComponentName(c, PowerTileService.class));
            } catch (RuntimeException ignored) {
                // tile not added; nothing to refresh
            }
        }
    }
}
