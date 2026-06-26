/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Phase C4: a single log payload as it travels on the wire.
 *  Mirrors the [com.itsaky.androidide.logwire.LogPayload] but
 *  uses the host plugin's own API surface so the JdwpServer
 *  doesn't need to import utilities/logwire directly.
 */
package com.zerostudio.logplugin.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public final class LogPayload {
    public final byte level;
    public final long timestampMs;
    @NonNull public final String threadId;
    @NonNull public final String tag;
    @NonNull public final String message;
    @Nullable public final String throwable;

    public LogPayload(byte level,
                      long timestampMs,
                      @NonNull String threadId,
                      @NonNull String tag,
                      @NonNull String message,
                      @Nullable String throwable) {
        this.level = level;
        this.timestampMs = timestampMs;
        this.threadId = threadId;
        this.tag = tag;
        this.message = message;
        this.throwable = throwable;
    }

    /** Convert to the wire-protocol representation. */
    @NonNull
    public com.itsaky.androidide.logwire.LogPayload toWire() {
        return new com.itsaky.androidide.logwire.LogPayload(
                level, timestampMs, threadId, tag, message, throwable);
    }

    /** Read from the wire format. */
    @NonNull
    public static LogPayload fromWire(@NonNull com.itsaky.androidide.logwire.LogPayload wire) {
        return new LogPayload(
                wire.level, wire.timestampMs,
                wire.threadId, wire.tag, wire.message, wire.throwable);
    }
}
