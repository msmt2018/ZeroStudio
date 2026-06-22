/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  A simple bidirectional stream that reads JDWP packets from a socket
 *  and dispatches them to the registered {@link PacketListener}.
 *
 *  We do not use the NIO Selector API: the IDE only ever needs one
 *  connection at a time, and a plain old blocking thread is easier to
 *  reason about and easy to interrupt.
 */

package com.zerostudio.debugger.jdwp;

import android.util.Log;
import androidx.annotation.NonNull;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads packets from the JDWP socket and dispatches them to the listener
 * associated with the connection.
 */
public final class JdwpPacketReader {

    private static final String TAG = "JdwpPacketReader";

    private final Socket socket;
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    public JdwpPacketReader(@NonNull Socket socket, @NonNull Listener listener) {
        this.socket = socket;
        this.listener = listener;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this::loop, "jdwp-reader");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        if (thread != null) {
            thread.interrupt();
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void loop() {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()))) {
            while (running.get() && !socket.isClosed()) {
                try {
                    JdwpPacket packet = JdwpPacketCodec.decode(in);
                    if (packet.isReply()) {
                        listener.onReply(packet);
                    } else {
                        // Command packets from the server are events or
                        // unexpected commands; we always treat them as
                        // events.
                        listener.onEvent(packet);
                    }
                } catch (IOException ioe) {
                    if (running.get()) {
                        Log.w(TAG, "Read failed", ioe);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Reader loop ended", e);
        } finally {
            listener.onDisconnect();
        }
    }

    /** Receiver for incoming packets. */
    public interface Listener {
        void onReply(@NonNull JdwpPacket packet);
        void onEvent(@NonNull JdwpPacket packet);
        void onDisconnect();
    }
}
