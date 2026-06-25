/*
 *  ZeroStudio IDE - utilities/logwire
 *
 *  Phase C.1: tests for FrameCodec / Handshake / ErrorPayload.
 *  Round-trips the wire encoding and verifies the codec rejects
 *  bad magic / bad length.
 */
package com.itsaky.androidide.logwire;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FrameCodecTest {

    @Test
    public void frameRoundTrip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FrameCodec.writeFrame(out, WireConstants.TYPE_LOG_PAYLOAD,
                new byte[]{1, 2, 3, 4, 5});
        byte[] bytes = out.toByteArray();
        // 4 (magic) + 1 (type) + 4 (length) + 5 (payload) = 14
        assertEquals(14, bytes.length);
        FrameCodec.Frame f = FrameCodec.readFrame(new ByteArrayInputStream(bytes));
        assertEquals(WireConstants.TYPE_LOG_PAYLOAD, f.type);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, f.payload);
    }

    @Test
    public void frameEmptyPayload() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FrameCodec.writeFrame(out, WireConstants.TYPE_HEARTBEAT, new byte[0]);
        byte[] bytes = out.toByteArray();
        assertEquals(9, bytes.length);
        FrameCodec.Frame f = FrameCodec.readFrame(new ByteArrayInputStream(bytes));
        assertEquals(WireConstants.TYPE_HEARTBEAT, f.type);
        assertEquals(0, f.payload.length);
    }

    @Test
    public void frameRejectsBadMagic() {
        byte[] bad = new byte[]{
                'X', 'X', 'X', 'X',            // bad magic
                WireConstants.TYPE_LOG_PAYLOAD,
                0, 0, 0, 0
        };
        try {
            FrameCodec.readFrame(new ByteArrayInputStream(bad));
            fail("expected IOException for bad magic");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void frameRejectsBadLength() {
        byte[] bad = new byte[]{
                'L', 'O', 'G', 'W',                       // magic
                WireConstants.TYPE_LOG_PAYLOAD,
                0, 0, 0, (byte) 0xFF                      // length = -1
        };
        try {
            FrameCodec.readFrame(new ByteArrayInputStream(bad));
            fail("expected IOException for bad length");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void frameRejectsOversizedPayload() {
        try {
            FrameCodec.writeFrame(new ByteArrayOutputStream(),
                    WireConstants.TYPE_LOG_PAYLOAD,
                    new byte[WireConstants.MAX_PAYLOAD_SIZE + 1]);
            fail("expected IOException for too-large payload");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void drain_silentlyConsumesRest() throws IOException {
        // Drain should not throw on EOF
        FrameCodec.drain(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        // No exception means success
    }

    @Test
    public void handshakeRoundTrip() {
        Handshake in = new Handshake(
                WireConstants.PROTOCOL_VERSION,
                1234,
                "com.example.myapp",
                0x123456789ABCDEF0L);
        byte[] data = in.write();
        Handshake out = Handshake.read(data);
        assertEquals(in.protocolVersion, out.protocolVersion);
        assertEquals(in.pid, out.pid);
        assertEquals(in.packageName, out.packageName);
        assertEquals(in.sessionId, out.sessionId);
    }

    @Test
    public void handshakeDefaultFor() {
        Handshake h = Handshake.defaultFor("com.example");
        assertEquals(WireConstants.PROTOCOL_VERSION, h.protocolVersion);
        assertEquals("com.example", h.packageName);
        // pid may be 0 in test environment but must be set
        assertEquals(true, h.pid >= 0);
        assertEquals(true, h.sessionId > 0L);
    }

    @Test
    public void handshakeReadRejectsShortBuffer() {
        try {
            Handshake.read(new byte[]{0, 0, 0});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void errorPayloadRoundTrip() {
        ErrorPayload in = new ErrorPayload(ErrorPayload.CODE_OUT_OF_MEMORY, "OOM at line 42");
        byte[] data = in.write();
        ErrorPayload out = ErrorPayload.read(data);
        assertEquals(in.code, out.code);
        assertEquals(in.message, out.message);
    }

    @Test
    public void errorPayloadAllCodes() {
        int[] codes = {
                ErrorPayload.CODE_IO_ERROR,
                ErrorPayload.CODE_PROTOCOL_MISMATCH,
                ErrorPayload.CODE_INTERNAL,
                ErrorPayload.CODE_SHUTTING_DOWN,
                ErrorPayload.CODE_OUT_OF_MEMORY
        };
        for (int code : codes) {
            ErrorPayload in = new ErrorPayload(code, "code " + code);
            ErrorPayload out = ErrorPayload.read(in.write());
            assertEquals(code, out.code);
            assertEquals("code " + code, out.message);
        }
    }

    @Test
    public void errorPayloadReadRejectsShortBuffer() {
        try {
            ErrorPayload.read(new byte[]{0, 0, 0});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void errorPayloadUnicodeMessage() {
        ErrorPayload in = new ErrorPayload(
                ErrorPayload.CODE_INTERNAL, "出错了💥 main thread");
        ErrorPayload out = ErrorPayload.read(in.write());
        assertEquals("出错了💥 main thread", out.message);
    }

    @Test
    public void handshakeBackToBack() throws IOException {
        // Two handshakes back-to-back on the same stream
        Handshake h1 = Handshake.defaultFor("app1");
        Handshake h2 = Handshake.defaultFor("app2");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FrameCodec.writeFrame(out, WireConstants.TYPE_HANDSHAKE, h1.write());
        FrameCodec.writeFrame(out, WireConstants.TYPE_HANDSHAKE, h2.write());
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
        Handshake r1 = Handshake.read(FrameCodec.readFrame(in).payload);
        Handshake r2 = Handshake.read(FrameCodec.readFrame(in).payload);
        assertEquals("app1", r1.packageName);
        assertEquals("app2", r2.packageName);
    }
}
