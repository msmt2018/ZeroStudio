/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Phase C4: minimal TCP server that accepts a single IDE
 *  connection and forwards [LogPayload]s from the
 *  [com.zerostudio.logplugin.capture.LogCaptureService] to the
 *  IDE. A more sophisticated implementation would multiplex
 *  multiple consumers, but the IDE-only topology means one
 *  consumer at a time is sufficient.
 */
package com.zerostudio.logplugin.transport;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsaky.androidide.logwire.LogPayload;
import com.itsaky.androidide.logwire.WireConstants;
import com.zerostudio.logplugin.util.LogBuffer;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LogSocketServer {

    private static final String TAG = "LogSocketServer";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Socket> clients = new CopyOnWriteArrayList<>();
    @NonNull private final LogBuffer fallback;
    @Nullable private ServerSocket serverSocket;
    @Nullable private Thread acceptThread;
    @Nullable private ScheduledExecutorService heartbeatExecutor;
    @Nullable private ScheduledFuture<?> heartbeatTask;

    public LogSocketServer() {
        this(new LogBuffer(/* capacity= */ 256));
    }

    public LogSocketServer(@NonNull LogBuffer fallback) {
        this.fallback = fallback;
    }

    public int start(int portHint) {
        if (!running.compareAndSet(false, true)) {
            return serverSocket == null ? 0 : serverSocket.getLocalPort();
        }
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), portHint));
            acceptThread = new Thread(this::acceptLoop, "LogSocketServer-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LogSocketServer-heartbeat");
                t.setDaemon(true);
                return t;
            });
            heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat, 5, 5, TimeUnit.SECONDS);
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            Log.e(TAG, "Failed to start LogSocketServer", e);
            running.set(false);
            return 0;
        }
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        if (heartbeatExecutor != null) heartbeatExecutor.shutdownNow();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        for (Socket s : clients) {
            try { s.close(); } catch (IOException ignored) { }
        }
        clients.clear();
    }

    public boolean isRunning() {
        return running.get();
    }

    public int port() {
        return serverSocket == null ? 0 : serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket s = serverSocket.accept();
                for (Socket existing : clients) {
                    try { existing.close(); } catch (IOException ignored) { }
                }
                clients.clear();
                clients.add(s);
                // Replay the fallback buffer to the new client.
                try {
                    DataOutputStream out = new DataOutputStream(
                            new BufferedOutputStream(s.getOutputStream()));
                    for (com.zerostudio.logplugin.api.LogPayload p : fallback.snapshot()) {
                        writeFrame(out, WireConstants.TYPE_LOG_PAYLOAD, p.toWire().write());
                    }
                    out.flush();
                } catch (IOException e) {
                    Log.w(TAG, "replay failed: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running.get()) {
                    Log.w(TAG, "accept failed", e);
                }
            }
        }
    }

    /** Push a payload to every connected client. */
    public void publish(@NonNull com.zerostudio.logplugin.api.LogPayload payload) {
        fallback.append(payload);
        byte[] bytes = payload.toWire().write();
        for (Socket s : clients) {
            try {
                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(s.getOutputStream()));
                writeFrame(out, WireConstants.TYPE_LOG_PAYLOAD, bytes);
                out.flush();
            } catch (IOException e) {
                Log.w(TAG, "publish failed: " + e.getMessage());
                try { s.close(); } catch (IOException ignored) { }
                clients.remove(s);
            }
        }
    }

    private void sendHeartbeat() {
        for (Socket s : clients) {
            try {
                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(s.getOutputStream()));
                writeFrame(out, WireConstants.TYPE_HEARTBEAT, new byte[0]);
                out.flush();
            } catch (IOException e) {
                clients.remove(s);
            }
        }
    }

    private static void writeFrame(DataOutputStream out, byte type, byte[] payload) throws IOException {
        out.writeInt(WireConstants.MAGIC);
        out.writeByte(type);
        out.writeInt(payload.length);
        out.write(payload);
    }
}
