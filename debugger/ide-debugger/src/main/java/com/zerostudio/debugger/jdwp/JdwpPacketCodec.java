/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Encode / decode helper for JDWP packets. This is a small, focused
 *  utility: it never allocates more than it has to, and it works for both
 *  command and reply packets.
 */

package com.zerostudio.debugger.jdwp;

import androidx.annotation.NonNull;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class JdwpPacketCodec {

    private JdwpPacketCodec() {
        // no instances
    }

    @NonNull
    public static byte[] encode(@NonNull JdwpPacket p) {
        int payloadLength = JdwpPacket.HEADER_SIZE - 4 + p.data.length;
        // The length field is the number of bytes following the length
        // field itself.
        byte[] out = new byte[4 + payloadLength];
        out[0] = (byte) ((payloadLength >>> 24) & 0xff);
        out[1] = (byte) ((payloadLength >>> 16) & 0xff);
        out[2] = (byte) ((payloadLength >>> 8) & 0xff);
        out[3] = (byte) (payloadLength & 0xff);
        out[4] = (byte) ((p.id >>> 24) & 0xff);
        out[5] = (byte) ((p.id >>> 16) & 0xff);
        out[6] = (byte) ((p.id >>> 8) & 0xff);
        out[7] = (byte) (p.id & 0xff);
        out[8] = p.flags;
        out[9] = p.commandSet;
        out[10] = p.command;
        System.arraycopy(p.data, 0, out, 11, p.data.length);
        return out;
    }

    @NonNull
    public static JdwpPacket decode(@NonNull DataInputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new IOException("EOF reading packet length");
        }
        int length = (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
        if (length < 7) {
            throw new IOException("Packet too short: " + length);
        }
        if (length > 16 * 1024 * 1024) {
            throw new IOException("Packet too large: " + length);
        }
        byte[] rest = new byte[length];
        in.readFully(rest);
        int id = ((rest[0] & 0xff) << 24) | ((rest[1] & 0xff) << 16)
                | ((rest[2] & 0xff) << 8) | (rest[3] & 0xff);
        byte flags = rest[4];
        byte cs = rest[5];
        byte cmd = rest[6];
        byte[] data = new byte[rest.length - 7];
        System.arraycopy(rest, 7, data, 0, data.length);
        return new JdwpPacket(id, flags, cs, cmd, data);
    }

    /** Convenience: write a packet to an output stream. */
    public static void writeTo(@NonNull DataOutputStream out, @NonNull JdwpPacket p)
            throws IOException {
        out.write(encode(p));
        out.flush();
    }
}
