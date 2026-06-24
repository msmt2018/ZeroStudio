/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  A user-set breakpoint. The IDE owns one of these per source line; the
 *  engine converts them to JDWP EventRequest.Set commands.
 *
 *  Phase E2: supports a hit count + condition expression. The hit count
 *  mode controls how the count is interpreted:
 *
 *    - ALWAYS         - suspend on every hit (no count modifier emitted)
 *    - EQUAL          - suspend only when hit count == N
 *    - GREATER_THAN   - suspend only when hit count > N
 *    - MULTIPLE       - suspend every Nth hit
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Breakpoint {

    /** Lifecycle states of a breakpoint. */
    public enum State {
        /** Set by the user; not yet sent to the JDWP server. */
        PENDING,
        /** Sent to the JDWP server and acknowledged. */
        VERIFIED,
        /** The server rejected the request (e.g. line not found). */
        INVALID,
        /** User disabled the breakpoint. */
        DISABLED
    }

    /**
     * Hit count policy. Mapped to the JDWP {@code Count} modifier
     * (kind 1). When the policy is {@link #ALWAYS} the count value
     * is ignored and no modifier is emitted.
     */
    public enum HitCountMode {
        ALWAYS,
        EQUAL,
        GREATER_THAN,
        MULTIPLE
    }

    public final long id;
    @NonNull public final String sourceFile;
    public final int line;
    /**
     * Optional hit condition. If non-null, the debugger evaluates this
     * expression in the breakpoint frame and only suspends when the result
     * is non-zero / non-null / true. Evaluated client-side in
     * {@code Debugger.onSuspend}.
     */
    @Nullable public final String condition;
    /**
     * Optional log message expression (e.g. {@code "x=" + x}). If non-null,
     * the breakpoint acts as a logpoint: the value is appended to the IDE
     * log and the VM is resumed without ever pausing.
     */
    @Nullable public final String logMessage;
    /** Hit count policy; defaults to {@link HitCountMode#ALWAYS}. */
    @NonNull public volatile HitCountMode hitCountMode = HitCountMode.ALWAYS;
    /** Hit count threshold; only meaningful when mode != ALWAYS. */
    public volatile int hitCount = 0;
    public volatile State state = State.PENDING;
    /** JDWP request id (filled in after the server acknowledges). */
    public volatile int requestId = -1;

    public Breakpoint(
            long id,
            @NonNull String sourceFile,
            int line,
            @Nullable String condition) {
        this(id, sourceFile, line, condition, null, HitCountMode.ALWAYS, 0);
    }

    public Breakpoint(
            long id,
            @NonNull String sourceFile,
            int line,
            @Nullable String condition,
            @Nullable String logMessage) {
        this(id, sourceFile, line, condition, logMessage, HitCountMode.ALWAYS, 0);
    }

    public Breakpoint(
            long id,
            @NonNull String sourceFile,
            int line,
            @Nullable String condition,
            @Nullable String logMessage,
            @NonNull HitCountMode hitCountMode,
            int hitCount) {
        this.id = id;
        this.sourceFile = sourceFile;
        this.line = line;
        this.condition = condition;
        this.logMessage = logMessage;
        this.hitCountMode = hitCountMode == null ? HitCountMode.ALWAYS : hitCountMode;
        this.hitCount = hitCount;
    }

    public boolean isVerified() {
        return state == State.VERIFIED;
    }

    public boolean isLogpoint() {
        return logMessage != null && !logMessage.isEmpty();
    }

    public boolean isConditional() {
        return condition != null && !condition.isEmpty();
    }

    /** Whether the breakpoint should emit a Count modifier to JDWP. */
    public boolean hasHitCountFilter() {
        return hitCountMode != HitCountMode.ALWAYS && hitCount > 0;
    }

    @Override
    public String toString() {
        return "Breakpoint{id=" + id
                + ", file=" + sourceFile
                + ", line=" + line
                + ", state=" + state
                + ", requestId=" + requestId
                + ", condition=" + condition
                + ", logMessage=" + logMessage
                + ", hitCount=" + hitCountMode + ":" + hitCount
                + '}';
    }
}
