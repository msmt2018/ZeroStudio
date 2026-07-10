/*
 *  ZeroStudio IDE - utilities/logwire
 *
 *  Phase C.1: IDE-side TCP client that consumes the logwire
 *  protocol published by ide-log-plugin's [LogSocketServer].
 *
 *  Usage:
 *    <pre>
 *      LogWireClient client = new LogWireClient(host, port);
 *      client.setListener(new LogWireClient.Listener() {
 *          public void onPayload(LogPayload p) { ... }
 *          public void onHandshake(Handshake h) { ... }
 *          public void onError(ErrorPayload e) { ... }
 *          public void onDisconnected(IOException cause) { ... }
 *      });
 *      client.connect();           // starts the reader thread
 *      // ...
 *      client.close();             // sends BYE
 *    </pre>
 *
 *  Wire format: see WireConstants.
 *
 *  The client is single-threaded: one background thread does
 *  readFrame() in a loop. Callbacks are dispatched on that
 *  thread, so listeners must be thread-safe (or hand off to
 *  the main thread themselves).
 */
package com.itsaky.androidide.logwire;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LogWireClient {

    /** Connect timeout (ms). 1.5s is enough for loopback. */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 1_500;

    public interface Listener {
        /** Server sent the handshake frame. */
        default void onHandshake(@NonNull Handshake handshake) {}
        /** Server pushed a log payload. */
        default void onPayload(@NonNull LogPayload payload) {}
        /** Server pushed an error frame (terminating). */
        default void onError(@NonNull ErrorPayload error) {}
        /** Server sent BYE. */
        default void onBye() {}
        /** Reader died (network/IO error). */
        default void onDisconnected(@Nullable IOException cause) {}
    }

    @NonNull private final String host;
    private final int port;
    @NonNull private final AtomicBoolean closed = new AtomicBoolean(false);
    @Nullable private Socket socket;
    @Nullable private Thread readerThread;
    @Nullable private Listener listener;
    @Nullable private Handshake lastHandshake;
    private final int connectTimeoutMs;

    public LogWireClient(@NonNull String host, int port) {
        this(host, port, DEFAULT_CONNECT_TIMEOUT_MS);
    }

    public LogWireClient(@NonNull String host, int port, int connectTimeoutMs) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    @Nullable
    public Handshake getLastHandshake() { return lastHandshake; }

    /**
     * Open the TCP socket and start the reader thread.
     * Sends a HANDSHAKE frame first, then reads in a loop.
     */
    public void connect(@NonNull Handshake handshake) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        s.setTcpNoDelay(true);
        this.socket = s;
        // Send handshake
        OutputStream out = new BufferedOutputStream(s.getOutputStream());
        FrameCodec.writeFrame(out, WireConstants.TYPE_HANDSHAKE, handshake.write());
        // Start reader
        Thread t = new Thread(this::readLoop, "LogWireClient-reader");
        t.setDaemon(true);
        this.readerThread = t;
        t.start();
    }

    /** Convenience: connect with handshake.defaultFor(getPackageName()). */
    public void connect(@NonNull String packageName) throws IOException {
        connect(Handshake.defaultFor(packageName));
    }

    private void readLoop() {
        Socket s = socket;
        Listener l = listener;
        if (s == null) return;
        try (DataInputStream in = new DataInputStream(s.getInputStream())) {
            while (!closed.get()) {
                FrameCodec.Frame f = FrameCodec.readFrame(in);
                if (l == null) continue;
                switch (f.type) {
                    case WireConstants.TYPE_HANDSHAKE: {
                        Handshake h = Handshake.read(f.payload);
                        this.lastHandshake = h;
                        l.onHandshake(h);
                        break;
                    }
                    case WireConstants.TYPE_LOG_PAYLOAD: {
                        LogPayload p = LogPayload.read(f.payload);
                        l.onPayload(p);
                        break;
                    }
                    case WireConstants.TYPE_HEARTBEAT:
                        // ignore
                        break;
                    case WireConstants.TYPE_ERROR: {
                        ErrorPayload e = ErrorPayload.read(f.payload);
                        l.onError(e);
                        break;
                    }
                    case WireConstants.TYPE_BYE:
                        l.onBye();
                        return;
                    default:
                        // unknown type, ignore
                        break;
                }
            }
        } catch (IOException ioe) {
            if (!closed.get() && l != null) {
                l.onDisconnected(ioe);
            }
        }
    }

    /**
     * Send a heartbeat. Useful for keep-alive on long-idle sessions.
     */
    public void sendHeartbeat() throws IOException {
        Socket s = socket;
        if (s == null) throw new IOException("not connected");
        OutputStream out = s.getOutputStream();
        synchronized (out) {
            FrameCodec.writeFrame(out, WireConstants.TYPE_HEARTBEAT, new byte[0]);
        }
    }

    /**
     * Send BYE and close the socket. Safe to call multiple times.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        Socket s = socket;
        if (s != null) {
            try {
                OutputStream out = s.getOutputStream();
                synchronized (out) {
                    FrameCodec.writeFrame(out, WireConstants.TYPE_BYE, new byte[0]);
                }
            } catch (IOException ignored) {
                // best-effort
            }
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    public boolean isClosed() { return closed.get(); }
}
