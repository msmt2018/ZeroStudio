/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.capture;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import com.zerostudio.logplugin.api.ILogSink;
import com.zerostudio.logplugin.api.LogLevel;
import com.zerostudio.logplugin.api.LogPayload;
import com.zerostudio.logplugin.api.LogTransportType;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A small watchdog that posts a heartbeat runnable to the main looper and
 * reports an ANR if the heartbeat is not acknowledged within a configurable
 * timeout.
 *
 * <p>The implementation is intentionally simple: it does not attempt to be a
 * production-quality ANR detector (Android's own system_server does that
 * much better), but it is a useful, low-overhead supplement for the IDE:
 * developers get immediate feedback when their code blocks the UI thread.
 */
public final class AnrWatchdog {

    private static final String TAG = "AnrWatchdog";

    /** How long we wait before declaring an ANR. */
    public static final long DEFAULT_TIMEOUT_MS = 5_000L;
    /** How often we re-arm the heartbeat. */
    public static final long DEFAULT_INTERVAL_MS = 1_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<ILogSink> sinks;
    private final AtomicLong sequence;
    private final int pid;
    private final String packageName;
    private final long timeoutMs;
    private final long intervalMs;

    private volatile boolean running;
    private Thread thread;
    private long lastPing;
    private long lastReported;

    public AnrWatchdog(
            @NonNull CopyOnWriteArrayList<ILogSink> sinks,
            @NonNull AtomicLong sequence,
            int pid,
            @NonNull String packageName) {
        this(sinks, sequence, pid, packageName, DEFAULT_TIMEOUT_MS, DEFAULT_INTERVAL_MS);
    }

    public AnrWatchdog(
            @NonNull CopyOnWriteArrayList<ILogSink> sinks,
            @NonNull AtomicLong sequence,
            int pid,
            @NonNull String packageName,
            long timeoutMs,
            long intervalMs) {
        this.sinks = sinks;
        this.sequence = sequence;
        this.pid = pid;
        this.packageName = packageName;
        this.timeoutMs = timeoutMs;
        this.intervalMs = intervalMs;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "ide-log-plugin-anr");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void loop() {
        lastPing = System.currentTimeMillis();
        mainHandler.post(this::acknowledge);
        while (running) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            long now = System.currentTimeMillis();
            long gap = now - lastPing;
            if (gap >= timeoutMs && now - lastReported > timeoutMs) {
                reportAnr(gap);
                lastReported = now;
            }
            // Re-arm
            mainHandler.post(this::acknowledge);
        }
    }

    private void acknowledge() {
        lastPing = System.currentTimeMillis();
    }

    private void reportAnr(long blockedMs) {
        try {
            StackTraceElement[] mainStack = Looper.getMainLooper().getThread().getStackTrace();
            StringBuilder sb = new StringBuilder();
            sb.append("Main thread blocked for ").append(blockedMs).append("ms\n");
            sb.append("Main thread stack:\n");
            for (StackTraceElement el : mainStack) {
                sb.append("    at ").append(el).append('\n');
            }
            LogPayload payload = new LogPayload(
                    sequence.incrementAndGet(),
                    System.currentTimeMillis(),
                    LogLevel.ANR,
                    LogTransportType.ANR,
                    "ANR",
                    pid,
                    Looper.getMainLooper().getThread().getId(),
                    sb.toString(),
                    null);
            for (ILogSink s : sinks) {
                try {
                    s.onLog(payload);
                } catch (Throwable t) {
                    Log.w(TAG, "Sink failed to handle ANR", t);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to report ANR", t);
        }
    }
}
