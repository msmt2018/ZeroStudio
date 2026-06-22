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

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.logplugin.api.ILogSink;
import com.zerostudio.logplugin.api.LogLevel;
import com.zerostudio.logplugin.api.LogPayload;
import com.zerostudio.logplugin.api.LogTransportType;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Installs a {@link Thread.UncaughtExceptionHandler} that intercepts all
 * uncaught Java exceptions in the host process, records them as
 * {@link LogPayload} records, flushes the log buffer, and then delegates to
 * the previously installed handler so that the normal crash flow
 * (debuggerd, debuggerd-wrapper, Play Console, ...) still runs.
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";

    private final Thread.UncaughtExceptionHandler previous;
    private final CopyOnWriteArrayList<ILogSink> sinks;
    private final AtomicLong sequence;
    private final int pid;
    private final String packageName;

    public CrashHandler(
            @NonNull Thread.UncaughtExceptionHandler previous,
            @NonNull CopyOnWriteArrayList<ILogSink> sinks,
            @NonNull AtomicLong sequence,
            int pid,
            @NonNull String packageName) {
        this.previous = previous;
        this.sinks = sinks;
        this.sequence = sequence;
        this.pid = pid;
        this.packageName = packageName;
    }

    public static void install(
            @NonNull CopyOnWriteArrayList<ILogSink> sinks,
            @NonNull AtomicLong sequence,
            int pid,
            @NonNull String packageName) {
        Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        if (previous instanceof CrashHandler) {
            return; // already installed
        }
        CrashHandler handler = new CrashHandler(previous, sinks, sequence, pid, packageName);
        Thread.setDefaultUncaughtExceptionHandler(handler);
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        try {
            LogPayload payload = new LogPayload(
                    sequence.incrementAndGet(),
                    System.currentTimeMillis(),
                    LogLevel.JAVA_CRASH,
                    LogTransportType.CRASH,
                    t.getName(),
                    pid,
                    (int) t.getId(),
                    formatCrashMessage(t, e),
                    e);
            for (ILogSink s : sinks) {
                try {
                    s.onLog(payload);
                } catch (Throwable ignored) {
                }
            }
            // Give sinks 200ms to flush. In practice this is not enough for
            // network I/O, but our default sink (LogSocketServer) writes
            // synchronously to a small in-process queue and is fast.
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        } catch (Throwable inner) {
            Log.e(TAG, "Failed to record crash", inner);
        } finally {
            if (previous != null) {
                previous.uncaughtException(t, e);
            }
        }
    }

    @NonNull
    private static String formatCrashMessage(@NonNull Thread t, @NonNull Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return "Uncaught exception in thread " + t.getName() + "\n" + sw.toString();
    }

    @Nullable
    public Thread.UncaughtExceptionHandler getPrevious() {
        return previous;
    }
}
