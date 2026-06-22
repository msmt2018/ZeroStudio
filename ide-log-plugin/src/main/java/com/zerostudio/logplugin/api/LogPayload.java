/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A single log record flowing through the plugin.
 *
 * <p>Instances are immutable and may be safely handed off across threads.
 */
public final class LogPayload {

    /** Monotonically increasing id, unique within a single plugin session. */
    public final long id;
    /** Wall-clock time in milliseconds since epoch. */
    public final long timestamp;
    /** The level of this message, see {@link LogLevel}. */
    public final int level;
    /** The transport type, see {@link LogTransportType}. */
    public final int transport;
    /** Logger tag or class name. */
    @NonNull public final String tag;
    /** Process id of the source. */
    public final int pid;
    /** Thread id of the source, or -1 when not applicable. */
    public final int tid;
    /** The textual content of the message. */
    @NonNull public final String message;
    /** Optional throwable, may be null. */
    @Nullable public final Throwable throwable;

    public LogPayload(
            long id,
            long timestamp,
            int level,
            int transport,
            @NonNull String tag,
            int pid,
            int tid,
            @NonNull String message,
            @Nullable Throwable throwable) {
        this.id = id;
        this.timestamp = timestamp;
        this.level = level;
        this.transport = transport;
        this.tag = tag;
        this.pid = pid;
        this.tid = tid;
        this.message = message;
        this.throwable = throwable;
    }

    @Override
    public String toString() {
        return "LogPayload{"
                + "id=" + id
                + ", ts=" + timestamp
                + ", lvl=" + LogLevel.shortCode(level)
                + ", tr=" + transport
                + ", tag=" + tag
                + ", pid=" + pid
                + ", tid=" + tid
                + ", msg=" + message
                + '}';
    }
}
