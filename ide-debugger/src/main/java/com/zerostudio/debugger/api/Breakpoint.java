/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  A user-set breakpoint. The IDE owns one of these per source line; the
 *  engine converts them to JDWP EventRequest.Set commands.
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

    public final long id;
    @NonNull public final String sourceFile;
    public final int line;
    @Nullable public final String condition;
    public volatile State state = State.PENDING;
    /** JDWP request id (filled in after the server acknowledges). */
    public volatile int requestId = -1;

    public Breakpoint(
            long id,
            @NonNull String sourceFile,
            int line,
            @Nullable String condition) {
        this.id = id;
        this.sourceFile = sourceFile;
        this.line = line;
        this.condition = condition;
    }

    public boolean isVerified() {
        return state == State.VERIFIED;
    }

    @Override
    public String toString() {
        return "Breakpoint{id=" + id
                + ", file=" + sourceFile
                + ", line=" + line
                + ", state=" + state
                + ", requestId=" + requestId
                + ", condition=" + condition
                + '}';
    }
}
