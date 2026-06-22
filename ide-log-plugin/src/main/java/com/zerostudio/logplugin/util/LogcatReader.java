/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.util;

import android.util.Log;
import androidx.annotation.NonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;

/**
 * Parses the output of {@code logcat -v threadtime}. The format used by
 * logcat is well-defined and stable across API levels:
 *
 * <pre>
 *   MM-DD HH:MM:SS.mmm  PID  TID PRIORITY TAG: message
 * </pre>
 *
 * <p>The parser is permissive: lines that do not match the expected shape
 * are returned as raw messages under a synthetic tag.
 */
public final class LogcatReader {

    private static final String TAG = "LogcatReader";

    /** A single parsed logcat line. */
    public static final class Line {
        public final long timestampMs;
        public final int pid;
        public final int tid;
        public final int priority;
        @NonNull public final String tag;
        @NonNull public final String message;

        public Line(long timestampMs, int pid, int tid, int priority,
                @NonNull String tag, @NonNull String message) {
            this.timestampMs = timestampMs;
            this.pid = pid;
            this.tid = tid;
            this.priority = priority;
            this.tag = tag;
            this.message = message;
        }
    }

    private LogcatReader() {
        // no instances
    }

    /**
     * Run {@code logcat -v threadtime -b all} and feed each parsed line to
     * the supplied sink. The call blocks until the stream is closed.
     */
    public static void pumpAsync(@NonNull Sink sink) throws IOException {
        Process p = new ProcessBuilder("logcat", "-v", "threadtime", "-b", "all")
                .redirectErrorStream(true)
                .start();
        Thread t = new Thread(() -> drain(p.getInputStream(), sink), "ide-log-plugin-logcat");
        t.setDaemon(true);
        t.start();
    }

    private static void drain(@NonNull InputStream in, @NonNull Sink sink) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    Line l = parse(line);
                    if (l != null) {
                        sink.onLine(l);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to parse line: " + line, t);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "logcat stream ended", e);
        }
    }

    /**
     * Parse a single logcat line. Returns null for the header line and for
     * empty strings.
     */
    @org.jetbrains.annotations.Nullable
    public static Line parse(@NonNull String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        // 09-21 14:23:18.123  12345  12350 I Tag: hello world
        // We tokenize on whitespace. Logcat's threadtime format uses a single
        // space between every field, but tolerate accidental double spaces
        // (some emulators output two spaces after the time component).
        String[] tokens = raw.split("\\s+", 6);
        if (tokens.length < 5) {
            return null;
        }
        // tokens[0] = date (MM-DD)
        // tokens[1] = time (HH:MM:SS.mmm)
        // tokens[2] = pid
        // tokens[3] = tid
        // tokens[4] = priority letter
        // tokens[5] = tag + ": " + message
        int pid, tid;
        try {
            pid = Integer.parseInt(tokens[2]);
            tid = Integer.parseInt(tokens[3]);
        } catch (NumberFormatException nfe) {
            return null;
        }
        char p = tokens[4].charAt(0);
        int priority;
        switch (p) {
            case 'V': priority = android.util.Log.VERBOSE; break;
            case 'D': priority = android.util.Log.DEBUG; break;
            case 'I': priority = android.util.Log.INFO; break;
            case 'W': priority = android.util.Log.WARN; break;
            case 'E': priority = android.util.Log.ERROR; break;
            case 'F': priority = android.util.Log.ASSERT; break;
            default: priority = android.util.Log.INFO; break;
        }
        String tagAndMessage = tokens.length > 5 ? tokens[5] : "";
        int colon = tagAndMessage.indexOf(':');
        if (colon < 0) {
            // No colon means we cannot separate tag from message; treat the
            // whole thing as a message with a synthetic tag.
            long ts = parseDateTime(tokens[0] + " " + tokens[1]);
            return new Line(ts, pid, tid, priority, "logcat", tagAndMessage);
        }
        String tag = tagAndMessage.substring(0, colon).trim();
        String msg = tagAndMessage.substring(colon + 1).trim();
        long ts = parseDateTime(tokens[0] + " " + tokens[1]);
        return new Line(ts, pid, tid, priority, tag, msg);
    }

    private static long parseDateTime(@NonNull String dateTime) {
        // logcat omits the year, so we use the current year as an anchor.
        Calendar c = Calendar.getInstance(new SimpleTimeZone(0, "UTC"), Locale.US);
        int year = c.get(Calendar.YEAR);
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "MM-dd HH:mm:ss.SSS", Locale.US);
            sdf.setTimeZone(new SimpleTimeZone(0, "UTC"));
            c.setTime(sdf.parse(year + "-" + dateTime));
            return c.getTimeInMillis();
        } catch (java.text.ParseException e) {
            return System.currentTimeMillis();
        }
    }

    /** Sink for parsed logcat lines. */
    public interface Sink {
        void onLine(@NonNull Line line);
    }

    /** Pull-mode: read everything currently in the logcat ring buffer. */
    @NonNull
    public static List<Line> snapshot(int maxLines) throws IOException {
        Process p = new ProcessBuilder("logcat", "-d", "-v", "threadtime", "-b", "all")
                .redirectErrorStream(true)
                .start();
        List<Line> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            Deque<Line> ring = new ArrayDeque<>(maxLines);
            String line;
            while ((line = br.readLine()) != null) {
                Line l = parse(line);
                if (l != null) {
                    if (ring.size() >= maxLines) {
                        ring.pollFirst();
                    }
                    ring.addLast(l);
                }
            }
            out.addAll(ring);
        } finally {
            p.destroy();
        }
        return out;
    }
}
