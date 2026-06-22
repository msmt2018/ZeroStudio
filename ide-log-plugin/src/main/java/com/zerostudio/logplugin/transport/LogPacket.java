/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.transport;

import androidx.annotation.NonNull;
import com.zerostudio.logwire.WireConstants;

/**
 * Backwards-compatible type alias used internally by the plugin. The wire
 * format is fully implemented in {@link com.zerostudio.logwire.WireCodec};
 * this class is a thin wrapper that exposes the constant names plugin
 * code has been using since the first AIDL-based implementation.
 */
public final class LogPacket {

    /** Re-exported from WireConstants for code that has not been updated. */
    public static final byte TYPE_LOG = WireConstants.TYPE_LOG;
    public static final byte TYPE_HELLO = WireConstants.TYPE_HELLO;
    public static final byte TYPE_HEARTBEAT = WireConstants.TYPE_HEARTBEAT;
    public static final byte TYPE_BACKPRESSURE = WireConstants.TYPE_BACKPRESSURE;
    public static final byte TYPE_JDWP = WireConstants.TYPE_JDWP;

    public final byte type;
    @NonNull public final byte[] body;

    public LogPacket(byte type, @NonNull byte[] body) {
        this.type = type;
        this.body = body;
    }
}
