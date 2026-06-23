/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Snapshot of a stopped thread, sent to the UI when the debugger halts
 *  the target program.
 */

package com.zerostudio.debugger.api;

import androidx.annotation.NonNull;
import java.util.Collections;
import java.util.List;

public final class SuspendInfo {

    public enum Reason {
        BREAKPOINT,
        STEP,
        EXCEPTION,
        USER_REQUEST,
        VM_START,
        SINGLE_STEP,
        FRAME_POP,
        UNKNOWN
    }

    public final long threadId;
    @NonNull public final Reason reason;
    @NonNull public final List<StackFrameInfo> frames;
    /** Optional payload (e.g. the exception class+message for EXCEPTION). */
    @NonNull public final String description;
    /**
     * Phase B2: when {@link #reason} is {@link Reason#EXCEPTION}, this
     * carries the JDWP refTypeId of the thrown exception class. Zero
     * (the default) means "no exception attached".
     */
    public final long exceptionClassId;
    /**
     * Phase B2: optional message of the exception (typically
     * {@code Throwable.getMessage()}). May be null when the exception
     * has no message.
     */
    @NonNull public final String exceptionMessage;
    /**
     * Phase B2: true if the exception was caught by a surrounding
     * try/catch; false if it propagated uncaught to the top of the
     * thread. For non-EXCEPTION suspends this is always false.
     */
    public final boolean exceptionCaught;

    public SuspendInfo(
            long threadId,
            @NonNull Reason reason,
            @NonNull List<StackFrameInfo> frames,
            @NonNull String description) {
        this(threadId, reason, frames, description, 0L, "", false);
    }

    /**
     * Phase B2: full constructor carrying the exception context
     * (class refTypeId, message, caught-vs-uncaught). Use the simpler
     * four-arg constructor for non-EXCEPTION suspends.
     */
    public SuspendInfo(
            long threadId,
            @NonNull Reason reason,
            @NonNull List<StackFrameInfo> frames,
            @NonNull String description,
            long exceptionClassId,
            @NonNull String exceptionMessage,
            boolean exceptionCaught) {
        this.threadId = threadId;
        this.reason = reason;
        this.frames = frames;
        this.description = description;
        this.exceptionClassId = exceptionClassId;
        this.exceptionMessage = exceptionMessage == null ? "" : exceptionMessage;
        this.exceptionCaught = exceptionCaught;
    }

    public static SuspendInfo empty() {
        return new SuspendInfo(0, Reason.UNKNOWN, Collections.emptyList(), "");
    }
}
