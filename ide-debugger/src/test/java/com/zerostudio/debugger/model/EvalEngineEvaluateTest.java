/*
 *  ZeroStudio IDE - EvalEngine.evaluate() 端到端测试 (PR-8)
 *
 *  这些测试不直接构造 EvalEngine.Parser AST,而是把整个解析+JDWP
 *  调用链都跑一遍,通过 FakeJdwpClient 返回预制的响应来覆盖各种
 *  解析器+JDWP 路径。
 *
 *  覆盖:
 *    - LOCAL 解析(原语 int / long / boolean)
 *    - LOCAL 解析(对象,目前 id 被 SourceLocator 抹平)
 *    - THIS 解析
 *    - FIELD 解析(ObjectReference.GetValues)
 *    - METHOD 解析(ObjectReference.InvokeMethod)
 *    - String literal (VirtualMachine.CreateString)
 *    - 整数 / 双精度 字面量(纯解析)
 *    - 错误路径:local 不存在 / field 不存在 / class 没加载 / method 不存在
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.api.EvalResult;
import com.zerostudio.debugger.api.EvalResult.Tag;
import com.zerostudio.debugger.jdwp.CommandCodes;
import com.zerostudio.debugger.jdwp.CommandSet;
import com.zerostudio.debugger.jdwp.FakeJdwpClient;
import com.zerostudio.debugger.jdwp.JdwpPayloads;
import com.zerostudio.debugger.jdwp.JdwpPayloads.Field;
import com.zerostudio.debugger.jdwp.JdwpPayloads.Method;
import com.zerostudio.debugger.jdwp.JdwpPayloads.Var;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EvalEngineEvaluateTest {

    private static final long THREAD_ID = 0x42L;
    private static final long FRAME_ID = 0x42L;
    private static final long CLASS_ID = 0x100L;
    private static final long METHOD_ID = 0x200L;

    /** 构造一个绑定到 FakeJdwpClient 的 Debugger + EvalEngine。 */
    private static EvalEngine newEngine(FakeJdwpClient fake) {
        Debugger d = new Debugger(fake); // 包私有构造器
        return d.eval();
    }

    // ---------- LOCAL 原语 ----------

    @Test
    public void evaluate_localInt_returnsIntValue() {
        FakeJdwpClient fake = new FakeJdwpClient();
        // 1) Frames
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        // 2) VariableTable: 1 个 i (int, slot 0)
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "i", "I", 1, 0),
        }));
        // 3) GetValues: 'I' + 42
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'I', JdwpPayloads.intValue(42)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "i");

        assertFalse(r.isError());
        assertEquals(Tag.INT, r.tag);
        assertEquals("I", r.typeSignature);
        assertEquals("42", r.displayValue);
        // 3 个 JDWP 命令
        assertEquals(3, fake.commandCount());
    }

    @Test
    public void evaluate_localLong_returnsLongValue() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "counter", "J", 2, 1),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'J', JdwpPayloads.longValue(100L)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "counter");

        assertFalse(r.isError());
        assertEquals(Tag.LONG, r.tag);
        assertEquals("100", r.displayValue);
    }

    @Test
    public void evaluate_localBoolean_returnsBooleanValue() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "flag", "Z", 1, 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'Z', JdwpPayloads.boolValue(true)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "flag");

        assertFalse(r.isError());
        assertEquals(Tag.BOOLEAN, r.tag);
        assertEquals("true", r.displayValue);
    }

    @Test
    public void evaluate_localObjectString_idFlattened() {
        // SourceLocator.fetchLocal 把对象 id 抹平为 0L (已知行为);
        // displayValue 是 <object id=...> 形式。
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "name", "Ljava/lang/String;", 1, 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'L', JdwpPayloads.longValue(0x9000L)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "name");

        assertFalse(r.isError());
        assertEquals(Tag.OBJECT, r.tag);
        assertEquals("Ljava/lang/String;", r.typeSignature);
        // displayValue 是 <object id=36864> 形式
        assertEquals("<object id=36864>", r.displayValue);
    }

    // ---------- THIS ----------

    @Test
    public void evaluate_this_resolvesToObject() {
        FakeJdwpClient fake = new FakeJdwpClient();
        // 1) Frames
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        // 2) VariableTable: 没有 "this"(static / 编译器没把它列出来)
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "x", "I", 1, 0),
        }));
        // 3) ThisObject: 'L' + 0x5000
        fake.enqueueOkReply(JdwpPayloads.thisObjectReply((byte) 'L', 0x5000L));
        // 4) ObjectReference.ReferenceType: 'L' + classId 0x600
        fake.enqueueOkReply(JdwpPayloads.referenceTypeReply((byte) 'L', 0x600L));
        // 5) ReferenceType.Signature: "Lcom/example/Foo;"
        fake.enqueueOkReply(JdwpPayloads.signatureReply("Lcom/example/Foo;"));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this");

        assertFalse(r.isError());
        assertEquals(Tag.OBJECT, r.tag);
        assertEquals(0x5000L, r.objectId);
        assertEquals("Lcom/example/Foo;", r.typeSignature);
        assertEquals(5, fake.commandCount());
    }

    // ---------- FIELD 访问 ----------

    @Test
    public void evaluate_thisField_callsGetValues() {
        FakeJdwpClient fake = new FakeJdwpClient();
        // this 解析
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Foo;");
        // 6) VirtualMachine.ClassesBySignature: 'L' + classId 0x1000
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0x1000L, 0));
        // 7) ReferenceType.Fields: 1 个字段 "name",签名 "Ljava/lang/String;"
        fake.enqueueOkReply(JdwpPayloads.fieldsReply(new Field[] {
                new Field(0x9000L, "name", "Ljava/lang/String;", 0),
        }));
        // 8) ObjectReference.GetValues: 'L' + 0x9001(字段的值是一个 String 对象)
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'L', JdwpPayloads.longValue(0x9001L)));
        // 9) ReferenceType.Fields: 再次获取字段签名
        fake.enqueueOkReply(JdwpPayloads.fieldsReply(new Field[] {
                new Field(0x9000L, "name", "Ljava/lang/String;", 0),
        }));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.name");

        assertFalse(r.isError());
        assertEquals(Tag.OBJECT, r.tag);
        assertEquals("Ljava/lang/String;", r.typeSignature);
        assertEquals("<object id=36865>", r.displayValue);
        assertEquals(9, fake.commandCount());
    }

    // ---------- METHOD 调用 ----------

    @Test
    public void evaluate_thisToString_invokesMethod() {
        FakeJdwpClient fake = new FakeJdwpClient();
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Foo;");
        // 6) VirtualMachine.ClassesBySignature
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0x1000L, 0));
        // 7) ReferenceType.Methods: 第一次按 "()V" 查 -> 不匹配 toString
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xA000L, "toString", "()Ljava/lang/String;", 0),
        }));
        // 8) ReferenceType.Methods: 按 arity 查 -> 找到 toString
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xA000L, "toString", "()Ljava/lang/String;", 0),
        }));
        // 9) ObjectReference.InvokeMethod 返回: tag='L' + objectId 0xA001
        //    (注意: InvokeMethod 响应**没有** count 前缀,与 GetValues 不同)
        fake.enqueueOkReply(JdwpPayloads.invokeMethodReply((byte) 'L', JdwpPayloads.longValue(0xA001L)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.toString()");

        assertFalse(r.isError());
        // PR-9: invokeMethod 现在用 response 的 tag 决定返回类型。
        assertEquals(Tag.OBJECT, r.tag);
        assertEquals("Ljava/lang/Object;", r.typeSignature);
        assertEquals("<object id=40961>", r.displayValue);
        assertEquals(9, fake.commandCount());
    }

    // ---------- 字面量 ----------

    @Test
    public void evaluate_stringLiteral_callsCreateString() {
        FakeJdwpClient fake = new FakeJdwpClient();
        // VirtualMachine.CreateString: 返回 stringId 0xB000
        fake.enqueueOkReply(JdwpPayloads.createStringReply(0xB000L));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "\"hello\"");

        assertFalse(r.isError());
        assertEquals(Tag.STRING, r.tag);
        assertEquals("hello", r.displayValue);
        assertEquals(0xB000L, r.objectId);
        assertEquals(1, fake.commandCount());
    }

    @Test
    public void evaluate_intLiteral_noJdwp() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "42");
        assertFalse(r.isError());
        // 解析器把 42 存为 LITERAL_LONG,LOCAL_INT 是死代码。
        assertEquals(Tag.LONG, r.tag);
        assertEquals("J", r.typeSignature);
        assertEquals("42", r.displayValue);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void evaluate_doubleLiteral_noJdwp() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "3.14");
        assertFalse(r.isError());
        assertEquals(Tag.DOUBLE, r.tag);
        assertEquals("D", r.typeSignature);
        assertEquals("3.14", r.displayValue);
        assertEquals(0, fake.commandCount());
    }

    // ---------- 错误路径 ----------

    @Test
    public void evaluate_localNotFound_returnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        // Frames
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        // VariableTable: 0 vars -> fetchLocal 直接返回 null
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[0]));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "x");

        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("no such local: x"));
    }

    @Test
    public void evaluate_emptyExpression_returnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "   ");
        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("empty expression"));
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void evaluate_trailingInput_returnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "i j");
        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("trailing input"));
    }

    @Test
    public void evaluate_fieldNotFound_returnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Foo;");
        // ClassesBySignature: 找到 class
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0x1000L, 0));
        // Fields: 0 个字段
        fake.enqueueOkReply(JdwpPayloads.fieldsReply(new Field[0]));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.missing");

        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("no field: Lcom/example/Foo;.missing"));
    }

    @Test
    public void evaluate_classNotLoaded_returnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Unloaded;");
        // ClassesBySignature: 0 个
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureEmpty());

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.x");

        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("class not loaded: Lcom/example/Unloaded;"));
    }

    @Test
    public void evaluate_methodNotFound_returnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Foo;");
        // ClassesBySignature
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0x1000L, 0));
        // Methods (第一次按 "()V" 查): 0 个
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[0]));
        // Methods (fallback 按名字查): 0 个
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[0]));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.missing()");

        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("no method: missing"));
    }

    @Test
    public void evaluate_methodWithTwoIntArgs_returnsIntResult() {
        // this.add(1, 2) where add(int, int) -> int
        FakeJdwpClient fake = new FakeJdwpClient();
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Calc;");
        // 6) ClassesBySignature
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0x1000L, 0));
        // 7) Methods (first try "(JJ)V" — won't match)
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xA000L, "add", "(II)I", 0),
        }));
        // 8) Methods (fallback lookupMethodByNameAndArity — finds "add" with 2 args)
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xA000L, "add", "(II)I", 0),
        }));
        // 9) InvokeMethod -> 'I' + 3
        fake.enqueueOkReply(JdwpPayloads.invokeMethodReply((byte) 'I', JdwpPayloads.intValue(3)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.add(1, 2)");

        assertFalse(r.isError());
        // PR-9: invokeMethod now uses the actual return type from the response tag.
        assertEquals(Tag.INT, r.tag);
        assertEquals("I", r.typeSignature);
        assertEquals("3", r.displayValue);
    }

    @Test
    public void evaluate_methodWithStringArg_invokesCorrectly() {
        // this.greet("") where greet(String) -> String.
        // args are evaluated first, so the order is:
        //   1) this resolution (5 commands)
        //   2) CreateString for the "" literal (1 command)
        //   3) ClassesBySignature
        //   4) Methods (first try) - signature build is (Ljava/lang/String;)V -> no match
        //   5) Methods (fallback by name+arity) - finds greet
        //   6) InvokeMethod
        FakeJdwpClient fake = new FakeJdwpClient();
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Greeter;");
        // 6) CreateString for ""
        fake.enqueueOkReply(JdwpPayloads.createStringReply(0xD000L));
        // 7) ClassesBySignature
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0x1000L, 0));
        // 8) Methods (first try — sig doesn't match)
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xB000L, "greet", "(Ljava/lang/String;)Ljava/lang/String;", 0),
        }));
        // 9) Methods (fallback by name+arity — finds it)
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xB000L, "greet", "(Ljava/lang/String;)Ljava/lang/String;", 0),
        }));
        // 10) InvokeMethod -> 'L' + objectId
        fake.enqueueOkReply(JdwpPayloads.invokeMethodReply((byte) 'L', JdwpPayloads.longValue(0xC000L)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.greet(\"\")");

        assertFalse(r.isError());
        assertEquals(Tag.OBJECT, r.tag);
        assertEquals("Ljava/lang/Object;", r.typeSignature);
        assertEquals("<object id=49152>", r.displayValue);
    }

    @Test
    public void evaluate_methodWithOneArg() {
        // this.increment(5) where increment(int) -> int
        FakeJdwpClient fake = new FakeJdwpClient();
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Counter;");
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0x1000L, 0));
        // Methods (first try "(J)V" — won't match; return empty)
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[0]));
        // (current code falls back to lookupMethodByNameAndArity which queries
        //  Methods again — so the second Methods response below is for that.)
        // Methods (fallback by arity) -> finds increment(I)I
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xD000L, "increment", "(I)I", 0),
        }));
        // InvokeMethod -> 'I' + 6
        fake.enqueueOkReply(JdwpPayloads.invokeMethodReply((byte) 'I', JdwpPayloads.intValue(6)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.increment(5)");

        assertFalse(r.isError());
        assertEquals(Tag.INT, r.tag);
        assertEquals("6", r.displayValue);
    }

    @Test
    public void evaluate_methodWithArgs_methodNotFound_returnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Foo;");
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0x1000L, 0));
        // Methods (first try): 0 results
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[0]));
        // Methods (fallback by arity): 0 results
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[0]));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.missing(1)");

        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("no method: missing"));
    }

    @Test
    public void evaluate_methodWithArgs_argEvaluationError_returnsError() {
        // this.add(x) where x doesn't exist
        FakeJdwpClient fake = new FakeJdwpClient();
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Calc;");
        // 现在要解析 arg `x` -> LOCAL,失败
        // (需要在 frames 之后给一个 0 vars 的 var table)
        // frames: 一个新栈帧(为解析 x 准备的栈帧)
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        // var table: 0 vars -> 找不到 x
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[0]));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.add(x)");

        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("no such local: x"));
    }

    @Test
    public void evaluate_methodWithTwoIntArgs_requestEncodesArgsAsLongs() {
        // 验证 InvokeMethod 请求里:arg 字节确实按"J"(long)编码(因为
        // 解析器把字面量都存为 LITERAL_LONG)。
        FakeJdwpClient fake = new FakeJdwpClient();
        queueThisResolution(fake, 0x5000L, 0x600L, "Lcom/example/Calc;");
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply((byte) 'L', 0x1000L, 0));
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xA000L, "add", "(II)I", 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xA000L, "add", "(II)I", 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.invokeMethodReply((byte) 'I', JdwpPayloads.intValue(3)));

        EvalEngine e = newEngine(fake);
        e.evaluate(THREAD_ID, FRAME_ID, "this.add(1, 2)");

        // 找到发到 ObjectReference.InvokeMethod 的那条命令
        FakeJdwpClient.SentCommand invoke = null;
        for (FakeJdwpClient.SentCommand c : fake.sentCommands()) {
            if (c.commandSet == CommandSet.ObjectReference
                    && c.command == CommandCodes.ObjectReferenceCmd.InvokeMethod) {
                invoke = c;
                break;
            }
        }
        assertNotNull("expected an InvokeMethod command to be sent", invoke);
        // 数据布局:objectId(8) + threadId(8) + argCount(4) + arg1(8) + arg2(8) + methodId(8) = 44
        assertEquals(44, invoke.data.length);
        // argCount 在偏移 16..19 应该是 2
        int argCount = ((invoke.data[16] & 0xff) << 24)
                | ((invoke.data[17] & 0xff) << 16)
                | ((invoke.data[18] & 0xff) << 8)
                | (invoke.data[19] & 0xff);
        assertEquals(2, argCount);
        // arg1(1) 位于偏移 20..27,大端
        long arg1 = 0;
        for (int i = 0; i < 8; i++) {
            arg1 = (arg1 << 8) | (invoke.data[20 + i] & 0xffL);
        }
        assertEquals(1L, arg1);
        // arg2(2) 位于偏移 28..35
        long arg2 = 0;
        for (int i = 0; i < 8; i++) {
            arg2 = (arg2 << 8) | (invoke.data[28 + i] & 0xffL);
        }
        assertEquals(2L, arg2);
        // methodId 位于偏移 36..43
        long methodId = 0;
        for (int i = 0; i < 8; i++) {
            methodId = (methodId << 8) | (invoke.data[36 + i] & 0xffL);
        }
        assertEquals(0xA000L, methodId);
    }

    // ---------- 辅助 ----------

    /**
     * 把"解析 this"路径所需的 5 个响应排入队列。
     */
    private static void queueThisResolution(
            FakeJdwpClient fake, long objectId, long classId, String classSig) {
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                // 一个不相干的局部,让 fallback 走到 readThis
                new Var(0L, "ignored", "I", 1, 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.thisObjectReply((byte) 'L', objectId));
        fake.enqueueOkReply(JdwpPayloads.referenceTypeReply((byte) 'L', classId));
        fake.enqueueOkReply(JdwpPayloads.signatureReply(classSig));
    }

    // ---------- 字段/方法调用的命令码 sanity check ----------

    @Test
    public void sanity_fieldTest_hitsObjectReferenceGetValues() {
        // 这个测试只验证 command 码:
        // a) ObjectReference.GetValues 的 commandSet/command 是 9/2
        // b) SourceLocator.fetchLocal 用的 CommandSet.ThreadReference.Frames 是 11/6
        // c) VariableTable 是 6/2,GetValues 是 16/1
        assertEquals(11, CommandSet.ThreadReference);
        assertEquals(6, CommandCodes.ThreadReferenceCmd.Frames);
        assertEquals(6, CommandSet.Method);
        assertEquals(2, CommandCodes.MethodCmd.VariableTable);
        assertEquals(16, CommandSet.StackFrame);
        assertEquals(1, CommandCodes.StackFrameCmd.GetValues);
        assertEquals(9, CommandSet.ObjectReference);
        assertEquals(2, CommandCodes.ObjectReferenceCmd.GetValues);
        assertEquals(1, CommandSet.VirtualMachine);
        assertEquals(11, CommandCodes.VirtualMachineCmd.CreateString);
    }

    // ---------- Phase A1: 算术运算符端到端 ----------

    @Test
    public void evaluate_literalAddition_noJdwp() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "1 + 2");
        assertFalse(r.isError());
        assertEquals(Tag.LONG, r.tag);
        assertEquals("J", r.typeSignature);
        assertEquals("3", r.displayValue);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void evaluate_literalSubtraction_noJdwp() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "10 - 4");
        assertFalse(r.isError());
        assertEquals("6", r.displayValue);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void evaluate_literalMultiplication_noJdwp() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "6 * 7");
        assertFalse(r.isError());
        assertEquals("42", r.displayValue);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void evaluate_literalDivision_noJdwp() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "20 / 4");
        assertFalse(r.isError());
        assertEquals("5", r.displayValue);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void evaluate_literalModulo_noJdwp() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "10 % 3");
        assertFalse(r.isError());
        assertEquals("1", r.displayValue);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void evaluate_literalDivisionByZero_returnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "1 / 0");
        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("division by zero"));
    }

    @Test
    public void evaluate_precedenceMultiplyBeforeAdd() {
        // 1 + 2 * 3 == 7. Both sides are pure literals so zero JDWP calls.
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "1 + 2 * 3");
        assertFalse(r.isError());
        assertEquals("7", r.displayValue);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void evaluate_parensOverridePrecedence() {
        // (1 + 2) * 3 == 9.
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "(1 + 2) * 3");
        assertFalse(r.isError());
        assertEquals("9", r.displayValue);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void evaluate_localPlusLiteral() {
        // local `i` is 41, then `i + 1` is 42. The `i` lookup still
        // requires Frames + VariableTable + GetValues, then the literal
        // +1 is evaluated locally. Phase A1 widens the result to long
        // because the parser stores int literals as LITERAL_LONG.
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "i", "I", 1, 0),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'I', JdwpPayloads.intValue(41)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "i + 1");
        assertFalse(r.isError());
        assertEquals(Tag.LONG, r.tag);
        assertEquals("J", r.typeSignature);
        assertEquals("42", r.displayValue);
        assertEquals(3, fake.commandCount());
    }

    @Test
    public void evaluate_localDividedByLiteral() {
        // count(100) / 4 -> 25 (long).
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "count", "J", 2, 1),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'J', JdwpPayloads.longValue(100L)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "count / 4");
        assertFalse(r.isError());
        assertEquals(Tag.LONG, r.tag);
        assertEquals("25", r.displayValue);
    }

    @Test
    public void evaluate_doubleArithmetic_widensToDouble() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "1.5 + 2.5");
        assertFalse(r.isError());
        assertEquals(Tag.DOUBLE, r.tag);
        assertEquals("D", r.typeSignature);
        assertEquals("4.0", r.displayValue);
    }

    @Test
    public void evaluate_stringOperand_returnsError() {
        // `"x" + 1` -- Phase A1 doesn't support string concat yet; the
        // helper should report a clean error and not throw.
        FakeJdwpClient fake = new FakeJdwpClient();
        // CreateString for the literal
        fake.enqueueOkReply(JdwpPayloads.createStringReply(0xB000L));
        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "\"x\" + 1");
        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("requires numeric"));
    }
}
