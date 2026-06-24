/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase H2: Callback interface for async operations.
 *
 *  A simple callback interface used by Debugger's async methods
 *  (evalAsync, getStackFramesAsync) to deliver results off the UI thread.
 *
 *  The callback is always invoked, even if the debugger is disconnected
 *  or the operation fails. The result may be empty or indicate an error.
 *
 *  Thread safety: The callback is invoked on the same thread that
 *  submitted the async task (the caller's thread).
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;

/**
 * Callback interface for async debugger operations.
 *
 * Implement this interface to receive results from:
 *   - {@link Debugger#evalAsync(long, long, String, Callback)}
 *   - {@link Debugger#getStackFramesAsync(long, int, int, Callback)}
 *
 * @param <T> the type of the result
 */
public interface Callback<T> {

    /**
     * Called when the async operation completes, with the result.
     *
     * This is always called, even on failure. In case of failure, the
     * result may be null, empty, or contain error information.
     *
     * @param result the result of the operation (may be null or empty)
     */
    void onResult(@NonNull T result);
}
