/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase H.3: IdleSessionKiller — automatic disconnect after long idle.
 *
 *  When the user pauses debugging and walks away (or closes the IDE
 *  without disconnecting), the JDWP socket and the target app's JDWP
 *  server stay open. The target app holds a thread in suspended state
 *  (or pinned for breakpoint), preventing the user from using the app
 *  normally.
 *
 *  IdleSessionKiller tracks the time of the last debugger activity
 *  (any inbound event or outbound command). If no activity for
 *  [idleTimeoutMs] (default 5 minutes), it:
 *    1. Sends VM.Resume to release the target VM
 *    2. Closes the JDWP socket
 *    3. Marks the session as DISCONNECTED
 *
 *  Activity is reported via [touch]. The user can disable the killer
 *  by passing `idleTimeoutMs = 0` to the constructor.
 */
package com.zerostudio.debugger.model;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IdleSessionKiller {

    /** Default idle window (ms): 5 minutes. */
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 5L * 60L * 1_000L;

    /** How often the watchdog task runs (must be much smaller than timeout). */
    public static final long DEFAULT_CHECK_INTERVAL_MS = 30L * 1_000L;

    public interface Listener {
        @MainThread
        void onIdleTimeoutReached(long idleMs);
    }

    private final long idleTimeoutMs;
    private final long checkIntervalMs;
    @Nullable private final DebugSession session;
    @Nullable private Listener listener;
    private final AtomicLong lastActivityMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean active = new AtomicBoolean(false);
    @Nullable private ScheduledExecutorService scheduler;
    @Nullable private ScheduledFuture<?> task;

    public IdleSessionKiller(@Nullable DebugSession session) {
        this(session, DEFAULT_IDLE_TIMEOUT_MS, DEFAULT_CHECK_INTERVAL_MS);
    }

    public IdleSessionKiller(@Nullable DebugSession session, long idleTimeoutMs) {
        this(session, idleTimeoutMs, DEFAULT_CHECK_INTERVAL_MS);
    }

    public IdleSessionKiller(@Nullable DebugSession session, long idleTimeoutMs,
                             long checkIntervalMs) {
        this.session = session;
        this.idleTimeoutMs = idleTimeoutMs;
        this.checkIntervalMs = checkIntervalMs;
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    /**
     * Start the idle-killer watchdog. Idempotent: calling start()
     * twice is a no-op.
     */
    @AnyThread
    public synchronized void start() {
        if (!active.compareAndSet(false, true)) return;
        if (idleTimeoutMs <= 0) return;  // disabled
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "IdleSessionKiller");
                t.setDaemon(true);
                return t;
            });
        }
        touch();
        task = scheduler.scheduleAtFixedRate(this::check, checkIntervalMs,
                checkIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop the idle-killer watchdog. Pending checks are cancelled.
     */
    @AnyThread
    public synchronized void stop() {
        if (!active.compareAndSet(true, false)) return;
        if (task != null) task.cancel(false);
        task = null;
    }

    /**
     * Mark the session as active. Call this from any debugger callback
     * (inbound event, outbound command, breakpoint hit, etc.) to reset
     * the idle timer.
     */
    @AnyThread
    public void touch() {
        lastActivityMs.set(System.currentTimeMillis());
    }

    /**
     * Returns the current idle duration in milliseconds.
     */
    public long currentIdleMs() {
        return System.currentTimeMillis() - lastActivityMs.get();
    }

    public boolean isEnabled() { return idleTimeoutMs > 0; }
    public long idleTimeoutMs() { return idleTimeoutMs; }

    @AnyThread
    private void check() {
        long idle = currentIdleMs();
        if (idle < idleTimeoutMs) return;
        // Idle threshold reached.
        stop();
        Listener l = listener;
        if (l != null) {
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> l.onIdleTimeoutReached(idle));
        }
    }

    public void shutdown() {
        stop();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
