/*
 *  ZeroStudio IDE - logwire
 *
 *  Wire protocol types shared between the ZeroStudio IDE and the
 *  `ide-log-plugin` AAR that runs inside a debug-variant APK. Both sides
 *  must agree on the exact byte-level format and the exact constants used
 *  in the protocol headers.
 *
 *  The protocol is intentionally small: it is the same one originally
 *  carried over AIDL in AndroidIDE, modernised for the new TCP-based
 *  pipeline introduced in PR-1.
 */

package com.zerostudio.logwire;

import androidx.annotation.NonNull;

/** Magic byte sequence at the start of every packet. */
public final class WireConstants {
    public static final int MAGIC = 0x4C50_5352; // 'LPSR' in ASCII
    public static final int WIRE_VERSION = 1;
    public static final byte TYPE_LOG = 1;
    public static final byte TYPE_HELLO = 2;
    public static final byte TYPE_HEARTBEAT = 3;
    public static final byte TYPE_BACKPRESSURE = 4;
    public static final byte TYPE_JDWP = 5;

    private WireConstants() {
        // no instances
    }
}
