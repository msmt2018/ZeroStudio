/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase B2: EXCEPTION event end-to-end tests.
 *
 *  These tests build a JDWP EXCEPTION event packet by hand, hand
 *  it to DebugEventBus.dispatch and assert the resulting
 *  SuspendInfo carries:
 *    - reason == EXCEPTION
 *    - exceptionClassId
 *    - exceptionMessage (fetched via StringReference.Value)
 *    - exceptionCaught flag (true when catch* fields are non-zero)
 *
 *  The "Frames" call made by buildSuspendEx is satisfied with a
 *  pre-canned reply so the test does not depend on variable
 *  table or method-table lookups.
 */

package com.zerostudio.debugger.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.api.SuspendInfo;
import com.zerostudio.debugger.jdwp.CommandCodes;
import com.zerostudio.debugger.jdwp.CommandSet;
import com.zerostudio.debugger.jdwp.JdwpEvents;
import com.zerostudio.debugger.jdwp.FakeJdwpClient;
import com.zerostudio.debugger.jdwp.JdwpPacket;
import com.zerostudio.debugger.util.ByteBuf;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(JUnit4.class)
public class DebugEventBusExceptionTest {

    private static final long THREAD_ID = 0x1000L;
    private static final long EX_CLASS_ID = 0xABCDL;
    private static final long EX_OBJECT_ID = 0x1234L;
    private static final long CATCH_CLASS_ID = 0x9000L;
    private static final long CATCH_METHOD_ID = 0x8000L;
    private static final long CATCH_INDEX = 0x42L;
    private static final int REQUEST_ID = 5;

    /**
     * Build a JDWP EXCEPTION event packet (commandSet=64, command=100).
     * Layout:
     *   [suspendPolicy][eventCount=1]
     *     [eventKind=EXCEPTION][requestId][threadId]
     *     [exClassId][exTag][exObjectId]
     *     [catchClassId][catchMethodId][catchIndex]
     */
    private static JdwpPacket buildExceptionEvent(boolean caught) {
        ByteBuf b = new ByteBuf();
        b.writeByte(JdwpEvents.SuspendPolicy.ALL);
        b.writeInt(1);
        b.writeByte(com.zerostudio.debugger.jdwp.JdwpEvents.EventKind.EXCEPTION);
        b.writeInt(REQUEST_ID);
        b.writeLong(THREAD_ID);
        b.writeLong(EX_CLASS_ID);
        b.writeByte('L');                    // exTag
        b.writeLong(EX_OBJECT_ID);
        if (caught) {
            b.writeLong(CATCH_CLASS_ID);
            b.writeLong(CATCH_METHOD_ID);
            b.writeLong(CATCH_INDEX);
        } else {
            b.writeLong(0L);
            b.writeLong(0L);
            b.writeLong(0L);
        }
        return new JdwpPacket(
                /* id= */ 0,
                /* flags= */ 0,
                /* commandSet= */ 64,  // events
                /* command= */ 100,
                b.toByteArray());
    }

    /** StackFrame-less frames reply (count=0). */
    private static byte[] emptyFramesReply() {
        ByteBuf b = new ByteBuf();
        b.writeInt(0);
        return b.toByteArray();
    }

    /** StringReference.Value reply: [len][utf-8 bytes]. */
    private static byte[] stringValueReply(String s) {
        ByteBuf b = new ByteBuf();
        b.writeString(s);
        return b.toByteArray();
    }

    @Test
    public void exceptionCaught_propagatesClassAndMessage() {
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger d = new Debugger(fake);

        // For buildSuspendEx -> debugger.getStackFrames (Frames cmd).
        // 0 frames is fine for the test.
        fake.enqueueOkReply(emptyFramesReply());
        // StringReference.Value for the exception message.
        fake.enqueueOkReply(stringValueReply("boom!"));

        AtomicReference<SuspendInfo> captured = new AtomicReference<>();
        d.addListener(new Debugger.Listener() {
            @Override public void onSuspend(SuspendInfo info) { captured.set(info); }
        });

        d.onEvent(buildExceptionEvent(/* caught= */ true));

        SuspendInfo info = captured.get();
        assertNotNull(info);
        assertEquals(SuspendInfo.Reason.EXCEPTION, info.reason);
        assertEquals(EX_CLASS_ID, info.exceptionClassId);
        assertEquals("boom!", info.exceptionMessage);
        assertTrue("expected exceptionCaught=true", info.exceptionCaught);
        // The description should include the message.
        assertTrue("description should mention the message: " + info.description,
                info.description.contains("boom!"));

        // Verify a StringReference.Value was sent with the exception object id.
        boolean foundStringValueCmd = false;
        for (FakeJdwpClient.SentCommand sc : fake.sentCommands()) {
            if (sc.commandSet == CommandSet.StringReference
                    && sc.command == CommandCodes.StringReferenceCmd.Value) {
                ByteBuf in = new ByteBuf(sc.data);
                assertEquals(EX_OBJECT_ID, in.readLong());
                foundStringValueCmd = true;
            }
        }
        assertTrue("expected a StringReference.Value command", foundStringValueCmd);
    }

    @Test
    public void exceptionUncaught_setsCaughtFalseAndEmptyMessage() {
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger d = new Debugger(fake);

        // Frames (empty)
        fake.enqueueOkReply(emptyFramesReply());
        // StringReference.Value -> empty string
        fake.enqueueOkReply(stringValueReply(""));

        AtomicReference<SuspendInfo> captured = new AtomicReference<>();
        d.addListener(new Debugger.Listener() {
            @Override public void onSuspend(SuspendInfo info) { captured.set(info); }
        });

        d.onEvent(buildExceptionEvent(/* caught= */ false));

        SuspendInfo info = captured.get();
        assertNotNull(info);
        assertEquals(SuspendInfo.Reason.EXCEPTION, info.reason);
        assertEquals(EX_CLASS_ID, info.exceptionClassId);
        assertFalse("expected exceptionCaught=false", info.exceptionCaught);
        // When the message is empty we fall back to the description that
        // names the catch class id (or "0" for uncaught).
        assertNotNull(info.description);
    }

    @Test
    public void exceptionMessageLookupError_doesNotCrash() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.setFailOnMissingResponder(false); // return error reply
        Debugger d = new Debugger(fake);

        // Frames (empty)
        fake.enqueueOkReply(emptyFramesReply());
        // StringReference.Value -> error
        fake.enqueueErrorReply((short) 100);

        AtomicReference<SuspendInfo> captured = new AtomicReference<>();
        d.addListener(new Debugger.Listener() {
            @Override public void onSuspend(SuspendInfo info) { captured.set(info); }
        });

        d.onEvent(buildExceptionEvent(/* caught= */ true));

        SuspendInfo info = captured.get();
        assertNotNull(info);
        assertEquals(SuspendInfo.Reason.EXCEPTION, info.reason);
        // Even though the message fetch failed, the suspend must still happen.
        assertEquals(EX_CLASS_ID, info.exceptionClassId);
        assertEquals("", info.exceptionMessage);
        assertTrue(info.exceptionCaught);
    }
}
