/*
 *  ZeroStudio IDE - EvalErrorMapper 单元测试 (PR-D8.1)
 *
 *  覆盖所有 friendly() 翻译分支:
 *    - 空 / null / 默认回退
 *    - 空表达式 / 解析错误 / 尾部未识别 / IO 错误
 *    - 除零 / 取模除零
 *    - 字段访问 / 数组下标 / 方法调用错误
 *    - 不支持的操作符
 *    - 未知错误回退到原文
 *    - 大小写不敏感 (lower())
 */

package com.itsaky.androidide.debugger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EvalErrorMapperTest {

    @Test
    public void empty_returnsDefault() {
        assertEquals("求值失败", EvalErrorMapper.friendly(""));
    }

    @Test
    public void emptyExpression() {
        assertEquals("表达式为空", EvalErrorMapper.friendly("(empty expression)"));
    }

    @Test
    public void parseError_keepsDetail() {
        // "parse error: mismatched input 'foo'" -> "语法错误:mismatched input 'foo'"
        String out = EvalErrorMapper.friendly("parse error: mismatched input 'foo'");
        assertTrue("应包含 '语法错误:'", out.startsWith("语法错误:"));
        assertTrue("应保留原始 detail", out.contains("mismatched input 'foo'"));
    }

    @Test
    public void divisionByZero() {
        assertEquals("除数不能为零", EvalErrorMapper.friendly("division by zero"));
    }

    @Test
    public void moduloByZero() {
        assertEquals("取模除数不能为零", EvalErrorMapper.friendly("modulo by zero"));
    }

    @Test
    public void ioError_friendly() {
        assertEquals("调试器连接已断开,请重试",
                EvalErrorMapper.friendly("io: connection reset"));
    }

    @Test
    public void noThis() {
        assertEquals("当前栈帧没有 this", EvalErrorMapper.friendly("no 'this' in current frame"));
    }

    @Test
    public void trailingInput() {
        assertEquals("表达式尾部有未识别的内容",
                EvalErrorMapper.friendly("trailing input: 'foo'"));
    }

    @Test
    public void unsupportedExpressionKind() {
        assertEquals("暂不支持该表达式",
                EvalErrorMapper.friendly("unsupported expression kind: lambda"));
    }

    @Test
    public void unsupportedBinaryOperator() {
        assertEquals("暂不支持该二元运算符",
                EvalErrorMapper.friendly("unsupported binary operator: ^^^"));
    }

    @Test
    public void fieldAccessOnNonObject() {
        assertEquals("字段访问需要对象",
                EvalErrorMapper.friendly("field access on non-object"));
    }

    @Test
    public void fieldAccessWithoutReceiver() {
        assertEquals("字段访问缺少对象",
                EvalErrorMapper.friendly("field access without receiver"));
    }

    @Test
    public void indexOnNonArray() {
        assertEquals("下标访问需要数组",
                EvalErrorMapper.friendly("index on non-array (sig=I)"));
    }

    @Test
    public void methodCallOnNonObject() {
        assertEquals("方法调用需要对象",
                EvalErrorMapper.friendly("method call on non-object"));
    }

    @Test
    public void methodCallNeedsReceiver() {
        assertEquals("方法调用缺少对象",
                EvalErrorMapper.friendly("method call needs a receiver"));
    }

    @Test
    public void requiresNumeric() {
        assertEquals("运算符需要数值操作数",
                EvalErrorMapper.friendly("operator '+' requires numeric left operand (got Ljava/lang/String;)"));
    }

    @Test
    public void unknownError_fallsBackToOriginal() {
        String raw = "completely unknown error xyz";
        assertEquals(raw, EvalErrorMapper.friendly(raw));
    }

    @Test
    public void caseInsensitive() {
        // 大写也匹配
        assertEquals("除数不能为零", EvalErrorMapper.friendly("DIVISION BY ZERO"));
        assertEquals("取模除数不能为零", EvalErrorMapper.friendly("Modulo By Zero"));
    }

    @Test
    public void ioConnectionClosed() {
        // "closed" 关键字也匹配 IO
        assertEquals("调试器连接已断开,请重试",
                EvalErrorMapper.friendly("io: stream already closed"));
    }

    @Test
    public void malformedTernary() {
        assertEquals("三元运算符格式错误", EvalErrorMapper.friendly("malformed ternary"));
    }
}
