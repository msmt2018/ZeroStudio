/*
 *  ZeroStudio IDE - 应用就绪信号监听
 *
 *  PR-D7: 目标 app 启动后, DebuggerBootstrapProvider 把 "app ready"
 *  信号以 logcat 一行 + ContentProvider 写文件双通道方式通知 IDE。
 *  本类负责监听 logcat 通道。
 *
 *  信号协议 (约定):
 *    tag:    "ZeroStudioDebug"
 *    format: "READY pkg=<packageName> jdwp=<port> build=<variant>"
 *
 *  监听器在 IDE 启动后挂到 Logcat (通过 Shizuku / run-as 拿 logcat 输
 *  出),匹配到上述格式就回调 [onAppReady].
 *
 *  注意: 本类不直接做 attach; 它只发信号, 由 [DebugSessionLauncher]
 *  / [AutoAttachManager] 继续接续.
 */

package com.itsaky.androidide.debugger;

import android.util.Log;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppReadySignalWatcher {

    private static final String TAG = "AppReadySignalWatcher";

    /** logcat tag that the DebuggerBootstrapProvider logs to. */
    public static final String SIGNAL_TAG = "ZeroStudioDebug";

    /**
     * Format: "READY pkg=<packageName> jdwp=<port> build=<variant>".
     * We allow optional spaces and any number of extra fields after `build=`.
     */
    public static final Pattern SIGNAL_PATTERN = Pattern.compile(
            "READY\\s+pkg=(?<pkg>[^\\s]+)\\s+jdwp=(?<port>\\d+)(?:\\s+build=(?<build>\\S+))?");

    public interface Listener {
        void onAppReady(@NonNull String packageName, int jdwpPort, @Nullable String variant);
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor;
    @Nullable private Listener listener;
    @Nullable private Thread currentReader;

    public AppReadySignalWatcher() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AppReadySignalWatcher");
            t.setDaemon(true);
            return t;
        });
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    /**
     * Start watching for app-ready signals. Spawns a logcat reader thread
     * that uses `sh -c "logcat -s <TAG>:V"` via the local shell.
     * Idempotent: calling start() twice is a no-op.
     */
    @AnyThread
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        executor.submit(this::runLoop);
    }

    @AnyThread
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (currentReader != null) {
            currentReader.interrupt();
        }
        executor.shutdownNow();
    }

    @WorkerThread
    private void runLoop() {
        // 反复尝试连接 logcat; 失败/中断 5s 后重试. logcat 命令对 shell
        // 始终有 READY 输入流,所以 reader.join() 不会立刻返回.
        while (running.get()) {
            try {
                spawnLogcatReader();
            } catch (Throwable t) {
                Log.w(TAG, "logcat reader crashed: " + t.getMessage());
            }
            if (running.get()) {
                try {
                    Thread.sleep(5_000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @WorkerThread
    private void spawnLogcatReader() throws java.io.IOException {
        Process p = new ProcessBuilder("sh", "-c",
                "logcat -s " + SIGNAL_TAG + ":V")
                .redirectErrorStream(true)
                .start();
        currentReader = Thread.currentThread();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            String line;
            while (running.get() && (line = br.readLine()) != null) {
                parseAndDispatch(line);
            }
        } finally {
            try { p.destroy(); } catch (Throwable ignored) {}
        }
    }

    /** Exposed for tests: parse one line and dispatch. */
    @WorkerThread
    public void parseAndDispatch(@NonNull String line) {
        Matcher m = SIGNAL_PATTERN.matcher(line);
        if (!m.find()) return;
        String pkg = m.group("pkg");
        String portStr = m.group("port");
        String build = m.group("build");
        if (pkg == null || portStr == null) return;
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException nfe) {
            return;
        }
        if (port <= 0) return;
        Log.i(TAG, "app-ready signal: pkg=" + pkg + " jdwp=" + port);
        Listener l = listener;
        if (l != null) l.onAppReady(pkg, port, build);
    }
}
