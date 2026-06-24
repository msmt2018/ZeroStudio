/*
 *  ZeroStudio IDE - Logcat 行读取器 (PR-D7 重构)
 *
 *  通用能力:
 *    - 给定 shell 命令(默认 `logcat -s <TAG>:V`),在后台线程 spawn Process,
 *      持续读取 stdout 按行回调 onLine(line)
 *    - 5s 重连: 进程退出 / 出错时自动重启 (调用方可以关闭自动重连)
 *    - stop() 立刻 interrupt 当前 reader + destroy 进程
 *
 *  设计:
 *    - 单线程 daemon executor 调度整条 run loop,避免 spawn 太频繁
 *    - reader 线程自己负责 readLine 循环;不在 executor 主线程上做 IO 阻塞,
 *      这样 stop() 可以立刻把 reader 中断掉
 *    - Process 句柄在 finally 里 destroy, 防止进程泄漏
 *
 *  与 [AppReadySignalWatcher] 的关系: 后者持有 LogcatReader 实例,
 *  通过 SIGNAL_TAG 启动 + 自定义 LINE_PATTERN 解析。本类只负责 "读到行",
 *  解析逻辑在调用方。
 */

package com.itsaky.androidide.debugger;

import android.util.Log;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LogcatReader {

    private static final String TAG = "LogcatReader";
    // PR-D9.3 (#46): 退避改成指数 (1s, 2s, 4s, ... 8s 上限), 比原先固定 5s
    // 在断连频繁时更友好, 长时间断连时也避免空跑。
    private static final long BACKOFF_INITIAL_MS = 1_000L;
    private static final long BACKOFF_MAX_MS = 8_000L;
    // PR-D9.3 (#46): 行读取 poll 间隔缩短到 1s, 让 stop() 更快生效。
    private static final long READ_LINE_TIMEOUT_MS = 1_000L;

    public interface LineListener {
        /** Called on the reader thread for each non-null line. */
        @WorkerThread
        void onLine(@NonNull String line);
        /** Called when the process dies and we couldn't recover. */
        @WorkerThread
        void onReaderError(@NonNull Throwable t);
    }

    private final String command;
    private final boolean autoReconnect;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    @Nullable private LineListener listener;
    @Nullable private volatile Process currentProcess;
    @Nullable private volatile Thread currentReader;

    public LogcatReader(@NonNull String command) {
        this(command, true);
    }

    public LogcatReader(@NonNull String command, boolean autoReconnect) {
        this.command = command;
        this.autoReconnect = autoReconnect;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LogcatReader");
            t.setDaemon(true);
            return t;
        });
    }

    public void setListener(@Nullable LineListener l) { this.listener = l; }

    @AnyThread
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        executor.submit(this::runLoop);
    }

    @AnyThread
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        Thread t = currentReader;
        if (t != null) t.interrupt();
        Process p = currentProcess;
        if (p != null) {
            try { p.destroy(); } catch (Throwable ignored) {}
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @WorkerThread
    private void runLoop() {
        // PR-D9.3 (#46): 指数退避。每次 spawn 失败后 backoff 翻倍, 上限 BACKOFF_MAX_MS。
        // 成功 spawn 后回到 INITIAL, 避免一次成功后下次异常时还卡在长退避上。
        long backoffMs = BACKOFF_INITIAL_MS;
        while (running.get()) {
            try {
                spawnOnce();
                // 进程正常返回 (running 仍为 true 但读循环退出) → 视为瞬时断连,
                // 也走退避, 但成功 spawn 视为重置 backoff (放在 finally 后做)。
                backoffMs = BACKOFF_INITIAL_MS;
            } catch (Throwable t) {
                Log.w(TAG, "logcat process crashed: " + t.getMessage());
                LineListener l = listener;
                if (l != null) {
                    try { l.onReaderError(t); } catch (Throwable ignored) {}
                }
            }
            if (!running.get()) return;
            if (!autoReconnect) return;
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMs = Math.min(backoffMs * 2L, BACKOFF_MAX_MS);
        }
    }

    @WorkerThread
    private void spawnOnce() throws IOException {
        Process p = new ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start();
        currentProcess = p;
        currentReader = Thread.currentThread();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            String line;
            while (running.get() && (line = readLineWithTimeout(br)) != null) {
                LineListener l = listener;
                if (l != null) {
                    try { l.onLine(line); } catch (Throwable ignored) {}
                }
            }
        } finally {
            currentProcess = null;
            currentReader = null;
            try { p.destroy(); } catch (Throwable ignored) {}
        }
    }

    /**
     * 用 poll 方式读一行,避免 readLine() 在没有换行时永远阻塞,
     * 这样 stop() → interrupt() 后能及时退出。
     */
    @Nullable
    private String readLineWithTimeout(@NonNull BufferedReader br) throws IOException {
        long deadline = System.currentTimeMillis() + READ_LINE_TIMEOUT_MS;
        StringBuilder sb = new StringBuilder();
        while (running.get() && System.currentTimeMillis() < deadline) {
            if (Thread.currentThread().isInterrupted()) return null;
            if (br.ready()) {
                int c = br.read();
                if (c == -1) return sb.length() == 0 ? null : sb.toString();
                if (c == '\n') return sb.toString();
                if (c != '\r') sb.append((char) c);
            } else {
                try { Thread.sleep(20L); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }
}
