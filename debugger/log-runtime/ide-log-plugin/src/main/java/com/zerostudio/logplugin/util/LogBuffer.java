/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Phase C4: a thread-safe ring buffer for log lines. Used both by
 *  the [LogCaptureService] to buffer entries before they are
 *  flushed to the IDE, and by the IDE's log viewer to retain a
 *  scrollback when a breakpoint fires.
 */
package com.zerostudio.logplugin.util;

import androidx.annotation.NonNull;

import com.zerostudio.logplugin.api.LogPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public final class LogBuffer {

    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Object[] entries;
    private int head = 0;
    private int size = 0;

    public LogBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity < 1: " + capacity);
        }
        this.capacity = capacity;
        this.entries = new Object[capacity];
    }

    /** Append a payload. Older entries are overwritten when full. */
    public void append(@NonNull LogPayload entry) {
        lock.lock();
        try {
            entries[head] = entry;
            head = (head + 1) % capacity;
            if (size < capacity) size++;
        } finally {
            lock.unlock();
        }
    }

    /** Number of entries currently held. */
    public int size() {
        lock.lock();
        try { return size; } finally { lock.unlock(); }
    }

    /** Maximum number of entries this buffer can hold. */
    public int capacity() {
        return capacity;
    }

    /** Drain all entries in oldest-to-newest order. */
    @NonNull
    public List<LogPayload> snapshot() {
        lock.lock();
        try {
            List<LogPayload> out = new ArrayList<>(size);
            int start = (head - size + capacity) % capacity;
            for (int i = 0; i < size; i++) {
                int idx = (start + i) % capacity;
                Object e = entries[idx];
                if (e instanceof LogPayload) {
                    out.add((LogPayload) e);
                }
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    /** Drop all entries. */
    public void clear() {
        lock.lock();
        try {
            for (int i = 0; i < capacity; i++) entries[i] = null;
            head = 0;
            size = 0;
        } finally {
            lock.unlock();
        }
    }
}
