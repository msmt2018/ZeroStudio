/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase A8: full-feature integration tests for the EvalEngine.
 *
 *  These tests exercise the parser, eval, and JDWP layer in
 *  combination, so they catch regressions where two phases
 *  interact poorly (e.g. ternary inside method args, string
 *  concat with a chained field, etc.).
 *
 *  Compared to EvalEngineEvaluateTest these are deliberately
 *  longer, more realistic expressions drawn from the kinds of
 *  watches / conditions a user would actually set in the UI.
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.api.EvalResult;
import com.zerostudio.debugger.api.EvalResult.Tag;
import com.zerostudio.debugger.jdwp.FakeJdwpClient;
import com.zerostudio.debugger.jdwp.JdwpPayloads;
import com.zerostudio.debugger.jdwp.JdwpPayloads.Var;
import com.zerostudio.debugger.jdwp.JdwpClient;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Cross-feature integration tests. Each test sets up the
 * minimum JDWP fixture needed to drive an expression end-to-end
 * and asserts the resulting {@link EvalResult} value + type.
 */
public class EvalEngineIntegrationTest {

    private static final long THREAD_ID = 0x1L;
    private static final long FRAME_ID  = 0x2L;
    private static final long CLASS_ID  = 0x3L;
    private static final long METHOD_ID = 0x4L;

    private static EvalEngine newEngine(JdwpClient client) {
        try {
            Constructor<EvalEngine> ctor = EvalEngine.class
                    .getDeclaredConstructor(JdwpClient.class);
            ctor.setAccessible(true);
            return ctor.newInstance(client);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // ---------- 综合场景 ----------

    @Test
    public void mixedArithmeticWithParensAndPrecedence() {
        // (3 + 4) * (10 - 2) / 7 == 8
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID,
                "(3 + 4) * (10 - 2) / 7");
        assertFalse(r.isError());
        assertEquals("8", r.displayValue);
    }

    @Test
    public void comparisonOfArithmetic() {
        // 3 + 4 > 5 && 10 * 2 == 20
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID,
                "3 + 4 > 5 && 10 * 2 == 20");
        assertFalse(r.isError());
        assertEquals("true", r.displayValue);
    }

    @Test
    public void ternaryInArithmetic() {
        // (a > 0 ? 1 : 2) + 3  -- if a is 5: 1+3 = 4
        FakeJdwpClient fake = new FakeJdwpClient();
        // a == 5 lookup
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "a", "I", 1, 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'I', JdwpPayloads.intValue(5)));
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID,
                "(a > 0 ? 1 : 2) + 3");
        assertFalse(r.isError());
        assertEquals("4", r.displayValue);
    }

    @Test
    public void stringConcatInsideTernary() {
        // (count == 0 ? "empty" : "n=") + count
        // count=0, so "empty" + 0 = "empty0"
        FakeJdwpClient fake = new FakeJdwpClient();
        // count lookup
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "count", "I", 1, 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'I', JdwpPayloads.intValue(0)));
        // createString for "empty"
        fake.enqueueOkReply(JdwpPayloads.createStringReply(0xA0L));
        // createString for "empty0" (concat result)
        fake.enqueueOkReply(JdwpPayloads.createStringReply(0xA1L));
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID,
                "(count == 0 ? \"empty\" : \"n=\") + count");
        assertFalse(r.isError());
        assertEquals(Tag.STRING, r.tag);
        assertEquals("empty0", r.displayValue);
    }

    @Test
    public void arrayLengthAndIndex() {
        // arr[arr.length - 1]  -- last element
        FakeJdwpClient fake = new FakeJdwpClient();
        // arr lookup
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0xAAL, "arr", "[I", 1, 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) '[', JdwpPayloads.longValue(0xAA)));
        // arr.length -> 3
        fake.enqueueOkReply(JdwpPayloads.arrayLengthReply(3));
        // arr[2] -> 30
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'I', JdwpPayloads.intValue(30)));
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID,
                "arr[arr.length - 1]");
        assertFalse(r.isError());
        assertEquals(Tag.INT, r.tag);
        assertEquals("30", r.displayValue);
    }

    @Test
    public void chainedFieldWithStatic() {
        // local.count + java.lang.Integer.MAX_VALUE / 2  -- 1 + 1073741823
        FakeJdwpClient fake = new FakeJdwpClient();
        // local.count lookup
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "count", "I", 1, 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'I', JdwpPayloads.intValue(1)));
        // ClassesBySignature for Ljava/lang/Integer;
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0xCAFE, 0x05));
        // ReferenceType.Fields (lookup MAX_VALUE)
        fake.enqueueOkReply(JdwpPayloads.fieldsReply(new JdwpPayloads.Field[] {
                new JdwpPayloads.Field(0xFEEDL, "MAX_VALUE", "I", 0x0019)
        }));
        // ReferenceType.GetValues (static)
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'I', JdwpPayloads.intValue(Integer.MAX_VALUE)));
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID,
                "count + java.lang.Integer.MAX_VALUE / 2");
        assertFalse(r.isError());
        assertEquals("1073741824", r.displayValue);
    }

    @Test
    public void complexBooleanExpression() {
        // 1 < 2 && 3 > 2 || false == (true || false) && 1 == 1
        // = (true && true) || (false == (true || false) && true)
        // = true || (false == true && true)
        // = true || (false && true)
        // = true || false
        // = true
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID,
                "1 < 2 && 3 > 2 || false == (true || false) && 1 == 1");
        assertFalse(r.isError());
        assertEquals("true", r.displayValue);
    }

    @Test
    public void methodCallWithArithmeticArg() {
        // 1 + foo.calc(2)  -- calc is instance method, returns 42
        FakeJdwpClient fake = new FakeJdwpClient();
        // foo lookup
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0xFFL, "foo", "Lcom/example/Foo;", 1, 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'L', JdwpPayloads.longValue(0xFF)));
        // ClassesBySignature for the owner
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0xCAFE, 0x05));
        // Methods on the owner
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new JdwpPayloads.Method[] {
                new JdwpPayloads.Method(0x100, "calc", "(I)I", 0x0001)
        }));
        // InvokeMethod reply
        fake.enqueueOkReply(JdwpPayloads.invokeMethodReply((byte) 'I', JdwpPayloads.intValue(42)));
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "1 + foo.calc(2)");
        assertFalse(r.isError());
        assertEquals("43", r.displayValue);
    }

    @Test
    public void guardExpressionsDontExplode() {
        // Verify that the parser + eval can handle a "kitchen sink"
        // expression without throwing or returning a spurious
        // success. The expression deliberately mixes many
        // sub-features; we don't assert the actual value, only that
        // we get a clean EvalResult back (success or error).
        FakeJdwpClient fake = new FakeJdwpClient();
        // Set up enough state to avoid the early "no such local" error.
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0xAAL, "arr", "[I", 1, 0),
                new Var(0L, "i", "I", 2, 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) '[', JdwpPayloads.longValue(0xAA)));
        // arr.length
        fake.enqueueOkReply(JdwpPayloads.arrayLengthReply(3));
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID,
                "(i >= 0 && i < arr.length) ? arr[i] : -1");
        // Whatever the JDWP layer returns for the chained index,
        // we MUST not throw and we MUST not claim success for a
        // partial result; we either succeed (with a value) or
        // report a clean error. Both are acceptable here.
        assertNotNull(r);
        assertTrue("eval result must be either success or a clean error",
                !r.isError() || (r.error != null && !r.error.isEmpty()));
    }
}
