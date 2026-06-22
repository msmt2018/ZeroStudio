/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.transport;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.logplugin.api.ILogSink;
import com.zerostudio.logplugin.api.LogPayload;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The TCP server that delivers log records to the IDE. The server is the
 * single point of egress; all sinks push into it and a single background
 * thread serializes them on the wire.
 *
 * <p>The class is intentionally thread-safe and intentionally not "fancy":
 * we want it to work on Android API 21+ and to be robust to partial reads
 * and disconnects.
 */
public final class LogSocketServer {

    private static final String TAG = "LogSocketServer";
    /** Heartbeat interval. The IDE uses heartbeats to detect dead sessions. */
    private static final long HEARTBEAT_INTERVAL_MS = 5_000L;

    private final int port;
    private final CopyOnWriteArrayList<ILogSink> sinks = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0L);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final LinkedBlockingQueue<LogPayload> outbound = new LinkedBlockingQueue<>(4_096);

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataOutputStream clientOutput;
    private Thread acceptThread;
    private Thread writerThread;
    private Thread readerThread;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private int jdwpPort = 0;
    private final String pluginVersion;

    public LogSocketServer(int port, @NonNull String pluginVersion) {
        this.port = port;
        this.pluginVersion = pluginVersion;
    }

    /**
     * Set the port on which the embedded JDWP server is listening. This is
     * advertised in the hello packet so the IDE knows where to attach the
     * debugger.
     */
    public void setJdwpPort(int port) {
        this.jdwpPort = port;
    }

    /** Add a sink. Sinks are invoked on a background thread. */
    public void addSink(@NonNull ILogSink sink) {
        sinks.addIfAbsent(sink);
    }

    /** Remove a sink. */
    public void removeSink(@NonNull ILogSink sink) {
        sinks.remove(sink);
    }

    /** Returns true if the server has a client connected. */
    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
    }

    /** Start the server. Idempotent. */
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        ThreadFactory tf = new ThreadFactory() {
            @Override public Thread newThread(@NonNull Runnable r) {
                Thread t = new Thread(r, "ide-log-plugin");
                t.setDaemon(true);
                return t;
            }
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(tf);
        acceptThread = tf.newThread(this::acceptLoop);
        acceptThread.setName("ide-log-plugin-accept");
        acceptThread.start();
        writerThread = tf.newThread(this::writerLoop);
        writerThread.setName("ide-log-plugin-writer");
        writerThread.start();
    }

    /** Stop the server and close all sockets. */
    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        try {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Error while stopping LogSocketServer", e);
        }
    }

    /** Enqueue a log payload for delivery. */
    public void submit(@NonNull LogPayload payload) {
        if (!running.get()) {
            return;
        }
        if (!outbound.offer(payload)) {
            // queue is full; drop the oldest and add the new one
            outbound.poll();
            outbound.offer(payload);
            notifyBackpressure(1);
        }
    }

    private void notifyBackpressure(int dropped) {
        byte[] body = LogPacketCodec.encodeBackpressure(dropped);
        try {
            byte[] wire = LogPacketCodec.encode(new LogPacket(LogPacket.TYPE_BACKPRESSURE, body));
            synchronized (this) {
                if (clientOutput != null) {
                    clientOutput.write(wire);
                    clientOutput.flush();
                }
            }
        } catch (IOException e) {
            // ignore - reader will see the disconnect and tear down
        }
        for (ILogSink s : sinks) {
            try {
                s.onBackpressure(dropped);
            } catch (Throwable t) {
                Log.w(TAG, "Sink failed to handle backpressure", t);
            }
        }
    }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            while (running.get()) {
                try {
                    Socket s = serverSocket.accept();
                    // First-come-first-serve; if there is an existing client we
                    // close it (we only support one IDE at a time).
                    synchronized (this) {
                        if (clientSocket != null && !clientSocket.isClosed()) {
                            clientSocket.close();
                        }
                        clientSocket = s;
                        clientOutput = new DataOutputStream(
                                new BufferedOutputStream(s.getOutputStream()));
                    }
                    readerThread = new Thread(this::readerLoop, "ide-log-plugin-reader");
                    readerThread.setDaemon(true);
                    readerThread.start();
                    sendHello();
                    startHeartbeat();
                    for (ILogSink sink : sinks) {
                        try {
                            sink.onConnectionStateChanged(true);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        Log.w(TAG, "Accept failed", e);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not bind log server on port " + port, e);
        }
    }

    private void sendHello() {
        try {
            byte[] body = LogPacketCodec.encodeHello(
                    pluginVersion, LogPacketCodec.WIRE_VERSION, port, jdwpPort);
            byte[] wire = LogPacketCodec.encode(new LogPacket(LogPacket.TYPE_HELLO, body));
            synchronized (this) {
                if (clientOutput != null) {
                    clientOutput.write(wire);
                    clientOutput.flush();
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to send hello", e);
        }
    }

    private void startHeartbeat() {
        if (scheduler == null) {
            return;
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                byte[] wire = LogPacketCodec.encode(
                        new LogPacket(LogPacket.TYPE_HEARTBEAT, new byte[0]));
                synchronized (this) {
                    if (clientOutput != null) {
                        clientOutput.write(wire);
                        clientOutput.flush();
                    }
                }
            } catch (IOException e) {
                // reader will see the disconnect
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void writerLoop() {
        while (running.get()) {
            try {
                LogPayload p = outbound.poll(200, TimeUnit.MILLISECONDS);
                if (p == null) {
                    continue;
                }
                // Dispatch to in-process sinks first.
                for (ILogSink s : sinks) {
                    try {
                        s.onLog(p);
                    } catch (Throwable t) {
                        Log.w(TAG, "Sink failed", t);
                    }
                }
                // Then forward over the wire if connected.
                byte[] body = LogPacketCodec.encodeLog(p);
                byte[] wire = LogPacketCodec.encode(new LogPacket(LogPacket.TYPE_LOG, body));
                synchronized (this) {
                    if (clientOutput != null) {
                        try {
                            clientOutput.write(wire);
                            clientOutput.flush();
                        } catch (IOException e) {
                            // Reader thread will tear down.
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                Log.e(TAG, "Unexpected error in writer loop", t);
            }
        }
    }

    private void readerLoop() {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream())) {
            while (running.get() && !clientSocket.isClosed()) {
                try {
                    LogPacket p = LogPacketCodec.decode(in);
                    // We currently treat all inbound packets as no-ops on the
                    // server side. Future extensions (e.g. control messages)
                    // would dispatch on p.type here.
                } catch (IOException eof) {
                    break;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Reader loop error", e);
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException ignored) {
            }
            synchronized (this) {
                clientSocket = null;
                clientOutput = null;
            }
            for (ILogSink s : sinks) {
                try {
                    s.onConnectionStateChanged(false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Returns the port the server is actually listening on. */
    public int getPort() {
        return serverSocket == null ? port : serverSocket.getLocalPort();
    }

    @Nullable
    public Socket getClientSocket() {
        return clientSocket;
    }

    /**
     * Allocate a new server with a free port chosen by the operating system.
     * The port is then available via {@link #getPort()}.
     */
    @NonNull
    public static LogSocketServer onFreePort(@NonNull String pluginVersion) {
        return new LogSocketServer(0, pluginVersion);
    }
}
