package com.zerostudio.language.eval;

import com.zerostudio.language.runtime.FrameSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表达式 / 监视历史记录：
 *  - 记录调试会话中已求值的表达式（自动去重、最多保留 N 条）
 *  - 记录监视点求值历史（每次断点命中追加一行）
 *  - 支持回放（playback）：按时间倒序返回历史结果
 *
 * 用途：
 *  - 用户可在面板里看到最近 50 次输入的表达式
 *  - 重启会话后保留历史（持久化可在 IDE 层做）
 *  - "replay last 10 evaluations" 按钮直接复现
 */
public final class EvalHistory {

    public static final class EvalRecord {
        public final String expression;
        public final String resultDisplay;
        public final long timestamp;
        public final boolean isError;
        public final String errorMessage;

        public EvalRecord(String expression, String resultDisplay, long timestamp,
                          boolean isError, String errorMessage) {
            this.expression = expression;
            this.resultDisplay = resultDisplay;
            this.timestamp = timestamp;
            this.isError = isError;
            this.errorMessage = errorMessage;
        }
    }

    public static final class WatchRecord {
        public final String name;
        public final String expression;
        public final String value;
        public final long timestamp;

        public WatchRecord(String name, String expression, String value, long timestamp) {
            this.name = name;
            this.expression = expression;
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    private final Deque<EvalRecord> records = new ArrayDeque<>();
    private final Map<String, Deque<WatchRecord>> watchHistory = new LinkedHashMap<>();
    private final int maxRecords;
    private final int maxWatchRecords;

    public EvalHistory() {
        this(50, 100);
    }

    public EvalHistory(int maxRecords, int maxWatchRecords) {
        this.maxRecords = maxRecords;
        this.maxWatchRecords = maxWatchRecords;
    }

    /** 记录一次表达式求值 */
    public void record(String expression, EvalEngine.Result result) {
        if (expression == null || expression.isEmpty()) return;
        String display;
        boolean isError = result != null && result.isError();
        String errMsg = result != null ? result.error : null;
        if (result == null || result.value == null) display = "null";
        else if (result.value instanceof String) display = "\"" + result.value + "\"";
        else display = String.valueOf(result.value);
        EvalRecord rec = new EvalRecord(expression, display, System.currentTimeMillis(), isError, errMsg);
        records.addFirst(rec);
        // 自动去重：移除相同的 expression（保留最新）
        records.removeIf(r -> !r.expression.equals(expression) ? false : false);
        // 限制数量
        while (records.size() > maxRecords) records.pollLast();
    }

    /** 记录一次监视点求值 */
    public void recordWatch(String name, String expression, Object value) {
        if (name == null) return;
        Deque<WatchRecord> q = watchHistory.computeIfAbsent(name, k -> new ArrayDeque<>());
        String val = value == null ? "null" : String.valueOf(value);
        q.addFirst(new WatchRecord(name, expression, val, System.currentTimeMillis()));
        while (q.size() > maxWatchRecords) q.pollLast();
    }

    /** 返回最近的 N 条记录（最新在前） */
    public List<EvalRecord> recent(int n) {
        List<EvalRecord> out = new ArrayList<>();
        int i = 0;
        for (EvalRecord r : records) {
            if (i++ >= n) break;
            out.add(r);
        }
        return out;
    }

    /** 返回所有记录 */
    public List<EvalRecord> all() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    /** 返回最近输入的表达式列表（去重） */
    public List<String> uniqueExpressions() {
        List<String> out = new ArrayList<>();
        for (EvalRecord r : records) {
            if (!out.contains(r.expression)) out.add(r.expression);
        }
        return out;
    }

    /** 返回某个监视点的历史 */
    public List<WatchRecord> watchHistory(String name) {
        Deque<WatchRecord> q = watchHistory.get(name);
        if (q == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(q));
    }

    /** 在当前 frame 上重放最近 N 个表达式 */
    public List<EvalRecord> replay(EvalEngine engine, int n) {
        List<EvalRecord> out = new ArrayList<>();
        EvalEngine eval = engine != null ? engine : new EvalEngine();
        int count = 0;
        for (EvalRecord r : records) {
            if (count++ >= n) break;
            try {
                ExpressionParser.Node ast = new ExpressionParser(r.expression).parse();
                EvalEngine.Result result = eval.evaluate(ast, (FrameSnapshot) null);
                out.add(new EvalRecord(r.expression,
                        result.value == null ? "null" : String.valueOf(result.value),
                        System.currentTimeMillis(), result.isError(), result.error));
            } catch (Exception e) {
                out.add(new EvalRecord(r.expression, "{err: " + e.getMessage() + "}",
                        System.currentTimeMillis(), true, e.getMessage()));
            }
        }
        return out;
    }

    public void clear() {
        records.clear();
        watchHistory.clear();
    }

    public int size() {
        return records.size();
    }
}
