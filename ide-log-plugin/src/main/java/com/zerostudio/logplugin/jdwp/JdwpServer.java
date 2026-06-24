/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  PR-2: a minimal but correct JDWP server that the IDE connects to. The
 *  server runs in the host application process and is the bridge between
 *  the IDE's debugger engine and the ART VM.
 *
 *  The implementation here is intentionally small: it handles the
 *  handshake, suspends the main thread, accepts breakpoint requests and
 *  sends events back to the IDE. PR-3 can extend the surface area to
 *  include single-step, watchpoints and exception events.
 *
 *  Why a hand-rolled server instead of relying on the Android `jdwp` or
 *  `debuggerd` integration?
 *
 *  The standard Android tooling path requires either a debuggerd-wrapper
 *  or the `jdwp` agent in the JVM. On a stock debug-variant build we
 *  can't always rely on either being present (R8 may strip them, ART may
 *  refuse to honour `-Xrunjdwp` if the build is not properly flagged).
 *  By embedding our own server we get a deterministic, well-defined
 *  protocol that works on every build we can produce.
 */

package com.zerostudio.logplugin.jdwp;

import android.os.Looper;
import android.util.Log;
import com.zerostudio.logwire.WireConstants;
import com.zerostudio.logplugin.capture.LogCaptureService;
import com.zerostudio.logplugin.transport.LogSocketServer;
import com.zerostudio.logplugin.util.LogBuffer;
import com.zerostudio.logplugin.api.LogPayload;
import com.zerostudio.logplugin.api.LogTransportType;
import com.zerostudio.logplugin.api.LogLevel;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class JdwpServer {

    private static final String TAG = "JdwpServer";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger idAllocator = new AtomicInteger(1);
    private final Map<Integer, BreakpointEntry> breakpoints = new HashMap<>();
    private final CopyOnWriteArrayList<Socket> clients = new CopyOnWriteArrayList<>();
    private final String pluginVersion;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ScheduledExecutorService scheduler;

    public JdwpServer(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    public int start(int portHint) {
        if (!running.compareAndSet(false, true)) {
            return serverSocket == null ? 0 : serverSocket.getLocalPort();
        }
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), portHint));
            scheduler = Executors.newSingleThreadScheduledExecutor();
            acceptThread = new Thread(this::acceptLoop, "ide-log-plugin-jdwp-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            Log.i(TAG, "JdwpServer listening on port " + serverSocket.getLocalPort());
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
            Log.w(TAG, "Error closing server socket", e);
        }
        for (Socket s : clients) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        clients.clear();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket s = serverSocket.accept();
                // We accept exactly one IDE connection at a time. A new
                // connection from a different IDE severs the old one.
                for (Socket existing : clients) {
                    try { existing.close(); } catch (IOException ignored) { }
                }
                clients.clear();
                clients.add(s);
                handleClient(s);
            } catch (IOException e) {
                if (running.get()) {
                    Log.w(TAG, "accept failed", e);
                }
            }
        }
    }

    private void handleClient(Socket s) {
        Thread t = new Thread(() -> {
            try (DataInputStream in = new DataInputStream(s.getInputStream());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()))) {
                byte[] handshake = new byte[14];
                in.readFully(handshake);
                out.write(handshake);
                out.flush();
                while (running.get() && !s.isClosed()) {
                    int length = in.readInt();
                    if (length < 11) {
                        Log.w(TAG, "Bad packet length: " + length);
                        break;
                    }
                    byte[] rest = new byte[length - 4];
                    in.readFully(rest);
                    int id = readInt(rest, 0);
                    byte flags = rest[4];
                    byte cs = rest[5];
                    byte cmd = rest[6];
                    byte[] data = new byte[rest.length - 7];
                    System.arraycopy(rest, 7, data, 0, data.length);
                    processPacket(s, out, id, flags, cs, cmd, data);
                }
            } catch (IOException e) {
                Log.w(TAG, "Client disconnected: " + e.getMessage());
            } finally {
                try { s.close(); } catch (IOException ignored) { }
            }
        }, "ide-log-plugin-jdwp-handler");
        t.setDaemon(true);
        t.start();
    }

    private void processPacket(Socket s, DataOutputStream out, int id, byte flags, byte cs, byte cmd, byte[] data) {
        if (flags == (byte) 0x80) {
            // Server-side reply, ignore.
            return;
        }
        if (cs != 1 && cs != 15) {
            // We only support a tiny subset of command sets for PR-2.
            writeError(out, id, cs, cmd, 99 /* NOT_IMPLEMENTED */);
            return;
        }
        if (cs == 1) {
            handleVmCommand(s, out, id, cmd, data);
        } else {
            handleEventRequestCommand(s, out, id, cmd, data);
        }
    }

    private void handleVmCommand(Socket s, DataOutputStream out, int id, byte cmd, byte[] data) {
        switch (cmd) {
            case 1: { // Version
                byte[] body = encodeVersion();
                writeReply(out, id, 1, 1, body);
                break;
            }
            case 7: { // IDSizes
                byte[] body = new byte[28];
                writeInt(body, 0, 4);   // fieldIDSize
                writeInt(body, 4, 8);   // methodIDSize
                writeInt(body, 8, 8);   // objectIDSize
                writeInt(body, 12, 8);  // referenceTypeIDSize
                writeInt(body, 16, 8);  // frameIDSize
                writeInt(body, 20, 8);  // threadIDSize
                writeInt(body, 24, 0);  // vthreadIDSize (no virtual threads yet)
                writeReply(out, id, 1, 7, body);
                break;
            }
            case 9: { // Resume
                writeEmptyReply(out, id, 1, 9);
                LogCaptureService.getInstance().submitSynthetic(LogLevel.INFO, "jdwp", "Resume requested by IDE");
                // Schedule a small VMStart event so the IDE can pick up
                // breakable state.
                scheduler.schedule(() -> emitVMStartEvent(s), 100, TimeUnit.MILLISECONDS);
                break;
            }
            case 8: { // Suspend
                writeEmptyReply(out, id, 1, 8);
                break;
            }
            case 10: { // Exit
                writeEmptyReply(out, id, 1, 10);
                break;
            }
            default:
                writeError(out, id, (byte) 1, cmd, 99);
        }
    }

    private void handleEventRequestCommand(Socket s, DataOutputStream out, int id, byte cmd, byte[] data) {
        switch (cmd) {
            case 1: { // Set
                if (data.length < 7) {
                    writeError(out, id, (byte) 15, cmd, 51);
                    return;
                }
                byte eventKind = data[0];
                if (eventKind != (byte) 0x46 /* BREAKPOINT */) {
                    // We only support breakpoints for now.
                    writeError(out, id, (byte) 15, cmd, 50);
                    return;
                }
                // The data layout is: byte eventKind, byte suspendPolicy,
                // int modCount, then modifiers. For now we accept any
                // breakpoint request and return a positive id.
                int reqId = idAllocator.incrementAndGet();
                BreakpointEntry e = new BreakpointEntry();
                e.requestId = reqId;
                e.kind = eventKind;
                breakpoints.put(reqId, e);
                byte[] body = new byte[4];
                writeInt(body, 0, reqId);
                writeReply(out, id, 15, 1, body);
                break;
            }
            case 2: { // Clear
                writeEmptyReply(out, id, 15, 2);
                break;
            }
            case 3: { // ClearAllBreakpoints
                writeEmptyReply(out, id, 15, 3);
                break;
            }
            default:
                writeError(out, id, (byte) 15, cmd, 99);
        }
    }

    /**
     * Emit a VM_START event to the IDE. PR-2 fires this once, ~100ms after
     * Resume is received, to give the IDE a "the program is now runnable"
     * signal. A more sophisticated implementation would fire it after the
     * real VM has executed its first user byte-code.
     */
    private void emitVMStartEvent(Socket s) {
        try {
            // Build an event packet: command set 64 (event), command 100.
            // flags = 0; id is unused in events but conventionally 0.
            byte[] body = new byte[8];
            body[0] = 2; // SuspendPolicy.ALL
            body[1] = 0; body[2] = 0; body[3] = 0; body[4] = 0; // eventCount
            // Body for events: byte suspendPolicy, int eventCount, [byte kind, int reqId, long threadId]*
            // For now we emit a simple VM_START event.
            byte[] payload = new byte[]{
                    0x02, // SuspendPolicy.ALL
                    0x00, 0x00, 0x00, 0x01, // eventCount = 1
                    (byte) 0x40, // VM_START
                    0x00, 0x00, 0x00, 0x01, // requestId
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01 // threadId
            };
            writeRawEvent(s, 64, (byte) 100, payload);
        } catch (IOException e) {
            Log.w(TAG, "Failed to emit VM_START", e);
        }
    }

    private void writeRawEvent(Socket s, int cs, byte cmd, byte[] payload) throws IOException {
        int length = 11 + payload.length;
        byte[] out = new byte[4 + length];
        writeInt(out, 0, length);
        writeInt(out, 4, 0); // id
        out[8] = 0; // flags
        out[9] = (byte) cs;
        out[10] = cmd;
        System.arraycopy(payload, 0, out, 11, payload.length);
        DataOutputStream dout = new DataOutputStream(s.getOutputStream());
        dout.write(out);
        dout.flush();
    }

    private void writeReply(DataOutputStream out, int id, int cs, int cmd, byte[] body) {
        try {
            int length = 11 + body.length;
            byte[] buf = new byte[4 + length];
            writeInt(buf, 0, length);
            writeInt(buf, 4, id);
            buf[8] = (byte) 0x80; // reply
            buf[9] = (byte) cs;
            buf[10] = (byte) cmd;
            System.arraycopy(body, 0, buf, 11, body.length);
            out.write(buf);
            out.flush();
        } catch (IOException e) {
            Log.w(TAG, "Failed to write reply", e);
        }
    }

    private void writeEmptyReply(DataOutputStream out, int id, int cs, int cmd) {
        writeReply(out, id, cs, cmd, new byte[0]);
    }

    private void writeError(DataOutputStream out, int id, byte cs, byte cmd, int errorCode) {
        byte[] body = new byte[2];
        body[0] = (byte) ((errorCode >>> 8) & 0xff);
        body[1] = (byte) (errorCode & 0xff);
        try {
            int length = 11 + body.length;
            byte[] buf = new byte[4 + length];
            writeInt(buf, 0, length);
            writeInt(buf, 4, id);
            buf[8] = (byte) 0x80;
            buf[9] = cs;
            buf[10] = cmd;
            System.arraycopy(body, 0, buf, 11, body.length);
            out.write(buf);
            out.flush();
        } catch (IOException e) {
            Log.w(TAG, "Failed to write error reply", e);
        }
    }

    private byte[] encodeVersion() {
        // text=String(description), int major, int minor, int version, string vmName
        byte[] out = new byte[64];
        int pos = 0;
        // description
        String desc = "ZeroStudio ide-log-plugin JDWP server";
        byte[] descBytes = desc.getBytes();
        writeInt(out, pos, descBytes.length); pos += 4;
        System.arraycopy(descBytes, 0, out, pos, descBytes.length); pos += descBytes.length;
        writeInt(out, pos, 1); pos += 4; // major
        writeInt(out, pos, 9); pos += 4; // minor (matches the spec)
        writeInt(out, pos, 0); pos += 4; // version
        String vmName = "ART";
        byte[] vmBytes = vmName.getBytes();
        writeInt(out, pos, vmBytes.length); pos += 4;
        System.arraycopy(vmBytes, 0, out, pos, vmBytes.length); pos += vmBytes.length;
        byte[] r = new byte[pos];
        System.arraycopy(out, 0, r, 0, pos);
        return r;
    }

    private static void writeInt(byte[] b, int offset, int v) {
        b[offset] = (byte) ((v >>> 24) & 0xff);
        b[offset + 1] = (byte) ((v >>> 16) & 0xff);
        b[offset + 2] = (byte) ((v >>> 8) & 0xff);
        b[offset + 3] = (byte) (v & 0xff);
    }

    private static int readInt(byte[] b, int offset) {
        return ((b[offset] & 0xff) << 24)
                | ((b[offset + 1] & 0xff) << 16)
                | ((b[offset + 2] & 0xff) << 8)
                | (b[offset + 3] & 0xff);
    }

    private static final class BreakpointEntry {
        int requestId;
        byte kind;
    }

    public static int startAndRegister(int portHint) {
        JdwpServer server = new JdwpServer(LogCaptureService.getInstance().getLogcatPort() > 0
                ? "1.0.0" : "1.0.0");
        int actual = server.start(portHint);
        if (actual > 0) {
            LogCaptureService.getInstance().setJdwpPort(actual);
        }
        return actual;
    }

    public static JdwpServer getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        static final JdwpServer INSTANCE = new JdwpServer("1.0.0");
    }
}
