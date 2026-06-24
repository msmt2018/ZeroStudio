/*
 *  ZeroStudio IDE - 日志点 (logpoint) 输出缓冲
 *
 *  命中"日志点"断点时,ide-debugger 求值 logMessage 后把结果通过
 *  DebugEvents.LOGPOINT 推过来。本类维护一个最近 N 条的环形缓冲,
 *  并向注册的 listener 派发增量事件。
 *
 *  PR-6: 与 LogFragment 配套。
 */

package com.itsaky.androidide.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LogStore {

    public static final int DEFAULT_CAPACITY = 500;

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

    private final List<Entry> entries = new ArrayList<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private int capacity = DEFAULT_CAPACITY;

    private LogStore() {}

    public void setCapacity(int capacity) {
        if (capacity < 1) return;
        this.capacity = capacity;
        synchronized (entries) {
            while (entries.size() > capacity) entries.remove(0);
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
            entries.add(e);
            while (entries.size() > capacity) entries.remove(0);
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
