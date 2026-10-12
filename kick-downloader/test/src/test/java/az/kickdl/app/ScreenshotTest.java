package az.kickdl.app;

import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import az.kickdl.app.data.Prefs;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.dl.DownloadTask;
import az.kickdl.app.net.KickApi;
import az.kickdl.app.screens.MediaCard;
import az.kickdl.app.ui.RingButton;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "az-w411dp-h891dp-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotTest {
    static final String OUT = System.getProperty("shots.dir", "build/shots");
    MockKick mock;

    @Before public void setUp() throws Exception {
        Reset.all();
        mock = new MockKick();
        mock.start();
        KickApi.base = mock.root + "/api/";
        Prefs.gallery(false);
        new File(OUT).mkdirs();
    }

    @After public void tearDown() { mock.stop(); }

    static void pump(long ms) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }

    static void waitFor(BooleanSupplier c, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            pump(50);
            if (c.getAsBoolean()) return;
            Thread.sleep(30);
        }
        throw new AssertionError("timeout");
    }

    static final int W = 1233, H = 2673;

    static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);
    }

    static void shot(View decor, String name) throws Exception {
        pump(1200);
        layout(decor);
        pump(400);
        layout(decor);
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(b));
        Bitmap small = Bitmap.createScaledBitmap(b, W / 2, H / 2, true);
        try (FileOutputStream os = new FileOutputStream(new File(OUT, name + ".png"))) {
            small.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        System.out.println("SHOT " + name);
    }

    static List<View> all(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) all(((ViewGroup) v).getChildAt(i), out);
        return out;
    }

    static TextView findText(View root, String prefix) {
        for (View v : all(root, new ArrayList<>()))
            if (v instanceof TextView && v.isShown() && ((TextView) v).getText().toString().startsWith(prefix)) return (TextView) v;
        return null;
    }

    static <T> T find(View root, Class<T> cls) {
        for (View v : all(root, new ArrayList<>())) if (cls.isInstance(v) && v.isShown()) return cls.cast(v);
        return null;
    }

    static void clickUp(View v) {
        View x = v;
        while (x != null && !x.hasOnClickListeners()) x = (View) x.getParent();
        x.performClick();
    }

    static void scrollTop(View decor) {
        for (int k = 0; k < 20; k++) {
            for (View v : all(decor, new ArrayList<>())) if (v instanceof ListView && v.isShown()) ((ListView) v).scrollListBy(-400);
            layout(decor);
        }
    }

    @Test public void screens() throws Exception {
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = ac.get();
        View decor = a.getWindow().getDecorView();
        pump(500);
        layout(decor);
        shot(decor, "01_home_empty");

        EditText input = find(decor, EditText.class);
        input.setText("str");
        waitFor(() -> { layout(decor); return findText(decor, "streamfan") != null; }, 15000);
        shot(decor, "02_search");

        clickUp(findText(decor, "StreamerOne"));
        waitFor(() -> { layout(decor); return find(decor, MediaCard.class) != null; }, 15000);
        scrollTop(decor);
        shot(decor, "03_channel");
        clickUp(findText(decor, a.getString(R.string.follow)));
        pump(400);
        for (View v : all(decor, new ArrayList<>())) if (v instanceof ListView && v.isShown()) ((ListView) v).scrollListBy(1500);
        layout(decor);
        shot(decor, "04_channel_list");

        mock.slow = true;
        mock.slowMs = 900;
        Prefs.connections(1);
        RingButton ring = find(find(decor, MediaCard.class), RingButton.class);
        ring.performClick();
        waitFor(() -> { layout(decor); return findText(decor, "720p60") != null; }, 15000);
        pump(1500);
        clickUp(findText(decor, a.getString(R.string.trim)));
        shot(decor, "05_quality_sheet");
        TextView dl = null;
        for (View v : all(decor, new ArrayList<>()))
            if (v instanceof TextView && ((TextView) v).getText().toString().startsWith(a.getString(R.string.download) + "  ·")) dl = (TextView) v;
        clickUp(dl);
        pump(300);
        waitFor(() -> { for (DownloadTask t : DownloadManager.get().all()) if (t.segDone >= 2) return true; return false; }, 30000);
        layout(decor);
        shot(decor, "06_channel_downloading");

        a.switchTab(MainActivity.TAB_DOWNLOADS);
        pump(300);
        shot(decor, "07_downloads_active");
        mock.slowMs = 10;
        waitFor(() -> DownloadManager.get().all().get(0).status == DownloadTask.DONE, 60000);
        clickUp(findText(decor, a.getString(R.string.history)));
        shot(decor, "08_downloads_history");

        a.switchTab(MainActivity.TAB_FOLLOWING);
        shot(decor, "09_following");
        a.switchTab(MainActivity.TAB_SETTINGS);
        shot(decor, "10_settings");
        a.switchTab(MainActivity.TAB_HOME);
        pump(300);
        input.setText("");
        shot(decor, "11_home_idle");
        ac.pause().stop().destroy();
    }

    @Test public void icon() throws Exception {
        android.graphics.drawable.Drawable d = org.robolectric.RuntimeEnvironment.getApplication().getDrawable(R.mipmap.ic_launcher);
        Bitmap b = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(new android.graphics.RectF(0, 0, 432, 432), 100, 100, android.graphics.Path.Direction.CW);
        c.clipPath(clip);
        d.setBounds(0, 0, 432, 432);
        d.draw(c);
        try (FileOutputStream os = new FileOutputStream(new File(OUT, "00_icon.png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, os); }
    }

    @Test public void player() throws Exception {
        File f = new File(System.getProperty("java.io.tmpdir"), "kdl-shot.mp4");
        java.nio.file.Files.write(f.toPath(), new byte[1024]);
        Intent i = new Intent(Intent.ACTION_VIEW).setData(Uri.fromFile(f)).putExtra(PlayerActivity.EXTRA_TITLE, "StreamerOne - Stream number 5 with a pretty long title");
        ActivityController<PlayerActivity> ac = Robolectric.buildActivity(PlayerActivity.class, i).setup();
        View decor = ac.get().getWindow().getDecorView();
        View err = null;
        pump(600);
        layout(decor);
        // hide the error box to see the bare controls
        for (View v : all(decor, new ArrayList<>())) if (v instanceof TextView && ((TextView) v).getText().toString().startsWith("Playback failed")) err = (View) v.getParent();
        if (err != null) err.setVisibility(View.GONE);
        shot(decor, "12_player");
        ac.pause().stop().destroy();
    }
}
