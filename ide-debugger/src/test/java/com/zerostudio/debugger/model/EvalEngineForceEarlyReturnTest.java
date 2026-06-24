/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase B5: ForceEarlyReturn end-to-end tests.
 *
 *  Exercises EvalEngine.forceEarlyReturn which issues JDWP
 *  ThreadReference.ForceEarlyReturn (11/13). Verifies:
 *    - payload layout (threadId + tag + value),
 *    - success path,
 *    - JDWP error path,
 *    - bad value format,
 *    - void signature rejected without a JDWP roundtrip.
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
public class EvalEngineForceEarlyReturnTest {

    private static final long THREAD_ID = 0x10L;

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

    private static byte[] readTail(ByteBuf in) {
        int n = in.readable();
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) in.readByte();
        }
        return out;
    }

    @Test
    public void forceEarlyReturn_int() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.forceEarlyReturn(THREAD_ID, "I", "42");
        assertFalse(r.isError());
        assertEquals(EvalResult.Tag.INT, r.tag);
        assertEquals("42", r.displayValue);

        FakeJdwpClient.SentCommand sc = singleSent(fake);
        assertEquals(CommandSet.ThreadReference, sc.commandSet);
        assertEquals(CommandCodes.ThreadReferenceCmd.ForceEarlyReturn, sc.command);

        ByteBuf in = new ByteBuf(sc.data);
        assertEquals(THREAD_ID, in.readLong());
        assertEquals('I', in.readByte());
        assertEquals(42, in.readInt());
        assertEquals(0, in.readable());
    }

    @Test
    public void forceEarlyReturn_long() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.forceEarlyReturn(THREAD_ID, "J", "100");
        assertFalse(r.isError());

        FakeJdwpClient.SentCommand sc = singleSent(fake);
        ByteBuf in = new ByteBuf(sc.data);
        assertEquals(THREAD_ID, in.readLong());
        assertEquals('J', in.readByte());
        assertEquals(100L, in.readLong());
    }

    @Test
    public void forceEarlyReturn_objectWritesId() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.forceEarlyReturn(THREAD_ID, "Ljava/lang/Object;", "4660");
        assertFalse(r.isError());

        FakeJdwpClient.SentCommand sc = singleSent(fake);
        ByteBuf in = new ByteBuf(sc.data);
        assertEquals(THREAD_ID, in.readLong());
        assertEquals('L', in.readByte());
        // 4660 = 0x1234
        assertEquals(0x1234L, in.readLong());
    }

    @Test
    public void forceEarlyReturn_jdwpErrorReturnsEvalError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueErrorReply((short) 100);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.forceEarlyReturn(THREAD_ID, "I", "1");
        assertTrue(r.isError());
        assertTrue(r.error.contains("ForceEarlyReturn"));
    }

    @Test
    public void forceEarlyReturn_voidSignatureRejected() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);

        // ForceEarlyReturn cannot be used on a void method.
        EvalResult r = e.forceEarlyReturn(THREAD_ID, "V", "");
        assertTrue(r.isError());
        assertTrue(r.error.contains("non-void"));
        // No JDWP roundtrip attempted.
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void forceEarlyReturn_emptySignatureRejected() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);

        EvalResult r = e.forceEarlyReturn(THREAD_ID, "", "1");
        assertTrue(r.isError());
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void forceEarlyReturn_badValueFormatReturnsError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        EvalEngine e = newEngine(fake);

        EvalResult r = e.forceEarlyReturn(THREAD_ID, "I", "not-a-number");
        assertTrue(r.isError());
        assertTrue(r.error.contains("bad value"));
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void forceEarlyReturn_booleanEncodesAsByte() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        EvalEngine e = newEngine(fake);

        EvalResult r = e.forceEarlyReturn(THREAD_ID, "Z", "true");
        assertFalse(r.isError());

        FakeJdwpClient.SentCommand sc = singleSent(fake);
        ByteBuf in = new ByteBuf(sc.data);
        assertEquals(THREAD_ID, in.readLong());
        assertEquals('Z', in.readByte());
        assertEquals(1, in.readByte());
    }
}
