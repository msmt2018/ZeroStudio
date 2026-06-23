/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  The main JDWP client. Encapsulates a single connection to a JDWP server
 *  running inside a debug-variant application process.
 *
 *  Usage:
 *
 *    JdwpClient client = new JdwpClient();
 *    client.connect("127.0.0.1", 5005);
 *    client.sendCommand(...);
 *    client.registerEventListener(event -> { ... });
 *    client.close();
 *
 *  The client is thread-safe: it uses a single DataOutputStream guarded by
 *  a lock to send packets, and a dedicated reader thread for inbound
 *  packets.
 */

package com.zerostudio.debugger.jdwp;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

// Note: this class is no longer `final` so unit tests can subclass it
// (see `FakeJdwpClient` in src/test). The class is still safe to extend
// because all the public methods are well-defined and there are no
// package-private fields that subclasses could clobber.
public class JdwpClient {

    private static final String TAG = "JdwpClient";
    private static final int HANDSHAKE_TIMEOUT_MS = 5_000;
    private static final byte[] HANDSHAKE_BYTES = "JDWP-Handshake".getBytes();

    private final Object writeLock = new Object();
    private final AtomicInteger idAllocator = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, ReplyFuture> pending = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<EventListener> eventListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ConnectionListener> connectionListeners =
            new CopyOnWriteArrayList<>();

    @Nullable private Socket socket;
    @Nullable private DataOutputStream out;
    @Nullable private JdwpPacketReader reader;
    @Nullable private String host;
    private int port;

    /**
     * Open a TCP connection to the JDWP server and perform the handshake.
     */
    public void connect(@NonNull String host, int port) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), HANDSHAKE_TIMEOUT_MS);
        s.setTcpNoDelay(true);
        try {
            performHandshake(s);
        } catch (IOException e) {
            try { s.close(); } catch (IOException ignored) { }
            throw e;
        }
        this.socket = s;
        this.host = host;
        this.port = port;
        this.out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        this.reader = new JdwpReaderImpl(s);
        reader.start();
        notifyConnected();
    }

    private void performHandshake(Socket s) throws IOException {
        s.getOutputStream().write(HANDSHAKE_BYTES);
        s.getOutputStream().flush();
        byte[] expected = new byte[HANDSHAKE_BYTES.length];
        int read = 0;
        while (read < expected.length) {
            int n = s.getInputStream().read(expected, read, expected.length - read);
            if (n < 0) {
                throw new IOException("EOF during handshake");
            }
            read += n;
        }
        for (int i = 0; i < HANDSHAKE_BYTES.length; i++) {
            if (expected[i] != HANDSHAKE_BYTES[i]) {
                throw new IOException("Bad handshake response");
            }
        }
    }

    /**
     * Send a command and block until the reply arrives.
     *
     * @param commandSet the command set
     * @param command the command
     * @param data the command payload
     * @return the reply packet (with the 2-byte error code stripped)
     */
    @NonNull
    public JdwpPacket sendCommand(byte commandSet, byte command, @NonNull byte[] data)
            throws IOException {
        int id = idAllocator.incrementAndGet();
        JdwpPacket p = new JdwpPacket.Builder()
                .id(id)
                .commandSet(commandSet)
                .command(command)
                .data(data)
                .build();
        ReplyFuture future = new ReplyFuture();
        pending.put(id, future);
        try {
            writePacket(p);
            return future.await();
        } finally {
            pending.remove(id);
        }
    }

    /**
     * Send a command asynchronously. Replies are dropped (the IDE only ever
     * issues fire-and-forget commands for things like clearing events).
     */
    public void sendCommandNoReply(byte commandSet, byte command, @NonNull byte[] data) {
        int id = idAllocator.incrementAndGet();
        JdwpPacket p = new JdwpPacket.Builder()
                .id(id)
                .commandSet(commandSet)
                .command(command)
                .data(data)
                .build();
        try {
            writePacket(p);
        } catch (IOException e) {
            Log.w(TAG, "sendCommandNoReply failed", e);
        }
    }

    private void writePacket(@NonNull JdwpPacket p) throws IOException {
        DataOutputStream o = out;
        if (o == null) {
            throw new IOException("Not connected");
        }
        synchronized (writeLock) {
            JdwpPacketCodec.writeTo(o, p);
        }
    }

    /** Close the connection. */
    public void close() {
        try {
            if (reader != null) {
                reader.stop();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;
        out = null;
        reader = null;
        // Fail all pending futures.
        for (ReplyFuture f : pending.values()) {
            f.fail(new IOException("Connection closed"));
        }
        pending.clear();
        notifyDisconnected();
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    @Nullable public String host() { return host; }
    public int port() { return port; }

    /** Register a listener for events. */
    public void addEventListener(@NonNull EventListener listener) {
        eventListeners.addIfAbsent(listener);
    }

    public void removeEventListener(@NonNull EventListener listener) {
        eventListeners.remove(listener);
    }

    public void addConnectionListener(@NonNull ConnectionListener listener) {
        connectionListeners.addIfAbsent(listener);
    }

    public void removeConnectionListener(@NonNull ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    private void notifyConnected() {
        for (ConnectionListener l : connectionListeners) {
            try { l.onConnected(); } catch (Throwable t) { Log.w(TAG, "listener", t); }
        }
    }

    private void notifyDisconnected() {
        for (ConnectionListener l : connectionListeners) {
            try { l.onDisconnected(); } catch (Throwable t) { Log.w(TAG, "listener", t); }
        }
    }

    private void dispatchEvent(@NonNull JdwpPacket p) {
        for (EventListener l : eventListeners) {
            try {
                l.onEvent(p);
            } catch (Throwable t) {
                Log.w(TAG, "event listener failed", t);
            }
        }
    }

    private final class JdwpReaderImpl extends JdwpPacketReader {
        JdwpReaderImpl(@NonNull Socket s) {
            super(s, new JdwpPacketReader.Listener() {
                @Override
                public void onReply(@NonNull JdwpPacket packet) {
                    ReplyFuture f = pending.remove(packet.id);
                    if (f == null) {
                        Log.w(TAG, "Reply for unknown id " + packet.id);
                        return;
                    }
                    f.complete(packet);
                }

                @Override
                public void onEvent(@NonNull JdwpPacket packet) {
                    dispatchEvent(packet);
                }

                @Override
                public void onDisconnect() {
                    close();
                }
            });
        }
    }

    private static final class ReplyFuture {
        private JdwpPacket reply;
        private IOException failure;
        private boolean done;
        private final Object lock = new Object();

        synchronized void complete(JdwpPacket p) {
            this.reply = p;
            this.done = true;
            notifyAll();
        }

        synchronized void fail(IOException e) {
            this.failure = e;
            this.done = true;
            notifyAll();
        }

        JdwpPacket await() throws IOException {
            synchronized (lock) {
                long deadline = System.currentTimeMillis() + 5_000L;
                while (!done) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        throw new IOException("Reply timeout");
                    }
                    try {
                        lock.wait(remaining);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted", ie);
                    }
                }
                if (failure != null) throw failure;
                return reply;
            }
        }
    }

    /** Listener for events sent by the JDWP server. */
    public interface EventListener {
        void onEvent(@NonNull JdwpPacket packet);
    }

    /** Listener for connection state changes. */
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
    }
}
