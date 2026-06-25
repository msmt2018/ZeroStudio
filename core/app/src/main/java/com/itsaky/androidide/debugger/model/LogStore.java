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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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
    private volatile boolean enabled = true;

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

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isEnabled() { return enabled; }

    public void append(@Nullable String sourceFile, int line, @NonNull String text) {
        if (!enabled) return;
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
        final Entry copy = e;
        dispatchHandler.post(() -> {
            for (Listener l : listeners) {
                try { l.onLogAppended(copy); } catch (Throwable ignored) {}
            }
        });
    }

    public void clear() {
        synchronized (entries) { entries.clear(); }
        // PR-D7: clear 同样在后台派发。
        dispatchHandler.post(() -> {
            for (Listener l : listeners) {
                try { l.onLogCleared(); } catch (Throwable ignored) {}
            }
        });
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

    public int exportToFile(@NonNull File outFile) throws java.io.IOException {
        List<Entry> snap = snapshot();
        StringBuilder sb = new StringBuilder();
        for (Entry e : snap) {
            sb.append(e.timestamp).append('\t')
                    .append(e.sourceFile).append(':').append(e.line).append('\t')
                    .append(e.text).append('\n');
        }
        File parent = outFile.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(outFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        return snap.size();
    }
}
