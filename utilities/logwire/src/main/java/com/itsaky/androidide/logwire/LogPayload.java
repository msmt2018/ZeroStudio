/*
 *  ZeroStudio IDE - utilities/logwire
 *
 *  Single log line flowing over the wire.
 *
 *  Wire layout (matches LogPayload.write):
 *    level      (1 byte)
 *    timestamp  (8 bytes, long, ms since epoch)
 *    threadId   (4 bytes length + N bytes UTF-8)
 *    tag        (4 bytes length + N bytes UTF-8)
 *    message    (4 bytes length + N bytes UTF-8)
 *    throwable  (4 bytes length + N bytes UTF-8, 0 if absent)
 */
package com.itsaky.androidide.logwire;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;

public final class LogPayload {

    public final byte level;
    public final long timestampMs;
    @NonNull public final String threadId;
    @NonNull public final String tag;
    @NonNull public final String message;
    @Nullable public final String throwable;

    public LogPayload(byte level,
                      long timestampMs,
                      @NonNull String threadId,
                      @NonNull String tag,
                      @NonNull String message,
                      @Nullable String throwable) {
        this.level = level;
        this.timestampMs = timestampMs;
        this.threadId = threadId;
        this.tag = tag;
        this.message = message;
        this.throwable = throwable;
    }

    /**
     * Serialize this payload to a byte array using the layout
     * documented above. The output is the payload part of a logwire
     * frame; the caller is responsible for prepending magic+type+
     * length.
     */
    @NonNull
    public byte[] write() {
        byte[] threadBytes = threadId.getBytes(StandardCharsets.UTF_8);
        byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] throwableBytes = throwable == null
                ? new byte[0]
                : throwable.getBytes(StandardCharsets.UTF_8);

        int size = 1 + 8
                + 4 + threadBytes.length
                + 4 + tagBytes.length
                + 4 + messageBytes.length
                + 4 + throwableBytes.length;
        if (size > WireConstants.MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "LogPayload too large: " + size + " > " + WireConstants.MAX_PAYLOAD_SIZE);
        }
        byte[] out = new byte[size];
        int off = 0;
        out[off++] = level;
        writeLong(out, off, timestampMs); off += 8;
        writeString(out, off, threadBytes); off += 4 + threadBytes.length;
        writeString(out, off, tagBytes);     off += 4 + tagBytes.length;
        writeString(out, off, messageBytes); off += 4 + messageBytes.length;
        writeString(out, off, throwableBytes);
        return out;
    }

    /** Read a LogPayload from a buffer. */
    @NonNull
    public static LogPayload read(@NonNull byte[] data) {
        if (data.length < 1 + 8 + 4) {
            throw new IllegalArgumentException("LogPayload too short: " + data.length);
        }
        int off = 0;
        byte level = data[off++];
        long ts = readLong(data, off); off += 8;
        Object[] t = readString(data, off); off += 4 + ((byte[]) t[0]).length;
        Object[] g = readString(data, off); off += 4 + ((byte[]) g[0]).length;
        Object[] m = readString(data, off); off += 4 + ((byte[]) m[0]).length;
        Object[] x = off < data.length ? readString(data, off) : new Object[] {new byte[0], ""};
        return new LogPayload(
                level, ts,
                (String) t[1],
                (String) g[1],
                (String) m[1],
                ((byte[]) x[0]).length == 0 ? null : (String) x[1]);
    }

    private static void writeString(byte[] out, int off, byte[] bytes) {
        WireConstants.writeIntBE(out, off, bytes.length);
        System.arraycopy(bytes, 0, out, off + 4, bytes.length);
    }

    private static Object[] readString(byte[] buf, int off) {
        int len = WireConstants.readIntBE(buf, off);
        byte[] bytes = new byte[len];
        System.arraycopy(buf, off + 4, bytes, 0, len);
        return new Object[] {bytes, new String(bytes, StandardCharsets.UTF_8)};
    }

    private static void writeLong(byte[] out, int off, long v) {
        WireConstants.writeIntBE(out, off,     (int) (v >>> 32));
        WireConstants.writeIntBE(out, off + 4, (int) v);
    }

    private static long readLong(byte[] buf, int off) {
        return (((long) WireConstants.readIntBE(buf, off)) << 32)
                | (WireConstants.readIntBE(buf, off + 4) & 0xffffffffL);
    }
}
