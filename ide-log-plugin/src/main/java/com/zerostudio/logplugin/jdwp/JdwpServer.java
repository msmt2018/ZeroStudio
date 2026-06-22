/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.jdwp;

import android.util.Log;
import androidx.annotation.NonNull;
import com.zerostudio.logplugin.capture.LogCaptureService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stub for the JDWP server that will be implemented in PR-2. This class is
 * already wired into the plugin so that the log service can advertise the
 * JDWP port in its hello packet; PR-2 fills in the actual
 * implementation of {@link #start(int)} to start a JDWP-protocol server.
 *
 * <p>For PR-1 we expose only the listener socket so that the IDE can verify
 * connectivity; the IDE itself drives the protocol.
 */
public final class JdwpServer {

    private static final String TAG = "JdwpServer";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Socket> clients = new CopyOnWriteArrayList<>();
    private ServerSocket serverSocket;
    private Thread acceptThread;

    /**
     * Start the server on the given port. The current stub opens a
     * {@link ServerSocket} and immediately discards inbound connections -
     * the IDE never connects during PR-1, but we keep the surface stable so
     * that PR-2 only has to fill in the protocol handling.
     */
    public int start(int port) {
        if (!running.compareAndSet(false, true)) {
            return serverSocket == null ? 0 : serverSocket.getLocalPort();
        }
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            acceptThread = new Thread(this::acceptLoop, "ide-log-plugin-jdwp");
            acceptThread.setDaemon(true);
            acceptThread.start();
            Log.i(TAG, "JdwpServer stub listening on port " + serverSocket.getLocalPort());
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            Log.e(TAG, "Failed to start JdwpServer", e);
            running.set(false);
            return 0;
        }
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Error closing JdwpServer", e);
        }
        for (Socket s : clients) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        clients.clear();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket s = serverSocket.accept();
                clients.add(s);
                // PR-2: drive the JDWP handshake and protocol here.
            } catch (IOException e) {
                if (running.get()) {
                    Log.w(TAG, "accept failed", e);
                }
            }
        }
    }

    /**
     * Convenience: start a JDWP server and register its port with the
     * {@link LogCaptureService} so that the IDE can discover it via the
     * hello packet.
     */
    public static int startAndRegister(int portHint) {
        JdwpServer server = new JdwpServer();
        int actual = server.start(portHint);
        if (actual > 0) {
            LogCaptureService.getInstance().setJdwpPort(actual);
        }
        return actual;
    }

    @NonNull
    public static JdwpServer getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        static final JdwpServer INSTANCE = new JdwpServer();
    }
}
