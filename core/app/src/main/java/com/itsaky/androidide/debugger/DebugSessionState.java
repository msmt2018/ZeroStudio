/*
 *  ZeroStudio IDE - 调试会话运行时状态
 *
 *  PR-4: 跟踪当前暂停的线程、当前栈帧、整条调用栈。
 *  由 DebuggerController 在 onSuspend / onResumed / onFrameSelected 时维护，
 *  并向 UI 监听器广播。
 *
 *  与 ide-debugger 的关系：状态是这份代码的 source of truth；
 *  实际数据（线程 / 帧）由 ide-debugger 持有，本类只做镜像。
 */

package com.itsaky.androidide.debugger;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.debugger.api.StackFrameInfo;
import com.zerostudio.debugger.api.SuspendInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DebugSessionState {

    public interface Listener {
        /** 暂停/恢复/帧切换都会触发。 */
        default void onStateChanged(@NonNull DebugSessionState state) {}
    }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private long pausedThreadId = -1L;
    private long currentFrameId = -1L;
    @Nullable private SuspendInfo lastSuspend;
    @NonNull private List<StackFrameInfo> frames = Collections.emptyList();
    private boolean suspended;

    public void addListener(@NonNull Listener l) { listeners.addIfAbsent(l); }
    public void removeListener(@NonNull Listener l) { listeners.remove(l); }

    public long pausedThreadId() { return pausedThreadId; }
    public long currentFrameId() { return currentFrameId; }
    @Nullable public SuspendInfo lastSuspend() { return lastSuspend; }
    @NonNull public List<StackFrameInfo> frames() { return frames; }
    public boolean isSuspended() { return suspended; }

    @Nullable
    public StackFrameInfo currentFrame() {
        if (currentFrameId <= 0L) return null;
        for (StackFrameInfo f : frames) {
            if (f.frameId == currentFrameId) return f;
        }
        return frames.isEmpty() ? null : frames.get(0);
    }

    @MainThread
    public void onSuspend(@NonNull SuspendInfo info) {
        this.lastSuspend = info;
        this.pausedThreadId = info.threadId;
        this.frames = info.frames == null
                ? Collections.emptyList()
                : new ArrayList<>(info.frames);
        this.currentFrameId = this.frames.isEmpty() ? -1L : this.frames.get(0).frameId;
        this.suspended = true;
        fire();
    }

    @MainThread
    public void onResume() {
        this.lastSuspend = null;
        this.pausedThreadId = -1L;
        this.currentFrameId = -1L;
        this.frames = Collections.emptyList();
        this.suspended = false;
        fire();
    }

    @MainThread
    public void onDisconnected() {
        onResume();
    }

    @MainThread
    public void selectFrame(long frameId) {
        for (StackFrameInfo f : frames) {
            if (f.frameId == frameId) {
                this.currentFrameId = frameId;
                fire();
                return;
            }
        }
    }

    private void fire() {
        DebugSessionState snap = snapshot();
        for (Listener l : listeners) {
            try { l.onStateChanged(snap); } catch (Throwable ignored) {}
        }
    }

    @NonNull
    public DebugSessionState snapshot() { return this; }
}
