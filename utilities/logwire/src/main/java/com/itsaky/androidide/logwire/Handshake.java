/*
 *  ZeroStudio IDE - utilities/logwire
 *
 *  Initial handshake payload exchanged right after the TCP connect
 *  (but BEFORE the JDWP-Handshake ASCII exchange, if the channel
 *  is shared).
 *
 *  Payload layout:
 *    version      (4 bytes int BE)  - WireConstants.PROTOCOL_VERSION
 *    pid          (4 bytes int BE)  - target process id
 *    pkg          (length-prefixed string)
 *    sessionId    (8 bytes long BE) - random per-launch session id
 */
package com.itsaky.androidide.logwire;

import androidx.annotation.NonNull;

public final class Handshake {

    public final int protocolVersion;
    public final int pid;
    @NonNull public final String packageName;
    public final long sessionId;

    public Handshake(int protocolVersion, int pid,
                     @NonNull String packageName, long sessionId) {
        this.protocolVersion = protocolVersion;
        this.pid = pid;
        this.packageName = packageName;
        this.sessionId = sessionId;
    }

    public static Handshake defaultFor(@NonNull String packageName) {
        int pid = (int) android.os.Process.myPid();
        long session = System.nanoTime();
        return new Handshake(WireConstants.PROTOCOL_VERSION, pid, packageName, session);
    }

    @NonNull
    public byte[] write() {
        byte[] pkgBytes = packageName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int size = 4 + 4 + 4 + pkgBytes.length + 8;
        byte[] out = new byte[size];
        int off = 0;
        WireConstants.writeIntBE(out, off, protocolVersion); off += 4;
        WireConstants.writeIntBE(out, off, pid);             off += 4;
        WireConstants.writeIntBE(out, off, pkgBytes.length); off += 4;
        System.arraycopy(pkgBytes, 0, out, off, pkgBytes.length); off += pkgBytes.length;
        // 64-bit session id
        WireConstants.writeIntBE(out, off, (int) (sessionId >>> 32)); off += 4;
        WireConstants.writeIntBE(out, off, (int) sessionId);
        return out;
    }

    @NonNull
    public static Handshake read(@NonNull byte[] data) {
        if (data.length < 4 + 4 + 4) {
            throw new IllegalArgumentException("Handshake too short: " + data.length);
        }
        int off = 0;
        int ver = WireConstants.readIntBE(data, off); off += 4;
        int pid = WireConstants.readIntBE(data, off); off += 4;
        int pkgLen = WireConstants.readIntBE(data, off); off += 4;
        if (data.length < off + pkgLen + 8) {
            throw new IllegalArgumentException("Handshake truncated");
        }
        byte[] pkgBytes = new byte[pkgLen];
        System.arraycopy(data, off, pkgBytes, 0, pkgLen); off += pkgLen;
        long hi = WireConstants.readIntBE(data, off) & 0xffffffffL; off += 4;
        long lo = WireConstants.readIntBE(data, off) & 0xffffffffL;
        long session = (hi << 32) | lo;
        return new Handshake(ver, pid, new String(pkgBytes,
                java.nio.charset.StandardCharsets.UTF_8), session);
    }
}
