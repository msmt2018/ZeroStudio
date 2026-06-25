/*
 *  ZeroStudio IDE - utilities/logwire
 *
 *  Error frame payload. When the server (target app) hits a problem
 *  it can send an Error frame before the Bye so the IDE shows a
 *  meaningful message instead of "connection closed".
 *
 *  Payload layout:
 *    code    (4 bytes int BE)
 *    message (length-prefixed UTF-8 string)
 *
 *  Codes:
 *    0x01 IO_ERROR        - generic I/O failure
 *    0x02 PROTOCOL_MISMATCH - protocol version mismatch
 *    0x03 INTERNAL        - server-side internal error
 *    0x04 SHUTTING_DOWN   - server is shutting down
 *    0x05 OUT_OF_MEMORY
 */
package com.itsaky.androidide.logwire;

import androidx.annotation.NonNull;

public final class ErrorPayload {

    public static final int CODE_IO_ERROR          = 0x01;
    public static final int CODE_PROTOCOL_MISMATCH = 0x02;
    public static final int CODE_INTERNAL          = 0x03;
    public static final int CODE_SHUTTING_DOWN     = 0x04;
    public static final int CODE_OUT_OF_MEMORY     = 0x05;

    public final int code;
    @NonNull public final String message;

    public ErrorPayload(int code, @NonNull String message) {
        this.code = code;
        this.message = message;
    }

    @NonNull
    public byte[] write() {
        byte[] msgBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int size = 4 + 4 + msgBytes.length;
        byte[] out = new byte[size];
        WireConstants.writeIntBE(out, 0, code);
        WireConstants.writeIntBE(out, 4, msgBytes.length);
        System.arraycopy(msgBytes, 0, out, 8, msgBytes.length);
        return out;
    }

    @NonNull
    public static ErrorPayload read(@NonNull byte[] data) {
        if (data.length < 4 + 4) {
            throw new IllegalArgumentException("ErrorPayload too short: " + data.length);
        }
        int code = WireConstants.readIntBE(data, 0);
        int len = WireConstants.readIntBE(data, 4);
        if (data.length < 8 + len) {
            throw new IllegalArgumentException("ErrorPayload truncated");
        }
        byte[] msgBytes = new byte[len];
        System.arraycopy(data, 8, msgBytes, 0, len);
        return new ErrorPayload(code, new String(msgBytes,
                java.nio.charset.StandardCharsets.UTF_8));
    }
}
