package az.kickdl.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import az.kickdl.app.data.Media;
import az.kickdl.app.data.Prefs;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.dl.DownloadTask;
import az.kickdl.app.net.KickApi;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@LooperMode(LooperMode.Mode.PAUSED)
public class DownloadTest {
    MockKick mock;

    @Before public void setUp() throws Exception {
        Reset.all();
        mock = new MockKick();
        mock.start();
        KickApi.base = mock.root + "/api/";
        Prefs.gallery(false);
    }

    @After public void tearDown() { mock.stop(); }

    static void waitFor(String what, BooleanSupplier c, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
            if (c.getAsBoolean()) return;
            Thread.sleep(30);
        }
        fail("timeout waiting for " + what);
    }

    DownloadTask task(String url) {
        DownloadTask t = new DownloadTask();
        t.title = "t";
        t.channelName = "ch";
        t.url = url;
        t.quality = "720p60";
        t.durationSec = 120;
        return t;
    }

    @Test public void trimDownloadsOnlyPart() throws Exception {
        DownloadTask t = task(mock.root + "/vod/master.m3u8");
        t.trimStart = 25;
        t.trimEnd = 61;
        DownloadManager.get().enqueue(t);
        waitFor("done", () -> t.status == DownloadTask.DONE || t.status == DownloadTask.FAILED, 20000);
        assertEquals(t.error, DownloadTask.DONE, t.status);
        // segments [20-30),[30-40),[40-50),[50-60),[60-70) overlap 25..61
        assertEquals(5, t.segTotal);
        assertTrue(t.outputName.contains("cut"));
        System.out.println("TRIM OK " + t.outputName);
    }

    @Test public void pauseAndResume() throws Exception {
        mock.slow = true;
        Prefs.connections(1);
        DownloadTask t = task(mock.root + "/vod/master.m3u8");
        DownloadManager.get().enqueue(t);
        waitFor("some progress", () -> t.segDone >= 3, 20000);
        DownloadManager.get().pause(t.id);
        waitFor("paused", () -> t.status == DownloadTask.PAUSED, 10000);
        int done = t.segDone;
        long bytes = t.bytesDone;
        assertTrue(done > 0 && done < 12);
        assertEquals(new java.io.File(t.tempPath).length(), bytes);
        int hitsAtPause = mock.segHits.get();
        DownloadManager.get().resume(t.id);
        waitFor("done", () -> t.status == DownloadTask.DONE || t.status == DownloadTask.FAILED, 30000);
        assertEquals(t.error, DownloadTask.DONE, t.status);
        int fetchedAfter = mock.segHits.get() - hitsAtPause;
        System.out.println("PAUSE/RESUME OK paused at " + done + " fetched after resume=" + fetchedAfter + " size=" + t.outputSize);
        assertTrue("resume must not refetch everything", fetchedAfter <= 12 - done + 2);
        assertEquals(t.bytesDone, t.outputSize);
        Prefs.connections(6);
    }

    @Test public void cancelRemovesTask() throws Exception {
        mock.slow = true;
        DownloadTask t = task(mock.root + "/vod/master.m3u8");
        DownloadManager.get().enqueue(t);
        waitFor("running", () -> t.segDone >= 1, 20000);
        DownloadManager.get().cancel(t.id);
        waitFor("gone", () -> DownloadManager.get().find(t.id) == null, 10000);
        assertFalse(new java.io.File(t.tempPath).exists());
        System.out.println("CANCEL OK");
    }

    @Test public void liveRecording() throws Exception {
        DownloadTask t = task(mock.root + "/live/master.m3u8");
        t.live = true;
        t.kind = Media.LIVE;
        t.quality = "";
        DownloadManager.get().enqueue(t);
        waitFor("recording", () -> t.segDone >= 6, 20000);
        assertEquals(DownloadTask.RUNNING, t.status);
        DownloadManager.get().stopRecording(t.id);
        waitFor("done", () -> t.status == DownloadTask.DONE || t.status == DownloadTask.FAILED, 20000);
        assertEquals(t.error, DownloadTask.DONE, t.status);
        System.out.println("LIVE OK segs=" + t.segDone + " recorded=" + t.recordedSec + "s q=" + t.quality + " name=" + t.outputName);
        assertTrue(t.outputName.contains("live"));
    }

    @Test public void directClip() throws Exception {
        DownloadTask t = task(mock.root + "/c/video.mp4");
        t.direct = true;
        t.kind = Media.CLIP;
        DownloadManager.get().enqueue(t);
        waitFor("done", () -> t.status == DownloadTask.DONE || t.status == DownloadTask.FAILED, 20000);
        assertEquals(t.error, DownloadTask.DONE, t.status);
        assertEquals(300_000, t.outputSize);
        assertTrue(t.outputName.endsWith(".mp4"));
        System.out.println("DIRECT OK " + t.outputName);
    }

    @Test public void links() {
        KickApi.Link l = KickApi.parseLink("watch this https://kick.com/streamerone/videos/5c697a87-afce-4256-b01f-3c8fe71ef5cb now");
        assertEquals(1, l.type);
        assertEquals("streamerone", l.channel);
        assertEquals("5c697a87-afce-4256-b01f-3c8fe71ef5cb", l.id);
        l = KickApi.parseLink("https://kick.com/mxddy?clip=clip_01GYXVB5Y8PWAPWCWMSBCFB05X");
        assertEquals(2, l.type);
        assertEquals("clip_01GYXVB5Y8PWAPWCWMSBCFB05X", l.id);
        l = KickApi.parseLink("https://kick.com/spreen/clips/clip_01J8RGZRKHXHXXKJEHGRM932A5");
        assertEquals(2, l.type);
        l = KickApi.parseLink("kick.com/Buddha");
        assertEquals(0, l.type);
        assertEquals("Buddha", l.channel);
        assertNull(KickApi.parseLink("hello"));
        assertNull(KickApi.parseLink("https://kick.com/categories/just-chatting"));
        assertEquals("some-user", KickApi.slugify("Some_User"));
    }

    @Test public void apiParsing() throws Exception {
        az.kickdl.app.data.Channel c = KickApi.channel("streamerone");
        assertEquals("StreamerOne", c.name);
        assertEquals(812345, c.followers);
        assertTrue(c.live);
        assertTrue(c.verified);
        assertEquals("Just Chatting", c.liveCategory);
        java.util.List<Media> v = KickApi.videos(c);
        assertEquals(6, v.size());
        assertEquals(3600.0, v.get(0).durationSec, 0.01);
        assertEquals("uuid-0", v.get(0).id);
        assertTrue(v.get(0).createdAt > 0);
        KickApi.ClipPage p = KickApi.clips(c, "view", "all", null);
        assertEquals(1, p.items.size());
        assertNull(p.nextCursor);
        assertEquals(43, p.items.get(0).durationSec, 0.01);
        java.util.List<az.kickdl.app.data.Channel> s = KickApi.search("str");
        assertEquals(2, s.size());
        assertTrue(s.get(0).live);
        assertEquals(812345, s.get(0).followers);
        try { KickApi.channel("nobody"); fail(); } catch (KickApi.NotFoundException expected) {}
        System.out.println("API OK");
    }
}
