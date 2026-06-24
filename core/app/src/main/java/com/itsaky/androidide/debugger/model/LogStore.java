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

    /** PR-D6: Deque FIFO,pollFirst 是 O(1)。 */
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private int capacity = DEFAULT_CAPACITY;

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
        for (Listener l : listeners) {
            try { l.onLogAppended(e); } catch (Throwable ignored) {}
        }
    }

    public void clear() {
        synchronized (entries) { entries.clear(); }
        for (Listener l : listeners) {
            try { l.onLogCleared(); } catch (Throwable ignored) {}
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
