/*
 *  ZeroStudio IDE - logwire
 *
 *  See com.zerostudio.logwire.WireConstants for the protocol description.
 */

package com.zerostudio.logwire;

import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Encode and decode log wire packets. This is the IDE-side counterpart of
 * the codec inside `ide-log-plugin`; both modules must produce identical
 * bytes for the same input.
 */
public final class WireCodec {

    private WireCodec() {
        // no instances
    }

    @NonNull
    public static byte[] encode(@NonNull WirePacket packet) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(WireConstants.MAGIC);
            dos.writeInt(WireConstants.WIRE_VERSION);
            dos.writeByte(packet.type & 0xff);
            dos.writeInt(packet.body.length);
            dos.write(packet.body);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("encode", e);
        }
    }

    @NonNull
    public static WirePacket decode(@NonNull DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != WireConstants.MAGIC) {
            throw new IOException("bad magic 0x" + Integer.toHexString(magic));
        }
        int version = in.readInt();
        if (version != WireConstants.WIRE_VERSION) {
            throw new IOException("bad version " + version);
        }
        int type = in.readUnsignedByte();
        int length = in.readInt();
        if (length < 0 || length > 16 * 1024 * 1024) {
            throw new IOException("bad length " + length);
        }
        byte[] body = new byte[length];
        in.readFully(body);
        return new WirePacket((byte) type, body);
    }

    @NonNull
    public static byte[] encodeLogRecord(
            long id,
            long timestamp,
            int level,
            int transport,
            @NonNull String tag,
            int pid,
            int tid,
            @NonNull String message,
            @NonNull String stack) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeLong(id);
            dos.writeLong(timestamp);
            dos.writeByte(level & 0xff);
            dos.writeByte(transport & 0xff);
            dos.writeUTF(tag);
            dos.writeInt(pid);
            dos.writeInt(tid);
            dos.writeUTF(message);
            dos.writeUTF(stack);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("encodeLogRecord", e);
        }
    }

    @NonNull
    public static LogRecord decodeLogRecord(@NonNull byte[] body) throws IOException {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(body);
                DataInputStream dis = new DataInputStream(bais)) {
            long id = dis.readLong();
            long ts = dis.readLong();
            int lvl = dis.readUnsignedByte();
            int tr = dis.readUnsignedByte();
            String tag = dis.readUTF();
            int pid = dis.readInt();
            int tid = dis.readInt();
            String msg = dis.readUTF();
            String stack = dis.readUTF();
            return new LogRecord(id, ts, lvl, tr, tag, pid, tid, msg, stack);
        }
    }

    @NonNull
    public static byte[] encodeHello(
            @NonNull String pluginName,
            @NonNull String pluginVersion,
            int apiVersion,
            int logcatPort,
            int jdwpPort) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(pluginName);
            dos.writeUTF(pluginVersion);
            dos.writeInt(apiVersion);
            dos.writeInt(logcatPort);
            dos.writeInt(jdwpPort);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("encodeHello", e);
        }
    }

    @NonNull
    public static HelloInfo decodeHello(@NonNull byte[] body) throws IOException {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(body);
                DataInputStream dis = new DataInputStream(bais)) {
            String name = dis.readUTF();
            String version = dis.readUTF();
            int api = dis.readInt();
            int logcat = dis.readInt();
            int jdwp = dis.readInt();
            return new HelloInfo(name, version, api, logcat, jdwp);
        }
    }

    public static int decodeBackpressure(@NonNull byte[] body) throws IOException {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(body);
                DataInputStream dis = new DataInputStream(bais)) {
            return dis.readInt();
        }
    }

    @NonNull
    public static byte[] encodeBackpressure(int dropped) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(dropped);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("encodeBackpressure", e);
        }
    }

    /** Decoded log record. */
    public static final class LogRecord {
        public final long id;
        public final long timestamp;
        public final int level;
        public final int transport;
        @NonNull public final String tag;
        public final int pid;
        public final int tid;
        @NonNull public final String message;
        @NonNull public final String stack;

        public LogRecord(
                long id,
                long timestamp,
                int level,
                int transport,
                @NonNull String tag,
                int pid,
                int tid,
                @NonNull String message,
                @NonNull String stack) {
            this.id = id;
            this.timestamp = timestamp;
            this.level = level;
            this.transport = transport;
            this.tag = tag;
            this.pid = pid;
            this.tid = tid;
            this.message = message;
            this.stack = stack;
        }
    }

    /** Decoded hello packet. */
    public static final class HelloInfo {
        @NonNull public final String pluginName;
        @NonNull public final String pluginVersion;
        public final int apiVersion;
        public final int logcatPort;
        public final int jdwpPort;

        public HelloInfo(
                @NonNull String pluginName,
                @NonNull String pluginVersion,
                int apiVersion,
                int logcatPort,
                int jdwpPort) {
            this.pluginName = pluginName;
            this.pluginVersion = pluginVersion;
            this.apiVersion = apiVersion;
            this.logcatPort = logcatPort;
            this.jdwpPort = jdwpPort;
        }
    }

    /** A wire packet. */
    public static final class WirePacket {
        public final byte type;
        @NonNull public final byte[] body;

        public WirePacket(byte type, @NonNull byte[] body) {
            this.type = type;
            this.body = body;
        }
    }
}
