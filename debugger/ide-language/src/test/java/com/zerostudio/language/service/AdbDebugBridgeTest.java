package com.zerostudio.language.service;

import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.StackFrame;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class AdbDebugBridgeTest {

    @Test
    public void connectAndDisconnect() {
        AdbDebugBridge b = new AdbDebugBridge();
        assertFalse(b.isConnected());
        b.connect("emulator-5554");
        assertTrue(b.isConnected());
        assertEquals("emulator-5554", b.deviceSerial());
        b.disconnect();
        assertFalse(b.isConnected());
    }

    @Test
    public void pushBreakpointEmitsEvent() {
        AdbDebugBridge b = new AdbDebugBridge();
        b.connect("dev");
        AtomicReference<AdbDebugBridge.DebugEvent> got = new AtomicReference<>();
        b.addListener(got::set);
        b.pushBreakpoint("X.java", 10, "foo");
        assertNotNull(got.get());
        assertEquals(AdbDebugBridge.DebugEvent.Kind.BREAKPOINT_HIT, got.get().kind);
        assertEquals("X.java", got.get().frame.topFrame().sourcePath);
    }

    @Test
    public void pushWithoutConnectDoesNotEmit() {
        AdbDebugBridge b = new AdbDebugBridge();
        AtomicInteger count = new AtomicInteger();
        b.addListener(e -> count.incrementAndGet());
        b.pushBreakpoint("X.java", 1, "m");
        assertEquals(0, count.get());
    }

    @Test
    public void pushExceptionIncludesType() {
        AdbDebugBridge b = new AdbDebugBridge();
        b.connect("dev");
        AtomicReference<AdbDebugBridge.DebugEvent> got = new AtomicReference<>();
        b.addListener(got::set);
        b.pushException("java.lang.NullPointerException", "boom");
        assertNotNull(got.get());
        assertEquals(AdbDebugBridge.DebugEvent.Kind.EXCEPTION, got.get().kind);
        assertEquals("java.lang.NullPointerException", got.get().frame.values().get("__exception__").value);
    }

    @Test
    public void pollQueueFifoOrder() {
        AdbDebugBridge b = new AdbDebugBridge();
        b.connect("dev");
        b.pushBreakpoint("A.java", 1, "m");
        b.pushBreakpoint("B.java", 2, "n");
        AdbDebugBridge.DebugEvent first = b.pollEvent();
        AdbDebugBridge.DebugEvent second = b.pollEvent();
        assertNotNull(first);
        assertNotNull(second);
        assertEquals("A.java", first.frame.topFrame().sourcePath);
        assertEquals("B.java", second.frame.topFrame().sourcePath);
    }

    @Test
    public void listenerExceptionDoesNotBreakOthers() {
        AdbDebugBridge b = new AdbDebugBridge();
        b.connect("dev");
        AtomicInteger count = new AtomicInteger();
        b.addListener(e -> { throw new RuntimeException("oops"); });
        b.addListener(e -> count.incrementAndGet());
        b.pushBreakpoint("X.java", 1, "m");
        assertEquals(1, count.get());
    }
}
