package com.zerostudio.language.eval;

import com.zerostudio.language.eval.ExpressionParser.Node;
import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches 面板模型：每条 watch 是一个待求值表达式 + 最近一次值。
 * 配合 EvalEngine 在 FrameSnapshot 上执行。
 */
public final class WatchPanel {

    public static final class WatchEntry {
        public final String expression;
        public final String typeName;
        public final String displayValue;
        public final boolean isError;
        public final boolean isNull;

        public WatchEntry(String expression, String typeName, String displayValue,
                          boolean isError, boolean isNull) {
            this.expression = expression;
            this.typeName = typeName;
            this.displayValue = displayValue;
            this.isError = isError;
            this.isNull = isNull;
        }
    }

    private final Map<String, WatchEntry> watches = new LinkedHashMap<>();
    private final EvalEngine engine = new EvalEngine();

    public synchronized void addWatch(String expression) {
        watches.put(expression, new WatchEntry(expression, "?", "<not yet evaluated>", false, true));
    }

    public synchronized void removeWatch(String expression) {
        watches.remove(expression);
    }

    public synchronized void clear() { watches.clear(); }

    public synchronized List<WatchEntry> watches() {
        return Collections.unmodifiableList(new ArrayList<>(watches.values()));
    }

    public synchronized void evaluate(FrameSnapshot frame) {
        for (String expr : new ArrayList<>(watches.keySet())) {
            try {
                ExpressionParser.Node ast = new ExpressionParser(expr).parse();
                EvalEngine.Result r = engine.evaluate(ast, frame);
                if (r.isError()) {
                    watches.put(expr, new WatchEntry(expr, "Error", r.error, true, false));
                } else if (r.isNull()) {
                    watches.put(expr, new WatchEntry(expr, "null", "null", false, true));
                } else {
                    String typeName = r.value != null ? r.value.getClass().getSimpleName() : "Object";
                    String display = formatValue(r.value);
                    watches.put(expr, new WatchEntry(expr, typeName, display, false, false));
                }
            } catch (Exception e) {
                watches.put(expr, new WatchEntry(expr, "ParseError", e.getMessage(), true, false));
            }
        }
    }

    private String formatValue(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return "\"" + v + "\"";
        if (v.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("[");
            Object[] arr = (Object[]) v;
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatValue(arr[i]));
            }
            return sb.append("]").toString();
        }
        if (v instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (Object item : (List<?>) v) {
                if (i++ > 0) sb.append(", ");
                sb.append(formatValue(item));
            }
            return sb.append("]").toString();
        }
        return String.valueOf(v);
    }
}
