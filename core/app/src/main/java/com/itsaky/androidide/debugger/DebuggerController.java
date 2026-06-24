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

import android.text.TextUtils;
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

    /**
     * PR-D4: 目标应用包名(由 DebugSessionLauncher 在 launch 后写入)。
     * 用于 stop() 时通过 am force-stop 终止进程。
     */
    @Nullable private String targetPackage;

    /**
     * PR-D4: 单一后台线程,用于 stop() 中的 force-stop 等不可阻塞 UI
     * 的命令;也用于异步触发 a11y 公告(避免公告过密影响 UI 渲染)。
     */
    private final java.util.concurrent.ExecutorService bg =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "DebuggerController-bg");
                t.setDaemon(true);
                return t;
            });

    /** PR-D4: 暴露 targetPackage 给需要它的 Fragment (例如 VariablesFragment)。 */
    @Nullable public String targetPackage() { return targetPackage; }

    /** PR-D4: 在 launch 成功后由 DebugSessionLauncher 写入。 */
    public void setTargetPackage(@Nullable String pkg) { this.targetPackage = pkg; }

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
            ILogger.ROOT.info("Debugger connected to " + host + ":" + port);
        } catch (Throwable t) {
            ILogger.ROOT.error("Failed to connect to JDWP server: " + t.getMessage(), t);
        }
    }

    public void disconnect() {
        if (debugger == null) return;
        try { debugger.disconnect(); } catch (Throwable ignored) {}
        BreakpointManager.getInstance().bindDebugger(null);
        sessionState.onDisconnected();
        pausedAtThreadId = -1L;
        targetPackage = null;
        DebuggerAccessibility.announceDisconnected(attachedActivity);
    }

    public void resume() {
        if (debugger == null) { flash("未连接调试器"); return; }
        debugger.resume();
    }

    public void pause() {
        if (debugger == null) { flash("未连接调试器"); return; }
        debugger.pause();
    }

    /**
     * PR-D4: 真正"停止"调试 —— 流程:
     *   1. 尝试 resume(让被暂停的线程继续运行,避免 force-stop 后留下
     *      VM 处于挂起态导致 JDWP disconnect 失败)
     *   2. 调用 debugger.disconnect() 关闭 JDWP 客户端
     *   3. 异步在后台线程用 {@code am force-stop <pkg>} 终止目标进程
     *   4. 清空 targetPackage,通知 UI
     * 如果 debugger 还没连接,只清空 targetPackage。
     */
    public void stop() {
        if (debugger == null && TextUtils.isEmpty(targetPackage)) {
            flash("未连接调试器");
            return;
        }
        // 1) 先 resume 让 VM 回到运行态,避免后续 disconnect 因为 suspend policy
        //    而 hang 住。
        try { if (debugger != null) debugger.resume(); } catch (Throwable ignored) {}
        // 2) 关闭 JDWP。
        try { if (debugger != null) debugger.disconnect(); } catch (Throwable ignored) {}
        BreakpointManager.getInstance().bindDebugger(null);
        sessionState.onDisconnected();
        pausedAtThreadId = -1L;
        // 3) 异步 force-stop 目标进程,避免阻塞 UI 线程。
        final String pkg = targetPackage;
        targetPackage = null;
        if (!TextUtils.isEmpty(pkg)) {
            bg.submit(() -> {
                try {
                    Process p = new ProcessBuilder("sh", "-c",
                            "am force-stop " + pkg).redirectErrorStream(true).start();
                    p.waitFor();
                } catch (Throwable t) {
                    ILogger.ROOT.warn(TAG + ": force-stop failed: " + t.getMessage());
                }
            });
        }
        DebuggerAccessibility.announceDisconnected(attachedActivity);
        flash("已停止调试" + (TextUtils.isEmpty(pkg) ? "" : " (" + pkg + ")"));
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
        openFrame(frame);
    }

    public void showCurrentFrame() {
        SuspendInfo info = debugger == null ? null : debugger.lastSuspendInfo();
        if (info == null) { flash("当前没有暂停点"); return; }
        StackFrameInfo frame = (info.frames == null || info.frames.isEmpty()) ? null : info.frames.get(0);
        if (attachedActivity == null) return;
        if (frame == null) {
            attachedActivity.showFlashInfo("线程 " + info.threadId + " 暂停中 (无栈帧信息)");
            return;
        }
        attachedActivity.showFlashInfo(
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

    private boolean requireThread() {
        if (debugger == null) { flash("未连接调试器"); return false; }
        if (pausedAtThreadId <= 0L) { flash("当前没有暂停的线程"); return false; }
        return true;
    }

    private void flash(String msg) {
        if (attachedActivity != null) attachedActivity.showFlashInfo(msg);
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
        DebuggerAccessibility.announceResumed(attachedActivity);
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        if (attachedActivity == null) return;
        if (connected) {
            attachedActivity.showFlashInfo("调试器已连接");
        } else {
            attachedActivity.showFlashInfo("调试器已断开");
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
        // PR-D4: 切到 Variables tab,让用户立刻看到当前帧的变量(以及
        // Call Stack 中的 frame 切换和 Watch 视图的实时求值)。
        if (attachedActivity != null) {
            try {
                attachedActivity.openDebuggerTab(
                    com.itsaky.androidide.debugger.fragment.VariablesFragment.class);
            } catch (Throwable t) {
                // 切 tab 失败不影响调试器本身的工作
            }
        }
        // PR-D4: a11y 公告:如果是断点命中,优先播报断点 id;
        // 其它原因(单步、异常等)播报当前位置。
        if (bp != null) {
            DebuggerAccessibility.announceBreakpointHit(
                    attachedActivity, bp.id, frame.sourceFile, frame.lineNumber);
        } else if (info.reason == SuspendInfo.Reason.EXCEPTION) {
            // 异常挂起:SuspendInfo 没有 exception 对象引用,只有
            // exceptionClassId + exceptionMessage;但有 JDWP refTypeId 也
            // 拿不到类名,所以这里用 description 字段顶上。
            String exName = info.description == null ? "" : info.description;
            DebuggerAccessibility.announceException(
                    attachedActivity, exName,
                    frame.sourceFile, frame.lineNumber);
        } else {
            DebuggerAccessibility.announcePaused(
                    attachedActivity, frame.sourceFile, frame.lineNumber);
        }
    }

    /**
     * PR-D4: 跳转到异常源位置。当前没有源位置时,会退回到第一个栈帧;
     * 若 {@link SuspendInfo} 不是异常挂起,直接走 {@link #gotoCurrentBreakpoint()}。
     */
    public void gotoException() {
        if (debugger == null) { flash("未连接调试器"); return; }
        SuspendInfo info = debugger.lastSuspendInfo();
        if (info == null) { flash("当前没有暂停点"); return; }
        if (info.reason != SuspendInfo.Reason.EXCEPTION) {
            gotoCurrentBreakpoint();
            return;
        }
        StackFrameInfo frame = (info.frames == null || info.frames.isEmpty())
                ? null : info.frames.get(0);
        if (frame == null) { flash("无法定位异常源"); return; }
        openFrame(frame);
    }

    /** PR-D4: 抽出 gotoCurrentBreakpoint / gotoException 复用逻辑。 */
    private void openFrame(@NonNull StackFrameInfo frame) {
        if (attachedActivity == null) return;
        com.itsaky.androidide.models.Range range =
                new com.itsaky.androidide.models.Range(
                        new com.itsaky.androidide.models.Position(frame.lineNumber - 1, 0),
                        new com.itsaky.androidide.models.Position(frame.lineNumber - 1, 0));
        attachedActivity.openFileAndSelect(new File(frame.sourceFile), range);
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
                            attachedActivity.showFlashInfo("已添加监视: " + expr);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
