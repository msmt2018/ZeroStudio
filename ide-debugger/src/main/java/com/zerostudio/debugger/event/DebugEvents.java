/*
 *  ZeroStudio IDE - ide-debugger
 */

package com.zerostudio.debugger.event;

import androidx.annotation.NonNull;
import com.zerostudio.debugger.api.Breakpoint;
import com.zerostudio.debugger.api.SuspendInfo;

public final class DebugEvents {

    public enum Type { VM_START, VM_DEATH, SUSPEND, RESUME, BREAKPOINT_CHANGED, THREAD_START, THREAD_DEATH, CLASS_PREPARE, LOGPOINT }

    public final Type type;
    @NonNull public final SuspendInfo suspend;
    @NonNull public final String message;
    /** Source file of the breakpoint that produced this event, if any. */
    @androidx.annotation.Nullable public final String sourceFile;
    public final int line;

    private DebugEvents(@NonNull Type type, @NonNull SuspendInfo suspend, @NonNull String message) {
        this(type, suspend, message, null, -1);
    }

    private DebugEvents(
            @NonNull Type type,
            @NonNull SuspendInfo suspend,
            @NonNull String message,
            @androidx.annotation.Nullable String sourceFile,
            int line) {
        this.type = type;
        this.suspend = suspend;
        this.message = message;
        this.sourceFile = sourceFile;
        this.line = line;
    }

    public static DebugEvents vmStart() {
        return new DebugEvents(Type.VM_START, SuspendInfo.empty(), "VM started");
    }

    public static DebugEvents suspend(@NonNull SuspendInfo info) {
        return new DebugEvents(Type.SUSPEND, info, info.description);
    }

    public static DebugEvents resume() {
        return new DebugEvents(Type.RESUME, SuspendInfo.empty(), "Resumed");
    }

    /**
     * Construct a LOGPOINT event. The IDE's LogFragment listens to the
     * bus and appends {@code text} to the buffer. {@code bp} is just used
     * for source-location metadata.
     */
    public static DebugEvents logpoint(@NonNull Breakpoint bp, @NonNull String text) {
        return new DebugEvents(Type.LOGPOINT, SuspendInfo.empty(), text, bp.sourceFile, bp.line);
    }

    public static DebugEvents of(@NonNull Type type, @NonNull String message) {
        return new DebugEvents(type, SuspendInfo.empty(), message);
    }
}
