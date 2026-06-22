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

/**
 * A consumer of {@link LogPayload} records. Implementations are invoked on a
 * background thread that is owned by the plugin.
 */
public interface ILogSink {

    /**
     * Called for every captured record. The sink must not block for a long
     * time; if it does the plugin's own back-pressure logic will drop new
     * records and inform the sink via {@link #onBackpressure(int)}.
     */
    void onLog(LogPayload payload);

    /**
     * Called when the plugin drops records because the sink could not keep
     * up. The argument is the number of records that were dropped in this
     * batch.
     */
    void onBackpressure(int droppedCount);

    /**
     * Optional callback fired when the underlying transport (e.g. the socket
     * connection to the IDE) has changed state.
     *
     * @param connected true if the transport is now connected and active.
     */
    default void onConnectionStateChanged(boolean connected) {
        // no-op by default
    }
}
