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

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.logplugin.api.ILogService;
import com.zerostudio.logplugin.api.ILogSink;
import com.zerostudio.logplugin.api.LogLevel;
import com.zerostudio.logplugin.api.LogPayload;
import com.zerostudio.logplugin.api.LogTransportType;
import com.zerostudio.logplugin.transport.LogSocketServer;
import com.zerostudio.logplugin.util.LogBuffer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The runtime implementation of {@link ILogService}. This class is the
 * single integration point: it owns the sinks list, the in-memory ring
 * buffer, the socket server and the various capture threads.
 *
 * <p>It is referenced reflectively from {@link com.zerostudio.logplugin.api.LogServiceHolder}
 * to keep the public API surface free of Android dependencies.
 */
public final class LogCaptureService implements ILogService {

    private static final String TAG = "LogCaptureService";
    private static final String PLUGIN_VERSION = "1.0.0";

    private static final LogCaptureService INSTANCE = new LogCaptureService();

    /** The default buffer size for the in-memory ring buffer. */
    public static final int DEFAULT_BUFFER_CAPACITY = 5_000;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<ILogSink> sinks = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0L);

    private LogBuffer buffer;
    private LogSocketServer socketServer;
    private LogcatCapture logcatCapture;
    private AnrWatchdog anrWatchdog;
    private NativeLogBridge nativeLogBridge;

    private Context appContext;
    private int pid;
    private String packageName;
    private int jdwpPort = 0;

    private LogCaptureService() {
        // private singleton
    }

    @NonNull
    public static LogCaptureService getInstance() {
        return INSTANCE;
    }

    @Override
    public void initialize(@NonNull Context context) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        this.appContext = context.getApplicationContext();
        this.pid = Process.myPid();
        this.packageName = this.appContext.getPackageName();
        this.buffer = new LogBuffer(DEFAULT_BUFFER_CAPACITY);
        // The socket server listens on a free port; the actual port is
        // exposed via ILogService.getListenPort() and the IDE picks it up
        // from the hello packet.
        this.socketServer = new LogSocketServer(0, PLUGIN_VERSION);
        this.socketServer.addSink(new BufferSink());
        this.socketServer.start();

        // Capture all logcat lines for the host process.
        this.logcatCapture = new LogcatCapture(sinks, sequence, pid);
        this.logcatCapture.start();

        // Detect ANRs.
        this.anrWatchdog = new AnrWatchdog(sinks, sequence, pid, packageName);
        this.anrWatchdog.start();

        // Bridge native / JNI logs.
        this.nativeLogBridge = new NativeLogBridge(sinks, sequence, pid);
        this.nativeLogBridge.start();

        // Catch uncaught exceptions.
        CrashHandler.install(sinks, sequence, pid, packageName);

        Log.i(TAG, "ide-log-plugin initialized (pid=" + pid
                + ", pkg=" + packageName
                + ", sdk=" + Build.VERSION.SDK_INT + ")");
    }

    /** Set the port on which the embedded JDWP server is listening. */
    public void setJdwpPort(int port) {
        this.jdwpPort = port;
        if (socketServer != null) {
            socketServer.setJdwpPort(port);
        }
    }

    /** Returns the actual port the log server is bound to. */
    public int getLogcatPort() {
        return socketServer == null ? 0 : socketServer.getPort();
    }

    @Override
    public void registerSink(@NonNull ILogSink sink) {
        sinks.addIfAbsent(sink);
    }

    @Override
    public void unregisterSink(@NonNull ILogSink sink) {
        sinks.remove(sink);
    }

    @Override
    public boolean isConnected() {
        return socketServer != null && socketServer.isConnected();
    }

    @Override
    public int getListenPort() {
        return getLogcatPort();
    }

    @Override
    public void submitSynthetic(int level, @NonNull String tag, @NonNull String message) {
        if (shutdown.get()) {
            return;
        }
        LogPayload p = new LogPayload(
                sequence.incrementAndGet(),
                System.currentTimeMillis(),
                level,
                LogTransportType.APP,
                tag,
                pid,
                (int) Thread.currentThread().getId(),
                message,
                null);
        if (buffer != null) {
            buffer.add(p);
        }
        if (socketServer != null) {
            socketServer.submit(p);
        }
    }

    @Override
    public void shutdown() {
        if (!initialized.get() || !shutdown.compareAndSet(false, true)) {
            return;
        }
        try {
            if (logcatCapture != null) logcatCapture.stop();
            if (anrWatchdog != null) anrWatchdog.stop();
            if (nativeLogBridge != null) nativeLogBridge.stop();
            if (socketServer != null) socketServer.stop();
        } catch (Throwable t) {
            Log.w(TAG, "Error during shutdown", t);
        }
    }

    @Nullable
    public Context getAppContext() {
        return appContext;
    }

    public int getPid() {
        return pid;
    }

    @NonNull
    public String getPackageName() {
        return packageName == null ? "" : packageName;
    }

    /** Read-only view of the in-memory ring buffer. */
    @NonNull
    public java.util.List<LogPayload> snapshotBuffer() {
        return buffer == null
                ? java.util.Collections.emptyList()
                : buffer.snapshot();
    }

    /** Whether the host application is a debuggable build (best-effort). */
    public boolean isHostDebuggable() {
        if (appContext == null) {
            return false;
        }
        try {
            int flags = appContext.getPackageManager().getApplicationInfo(
                    packageName, 0).flags;
            return (flags & PackageManager.FLAG_DEBUGGABLE) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Forwards log records into the in-memory ring buffer. */
    private final class BufferSink implements ILogSink {
        @Override
        public void onLog(@NonNull LogPayload payload) {
            if (buffer != null) {
                buffer.add(payload);
            }
        }

        @Override
        public void onBackpressure(int droppedCount) {
            // No-op: the buffer is bounded and the writer drops as needed.
        }
    }

    /** Suppress unused-import warning. */
    @SuppressWarnings("unused")
    private void keepReferences() {
        int l = LogLevel.INFO;
    }
}
