/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase B3: SetValues end-to-end tests.
 *
 *  Exercises the three "modify variable" helpers added to
 *  EvalEngine:
 *    - setArrayElement:   ArrayReference.SetValues   (13/3)
 *    - setLocal:          StackFrame.SetValues       (16/2)
 *    - setStaticField:    ClassType.SetValues        (3/2)
 *
 *  plus the writeValue() helper that encodes primitive values
 *  in the SetValues payload.
 *
 *  Each test verifies:
 *    - the right command set / command code is sent,
 *    - the payload is encoded correctly,
 *    - the helper returns a non-error EvalResult on success,
 *    - errors from the JDWP server are surfaced as EvalResult.error.
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.api.EvalResult;
import com.zerostudio.debugger.jdwp.CommandCodes;
import com.zerostudio.debugger.jdwp.CommandSet;
import com.zerostudio.debugger.jdwp.FakeJdwpClient;
import com.zerostudio.debugger.jdwp.JdwpClient;
import com.zerostudio.debugger.util.ByteBuf;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Constructor;

@RunWith(JUnit4.class)
public class EvalEngineSetValuesTest {

    private static final long THREAD_ID = 0x10L;
    private static final long FRAME_ID  = 0x20L;
    private static final long ARRAY_ID  = 0x30L;
    private static final long CLASS_ID  = 0x40L;
    private static final long FIELD_ID  = 0x50L;

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

    private static FakeJdwpClient.SentCommand singleSent(FakeJdwpClient fake) {
        assertEquals(1, fake.commandCount());
        return fake.sentCommands().get(0);
    }

    /** Read the unread tail of a ByteBuf into a byte array (test helper). */
    private static byte[] readTail(ByteBuf in) {
        int n = in.readable();
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) in.readByte();
        }
        return out;
    }

    // ---------- writeValue ----------

    @Test
    public void writeValue_int() {
        ByteBuf b = new ByteBuf();
        EvalEngine.writeValue(b, "I", "42");
        assertArrayEquals(new byte[] {0, 0, 0, 42}, b.toByteArray());
    }

    @Test
    public void writeValue_long() {
        ByteBuf b = new ByteBuf();
        EvalEngine.writeValue(b, "J", "100");
        assertArrayEquals(
                new byte[] {0, 0, 0, 0, 0, 0, 0, 100},
                b.toByteArray());
    }

    @Test
    public void writeValue_boolean() {
        ByteBuf b = new ByteBuf();
        EvalEngine.writeValue(b, "Z", "true");
        assertArrayEquals(new byte[] {1}, b.toByteArray());
    }

    @Test
    public void writeValue_objectWritesId() {
        ByteBuf b = new ByteBuf();
        EvalEngine.writeValue(b, "Ljava/lang/Object;", "4660");
        // 0x1234 -> 0,0,0,0,0,0,0x12,0x34
        assertArrayEquals(
                new byte[] {0, 0, 0, 0, 0, 0, 0x12, 0x34},
                b.toByteArray());
    }

    @Test
    public void writeValue_voidWritesNothing() {
        ByteBuf b = new ByteBuf();
        EvalEngine.writeValue(b, "V", "");
        assertEquals(0, b.size());
    }

    // ---------- setArrayElement ----------

    @Test
    public void setArrayElement_intArraySuccess() {
        FakeJdwpClient fake = new FakeJdwpClient();
        // No payload (SetValues returns void).
        fake.enqueueOkReply(new byte[0]);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.setArrayElement(ARRAY_ID, 3, "[I", "99");
        assertFalse(r.isError());
        assertEquals("99", r.displayValue);

        FakeJdwpClient.SentCommand sc = singleSent(fake);
        assertEquals(CommandSet.ArrayReference, sc.commandSet);
        assertEquals(CommandCodes.ArrayReferenceCmd.SetValues, sc.command);
        ByteBuf in = new ByteBuf(sc.data);
        assertEquals(ARRAY_ID, in.readLong());
        assertEquals(1, in.readInt());
        assertEquals(3, in.readInt());
        assertEquals('I', in.readByte());
        assertArrayEquals(new byte[] {0, 0, 0, 99}, readTail(in));
    }

    @Test
    public void setArrayElement_stringArraySuccess() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.setArrayElement(ARRAY_ID, 0, "[Ljava/lang/String;", "4660");
        assertFalse(r.isError());
        assertEquals(EvalResult.Tag.OBJECT, r.tag);

        FakeJdwpClient.SentCommand sc = singleSent(fake);
        assertEquals(CommandSet.ArrayReference, sc.commandSet);
        assertEquals(CommandCodes.ArrayReferenceCmd.SetValues, sc.command);
        ByteBuf in = new ByteBuf(sc.data);
        assertEquals(ARRAY_ID, in.readLong());
        assertEquals(1, in.readInt());
        assertEquals(0, in.readInt());
        assertEquals('L', in.readByte());
        assertArrayEquals(
                new byte[] {0, 0, 0, 0, 0, 0, 0x12, 0x34},
                readTail(in));
    }

    @Test
    public void setArrayElement_jdwpErrorReturnsEvalError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueErrorReply((short) 100);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.setArrayElement(ARRAY_ID, 0, "[I", "1");
        assertTrue(r.isError());
        assertTrue(r.error.contains("ArrayReference.SetValues"));
    }

    @Test
    public void setArrayElement_malformedSigReturnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);
        // Pass an empty array signature -> arrayElementSignature returns ""
        EvalResult r = e.setArrayElement(ARRAY_ID, 0, "", "1");
        assertTrue(r.isError());
        assertEquals(0, fake.commandCount()); // no JDWP call
    }

    // ---------- setLocal ----------

    @Test
    public void setLocal_intLocalSuccess() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.setLocal(THREAD_ID, FRAME_ID, /* slot= */ 2, "I", "7");
        assertFalse(r.isError());
        assertEquals("7", r.displayValue);

        FakeJdwpClient.SentCommand sc = singleSent(fake);
        assertEquals(CommandSet.StackFrame, sc.commandSet);
        assertEquals(CommandCodes.StackFrameCmd.SetValues, sc.command);
        ByteBuf in = new ByteBuf(sc.data);
        assertEquals(THREAD_ID, in.readLong());
        assertEquals(FRAME_ID, in.readLong());
        assertEquals(1, in.readInt());
        assertEquals(2, in.readInt());
        assertEquals('I', in.readByte());
        assertArrayEquals(new byte[] {0, 0, 0, 7}, readTail(in));
    }

    @Test
    public void setLocal_longLocalSuccess() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.setLocal(THREAD_ID, FRAME_ID, 0, "J", "12345");
        assertFalse(r.isError());

        FakeJdwpClient.SentCommand sc = singleSent(fake);
        ByteBuf in = new ByteBuf(sc.data);
        assertEquals('J', in.readByte());
        assertArrayEquals(
                new byte[] {0, 0, 0, 0, 0, 0, 0x30, 0x39},
                readTail(in));
    }

    @Test
    public void setLocal_jdwpErrorReturnsEvalError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueErrorReply((short) 100);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.setLocal(THREAD_ID, FRAME_ID, 0, "I", "1");
        assertTrue(r.isError());
        assertTrue(r.error.contains("StackFrame.SetValues"));
    }

    // ---------- setStaticField ----------

    @Test
    public void setStaticField_intSuccess() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.setStaticField(CLASS_ID, FIELD_ID, "I", "256");
        assertFalse(r.isError());
        assertEquals("256", r.displayValue);

        FakeJdwpClient.SentCommand sc = singleSent(fake);
        assertEquals(CommandSet.ClassType, sc.commandSet);
        assertEquals(CommandCodes.ClassTypeCmd.SetValues, sc.command);
        ByteBuf in = new ByteBuf(sc.data);
        assertEquals(CLASS_ID, in.readLong());
        assertEquals(1, in.readInt());
        assertEquals(FIELD_ID, in.readLong());
        assertEquals('I', in.readByte());
        assertArrayEquals(new byte[] {0, 0, 0x01, 0x00}, readTail(in));
    }

    @Test
    public void setStaticField_jdwpErrorReturnsEvalError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueErrorReply((short) 100);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.setStaticField(CLASS_ID, FIELD_ID, "I", "1");
        assertTrue(r.isError());
        assertTrue(r.error.contains("ClassType.SetValues"));
    }

    @Test
    public void setStaticField_badValueFormatReturnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);

        // Not a valid int.
        EvalResult r = e.setStaticField(CLASS_ID, FIELD_ID, "I", "not-a-number");
        assertTrue(r.isError());
        assertTrue(r.error.contains("bad value"));
        assertEquals(0, fake.commandCount());
    }

    // ---------- sanity: the success result carries the right tag ----------

    @Test
    public void setArrayElement_returnsTagForElementType() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.setArrayElement(ARRAY_ID, 0, "[Z", "true");
        assertFalse(r.isError());
        assertNotNull(r);
        assertEquals(EvalResult.Tag.BOOLEAN, r.tag);
    }
}
