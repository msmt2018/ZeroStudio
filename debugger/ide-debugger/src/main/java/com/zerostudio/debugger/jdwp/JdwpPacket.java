/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Low-level JDWP packet representation. A JDWP packet is:
 *
 *    [length:int32][id:int32][flags:uint8][commandSet:uint8][command:uint8]
 *      ... command data ...
 *
 *  The "flags" byte is set to 0x80 in reply packets; in command packets it
 *  is 0x00. The id is unique within a session: command packets and their
 *  matching reply share an id.
 */

package com.zerostudio.debugger.jdwp;

import androidx.annotation.NonNull;
import java.util.Arrays;

public final class JdwpPacket {

    /** Length of the fixed header (length + id + flags + commandSet + command). */
    public static final int HEADER_SIZE = 11;

    /** Flag byte value for reply packets. */
    public static final byte FLAG_REPLY = (byte) 0x80;
    /** Flag byte value for command packets. */
    public static final byte FLAG_COMMAND = 0x00;

    public final int id;
    public final byte flags;
    public final byte commandSet;
    public final byte command;
    @NonNull public final byte[] data;

    public JdwpPacket(int id, byte flags, byte commandSet, byte command, @NonNull byte[] data) {
        this.id = id;
        this.flags = flags;
        this.commandSet = commandSet;
        this.command = command;
        this.data = data;
    }

    public boolean isReply() {
        return (flags & 0xff) == 0x80;
    }

    public boolean isError() {
        // A reply packet has a 2-byte error code at the start of data when
        // an error is signalled (errorCode != 0).
        if (!isReply()) {
            return false;
        }
        if (data.length < 2) {
            return false;
        }
        int error = ((data[0] & 0xff) << 8) | (data[1] & 0xff);
        return error != 0;
    }

    public int errorCode() {
        if (!isReply() || data.length < 2) {
            return 0;
        }
        return ((data[0] & 0xff) << 8) | (data[1] & 0xff);
    }

    /** Builder for command packets. */
    public static final class Builder {
        private int id;
        private byte commandSet;
        private byte command;
        private byte[] data = new byte[0];

        public Builder id(int v) { this.id = v; return this; }
        public Builder commandSet(byte v) { this.commandSet = v; return this; }
        public Builder command(byte v) { this.command = v; return this; }
        public Builder data(@NonNull byte[] v) { this.data = v; return this; }
        public JdwpPacket build() { return new JdwpPacket(id, FLAG_COMMAND, commandSet, command, data); }
    }

    @Override
    public String toString() {
        return "JdwpPacket{"
                + "id=" + id
                + ", flags=0x" + Integer.toHexString(flags & 0xff)
                + ", cs=" + (commandSet & 0xff)
                + ", cmd=" + (command & 0xff)
                + ", len=" + data.length
                + (isReply() && isError() ? ", err=" + errorCode() : "")
                + ", data=" + Arrays.toString(data)
                + '}';
    }
}
