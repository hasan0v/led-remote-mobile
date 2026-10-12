package az.kickdl.app.dl;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.ByteBuffer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RemuxerTest {
    // AAC-LC, 48 kHz, stereo, no CRC
    static final byte[] ADTS_48K = {(byte) 0xFF, (byte) 0xF1, 0x4C, (byte) 0x80, 0x2E, 0x7F, (byte) 0xFC};

    @Test public void adtsToAsc() {
        assertArrayEquals(new byte[]{0x11, (byte) 0x90}, Remuxer.ascFromAdts(ADTS_48K));
        assertEquals(48000, Remuxer.rateFromAdts(ADTS_48K));
        assertEquals(2, Remuxer.channelsFromAdts(ADTS_48K));
        assertArrayEquals(new byte[]{0x11, (byte) 0x90}, Remuxer.ascFor(2, 48000, 2));
        assertArrayEquals(new byte[]{0x12, 0x10}, Remuxer.ascFor(2, 44100, 2));
    }

    @Test public void headerDetection() {
        ByteBuffer b = ByteBuffer.allocate(32);
        b.put(ADTS_48K);
        assertEquals(7, Remuxer.adtsHeaderLength(b, 20));
        ByteBuffer crc = ByteBuffer.allocate(32);
        crc.put(new byte[]{(byte) 0xFF, (byte) 0xF0, 0x4C, (byte) 0x80, 0, 0, 0, 0, 0});
        assertEquals(9, Remuxer.adtsHeaderLength(crc, 20));
        ByteBuffer raw = ByteBuffer.allocate(32);
        raw.put(new byte[]{0x21, 0x10, 0x05});
        assertEquals(0, Remuxer.adtsHeaderLength(raw, 20));
        assertEquals(0, Remuxer.adtsHeaderLength(b, 5));
        assertTrue(Remuxer.isAac("audio/mp4a-latm"));
        assertFalse(Remuxer.isAac("audio/mpeg"));
    }

    static byte[] adts(int payloadLen, boolean crc, int fill) {
        int header = crc ? 9 : 7, len = header + payloadLen;
        byte[] f = new byte[len];
        f[0] = (byte) 0xFF;
        f[1] = (byte) (crc ? 0xF0 : 0xF1);
        f[2] = 0x4C;
        f[3] = (byte) (0x80 | ((len >> 11) & 0x03));
        f[4] = (byte) ((len >> 3) & 0xFF);
        f[5] = (byte) (((len & 7) << 5) | 0x1F);
        f[6] = (byte) 0xFC;
        for (int i = header; i < len; i++) f[i] = (byte) fill;
        return f;
    }

    @Test public void splitsConcatenatedFrames() {
        byte[] a = adts(300, false, 1), b = adts(411, true, 2), c = adts(5, false, 3);
        ByteBuffer buf = ByteBuffer.allocateDirect(4096);
        buf.put(a).put(b).put(c);
        int size = a.length + b.length + c.length;
        int[] out = new int[64];
        assertEquals(3, Remuxer.splitAdts(buf, size, out));
        assertEquals(7, out[0]);
        assertEquals(300, out[1]);
        assertEquals(a.length + 9, out[2]);
        assertEquals(411, out[3]);
        assertEquals(a.length + b.length + 7, out[4]);
        assertEquals(5, out[5]);
        for (int i = out[2]; i < out[2] + out[3]; i++) assertEquals(2, buf.get(i));
    }

    @Test public void rawSampleIsOneFrame() {
        ByteBuffer buf = ByteBuffer.allocateDirect(64);
        buf.put(new byte[]{0x21, 0x10, 0x05, 0x00});
        int[] out = new int[8];
        assertEquals(1, Remuxer.splitAdts(buf, 40, out));
        assertEquals(0, out[0]);
        assertEquals(40, out[1]);
    }
}
