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
import com.zerostudio.logplugin.api.ILogSink;
import com.zerostudio.logplugin.api.LogLevel;
import com.zerostudio.logplugin.api.LogPayload;
import com.zerostudio.logplugin.api.LogTransportType;
import com.zerostudio.logplugin.util.LogcatReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bridges {@code logcat} output into the standard {@link LogPayload}
 * pipeline. Runs a single background thread that owns the {@code logcat}
 * process for the lifetime of the application.
 */
public final class LogcatCapture {

    private static final String TAG = "LogcatCapture";

    private final CopyOnWriteArrayList<ILogSink> sinks;
    private final AtomicLong sequence;
    private final int pid;

    private volatile boolean running;
    private Thread thread;
    private Process process;

    public LogcatCapture(
            @NonNull CopyOnWriteArrayList<ILogSink> sinks,
            @NonNull AtomicLong sequence,
            int pid) {
        this.sinks = sinks;
        this.sequence = sequence;
        this.pid = pid;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::pump, "ide-log-plugin-logcat");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (process != null) {
            process.destroy();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void pump() {
        try {
            process = new ProcessBuilder(
                    "logcat", "-v", "threadtime", "-b", "all")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (running && (line = br.readLine()) != null) {
                    try {
                        LogcatReader.Line l = LogcatReader.parse(line);
                        if (l == null) {
                            continue;
                        }
                        emit(l);
                    } catch (Throwable t) {
                        Log.w(TAG, "Failed to process logcat line", t);
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                Log.w(TAG, "logcat capture ended", e);
            }
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void emit(@NonNull LogcatReader.Line l) {
        int level;
        switch (l.priority) {
            case android.util.Log.VERBOSE: level = LogLevel.VERBOSE; break;
            case android.util.Log.DEBUG:   level = LogLevel.DEBUG;   break;
            case android.util.Log.INFO:    level = LogLevel.INFO;    break;
            case android.util.Log.WARN:    level = LogLevel.WARN;    break;
            case android.util.Log.ERROR:   level = LogLevel.ERROR;   break;
            case android.util.Log.ASSERT:  level = LogLevel.ASSERT;  break;
            default:                       level = LogLevel.INFO;    break;
        }
        LogPayload payload = new LogPayload(
                sequence.incrementAndGet(),
                l.timestampMs,
                level,
                LogTransportType.LOGCAT,
                l.tag,
                l.pid,
                l.tid,
                l.message,
                null);
        for (ILogSink s : sinks) {
            try {
                s.onLog(payload);
            } catch (Throwable t) {
                Log.w(TAG, "Sink failed", t);
            }
        }
    }
}
