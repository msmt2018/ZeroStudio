package com.zerostudio.language.breakpoint;

import com.zerostudio.language.eval.EvalEngine;
import com.zerostudio.language.eval.ExpressionParser;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.runtime.FrameSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 断点管理器：函数断点 / 字段观察点（数据断点）/ 临时断点 / 普通行断点统一管理。
 *
 * 函数断点（FunctionBreakpoint）：按方法名匹配，命中方法入口时触发。
 *   - 通过 SourceLocator + parsed file 找到方法首行
 *   - 运行时通过 FrameSnapshot.methodName 匹配
 *
 * 字段观察点（FieldWatchpoint）：按字段名匹配，命中字段读写时触发。
 *   - 通过 __field_access__ 哨兵变量传递
 *   - 写观察点用 __field_write__ = true
 *
 * 临时断点（TemporaryBreakpoint）：命中 N 次后自动移除。
 *   - hitLimit 达到后从 Registry 删除
 */
public final class BreakpointManager {

    public enum AccessMode { READ, WRITE, READ_WRITE }

    /** 函数断点 */
    public static final class FunctionBreakpoint {
        public final String id;
        public final String className;
        public final String methodName;
        public final String condition;
        public int hitCount = 0;
        public final int hitLimit;     // 0 = 无限
        public boolean enabled = true;

        public FunctionBreakpoint(String className, String methodName, String condition, int hitLimit) {
            this.id = "fnb-" + UUID.randomUUID();
            this.className = className;
            this.methodName = methodName;
            this.condition = condition;
            this.hitLimit = hitLimit;
        }

        public boolean matches(String cls, String method) {
            if (!enabled) return false;
            if (methodName == null) return false;
            if (methodName.equals(method)) {
                if (className == null || className.isEmpty() || className.equals(cls)) return true;
            }
            return false;
        }
    }

    /** 字段观察点 */
    public static final class FieldWatchpoint {
        public final String id;
        public final String className;
        public final String fieldName;
        public final AccessMode access;
        public int hitCount = 0;
        public boolean enabled = true;

        public FieldWatchpoint(String className, String fieldName, AccessMode access) {
            this.id = "fwp-" + UUID.randomUUID();
            this.className = className;
            this.fieldName = fieldName;
            this.access = access;
        }

        public boolean matches(String cls, String field, boolean isWrite) {
            if (!enabled) return false;
            if (fieldName == null || !fieldName.equals(field)) return false;
            if (className != null && !className.isEmpty() && !className.equals(cls)) return false;
            if (access == AccessMode.READ && isWrite) return false;
            if (access == AccessMode.WRITE && !isWrite) return false;
            return true;
        }
    }

    private final List<FunctionBreakpoint> functionBreakpoints = new ArrayList<>();
    private final List<FieldWatchpoint> fieldWatchpoints = new ArrayList<>();

    public void addFunction(FunctionBreakpoint fb) {
        functionBreakpoints.add(fb);
    }

    public void removeFunction(String id) {
        functionBreakpoints.removeIf(fb -> fb.id.equals(id));
    }

    public void addWatchpoint(FieldWatchpoint wp) {
        fieldWatchpoints.add(wp);
    }

    public void removeWatchpoint(String id) {
        fieldWatchpoints.removeIf(wp -> wp.id.equals(id));
    }

    public List<FunctionBreakpoint> functions() {
        return Collections.unmodifiableList(functionBreakpoints);
    }

    public List<FieldWatchpoint> watchpoints() {
        return Collections.unmodifiableList(fieldWatchpoints);
    }

    /** 在 frame 进入时检查所有函数断点 */
    public List<Breakpoint.HitResult> checkFunctionEntry(FrameSnapshot frame) {
        List<Breakpoint.HitResult> results = new ArrayList<>();
        FrameSnapshot.StackFrame top = frame == null ? null : frame.topFrame();
        if (top == null) return results;
        String cls = top.className;
        String method = top.methodName;
        // 收集要移除的断点 id（避免 ConcurrentModificationException）
        java.util.List<String> toRemove = new java.util.ArrayList<>();
        for (FunctionBreakpoint fb : functionBreakpoints) {
            if (fb.matches(cls, method)) {
                fb.hitCount++;
                if (fb.hitLimit > 0 && fb.hitCount >= fb.hitLimit) {
                    toRemove.add(fb.id);
                }
                if (fb.condition != null && !fb.condition.isEmpty()) {
                    try {
                        ExpressionParser.Node ast = new ExpressionParser(fb.condition).parse();
                        EvalEngine.Result r = new EvalEngine().evaluate(ast, frame);
                        if (!Boolean.TRUE.equals(r.value)) continue;
                    } catch (Exception ignored) { continue; }
                }
                results.add(Breakpoint.HitResult.stop());
            }
        }
        for (String id : toRemove) removeFunction(id);
        return results;
    }

    /** 在字段访问时检查所有字段观察点 */
    public List<Breakpoint.HitResult> checkFieldAccess(FrameSnapshot frame, String fieldName, boolean isWrite) {
        List<Breakpoint.HitResult> results = new ArrayList<>();
        FrameSnapshot.StackFrame top = frame == null ? null : frame.topFrame();
        if (top == null) return results;
        String cls = top.className;
        for (FieldWatchpoint wp : fieldWatchpoints) {
            if (wp.matches(cls, fieldName, isWrite)) {
                wp.hitCount++;
                results.add(Breakpoint.HitResult.stop());
            }
        }
        return results;
    }

    /**
     * 临时断点：命中后自动转为已禁用。
     * 复用 Breakpoint.Builder 构造普通断点。
     */
    public static Breakpoint createTemporary(String file, int line, int hitLimit) {
        return Breakpoint.builder()
                .id("tmp-" + UUID.randomUUID())
                .sourceFile(file)
                .line(line)
                .kind(Breakpoint.Kind.LINE)
                .enabled(true)
                .hitThreshold(hitLimit)
                .build();
    }

    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "(?:public|private|protected|static|abstract|final|synchronized|native|void|int|long|short|byte|char|float|double|boolean|String|[A-Z]\\w+(?:<[^>]*>)?)\\s+([A-Za-z_]\\w*)\\s*\\(");

    /**
     * 从源码中查找方法首行号（用于在编辑时把函数断点转成行断点）。
     */
    public static int findMethodLine(String source, String methodName) {
        if (source == null || methodName == null) return -1;
        String[] lines = source.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = METHOD_PATTERN.matcher(lines[i]);
            if (m.find() && methodName.equals(m.group(1))) {
                return i + 1;
            }
        }
        return -1;
    }

    public static SourceRange toSourceRange(String path, int line) {
        return new SourceRange(
                new SourcePosition(path, line, 1),
                new SourcePosition(path, line, 1));
    }
}
