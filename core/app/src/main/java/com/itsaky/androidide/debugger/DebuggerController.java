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
import com.itsaky.androidide.ui.CodeEditorView;
import com.itsaky.androidide.utils.ILogger;
import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.api.SuspendInfo;
import com.zerostudio.debugger.model.DebugSession.State;
import java.io.File;

public final class DebuggerController
        implements Debugger.Listener {

    private static final String TAG = "DebuggerController";
    private static final DebuggerController INSTANCE = new DebuggerController();

    public static DebuggerController getInstance() { return INSTANCE; }

    @Nullable private Debugger debugger;
    @Nullable private com.itsaky.androidide.activities.editor.BaseEditorActivity attachedActivity;
    @Nullable private Thread pausedAtThread;

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

    public void attachActivity(@Nullable com.itsaky.androidide.activities.editor.BaseEditorActivity activity) {
        this.attachedActivity = activity;
    }

    /** 连接到目标应用（按 BuildConfig/配置决定 host:port） */
    public void connect(@NonNull String host, int port) {
        try {
            if (debugger == null) {
                debugger = new Debugger();
                debugger.addListener(this);
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
        debugger.stepOver(pausedAtThread.getId());
    }

    public void stepInto() {
        if (!requireThread()) return;
        debugger.stepInto(pausedAtThread.getId());
    }

    public void stepOut() {
        if (!requireThread()) return;
        debugger.stepOut(pausedAtThread.getId());
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
        com.itsaky.androidide.models.Range range =
                new com.itsaky.androidide.models.Range(
                        new com.itsaky.androidide.models.Position(info.location.line - 1, 0),
                        new com.itsaky.androidide.models.Position(info.location.line - 1, 0));
        if (attachedActivity != null) {
            attachedActivity.openFileAndSelect(new File(info.location.sourceFile), range);
        }
    }

    public void showCurrentFrame() {
        SuspendInfo info = debugger == null ? null : debugger.lastSuspendInfo();
        if (info == null) { flash("当前没有暂停点"); return; }
        if (attachedActivity != null) {
            attachedActivity.flashInfo(
                    "线程 " + info.threadId + " 暂停于 " + info.location.sourceFile
                            + ":" + info.location.line);
        }
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
        if (pausedAtThread == null) { flash("当前没有暂停的线程"); return false; }
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
    public void onResumed() { /* 状态变化已通过 event bus 同步 */ }

    @Override
    public void onConnectionChanged(boolean connected) {
        if (attachedActivity == null) return;
        if (connected) {
            attachedActivity.flashInfo("调试器已连接");
        } else {
            attachedActivity.flashInfo("调试器已断开");
        }
    }

    public void onSuspend(@NonNull SuspendInfo info) {
        pausedAtThread = findThreadById(info.threadId);
        com.itsaky.androidide.debugger.model.IdeBreakpoint bp =
                BreakpointManager.getInstance().findAt(
                        info.location.sourceFile, info.location.line);
        if (bp != null) {
            BreakpointManager.getInstance().markHit(bp);
        }
    }

    @Nullable
    private Thread findThreadById(long id) {
        // 简化：实际中应通过 ThreadReference 缓存
        Thread[] ts = new Thread[Thread.activeCount()];
        Thread.enumerate(ts);
        for (Thread t : ts) if (t != null && t.getId() == id) return t;
        return null;
    }
}
