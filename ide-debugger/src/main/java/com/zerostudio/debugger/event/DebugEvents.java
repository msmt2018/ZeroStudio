/*
 *  ZeroStudio IDE - ide-debugger
 */

package com.zerostudio.debugger.event;

import androidx.annotation.NonNull;
import com.zerostudio.debugger.api.SuspendInfo;

public final class DebugEvents {

    public enum Type { VM_START, VM_DEATH, SUSPEND, RESUME, BREAKPOINT_CHANGED, THREAD_START, THREAD_DEATH, CLASS_PREPARE }

    public final Type type;
    @NonNull public final SuspendInfo suspend;
    @NonNull public final String message;

    private DebugEvents(@NonNull Type type, @NonNull SuspendInfo suspend, @NonNull String message) {
        this.type = type;
        this.suspend = suspend;
        this.message = message;
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

    public static DebugEvents of(@NonNull Type type, @NonNull String message) {
        return new DebugEvents(type, SuspendInfo.empty(), message);
    }
}
