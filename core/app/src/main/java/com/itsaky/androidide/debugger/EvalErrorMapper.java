/*
 *  ZeroStudio IDE - 表达式求值错误提示翻译
 *
 *  PR-D6 batch 3/3: EvalEngine 返回的错误是英文 (例如 "division by zero",
 *  "io: connection reset") — 弹到 UI 上对中文用户不友好。本类把常见
 *  错误翻译成中文短句,匹配不到时回退到原 error 字符串。
 *
 *  匹配采用子串包含,不要求严格相等 — 这样既能匹配 "io: xxx" 也能
 *  匹配未来 EvalEngine 内部多包一层的格式 ("EvalError: io: xxx")。
 */

package com.itsaky.androidide.debugger;

import androidx.annotation.NonNull;

public final class EvalErrorMapper {

    private EvalErrorMapper() {}

    /**
     * 把 EvalResult.error 翻译成中文提示。{@code raw} 为 null / 空时
     * 返回默认提示"求值失败"。
     */
    @NonNull
    public static String friendly(@NonNull String raw) {
        if (raw.isEmpty()) return "求值失败";
        String lower = raw.toLowerCase();
        if (lower.contains("empty expression")) return "表达式为空";
        if (lower.contains("parse error")) {
            // 保留原始细节方便用户定位语法错误
            return "语法错误:" + raw.substring("parse error:".length()).trim();
        }
        if (lower.contains("division by zero")) return "除数不能为零";
        if (lower.contains("modulo by zero")) return "取模除数不能为零";
        if (lower.contains("io:") || lower.contains("i/o") || lower.contains("connection")
                || lower.contains("closed")) {
            return "调试器连接已断开,请重试";
        }
        if (lower.contains("no 'this'")) return "当前栈帧没有 this";
        if (lower.contains("trailing input")) return "表达式尾部有未识别的内容";
        if (lower.contains("unsupported expression kind")) return "暂不支持该表达式";
        if (lower.contains("unsupported binary operator")) return "暂不支持该二元运算符";
        if (lower.contains("unsupported op")) return "暂不支持该运算符";
        if (lower.contains("field access on non-object")) return "字段访问需要对象";
        if (lower.contains("field access without receiver")) return "字段访问缺少对象";
        if (lower.contains("index on non-array")) return "下标访问需要数组";
        if (lower.contains("array index without")) return "数组下标访问格式错误";
        if (lower.contains("method call on non-object")) return "方法调用需要对象";
        if (lower.contains("method call needs a receiver")) return "方法调用缺少对象";
        if (lower.contains("requires numeric")) return "运算符需要数值操作数";
        if (lower.contains("malformed ternary")) return "三元运算符格式错误";
        // 未匹配到:直接返回原文(可能是 EvalEngine 新加的错误类型,留给
        // 工程师后续扩展映射表)
        return raw;
    }
}
