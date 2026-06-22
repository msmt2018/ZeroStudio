/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.util;

import androidx.annotation.NonNull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A bounded, thread-safe ring buffer for {@link com.zerostudio.logplugin.api.LogPayload}.
 *
 * <p>Used by the various capture mechanisms (logcat, crash, ANR, JNI) to
 * retain a small window of recent records in memory so that newly connected
 * IDEs can be primed with historical context.
 */
public final class LogBuffer {

    private final int capacity;
    private final Deque<com.zerostudio.logplugin.api.LogPayload> deque;
    private final Object lock = new Object();

    public LogBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.deque = new ArrayDeque<>(capacity);
    }

    /** Add a record to the buffer. Oldest records are evicted as needed. */
    public void add(@NonNull com.zerostudio.logplugin.api.LogPayload payload) {
        synchronized (lock) {
            while (deque.size() >= capacity) {
                deque.pollFirst();
            }
            deque.addLast(payload);
        }
    }

    /** Snapshot the buffer contents. The returned list is immutable and safe to use. */
    @NonNull
    public List<com.zerostudio.logplugin.api.LogPayload> snapshot() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(deque));
        }
    }

    /** Drop all records. */
    public void clear() {
        synchronized (lock) {
            deque.clear();
        }
    }

    public int size() {
        synchronized (lock) {
            return deque.size();
        }
    }

    public int capacity() {
        return capacity;
    }
}
