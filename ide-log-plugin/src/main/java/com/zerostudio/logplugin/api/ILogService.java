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

import android.content.Context;
import androidx.annotation.NonNull;

/**
 * The main entry point of the log plugin. The host application obtains an
 * instance via {@link #get()} and uses it to subscribe sinks, query the
 * transport state and shut the plugin down on application exit.
 *
 * <p>The implementation lives inside the host process; the IDE never directly
 * references it - communication goes through the TCP socket exposed by
 * {@link com.zerostudio.logplugin.transport.LogSocketServer}.
 */
public interface ILogService {

    /**
     * Initialize the plugin. Must be called before any sink is registered.
     * It is safe to call this multiple times; subsequent calls are no-ops.
     */
    void initialize(@NonNull Context context);

    /**
     * Register a sink. The plugin guarantees that {@link ILogSink#onLog} is
     * invoked sequentially and never concurrently for the same sink.
     */
    void registerSink(@NonNull ILogSink sink);

    /** Unregister a previously registered sink. No-op if not registered. */
    void unregisterSink(@NonNull ILogSink sink);

    /** Returns true if the plugin is currently connected to the IDE. */
    boolean isConnected();

    /** Returns the port on which the plugin listens for IDE connections. */
    int getListenPort();

    /**
     * Submit a synthetic log record. The plugin tags it with the host process
     * and thread id and forwards it through the standard pipeline.
     */
    void submitSynthetic(int level, @NonNull String tag, @NonNull String message);

    /** Request a clean shutdown. After this call no further records are emitted. */
    void shutdown();

    /** Obtain the singleton instance for the current process. */
    @NonNull
    static ILogService get() {
        return LogServiceHolder.INSTANCE;
    }
}
