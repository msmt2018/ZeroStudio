package com.zerostudio.language.breakpoint;

import com.zerostudio.language.eval.EvalEngine;
import com.zerostudio.language.eval.ExpressionParser;
import com.zerostudio.language.eval.ExpressionParser.Node;
import com.zerostudio.language.runtime.FrameSnapshot;

/**
 * 断点行为分类：
 *  - LINE: 普通行断点，命中即停
 *  - CONDITIONAL: 条件断点，命中时求值表达式为 true 才停
 *  - LOGPOINT: 日志点，命中时求值表达式并把结果输出到控制台
 *  - EXCEPTION: 异常断点，捕获到指定异常类型时停
 */
public final class Breakpoint {

    public enum Kind { LINE, CONDITIONAL, LOGPOINT, EXCEPTION }

    public final String id;
    public final String sourceFile;
    public final int line;
    public final Kind kind;
    public final String condition;
    public final String logMessage;
    public final String exceptionType;
    public final boolean enabled;
    public final int hitCount;          // 当前命中次数
    public final int hitThreshold;      // 触发阈值

    private Breakpoint(String id, String sourceFile, int line, Kind kind,
                       String condition, String logMessage, String exceptionType,
                       boolean enabled, int hitCount, int hitThreshold) {
        this.id = id;
        this.sourceFile = sourceFile;
        this.line = line;
        this.kind = kind;
        this.condition = condition;
        this.logMessage = logMessage;
        this.exceptionType = exceptionType;
        this.enabled = enabled;
        this.hitCount = hitCount;
        this.hitThreshold = hitThreshold;
    }

    public static Builder builder() { return new Builder(); }

    /** 在断点命中时执行：返回 true 表示应当停止 */
    public HitResult onHit(FrameSnapshot frame) {
        if (!enabled) return HitResult.skip();
        switch (kind) {
            case LINE:
                if (hitThreshold > 0 && (hitCount + 1) < hitThreshold) {
                    return HitResult.skip();
                }
                return HitResult.stop();
            case CONDITIONAL: {
                if (condition == null || condition.isEmpty()) return HitResult.stop();
                Node ast = new ExpressionParser(condition).parse();
                EvalEngine engine = new EvalEngine();
                EvalEngine.Result r = engine.evaluate(ast, frame);
                if (r.isError()) return HitResult.skip();
                if (r.isNull()) return HitResult.skip();
                boolean truth = Boolean.TRUE.equals(r.value);
                return truth ? HitResult.stop() : HitResult.skip();
            }
            case LOGPOINT: {
                if (logMessage == null) return HitResult.skip();
                String text = logMessage;
                if (logMessage.contains("{")) {
                    // inline {expr} expansion
                    StringBuilder out = new StringBuilder();
                    int i = 0;
                    while (i < logMessage.length()) {
                        char c = logMessage.charAt(i);
                        if (c == '{') {
                            int end = logMessage.indexOf('}', i);
                            if (end > i) {
                                String expr = logMessage.substring(i + 1, end);
                                try {
                                    Node e = new ExpressionParser(expr).parse();
                                    EvalEngine.Result r = new EvalEngine().evaluate(e, frame);
                                    out.append(r.value != null ? r.value : "null");
                                } catch (Exception e) { out.append("{err}"); }
                                i = end + 1;
                                continue;
                            }
                        }
                        out.append(c);
                        i++;
                    }
                    text = out.toString();
                }
                return HitResult.log(text);
            }
            case EXCEPTION: {
                if (frame.values().containsKey("__exception__")
                        && exceptionType != null) {
                    String actualType = (String) frame.values().get("__exception__").value;
                    if (actualType != null
                            && (actualType.equals(exceptionType)
                            || actualType.startsWith(exceptionType))) {
                        return HitResult.stop();
                    }
                }
                return HitResult.skip();
            }
        }
        return HitResult.skip();
    }

    public static final class HitResult {
        public final boolean stop;
        public final boolean skip;
        public final String log;

        private HitResult(boolean stop, boolean skip, String log) {
            this.stop = stop; this.skip = skip; this.log = log;
        }
        public static HitResult stop() { return new HitResult(true, false, null); }
        public static HitResult skip() { return new HitResult(false, true, null); }
        public static HitResult log(String text) { return new HitResult(false, false, text); }
    }

    public static final class Builder {
        private String id;
        private String sourceFile;
        private int line;
        private Kind kind = Kind.LINE;
        private String condition;
        private String logMessage;
        private String exceptionType;
        private boolean enabled = true;
        private int hitCount = 0;
        private int hitThreshold = 0;

        public Builder id(String v) { this.id = v; return this; }
        public Builder sourceFile(String v) { this.sourceFile = v; return this; }
        public Builder line(int v) { this.line = v; return this; }
        public Builder kind(Kind v) { this.kind = v; return this; }
        public Builder condition(String v) { this.kind = Kind.CONDITIONAL; this.condition = v; return this; }
        public Builder logMessage(String v) { this.kind = Kind.LOGPOINT; this.logMessage = v; return this; }
        public Builder exceptionType(String v) { this.kind = Kind.EXCEPTION; this.exceptionType = v; return this; }
        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder hitCount(int v) { this.hitCount = v; return this; }
        public Builder hitThreshold(int v) { this.hitThreshold = v; return this; }

        public Breakpoint build() {
            return new Breakpoint(id, sourceFile, line, kind, condition, logMessage,
                    exceptionType, enabled, hitCount, hitThreshold);
        }
    }

    /** 断点注册表：按 sourceFile 分组 */
    public static final class Registry {
        private final java.util.Map<String, java.util.List<Breakpoint>> byFile = new java.util.HashMap<>();

        public void add(Breakpoint bp) {
            byFile.computeIfAbsent(bp.sourceFile, k -> new java.util.ArrayList<>()).add(bp);
        }
        public void remove(String id) {
            for (java.util.List<Breakpoint> list : byFile.values()) {
                list.removeIf(b -> b.id.equals(id));
            }
        }
        public java.util.List<Breakpoint> all() {
            java.util.List<Breakpoint> out = new java.util.ArrayList<>();
            for (java.util.List<Breakpoint> list : byFile.values()) out.addAll(list);
            return out;
        }
        public java.util.List<Breakpoint> forFile(String f) {
            return byFile.getOrDefault(f, java.util.Collections.emptyList());
        }
        public void clear() { byFile.clear(); }
    }
}
