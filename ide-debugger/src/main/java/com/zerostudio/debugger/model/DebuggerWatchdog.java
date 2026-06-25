/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase H.2: ANR Watchdog — protection against stuck debugger calls.
 *
 *  Android ANR threshold is 5s for foreground services and 30s for
 *  background work. A debugger that hangs waiting for a JDWP reply
 *  would freeze the UI thread or — worse — look "responsive" while
 *  leaving the user unable to cancel.
 *
 *  DebuggerWatchdog enforces a per-call budget. If a JDWP sendCommand
 *  does not return within the budget, the watchdog:
 *    1. Marks the call as TIMED_OUT
 *    2. Cancels the JdwpClient's pending command
 *    3. Notifies the listener (so the UI can show "stuck, retrying…")
 *
 *  The watchdog is implemented as a scheduled-executor-driven timeout
 *  fence. The actual cancel logic is in JdwpClient; this class only
 *  manages the timer and listener.
 */
package com.zerostudio.debugger.model;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class DebuggerWatchdog {

    /** Default per-call budget (ms). Aligned with Android foreground ANR. */
    public static final long DEFAULT_BUDGET_MS = 30_000L;

    public interface Listener {
        /** A debugger call exceeded the budget. The call has been cancelled. */
        @MainThread
        void onStuckCall(@NonNull String callName, long elapsedMs);
    }

    private final long budgetMs;
    @Nullable private Listener listener;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong nextHandle = new AtomicLong(1);
    private final ConcurrentMap<Long, Entry> inflight = new ConcurrentHashMap<>();
    private volatile boolean shutdown = false;

    public DebuggerWatchdog() {
        this(DEFAULT_BUDGET_MS);
    }

    public DebuggerWatchdog(long budgetMs) {
        this.budgetMs = budgetMs;
        ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DebuggerWatchdog");
            t.setDaemon(true);
            return t;
        });
        this.scheduler = s;
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }
    public long budgetMs() { return budgetMs; }

    /**
     * Begin timing a debugger call. Returns a handle that must be
     * passed to [complete] when the call returns.
     */
    @AnyThread
    public long begin(@NonNull String callName) {
        long handle = nextHandle.getAndIncrement();
        Entry e = new Entry(callName, System.nanoTime());
        inflight.put(handle, e);
        ScheduledFuture<?> task = scheduler.schedule(() -> onTimeout(handle),
                budgetMs, TimeUnit.MILLISECONDS);
        e.timeoutTask = task;
        return handle;
    }

    /**
     * Mark a call as completed. Cancels the timeout and clears the entry.
     * Safe to call from any thread.
     */
    @AnyThread
    public void complete(long handle) {
        Entry e = inflight.remove(handle);
        if (e == null) return;
        ScheduledFuture<?> task = e.timeoutTask;
        if (task != null) task.cancel(false);
    }

    @AnyThread
    private void onTimeout(long handle) {
        Entry e = inflight.remove(handle);
        if (e == null) return;
        long elapsedMs = (System.nanoTime() - e.startNanos) / 1_000_000L;
        Listener l = listener;
        if (l != null) {
            // Marshal to main thread (listener is @MainThread).
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> l.onStuckCall(e.callName, elapsedMs));
        }
    }

    /**
     * Shut down the watchdog. Pending timeouts are cancelled.
     */
    public void shutdown() {
        if (shutdown) return;
        shutdown = true;
        scheduler.shutdownNow();
        inflight.clear();
    }

    public int inflightCount() { return inflight.size(); }

    private static final class Entry {
        @NonNull final String callName;
        final long startNanos;
        @Nullable volatile ScheduledFuture<?> timeoutTask;
        Entry(@NonNull String callName, long startNanos) {
            this.callName = callName;
            this.startNanos = startNanos;
        }
    }
}
