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

    // Fields are no longer initialised inline so the package-private
    // constructor below can inject a fake JdwpClient for unit testing.
    private final JdwpClient client;
    private final DebugSession session;
    private final BreakpointStore breakpoints;
    private final SourceLocator sourceLocator;
    private final EvalEngine eval;
    private final DebugEventBus eventBus;
    private final CopyOnWriteArrayList<Listener> listeners;
    private final AtomicLong breakpointIdGen;
    private final AtomicBoolean vmStartReceived;
    @Nullable private volatile SuspendInfo lastSuspend;

    public Debugger() {
        this(new JdwpClient());
    }

    /**
     * Package-private constructor used by the unit tests to inject a fake
     * {@link JdwpClient} (see {@code FakeJdwpClient} in src/test). The
     * production code path always goes through the no-arg constructor.
     */
    Debugger(@NonNull JdwpClient client) {
        this.client = client;
        this.session = new DebugSession();
        this.breakpoints = new BreakpointStore();
        this.sourceLocator = new SourceLocator(this);
        this.eval = new EvalEngine(this);
        this.eventBus = new DebugEventBus();
        this.listeners = new CopyOnWriteArrayList<>();
        this.breakpointIdGen = new AtomicLong(0L);
        this.vmStartReceived = new AtomicBoolean(false);
        this.lastSuspend = null;
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
     * Look up a single local variable by name in the current frame. Used
     * by the expression evaluator to resolve an identifier before it can
     * be stringified. Returns null if no such local exists.
     */
    @Nullable
    public VariableInfo fetchLocal(long threadId, long frameId, @NonNull String name) {
        try {
            return sourceLocator.fetchLocal(threadId, frameId, name);
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    /**
     * Open a connection to the JDWP server running inside the target
     * application. Blocks until the handshake completes.
     */
    public void connect(@NonNull String host, int port) throws java.io.IOException {
        eventBus.bindClient(client); // Phase B6: enable re-subscribe on reconnect.
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
        return addBreakpoint(sourceFile, line, null, null);
    }

    public long addBreakpoint(
            @NonNull String sourceFile, int line, @Nullable String condition) {
        return addBreakpoint(sourceFile, line, condition, null);
    }

    public long addBreakpoint(
            @NonNull String sourceFile,
            int line,
            @Nullable String condition,
            @Nullable String logMessage) {
        long id = breakpointIdGen.incrementAndGet();
        Breakpoint bp = new Breakpoint(id, sourceFile, line, condition, logMessage);
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

    /**
     * Phase B6: called after the JdwpClient has reconnected to the
     * JDWP server. We re-install every breakpoint in
     * {@link BreakpointStore} and re-subscribe to events that the
     * server has forgotten about (CLASS_PREPARE, BREAKPOINT,
     * EXCEPTION, etc.).
     *
     * Re-installation is a best-effort retry: a JDWP error reply is
     * logged but does not throw.
     */
    @Override
    public void onReconnected() {
        session.setState(DebugSession.State.CONNECTED);
        notifyConnectionChanged(true);
        try {
            resubscribeAndReinstall();
        } catch (Throwable t) {
            Log.w(TAG, "reconnect hook failed", t);
        }
    }

    private void resubscribeAndReinstall() {
        // 1. Reinstall all breakpoints.
        for (Breakpoint bp : breakpoints.all()) {
            try {
                sourceLocator.installBreakpoint(bp);
            } catch (Throwable t) {
                Log.w(TAG, "reinstall bp " + bp.id + " failed", t);
            }
        }
        // 2. Re-subscribe to CLASS_PREPARE so late-loaded classes get
        //    their pending breakpoints installed.
        try {
            eventBus.subscribeClassPrepare();
        } catch (Throwable t) {
            Log.w(TAG, "re-subscribe CLASS_PREPARE failed", t);
        }
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
        // PR-6: handle conditional breakpoints and logpoints BEFORE
        // notifying the UI. A logpoint never pauses the program; a
        // conditional breakpoint only pauses if the condition evaluates
        // truthy.
        if (info.reason == SuspendInfo.Reason.BREAKPOINT
                && info.frames != null && !info.frames.isEmpty()) {
            StackFrameInfo top = info.frames.get(0);
            Breakpoint bp = breakpoints.findByLocation(top.sourceFile, top.lineNumber);
            if (bp != null) {
                if (bp.isLogpoint()) {
                    handleLogpoint(bp, info, top);
                    return;
                }
                if (bp.isConditional()) {
                    if (!handleCondition(bp, info, top)) {
                        return; // condition false -> don't suspend
                    }
                }
            }
        }
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

    /**
     * Evaluate a logpoint expression in the top frame and publish the
     * resulting message on the log bus. Resumes the VM without ever
     * notifying the UI about a suspend.
     */
    private void handleLogpoint(
            @NonNull Breakpoint bp,
            @NonNull SuspendInfo info,
            @NonNull StackFrameInfo top) {
        EvalResult r = eval.evaluate(top.threadId, top.frameId, bp.logMessage);
        String text = r.isError() || r.displayValue == null
                ? "(eval error: " + r.error + ")"
                : r.displayValue;
        eventBus.publish(DebugEvents.logpoint(bp, text));
        // Resume immediately.
        try { sourceLocator.resumeAll(); } catch (java.io.IOException ignored) {}
        session.setState(DebugSession.State.RUNNING);
        lastSuspend = null;
    }

    /**
     * Evaluate a conditional breakpoint. Returns true if the program should
     * suspend (condition truthy or evaluation failed), false if it should
     * silently resume.
     */
    private boolean handleCondition(
            @NonNull Breakpoint bp,
            @NonNull SuspendInfo info,
            @NonNull StackFrameInfo top) {
        EvalResult r = eval.evaluate(top.threadId, top.frameId, bp.condition);
        if (r.isError()) {
            // Don't silently skip - if the user wrote a bad condition,
            // pause so they can see it.
            return true;
        }
        if (isTruthy(r)) return true;
        // Condition is false: silently resume.
        try { sourceLocator.resumeAll(); } catch (java.io.IOException ignored) {}
        session.setState(DebugSession.State.RUNNING);
        lastSuspend = null;
        return false;
    }

    // Package-private so the unit tests can drive the truthiness rules
    // directly. Production callers go through handleCondition().
    static boolean isTruthy(@NonNull EvalResult r) {
        if (r.displayValue == null) return true; // unknown -> suspend
        switch (r.tag) {
            case BOOLEAN: return r.displayValue.equals("true");
            case INT: case LONG: case SHORT: case BYTE: case CHAR:
                try { return Long.parseLong(r.displayValue) != 0L; }
                catch (NumberFormatException e) { return true; }
            case FLOAT: case DOUBLE:
                try { return Double.parseDouble(r.displayValue) != 0.0; }
                catch (NumberFormatException e) { return true; }
            case OBJECT: case ARRAY: case STRING:
                // null reference -> don't suspend; everything else -> suspend
                return r.objectId != 0L;
            default:
                return true;
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

    /**
     * Phase B1: callback invoked by the {@link com.zerostudio.debugger.event.DebugEventBus}
     * whenever a CLASS_PREPARE event arrives. We hand the
     * freshly-loaded class off to the SourceLocator so it can retry
     * any pending breakpoints that were waiting for this class.
     *
     * @param classId the refTypeId of the freshly-prepared class
     * @param classSignature the JVM type signature (e.g. {@code Lcom/example/Foo;})
     * @param sourceFile the class's source-file attribute (may be null)
     */
    public void onClassPrepare(long classId, @NonNull String classSignature,
                                @Nullable String sourceFile) {
        if (sourceLocator == null) return;
        sourceLocator.retryPending(classId, sourceFile);
    }

    /**
     * Phase B2: read the message of a thrown exception object via
     * JDWP {@code StringReference.Value} (command set 10, command 1).
     * Returns an empty string if the object isn't a string or the
     * call fails. The call is best-effort: an exception in a debugger
     * is a debugger bug, not a user error, so we never throw out of
     * this method.
     */
    @NonNull
    public String fetchExceptionMessage(long exceptionObjectId, byte objectTag) {
        return readString(exceptionObjectId);
    }

    /**
     * Phase B4: general purpose read of a {@code java.lang.String}
     * object via JDWP {@code StringReference.Value} (command set 10,
     * command 1). Returns an empty string if the object id is null
     * or the call fails. The {@code objectTag} parameter is
     * accepted for symmetry with [fetchExceptionMessage] but is
     * not used; the JDWP {@code StringReference} command set is
     * always used because the string-id resolution is independent
     * of the receiver tag.
     */
    @NonNull
    public String readString(long stringObjectId) {
        if (stringObjectId == 0L) return "";
        try {
            com.zerostudio.debugger.util.ByteBuf buf = new com.zerostudio.debugger.util.ByteBuf();
            buf.writeLong(stringObjectId);
            com.zerostudio.debugger.jdwp.JdwpPacket reply = client.sendCommand(
                    com.zerostudio.debugger.jdwp.CommandSet.StringReference,
                    com.zerostudio.debugger.jdwp.CommandCodes.StringReferenceCmd.Value,
                    buf.toByteArray());
            if (reply.errorCode() != 0) return "";
            com.zerostudio.debugger.util.ByteBuf in = new com.zerostudio.debugger.util.ByteBuf(reply.data);
            return in.readString();
        } catch (java.io.IOException ex) {
            return "";
        }
    }

    /**
     * Phase B4: create a string in the target VM and return its
     * string-id via JDWP {@code VirtualMachine.CreateString}
     * (command set 1, command 11). Returns 0 if the call fails;
     * the string-id can then be passed to [readString] or used as
     * an argument to a method invocation.
     */
    public long createString(@NonNull String value) {
        try {
            com.zerostudio.debugger.util.ByteBuf buf = new com.zerostudio.debugger.util.ByteBuf();
            buf.writeString(value);
            com.zerostudio.debugger.jdwp.JdwpPacket reply = client.sendCommand(
                    com.zerostudio.debugger.jdwp.CommandSet.VirtualMachine,
                    com.zerostudio.debugger.jdwp.CommandCodes.VirtualMachineCmd.CreateString,
                    buf.toByteArray());
            if (reply.errorCode() != 0) return 0L;
            com.zerostudio.debugger.util.ByteBuf in = new com.zerostudio.debugger.util.ByteBuf(reply.data);
            return in.readLong();
        } catch (java.io.IOException ex) {
            return 0L;
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
