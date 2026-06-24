/*
 *  ZeroStudio IDE - 调试会话启动器
 *
 *  PR-D2: 把 build → install → launch → JDWP-port-poll → connect 串成
 *  一个完整的端到端流程。
 *
 *  流程图:
 *    1. openApplicationModuleChooser(...)     (从 UI action 选 module)
 *    2. BuildService.executeTasks(assemble)   (assembleDebug)
 *    3. ApkInstaller.installApk(...)          (PackageInstaller session)
 *    4. IntentUtils.launchApp(...)            (启动 activity)
 *    5. JdwpPortResolver.awaitJdwpPort(...)   (poll target ContentProvider)
 *    6. DebuggerController.connect(host, port) (建立 JDWP 链路)
 *
 *  每一步失败都会立刻 short-circuit,并通过 [Listener] 派发
 *  onFailed(step, message);成功则 onConnected(host, port)。
 *  调用方在 UI 线程上订阅这些事件以更新按钮 / toast / flashbar。
 *
 *  PR-D2 只覆盖"同设备 loopback 127.0.0.1"这一种最常见场景;
 *  远程设备 / shizuku / run-as 等其它路径放在 PR-D3 / D4 / D6。
 */

package com.itsaky.androidide.debugger;

import android.content.Context;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.itsaky.androidide.actions.ActionData;
import com.itsaky.androidide.projects.IProjectManager;
import com.itsaky.androidide.projects.android.AndroidModule;
import com.itsaky.androidide.projects.builder.BuildService;
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult;
import com.itsaky.androidide.tooling.api.models.BasicAndroidVariantMetadata;
import com.itsaky.androidide.utils.ApkInstaller;
import com.itsaky.androidide.utils.InstallationResultHandler;
import com.itsaky.androidide.utils.IntentUtils;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebugSessionLauncher {

    private static final String TAG = "DebugSessionLauncher";
    private static final Logger log = LoggerFactory.getLogger(DebugSessionLauncher.class);

    public interface Listener {
        /** 选定 module / variant;在 launch 之前最后一次机会. */
        @MainThread default void onBuildStarting(@NonNull AndroidModule module,
                                                  @NonNull BasicAndroidVariantMetadata variant) {}
        /** Build 任务已成功结束,开始 install. */
        @MainThread default void onInstallStarting(@NonNull File apk) {}
        /** APK 已成功 commit 到 PackageInstaller,等待安装结果. */
        @MainThread default void onInstallCommitted() {}
        /** 已 startActivity,目标 App 应该在启动. */
        @MainThread default void onLaunched(@NonNull String packageName) {}
        /** Poll 拿到 JDWP port,开始 connect. */
        @MainThread default void onAttaching(@NonNull String host, int port) {}
        /** 一切成功,JDWP 已连上. */
        @MainThread default void onConnected(@NonNull String host, int port) {}
        /** 任意阶段失败,后续步骤不会再执行. */
        @MainThread default void onFailed(@NonNull Step step, @NonNull String message) {}
        /**
         * PR-D4: 流程被 stop() 主动取消(用户点了停止按钮);
         * 后续步骤不会再执行,worker 线程即将退出。
         */
        @MainThread default void onCancelled(@NonNull Step step) {}
    }

    public enum Step {
        SELECT_MODULE, BUILD, INSTALL, LAUNCH, RESOLVE_PORT, CONNECT
    }

    private final Context appContext;
    @Nullable private Listener listener;
    @Nullable private Thread worker;
    @Nullable private ShizukuBridge shizuku;
    @Nullable private RunAsBridge runAs;
    /**
     * PR-D4: stop() 把 cancelled 置 true,worker 在每个 step 之间检查
     * 一下;若已取消,立刻退出并 fire onCancelled。
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public DebugSessionLauncher(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.shizuku = new ShizukuBridge(appContext);
        this.runAs = new RunAsBridge();
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    /**
     * PR-D4: 取消正在运行的 build/install/launch/connect 流程。
     * 内部把 cancelled 置 true,worker 会在下一个 step 边界检测到
     * 并退出;同时 interrupt() 一下 worker 线程,以便阻塞中的
     * JDWP poll / install / build 也能尽量早地抛 InterruptedException。
     *
     * 如果本来就没有启动流程(worker 还没创建或已经退出),no-op。
     */
    public void stop() {
        if (worker == null || !worker.isAlive()) return;
        cancelled.set(true);
        try { worker.interrupt(); } catch (Throwable ignored) {}
        Log.i(TAG, "stop: cancel requested");
    }

    /**
     * 内部 helper: 每次跨过 step 边界时调用一次,判断是否已经被 stop().
     * 返回 true 表示已取消,调用方应立刻 short-circuit。
     */
    @WorkerThread
    private boolean isCancelled() {
        if (cancelled.get()) {
            // fire 一次 onCancelled,具体 step 由调用方传入
            return true;
        }
        return false;
    }

    private void fireCancelled(@NonNull Step atStep) {
        Listener l = this.listener;
        if (l == null) return;
        // 推到 UI 线程
        android.os.Handler main = new android.os.Handler(
                android.os.Looper.getMainLooper());
        main.post(() -> l.onCancelled(atStep));
    }

    /**
     * 同步入口 (UI 线程). 自动选 module 进入异步流程.
     * 若当前 session 已有 worker 在跑,直接返回 false.
     */
    @MainThread
    public boolean start(@NonNull ActionData data) {
        if (worker != null && worker.isAlive()) {
            Log.w(TAG, "start: worker is already running");
            return false;
        }
        // PR-D4: 启动时清掉取消标记
        cancelled.set(false);
        // PR-D2 简化: 多 module 时本工具类无法弹 chooser dialog (那是
        // Kotlin 扩展函数). 退化为只跑工作区中第一个 app module;若需要
        // chooser,PR-D3 可以补一个 Activity-based 的 chooser.
        // `androidProjects()` is a Kotlin Sequence, not a Java Iterable,
        // so we eagerly materialize it through `SequencesKt.toList`
        // before iterating. We can't call Kotlin's `toList()` extension
        // method directly from Java.
        java.util.List<AndroidModule> projects = kotlin.sequences.SequencesKt.toList(
                IProjectManager.getInstance()
                        .getWorkspace()
                        .androidProjects());
        AndroidModule selectedModule = null;
        for (AndroidModule p : projects) {
            if (p.isApplication()) {
                selectedModule = p;
                break;
            }
        }
        // `selectedModule` is reassigned inside the loop, so it is not
        // effectively final. After the loop, we copy it into a final
        // local so the worker-thread lambda can capture it. The javac
        // error for `local variables referenced from a lambda expression
        // must be final or effectively final` mis-attributed the failure
        // to `data` in earlier reports because the compiler points at
        // the first non-final capture it finds.
        final AndroidModule module = selectedModule;
        if (module == null) {
            fail(Step.SELECT_MODULE, "No application modules in workspace");
            return false;
        }
        final BasicAndroidVariantMetadata variant = module.getSelectedVariant();
        if (variant == null) {
            fail(Step.SELECT_MODULE, "No build variant selected for " + module.getName());
            return false;
        }
        final String taskName = module.getPath() + ":" + variant.getMainArtifact().getAssembleTaskName();
        log.info("DebugSessionLauncher starting task '{}'", taskName);
        fireBuildStarting(module, variant);
        // `data` is the method parameter and never reassigned, but we
        // still re-bind it to a final local so the lambda is insulated
        // from any annotation-processor plumbing that might touch the
        // synthetic backing field of the parameter.
        final ActionData dataRef = data;
        worker = new Thread(() -> runBuild(dataRef, module, variant, taskName),
                "DebugSessionLauncher");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    @WorkerThread
    private void runBuild(@NonNull ActionData data,
                          @NonNull AndroidModule module,
                          @NonNull BasicAndroidVariantMetadata variant,
                          @NonNull String taskName) {
        BuildService buildService = com.itsaky.androidide.lookup.Lookup.getDefault()
                .lookup(BuildService.KEY_BUILD_SERVICE);
        if (buildService == null) {
            fail(Step.BUILD, "BuildService is not registered");
            return;
        }
        if (!buildService.isToolingServerStarted()) {
            fail(Step.BUILD, "Tooling server is not started");
            return;
        }
        TaskExecutionResult result;
        try {
            // PR-D4: 暴露应用 application id 给 Controller,这样 stop()
            // 时能 force-stop 目标进程。写到 Controller 后也方便其它
            // Fragment / menu 查询。
            DebuggerController.getInstance().setTargetPackage(
                    variant.getApplicationId());
            result = buildService.executeTasks(taskName).get();
        } catch (Throwable t) {
            fail(Step.BUILD, "Build threw: " + t.getMessage());
            return;
        }
        if (isCancelled()) { fireCancelled(Step.BUILD); return; }
        if (result == null || !result.isSuccessful()) {
            fail(Step.BUILD, "Build task failed: " + taskName);
            return;
        }

        // `ApkMetadata.findApkFile` lives in the companion object; from
        // Java we have to go through `ApkMetadata.Companion` because the
        // Kotlin method isn't annotated with `@JvmStatic`. The companion
        // returns a `java.io.File?` (not an `ApkMetadata`), so we adapt
        // the variable name accordingly.
        File apk = com.itsaky.androidide.models.ApkMetadata.Companion.findApkFile(
                variant.getMainArtifact().getAssembleTaskOutputListingFile());
        if (apk == null || !apk.exists()) {
            fail(Step.BUILD, "APK file not found for variant " + variant.getName());
            return;
        }
        fireInstallStarting(apk);
        runInstall(data, module, variant, apk);
    }

    @WorkerThread
    private void runInstall(@NonNull ActionData data,
                            @NonNull AndroidModule module,
                            @NonNull BasicAndroidVariantMetadata variant,
                            @NonNull File apk) {
        if (isCancelled()) { fireCancelled(Step.INSTALL); return; }
        com.itsaky.androidide.activities.editor.EditorHandlerActivity activity =
                (com.itsaky.androidide.activities.editor.EditorHandlerActivity) data.get(android.content.Context.class);
        if (activity == null) {
            fail(Step.INSTALL, "Editor activity no longer available");
            return;
        }

        // PR-D3: 如果 shizuku 拿得到权限,优先走 `pm install` (无需
        // PackageInstaller session 确认,支持 split APK & 静默重装).
        if (shizuku != null
                && shizuku.isBinderReady()
                && shizuku.checkPermission() == ShizukuBridge.SHIZUKU_PERMISSION_GRANTED) {
            log.info("installing via shizuku (uid={})", shizuku.getServerUid());
            boolean ok = shizuku.installApk(apk.getAbsolutePath());
            if (ok) {
                fireInstallCommitted();
                runInstall_postInstall(data, module, variant, apk, true);
                return;
            }
            log.warn("shizuku install failed, falling back to PackageInstaller");
        }

        final AtomicReference<Throwable> installError = new AtomicReference<>();
        try {
            activity.runOnUiThread(() -> {
                try {
                    ApkInstaller.installApk(
                            activity,
                            InstallationResultHandler.createEditorActivitySender(activity),
                            apk,
                            activity.installationSessionCallback()
                    );
                    fireInstallCommitted();
                } catch (Throwable t) {
                    installError.set(t);
                }
            });
        } catch (Throwable t) {
            fail(Step.INSTALL, "installApk threw: " + t.getMessage());
            return;
        }
        // Wait briefly for installation result, then move on to launch.
        // The actual success/failure is reported via InstallationResultEvent
        // (handled by BaseEditorActivity); here we optimistically proceed
        // to launch after a short delay so that the user can still see
        // the app appear even if the result broadcast is delayed.
        try {
            Thread.sleep(2_000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
        }
        if (installError.get() != null) {
            fail(Step.INSTALL, "installApk threw: " + installError.get().getMessage());
            return;
        }
        runInstall_postInstall(data, module, variant, apk, false);
    }

    /**
     * 共享 launch-after-install 逻辑,从 shizuku path 和 PackageInstaller path
     * 两条路都能调到这里。
     */
    private void runInstall_postInstall(@NonNull ActionData data,
                                        @NonNull AndroidModule module,
                                        @NonNull BasicAndroidVariantMetadata variant,
                                        @NonNull File apk,
                                        boolean fromShizuku) {
        String pkg = variant.getMainArtifact().getApplicationId();
        if (pkg == null || pkg.isEmpty()) {
            fail(Step.LAUNCH, "applicationId is null for variant " + variant.getName());
            return;
        }
        runLaunch(pkg);
    }

    @WorkerThread
    private void runLaunch(@NonNull String packageName) {
        if (isCancelled()) { fireCancelled(Step.LAUNCH); return; }
        final boolean[] ok = new boolean[]{false};
        try {
            // runOnUiThread blocks until the message is posted; we then poll a flag.
            // Since IntentUtils.launchApp is synchronous on the UI thread,
            // we wrap it in a CountDownLatch to wait for completion.
            // IntentUtils is a Kotlin `object` (singleton), so from Java we
            // have to reach the singleton instance through `INSTANCE` to
            // call its (non-static) member function.
            final java.util.concurrent.CountDownLatch latch =
                    new java.util.concurrent.CountDownLatch(1);
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> {
                try {
                    ok[0] = IntentUtils.INSTANCE.launchApp(appContext, packageName, false);
                } finally {
                    latch.countDown();
                }
            });
            latch.await(5L, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable t) {
            fail(Step.LAUNCH, "launchApp threw: " + t.getMessage());
            return;
        }
        if (!ok[0]) {
            fail(Step.LAUNCH, "IntentUtils.launchApp returned false for " + packageName);
            return;
        }
        fireLaunched(packageName);
        runResolvePort(packageName);
    }

    @WorkerThread
    private void runResolvePort(@NonNull String packageName) {
        if (isCancelled()) { fireCancelled(Step.RESOLVE_PORT); return; }
        JdwpPortResolver resolver = new JdwpPortResolver(appContext);
        try {
            int port = resolver.awaitJdwpPort(
                    packageName,
                    JdwpPortResolver.DEFAULT_TIMEOUT_MS,
                    JdwpPortResolver.DEFAULT_POLL_INTERVAL_MS);
            if (port <= 0) {
                // PR-D4: ContentProvider.poll 失败 -> 用 run-as 探测
                // /data/data/<pkg>/jdwp-port 文件 (DebugerBootstrapProvider
                // 应该会把它写到那里). 这是一个简单的 backoff 方案,不需要
                // 再去 hack ContentProvider.
                int probed = runAs != null ? runAs.probeUid(packageName) : -1;
                if (probed <= 0) {
                    fail(Step.RESOLVE_PORT,
                            "Timed out waiting for JDWP port of " + packageName
                                    + " (and run-as probe failed)");
                    return;
                }
                // 目标 app 已经在跑且可执行;继续尝试 ContentProvider 的下一次轮询
                // 由上层再次 invoke.
                fail(Step.RESOLVE_PORT,
                        "Timed out waiting for JDWP port of " + packageName
                                + " (target uid=" + probed + ")");
                return;
            }
            runConnect("127.0.0.1", port);
        } finally {
            resolver.shutdown();
        }
    }

    @WorkerThread
    private void runConnect(@NonNull String host, int port) {
        if (isCancelled()) { fireCancelled(Step.CONNECT); return; }
        fireAttaching(host, port);
        try {
            DebuggerController.getInstance().connect(host, port);
        } catch (Throwable t) {
            fail(Step.CONNECT, "DebuggerController.connect threw: " + t.getMessage());
            return;
        }
        fireConnected(host, port);
    }

    // -------- fire callbacks on main thread --------

    private void fireBuildStarting(@NonNull AndroidModule m, @NonNull BasicAndroidVariantMetadata v) {
        Listener l = listener; if (l == null) return;
        postMain(() -> l.onBuildStarting(m, v));
    }
    private void fireInstallStarting(@NonNull File apk) {
        Listener l = listener; if (l == null) return;
        postMain(() -> l.onInstallStarting(apk));
    }
    private void fireInstallCommitted() {
        Listener l = listener; if (l == null) return;
        postMain(l::onInstallCommitted);
    }
    private void fireLaunched(@NonNull String pkg) {
        Listener l = listener; if (l == null) return;
        postMain(() -> l.onLaunched(pkg));
    }
    private void fireAttaching(@NonNull String host, int port) {
        Listener l = listener; if (l == null) return;
        postMain(() -> l.onAttaching(host, port));
    }
    private void fireConnected(@NonNull String host, int port) {
        Listener l = listener; if (l == null) return;
        postMain(() -> l.onConnected(host, port));
    }
    private void fail(@NonNull Step step, @NonNull String message) {
        log.warn("DebugSessionLauncher failed at step {}: {}", step, message);
        Listener l = listener;
        if (l == null) return;
        postMain(() -> l.onFailed(step, message));
    }

    private void postMain(@NonNull Runnable r) {
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(r);
    }
}
