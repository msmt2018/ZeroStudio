/*
 *  ZeroStudio IDE - utilities/logwire
 *
 *  Constants for the logwire protocol used to stream logcat-like
 *  entries from the target application back to the IDE over a TCP
 *  socket.
 *
 *  Wire format
 *  -----------
 *  Each message is framed as:
 *    magic         (4 bytes, ASCII "LOGW")
 *    type          (1 byte)
 *    length        (4 bytes, big-endian)  - size of payload in bytes
 *    payload       (length bytes)
 *
 *  Types
 *  -----
 *    0x01 LOG_PAYLOAD   - LogPayload
 *    0x02 HANDSHAKE     - Handshake
 *    0x03 HEARTBEAT     - empty payload
 *    0x04 BYE           - empty payload
 *    0x05 ERROR         - ErrorPayload
 *
 *  All multi-byte integers are big-endian. All strings are
 *  length-prefixed with a 4-byte int length.
 *
 *  Both sides must agree on PROTOCOL_VERSION in the Handshake
 *  payload; mismatches cause an immediate Bye.
 */
package com.itsaky.androidide.logwire;

public final class WireConstants {

    private WireConstants() {
        // no instances
    }

    /** ASCII "LOGW" - magic prefix at the start of every frame. */
    public static final int MAGIC = 0x4C4F4757;

    /** Protocol version - bump on any breaking wire-format change. */
    public static final int PROTOCOL_VERSION = 1;

    /** Max payload size, in bytes. Larger payloads are dropped. */
    public static final int MAX_PAYLOAD_SIZE = 1024 * 1024;

    // ---- message types ----

    public static final byte TYPE_LOG_PAYLOAD = 0x01;
    public static final byte TYPE_HANDSHAKE   = 0x02;
    public static final byte TYPE_HEARTBEAT   = 0x03;
    public static final byte TYPE_BYE         = 0x04;
    public static final byte TYPE_ERROR       = 0x05;

    // ---- log levels (mirror android.util.Log) ----

    public static final byte LOG_VERBOSE = 2;
    public static final byte LOG_DEBUG   = 3;
    public static final byte LOG_INFO    = 4;
    public static final byte LOG_WARN    = 5;
    public static final byte LOG_ERROR   = 6;
    public static final byte LOG_ASSERT  = 7;

    // ---- transport types ----

    public static final byte TRANSPORT_TCP   = 0x01;
    public static final byte TRANSPORT_FILE = 0x02;

    /** Read a 4-byte big-endian int from the buffer at the given offset. */
    public static int readIntBE(byte[] buf, int off) {
        return ((buf[off]     & 0xff) << 24)
             | ((buf[off + 1] & 0xff) << 16)
             | ((buf[off + 2] & 0xff) <<  8)
             |  (buf[off + 3] & 0xff);
    }

    /** Write a 4-byte big-endian int into the buffer at the given offset. */
    public static void writeIntBE(byte[] buf, int off, int value) {
        buf[off]     = (byte) ((value >>> 24) & 0xff);
        buf[off + 1] = (byte) ((value >>> 16) & 0xff);
        buf[off + 2] = (byte) ((value >>>  8) & 0xff);
        buf[off + 3] = (byte)  (value         & 0xff);
    }
}
