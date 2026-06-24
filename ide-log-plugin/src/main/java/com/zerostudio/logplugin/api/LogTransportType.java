/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Phase C4: transport types for the logcat stream from the host
 *  application to the IDE. Either a TCP socket (the common case)
 *  or a file (used as a side-channel when shizuku / adb are not
 *  available).
 */
package com.zerostudio.logplugin.api;

public enum LogTransportType {
    /** Stream over a TCP socket. The default. */
    TCP(com.itsaky.androidide.logwire.WireConstants.TRANSPORT_TCP),
    /** Append to a file in the app's external files dir. */
    FILE(com.itsaky.androidide.logwire.WireConstants.TRANSPORT_FILE);

    public final byte wire;

    LogTransportType(byte wire) {
        this.wire = wire;
    }

    public static LogTransportType fromWire(byte b) {
        for (LogTransportType t : values()) {
            if (t.wire == b) return t;
        }
        return TCP;
    }
}
