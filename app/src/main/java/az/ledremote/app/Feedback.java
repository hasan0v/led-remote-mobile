package az.ledremote.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/** Key-press haptics and the synthesized click sound. */
final class Feedback {
    static final int VIB_OFF = 0, VIB_LIGHT = 1, VIB_MEDIUM = 2, VIB_STRONG = 3;

    private final Vibrator vibrator;
    private SoundPool pool;
    private int pressSound, releaseSound;
    private boolean soundReady;

    Feedback(Context c) {
        vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        try {
            pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override public void onLoadComplete(SoundPool p, int id, int status) { soundReady = status == 0; }
            });
            pressSound = pool.load(writeWav(c, "press.wav", 2400, 0.018, 0.9, 1), 1);
            releaseSound = pool.load(writeWav(c, "release.wav", 3400, 0.010, 0.45, 2), 1);
        } catch (IOException | RuntimeException e) {
            pool = null;
        }
    }

    boolean canVibrate() {
        return vibrator != null && vibrator.hasVibrator();
    }

    void press(int level, boolean sound) {
        if (sound) play(pressSound, 0.7f);
        vibrate(level, true);
    }

    void release(int level, boolean sound) {
        if (sound) play(releaseSound, 0.35f);
        if (level >= VIB_MEDIUM) vibrate(level, false);
    }

    private void play(int id, float vol) {
        if (pool != null && soundReady) pool.play(id, vol, vol, 1, 0, 1f);
    }

    private void vibrate(int level, boolean press) {
        if (level == VIB_OFF || !canVibrate()) return;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                int effect = !press ? VibrationEffect.EFFECT_TICK
                        : level == VIB_LIGHT ? VibrationEffect.EFFECT_TICK
                        : level == VIB_MEDIUM ? VibrationEffect.EFFECT_CLICK : VibrationEffect.EFFECT_HEAVY_CLICK;
                vibrator.vibrate(VibrationEffect.createPredefined(effect));
            } else if (Build.VERSION.SDK_INT >= 26) {
                int ms = press ? (level == VIB_LIGHT ? 8 : level == VIB_MEDIUM ? 14 : 22) : 6;
                int amp = press ? (level == VIB_LIGHT ? 70 : level == VIB_MEDIUM ? 160 : 255) : 60;
                vibrator.vibrate(VibrationEffect.createOneShot(ms, vibrator.hasAmplitudeControl() ? amp : VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(press ? (level == VIB_LIGHT ? 10 : level == VIB_MEDIUM ? 18 : 28) : 8);
            }
        } catch (RuntimeException ignored) {
            // vibration is best effort
        }
    }

    void destroy() {
        if (pool != null) pool.release();
        pool = null;
    }

    /** A short plastic "tick": noise transient plus a quickly decaying tone. */
    private static String writeWav(Context c, String name, double freq, double length, double noise, int seed) throws IOException {
        int rate = 44100, n = (int) (rate * length);
        byte[] pcm = new byte[n * 2];
        Random rnd = new Random(seed);
        for (int i = 0; i < n; i++) {
            double t = (double) i / rate;
            double env = Math.exp(-t / 0.0022);
            double s = Math.sin(2 * Math.PI * freq * t) * 0.55 + Math.sin(2 * Math.PI * freq * 2.7 * t) * 0.2;
            s += (rnd.nextDouble() * 2 - 1) * noise * Math.exp(-t / 0.0007);
            double attack = Math.min(1.0, i / 12.0);
            short v = (short) Math.max(-32767, Math.min(32767, s * env * attack * 0.6 * 32767));
            pcm[i * 2] = (byte) v;
            pcm[i * 2 + 1] = (byte) (v >> 8);
        }
        File f = new File(c.getCacheDir(), name);
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(header(pcm.length, rate));
            out.write(pcm);
        } finally {
            out.close();
        }
        return f.getAbsolutePath();
    }

    private static byte[] header(int dataLen, int rate) {
        byte[] h = new byte[44];
        put(h, 0, "RIFF"); le(h, 4, 36 + dataLen); put(h, 8, "WAVEfmt ");
        le(h, 16, 16); h[20] = 1; h[22] = 1; le(h, 24, rate); le(h, 28, rate * 2); h[32] = 2; h[34] = 16;
        put(h, 36, "data"); le(h, 40, dataLen);
        return h;
    }

    private static void put(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i);
    }

    private static void le(byte[] b, int off, int v) {
        for (int i = 0; i < 4; i++) b[off + i] = (byte) (v >> (8 * i));
    }
}
