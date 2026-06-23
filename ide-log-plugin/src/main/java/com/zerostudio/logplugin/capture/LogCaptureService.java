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

import com.itsaky.androidide.logwire.WireConstants;
import com.zerostudio.logplugin.api.LogPayload;
import com.zerostudio.logplugin.transport.LogSocketServer;
import com.zerostudio.logplugin.util.LogBuffer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public final class LogCaptureService {

    private static final String TAG = "LogCaptureService";
    private static final LogCaptureService INSTANCE = new LogCaptureService();

    @NonNull private final LogSocketServer server = new LogSocketServer();
    @NonNull private final AtomicInteger jdwpPort = new AtomicInteger(0);
    @NonNull private final AtomicInteger logcatPort = new AtomicInteger(0);
    @Nullable private ScheduledExecutorService scheduler;

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
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
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
