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
        // 7) ReferenceType.Methods: 第一次按 "()V" 查
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xA000L, "toString", "()Ljava/lang/String;", 0),
        }));
        // 8) ReferenceType.Methods: 第二次按名字 fallback
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(0xA000L, "toString", "()Ljava/lang/String;", 0),
        }));
        // 9) ObjectReference.InvokeMethod: 'L' + 返回值 0xA001
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'L', JdwpPayloads.longValue(0xA001L)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "this.toString()");

        assertFalse(r.isError());
        // invokeNoArgMethod 用 ownerSig 作为 typeSignature
        assertEquals("Lcom/example/Foo;", r.typeSignature);
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
    public void evaluate_methodWithArgs_returnsError() {
        // 解析器接受有参方法,但 invokeNoArgMethod 只支持 0 参。
        // 用一个纯 LOCAL receiver + 方法调用,跳过 this 解析。
        FakeJdwpClient fake = new FakeJdwpClient();
        // 解析 `foo` -> LOCAL
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "foo", "Lcom/example/Foo;", 1, 0),
        }));
        // foo 是 object id,被抹平为 0,displayValue 是 <object id=...>
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'L', JdwpPayloads.longValue(0x7000L)));

        EvalEngine e = newEngine(fake);
        EvalResult r = e.evaluate(THREAD_ID, FRAME_ID, "foo.bar(1)");

        assertTrue(r.isError());
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("method calls with arguments are not supported"));
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
}
