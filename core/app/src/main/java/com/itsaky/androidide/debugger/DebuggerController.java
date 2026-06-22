/*
 *  ZeroStudio IDE - 调试器控制器
 *
 *  菜单 / UI 与 com.zerostudio.debugger.api.Debugger 之间的中间层：
 *  - 维护当前调试会话的状态
 *  - 把 runToCursor / stepOver / stepInto / stepOut 等调用翻译成 JDWP 命令
 *  - 监听 DebugSession 状态变化，向 UI 派发事件
 *
 *  PR-3 阶段：实现 UI → 控制器的全部动作；与 ide-debugger 的命令调用
 *  在 connectTo / sendCommand 异常时降级为 noop + flashInfo，避免影响
 *  未连接目标应用时的菜单可用性。
 */

package com.itsaky.androidide.debugger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.debugger.model.LogStore;
import com.itsaky.androidide.ui.CodeEditorView;
import com.itsaky.androidide.utils.ILogger;
import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.api.StackFrameInfo;
import com.zerostudio.debugger.api.SuspendInfo;
import com.zerostudio.debugger.event.DebugEventBus;
import com.zerostudio.debugger.event.DebugEvents;
import com.zerostudio.debugger.model.DebugSession.State;
import java.io.File;

public final class DebuggerController
        implements Debugger.Listener {

    private static final String TAG = "DebuggerController";
    private static final DebuggerController INSTANCE = new DebuggerController();

    public static DebuggerController getInstance() { return INSTANCE; }

    @Nullable private Debugger debugger;
    @Nullable private com.itsaky.androidide.activities.editor.BaseEditorActivity attachedActivity;
    /** JDWP thread id of the currently paused thread (used by stepOver/Into/Out). */
    private long pausedAtThreadId = -1L;

    /** PR-4: shared runtime state for the side panel (call stack, variables, watches). */
    private final DebugSessionState sessionState = new DebugSessionState();

    public DebugSessionState sessionState() { return sessionState; }

    /** PR-5: raw access to the underlying JDWP debugger (used by WatchesFragment). */
    @Nullable
    public com.zerostudio.debugger.api.Debugger debugger() { return debugger; }

    private DebuggerController() {
        BreakpointManager.getInstance().addListener(new BreakpointManager.Listener() {
            @Override
            public void onBreakpointsChanged(@NonNull java.util.List<com.itsaky.androidide.debugger.model.IdeBreakpoint> all) {
                if (attachedActivity != null) {
                    com.itsaky.androidide.debugger.view.BreakpointGutterManager.refreshAll();
                }
            }
        });
    }

    /**
     * PR-6: forward logpoint events from the freshly-created debugger's
     * event bus to the in-process LogStore. Called once per
     * {@link #connect(String, int)} call.
     */
    private void subscribeLogpointBus(@NonNull com.zerostudio.debugger.api.Debugger dbg) {
        dbg.eventBus().subscribe(new DebugEventBus.DebugEventsListener() {
            @Override
            public void onDebugEvent(@NonNull DebugEvents event) {
                if (event.type == DebugEvents.Type.LOGPOINT) {
                    LogStore.getInstance().append(event.sourceFile, event.line, event.message);
                }
            }
        });
    }

    public void attachActivity(@Nullable com.itsaky.androidide.activities.editor.BaseEditorActivity activity) {
        this.attachedActivity = activity;
    }

    /** 连接到目标应用（按 BuildConfig/配置决定 host:port） */
    public void connect(@NonNull String host, int port) {
        try {
            if (debugger == null) {
                debugger = new Debugger();
                debugger.addListener(this);
                subscribeLogpointBus(debugger);
            }
            debugger.connect(host, port);
            BreakpointManager.getInstance().bindDebugger(debugger);
            // 等待 VMStart (PR-2 里有 waitForVmStart，但不要阻塞 UI 线程)
            new Thread(() -> {
                try { debugger.waitForVmStart(30_000L); } catch (Throwable ignored) {}
            }, "jdwp-wait-vmstart").start();
            ILogger.info(TAG, "Debugger connected to " + host + ":" + port);
        } catch (Throwable t) {
            ILogger.error(TAG, "Failed to connect to JDWP server: " + t.getMessage(), t);
        }
    }

    public void disconnect() {
        if (debugger == null) return;
        try { debugger.disconnect(); } catch (Throwable ignored) {}
        BreakpointManager.getInstance().bindDebugger(null);
        sessionState.onDisconnected();
        pausedAtThreadId = -1L;
    }

    public void resume() {
        if (debugger == null) { flash("未连接调试器"); return; }
        debugger.resume();
    }

    public void pause() {
        if (debugger == null) { flash("未连接调试器"); return; }
        debugger.pause();
    }

    public void stop() {
        if (debugger == null) { flash("未连接调试器"); return; }
        // TODO: 终止目标进程
        flash("已请求停止调试");
    }

    public void stepOver() {
        if (!requireThread()) return;
        debugger.stepOver(pausedAtThreadId);
    }

    public void stepInto() {
        if (!requireThread()) return;
        debugger.stepInto(pausedAtThreadId);
    }

    public void stepOut() {
        if (!requireThread()) return;
        debugger.stepOut(pausedAtThreadId);
    }

    public void runToCursor() {
        CodeEditorView view = attachedActivity == null
                ? null
                : attachedActivity.getCurrentEditor();
        if (view == null) { flash("请先打开文件"); return; }
        com.itsaky.androidide.editor.ui.IDEEditor ed = view.getEditor();
        if (ed == null) { flash("请先打开文件"); return; }
        File file = ed.getFile();
        io.github.rosemoe.sora.text.Cursor cursor = ed.getCursor();
        if (file == null || cursor == null) {
            flash("请先把光标放在目标行");
            return;
        }
        int line = cursor.getLeftLine() + 1; // 0-based -> 1-based
        if (debugger == null) { flash("未连接调试器"); return; }
        debugger.runToCursor(BreakpointManager.normalize(file), line);
    }

    public void gotoCurrentBreakpoint() {
        SuspendInfo info = debugger == null ? null : debugger.lastSuspendInfo();
        if (info == null) { flash("当前没有暂停点"); return; }
        StackFrameInfo frame = (info.frames == null || info.frames.isEmpty()) ? null : info.frames.get(0);
        if (frame == null) { flash("当前没有可显示的栈帧"); return; }
        com.itsaky.androidide.models.Range range =
                new com.itsaky.androidide.models.Range(
                        new com.itsaky.androidide.models.Position(frame.lineNumber - 1, 0),
                        new com.itsaky.androidide.models.Position(frame.lineNumber - 1, 0));
        if (attachedActivity != null) {
            attachedActivity.openFileAndSelect(new File(frame.sourceFile), range);
        }
    }

    public void showCurrentFrame() {
        SuspendInfo info = debugger == null ? null : debugger.lastSuspendInfo();
        if (info == null) { flash("当前没有暂停点"); return; }
        StackFrameInfo frame = (info.frames == null || info.frames.isEmpty()) ? null : info.frames.get(0);
        if (attachedActivity == null) return;
        if (frame == null) {
            attachedActivity.flashInfo("线程 " + info.threadId + " 暂停中 (无栈帧信息)");
            return;
        }
        attachedActivity.flashInfo(
                "线程 " + info.threadId + " 暂停于 " + frame.sourceFile
                        + ":" + frame.lineNumber);
    }

    public void toggleDebugConnection() {
        if (debugger == null) {
            connect("127.0.0.1", 5005);
        } else {
            disconnect();
        }
    }

    @Nullable
    public com.zerostudio.debugger.api.Debugger debugger() {
        return debugger;
    }

    @Nullable
    public DebugSession.State sessionState() {
        return debugger == null ? null : debugger.session().getState();
    }

    private boolean requireThread() {
        if (debugger == null) { flash("未连接调试器"); return false; }
        if (pausedAtThreadId <= 0L) { flash("当前没有暂停的线程"); return false; }
        return true;
    }

    private void flash(String msg) {
        if (attachedActivity != null) attachedActivity.flashInfo(msg);
    }

    // -- Debugger.Listener --

    @Override
    public void onBreakpointChanged(@NonNull com.zerostudio.debugger.api.Breakpoint bp) {
        com.itsaky.androidide.debugger.model.IdeBreakpoint ideBp =
                BreakpointManager.getInstance().findByDebuggerId(bp.id);
        if (ideBp == null) return;
        switch (bp.state) {
            case VERIFIED:
                BreakpointManager.getInstance().markVerified(ideBp);
                break;
            case INVALID:
                BreakpointManager.getInstance().markInvalid(ideBp);
                break;
            case PENDING:
            case DISABLED:
            default:
                break;
        }
    }

    @Override
    public void onResumed() {
        pausedAtThreadId = -1L;
        sessionState.onResume();
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        if (attachedActivity == null) return;
        if (connected) {
            attachedActivity.flashInfo("调试器已连接");
        } else {
            attachedActivity.flashInfo("调试器已断开");
        }
    }

    @Override
    public void onSuspend(@NonNull SuspendInfo info) {
        pausedAtThreadId = info.threadId;
        sessionState.onSuspend(info);
        StackFrameInfo frame = (info.frames == null || info.frames.isEmpty()) ? null : info.frames.get(0);
        if (frame == null) return;
        com.itsaky.androidide.debugger.model.IdeBreakpoint bp =
                BreakpointManager.getInstance().findAt(frame.sourceFile, frame.lineNumber);
        if (bp != null) {
            BreakpointManager.getInstance().markHit(bp);
        }
    }

    /** PR-4: switch the current frame (drives Variables / Watches reload). */
    public void selectFrame(long frameId) {
        sessionState.selectFrame(frameId);
    }

    /** PR-4: prompt the user to add a new watch expression. */
    public void promptAddWatch() {
        if (!(attachedActivity instanceof android.app.Activity)) {
            return;
        }
        android.app.Activity act = (android.app.Activity) attachedActivity;
        android.widget.EditText input = new android.widget.EditText(act);
        input.setHint("表达式 (例如 i, list.size())");
        new androidx.appcompat.app.AlertDialog.Builder(act)
                .setTitle("添加监视表达式")
                .setView(input)
                .setPositiveButton("添加", (d, w) -> {
                    String expr = input.getText().toString().trim();
                    if (!expr.isEmpty()) {
                        com.itsaky.androidide.debugger.model.WatchStore.getInstance().add(expr);
                        if (attachedActivity != null) {
                            attachedActivity.flashInfo("已添加监视: " + expr);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
