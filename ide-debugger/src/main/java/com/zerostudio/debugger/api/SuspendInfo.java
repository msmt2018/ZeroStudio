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

    public SuspendInfo(
            long threadId,
            @NonNull Reason reason,
            @NonNull List<StackFrameInfo> frames,
            @NonNull String description) {
        this.threadId = threadId;
        this.reason = reason;
        this.frames = frames;
        this.description = description;
    }

    public static SuspendInfo empty() {
        return new SuspendInfo(0, Reason.UNKNOWN, Collections.emptyList(), "");
    }
}
