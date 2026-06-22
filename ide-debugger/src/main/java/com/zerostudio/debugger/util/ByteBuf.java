/*
 *  ZeroStudio IDE - ide-debugger
 */

package com.zerostudio.debugger.util;

import androidx.annotation.NonNull;
import java.nio.charset.StandardCharsets;

/**
 * A growable byte buffer with manual read/write cursors. We don't use
 * {@link java.nio.ByteBuffer} because the latter's allocations are more
 * expensive on Android, and we don't need its thread-safety guarantees.
 */
public final class ByteBuf {

    private byte[] data;
    private int writePos;
    private int readPos;

    public ByteBuf() {
        this(64);
    }

    public ByteBuf(int initialCapacity) {
        this.data = new byte[initialCapacity];
    }

    public ByteBuf(@NonNull byte[] bytes) {
        this.data = bytes;
    }

    private void ensure(int extra) {
        if (writePos + extra > data.length) {
            int newCap = Math.max(data.length * 2, writePos + extra);
            byte[] grown = new byte[newCap];
            System.arraycopy(data, 0, grown, 0, writePos);
            data = grown;
        }
    }

    public void writeByte(int b) {
        ensure(1);
        data[writePos++] = (byte) b;
    }

    public int readByte() {
        return data[readPos++];
    }

    public int readUnsignedByte() {
        return data[readPos++] & 0xff;
    }

    public void writeShort(int v) {
        ensure(2);
        data[writePos++] = (byte) ((v >>> 8) & 0xff);
        data[writePos++] = (byte) (v & 0xff);
    }

    public short readShort() {
        int v = ((data[readPos] & 0xff) << 8) | (data[readPos + 1] & 0xff);
        readPos += 2;
        return (short) v;
    }

    public int readUnsignedShort() {
        int v = ((data[readPos] & 0xff) << 8) | (data[readPos + 1] & 0xff);
        readPos += 2;
        return v;
    }

    public void writeInt(int v) {
        ensure(4);
        data[writePos++] = (byte) ((v >>> 24) & 0xff);
        data[writePos++] = (byte) ((v >>> 16) & 0xff);
        data[writePos++] = (byte) ((v >>> 8) & 0xff);
        data[writePos++] = (byte) (v & 0xff);
    }

    public int readInt() {
        int v = ((data[readPos] & 0xff) << 24)
                | ((data[readPos + 1] & 0xff) << 16)
                | ((data[readPos + 2] & 0xff) << 8)
                | (data[readPos + 3] & 0xff);
        readPos += 4;
        return v;
    }

    public void writeLong(long v) {
        ensure(8);
        data[writePos++] = (byte) ((v >>> 56) & 0xff);
        data[writePos++] = (byte) ((v >>> 48) & 0xff);
        data[writePos++] = (byte) ((v >>> 40) & 0xff);
        data[writePos++] = (byte) ((v >>> 32) & 0xff);
        data[writePos++] = (byte) ((v >>> 24) & 0xff);
        data[writePos++] = (byte) ((v >>> 16) & 0xff);
        data[writePos++] = (byte) ((v >>> 8) & 0xff);
        data[writePos++] = (byte) (v & 0xff);
    }

    public long readLong() {
        long v = ((long) (data[readPos] & 0xff) << 56)
                | ((long) (data[readPos + 1] & 0xff) << 48)
                | ((long) (data[readPos + 2] & 0xff) << 40)
                | ((long) (data[readPos + 3] & 0xff) << 32)
                | ((long) (data[readPos + 4] & 0xff) << 24)
                | ((long) (data[readPos + 5] & 0xff) << 16)
                | ((long) (data[readPos + 6] & 0xff) << 8)
                | (data[readPos + 7] & 0xff);
        readPos += 8;
        return v;
    }

    public void writeFloat(float v) {
        writeInt(Float.floatToRawIntBits(v));
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public void writeDouble(double v) {
        writeLong(Double.doubleToRawLongBits(v));
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public void writeString(@NonNull String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeInt(bytes.length);
        ensure(bytes.length);
        System.arraycopy(bytes, 0, data, writePos, bytes.length);
        writePos += bytes.length;
    }

    @NonNull
    public String readString() {
        int len = readInt();
        String s = new String(data, readPos, len, StandardCharsets.UTF_8);
        readPos += len;
        return s;
    }

    public void writeBytes(@NonNull byte[] bytes) {
        ensure(bytes.length);
        System.arraycopy(bytes, 0, data, writePos, bytes.length);
        writePos += bytes.length;
    }

    @NonNull
    public byte[] toByteArray() {
        byte[] out = new byte[writePos];
        System.arraycopy(data, 0, out, 0, writePos);
        return out;
    }

    public int size() { return writePos; }
    public int readable() { return writePos - readPos; }
    public int readPos() { return readPos; }
    public int writePos() { return writePos; }
}
