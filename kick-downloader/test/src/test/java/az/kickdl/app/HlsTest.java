package az.kickdl.app;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import az.kickdl.app.hls.Hls;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HlsTest {
    static final String MASTER = "#EXTM3U\n"
            + "#EXT-X-SESSION-DATA:DATA-ID=\"net.live-video.content.id\",VALUE=\"x\"\n"
            + "#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID=\"1080p60\",NAME=\"1080p60\",AUTOSELECT=YES,DEFAULT=YES\n"
            + "#EXT-X-STREAM-INF:BANDWIDTH=8500000,CODECS=\"avc1.64002A,mp4a.40.2\",RESOLUTION=1920x1080,FRAME-RATE=60.000,VIDEO=\"1080p60\"\n"
            + "1080p60/playlist.m3u8\n"
            + "#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID=\"720p60\",NAME=\"720p60\",AUTOSELECT=YES,DEFAULT=YES\n"
            + "#EXT-X-STREAM-INF:BANDWIDTH=3500000,CODECS=\"avc1.4D401F,mp4a.40.2\",RESOLUTION=1280x720,FRAME-RATE=60.000,VIDEO=\"720p60\"\n"
            + "720p60/playlist.m3u8\n"
            + "#EXT-X-STREAM-INF:BANDWIDTH=630000,RESOLUTION=640x360,FRAME-RATE=30.000,VIDEO=\"360p30\"\n"
            + "https://cdn.example.com/abs/360p30/playlist.m3u8?token=1\n";

    @Test public void master() {
        List<Hls.Variant> v = Hls.parseMaster(MASTER, "https://stream.kick.com/ivs/v1/abc/media/hls/master.m3u8");
        assertEquals(3, v.size());
        assertEquals("1080p60", v.get(0).label());
        assertEquals(1920, v.get(0).width);
        assertEquals("https://stream.kick.com/ivs/v1/abc/media/hls/1080p60/playlist.m3u8", v.get(0).url);
        assertEquals("360p30", v.get(2).label());
        assertEquals("https://cdn.example.com/abs/360p30/playlist.m3u8?token=1", v.get(2).url);
    }

    @Test public void media() {
        String m = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:5\n"
                + "#EXTINF:10.000,\n0.ts\n#EXTINF:10.000,\n1.ts\n#EXT-X-DISCONTINUITY\n#EXTINF:4.5,\n2.ts\n#EXT-X-ENDLIST\n";
        Hls.MediaPlaylist pl = Hls.parseMedia(m, "https://h/x/720p60/playlist.m3u8");
        assertEquals(3, pl.segments.size());
        assertTrue(pl.endList);
        assertEquals(24.5, pl.totalDuration, 0.001);
        assertEquals("https://h/x/720p60/1.ts", pl.segments.get(1).url);
        assertEquals(20.0, pl.segments.get(2).start, 0.001);
        assertEquals(7, pl.segments.get(2).sequence);
        assertTrue(pl.segments.get(2).discontinuity);
        assertFalse(pl.isFragmentedMp4());
    }

    @Test public void fmp4AndKeys() {
        String m = "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXT-X-KEY:METHOD=AES-128,URI=\"k.key\",IV=0x000102030405060708090a0b0c0d0e0f\n"
                + "#EXTINF:2,\n#EXT-X-BYTERANGE:1000@0\nseg.m4s\n#EXTINF:2,\n#EXT-X-BYTERANGE:500\nseg.m4s\n";
        Hls.MediaPlaylist pl = Hls.parseMedia(m, "https://h/a/b.m3u8");
        assertEquals("https://h/a/init.mp4", pl.initUrl);
        assertTrue(pl.encrypted());
        assertEquals(15, pl.segments.get(0).key.iv[15]);
        assertEquals(1000, pl.segments.get(1).byteOffset);
        assertEquals(500, pl.segments.get(1).byteLength);
    }

    @Test public void noMaster() {
        List<Hls.Variant> v = Hls.parseMaster("#EXTM3U\n#EXTINF:1,\na.ts\n", "https://h/p.m3u8");
        assertEquals(1, v.size());
        assertEquals("https://h/p.m3u8", v.get(0).url);
    }
}
