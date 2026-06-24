package com.zerostudio.language.service;

import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.StackFrame;
import com.zerostudio.language.runtime.FrameSnapshot.ThreadInfo;
import com.zerostudio.language.runtime.FrameSnapshot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Android Debug Bridge 集成（mock）：模拟 ADB / JDWP 客户端与运行中的应用通信。
 *  - pushBreakpoint / pushFrame：注入调试事件
 *  - events()：消费事件流（断点命中 / 帧快照 / 步进完成）
 *
 * 真实实现需要通过 socket 与 adb forward tcp:5037 通信，
 * 然后用 jdwp-debugger 协议传输 breakpoint / frame / variable 消息。
 */
public class AdbDebugBridge {

    public static final class DebugEvent {
        public enum Kind { BREAKPOINT_HIT, EXCEPTION, STEP_COMPLETE, PROCESS_EXIT, VM_START }
        public final Kind kind;
        public final FrameSnapshot frame;

        public DebugEvent(Kind kind, FrameSnapshot frame) {
            this.kind = kind;
            this.frame = frame;
        }
    }

    public interface EventListener {
        void onEvent(DebugEvent event);
    }

    private final BlockingQueue<DebugEvent> events = new LinkedBlockingQueue<>();
    private final List<EventListener> listeners = new ArrayList<>();
    private volatile boolean connected = false;
    private String deviceSerial = "";

    public void connect(String serial) {
        this.deviceSerial = serial == null ? "" : serial;
        this.connected = true;
    }

    public void disconnect() {
        this.connected = false;
        this.deviceSerial = "";
    }

    public boolean isConnected() { return connected; }
    public String deviceSerial() { return deviceSerial; }

    public void addListener(EventListener l) { if (l != null) listeners.add(l); }

    public void pushBreakpoint(String file, int line, String methodName) {
        if (!connected) return;
        FrameSnapshot f = new FrameSnapshot();
        f.addFrame(new StackFrame(methodName, classFromFile(file), line, file));
        f.addValue(new Value("__bp__", "Breakpoint", "Local", file + ":" + line));
        f.addThread(new ThreadInfo("main", "RUNNABLE", true));
        emit(new DebugEvent(DebugEvent.Kind.BREAKPOINT_HIT, f));
    }

    public void pushFrame(FrameSnapshot frame) {
        if (!connected) return;
        emit(new DebugEvent(DebugEvent.Kind.STEP_COMPLETE, frame));
    }

    public void pushException(String type, String message) {
        if (!connected) return;
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("__exception__", "Throwable", "Local", type));
        f.addValue(new Value("__message__", "String", "Local", message));
        emit(new DebugEvent(DebugEvent.Kind.EXCEPTION, f));
    }

    public DebugEvent pollEvent() {
        return events.poll();
    }

    public DebugEvent takeEvent() throws InterruptedException {
        return events.take();
    }

    protected void emit(DebugEvent event) {
        events.offer(event);
        for (EventListener l : new ArrayList<>(listeners)) {
            try { l.onEvent(event); } catch (Exception ignored) {}
        }
    }

    private static String classFromFile(String file) {
        if (file == null) return "Unknown";
        String name = file;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot >= 0) name = name.substring(0, dot);
        return name;
    }
}
