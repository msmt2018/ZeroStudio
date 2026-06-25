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
 *
 *  PR-D6: 重写 runInstall 等待逻辑 (Thread.sleep → EventBus latch)、
 *  runResolvePort 失败时通过 Listener 显式 flash、launch 成功时
 *  把 targetPackage 写入 DebuggerController 以便 stop() 真正 force-stop。
 */

package com.itsaky.androidide.debugger;

import android.content.Context;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.itsaky.androidide.actions.ActionData;
import com.itsaky.androidide.events.InstallationResultEvent;
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
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebugSessionLauncher {

    private static final String TAG = "DebugSessionLauncher";
    private static final Logger log = LoggerFactory.getLogger(DebugSessionLauncher.class);

    /** PR-D6: 等待安装结果最长 60 秒 (给冷启动 + 大 APK 留出余量)。 */
    private static final long INSTALL_RESULT_TIMEOUT_MS = 60_000L;

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
    /** PR-D6: 用 AtomicBoolean 防止两个 start() 同时跑。 */
    private final java.util.concurrent.atomic.AtomicBoolean busy =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /**
     * PR-D7: cancelled 标志,stop() 设置后,后续每一步的入口处都会先检查它。
     * - 配合 busy 一起使用:busy 表示"正在跑",cancelled 表示"应该立刻停"。
     * - 与 DebuggerController.stop() 区分:这里只中止 build/install/launch
     *   流程,不动 JDWP 端。
     */
    private final java.util.concurrent.atomic.AtomicBoolean cancelled =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /** PR-D7: 取消时调用的 runnable,用于打断 install latch.await 等阻塞。 */
    @Nullable private volatile java.util.concurrent.Future<?> currentTask;
    @Nullable private ShizukuBridge shizuku;
    @Nullable private RunAsBridge runAs;

    public DebugSessionLauncher(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.shizuku = new ShizukuBridge(appContext);
        this.runAs = new RunAsBridge();
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    /**
     * PR-D7: 主动取消正在进行的 build / install / launch 流程。
     * 已经 install 完并 startActivity 的不影响,会继续走完;在那之前的所有
     * 步骤会因 cancelled=true 而 short-circuit。
     *
     * <p>可重入,可在多次 start()/stop() 之间循环使用。
     */
    public void stop() {
        cancelled.set(true);
        java.util.concurrent.Future<?> f = currentTask;
        if (f != null) {
            f.cancel(true);  // mayInterruptIfRunning=true
        }
        log.info("DebugSessionLauncher.stop() called by user");
    }

    /**
     * 同步入口 (UI 线程). 自动选 module 进入异步流程.
     * 若当前 session 已有 worker 在跑,直接返回 false.
     */
    @MainThread
    public boolean start(@NonNull ActionData data) {
        if (!busy.compareAndSet(false, true)) {
            Log.w(TAG, "start: another session is already running");
            return false;
        }
        // PR-D7: 每次新 start() 都重置取消标志 (上一次 stop() 可能把它置为 true)。
        cancelled.set(false);
        // PR-D2 简化: 多 module 时本工具类无法弹 chooser dialog (那是
        // Kotlin 扩展函数). 退化为只跑工作区中第一个 app module;若需要
        // chooser,PR-D3 可以补一个 Activity-based 的 chooser.
        Iterator<AndroidModule> projects = IProjectManager.getInstance()
                .getWorkspace()
                .androidProjects()
                .iterator();
        AndroidModule module = null;
        while (projects.hasNext()) {
            AndroidModule p = projects.next();
            if (p.isApplication()) {
                module = p;
                break;
            }
        }
        if (module == null) {
            busy.set(false);  // PR-D6: 释放 busy 标记,允许下次 start()
            fail(Step.SELECT_MODULE, "No application modules in workspace");
            return false;
        }
        BasicAndroidVariantMetadata variant = module.getSelectedVariant();
        if (variant == null) {
            busy.set(false);
            fail(Step.SELECT_MODULE, "No build variant selected for " + module.getName());
            return false;
        }
        final AndroidModule selectedModule = module;
        final BasicAndroidVariantMetadata selectedVariant = variant;
        final String taskName = selectedModule.getPath() + ":"
                + selectedVariant.getMainArtifact().getAssembleTaskName();
        log.info("DebugSessionLauncher starting task '{}'", taskName);
        fireBuildStarting(selectedModule, selectedVariant);
        final ActionData snapshot = data;
        // PR-D7: 把 future 保存到 currentTask,stop() 才能取消。
        currentTask = DebuggerController.getInstance().bgExecutor().submit(() -> {
            try {
                runBuild(snapshot, selectedModule, selectedVariant, taskName);
            } catch (Throwable t) {
                if (cancelled.get()) {
                    log.info("run cancelled: {}", t.getMessage());
                } else {
                    fail(Step.BUILD, "uncaught: " + t.getMessage());
                }
            } finally {
                currentTask = null;
                busy.set(false);
            }
        });
        return true;
    }

    @WorkerThread
    private void runBuild(@NonNull ActionData data,
                          @NonNull AndroidModule module,
                          @NonNull BasicAndroidVariantMetadata variant,
                          @NonNull String taskName) {
        if (cancelled.get()) { log.info("runBuild: cancelled before start"); return; }
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

        File apk = ApkMetadata.Companion.findApkFile(
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
        if (cancelled.get()) { log.info("runInstall: cancelled before start"); return; }
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
                // shizuku install 同步;但仍然读 applicationId 写到 controller。
                runInstall_postInstall(data, module, variant, apk, true);
                return;
            }
            log.warn("shizuku install failed, falling back to PackageInstaller");
        }

        // PR-D6: 用 EventBus + CountDownLatch 等待真实的 InstallationResultEvent,
        // 替换原 Thread.sleep(2_000L) 的盲等。
        final CountDownLatch installLatch = new CountDownLatch(1);
        final AtomicReference<String> installError = new AtomicReference<>();
        final InstallResultSubscriber listener = new InstallResultSubscriber(
                new InstallResultCallback() {
                    @Override
                    public void onResult(@Nullable String pkg, @Nullable String error) {
                        installError.set(error);
                        installLatch.countDown();
                    }
                });
        boolean registered = false;
        try {
            EventBus.getDefault().register(listener);
            registered = true;
        } catch (Throwable t) {
            log.warn("EventBus.register failed: {}", t.getMessage());
        }
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
                    installError.set(t.getMessage());
                    installLatch.countDown();
                }
            });
        } catch (Throwable t) {
            if (registered) try { EventBus.getDefault().unregister(listener); } catch (Throwable ignored) {}
            fail(Step.INSTALL, "installApk threw: " + t.getMessage());
            return;
        }
        // PR-D6: 用 latch.await 替换 Thread.sleep。
        try {
            if (!installLatch.await(INSTALL_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                if (registered) try { EventBus.getDefault().unregister(listener); } catch (Throwable ignored) {}
                fail(Step.INSTALL, "Timed out waiting for InstallationResultEvent after "
                        + INSTALL_RESULT_TIMEOUT_MS + "ms");
                return;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (registered) try { EventBus.getDefault().unregister(listener); } catch (Throwable ignored) {}
            return;
        } finally {
            if (registered) {
                try { EventBus.getDefault().unregister(listener); } catch (Throwable ignored) {}
            }
        }
        if (installError.get() != null) {
            fail(Step.INSTALL, "installApk threw: " + installError.get());
            return;
        }
        // PR-D6: 用 listener 拿到的实际包名(可能与 variant.applicationId 不一致,
        // 比如测试包被替换);不强制覆盖 targetPackage,以免破坏已存在的覆盖逻辑。
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
        // PR-D6: 写入 targetPackage,DebuggerController.stop() 据此 force-stop。
        DebuggerController.getInstance().setTargetPackage(pkg);
        runLaunch(pkg);
    }

    @WorkerThread
    private void runLaunch(@NonNull String packageName) {
        if (cancelled.get()) { log.info("runLaunch: cancelled before start"); return; }
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
        if (cancelled.get()) { log.info("runResolvePort: cancelled before start"); return; }
        JdwpPortResolver resolver = new JdwpPortResolver(appContext);
        try {
            int port = resolver.awaitJdwpPort(
                    packageName,
                    JdwpPortResolver.DEFAULT_TIMEOUT_MS,
                    JdwpPortResolver.DEFAULT_POLL_INTERVAL_MS);
            if (port <= 0) {
                // PR-D6: ContentProvider poll 失败 -> 用 run-as 探测
                // /data/data/<pkg>/jdwp-port 文件。如果拿到 uid 则继续轮询一次;
                // 真正失败时通过 fail(...) 把字符串带到 UI (而不是默默 timeout)。
                int probed = runAs != null ? runAs.probeUid(packageName) : -1;
                if (probed > 0) {
                    // 目标 app 已起来,再给 ContentProvider 一次机会。
                    int retryPort = resolver.awaitJdwpPort(
                            packageName,
                            5_000L,
                            JdwpPortResolver.DEFAULT_POLL_INTERVAL_MS);
                    if (retryPort > 0) {
                        runConnect("127.0.0.1", retryPort);
                        return;
                    }
                }
                fail(Step.RESOLVE_PORT,
                        "Timed out waiting for JDWP port of " + packageName
                                + " (uid=" + probed + ")");
                return;
            }
            runConnect("127.0.0.1", port);
        } finally {
            resolver.shutdown();
        }
    }

    @WorkerThread
    private void runConnect(@NonNull String host, int port) {
        if (cancelled.get()) { log.info("runConnect: cancelled before start"); return; }
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

    // -------- PR-D6: EventBus listener for install result --------

    /**
     * Callback 收到 install 结果时调用 (onResult 在 EventBus 派发线程上,
     * 默认 MAIN,与 BaseEditorActivity.onInstallationResult 保持一致)。
     */
    public interface InstallResultCallback {
        void onResult(@Nullable String packageName, @Nullable String error);
    }

    /**
     * 一次性的 EventBus subscriber,等 InstallationResultEvent 后回调
     * {@link InstallResultCallback}。必须在拿到结果或超时后 unregister,
     * 否则会泄漏。EventBus 要求 @Subscribe 注解必须在具名 class 上,
     * 所以这里不能直接用 lambda / 匿名类。
     */
    public static class InstallResultSubscriber {
        @Nullable private final InstallResultCallback callback;

        public InstallResultSubscriber(@Nullable InstallResultCallback cb) {
            this.callback = cb;
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onInstallationResultEvent(@NonNull InstallationResultEvent ev) {
            // 与 BaseEditorActivity 一致:用 InstallationResultHandler.onResult 解析包名。
            // 但是这里我们没法拿到 Activity context (只是 EventBus event);
            // 退而求其次:从 intent.getStringExtra(PACKAGE_NAME) 读。
            android.content.Intent intent = ev.getIntent();
            String pkg = null;
            String err = null;
            if (intent != null) {
                pkg = intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_PACKAGE_NAME);
                if (pkg == null) {
                    pkg = intent.getStringExtra("android.intent.extra.PACKAGE_NAME");
                }
                int status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS,
                        android.content.pm.PackageInstaller.STATUS_FAILURE);
                if (status != android.content.pm.PackageInstaller.STATUS_SUCCESS) {
                    String msg = intent.getStringExtra(
                            android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE);
                    err = "status=" + status + (msg != null ? (": " + msg) : "");
                }
            }
            if (callback != null) {
                try { callback.onResult(pkg, err); } catch (Throwable ignored) {}
            }
        }
    }
}
