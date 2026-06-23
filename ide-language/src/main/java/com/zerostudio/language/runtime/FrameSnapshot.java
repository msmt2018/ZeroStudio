package com.zerostudio.language.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时帧快照：在断点命中时捕获的类成员、静态字段、调用栈、线程、变量值。
 * 充当 JDI（Java Debug Interface）的模拟层 — 在没有真实 JDWP 设备时，
 * 也可以为单元测试或离线分析提供一致的接口。
 */
public final class FrameSnapshot {

    public enum Kind { LOCAL, FIELD, STATIC, PARAMETER, THREAD_LOCAL }

    public static final class Value {
        public final String name;
        public final String typeName;   // "java.lang.String"
        public final String kindLabel;  // "Local" / "Field" / "Static" / "Param"
        public final Object value;      // 实际值（可为 null）
        public final boolean isNull;
        public final boolean isPrimitive;

        public Value(String name, String typeName, String kindLabel, Object value) {
            this.name = name;
            this.typeName = typeName;
            this.kindLabel = kindLabel;
            this.value = value;
            this.isNull = value == null;
            this.isPrimitive = isPrimitiveType(typeName);
        }

        public String displayValue() {
            if (isNull) return "null";
            if (isPrimitive) return String.valueOf(value);
            if (value instanceof String) return "\"" + value + "\"";
            return "<" + typeName + ">";
        }

        private static boolean isPrimitiveType(String t) {
            return t != null && (
                    t.equals("int") || t.equals("long") || t.equals("short")
                            || t.equals("byte") || t.equals("float") || t.equals("double")
                            || t.equals("boolean") || t.equals("char"));
        }
    }

    public static final class StackFrame {
        public final String methodName;
        public final String className;
        public final int lineNumber;
        public final String sourcePath;

        public StackFrame(String methodName, String className, int lineNumber, String sourcePath) {
            this.methodName = methodName;
            this.className = className;
            this.lineNumber = lineNumber;
            this.sourcePath = sourcePath;
        }

        public String display() {
            return className + "." + methodName + ":" + lineNumber;
        }
    }

    public static final class ThreadInfo {
        public final String name;
        public final String state;     // "RUNNABLE" / "BLOCKED" / ...
        public final boolean suspended;

        public ThreadInfo(String name, String state, boolean suspended) {
            this.name = name;
            this.state = state;
            this.suspended = suspended;
        }
    }

    private final List<StackFrame> frames = new ArrayList<>();
    private final Map<String, Value> values = new LinkedHashMap<>();
    private final List<ThreadInfo> threads = new ArrayList<>();
    private boolean frozen = false;
    private long captureTimestamp;

    public void addFrame(StackFrame frame) { frames.add(frame); }
    public void addValue(Value v) { values.put(v.name, v); }
    public void addThread(ThreadInfo t) { threads.add(t); }

    public void setFrozen(boolean frozen) { this.frozen = frozen; }
    public void setCaptureTimestamp(long ts) { this.captureTimestamp = ts; }

    public List<StackFrame> frames() { return Collections.unmodifiableList(frames); }
    public Map<String, Value> values() { return Collections.unmodifiableMap(values); }
    public List<ThreadInfo> threads() { return Collections.unmodifiableList(threads); }
    public boolean isFrozen() { return frozen; }
    public long captureTimestamp() { return captureTimestamp; }

    public StackFrame topFrame() { return frames.isEmpty() ? null : frames.get(0); }

    /** 反射取成员：从 values 提取指定 kind 的子集 */
    public List<Value> valuesOfKind(Kind k) {
        List<Value> out = new ArrayList<>();
        for (Value v : values.values()) {
            if (k.name().equals(v.kindLabel.toUpperCase())) out.add(v);
        }
        return out;
    }

    public Value getValue(String name) { return values.get(name); }
}
