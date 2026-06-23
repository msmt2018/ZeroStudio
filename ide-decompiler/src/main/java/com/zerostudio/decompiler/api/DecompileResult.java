package com.zerostudio.decompiler.api;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class DecompileResult {
    public final String className;
    public final String source;
    public final Map<Integer, Long> lineMapping;
    public final String failure;

    private DecompileResult(String className, String source, Map<Integer, Long> lineMapping, String failure) {
        this.className = className;
        this.source = source;
        this.lineMapping = lineMapping != null ? lineMapping : Collections.emptyMap();
        this.failure = failure;
    }

    public static DecompileResult ok(String className, String source, Map<Integer, Long> lineMapping) {
        return new DecompileResult(className, source, lineMapping, null);
    }

    public static DecompileResult fail(String className, String failure) {
        return new DecompileResult(className, null, null, failure);
    }

    public boolean isOk() { return failure == null && source != null; }

    /**
     * 反转 lineMapping：原本是 (java 源码行 → bytecode offset)，
     * 现在按 bytecode offset 升序排，可用于 Step Into 时从偏移反查源码行。
     */
    public NavigableMap<Long, Integer> reverseByOffset() {
        NavigableMap<Long, Integer> out = new TreeMap<>();
        if (lineMapping == null) return out;
        for (Map.Entry<Integer, Long> e : lineMapping.entrySet()) {
            out.putIfAbsent(e.getValue(), e.getKey());
        }
        return out;
    }

    /**
     * 给定 bytecode offset，查找最近的小于等于该 offset 的源码行号。
     * 用于断点命中（offset）→ 高亮源码（行）。
     */
    public int sourceLineForOffset(long offset) {
        NavigableMap<Long, Integer> reversed = reverseByOffset();
        Map.Entry<Long, Integer> entry = reversed.floorEntry(offset);
        return entry != null ? entry.getValue() : -1;
    }
}