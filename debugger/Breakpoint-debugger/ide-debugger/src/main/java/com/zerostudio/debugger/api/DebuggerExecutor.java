/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase H2: Background execution utilities for UI thread ANR protection.
 *
 *  The Debugger API is designed to be called from any thread, but the UI
 *  thread must remain responsive. This module provides:
 *
 *    - DebuggerExecutor: a bounded executor for background JDWP operations
 *    - AsyncResult<T>: a callback-based async result wrapper
 *    - UI-safe callbacks: results delivered to the main thread
 *
 *  Thread model:
 *    - Public Debugger API methods: any thread (blocking)
 *    - asyncEval() / asyncGetStackFrames(): schedule work on DebuggerExecutor,
 *      deliver result on the caller's thread (or main thread via callback)
 *    - The executor uses a bounded pool to prevent resource exhaustion
 *      when many breakpoints are set simultaneously
 *
 *  Usage:
 *    debugger.asyncEval(threadId, frameId, expression, new Callback<EvalResult>() {
 *        @Override public void onSuccess(EvalResult result) {
 *            textView.setText(result.value);
 *        }
 *        @Override public void onError(Throwable t) {
 *            Log.e(TAG, "eval failed", t);
 *        }
 *    });
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase H2: A bounded executor dedicated to debugger background operations.
 *
 * The executor uses a fixed-size thread pool to limit concurrent JDWP operations.
 * This prevents resource exhaustion when:
 *   - Many breakpoints are set simultaneously
 *   - Multiple watch expressions are evaluated
 *   - Stack frames are fetched for all threads
 *
 * Thread pool: 4 threads, unbounded queue (tasks wait if pool is busy).
 * Bounded by 4 concurrent operations to avoid overwhelming the JDWP server.
 */
public final class DebuggerExecutor implements Executor {

    private static final int CORE_POOL_SIZE = 4;
    private static final String THREAD_PREFIX = "DebuggerBg-";

    private final ExecutorService delegate;

    public DebuggerExecutor() {
        this.delegate = Executors.newFixedThreadPool(
                CORE_POOL_SIZE,
                r -> {
                    Thread t = new Thread(r, THREAD_PREFIX + threadCounter.getAndIncrement());
                    t.setDaemon(true); // Don't block app shutdown
                    return t;
                });
    }

    private static final AtomicInteger threadCounter = new AtomicInteger(0);

    /**
     * Submit a Runnable to be executed in the background.
     */
    @Override
    public void execute(@NonNull Runnable command) {
        delegate.execute(command);
    }

    /**
     * Submit a Callable and get a future.
     */
    public <T> java.util.concurrent.Future<T> submit(@NonNull Callable<T> task) {
        return delegate.submit(task);
    }

    /**
     * Shut down the executor gracefully. Waits up to 5 seconds for tasks to complete.
     */
    public void shutdown() {
        delegate.shutdown();
        try {
            if (!delegate.awaitTermination(5, TimeUnit.SECONDS)) {
                delegate.shutdownNow();
            }
        } catch (InterruptedException ex) {
            delegate.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The number of active threads in the pool.
     */
    public int getActiveCount() {
        return ((ThreadPoolExecutor) delegate).getActiveCount();
    }
}
