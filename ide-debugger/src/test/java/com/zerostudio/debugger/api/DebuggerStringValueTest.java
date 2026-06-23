/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase B4: StringReference / VirtualMachine.CreateString tests.
 *
 *  Covers:
 *    - Debugger.readString       -> StringReference.Value    (10/1)
 *    - Debugger.createString     -> VirtualMachine.CreateString (1/11)
 *    - Debugger.fetchExceptionMessage (B2 path, now an alias)
 *
 *  Each test verifies the JDWP command set / command code, the
 *  payload bytes, and the round-tripped value.
 */

package com.zerostudio.debugger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.zerostudio.debugger.jdwp.CommandCodes;
import com.zerostudio.debugger.jdwp.CommandSet;
import com.zerostudio.debugger.jdwp.FakeJdwpClient;
import com.zerostudio.debugger.util.ByteBuf;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DebuggerStringValueTest {

    private static final long STRING_ID = 0xAAABL;
    private static final long NEW_STRING_ID = 0xCAFE0L;

    /** StringReference.Value reply: [len][utf-8 bytes]. */
    private static byte[] stringValueReply(String s) {
        ByteBuf b = new ByteBuf();
        b.writeString(s);
        return b.toByteArray();
    }

    /** VirtualMachine.CreateString reply: [stringId]. */
    private static byte[] createStringReply(long id) {
        ByteBuf b = new ByteBuf();
        b.writeLong(id);
        return b.toByteArray();
    }

    @Test
    public void readString_returnsDecodedString() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(stringValueReply("hello world"));
        Debugger d = new Debugger(fake);

        String out = d.readString(STRING_ID);
        assertEquals("hello world", out);

        assertEquals(1, fake.commandCount());
        FakeJdwpClient.SentCommand sc = fake.sentCommands().get(0);
        assertEquals(CommandSet.StringReference, sc.commandSet);
        assertEquals(CommandCodes.StringReferenceCmd.Value, sc.command);
        ByteBuf in = new ByteBuf(sc.data);
        assertEquals(STRING_ID, in.readLong());
    }

    @Test
    public void readString_emptyOnZeroId() {
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger d = new Debugger(fake);

        assertEquals("", d.readString(0L));
        // No JDWP call when id is null/zero.
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void readString_emptyOnJdwpError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueErrorReply((short) 100);
        Debugger d = new Debugger(fake);

        assertEquals("", d.readString(STRING_ID));
    }

    @Test
    public void readString_emptyOnIoException() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.setFailOnMissingResponder(true);
        Debugger d = new Debugger(fake);

        // The FakeJdwpClient throws IOException when failOnMissingResponder=true.
        assertEquals("", d.readString(STRING_ID));
    }

    @Test
    public void fetchExceptionMessage_isAliasForReadString() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(stringValueReply("oops"));
        Debugger d = new Debugger(fake);

        // Same command, same payload, same return — the 'tag' argument
        // is ignored per the JDWP spec.
        String out = d.fetchExceptionMessage(STRING_ID, (byte) 'L');
        assertEquals("oops", out);
        assertEquals(1, fake.commandCount());
        FakeJdwpClient.SentCommand sc = fake.sentCommands().get(0);
        assertEquals(CommandSet.StringReference, sc.commandSet);
        assertEquals(CommandCodes.StringReferenceCmd.Value, sc.command);
    }

    @Test
    public void createString_returnsStringId() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(createStringReply(NEW_STRING_ID));
        Debugger d = new Debugger(fake);

        long id = d.createString("a string");
        assertEquals(NEW_STRING_ID, id);

        assertEquals(1, fake.commandCount());
        FakeJdwpClient.SentCommand sc = fake.sentCommands().get(0);
        assertEquals(CommandSet.VirtualMachine, sc.commandSet);
        assertEquals(CommandCodes.VirtualMachineCmd.CreateString, sc.command);
        ByteBuf in = new ByteBuf(sc.data);
        assertEquals("a string", in.readString());
    }

    @Test
    public void createString_emptyStringIsAllowed() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(createStringReply(NEW_STRING_ID));
        Debugger d = new Debugger(fake);

        long id = d.createString("");
        assertEquals(NEW_STRING_ID, id);

        FakeJdwpClient.SentCommand sc = fake.sentCommands().get(0);
        ByteBuf in = new ByteBuf(sc.data);
        // length == 0
        assertEquals(0, in.readInt());
    }

    @Test
    public void createString_zeroOnJdwpError() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueErrorReply((short) 100);
        Debugger d = new Debugger(fake);

        assertEquals(0L, d.createString("x"));
    }

    @Test
    public void createString_zeroOnIoException() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.setFailOnMissingResponder(true);
        Debugger d = new Debugger(fake);

        assertEquals(0L, d.createString("x"));
    }

    @Test
    public void sanity_noNullValuesReturned() {
        // Quick smoke: make sure both helpers are reachable and don't
        // return null under normal conditions.
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(stringValueReply("x"));
        fake.enqueueOkReply(createStringReply(0x10L));
        Debugger d = new Debugger(fake);

        assertNotNull(d.readString(STRING_ID));
        assertNotNull(Long.toString(d.createString("x")));
    }

    @Test
    public void readString_thenCreateString_roundTripsThroughFake() {
        // A single end-to-end sanity check: read a string, then create
        // a different one and verify the two calls are independent.
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(stringValueReply("first"));
        fake.enqueueOkReply(createStringReply(0x20L));
        Debugger d = new Debugger(fake);

        assertEquals("first", d.readString(STRING_ID));
        assertEquals(0x20L, d.createString("second"));
        assertEquals(2, fake.commandCount());

        // First call: StringReference.Value
        FakeJdwpClient.SentCommand sc0 = fake.sentCommands().get(0);
        assertEquals(CommandSet.StringReference, sc0.commandSet);
        assertEquals(CommandCodes.StringReferenceCmd.Value, sc0.command);
        // Second call: VirtualMachine.CreateString
        FakeJdwpClient.SentCommand sc1 = fake.sentCommands().get(1);
        assertEquals(CommandSet.VirtualMachine, sc1.commandSet);
        assertEquals(CommandCodes.VirtualMachineCmd.CreateString, sc1.command);
    }

    @Test
    public void createString_swallowsSyntheticErrorFromFake() {
        // Defensive: in case future refactors change the API surface,
        // make sure createString's error path doesn't throw.
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.setFailOnMissingResponder(false);
        Debugger d = new Debugger(fake);

        long id = d.createString("hello");
        // No queued responder -> the fake returns a synthetic error
        // reply with errorCode=100; createString must swallow it.
        assertEquals(0L, id);
    }
}
