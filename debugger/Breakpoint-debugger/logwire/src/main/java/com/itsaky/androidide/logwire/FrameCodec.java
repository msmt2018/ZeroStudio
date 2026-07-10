/*
 *  ZeroStudio IDE - utilities/logwire
 *
 *  帧编解码器 (FrameCodec): 处理 LOGW magic + type + length + payload
 *  4 段帧格式。同步阻塞 I/O 路径, 适合在单线程 reader 中使用。
 *
 *  不做粘包 / 半包处理: 假定 InputStream 是面向连接的 (TCP) 且调用
 *  readFrame() 一次读一帧;如果 half-packet 出现, 抛 IOException 让
 *  上层重连。
 */
package com.itsaky.androidide.logwire;

import androidx.annotation.NonNull;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class FrameCodec {

    private static final int HEADER_LEN = 4 + 1 + 4; // magic + type + length

    private FrameCodec() {}

    /** 一帧 (header + payload). */
    public static final class Frame {
        public final byte type;
        @NonNull public final byte[] payload;

        public Frame(byte type, @NonNull byte[] payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    /**
     * 从输入流读一帧。读 magic + type + length 然后读 length 字节 payload。
     * magic 不匹配抛 IOException。
     */
    @NonNull
    public static Frame readFrame(@NonNull InputStream in) throws IOException {
        DataInputStream din = in instanceof DataInputStream
                ? (DataInputStream) in
                : new DataInputStream(in);
        int magic = din.readInt();
        if (magic != WireConstants.MAGIC) {
            throw new IOException("Bad magic: 0x" + Integer.toHexString(magic));
        }
        byte type = din.readByte();
        int len = din.readInt();
        if (len < 0 || len > WireConstants.MAX_PAYLOAD_SIZE) {
            throw new IOException("Bad frame length: " + len);
        }
        byte[] payload = new byte[len];
        din.readFully(payload);
        return new Frame(type, payload);
    }

    /** 把一帧写到输出流。 */
    public static void writeFrame(@NonNull OutputStream out, byte type,
                                  @NonNull byte[] payload) throws IOException {
        if (payload.length > WireConstants.MAX_PAYLOAD_SIZE) {
            throw new IOException("Payload too large: " + payload.length);
        }
        // 4 字节 magic BE
        out.write((WireConstants.MAGIC >>> 24) & 0xff);
        out.write((WireConstants.MAGIC >>> 16) & 0xff);
        out.write((WireConstants.MAGIC >>>  8) & 0xff);
        out.write(WireConstants.MAGIC & 0xff);
        out.write(type);
        out.write((payload.length >>> 24) & 0xff);
        out.write((payload.length >>> 16) & 0xff);
        out.write((payload.length >>>  8) & 0xff);
        out.write(payload.length & 0xff);
        out.write(payload);
        out.flush();
    }

    /** 从流中跳过余下字节 (用于 BYE 后清理半包)。 */
    public static void drain(@NonNull InputStream in) throws IOException {
        try {
            while (in.read() != -1) {
                // discard
            }
        } catch (EOFException ignored) {
            // expected
        }
    }
}
