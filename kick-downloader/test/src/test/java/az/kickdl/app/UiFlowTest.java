package az.kickdl.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.MotionEvent;
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
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import az.kickdl.app.data.Follows;
import az.kickdl.app.data.Prefs;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.dl.DownloadTask;
import az.kickdl.app.net.KickApi;
import az.kickdl.app.screens.MediaCard;
import az.kickdl.app.ui.RingButton;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class UiFlowTest {
    MockKick mock;

    @Before public void setUp() throws Exception {
        Reset.all();
        mock = new MockKick();
        mock.start();
        KickApi.base = mock.root + "/api/";
        Prefs.gallery(false);
    }

    @After public void tearDown() { mock.stop(); }

    // ------------------------------------------------------------------ helpers

    static void pump(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    static void waitFor(String what, BooleanSupplier c, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            pump(50);
            if (c.getAsBoolean()) return;
            Thread.sleep(30);
        }
        fail("timeout waiting for " + what);
    }

    static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1233, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2673, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1233, 2673);
    }

    static List<View> all(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) all(((ViewGroup) v).getChildAt(i), out);
        return out;
    }

    static TextView findText(View root, String prefix) {
        for (View v : all(root, new ArrayList<>())) {
            if (v instanceof TextView && v.isShown() && ((TextView) v).getText().toString().startsWith(prefix)) return (TextView) v;
        }
        return null;
    }

    static <T> T find(View root, Class<T> cls) {
        for (View v : all(root, new ArrayList<>())) if (cls.isInstance(v) && v.isShown()) return cls.cast(v);
        return null;
    }

    static void clickUp(View v) {
        View x = v;
        while (x != null && !x.hasOnClickListeners()) x = (View) x.getParent();
        assertNotNull("no clickable ancestor", x);
        x.performClick();
    }

    // ------------------------------------------------------------------ the flow

    @Test public void fullFlow() throws Exception {
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = ac.get();
        View decor = a.getWindow().getDecorView();
        pump(500);
        layout(decor);
        assertNotNull("home title", findText(decor, a.getString(R.string.home_title)));

        // search
        EditText input = find(decor, EditText.class);
        assertNotNull(input);
        input.setText("str");
        waitFor("search results", () -> { layout(decor); return findText(decor, "streamfan") != null; }, 15000);
        TextView row = findText(decor, "StreamerOne");
        assertNotNull(row);
        System.out.println("SEARCH OK");

        // open channel
        clickUp(row);
        waitFor("vod cards", () -> { layout(decor); return find(decor, MediaCard.class) != null; }, 15000);
        System.out.println("TOUCHMODE " + decor.isInTouchMode());
        for (int k = 0; k < 20; k++) {
            for (View v : all(decor, new ArrayList<>())) if (v instanceof ListView && v.isShown()) ((ListView) v).scrollListBy(-400);
            layout(decor);
        }
        assertNotNull(findText(decor, "THE BEST"));
        assertNotNull(findText(decor, a.getString(R.string.watch_live)));
        System.out.println("CHANNEL OK");

        // follow
        clickUp(findText(decor, a.getString(R.string.follow)));
        pump(300);
        assertTrue(Follows.isFollowed("streamerone"));

        // download via quality sheet
        MediaCard card = find(decor, MediaCard.class);
        RingButton ring = find(card, RingButton.class);
        ring.performClick();
        waitFor("quality rows", () -> { layout(decor); return findText(decor, "720p60") != null; }, 15000);
        assertNotNull(findText(decor, "1080p60"));
        assertNotNull(findText(decor, a.getString(R.string.audio_only)));
        clickUp(findText(decor, "720p60"));
        pump(300);
        // trim toggle
        TextView trim = findText(decor, a.getString(R.string.trim));
        assertNotNull(trim);
        clickUp(trim);
        pump(400);
        layout(decor);
        TextView dl = null;
        for (View v : all(decor, new ArrayList<>()))
            if (v instanceof TextView && ((TextView) v).getText().toString().startsWith(a.getString(R.string.download) + "  ·")) dl = (TextView) v;
        assertNotNull("download button with size", dl);
        System.out.println("DL BUTTON: " + dl.getText());
        clickUp(dl);
        pump(500);

        waitFor("download done", () -> {
            for (DownloadTask t : DownloadManager.get().all())
                if (t.status == DownloadTask.DONE || t.status == DownloadTask.FAILED) return true;
            return false;
        }, 30000);
        DownloadTask t = DownloadManager.get().all().get(0);
        System.out.println("TASK status=" + t.status + " err=" + t.error + " bytes=" + t.bytesDone + " seg=" + t.segDone + "/" + t.segTotal
                + " out=" + t.outputUri + " name=" + t.outputName + " size=" + t.outputSize);
        assertEquals(DownloadTask.DONE, t.status);
        assertEquals(12, t.segTotal);

        // downloads tab
        a.switchTab(MainActivity.TAB_DOWNLOADS);
        pump(600);
        layout(decor);
        clickUp(findText(decor, a.getString(R.string.history)));
        pump(600);
        layout(decor);
        assertNotNull("history row", findText(decor, "Stream number"));
        System.out.println("DOWNLOADS OK");

        // following + settings
        a.switchTab(MainActivity.TAB_FOLLOWING);
        pump(800);
        layout(decor);
        assertNotNull(findText(decor, "StreamerOne"));
        a.switchTab(MainActivity.TAB_SETTINGS);
        pump(600);
        layout(decor);
        assertNotNull(findText(decor, a.getString(R.string.s_quality)));
        clickUp(findText(decor, a.getString(R.string.s_quality)));
        pump(500);
        a.onBackPressed();
        pump(500);
        a.switchTab(MainActivity.TAB_HOME);
        pump(600);
        input.setText("");
        pump(600);
        layout(decor);
        assertNotNull(findText(decor, a.getString(R.string.your_channels).toUpperCase()));
        assertNotNull(findText(decor, a.getString(R.string.recent_downloads).toUpperCase()));
        System.out.println("HOME IDLE OK");

        // shared link intent
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "look https://kick.com/streamerone");
        ac.newIntent(share);
        pump(800);
        ac.pause().stop().destroy();
        System.out.println("UI FLOW OK");
    }

    @Test public void player() throws Exception {
        File f = new File(System.getProperty("java.io.tmpdir"), "kdl-test.mp4");
        java.nio.file.Files.write(f.toPath(), new byte[1024]);
        Intent i = new Intent(Intent.ACTION_VIEW).setData(Uri.fromFile(f)).putExtra(PlayerActivity.EXTRA_TITLE, "Test video");
        ActivityController<PlayerActivity> ac = Robolectric.buildActivity(PlayerActivity.class, i).setup();
        PlayerActivity p = ac.get();
        View decor = p.getWindow().getDecorView();
        pump(500);
        layout(decor);
        assertNotNull(findText(decor, "Test video"));
        // tap, double tap, swipe on the gesture layer
        View touch = ((ViewGroup) ((ViewGroup) decor.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(2);
        long t0 = android.os.SystemClock.uptimeMillis();
        touch.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 1000, 1300, 0));
        touch.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 20, MotionEvent.ACTION_MOVE, 1000, 1000, 0));
        touch.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 40, MotionEvent.ACTION_MOVE, 1000, 800, 0));
        touch.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 60, MotionEvent.ACTION_UP, 1000, 800, 0));
        pump(800);
        for (String s : new String[]{"1×"}) { TextView v = findText(decor, s); if (v != null) { v.performClick(); pump(400); p.onBackPressed(); pump(400); } }
        ac.pause().stop().destroy();
        System.out.println("PLAYER OK");
    }
}
