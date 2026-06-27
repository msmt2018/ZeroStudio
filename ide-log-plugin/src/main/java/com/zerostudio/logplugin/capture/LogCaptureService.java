/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Phase C4: the host-side service that captures android.util.Log
 *  entries and forwards them to the [LogSocketServer] for the IDE
 *  to consume.
 *
 *  The service is a process-singleton: there is at most one
 *  capture service per app process. It owns:
 *    - a [LogSocketServer] bound to the loopback address
 *    - the JDWP server port (set by JdwpServer.startAndRegister)
 *    - a small in-memory buffer used for replay on reconnect
 */
package com.zerostudio.logplugin.capture;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zerostudio.logplugin.api.LogLevel;
import com.zerostudio.logplugin.api.LogPayload;
import com.zerostudio.logplugin.transport.LogSocketServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class LogCaptureService {

    private static final String TAG = "LogCaptureService";
    private static final LogCaptureService INSTANCE = new LogCaptureService();

    @NonNull private final LogSocketServer server = new LogSocketServer();
    @NonNull private final AtomicInteger jdwpPort = new AtomicInteger(0);
    @NonNull private final AtomicInteger logcatPort = new AtomicInteger(0);
    @Nullable private ScheduledExecutorService scheduler;
    @NonNull private final AtomicBoolean logcatReaderStarted = new AtomicBoolean(false);
    @Nullable private Process logcatProcess;

    private LogCaptureService() {
        // singleton
    }

    @NonNull
    public static LogCaptureService getInstance() {
        return INSTANCE;
    }

    public int startLogcat(int portHint) {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LogCaptureService-scheduler");
                t.setDaemon(true);
                return t;
            });
        }
        int port = server.start(portHint);
        if (port > 0) {
            logcatPort.set(port);
            startLogcatReader();
        }
        return port;
    }

    public int getLogcatPort() {
        return logcatPort.get();
    }

    public void setJdwpPort(int port) {
        jdwpPort.set(port);
    }

    public int getJdwpPort() {
        return jdwpPort.get();
    }

    public boolean isRunning() {
        return server.isRunning();
    }

    public void stop() {
        server.stop();
        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }
        logcatReaderStarted.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void startLogcatReader() {
        if (!logcatReaderStarted.compareAndSet(false, true)) {
            return;
        }
        Thread reader = new Thread(() -> {
            try {
                int pid = android.os.Process.myPid();
                ProcessBuilder pb = new ProcessBuilder(
                        "/system/bin/logcat", "-v", "threadtime", "--pid=" + pid);
                pb.redirectErrorStream(true);
                logcatProcess = pb.start();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(logcatProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        publishLogcatLine(line);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "logcat reader stopped: " + t.getMessage());
            } finally {
                logcatReaderStarted.set(false);
            }
        }, "LogCaptureService-logcat");
        reader.setDaemon(true);
        reader.start();
    }

    private void publishLogcatLine(@NonNull String line) {
        // threadtime: MM-DD HH:MM:SS.mmm pid tid LEVEL TAG: message
        String[] parts = line.trim().split("\\s+", 7);
        byte level = LogLevel.DEBUG;
        String tag = "logcat";
        String message = line;
        String thread = Thread.currentThread().getName();
        if (parts.length >= 7) {
            level = levelFromLetter(parts[4]);
            thread = parts[3];
            tag = parts[5].endsWith(":")
                    ? parts[5].substring(0, parts[5].length() - 1)
                    : parts[5];
            message = parts[6];
        }
        try {
            server.publish(new LogPayload(
                    level, System.currentTimeMillis(), thread, tag, message, null));
        } catch (Throwable t) {
            Log.w(TAG, "publish logcat line failed: " + t.getMessage());
        }
    }

    private static byte levelFromLetter(@NonNull String value) {
        if (value.isEmpty()) return LogLevel.DEBUG;
        switch (value.substring(0, 1).toUpperCase(Locale.US)) {
            case "V": return LogLevel.VERBOSE;
            case "D": return LogLevel.DEBUG;
            case "I": return LogLevel.INFO;
            case "W": return LogLevel.WARN;
            case "E": return LogLevel.ERROR;
            case "A": return LogLevel.ASSERT;
            default: return LogLevel.DEBUG;
        }
    }

    /** Push a synthetic log line as if it came from android.util.Log. */
    public void submitSynthetic(byte level, @NonNull String tag, @NonNull String message) {
        submitSynthetic(level, tag, message, null);
    }

    public void submitSynthetic(byte level, @NonNull String tag,
                                @NonNull String message, @Nullable Throwable t) {
        String throwable = null;
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            throwable = sw.toString();
        }
        LogPayload p = new LogPayload(
                level, System.currentTimeMillis(),
                Thread.currentThread().getName(),
                tag, message, throwable);
        try {
            server.publish(p);
        } catch (Throwable ex) {
            Log.w(TAG, "publish failed: " + ex.getMessage());
        }
    }
}
