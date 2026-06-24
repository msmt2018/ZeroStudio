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
 *  PR-D7 重构: 把 logcat 进程管理 / 行读取 / 重连等通用能力抽到
 *  [LogcatReader],本类只负责 "拿到行 → 解析 → 回调"。
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
    @Nullable private LogcatReader reader;
    @Nullable private Listener listener;

    public AppReadySignalWatcher() {
        // 构造时不开 reader, 等 start() 调用
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    /**
     * Start watching for app-ready signals. Spawns a [LogcatReader] that uses
     * `sh -c "logcat -s <TAG>:V"`. Idempotent: calling start() twice is a no-op.
     */
    @AnyThread
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        LogcatReader r = new LogcatReader("logcat -s " + SIGNAL_TAG + ":V", true);
        r.setListener(new LogcatReader.LineListener() {
            @Override
            public void onLine(@NonNull String line) {
                parseAndDispatch(line);
            }
            @Override
            public void onReaderError(@NonNull Throwable t) {
                Log.w(TAG, "logcat reader error: " + t.getMessage());
            }
        });
        this.reader = r;
        r.start();
    }

    @AnyThread
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        LogcatReader r = this.reader;
        if (r != null) r.stop();
        this.reader = null;
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
