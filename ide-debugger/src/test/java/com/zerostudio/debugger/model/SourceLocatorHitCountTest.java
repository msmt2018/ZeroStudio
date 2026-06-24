/*
 *  ZeroStudio IDE - SourceLocator 命中次数修饰符测试 (Phase E2)
 *
 *  验证 SourceLocator.installBreakpointAt 在断点携带 hit count 时
 *  会向 EventRequest.Set 的请求包中追加一个 COUNT 修饰符 (kind=1)。
 *
 *  - ALWAYS 模式 (hitCount=0) → modifier count = 1,只有 LOCATION
 *  - EQUAL   模式 (hitCount=5) → modifier count = 2,COUNT + LOCATION
 *  - GREATER_THAN 模式        → 同样 modifier count = 2
 *  - MULTIPLE 模式             → 同样 modifier count = 2
 *
 *  通过 FakeJdwpClient 拦截 EventRequest.Set 的 data 字段并断言。
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;

import com.zerostudio.debugger.api.Breakpoint;
import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.jdwp.CommandCodes;
import com.zerostudio.debugger.jdwp.CommandSet;
import com.zerostudio.debugger.jdwp.FakeJdwpClient;
import com.zerostudio.debugger.jdwp.JdwpPayloads;
import com.zerostudio.debugger.util.ByteBuf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SourceLocatorHitCountTest {

    private static final long CLASS_ID = 0x100L;
    private static final long METHOD_ID = 0x200L;
    private static final int LINE = 42;
    private static final int REQUEST_ID = 7;

    private static SourceLocator newLocator(FakeJdwpClient fake) {
        return new Debugger(fake).sourceLocator();
    }

    /** 排队 installBreakpoint 全流程所需的响应。 */
    private static void enqueueHappyPath(FakeJdwpClient fake) {
        // 1. VirtualMachine.ClassesBySignature -> 1 个类
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply(
                (byte) 'L', CLASS_ID, 0x01 /* prepared */));
        // 2. ReferenceType.SourceFile -> "Foo.java"
        fake.enqueueOkReply(JdwpPayloads.sourceFileReply("Foo.java"));
        // 3. ReferenceType.Methods -> 1 个方法
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new JdwpPayloads.Method[] {
                new JdwpPayloads.Method(METHOD_ID, "doIt", "()V", 0x0009),
        }));
        // 4. Method.LineTable -> 1 行,匹配 LINE
        fake.enqueueOkReply(JdwpPayloads.lineTableReply(0L, 0x1000L, 0x55L, LINE));
        // 5. EventRequest.Set -> requestId
        fake.enqueueOkReply(JdwpPayloads.eventRequestSetReply(REQUEST_ID));
    }

    private static byte[] extractEventRequestSet(FakeJdwpClient fake) {
        for (FakeJdwpClient.SentCommand sc : fake.sentCommands()) {
            if (sc.commandSet == CommandSet.EventRequest
                    && sc.command == CommandCodes.EventRequestCmd.Set) {
                return sc.data;
            }
        }
        throw new AssertionError("EventRequest.Set not sent");
    }

    @Test
    public void alwaysModeEmitsNoCountModifier() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        enqueueHappyPath(fake);

        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null, null,
                Breakpoint.HitCountMode.ALWAYS, 0);
        newLocator(fake).installBreakpoint(bp);

        assertEquals(Breakpoint.State.VERIFIED, bp.state);
        assertEquals(REQUEST_ID, bp.requestId);

        byte[] data = extractEventRequestSet(fake);
        ByteBuf in = new ByteBuf(data);
        assertEquals((byte) 0x46 /* BREAKPOINT */, in.readByte());   // eventKind
        in.readByte();                                              // suspendPolicy
        int modifierCount = in.readInt();
        assertEquals(1, modifierCount);
        // 第一个 modifier: LOCATION
        assertEquals(7 /* ModKind.LOCATION */, in.readByte());
    }

    @Test
    public void equalModeEmitsCountModifier() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        enqueueHappyPath(fake);

        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null, null,
                Breakpoint.HitCountMode.EQUAL, 5);
        newLocator(fake).installBreakpoint(bp);

        assertEquals(Breakpoint.State.VERIFIED, bp.state);

        byte[] data = extractEventRequestSet(fake);
        ByteBuf in = new ByteBuf(data);
        in.readByte();        // eventKind
        in.readByte();        // suspendPolicy
        int modifierCount = in.readInt();
        assertEquals(2, modifierCount);

        // 第一个 modifier: COUNT (kind 1)
        assertEquals(1 /* ModKind.COUNT */, in.readByte());
        int count = in.readInt();
        assertEquals(5, count);

        // 第二个 modifier: LOCATION
        assertEquals(7 /* ModKind.LOCATION */, in.readByte());
    }

    @Test
    public void multipleModeEmitsCountModifier() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        enqueueHappyPath(fake);

        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null, null,
                Breakpoint.HitCountMode.MULTIPLE, 10);
        newLocator(fake).installBreakpoint(bp);

        byte[] data = extractEventRequestSet(fake);
        ByteBuf in = new ByteBuf(data);
        in.readByte();
        in.readByte();
        int modifierCount = in.readInt();
        assertEquals(2, modifierCount);
        assertEquals(1, in.readByte()); // COUNT
        assertEquals(10, in.readInt());
    }

    @Test
    public void alwaysModeWithNonzeroCountTreatedAsAlways() throws Exception {
        // 防御性:hitCount>0 但 mode=ALWAYS → 不发 COUNT 修饰符。
        FakeJdwpClient fake = new FakeJdwpClient();
        enqueueHappyPath(fake);

        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null, null,
                Breakpoint.HitCountMode.ALWAYS, 99);
        newLocator(fake).installBreakpoint(bp);

        byte[] data = extractEventRequestSet(fake);
        ByteBuf in = new ByteBuf(data);
        in.readByte();
        in.readByte();
        assertEquals(1, in.readInt()); // 仅 LOCATION
    }
}
