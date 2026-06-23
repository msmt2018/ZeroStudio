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
import com.itsaky.androidide.models.ApkMetadata;
import com.itsaky.androidide.projects.IProjectManager;
import com.itsaky.androidide.projects.android.AndroidModule;
import com.itsaky.androidide.projects.builder.BuildService;
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult;
import com.itsaky.androidide.tooling.api.models.BasicAndroidVariantMetadata;
import com.itsaky.androidide.utils.ApkInstaller;
import com.itsaky.androidide.utils.InstallationResultHandler;
import com.itsaky.androidide.utils.IntentUtils;
import java.io.File;
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
    }

    public enum Step {
        SELECT_MODULE, BUILD, INSTALL, LAUNCH, RESOLVE_PORT, CONNECT
    }

    private final Context appContext;
    @Nullable private Listener listener;
    @Nullable private Thread worker;

    public DebugSessionLauncher(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

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
        // PR-D2 简化: 多 module 时本工具类无法弹 chooser dialog (那是
        // Kotlin 扩展函数). 退化为只跑工作区中第一个 app module;若需要
        // chooser,PR-D3 可以补一个 Activity-based 的 chooser.
        Iterable<AndroidModule> projects = IProjectManager.getInstance()
                .getWorkspace()
                .androidProjects();
        AndroidModule module = null;
        for (AndroidModule p : projects) {
            if (p.isApplication()) {
                module = p;
                break;
            }
        }
        if (module == null) {
            fail(Step.SELECT_MODULE, "No application modules in workspace");
            return false;
        }
        BasicAndroidVariantMetadata variant = module.getSelectedVariant();
        if (variant == null) {
            fail(Step.SELECT_MODULE, "No build variant selected for " + module.getName());
            return false;
        }
        String taskName = module.getPath() + ":" + variant.getMainArtifact().getAssembleTaskName();
        log.info("DebugSessionLauncher starting task '{}'", taskName);
        fireBuildStarting(module, variant);
        final ActionData snapshot = data;
        worker = new Thread(() -> runBuild(snapshot, module, variant, taskName),
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
            result = buildService.executeTasks(taskName).get();
        } catch (Throwable t) {
            fail(Step.BUILD, "Build threw: " + t.getMessage());
            return;
        }
        if (result == null || !result.isSuccessful()) {
            fail(Step.BUILD, "Build task failed: " + taskName);
            return;
        }

        ApkMetadata apkMeta = ApkMetadata.findApkFile(
                variant.getMainArtifact().getAssembleTaskOutputListingFile());
        if (apkMeta == null || !apkMeta.exists()) {
            fail(Step.BUILD, "APK file not found for variant " + variant.getName());
            return;
        }
        File apk = apkMeta;
        fireInstallStarting(apk);
        runInstall(data, module, variant, apk);
    }

    @WorkerThread
    private void runInstall(@NonNull ActionData data,
                            @NonNull AndroidModule module,
                            @NonNull BasicAndroidVariantMetadata variant,
                            @NonNull File apk) {
        com.itsaky.androidide.activities.editor.EditorHandlerActivity activity =
                (com.itsaky.androidide.activities.editor.EditorHandlerActivity) data.get(android.content.Context.class);
        if (activity == null) {
            fail(Step.INSTALL, "Editor activity no longer available");
            return;
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

        String pkg = variant.getMainArtifact().getApplicationId();
        if (pkg == null || pkg.isEmpty()) {
            fail(Step.LAUNCH, "applicationId is null for variant " + variant.getName());
            return;
        }
        runLaunch(pkg);
    }

    @WorkerThread
    private void runLaunch(@NonNull String packageName) {
        final boolean[] ok = new boolean[]{false};
        try {
            // runOnUiThread blocks until the message is posted; we then poll a flag.
            // Since IntentUtils.launchApp is synchronous on the UI thread,
            // we wrap it in a CountDownLatch to wait for completion.
            final java.util.concurrent.CountDownLatch latch =
                    new java.util.concurrent.CountDownLatch(1);
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> {
                try {
                    ok[0] = IntentUtils.launchApp(appContext, packageName, false);
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
        JdwpPortResolver resolver = new JdwpPortResolver(appContext);
        try {
            int port = resolver.awaitJdwpPort(
                    packageName,
                    JdwpPortResolver.DEFAULT_TIMEOUT_MS,
                    JdwpPortResolver.DEFAULT_POLL_INTERVAL_MS);
            if (port <= 0) {
                fail(Step.RESOLVE_PORT,
                        "Timed out waiting for JDWP port of " + packageName);
                return;
            }
            runConnect("127.0.0.1", port);
        } finally {
            resolver.shutdown();
        }
    }

    @WorkerThread
    private void runConnect(@NonNull String host, int port) {
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
