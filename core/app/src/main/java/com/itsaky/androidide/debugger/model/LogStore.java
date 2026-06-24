/*
 *  ZeroStudio IDE - 日志点 (logpoint) 输出缓冲
 *
 *  命中"日志点"断点时,ide-debugger 求值 logMessage 后把结果通过
 *  DebugEvents.LOGPOINT 推过来。本类维护一个最近 N 条的环形缓冲,
 *  并向注册的 listener 派发增量事件。
 *
 *  PR-6: 与 LogFragment 配套。
 *  PR-D6: 容量从 500 提到 10000,避免长时间调试时日志被过早挤掉。
 *        改成 ArrayDeque FIFO,remove(0) 是 O(n) —— 10000 条以内可接受,
 *        后续 PR-D7 可换成 LinkedList 或环形数组。
 */

package com.itsaky.androidide.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LogStore {

    /** PR-D6: 容量从 500 提到 10000 条。 */
    public static final int DEFAULT_CAPACITY = 10_000;

    public static final class Entry {
        public final long timestamp;
        @NonNull public final String sourceFile;
        public final int line;
        @NonNull public final String text;

        public Entry(long timestamp, @NonNull String sourceFile, int line, @NonNull String text) {
            this.timestamp = timestamp;
            this.sourceFile = sourceFile;
            this.line = line;
            this.text = text;
        }
    }

    public interface Listener {
        void onLogAppended(@NonNull Entry entry);
        void onLogCleared();
    }

    private static final LogStore INSTANCE = new LogStore();
    public static LogStore getInstance() { return INSTANCE; }

    /** PR-D7: Deque FIFO,pollFirst 是 O(1)。 */
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private int capacity = DEFAULT_CAPACITY;

    /** PR-D7: 后台派发线程,避免 listener.onLogAppended 在调用方线程上跑。 */
    private final android.os.HandlerThread dispatchThread =
            new android.os.HandlerThread("LogStore-Dispatch");
    @NonNull private final android.os.Handler dispatchHandler;
    {
        dispatchThread.start();
        dispatchHandler = new android.os.Handler(dispatchThread.getLooper());
    }

    private LogStore() {}

    public void setCapacity(int capacity) {
        if (capacity < 1) return;
        this.capacity = capacity;
        synchronized (entries) {
            while (entries.size() > capacity) entries.pollFirst();
        }
    }

    public void addListener(@NonNull Listener l) { listeners.addIfAbsent(l); }
    public void removeListener(@NonNull Listener l) { listeners.remove(l); }

    public void append(@NonNull String text) {
        append(null, -1, text);
    }

    public void append(@Nullable String sourceFile, int line, @NonNull String text) {
        Entry e = new Entry(System.currentTimeMillis(),
                sourceFile == null ? "" : sourceFile, line, text);
        synchronized (entries) {
            entries.addLast(e);
            // PR-D6: FIFO 驱逐。ArrayDeque.pollFirst 是 O(1),
            // 旧实现 entries.remove(0) 是 O(n)。
            while (entries.size() > capacity) entries.pollFirst();
        }
        // PR-D7: listener 派发切到 dispatchHandler 线程,
        // 避免在 JDWP read 线程上做 UI 更新(例如 LogFragment 增条目)。
        // PR-D8.4: coalesce 50ms 内的多条 append, 合并成 1 次 handler
        // 消息一次派发, 减少 handler 排队开销。
        final Entry copy = e;
        final boolean needSchedule;
        synchronized (pendingBatch) {
            pendingBatch.add(copy);
            // 距离上次 flush < coalesceMs 时, 不重新 schedule,
            // 让已有的 Runnable 把这条也 flush 掉。
            needSchedule = (System.nanoTime() - lastDispatchNanos) >= coalesceNs;
            if (needSchedule) {
                lastDispatchNanos = System.nanoTime();
            }
        }
        if (needSchedule) {
            dispatchHandler.postDelayed(flushRunnable, coalesceMs);
        }
    }

    /** PR-D8.4: flush pendingBatch 中累积的 entries 到所有 listener。 */
    private final Runnable flushRunnable = new Runnable() {
        @Override
        public void run() {
            final java.util.List<Entry> toFlush;
            synchronized (pendingBatch) {
                if (pendingBatch.isEmpty()) return;
                toFlush = new java.util.ArrayList<>(pendingBatch);
                pendingBatch.clear();
            }
            for (Listener l : listeners) {
                try {
                    for (Entry e : toFlush) l.onLogAppended(e);
                } catch (Throwable ignored) {}
            }
        }
    };
    private final java.util.List<Entry> pendingBatch = new java.util.ArrayList<>();
    private long lastDispatchNanos = 0L;
    // PR-D8.4: 实例字段(非 static final)以便 setCoalesceMsForTest
    // 在测试中改为 0 立即派发。生产代码 50ms 是合理值。
    private long coalesceMs = 50L;
    private long coalesceNs = 50L * 1_000_000L;

    public void clear() {
        synchronized (entries) { entries.clear(); }
        // PR-D8.4: clear 时清空 pendingBatch, 否则 flush 线程晚于
        // onLogCleared 触发的 onLogAppended 会被 listener 看成"已清空
        // 后又新增",破坏"先 clear 再 append"的语义。
        synchronized (pendingBatch) { pendingBatch.clear(); }
        // PR-D7: clear 同样在后台派发。
        dispatchHandler.post(() -> {
            for (Listener l : listeners) {
                try { l.onLogCleared(); } catch (Throwable ignored) {}
            }
        });
    }

    /**
     * PR-D8.4 测试钩子 (package-private): 把 coalesce 间隔设为 0,
     * 让测试中的 listener 立即派发(不走 50ms 延迟)。生产代码不应调用。
     */
    void setCoalesceMsForTest(long ms) {
        synchronized (pendingBatch) {
            coalesceMs = ms;
            coalesceNs = ms * 1_000_000L;
        }
    }

    @NonNull
    public List<Entry> snapshot() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    public int size() {
        synchronized (entries) { return entries.size(); }
    }
}
