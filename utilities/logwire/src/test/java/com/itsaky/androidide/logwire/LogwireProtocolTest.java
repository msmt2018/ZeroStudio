/*
 *  ZeroStudio IDE - utilities/logwire
 *
 *  Tests for the wire constants and the LogPayload encode/decode
 *  round-trip.
 */
package com.itsaky.androidide.logwire;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogwireProtocolTest {

    @Test
    public void magic_isLogw() {
        assertEquals(0x4C4F4757, WireConstants.MAGIC);
        // ASCII 'L'<<24 | 'O'<<16 | 'G'<<8 | 'W'
        assertEquals('L' << 24 | 'O' << 16 | 'G' << 8 | 'W', WireConstants.MAGIC);
    }

    @Test
    public void protocolVersion_isPositive() {
        assertTrue(WireConstants.PROTOCOL_VERSION > 0);
    }

    @Test
    public void intBE_roundTrips() {
        byte[] buf = new byte[4];
        WireConstants.writeIntBE(buf, 0, 0xCAFEBABE);
        assertEquals(0xCAFEBABE, WireConstants.readIntBE(buf, 0));

        WireConstants.writeIntBE(buf, 0, 0);
        assertEquals(0, WireConstants.readIntBE(buf, 0));

        WireConstants.writeIntBE(buf, 0, 1);
        assertEquals(1, WireConstants.readIntBE(buf, 0));

        WireConstants.writeIntBE(buf, 0, -1);
        assertEquals(-1, WireConstants.readIntBE(buf, 0));
    }

    @Test
    public void intBE_atOffset() {
        byte[] buf = new byte[8];
        WireConstants.writeIntBE(buf, 4, 0x12345678);
        // First 4 bytes untouched (zero).
        assertEquals(0, WireConstants.readIntBE(buf, 0));
        assertEquals(0x12345678, WireConstants.readIntBE(buf, 4));
    }

    @Test
    public void messageTypes_distinct() {
        byte[] types = {
                WireConstants.TYPE_LOG_PAYLOAD,
                WireConstants.TYPE_HANDSHAKE,
                WireConstants.TYPE_HEARTBEAT,
                WireConstants.TYPE_BYE,
                WireConstants.TYPE_ERROR
        };
        for (int i = 0; i < types.length; i++) {
            for (int j = i + 1; j < types.length; j++) {
                assertThat(types[i]).isNotEqualTo(types[j]);
            }
        }
    }

    @Test
    public void logLevels_matchAndroidLog() {
        // android.util.Log values: VERBOSE=2, DEBUG=3, INFO=4, WARN=5, ERROR=6, ASSERT=7
        assertEquals(2, WireConstants.LOG_VERBOSE);
        assertEquals(3, WireConstants.LOG_DEBUG);
        assertEquals(4, WireConstants.LOG_INFO);
        assertEquals(5, WireConstants.LOG_WARN);
        assertEquals(6, WireConstants.LOG_ERROR);
        assertEquals(7, WireConstants.LOG_ASSERT);
    }

    @Test
    public void logLevel_letters() {
        assertEquals('V', LogLevel.letter(LogLevel.VERBOSE));
        assertEquals('D', LogLevel.letter(LogLevel.DEBUG));
        assertEquals('I', LogLevel.letter(LogLevel.INFO));
        assertEquals('W', LogLevel.letter(LogLevel.WARN));
        assertEquals('E', LogLevel.letter(LogLevel.ERROR));
        assertEquals('A', LogLevel.letter(LogLevel.ASSERT));
        assertEquals('?', LogLevel.letter((byte) 99));
    }

    @Test
    public void logLevel_fromLetter() {
        assertEquals(LogLevel.VERBOSE, LogLevel.fromLetter('V'));
        assertEquals(LogLevel.VERBOSE, LogLevel.fromLetter('v'));
        assertEquals(LogLevel.DEBUG,   LogLevel.fromLetter('d'));
        assertEquals(LogLevel.INFO,    LogLevel.fromLetter('I'));
        assertEquals(LogLevel.WARN,    LogLevel.fromLetter('W'));
        assertEquals(LogLevel.ERROR,   LogLevel.fromLetter('e'));
        assertEquals(LogLevel.ASSERT,  LogLevel.fromLetter('A'));
        assertEquals(-1, LogLevel.fromLetter('X'));
    }

    @Test
    public void logPayload_roundTripsWithThrowble() {
        LogPayload in = new LogPayload(
                LogLevel.INFO, 1700000000123L,
                "main", "ZeroStudio", "hello", "stack trace...");
        byte[] data = in.write();
        LogPayload out = LogPayload.read(data);
        assertEquals(in.level, out.level);
        assertEquals(in.timestampMs, out.timestampMs);
        assertEquals(in.threadId, out.threadId);
        assertEquals(in.tag, out.tag);
        assertEquals(in.message, out.message);
        assertEquals(in.throwable, out.throwable);
    }

    @Test
    public void logPayload_roundTripsWithoutThrowable() {
        LogPayload in = new LogPayload(
                LogLevel.WARN, 0L,
                "worker-1", "Z", "short message", null);
        byte[] data = in.write();
        LogPayload out = LogPayload.read(data);
        assertEquals(LogLevel.WARN, out.level);
        assertEquals(0L, out.timestampMs);
        assertEquals("worker-1", out.threadId);
        assertEquals("Z", out.tag);
        assertEquals("short message", out.message);
        assertNull(out.throwable);
    }

    @Test
    public void logPayload_roundTripsEmptyStrings() {
        LogPayload in = new LogPayload(
                LogLevel.DEBUG, 1L, "", "", "", null);
        byte[] data = in.write();
        LogPayload out = LogPayload.read(data);
        assertEquals("", out.threadId);
        assertEquals("", out.tag);
        assertEquals("", out.message);
        assertNull(out.throwable);
    }

    @Test
    public void logPayload_unicodeRoundTrips() {
        LogPayload in = new LogPayload(
                LogLevel.ERROR, 1234L,
                "主线程", "零工作室", "错误信息💥", null);
        byte[] data = in.write();
        LogPayload out = LogPayload.read(data);
        assertEquals(in.threadId, out.threadId);
        assertEquals(in.tag, out.tag);
        assertEquals(in.message, out.message);
    }

    @Test
    public void logPayload_tooLargeFails() {
        String huge = new String(new char[WireConstants.MAX_PAYLOAD_SIZE]).replace('\0', 'x');
        LogPayload in = new LogPayload(
                LogLevel.INFO, 0L, "t", "tag", huge, null);
        try {
            in.write();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
