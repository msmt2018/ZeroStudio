/*
 *  ZeroStudio IDE - SourceLocator.fetchLocal() 端到端测试 (PR-8)
 *
 *  覆盖:
 *    - 找到匹配的局部变量(返回带值的 VariableInfo)
 *    - 没找到名字时回退到 'this'(通过 StackFrame.ThisObject)
 *    - varCount == 0 直接返回 null
 *    - 各 JDWP 错误码下返回 null
 *    - 跨多种原语类型(boolean / int / long)
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.api.VariableInfo;
import com.zerostudio.debugger.jdwp.FakeJdwpClient;
import com.zerostudio.debugger.jdwp.JdwpPayloads;
import com.zerostudio.debugger.jdwp.JdwpPayloads.Var;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SourceLocatorFetchLocalTest {

    private static final long THREAD_ID = 0x42L;
    private static final long FRAME_ID = 0x42L;
    private static final long CLASS_ID = 0x100L;
    private static final long METHOD_ID = 0x200L;

    private static SourceLocator newLocator(FakeJdwpClient fake) {
        return new Debugger(fake).sourceLocator();
    }

    @Test
    public void fetchLocal_existingIntLocal() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        // Frames
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        // VariableTable
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "i", "I", 1, 0),
                new Var(0L, "s", "Ljava/lang/String;", 1, 1),
        }));
        // GetValues
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'I', JdwpPayloads.intValue(7)));

        VariableInfo v = newLocator(fake).fetchLocal(THREAD_ID, FRAME_ID, "i");
        assertNotNull(v);
        assertEquals("i", v.name);
        assertEquals("I", v.typeSignature);
        assertEquals("7", v.value);
        assertTrue(v.isPrimitive);
        assertEquals(0, v.slot);
        // SourceLocator 始终把对象 id 抹平为 0
        assertEquals(0L, v.id);
        assertEquals(3, fake.commandCount());
    }

    @Test
    public void fetchLocal_unknownNameFallsBackToThis() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        // Frames
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        // VariableTable: 只含 "other",没 "this"
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "other", "I", 1, 0),
        }));
        // ThisObject
        fake.enqueueOkReply(JdwpPayloads.thisObjectReply((byte) 'L', 0x5000L));
        // ObjectReference.ReferenceType
        fake.enqueueOkReply(JdwpPayloads.referenceTypeReply((byte) 'L', 0x600L));
        // ReferenceType.Signature
        fake.enqueueOkReply(JdwpPayloads.signatureReply("Lcom/example/Foo;"));

        VariableInfo v = newLocator(fake).fetchLocal(THREAD_ID, FRAME_ID, "this");
        assertNotNull(v);
        assertEquals("this", v.name);
        assertEquals(0x5000L, v.id);
        assertEquals("Lcom/example/Foo;", v.typeSignature);
        assertEquals(5, fake.commandCount());
    }

    @Test
    public void fetchLocal_zeroVarsReturnsNull() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[0]));

        VariableInfo v = newLocator(fake).fetchLocal(THREAD_ID, FRAME_ID, "x");
        assertNull(v);
        assertEquals(2, fake.commandCount());
    }

    @Test
    public void fetchLocal_frameCountZeroReturnsNull() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        ByteBufHelper framesEmpty = ByteBufHelper.allocate()
                .writeInt(0);
        fake.enqueueOkReply(framesEmpty.toByteArray());

        VariableInfo v = newLocator(fake).fetchLocal(THREAD_ID, FRAME_ID, "x");
        assertNull(v);
        assertEquals(1, fake.commandCount());
    }

    @Test
    public void fetchLocal_framesErrorReturnsNull() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueErrorReply((short) 100);

        VariableInfo v = newLocator(fake).fetchLocal(THREAD_ID, FRAME_ID, "x");
        assertNull(v);
        assertEquals(1, fake.commandCount());
    }

    @Test
    public void fetchLocal_variableTableErrorReturnsNull() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueErrorReply((short) 101);

        VariableInfo v = newLocator(fake).fetchLocal(THREAD_ID, FRAME_ID, "x");
        assertNull(v);
        assertEquals(2, fake.commandCount());
    }

    @Test
    public void fetchLocal_getValuesErrorReturnsNull() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "i", "I", 1, 0),
        }));
        fake.enqueueErrorReply((short) 102);

        VariableInfo v = newLocator(fake).fetchLocal(THREAD_ID, FRAME_ID, "i");
        assertNull(v);
        assertEquals(3, fake.commandCount());
    }

    @Test
    public void fetchLocal_wrongFrameIdReturnsNull() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        // frames 返回不同的 frameId
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                0x9999L, (byte) 'L', CLASS_ID, METHOD_ID, 0L));

        VariableInfo v = newLocator(fake).fetchLocal(THREAD_ID, FRAME_ID, "x");
        assertNull(v);
        assertEquals(1, fake.commandCount());
    }

    @Test
    public void fetchLocal_booleanLocal() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "flag", "Z", 1, 2),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'Z', JdwpPayloads.boolValue(false)));

        VariableInfo v = newLocator(fake).fetchLocal(THREAD_ID, FRAME_ID, "flag");
        assertNotNull(v);
        assertEquals("flag", v.name);
        assertEquals("Z", v.typeSignature);
        assertEquals("false", v.value);
        assertTrue(v.isPrimitive);
        assertEquals(2, v.slot);
    }

    @Test
    public void fetchLocal_longLocal() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                FRAME_ID, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "ms", "J", 2, 3),
        }));
        fake.enqueueOkReply(JdwpPayloads.getValuesReply((byte) 'J', JdwpPayloads.longValue(123456789L)));

        VariableInfo v = newLocator(fake).fetchLocal(THREAD_ID, FRAME_ID, "ms");
        assertNotNull(v);
        assertEquals("ms", v.name);
        assertEquals("J", v.typeSignature);
        assertEquals("123456789", v.value);
        assertTrue(v.isPrimitive);
        assertEquals(3, v.slot);
    }

    /**
     * 测试内部辅助:用 ByteBuf 直接构造一段响应 payload,
     * 避免再开一个 public helper。
     */
    private static final class ByteBufHelper {
        private final com.zerostudio.debugger.util.ByteBuf buf;
        private ByteBufHelper() { this.buf = new com.zerostudio.debugger.util.ByteBuf(); }
        static ByteBufHelper allocate() { return new ByteBufHelper(); }
        ByteBufHelper writeInt(int v) { buf.writeInt(v); return this; }
        byte[] toByteArray() { return buf.toByteArray(); }
    }
}
