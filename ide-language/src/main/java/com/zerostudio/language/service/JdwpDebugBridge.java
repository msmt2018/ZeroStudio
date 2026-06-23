package com.zerostudio.language.service;

import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.StackFrame;
import com.zerostudio.language.runtime.FrameSnapshot.ThreadInfo;
import com.zerostudio.language.runtime.FrameSnapshot.Value;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class JdwpDebugBridge extends AdbDebugBridge {

    private Socket jdwpSocket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread readerThread;
    private volatile boolean running = false;
    private int nextId = 1;
    private final Map<Integer, ResponseCallback> pending = new HashMap<>();

    public interface ResponseCallback {
        void onResponse(int cmdSet, int cmd, byte[] data, int errorCode);
    }

    @Override
    public void connect(String serial) {
        super.connect(serial);
        try {
            int port = setupAdbForward(serial);
            jdwpSocket = new Socket("localhost", port);
            in = new DataInputStream(jdwpSocket.getInputStream());
            out = new DataOutputStream(jdwpSocket.getOutputStream());
            handshake();
            running = true;
            readerThread = new Thread(this::readLoop, "JDWP Reader");
            readerThread.start();
            enableEventNotification();
        } catch (Exception e) {
            disconnect();
            throw new RuntimeException("JDWP connect failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        running = false;
        super.disconnect();
        if (readerThread != null) {
            readerThread.interrupt();
        }
        if (jdwpSocket != null) {
            try { jdwpSocket.close(); } catch (IOException ignored) {}
        }
        jdwpSocket = null;
        in = null;
        out = null;
        pending.clear();
    }

    public void setBreakpoint(String className, int line) throws IOException {
        byte[] classId = findClass(className);
        byte[] location = createLocation(classId, line);
        sendEventRequest(21, 1, location);
    }

    public FrameSnapshot getStackFrames(long threadId, int startFrame, int maxFrames) throws IOException {
        FrameSnapshot snapshot = new FrameSnapshot();
        byte[] request = new byte[12];
        writeLong(request, 0, threadId);
        writeInt(request, 8, startFrame);
        writeInt(request, 12, maxFrames);
        sendCommand(12, 2, request, (cmdSet, cmd, data, errorCode) -> {
            int count = readInt(data, 0);
            int offset = 4;
            for (int i = 0; i < count && i < maxFrames; i++) {
                long frameId = readLong(data, offset);
                offset += 8;
                long methodId = readLong(data, offset);
                offset += 8;
                int line = readInt(data, offset);
                offset += 4;
                String methodName = readString(data, offset);
                offset += 2 + methodName.length();
                String fileName = readString(data, offset);
                offset += 2 + fileName.length();
                String className = readString(data, offset);
                offset += 2 + className.length();
                snapshot.addFrame(new StackFrame(methodName, className, line, fileName));
            }
        });
        return snapshot;
    }

    public List<Value> getLocalVariables(long threadId, long frameId) throws IOException {
        List<Value> vars = new ArrayList<>();
        byte[] request = new byte[16];
        writeLong(request, 0, threadId);
        writeLong(request, 8, frameId);
        sendCommand(14, 6, request, (cmdSet, cmd, data, errorCode) -> {
            int count = readInt(data, 0);
            int offset = 4;
            for (int i = 0; i < count; i++) {
                String name = readString(data, offset);
                offset += 2 + name.length();
                String sig = readString(data, offset);
                offset += 2 + sig.length();
                int slot = readInt(data, offset);
                offset += 4;
                vars.add(new Value(name, sig, "Local", "slot=" + slot));
            }
        });
        return vars;
    }

    public void step(long threadId, int size, int depth) throws IOException {
        byte[] request = new byte[16];
        writeLong(request, 0, threadId);
        writeByte(request, 8, (byte) size);
        writeByte(request, 9, (byte) depth);
        sendCommand(12, 6, request, null);
    }

    public void resume(long threadId) throws IOException {
        byte[] request = new byte[9];
        writeLong(request, 0, threadId);
        writeByte(request, 8, (byte) 0);
        sendCommand(12, 4, request, null);
    }

    public void suspend(long threadId) throws IOException {
        byte[] request = new byte[8];
        writeLong(request, 0, threadId);
        sendCommand(12, 3, request, null);
    }

    public long getMainThread() throws IOException {
        long[] result = new long[1];
        sendCommand(1, 10, new byte[0], (cmdSet, cmd, data, errorCode) -> {
            int count = readInt(data, 0);
            if (count > 0) {
                result[0] = readLong(data, 4);
            }
        });
        return result[0];
    }

    private int setupAdbForward(String serial) throws IOException {
        Process p = Runtime.getRuntime().exec("adb forward --list");
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int port = 8000 + (int) (Math.random() * 1000);
        Process forward = Runtime.getRuntime().exec("adb forward tcp:" + port + " jdwp:" + (serial != null ? serial : ""));
        try {
            forward.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return port;
    }

    private void handshake() throws IOException {
        out.write(new byte[]{0x4A, 0x44, 0x57, 0x50, 0x00, 0x01, 0x00, 0x00});
        out.flush();
        byte[] response = new byte[8];
        in.readFully(response);
    }

    private void enableEventNotification() throws IOException {
        sendCommand(1, 11, new byte[]{(byte) 1}, null);
    }

    private byte[] findClass(String className) throws IOException {
        byte[] result = null;
        sendCommand(1, 2, className.getBytes(), (cmdSet, cmd, data, errorCode) -> {
            result = new byte[8];
            System.arraycopy(data, 0, result, 0, 8);
        });
        return result;
    }

    private byte[] createLocation(byte[] classId, int line) {
        byte[] loc = new byte[16];
        System.arraycopy(classId, 0, loc, 0, 8);
        writeLong(loc, 8, 0);
        writeInt(loc, 12, line);
        return loc;
    }

    private void sendEventRequest(int cmdSet, int cmd, byte[] location) throws IOException {
        byte[] request = new byte[20 + location.length];
        writeByte(request, 0, (byte) 2);
        writeInt(request, 1, 1);
        writeInt(request, 5, 0);
        writeInt(request, 9, 0);
        System.arraycopy(location, 0, request, 13, location.length);
        writeInt(request, 13 + location.length, 0);
        sendCommand(cmdSet, cmd, request, null);
    }

    private synchronized void sendCommand(int cmdSet, int cmd, byte[] data, ResponseCallback callback) throws IOException {
        int id = nextId++;
        int length = 11 + data.length;
        out.writeInt(length);
        out.writeInt(id);
        out.writeByte(cmdSet);
        out.writeByte(cmd);
        out.write(data);
        out.flush();
        if (callback != null) {
            pending.put(id, callback);
        }
    }

    private void readLoop() {
        while (running) {
            try {
                int length = in.readInt();
                int id = in.readInt();
                byte flags = in.readByte();
                byte cmdSet = in.readByte();
                byte cmd = in.readByte();
                byte[] data = new byte[length - 11];
                if (data.length > 0) {
                    in.readFully(data);
                }
                if ((flags & 0x80) != 0) {
                    int errorCode = readInt(data, 0);
                    ResponseCallback cb = pending.remove(id);
                    if (cb != null) cb.onResponse(cmdSet, cmd, data, errorCode);
                } else {
                    ResponseCallback cb = pending.remove(id);
                    if (cb != null) cb.onResponse(cmdSet, cmd, data, 0);
                    if (cmdSet == 22 && cmd == 1) {
                        processEvent(data);
                    }
                }
            } catch (IOException e) {
                running = false;
            }
        }
    }

    private void processEvent(byte[] data) {
        int eventKind = data[0] & 0xFF;
        if (eventKind == 2) {
            long threadId = readLong(data, 1);
            long classId = readLong(data, 9);
            long methodId = readLong(data, 17);
            int line = readInt(data, 25);
            FrameSnapshot f = new FrameSnapshot();
            f.addThread(new ThreadInfo("Thread-" + threadId, "SUSPENDED", true));
            f.addFrame(new StackFrame("unknown", "unknown", line, "unknown"));
            emit(new DebugEvent(DebugEvent.Kind.BREAKPOINT_HIT, f));
        }
    }

    private void writeLong(byte[] buf, int offset, long value) {
        for (int i = 7; i >= 0; i--) {
            buf[offset + i] = (byte) (value & 0xFF);
            value >>= 8;
        }
    }

    private void writeInt(byte[] buf, int offset, int value) {
        for (int i = 3; i >= 0; i--) {
            buf[offset + i] = (byte) (value & 0xFF);
            value >>= 8;
        }
    }

    private void writeByte(byte[] buf, int offset, byte value) {
        buf[offset] = value;
    }

    private long readLong(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }

    private int readInt(byte[] data, int offset) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }

    private String readString(byte[] data, int offset) {
        int len = (data[offset] & 0xFF) << 8 | (data[offset + 1] & 0xFF);
        return new String(data, offset + 2, len);
    }
}
