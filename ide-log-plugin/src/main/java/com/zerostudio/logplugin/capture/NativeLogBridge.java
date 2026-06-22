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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures JNI / native log messages from the device. The strategy is to
 * run {@code logcat -b crash,main} and feed any line that originates from
 * a native source into the standard log pipeline.
 *
 * <p>We deliberately avoid an in-process JNI library: the IDE has the
 * same access to logcat from Java and we want the plugin to stay
 * 100% Java so that it can be packaged and re-packaged on the IDE side
 * without any NDK build step.
 */
public final class NativeLogBridge {

    private static final String TAG = "NativeLogBridge";

    /** Heuristic: tags commonly used by Android's native libraries. */
    private static final String[] NATIVE_TAGS = {
            "libc",
            "DEBUG",
            "tombstoned",
            "DEBUG",
            "linker",
            "art",
            "OpenGLRenderer",
            "SurfaceFlinger",
            "InputDispatcher",
            "GraphicBufferAllocator",
            "chatty",
            "PackageManager",
            "ActivityManager",
    };

    private final CopyOnWriteArrayList<ILogSink> sinks;
    private final AtomicLong sequence;
    private final int pid;

    private volatile boolean running;
    private Thread thread;

    public NativeLogBridge(
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
        thread = new Thread(this::pump, "ide-log-plugin-native");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void pump() {
        Process p = null;
        try {
            // -b crash,main  → crash buffer for tombstones, main for the rest
            p = new ProcessBuilder("logcat", "-v", "threadtime", "-b", "crash", "-b", "main")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (running && (line = br.readLine()) != null) {
                    try {
                        LogcatReader.Line parsed = LogcatReader.parse(line);
                        if (parsed == null) {
                            continue;
                        }
                        if (!isNativeLine(parsed)) {
                            continue;
                        }
                        int level = parsed.priority == android.util.Log.ERROR
                                ? LogLevel.NATIVE_CRASH
                                : LogLevel.JNI;
                        LogPayload payload = new LogPayload(
                                sequence.incrementAndGet(),
                                parsed.timestampMs,
                                level,
                                level == LogLevel.NATIVE_CRASH
                                        ? LogTransportType.NATIVE_CRASH
                                        : LogTransportType.JNI,
                                parsed.tag,
                                parsed.pid,
                                parsed.tid,
                                parsed.message,
                                null);
                        for (ILogSink s : sinks) {
                            try {
                                s.onLog(payload);
                            } catch (Throwable t) {
                                Log.w(TAG, "Sink failed", t);
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Error processing native log line", t);
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Native log pump ended", e);
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    private static boolean isNativeLine(@NonNull LogcatReader.Line l) {
        // The "tag" of a crash in the crash buffer is usually the basename of
        // the .so file; we accept it regardless of the tag name to keep the
        // behavior simple.
        if (l.priority == android.util.Log.ASSERT || l.priority == android.util.Log.ERROR) {
            return true;
        }
        String tag = l.tag;
        if (tag == null) {
            return false;
        }
        for (String n : NATIVE_TAGS) {
            if (tag.equals(n) || tag.startsWith(n + ":")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convenience: stream the contents of a single log file (typically
     * /data/tombstones/tombstone_xx) into the log pipeline. The IDE can
     * call this method when it detects a tombstone file change.
     */
    public static void ingestTombstone(
            @NonNull InputStream in,
            @NonNull CopyOnWriteArrayList<ILogSink> sinks,
            @NonNull AtomicLong sequence,
            int pid) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder buf = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                buf.append(line).append('\n');
                if (buf.length() > 32 * 1024) {
                    emit(sinks, sequence, pid, buf.toString());
                    buf.setLength(0);
                }
            }
            if (buf.length() > 0) {
                emit(sinks, sequence, pid, buf.toString());
            }
        }
    }

    private static void emit(
            @NonNull CopyOnWriteArrayList<ILogSink> sinks,
            @NonNull AtomicLong sequence,
            int pid,
            @NonNull String text) {
        LogPayload payload = new LogPayload(
                sequence.incrementAndGet(),
                System.currentTimeMillis(),
                LogLevel.NATIVE_CRASH,
                LogTransportType.NATIVE_CRASH,
                "tombstone",
                pid,
                -1,
                text,
                null);
        for (ILogSink s : sinks) {
            try {
                s.onLog(payload);
            } catch (Throwable t) {
                Log.w(TAG, "Sink failed for tombstone", t);
            }
        }
    }
}
