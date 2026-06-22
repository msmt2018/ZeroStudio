/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Top-level facade for the debugger engine. Consumers (the IDE's UI
 *  layer) interact with the engine exclusively through this class.
 *
 *  The facade owns the {@link JdwpClient}, the {@link DebugSession}
 *  state machine, the {@link BreakpointStore} and the
 *  {@link DebugEventBus}. It also implements the
 *  {@link JdwpClient.EventListener} and
 *  {@link JdwpClient.ConnectionListener} interfaces so that it can react
 *  to events from the JDWP server.
 *
 *  Lifecycle:
 *
 *    Debugger d = new Debugger();
 *    d.listener = ...;
 *    d.connect("127.0.0.1", 5005);
 *    d.waitForVmStart();
 *    d.addBreakpoint(file, line);
 *    d.resume();
 *    ...
 *    d.disconnect();
 *
 *  All public methods are thread-safe.
 */

package com.zerostudio.debugger.api;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.debugger.event.DebugEventBus;
import com.zerostudio.debugger.event.DebugEvents;
import com.zerostudio.debugger.jdwp.JdwpClient;
import com.zerostudio.debugger.jdwp.JdwpPacket;
import com.zerostudio.debugger.model.BreakpointStore;
import com.zerostudio.debugger.model.DebugSession;
import com.zerostudio.debugger.model.EvalEngine;
import com.zerostudio.debugger.model.SourceLocator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class Debugger implements JdwpClient.EventListener, JdwpClient.ConnectionListener {

    private static final String TAG = "Debugger";

    private final JdwpClient client = new JdwpClient();
    private final DebugSession session = new DebugSession();
    private final BreakpointStore breakpoints = new BreakpointStore();
    private final SourceLocator sourceLocator = new SourceLocator(this);
    private final EvalEngine eval = new EvalEngine(this);
    private final DebugEventBus eventBus = new DebugEventBus();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong breakpointIdGen = new AtomicLong(0L);
    private final AtomicBoolean vmStartReceived = new AtomicBoolean(false);
    @Nullable private volatile SuspendInfo lastSuspend = null;

    public Debugger() {
        client.addEventListener(this);
        client.addConnectionListener(this);
    }

    /** Register a high-level listener. */
    public void addListener(@NonNull Listener l) {
        listeners.addIfAbsent(l);
    }

    public void removeListener(@NonNull Listener l) {
        listeners.remove(l);
    }

    public DebugEventBus eventBus() { return eventBus; }
    public BreakpointStore breakpoints() { return breakpoints; }
    public DebugSession session() { return session; }
    public SourceLocator sourceLocator() { return sourceLocator; }
    public EvalEngine eval() { return eval; }
    public JdwpClient client() { return client; }

    /**
     * Open a connection to the JDWP server running inside the target
     * application. Blocks until the handshake completes.
     */
    public void connect(@NonNull String host, int port) throws java.io.IOException {
        client.connect(host, port);
    }

    /** Block until the VMStart event has been received. */
    public void waitForVmStart(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (vmStartReceived) {
            while (!vmStartReceived.get()) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new InterruptedException("VMStart timeout");
                }
                vmStartReceived.wait(remaining);
            }
        }
    }

    /**
     * Add a breakpoint at the given source file and line. Returns the
     * assigned breakpoint id; the breakpoint's state will become
     * [Breakpoint.State.VERIFIED] once the JDWP server acknowledges the
     * request, or [Breakpoint.State.INVALID] if the line cannot be
     * resolved.
     */
    public long addBreakpoint(@NonNull String sourceFile, int line) {
        return addBreakpoint(sourceFile, line, null);
    }

    public long addBreakpoint(
            @NonNull String sourceFile, int line, @Nullable String condition) {
        long id = breakpointIdGen.incrementAndGet();
        Breakpoint bp = new Breakpoint(id, sourceFile, line, condition);
        breakpoints.add(bp);
        try {
            sourceLocator.installBreakpoint(bp);
        } catch (java.io.IOException ioe) {
            Log.w(TAG, "Failed to install breakpoint", ioe);
            bp.state = Breakpoint.State.INVALID;
        }
        notifyBreakpointChanged(bp);
        return id;
    }

    public void removeBreakpoint(long id) {
        Breakpoint bp = breakpoints.get(id);
        if (bp == null) return;
        if (bp.requestId > 0) {
            try {
                sourceLocator.uninstallBreakpoint(bp);
            } catch (java.io.IOException ioe) {
                Log.w(TAG, "Failed to clear breakpoint", ioe);
            }
        }
        breakpoints.remove(id);
        notifyBreakpointChanged(bp);
    }

    public void disableBreakpoint(long id) {
        Breakpoint bp = breakpoints.get(id);
        if (bp == null) return;
        bp.state = Breakpoint.State.DISABLED;
        notifyBreakpointChanged(bp);
    }

    public void enableBreakpoint(long id) {
        Breakpoint bp = breakpoints.get(id);
        if (bp == null || bp.state != Breakpoint.State.DISABLED) return;
        bp.state = Breakpoint.State.PENDING;
        try {
            sourceLocator.installBreakpoint(bp);
        } catch (java.io.IOException ioe) {
            Log.w(TAG, "Failed to re-enable breakpoint", ioe);
        }
        notifyBreakpointChanged(bp);
    }

    /** Resume the target program. */
    public void resume() {
        try {
            sourceLocator.resumeAll();
        } catch (java.io.IOException e) {
            Log.w(TAG, "resume failed", e);
        }
        session.setState(DebugSession.State.RUNNING);
        lastSuspend = null;
        notifyResumed();
    }

    /** Pause the target program. */
    public void pause() {
        try {
            sourceLocator.suspendAll();
        } catch (java.io.IOException e) {
            Log.w(TAG, "pause failed", e);
        }
        session.setState(DebugSession.State.SUSPENDED);
    }

    /** Step over the current frame. */
    public void stepOver(long threadId) {
        try {
            sourceLocator.step(threadId, com.zerostudio.debugger.jdwp.StepDepth.OVER,
                    com.zerostudio.debugger.jdwp.StepSize.LINE);
            session.setState(DebugSession.State.STEPPING);
        } catch (java.io.IOException e) {
            Log.w(TAG, "stepOver failed", e);
        }
    }

    public void stepInto(long threadId) {
        try {
            sourceLocator.step(threadId, com.zerostudio.debugger.jdwp.StepDepth.INTO,
                    com.zerostudio.debugger.jdwp.StepSize.LINE);
            session.setState(DebugSession.State.STEPPING);
        } catch (java.io.IOException e) {
            Log.w(TAG, "stepInto failed", e);
        }
    }

    public void stepOut(long threadId) {
        try {
            sourceLocator.step(threadId, com.zerostudio.debugger.jdwp.StepDepth.OUT,
                    com.zerostudio.debugger.jdwp.StepSize.LINE);
            session.setState(DebugSession.State.STEPPING);
        } catch (java.io.IOException e) {
            Log.w(TAG, "stepOut failed", e);
        }
    }

    public void runToCursor(@NonNull String sourceFile, int line) {
        // Implemented as a one-shot breakpoint + resume; the BreakpointStore
        // is asked to clean up after the breakpoint is hit.
        long id = addBreakpoint(sourceFile, line);
        breakpoints.setOneShot(id, true);
        resume();
    }

    /** List current stack frames for the given thread. */
    @NonNull
    public List<StackFrameInfo> getStackFrames(long threadId, int start, int length) {
        try {
            return sourceLocator.getStackFrames(threadId, start, length);
        } catch (java.io.IOException e) {
            Log.w(TAG, "getStackFrames failed", e);
            return java.util.Collections.emptyList();
        }
    }

    @Nullable
    public SuspendInfo lastSuspendInfo() {
        return lastSuspend;
    }

    public void disconnect() {
        client.close();
        session.setState(DebugSession.State.DISCONNECTED);
    }

    // -- JdwpClient.EventListener --

    @Override
    public void onEvent(@NonNull JdwpPacket packet) {
        try {
            eventBus.dispatch(packet, this);
        } catch (Throwable t) {
            Log.w(TAG, "event dispatch failed", t);
        }
    }

    // -- JdwpClient.ConnectionListener --

    @Override
    public void onConnected() {
        session.setState(DebugSession.State.CONNECTED);
        notifyConnectionChanged(true);
    }

    @Override
    public void onDisconnected() {
        session.setState(DebugSession.State.DISCONNECTED);
        notifyConnectionChanged(false);
    }

    // -- internal hooks for the event bus --

    void onVmStart() {
        synchronized (vmStartReceived) {
            vmStartReceived.set(true);
            vmStartReceived.notifyAll();
        }
        session.setState(DebugSession.State.RUNNING);
        eventBus.publish(DebugEvents.vmStart());
    }

    void onSuspend(@NonNull SuspendInfo info) {
        lastSuspend = info;
        session.setState(DebugSession.State.SUSPENDED);
        // Clean up one-shot breakpoints.
        breakpoints.removeOneShots(info);
        eventBus.publish(DebugEvents.suspend(info));
        // Also notify high-level listeners for backward compatibility.
        for (Listener l : listeners) {
            try { l.onSuspend(info); } catch (Throwable ignored) { }
        }
    }

    void onResume() {
        session.setState(DebugSession.State.RUNNING);
        lastSuspend = null;
        eventBus.publish(DebugEvents.resume());
        for (Listener l : listeners) {
            try { l.onResumed(); } catch (Throwable ignored) { }
        }
    }

    void notifyBreakpointChanged(@NonNull Breakpoint bp) {
        for (Listener l : listeners) {
            try { l.onBreakpointChanged(bp); } catch (Throwable ignored) { }
        }
    }

    private void notifyResumed() {
        for (Listener l : listeners) {
            try { l.onResumed(); } catch (Throwable ignored) { }
        }
    }

    private void notifyConnectionChanged(boolean connected) {
        for (Listener l : listeners) {
            try { l.onConnectionChanged(connected); } catch (Throwable ignored) { }
        }
    }

    /** High-level listener for the IDE's UI layer. */
    public interface Listener {
        default void onBreakpointChanged(@NonNull Breakpoint bp) {}
        default void onResumed() {}
        default void onConnectionChanged(boolean connected) {}
        /** Called whenever the target program is suspended (breakpoint, step, etc.). */
        default void onSuspend(@NonNull SuspendInfo info) {}
    }
}
