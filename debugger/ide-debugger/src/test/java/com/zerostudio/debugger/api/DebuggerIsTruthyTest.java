/*
 *  ZeroStudio IDE - Debugger.isTruthy() 单元测试 (PR-8)
 *
 *  isTruthy 决定条件断点是否真正挂起:
 *    - true  -> 挂起(让用户看到)
 *    - false -> 静默 resume(不挂起)
 *
 *  覆盖每个 EvalResult.Tag 的真值判定 + 边界情况(null displayValue /
 *  null objectId)。
 */

package com.zerostudio.debugger.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.api.EvalResult.Tag;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DebuggerIsTruthyTest {

    @Test
    public void boolean_trueIsTruthy() {
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.BOOLEAN, "Z", "true")));
    }

    @Test
    public void boolean_falseIsFalsy() {
        assertFalse(Debugger.isTruthy(EvalResult.of(Tag.BOOLEAN, "Z", "false")));
    }

    @Test
    public void int_zeroIsFalsy() {
        assertFalse(Debugger.isTruthy(EvalResult.of(Tag.INT, "I", "0")));
    }

    @Test
    public void int_nonzeroIsTruthy() {
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.INT, "I", "1")));
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.INT, "I", "-1")));
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.INT, "I", "42")));
    }

    @Test
    public void long_zeroIsFalsy() {
        assertFalse(Debugger.isTruthy(EvalResult.of(Tag.LONG, "J", "0")));
    }

    @Test
    public void long_nonzeroIsTruthy() {
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.LONG, "J", "100")));
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.LONG, "J", "-1")));
    }

    @Test
    public void short_zeroIsFalsy() {
        assertFalse(Debugger.isTruthy(EvalResult.of(Tag.SHORT, "S", "0")));
    }

    @Test
    public void short_nonzeroIsTruthy() {
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.SHORT, "S", "1")));
    }

    @Test
    public void byte_zeroIsFalsy() {
        assertFalse(Debugger.isTruthy(EvalResult.of(Tag.BYTE, "B", "0")));
    }

    @Test
    public void byte_nonzeroIsTruthy() {
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.BYTE, "B", "1")));
    }

    @Test
    public void char_zeroIsFalsy() {
        assertFalse(Debugger.isTruthy(EvalResult.of(Tag.CHAR, "C", "0")));
    }

    @Test
    public void char_nonzeroIsTruthy() {
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.CHAR, "C", "65")));
    }

    @Test
    public void float_zeroIsFalsy() {
        assertFalse(Debugger.isTruthy(EvalResult.of(Tag.FLOAT, "F", "0.0")));
    }

    @Test
    public void float_nonzeroIsTruthy() {
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.FLOAT, "F", "1.5")));
    }

    @Test
    public void double_zeroIsFalsy() {
        assertFalse(Debugger.isTruthy(EvalResult.of(Tag.DOUBLE, "D", "0.0")));
    }

    @Test
    public void double_nonzeroIsTruthy() {
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.DOUBLE, "D", "3.14")));
    }

    @Test
    public void object_nullIdIsFalsy() {
        // objectId == 0 表示 null 引用 -> 不挂起
        assertFalse(Debugger.isTruthy(EvalResult.object(0L, "Ljava/lang/Object;")));
    }

    @Test
    public void object_nonNullIdIsTruthy() {
        assertTrue(Debugger.isTruthy(EvalResult.object(0xdeadL, "Ljava/lang/Object;")));
    }

    @Test
    public void array_nullIdIsFalsy() {
        // 用 of() 模拟一个 ARRAY + null id 的场景
        EvalResult r = EvalResult.of(Tag.ARRAY, "[Ljava/lang/Object;", "<array id=0>");
        // EvalResult.of 把 objectId 固定为 0L
        assertFalse(Debugger.isTruthy(r));
    }

    @Test
    public void array_nonNullIdIsTruthy() {
        // 用 string() 复用 objectId 字段
        assertTrue(Debugger.isTruthy(EvalResult.string(0xabcL, "x")));
    }

    @Test
    public void nullDisplayValue_defaultsToTruthy() {
        // 错误结果或未知结果走 default 分支 -> 挂起
        assertTrue(Debugger.isTruthy(EvalResult.error("io: timeout")));
    }

    @Test
    public void int_malformedDisplayValue_defaultsToTruthy() {
        // Long.parseLong 失败 -> catch 返回 true
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.INT, "I", "not-a-number")));
    }

    @Test
    public void double_malformedDisplayValue_defaultsToTruthy() {
        // Double.parseDouble 失败 -> catch 返回 true
        assertTrue(Debugger.isTruthy(EvalResult.of(Tag.DOUBLE, "D", "nope")));
    }
}
