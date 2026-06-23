/*
 *  ZeroStudio IDE - EvalEngine helpers unit tests (PR-9)
 *
 *  直接测 EvalEngine 里新加的 package-private 辅助函数:
 *    - buildSignature: 把 EvalResult 列表转成 JDWP 方法签名
 *    - encodeValue:    把单个 EvalResult 写成 JDWP 原始字节
 *    - tagToSignature: tag -> 签名字符串
 *    - countArity:     统计方法签名里参数个数
 *
 *  不再起 EvalEngine/FakeJdwpClient,只对纯函数做单测。
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.api.EvalResult;
import com.zerostudio.debugger.api.EvalResult.Tag;
import com.zerostudio.debugger.util.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EvalEngineHelpersTest {

    // ---------- buildSignature ----------

    @Test
    public void buildSignature_emptyArgsIsVoid() {
        assertEquals("()V", EvalEngine.buildSignature(new ArrayList<>()));
    }

    @Test
    public void buildSignature_singleLongArg() {
        assertEquals("(J)V", EvalEngine.buildSignature(args(EvalResult.of(Tag.LONG, "J", "1"))));
    }

    @Test
    public void buildSignature_twoLongArgs() {
        assertEquals("(JJ)V", EvalEngine.buildSignature(args(
                EvalResult.of(Tag.LONG, "J", "1"),
                EvalResult.of(Tag.LONG, "J", "2"))));
    }

    @Test
    public void buildSignature_mixedPrimitives() {
        assertEquals("(IJDLjava/lang/String;)V", EvalEngine.buildSignature(args(
                EvalResult.of(Tag.INT, "I", "1"),
                EvalResult.of(Tag.LONG, "J", "2"),
                EvalResult.of(Tag.DOUBLE, "D", "3.0"),
                EvalResult.string(0xabcL, "hi"))));
    }

    // ---------- encodeValue ----------

    @Test
    public void encodeValue_booleanTrue() {
        ByteBuf b = new ByteBuf();
        EvalEngine.encodeValue(b, EvalResult.of(Tag.BOOLEAN, "Z", "true"));
        assertArrayEquals(new byte[] {1}, b.toByteArray());
    }

    @Test
    public void encodeValue_booleanFalse() {
        ByteBuf b = new ByteBuf();
        EvalEngine.encodeValue(b, EvalResult.of(Tag.BOOLEAN, "Z", "false"));
        assertArrayEquals(new byte[] {0}, b.toByteArray());
    }

    @Test
    public void encodeValue_intZero() {
        ByteBuf b = new ByteBuf();
        EvalEngine.encodeValue(b, EvalResult.of(Tag.INT, "I", "0"));
        assertArrayEquals(new byte[] {0, 0, 0, 0}, b.toByteArray());
    }

    @Test
    public void encodeValue_int42() {
        ByteBuf b = new ByteBuf();
        EvalEngine.encodeValue(b, EvalResult.of(Tag.INT, "I", "42"));
        assertArrayEquals(new byte[] {0, 0, 0, 42}, b.toByteArray());
    }

    @Test
    public void encodeValue_longMaxValue() {
        ByteBuf b = new ByteBuf();
        EvalEngine.encodeValue(b, EvalResult.of(Tag.LONG, "J", "100"));
        // big-endian 8 bytes
        assertArrayEquals(
                new byte[] {0, 0, 0, 0, 0, 0, 0, 100},
                b.toByteArray());
    }

    @Test
    public void encodeValue_floatNaNBits() {
        ByteBuf b = new ByteBuf();
        EvalEngine.encodeValue(b, EvalResult.of(Tag.FLOAT, "F", "1.5"));
        // 1.5f in IEEE 754 is 0x3FC00000 -> 0x3F, 0xC0, 0x00, 0x00
        assertArrayEquals(new byte[] {0x3f, (byte) 0xc0, 0x00, 0x00}, b.toByteArray());
    }

    @Test
    public void encodeValue_doublePi() {
        ByteBuf b = new ByteBuf();
        EvalEngine.encodeValue(b, EvalResult.of(Tag.DOUBLE, "D", "1.0"));
        // 1.0 in IEEE 754 double is 0x3FF0000000000000
        assertArrayEquals(
                new byte[] {0x3f, (byte) 0xf0, 0, 0, 0, 0, 0, 0},
                b.toByteArray());
    }

    @Test
    public void encodeValue_objectWritesObjectId() {
        ByteBuf b = new ByteBuf();
        EvalEngine.encodeValue(b, EvalResult.object(0xdeadbeefL, "Ljava/lang/String;"));
        // 8 bytes big-endian objectId
        assertArrayEquals(
                new byte[] {(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef,
                            0, 0, 0, 0},
                b.toByteArray());
    }

    @Test
    public void encodeValue_emptySignatureDefaultsToObjectId() {
        ByteBuf b = new ByteBuf();
        EvalEngine.encodeValue(b, EvalResult.object(0x42L, ""));
        assertArrayEquals(
                new byte[] {0, 0, 0, 0, 0, 0, 0, 0x42},
                b.toByteArray());
    }

    @Test(expected = IllegalStateException.class)
    public void encodeValue_unknownPrefixThrows() {
        ByteBuf b = new ByteBuf();
        // 'Q' isn't a real JDWP type code
        EvalEngine.encodeValue(b, EvalResult.of(Tag.OBJECT, "Qfoo;", "<object id=1>"));
    }

    @Test
    public void encodeValue_byteAtMin() {
        ByteBuf b = new ByteBuf();
        EvalEngine.encodeValue(b, EvalResult.of(Tag.BYTE, "B", "-128"));
        assertArrayEquals(new byte[] { (byte) 0x80 }, b.toByteArray());
    }

    // ---------- tagToSignature ----------

    @Test
    public void tagToSignature_allPrimitiveTags() {
        assertEquals("V", EvalEngine.tagToSignature((byte) 'V'));
        assertEquals("Z", EvalEngine.tagToSignature((byte) 'Z'));
        assertEquals("B", EvalEngine.tagToSignature((byte) 'B'));
        assertEquals("C", EvalEngine.tagToSignature((byte) 'C'));
        assertEquals("S", EvalEngine.tagToSignature((byte) 'S'));
        assertEquals("I", EvalEngine.tagToSignature((byte) 'I'));
        assertEquals("J", EvalEngine.tagToSignature((byte) 'J'));
        assertEquals("F", EvalEngine.tagToSignature((byte) 'F'));
        assertEquals("D", EvalEngine.tagToSignature((byte) 'D'));
    }

    @Test
    public void tagToSignature_objectTagsDefaultToObject() {
        assertEquals("Ljava/lang/Object;", EvalEngine.tagToSignature((byte) 'L'));
        assertEquals("[Ljava/lang/Object;", EvalEngine.tagToSignature((byte) '['));
    }

    @Test
    public void tagToSignature_unknownDefaultsToObject() {
        assertEquals("Ljava/lang/Object;", EvalEngine.tagToSignature((byte) 'Q'));
    }

    // ---------- countArity ----------

    @Test
    public void countArity_noArgs() {
        assertEquals(0, EvalEngine.countArity("()V"));
    }

    @Test
    public void countArity_twoInts() {
        assertEquals(2, EvalEngine.countArity("(II)I"));
    }

    @Test
    public void countArity_stringAndLong() {
        assertEquals(2, EvalEngine.countArity("(Ljava/lang/String;J)V"));
    }

    @Test
    public void countArity_arrayArg() {
        assertEquals(1, EvalEngine.countArity("([I)V"));
        assertEquals(1, EvalEngine.countArity("([Ljava/lang/Object;)V"));
    }

    @Test
    public void countArity_mixed() {
        assertEquals(4, EvalEngine.countArity("(IZJLjava/lang/String;)V"));
    }

    @Test
    public void countArity_malformedSignatures() {
        assertEquals(0, EvalEngine.countArity(""));
        assertEquals(0, EvalEngine.countArity("no parens"));
        assertEquals(0, EvalEngine.countArity("(broken"));
    }

    // ---------- Phase A1: applyBinaryOp (纯函数) ----------

    @Test
    public void applyBinaryOp_additionOfLongs() {
        EvalResult r = EvalEngine.applyBinaryOp("+",
                EvalResult.of(Tag.LONG, "J", "10"),
                EvalResult.of(Tag.LONG, "J", "32"));
        assertEquals(Tag.LONG, r.tag);
        assertEquals("J", r.typeSignature);
        assertEquals("42", r.displayValue);
    }

    @Test
    public void applyBinaryOp_subtractionOfLongs() {
        EvalResult r = EvalEngine.applyBinaryOp("-",
                EvalResult.of(Tag.LONG, "J", "10"),
                EvalResult.of(Tag.LONG, "J", "3"));
        assertEquals("7", r.displayValue);
    }

    @Test
    public void applyBinaryOp_multiplicationOfLongs() {
        EvalResult r = EvalEngine.applyBinaryOp("*",
                EvalResult.of(Tag.LONG, "J", "6"),
                EvalResult.of(Tag.LONG, "J", "7"));
        assertEquals("42", r.displayValue);
    }

    @Test
    public void applyBinaryOp_divisionOfLongs() {
        EvalResult r = EvalEngine.applyBinaryOp("/",
                EvalResult.of(Tag.LONG, "J", "20"),
                EvalResult.of(Tag.LONG, "J", "4"));
        assertEquals("5", r.displayValue);
    }

    @Test
    public void applyBinaryOp_moduloOfLongs() {
        EvalResult r = EvalEngine.applyBinaryOp("%",
                EvalResult.of(Tag.LONG, "J", "10"),
                EvalResult.of(Tag.LONG, "J", "3"));
        assertEquals("1", r.displayValue);
    }

    @Test
    public void applyBinaryOp_divisionByZeroLong_returnsError() {
        EvalResult r = EvalEngine.applyBinaryOp("/",
                EvalResult.of(Tag.LONG, "J", "1"),
                EvalResult.of(Tag.LONG, "J", "0"));
        assertTrue(r.isError());
        assertTrue(r.error, r.error.contains("division by zero"));
    }

    @Test
    public void applyBinaryOp_moduloByZeroLong_returnsError() {
        EvalResult r = EvalEngine.applyBinaryOp("%",
                EvalResult.of(Tag.LONG, "J", "1"),
                EvalResult.of(Tag.LONG, "J", "0"));
        assertTrue(r.isError());
        assertTrue(r.error, r.error.contains("modulo by zero"));
    }

    @Test
    public void applyBinaryOp_doubleArithmetic_widensToDouble() {
        EvalResult r = EvalEngine.applyBinaryOp("+",
                EvalResult.of(Tag.LONG, "J", "1"),
                EvalResult.of(Tag.DOUBLE, "D", "0.5"));
        assertEquals(Tag.DOUBLE, r.tag);
        assertEquals("D", r.typeSignature);
        assertEquals("1.5", r.displayValue);
    }

    @Test
    public void applyBinaryOp_divisionByZeroDouble_returnsError() {
        EvalResult r = EvalEngine.applyBinaryOp("/",
                EvalResult.of(Tag.DOUBLE, "D", "1.0"),
                EvalResult.of(Tag.DOUBLE, "D", "0.0"));
        assertTrue(r.isError());
        assertTrue(r.error, r.error.contains("division by zero"));
    }

    @Test
    public void applyBinaryOp_stringOperand_returnsError() {
        EvalResult r = EvalEngine.applyBinaryOp("+",
                EvalResult.of(Tag.STRING, "Ljava/lang/String;", "x"),
                EvalResult.of(Tag.LONG, "J", "1"));
        assertTrue(r.isError());
        assertTrue(r.error, r.error.contains("requires numeric left operand"));
    }

    @Test
    public void applyBinaryOp_objectOperand_returnsError() {
        EvalResult r = EvalEngine.applyBinaryOp("+",
                EvalResult.object(0x100L, "Lcom/example/Foo;"),
                EvalResult.of(Tag.LONG, "J", "1"));
        assertTrue(r.isError());
        assertTrue(r.error, r.error.contains("requires numeric left operand"));
    }

    @Test
    public void applyBinaryOp_unknownOperator_returnsError() {
        // Defensive: parser should never produce this, but the helper
        // must not throw — it must report a clean error result.
        EvalResult r = EvalEngine.applyBinaryOp("?",
                EvalResult.of(Tag.LONG, "J", "1"),
                EvalResult.of(Tag.LONG, "J", "2"));
        assertTrue(r.isError());
        assertTrue(r.error, r.error.contains("unsupported binary operator"));
    }

    @Test
    public void applyBinaryOp_negativeLongResult() {
        // 5 - 10 == -5
        EvalResult r = EvalEngine.applyBinaryOp("-",
                EvalResult.of(Tag.LONG, "J", "5"),
                EvalResult.of(Tag.LONG, "J", "10"));
        assertEquals("-5", r.displayValue);
    }

    // ---------- Phase A2: applyComparisonOp (纯函数) ----------

    @Test
    public void applyComparisonOp_equalLongs() {
        EvalResult r = EvalEngine.applyComparisonOp("==",
                EvalResult.of(Tag.LONG, "J", "5"),
                EvalResult.of(Tag.LONG, "J", "5"));
        assertEquals(Tag.BOOLEAN, r.tag);
        assertEquals("Z", r.typeSignature);
        assertEquals("true", r.displayValue);
    }

    @Test
    public void applyComparisonOp_notEqualLongs() {
        EvalResult r = EvalEngine.applyComparisonOp("!=",
                EvalResult.of(Tag.LONG, "J", "5"),
                EvalResult.of(Tag.LONG, "J", "6"));
        assertEquals("true", r.displayValue);
    }

    @Test
    public void applyComparisonOp_lessThan() {
        EvalResult r = EvalEngine.applyComparisonOp("<",
                EvalResult.of(Tag.LONG, "J", "3"),
                EvalResult.of(Tag.LONG, "J", "5"));
        assertEquals("true", r.displayValue);
        r = EvalEngine.applyComparisonOp("<",
                EvalResult.of(Tag.LONG, "J", "5"),
                EvalResult.of(Tag.LONG, "J", "3"));
        assertEquals("false", r.displayValue);
    }

    @Test
    public void applyComparisonOp_greaterThan() {
        EvalResult r = EvalEngine.applyComparisonOp(">",
                EvalResult.of(Tag.LONG, "J", "5"),
                EvalResult.of(Tag.LONG, "J", "3"));
        assertEquals("true", r.displayValue);
    }

    @Test
    public void applyComparisonOp_lessEquals() {
        EvalResult r = EvalEngine.applyComparisonOp("<=",
                EvalResult.of(Tag.LONG, "J", "5"),
                EvalResult.of(Tag.LONG, "J", "5"));
        assertEquals("true", r.displayValue);
        r = EvalEngine.applyComparisonOp("<=",
                EvalResult.of(Tag.LONG, "J", "6"),
                EvalResult.of(Tag.LONG, "J", "5"));
        assertEquals("false", r.displayValue);
    }

    @Test
    public void applyComparisonOp_greaterEquals() {
        EvalResult r = EvalEngine.applyComparisonOp(">=",
                EvalResult.of(Tag.LONG, "J", "5"),
                EvalResult.of(Tag.LONG, "J", "5"));
        assertEquals("true", r.displayValue);
    }

    @Test
    public void applyComparisonOp_doubleCompare() {
        EvalResult r = EvalEngine.applyComparisonOp("<",
                EvalResult.of(Tag.DOUBLE, "D", "1.5"),
                EvalResult.of(Tag.DOUBLE, "D", "2.5"));
        assertEquals("true", r.displayValue);
    }

    @Test
    public void applyComparisonOp_objectIdentityEqual() {
        EvalResult r = EvalEngine.applyComparisonOp("==",
                EvalResult.object(0x100L, "Lcom/example/Foo;"),
                EvalResult.object(0x100L, "Lcom/example/Foo;"));
        assertEquals("true", r.displayValue);
    }

    @Test
    public void applyComparisonOp_objectIdentityNotEqual() {
        EvalResult r = EvalEngine.applyComparisonOp("==",
                EvalResult.object(0x100L, "Lcom/example/Foo;"),
                EvalResult.object(0x101L, "Lcom/example/Foo;"));
        assertEquals("false", r.displayValue);
        r = EvalEngine.applyComparisonOp("!=",
                EvalResult.object(0x100L, "Lcom/example/Foo;"),
                EvalResult.object(0x101L, "Lcom/example/Foo;"));
        assertEquals("true", r.displayValue);
    }

    @Test
    public void applyComparisonOp_refVsPrimitive_neverEqual() {
        EvalResult r = EvalEngine.applyComparisonOp("==",
                EvalResult.object(0x100L, "Lcom/example/Foo;"),
                EvalResult.of(Tag.LONG, "J", "0"));
        assertEquals("false", r.displayValue);
    }

    @Test
    public void applyComparisonOp_relationalOnNonNumeric_returnsError() {
        EvalResult r = EvalEngine.applyComparisonOp("<",
                EvalResult.of(Tag.STRING, "Ljava/lang/String;", "x"),
                EvalResult.of(Tag.LONG, "J", "1"));
        assertTrue(r.isError());
        assertTrue(r.error, r.error.contains("requires numeric"));
    }

    @Test
    public void applyComparisonOp_unknownOp_returnsError() {
        EvalResult r = EvalEngine.applyComparisonOp("?",
                EvalResult.of(Tag.LONG, "J", "1"),
                EvalResult.of(Tag.LONG, "J", "2"));
        assertTrue(r.isError());
        assertTrue(r.error, r.error.contains("unsupported comparison op"));
    }

    // ---------- 辅助 ----------

    @SafeVarargs
    private static List<EvalResult> args(EvalResult... es) {
        return new ArrayList<>(Arrays.asList(es));
    }
}
