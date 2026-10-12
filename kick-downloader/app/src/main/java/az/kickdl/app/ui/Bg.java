package az.kickdl.app.ui;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background work with results delivered on the main thread. */
public final class Bg {
    private Bg() {}

    public interface Job<T> { T run() throws Exception; }

    public interface Ok<T> { void ok(T value); }

    public interface Err { void err(Exception e); }

    private static final ExecutorService pool = Executors.newFixedThreadPool(6);
    public static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static <T> void run(final Job<T> job, final Ok<T> ok, final Err err) {
        pool.execute(() -> {
            try {
                final T v = job.run();
                MAIN.post(() -> { if (ok != null) ok.ok(v); });
            } catch (final Exception e) {
                MAIN.post(() -> { if (err != null) err.err(e); });
            }
        });
    }

    public static void exec(Runnable r) { pool.execute(r); }

    public static void main(Runnable r) { MAIN.post(r); }

    public static void later(Runnable r, long ms) { MAIN.postDelayed(r, ms); }
}
