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
import com.itsaky.androidide.utils.FlashbarActivityUtilsKt;
import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.api.StackFrameInfo;
import com.zerostudio.debugger.api.SuspendInfo;
import com.zerostudio.debugger.event.DebugEventBus;
import com.zerostudio.debugger.event.DebugEvents;
import com.zerostudio.debugger.model.DebugSession.State;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /** PR-D6: 被调试的应用包名,由 DebugSessionLauncher 写入。stop() 据此 force-stop。 */
    @Nullable private volatile String targetPackage;

    /**
     * PR-D6: 异步执行器,用于把 stop() / wait-for-install / JDWP port probe
     * 等阻塞操作从调用线程 (可能是 UI 线程) 切走。
     * 单独的 daemon executor,不与 IDE 其它异步任务共享。
     */
    private final ExecutorService bgExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "DebuggerController-bg");
                t.setDaemon(true);
                return t;
            });

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

    /** PR-D6: 写入目标包。DebugSessionLauncher 在 launch 成功后调用。 */
    public void setTargetPackage(@Nullable String pkg) {
        this.targetPackage = pkg;
    }

    /** PR-D6: 取目标包 (供其它模块使用)。 */
    @Nullable
    public String getTargetPackage() {
        return targetPackage;
    }

    /** PR-D6: 后台执行器,供 DebugSessionLauncher 切走阻塞 IO。 */
    @NonNull
    public ExecutorService bgExecutor() {
        return bgExecutor;
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
            ILogger.ROOT.info(TAG + ": " + "Debugger connected to " + host + ":" + port);
        } catch (Throwable t) {
            ILogger.ROOT.error(TAG + ": " + "Failed to connect to JDWP server: " + t.getMessage(), t);
        }
    }

    public void disconnect() {
        if (debugger == null) return;
        try { debugger.disconnect(); } catch (Throwable ignored) {}
        BreakpointManager.getInstance().bindDebugger(null);
        sessionState.onDisconnected();
        pausedAtThreadId = -1L;
        announceDisconnected();
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
     * PR-D6: 真正实现 stop():先 JDWP Resume 让目标进程从暂停态出来,
     * 然后用 {@code am force-stop} 终止目标包,最后断 JDWP。
     * 不阻塞调用线程 (通常是 UI 线程)。
     */
    public void stop() {
        if (debugger == null && (targetPackage == null || targetPackage.isEmpty())) {
            flash("未连接调试器");
            return;
        }
        final String pkg = targetPackage;
        bgExecutor().execute(() -> {
            // 1) 尝试恢复目标线程,避免 force-stop 时堆栈卡在暂停态。
            try {
                if (debugger != null) debugger.resume();
            } catch (Throwable ignored) {}
            // 2) force-stop 目标包。
            boolean forceStopped = false;
            if (pkg != null && !pkg.isEmpty()) {
                forceStopped = forceStopPackage(pkg);
            }
            // 3) 断开 JDWP。
            try {
                if (debugger != null) debugger.disconnect();
            } catch (Throwable ignored) {}
            // 4) 通知 UI。
            final boolean ok = forceStopped;
            postMain(() -> {
                if (attachedActivity != null) {
                    if (ok && pkg != null) {
                        FlashbarActivityUtilsKt.flashInfo(attachedActivity, attachedActivity.getString(
                                com.itsaky.androidide.R.string.debugger_stop_ok, pkg));
                    } else if (pkg == null || pkg.isEmpty()) {
                        FlashbarActivityUtilsKt.flashInfo(attachedActivity, attachedActivity.getString(
                                com.itsaky.androidide.R.string.debugger_stop_no_target));
                    } else {
                        FlashbarActivityUtilsKt.flashInfo(attachedActivity, attachedActivity.getString(
                                com.itsaky.androidide.R.string.debugger_stop_failed,
                                "force-stop exit != 0"));
                    }
                }
                // 重置本地状态
                debugger = null;
                BreakpointManager.getInstance().bindDebugger(null);
                sessionState.onDisconnected();
                pausedAtThreadId = -1L;
                // targetPackage 不清,用户可能紧接着再次启动调试;
                // 若想强制清,可在 connect() 时再覆盖。
            });
        });
    }

    /**
     * PR-D6: 用 {@code am force-stop <pkg>} 终止目标包。
     * 先通过反射尝试隐藏 PackageManager API,避免直接引用 hidden API
     * 导致 javac 找不到符号;失败时回退到 shell。
     */
    private static boolean forceStopPackage(@NonNull String pkg) {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object pm = activityThread.getMethod("getPackageManager").invoke(null);
            if (pm != null) {
                Class<?> userHandle = Class.forName("android.os.UserHandle");
                int userId = (Integer) userHandle.getMethod("getCallingUserId").invoke(null);
                pm.getClass().getMethod("forceStopPackage", String.class, int.class)
                        .invoke(pm, pkg, userId);
                return true;
            }
        } catch (Throwable ignored) {
            // Hidden API unavailable: fall back to shell below.
        }

        try {
            Process p = new ProcessBuilder("am", "force-stop", pkg)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Throwable t) {
            ILogger.ROOT.error(TAG + ": " + "force-stop failed for " + pkg + ": " + t.getMessage(), t);
            return false;
        }
    }

    private static void postMain(@NonNull Runnable r) {
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(r);
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

    /**
     * PR-D7: 跳转到异常抛出处。
     *
     * <p>当前实现策略:在栈帧列表中找第一个 sourceFile 命名空间看起来是
     * 用户应用代码的 (即 frame 来自用户包名,而不是 java.* / android.* /
     * kotlin.* / dalvik.* 等系统栈);找不到时回退到栈顶 (与
     * [gotoCurrentBreakpoint] 行为一致)。
     *
     * <p>真正的"异常位置"需要 JDWP ExceptionEvent 携带的 throwLocation
     * 字段,目前 [com.zerostudio.debugger.api.SuspendInfo] 没暴露这字段,
     * 等 ide-debugger 那边补上后,这里会直接使用 throwLocation。
     */
    public void gotoException() {
        SuspendInfo info = debugger == null ? null : debugger.lastSuspendInfo();
        if (info == null) { flash("当前没有暂停点"); return; }
        if (info.frames == null || info.frames.isEmpty()) {
            flash("当前没有可显示的栈帧");
            return;
        }
        StackFrameInfo target = findUserCodeFrame(info.frames);
        if (target == null) {
            target = info.frames.get(0); // fallback: 栈顶
        }
        com.itsaky.androidide.models.Range range =
                new com.itsaky.androidide.models.Range(
                        new com.itsaky.androidide.models.Position(target.lineNumber - 1, 0),
                        new com.itsaky.androidide.models.Position(target.lineNumber - 1, 0));
        if (attachedActivity != null) {
            attachedActivity.openFileAndSelect(new File(target.sourceFile), range);
        }
    }

    /** 跳过 java.*/android.*/kotlin.*/dalvik.* 等系统栈,找第一帧用户代码。 */
    @Nullable
    private static StackFrameInfo findUserCodeFrame(@NonNull List<StackFrameInfo> frames) {
        String userPkg = DebuggerController.getInstance().getTargetPackage();
        for (StackFrameInfo f : frames) {
            if (f == null || f.sourceFile == null) continue;
            String sf = f.sourceFile;
            if (sf.startsWith("java/") || sf.startsWith("javax/")
                    || sf.startsWith("android/") || sf.startsWith("androidx/")
                    || sf.startsWith("kotlin/") || sf.startsWith("kotlinx/")
                    || sf.startsWith("dalvik/") || sf.startsWith("com/android/")) {
                continue;
            }
            if (userPkg != null && !userPkg.isEmpty()) {
                String pkgPath = userPkg.replace('.', '/');
                if (sf.contains(pkgPath)) return f;
            } else {
                // 没目标包信息,只根据"非系统栈"判断
                return f;
            }
        }
        return null;
    }

    public void showCurrentFrame() {
        SuspendInfo info = debugger == null ? null : debugger.lastSuspendInfo();
        if (info == null) { flash("当前没有暂停点"); return; }
        StackFrameInfo frame = (info.frames == null || info.frames.isEmpty()) ? null : info.frames.get(0);
        if (attachedActivity == null) return;
        if (frame == null) {
            FlashbarActivityUtilsKt.flashInfo(attachedActivity, "线程 " + info.threadId + " 暂停中 (无栈帧信息)");
            return;
        }
        FlashbarActivityUtilsKt.flashInfo(attachedActivity,
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
    public State currentDebuggerState() {
        return debugger == null ? null : debugger.session().getState();
    }

    private boolean requireThread() {
        if (debugger == null) { flash("未连接调试器"); return false; }
        if (pausedAtThreadId <= 0L) { flash("当前没有暂停的线程"); return false; }
        return true;
    }

    private void flash(String msg) {
        if (attachedActivity != null) FlashbarActivityUtilsKt.flashInfo(attachedActivity, msg);
    }

    // ------- PR-D7: 4 事件 a11y announce + haptics -------

    /** 取 attachedActivity 的 root view 作为 announce anchor。无 attach 时返回 null。 */
    @Nullable
    private android.view.View a11yAnchor() {
        if (attachedActivity == null) return null;
        try {
            return attachedActivity.findViewById(android.R.id.content);
        } catch (Throwable t) {
            return null;
        }
    }

    private void announceConnected() {
        final android.view.View v = a11yAnchor();
        if (v == null || attachedActivity == null) return;
        try {
            DebuggerAccessibility.announceConnected(v, attachedActivity);
        } catch (Throwable ignored) {}
    }

    private void announceDisconnected() {
        final android.view.View v = a11yAnchor();
        if (v == null || attachedActivity == null) return;
        try {
            DebuggerAccessibility.announceDisconnected(v, attachedActivity);
        } catch (Throwable ignored) {}
    }

    private void announceResumed() {
        final android.view.View v = a11yAnchor();
        if (v == null || attachedActivity == null) return;
        try {
            DebuggerAccessibility.announceResumed(v, attachedActivity);
        } catch (Throwable ignored) {}
        DebuggerHaptics.onResumed(attachedActivity);
    }

    private void announcePaused(@NonNull String file, int line) {
        final android.view.View v = a11yAnchor();
        if (v == null || attachedActivity == null) return;
        try {
            DebuggerAccessibility.announcePaused(v, attachedActivity, file, line);
        } catch (Throwable ignored) {}
        DebuggerHaptics.onPaused(attachedActivity);
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
        announceResumed();
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        if (attachedActivity == null) return;
        if (connected) {
            FlashbarActivityUtilsKt.flashInfo(attachedActivity, "调试器已连接");
            announceConnected();
        } else {
            FlashbarActivityUtilsKt.flashInfo(attachedActivity, "调试器已断开");
            announceDisconnected();
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
        announcePaused(frame.sourceFile, frame.lineNumber);
        autoOpenDebuggerTab();
    }

    /**
     * PR-D7: 命中断点 / 暂停线程时自动把底部 sheet 切到调试 tab 并半展开。
     * 这样用户能直接看到 Logpoint 输出 / Variables 等上下文,不必手点。
     * 静默失败:任何异常都不能阻塞调试主流程。
     */
    private void autoOpenDebuggerTab() {
        if (!(attachedActivity instanceof com.itsaky.androidide.activities.editor.BaseEditorActivity)) return;
        final com.itsaky.androidide.activities.editor.BaseEditorActivity bea =
                (com.itsaky.androidide.activities.editor.BaseEditorActivity) attachedActivity;
        try {
            bea.openDebuggerTab(com.itsaky.androidide.debugger.fragment.LogpointFragment.class);
        } catch (Throwable t) {
            ILogger.ROOT.warn(TAG + ": " + "autoOpenDebuggerTab failed: " + t.getMessage());
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
                            FlashbarActivityUtilsKt.flashInfo(attachedActivity, "已添加监视: " + expr);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
