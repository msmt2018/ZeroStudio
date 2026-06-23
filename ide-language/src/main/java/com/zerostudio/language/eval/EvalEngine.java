package com.zerostudio.language.eval;

import com.zerostudio.language.eval.ExpressionParser.Node;
import com.zerostudio.language.eval.ExpressionParser.NodeKind;
import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 表达式求值引擎：在 FrameSnapshot 上执行 ExpressionParser 生成的 AST。
 * 支持：
 *  - 字面量、标识符
 *  - 二元运算（算术 / 关系 / 逻辑 / 三目）
 *  - 一元运算（!、-、+）
 *  - 函数调用（用 FrameSnapshot 的 values 作为命名空间）
 *  - 成员访问（a.b.c）
 *  - 数组索引（a[0]）
 *  - 强制类型转换
 *  - 三层变量作用域查找：local → field → static
 *  - null 安全：链式调用时遇到 null 立即返回 "null"，不抛 NPE
 */
public final class EvalEngine {

    public enum ResultKind { VALUE, ERROR, NULL, BREAK, CONTINUE, RETURN }

    public static final class Result {
        public final ResultKind kind;
        public final Object value;
        public final String error;

        private Result(ResultKind kind, Object value, String error) {
            this.kind = kind; this.value = value; this.error = error;
        }

        public static Result of(Object v) { return new Result(ResultKind.VALUE, v, null); }
        public static Result nullValue() { return new Result(ResultKind.NULL, null, null); }
        public static Result error(String msg) { return new Result(ResultKind.ERROR, null, msg); }

        public boolean isError() { return kind == ResultKind.ERROR; }
        public boolean isNull() { return kind == ResultKind.NULL; }
    }

    public Result evaluate(Node ast, FrameSnapshot frame) {
        try {
            return evalNode(ast, frame);
        } catch (Exception e) {
            return Result.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Result evalNode(Node node, FrameSnapshot frame) {
        switch (node.kind) {
            case NUMBER: return Result.of(parseNumber(node.value));
            case STRING: return Result.of(node.value);
            case IDENT: return resolveIdent(node.value, frame);
            case BINARY: return evalBinary(node, frame);
            case UNARY: return evalUnary(node, frame);
            case MEMBER: return evalMember(node, frame);
            case INDEX: return evalIndex(node, frame);
            case CALL: return evalCall(node, frame);
            case CAST: return evalCast(node, frame);
            default: return Result.error("Unsupported node: " + node.kind);
        }
    }

    private Result resolveIdent(String name, FrameSnapshot frame) {
        if ("null".equals(name)) return Result.nullValue();
        if ("true".equals(name)) return Result.of(Boolean.TRUE);
        if ("false".equals(name)) return Result.of(Boolean.FALSE);
        // 三层作用域：local → field → static
        Value v = frame.getValue(name);
        if (v != null) {
            return v.isNull ? Result.nullValue() : Result.of(v.value);
        }
        return Result.error("Unknown identifier: " + name);
    }

    private Result evalBinary(Node n, FrameSnapshot frame) {
        String op = n.value;
        if ("?:".equals(op)) {
            // ternary: cond ? then : else
            if (n.children.size() == 3) {
                Result c = evalNode(n.children.get(0), frame);
                if (c.isError()) return c;
                if (!c.isNull() && !Boolean.FALSE.equals(c.value)) return evalNode(n.children.get(1), frame);
                return evalNode(n.children.get(2), frame);
            }
            // elvis: cond ?: default
            if (n.children.size() == 2) {
                Result c = evalNode(n.children.get(0), frame);
                if (c.isError()) return c;
                if (!c.isNull()) return c;
                return evalNode(n.children.get(1), frame);
            }
        }
        if ("&&".equals(op)) {
            Result l = evalNode(n.children.get(0), frame);
            if (l.isError()) return l;
            if (l.isNull() || Boolean.FALSE.equals(l.value)) return l;
            return evalNode(n.children.get(1), frame);
        }
        if ("||".equals(op)) {
            Result l = evalNode(n.children.get(0), frame);
            if (l.isError()) return l;
            if (!l.isNull() && Boolean.TRUE.equals(l.value)) return l;
            return evalNode(n.children.get(1), frame);
        }
        Result l = evalNode(n.children.get(0), frame);
        if (l.isError()) return l;
        Result r = evalNode(n.children.get(1), frame);
        if (r.isError()) return r;
        return applyBinary(op, l, r);
    }

    private Result applyBinary(String op, Result l, Result r) {
        // null safety for ==, !=, +, and string concat
        if (l.isNull() || r.isNull()) {
            if ("==".equals(op)) return Result.of(l.isNull() == r.isNull());
            if ("!=".equals(op)) return Result.of(l.isNull() != r.isNull());
            if ("+".equals(op)) {
                if (l.isNull() && r.isNull()) return Result.of("nullnull");
                Object non = l.isNull() ? r.value : l.value;
                return Result.of("null" + non);
            }
            // For other operators, return null
            return Result.nullValue();
        }
        Object lo = l.value, ro = r.value;
        switch (op) {
            case "+": return Result.of(add(lo, ro));
            case "-": return Result.of(sub(lo, ro));
            case "*": return Result.of(mul(lo, ro));
            case "/": return Result.of(div(lo, ro));
            case "%": return Result.of(mod(lo, ro));
            case "<": return Result.of(cmp(lo, ro) < 0);
            case ">": return Result.of(cmp(lo, ro) > 0);
            case "<=": return Result.of(cmp(lo, ro) <= 0);
            case ">=": return Result.of(cmp(lo, ro) >= 0);
            case "==": return Result.of(lo.equals(ro));
            case "!=": return Result.of(!lo.equals(ro));
        }
        return Result.error("Unknown binary op: " + op);
    }

    private Result evalUnary(Node n, FrameSnapshot frame) {
        Result inner = evalNode(n.children.get(0), frame);
        if (inner.isError()) return inner;
        if ("!".equals(n.value)) {
            if (inner.isNull()) return Result.of(Boolean.TRUE);
            return Result.of(!Boolean.TRUE.equals(inner.value));
        }
        if ("-".equals(n.value)) {
            if (inner.isNull()) return Result.nullValue();
            if (inner.value instanceof Long) return Result.of(-((Long) inner.value));
            if (inner.value instanceof Double) return Result.of(-((Double) inner.value));
        }
        return inner;
    }

    private Result evalMember(Node n, FrameSnapshot frame) {
        Result target = evalNode(n.children.get(0), frame);
        if (target.isError()) return target;
        if (target.isNull()) return Result.nullValue(); // null safety
        // member access: only works for FrameSnapshot values maps
        if (target.value instanceof FrameSnapshot) {
            Value v = ((FrameSnapshot) target.value).getValue(n.value);
            if (v == null) return Result.error("No member: " + n.value);
            return v.isNull ? Result.nullValue() : Result.of(v.value);
        }
        if (target.value instanceof Map) {
            Object v = ((Map<?, ?>) target.value).get(n.value);
            if (v == null) return Result.error("No key: " + n.value);
            return Result.of(v);
        }
        return Result.error("Cannot read member '" + n.value + "' on " + target.value);
    }

    private Result evalIndex(Node n, FrameSnapshot frame) {
        Result target = evalNode(n.children.get(0), frame);
        if (target.isError()) return target;
        if (target.isNull()) return Result.nullValue();
        Result idx = evalNode(n.children.get(1), frame);
        if (idx.isError()) return idx;
        if (target.value instanceof List) {
            int i = ((Number) idx.value).intValue();
            List<?> list = (List<?>) target.value;
            if (i < 0 || i >= list.size()) return Result.error("Index OOB: " + i);
            Object v = list.get(i);
            return v == null ? Result.nullValue() : Result.of(v);
        }
        if (target.value instanceof Object[]) {
            int i = ((Number) idx.value).intValue();
            Object[] arr = (Object[]) target.value;
            if (i < 0 || i >= arr.length) return Result.error("Index OOB: " + i);
            Object v = arr[i];
            return v == null ? Result.nullValue() : Result.of(v);
        }
        if (target.value instanceof Map) {
            Object key = idx.value;
            Map<?, ?> map = (Map<?, ?>) target.value;
            if (!map.containsKey(key)) return Result.nullValue();
            Object v = map.get(key);
            return v == null ? Result.nullValue() : Result.of(v);
        }
        if (target.value instanceof int[]) {
            int i = ((Number) idx.value).intValue();
            int[] arr = (int[]) target.value;
            if (i < 0 || i >= arr.length) return Result.error("Index OOB: " + i);
            return Result.of((long) arr[i]);
        }
        if (target.value instanceof long[]) {
            int i = ((Number) idx.value).intValue();
            long[] arr = (long[]) target.value;
            if (i < 0 || i >= arr.length) return Result.error("Index OOB: " + i);
            return Result.of(arr[i]);
        }
        if (target.value instanceof String) {
            int i = ((Number) idx.value).intValue();
            String s = (String) target.value;
            if (i < 0 || i >= s.length()) return Result.error("Index OOB: " + i);
            return Result.of(String.valueOf(s.charAt(i)));
        }
        return Result.error("Cannot index " + target.value);
    }

    private Result evalCall(Node n, FrameSnapshot frame) {
        // children[0] is callee, rest are args
        Result callee = evalNode(n.children.get(0), frame);
        if (callee.isError()) return callee;
        if (callee.isNull()) return Result.nullValue();
        List<Result> args = new ArrayList<>();
        for (int i = 1; i < n.children.size(); i++) {
            Result a = evalNode(n.children.get(i), frame);
            if (a.isError()) return a;
            args.add(a);
        }
        if (callee.value instanceof Callable) {
            try {
                return ((Callable) callee.value).call(args);
            } catch (Exception e) {
                return Result.error(e.getMessage());
            }
        }
        return Result.error("Not callable: " + callee.value);
    }

    private Result evalCast(Node n, FrameSnapshot frame) {
        Result inner = evalNode(n.children.get(0), frame);
        if (inner.isError()) return inner;
        if (inner.isNull()) return Result.nullValue();
        return Result.of(inner.value); // type label only, value passthrough
    }

    private Object parseNumber(String s) {
        if (s.endsWith("L") || s.endsWith("l")) return Long.parseLong(s.substring(0, s.length() - 1));
        if (s.endsWith("F") || s.endsWith("f")) return Float.parseFloat(s.substring(0, s.length() - 1));
        if (s.endsWith("D") || s.endsWith("d")) return Double.parseDouble(s.substring(0, s.length() - 1));
        if (s.contains(".")) return Double.parseDouble(s);
        return Long.parseLong(s);
    }

    private Object add(Object a, Object b) {
        if (a instanceof String || b instanceof String) return String.valueOf(a) + String.valueOf(b);
        if (a instanceof Double || b instanceof Double) return ((Number) a).doubleValue() + ((Number) b).doubleValue();
        return ((Number) a).longValue() + ((Number) b).longValue();
    }
    private Object sub(Object a, Object b) {
        if (a instanceof Double || b instanceof Double) return ((Number) a).doubleValue() - ((Number) b).doubleValue();
        return ((Number) a).longValue() - ((Number) b).longValue();
    }
    private Object mul(Object a, Object b) {
        if (a instanceof Double || b instanceof Double) return ((Number) a).doubleValue() * ((Number) b).doubleValue();
        return ((Number) a).longValue() * ((Number) b).longValue();
    }
    private Object div(Object a, Object b) {
        if (((Number) b).doubleValue() == 0) throw new ArithmeticException("/ by zero");
        if (a instanceof Double || b instanceof Double) return ((Number) a).doubleValue() / ((Number) b).doubleValue();
        return ((Number) a).longValue() / ((Number) b).longValue();
    }
    private Object mod(Object a, Object b) {
        return ((Number) a).longValue() % ((Number) b).longValue();
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private int cmp(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        return ((Comparable) a).compareTo(b);
    }

    public interface Callable { Result call(List<Result> args); }
}
