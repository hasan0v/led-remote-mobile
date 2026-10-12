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
}
