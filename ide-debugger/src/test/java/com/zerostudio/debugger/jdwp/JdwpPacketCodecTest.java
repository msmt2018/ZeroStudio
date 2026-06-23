/*
 *  ZeroStudio IDE - JdwpPacketCodec 单元测试 (Phase F1)
 *
 *  覆盖:
 *    - encode / decode 一对往返 (round-trip)
 *    - 头字段 4 字节 length / 4 字节 id / 1 字节 flags /
 *      1 字节 commandSet / 1 字节 command
 *    - 长度计算正确 (length = HEADER_SIZE - 4 + data.length)
 *    - 空 data 也能正常编码/解码
 *    - 过大长度 (>= 16 MiB) 触发 IOException
 *    - 过短长度 (< 7) 触发 IOException
 *    - EOF 在 length 字段处触发 IOException
 *    - writeTo 写入 DataOutputStream 后通过 decode 还原
 *    - JdwpPacket.isReply / isError / errorCode 行为
 */

package com.zerostudio.debugger.jdwp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JdwpPacketCodecTest {

    @Test
    public void encodeCommandHeaderIs11Bytes() {
        JdwpPacket p = new JdwpPacket.Builder()
                .id(0x01020304)
                .commandSet((byte) 0x40)
                .command((byte) 0x01)
                .data(new byte[]{1, 2, 3, 4, 5})
                .build();
        byte[] encoded = JdwpPacketCodec.encode(p);
        // 4 字节 length + 4 字节 id + 1 字节 flags + 1 字节 cs + 1 字节 cmd + 5 字节 data
        assertEquals(11 + 5, encoded.length);
        // length = HEADER_SIZE - 4 + 5 = 11 - 4 + 5 = 12
        int length = ((encoded[0] & 0xff) << 24) | ((encoded[1] & 0xff) << 16)
                | ((encoded[2] & 0xff) << 8) | (encoded[3] & 0xff);
        assertEquals(12, length);
    }

    @Test
    public void encodeDecodeRoundTrip() throws IOException {
        byte[] data = new byte[]{0x10, 0x20, 0x30, (byte) 0xff, 0x42};
        JdwpPacket p = new JdwpPacket.Builder()
                .id(0xdeadbeef)
                .commandSet((byte) 0x40)
                .command((byte) 0x09)
                .data(data)
                .build();
        byte[] encoded = JdwpPacketCodec.encode(p);
        JdwpPacket decoded = JdwpPacketCodec.decode(
                new DataInputStream(new ByteArrayInputStream(encoded)));
        assertEquals(p.id, decoded.id);
        assertEquals(p.flags, decoded.flags);
        assertEquals(p.commandSet, decoded.commandSet);
        assertEquals(p.command, decoded.command);
        assertArrayEquals(data, decoded.data);
    }

    @Test
    public void emptyDataRoundTrip() throws IOException {
        JdwpPacket p = new JdwpPacket.Builder()
                .id(1)
                .commandSet((byte) 1)
                .command((byte) 1)
                .data(new byte[0])
                .build();
        byte[] encoded = JdwpPacketCodec.encode(p);
        JdwpPacket decoded = JdwpPacketCodec.decode(
                new DataInputStream(new ByteArrayInputStream(encoded)));
        assertEquals(0, decoded.data.length);
        assertEquals(1, decoded.id);
    }

    @Test
    public void encodeReplyFlagBit() {
        JdwpPacket p = new JdwpPacket(42, JdwpPacket.FLAG_REPLY, (byte) 0, (byte) 0,
                new byte[]{0, 0});
        byte[] encoded = JdwpPacketCodec.encode(p);
        // flags 是第 5 个字节 (0-indexed: 4)
        assertEquals((byte) 0x80, encoded[8]);
        assertTrue(p.isReply());
        assertFalse(p.isError());
        assertEquals(0, p.errorCode());
    }

    @Test
    public void replyWithErrorCodeReportsError() {
        // 错误码 = 0x1234 (大端)
        byte[] data = new byte[]{0x12, 0x34};
        JdwpPacket p = new JdwpPacket(7, JdwpPacket.FLAG_REPLY,
                (byte) 0x40, (byte) 0x01, data);
        assertTrue(p.isReply());
        assertTrue(p.isError());
        assertEquals(0x1234, p.errorCode());
    }

    @Test
    public void commandPacketIsNotReply() {
        JdwpPacket p = new JdwpPacket.Builder()
                .id(1)
                .commandSet((byte) 0x40)
                .command((byte) 0x09)
                .data(new byte[0])
                .build();
        assertFalse(p.isReply());
        assertFalse(p.isError());
        assertEquals(0, p.errorCode());
    }

    @Test
    public void shortReplyIsNotError() {
        JdwpPacket p = new JdwpPacket(1, JdwpPacket.FLAG_REPLY,
                (byte) 0, (byte) 0, new byte[]{0x00});
        // 长度 < 2 时 errorCode() 返回 0
        assertFalse(p.isError());
        assertEquals(0, p.errorCode());
    }

    @Test
    public void decodeRejectsTooShortLength() {
        // length 字段为 6,小于最小 7
        byte[] header = new byte[]{0, 0, 0, 6};
        try {
            JdwpPacketCodec.decode(new DataInputStream(new ByteArrayInputStream(header)));
            fail("Expected IOException for too-short length");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Packet too short"));
        }
    }

    @Test
    public void decodeRejectsTooLongLength() {
        // length 字段为 16 * 1024 * 1024 + 1 = 16777217
        byte[] header = new byte[]{(byte) 0x01, 0x00, 0x00, 0x01};
        try {
            JdwpPacketCodec.decode(new DataInputStream(new ByteArrayInputStream(header)));
            fail("Expected IOException for too-long length");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Packet too large"));
        }
    }

    @Test
    public void decodeThrowsOnEofAtLength() {
        byte[] empty = new byte[0];
        try {
            JdwpPacketCodec.decode(new DataInputStream(new ByteArrayInputStream(empty)));
            fail("Expected IOException for EOF at length");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("EOF"));
        }
    }

    @Test
    public void writeToOutputsEncodedBytes() throws IOException {
        JdwpPacket p = new JdwpPacket.Builder()
                .id(99)
                .commandSet((byte) 0x09)
                .command((byte) 0x02)
                .data(new byte[]{1, 2, 3})
                .build();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        JdwpPacketCodec.writeTo(dos, p);
        byte[] raw = baos.toByteArray();
        assertEquals(JdwpPacket.HEADER_SIZE + 3, raw.length);
        // decode again
        JdwpPacket decoded = JdwpPacketCodec.decode(
                new DataInputStream(new ByteArrayInputStream(raw)));
        assertEquals(99, decoded.id);
        assertEquals(0x09, decoded.commandSet & 0xff);
        assertEquals(0x02, decoded.command & 0xff);
        assertArrayEquals(new byte[]{1, 2, 3}, decoded.data);
    }

    @Test
    public void encodeDecodesBoundaryLength() throws IOException {
        // 测试 length 字段边界: 0x00000007 最小合法
        byte[] encoded = new byte[]{
                0, 0, 0, 7,           // length = 7
                0, 0, 0, 1,           // id = 1
                0,                    // flags = 0
                1,                    // commandSet
                2,                    // command
        };
        JdwpPacket p = JdwpPacketCodec.decode(
                new DataInputStream(new ByteArrayInputStream(encoded)));
        assertEquals(1, p.id);
        assertEquals(1, p.commandSet & 0xff);
        assertEquals(2, p.command & 0xff);
        assertEquals(0, p.data.length);
    }

    @Test
    public void toStringContainsAllRelevantFields() {
        JdwpPacket p = new JdwpPacket(0x10, JdwpPacket.FLAG_COMMAND,
                (byte) 0x40, (byte) 0x09, new byte[]{1, 2});
        String s = p.toString();
        // 至少包含 id + flags + cs + cmd + len + data
        assertTrue(s.contains("id=16"));
        assertTrue(s.contains("cs=64"));
        assertTrue(s.contains("cmd=9"));
        assertTrue(s.contains("len=2"));
    }
}
