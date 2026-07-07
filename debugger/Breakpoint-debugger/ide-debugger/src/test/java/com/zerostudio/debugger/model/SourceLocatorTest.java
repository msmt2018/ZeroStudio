/*
 *  ZeroStudio IDE - SourceLocator 剩余路径单元测试 (Phase F4)
 *
 *  覆盖 SourceLocator.installBreakpoint / uninstallBreakpoint /
 *  enable*Events / retryPending / getStackFrames / resumeAll /
 *  suspendAll / step 中尚未被 SourceLocatorFetchLocalTest 与
 *  SourceLocatorHitCountTest 覆盖的分支:
 *
 *    - installBreakpoint 在源码扩展名未知 / class 还未加载 /
 *      JDWP 返回错误 / SourceFile 名不匹配 / LineTable 没匹配行 /
 *      EventRequest.Set 报错 等路径下的状态机行为
 *    - uninstallBreakpoint 在 requestId ≤ 0 时为 no-op
 *    - enableClassPrepare 在 JDWP 返回错误时抛 IOException
 *    - enable{Breakpoint,SingleStep,Exception}Events 发送正确的 eventKind
 *    - retryPending 在 pending 为空 / 非 PENDING / 源文件不匹配 /
 *      安装成功 / 安装失败 等情况下的状态变化与监听器通知
 *    - getStackFrames 在 errorReply 时返回空列表
 *    - resumeAll / suspendAll / step 发出正确的 JDWP 命令
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.zerostudio.debugger.api.Breakpoint;
import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.jdwp.CommandCodes;
import com.zerostudio.debugger.jdwp.CommandSet;
import com.zerostudio.debugger.jdwp.JdwpEvents;
import com.zerostudio.debugger.jdwp.FakeJdwpClient;
import com.zerostudio.debugger.jdwp.JdwpPayloads;
import com.zerostudio.debugger.jdwp.JdwpPayloads.Method;
import com.zerostudio.debugger.jdwp.JdwpPayloads.Var;
import com.zerostudio.debugger.util.ByteBuf;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SourceLocatorTest {

    private static final long CLASS_ID = 0x100L;
    private static final long METHOD_ID = 0x200L;
    private static final int LINE = 42;
    private static final int REQUEST_ID = 7;

    private static SourceLocator newLocator(FakeJdwpClient fake) {
        return new Debugger(fake).sourceLocator();
    }

    /** 编码一段 Method.LineTable 响应:0 行(empty line table)。 */
    private static byte[] emptyLineTableReply() {
        ByteBuf b = new ByteBuf();
        b.writeLong(0L);     // start
        b.writeLong(0x1000L); // end
        b.writeInt(0);       // count = 0
        return b.toByteArray();
    }

    /** 编码一段 Method.LineTable 响应,包含 1 行但 line != 目标。 */
    private static byte[] lineTableReplyNoMatch(int actualLine) {
        ByteBuf b = new ByteBuf();
        b.writeLong(0L);
        b.writeLong(0x1000L);
        b.writeInt(1);
        b.writeLong(0x55L);
        b.writeInt(actualLine);
        return b.toByteArray();
    }

    // ----------------------------------------------------------------
    // installBreakpoint 失败路径
    // ----------------------------------------------------------------

    @Test
    public void installBreakpoint_unknownExtensionGoesPending() throws Exception {
        // 源文件不是 .java / .kt → guessClassSignature 返回 null → 入 pending 列表
        FakeJdwpClient fake = new FakeJdwpClient();
        Breakpoint bp = new Breakpoint(1L, "notes.txt", 10, null);
        newLocator(fake).installBreakpoint(bp);
        assertEquals(Breakpoint.State.PENDING, bp.state);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void installBreakpoint_classesBySignatureEmptyGoesPending() throws Exception {
        // ClassesBySignature 返回 0 个类(类未加载)→ PENDING,等 CLASS_PREPARE
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureEmpty());
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        newLocator(fake).installBreakpoint(bp);
        assertEquals(Breakpoint.State.PENDING, bp.state);
        assertEquals(1, fake.commandCount());
    }

    @Test
    public void installBreakpoint_classesBySignatureErrorMarksInvalid() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueErrorReply((short) 20);
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        newLocator(fake).installBreakpoint(bp);
        assertEquals(Breakpoint.State.INVALID, bp.state);
        assertEquals(1, fake.commandCount());
    }

    @Test
    public void installBreakpoint_sourceFileMismatchMarksInvalid() throws Exception {
        // 1. ClassesBySignature -> 1 class
        // 2. SourceFile 返回 "Bar.java" (与 bp "Foo.java" 不一致)
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply(
                (byte) 'L', CLASS_ID, 0x01));
        fake.enqueueOkReply(JdwpPayloads.sourceFileReply("Bar.java"));
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        newLocator(fake).installBreakpoint(bp);
        assertEquals(Breakpoint.State.INVALID, bp.state);
        assertEquals(2, fake.commandCount());
    }

    @Test
    public void installBreakpoint_sourceFileErrorMarksInvalid() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply(
                (byte) 'L', CLASS_ID, 0x01));
        fake.enqueueErrorReply((short) 21);
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        newLocator(fake).installBreakpoint(bp);
        assertEquals(Breakpoint.State.INVALID, bp.state);
        assertEquals(2, fake.commandCount());
    }

    @Test
    public void installBreakpoint_methodsErrorMarksInvalid() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply(
                (byte) 'L', CLASS_ID, 0x01));
        fake.enqueueOkReply(JdwpPayloads.sourceFileReply("Foo.java"));
        fake.enqueueErrorReply((short) 22);
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        newLocator(fake).installBreakpoint(bp);
        assertEquals(Breakpoint.State.INVALID, bp.state);
        assertEquals(3, fake.commandCount());
    }

    @Test
    public void installBreakpoint_lineTableEmptyMarksInvalid() throws Exception {
        // 1. ClassesBySignature
        // 2. SourceFile
        // 3. Methods (1 method)
        // 4. LineTable (0 rows) → 没有 codeIndex 匹配 → 跳出循环 → bp 标 INVALID
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply(
                (byte) 'L', CLASS_ID, 0x01));
        fake.enqueueOkReply(JdwpPayloads.sourceFileReply("Foo.java"));
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(METHOD_ID, "doIt", "()V", 0x0009),
        }));
        fake.enqueueOkReply(emptyLineTableReply());
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        newLocator(fake).installBreakpoint(bp);
        assertEquals(Breakpoint.State.INVALID, bp.state);
        // 不应发 EventRequest.Set
        assertEquals(4, fake.commandCount());
    }

    @Test
    public void installBreakpoint_lineTableNoMatchingLineMarksInvalid() throws Exception {
        // LineTable 报 line=10,目标 line=42 → 找不到匹配 → INVALID
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply(
                (byte) 'L', CLASS_ID, 0x01));
        fake.enqueueOkReply(JdwpPayloads.sourceFileReply("Foo.java"));
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(METHOD_ID, "doIt", "()V", 0x0009),
        }));
        fake.enqueueOkReply(lineTableReplyNoMatch(10));
        Breakpoint bp = new Breakpoint(1L, "Foo.java", 42, null);
        newLocator(fake).installBreakpoint(bp);
        assertEquals(Breakpoint.State.INVALID, bp.state);
        assertEquals(4, fake.commandCount());
    }

    @Test
    public void installBreakpoint_eventRequestSetErrorMarksInvalid() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply(
                (byte) 'L', CLASS_ID, 0x01));
        fake.enqueueOkReply(JdwpPayloads.sourceFileReply("Foo.java"));
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(METHOD_ID, "doIt", "()V", 0x0009),
        }));
        fake.enqueueOkReply(JdwpPayloads.lineTableReply(0L, 0x1000L, 0x55L, LINE));
        // EventRequest.Set 返回 error
        fake.enqueueErrorReply((short) 25);
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        newLocator(fake).installBreakpoint(bp);
        assertEquals(Breakpoint.State.INVALID, bp.state);
        // requestId 不应被赋值
        assertEquals(-1, bp.requestId);
    }

    @Test
    public void installBreakpoint_basenameUsesLastSeparator() throws Exception {
        // 路径里同时含 / 和 \,basename 应取最后一段。
        // 成功后应通过 SourceFile 的 basename 比对。
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply(
                (byte) 'L', CLASS_ID, 0x01));
        fake.enqueueOkReply(JdwpPayloads.sourceFileReply("Foo.java"));
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(METHOD_ID, "doIt", "()V", 0x0009),
        }));
        fake.enqueueOkReply(JdwpPayloads.lineTableReply(0L, 0x1000L, 0x55L, LINE));
        fake.enqueueOkReply(JdwpPayloads.eventRequestSetReply(REQUEST_ID));
        // bp.sourceFile 同时含正反斜杠 — basename 是 "Foo.java"
        Breakpoint bp = new Breakpoint(1L, "src/main\\com/example/Foo.java", LINE, null);
        newLocator(fake).installBreakpoint(bp);
        assertEquals(Breakpoint.State.VERIFIED, bp.state);
        assertEquals(REQUEST_ID, bp.requestId);
    }

    // ----------------------------------------------------------------
    // uninstallBreakpoint
    // ----------------------------------------------------------------

    @Test
    public void uninstallBreakpoint_withRequestIdSendsClear() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        // Clear 回复随便回一个 ok(实际会被忽略)
        fake.enqueueOkReply(new byte[0]);
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        bp.requestId = REQUEST_ID;
        newLocator(fake).uninstallBreakpoint(bp);
        assertEquals(1, fake.commandCount());
        FakeJdwpClient.SentCommand sent = fake.sentCommands().get(0);
        assertEquals(CommandSet.EventRequest, sent.commandSet);
        assertEquals(CommandCodes.EventRequestCmd.Clear, sent.command);
    }

    @Test
    public void uninstallBreakpoint_withNegativeRequestIdIsNoop() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        bp.requestId = -1;
        newLocator(fake).uninstallBreakpoint(bp);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void uninstallBreakpoint_withZeroRequestIdIsNoop() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        bp.requestId = 0;
        newLocator(fake).uninstallBreakpoint(bp);
        assertEquals(0, fake.commandCount());
    }

    // ----------------------------------------------------------------
    // enable*Events
    // ----------------------------------------------------------------

    @Test
    public void enableClassPrepare_sendsCorrectEventKind() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        newLocator(fake).enableClassPrepare();
        FakeJdwpClient.SentCommand sent = fake.sentCommands().get(0);
        assertEquals(CommandSet.EventRequest, sent.commandSet);
        assertEquals(CommandCodes.EventRequestCmd.Set, sent.command);
        ByteBuf in = new ByteBuf(sent.data);
        assertEquals((byte) 0x44, in.readByte()); // CLASS_PREPARE
        assertEquals((byte) 0, in.readByte());    // JdwpEvents.SuspendPolicy.NONE
        assertEquals(0, in.readInt());            // no modifiers
    }

    @Test
    public void enableClassPrepare_errorReplyThrows() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueErrorReply((short) 99);
        try {
            newLocator(fake).enableClassPrepare();
            fail("expected IOException");
        } catch (IOException ex) {
            assertTrue(ex.getMessage().contains("EventRequest.Set"));
        }
    }

    @Test
    public void enableBreakpointEvents_sendsCorrectEventKind() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        newLocator(fake).enableBreakpointEvents();
        FakeJdwpClient.SentCommand sent = fake.sentCommands().get(0);
        ByteBuf in = new ByteBuf(sent.data);
        assertEquals((byte) 0x46, in.readByte()); // BREAKPOINT
        assertEquals((byte) 2, in.readByte());     // JdwpEvents.SuspendPolicy.ALL
    }

    @Test
    public void enableSingleStepEvents_sendsCorrectEventKind() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        newLocator(fake).enableSingleStepEvents();
        FakeJdwpClient.SentCommand sent = fake.sentCommands().get(0);
        ByteBuf in = new ByteBuf(sent.data);
        assertEquals((byte) 0x4A, in.readByte()); // SINGLE_STEP
        assertEquals((byte) 2, in.readByte());    // JdwpEvents.SuspendPolicy.ALL
    }

    @Test
    public void enableExceptionEvents_sendsCorrectEventKind() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        newLocator(fake).enableExceptionEvents();
        FakeJdwpClient.SentCommand sent = fake.sentCommands().get(0);
        ByteBuf in = new ByteBuf(sent.data);
        assertEquals((byte) 0x47, in.readByte()); // EXCEPTION
        assertEquals((byte) 2, in.readByte());    // JdwpEvents.SuspendPolicy.ALL
    }

    // ----------------------------------------------------------------
    // retryPending
    // ----------------------------------------------------------------

    @Test
    public void retryPending_emptyListIsNoop() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        newLocator(fake).retryPending(CLASS_ID, "Foo.java");
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void retryPending_nonPendingBpIsRemoved() throws Exception {
        // 制造一个 bp: 处于 VERIFIED 状态(说明此前已安装),但仍在 pending 列表中。
        // retryPending 应该把它从 pending 列表中清掉,但不发送任何命令。
        FakeJdwpClient fake = new FakeJdwpClient();
        SourceLocator loc = newLocator(fake);
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        bp.state = Breakpoint.State.VERIFIED;
        // 通过 installBreakpoint 把 bp 入 pending? 这里没有公开方法,改用
        // 触发 installBreakpoint 让 bp 入 pending,然后再把 state 改回 VERIFIED。
        // 简单起见:用 unknownExtension 让 bp 入 pending。
        Breakpoint pending = new Breakpoint(2L, "notes.txt", 10, null);
        loc.installBreakpoint(pending);
        assertEquals(Breakpoint.State.PENDING, pending.state);
        // 把 state 改成 VERIFIED 模拟"已不再 PENDING"
        pending.state = Breakpoint.State.VERIFIED;
        loc.retryPending(CLASS_ID, "notes.txt");
        // pendingCount 现在应该 = 0
        assertEquals(0, loc.pendingCount());
    }

    @Test
    public void retryPending_nullSourceFileSkips() throws Exception {
        // sourceFile 为 null → 不应发送任何命令
        FakeJdwpClient fake = new FakeJdwpClient();
        SourceLocator loc = newLocator(fake);
        // 通过 unknownExtension 制造一个 PENDING bp
        Breakpoint bp = new Breakpoint(1L, "notes.txt", 10, null);
        loc.installBreakpoint(bp);
        loc.retryPending(CLASS_ID, null);
        // 仍然 pending,且没有新命令发出
        assertEquals(Breakpoint.State.PENDING, bp.state);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void retryPending_mismatchedSourceFileSkips() throws Exception {
        // sourceFile basename 与 bp 不匹配 → 跳过
        FakeJdwpClient fake = new FakeJdwpClient();
        SourceLocator loc = newLocator(fake);
        Breakpoint bp = new Breakpoint(1L, "notes.txt", 10, null);
        loc.installBreakpoint(bp);
        loc.retryPending(CLASS_ID, "Other.txt");
        assertEquals(Breakpoint.State.PENDING, bp.state);
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void retryPending_successfulInstallRemovesAndNotifies() throws Exception {
        // pending -> 配对的 CLASS_PREPARE -> installBreakpoint 成功 -> 从 pending 移除,通知 listener
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger dbg = new Debugger(fake);
        SourceLocator loc = dbg.sourceLocator();
        AtomicInteger notifyCount = new AtomicInteger(0);
        Breakpoint lastBp = new Breakpoint(0L, null, 0, null) {
            // 普通字段即可,我们只关心 listener 收到通知的次数
        };
        dbg.addListener(new Debugger.Listener() {
            @Override public void onBreakpointChanged(@NonNull Breakpoint bp) {
                notifyCount.incrementAndGet();
                lastBp.state = bp.state;
            }
        });
        // 1. installBreakpoint 入 pending (unknownExtension 触发)
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        loc.installBreakpoint(bp);
        assertEquals(0, notifyCount.get());

        // 2. 配对响应链:ClassesBySignature -> SourceFile -> Methods -> LineTable -> EventRequest.Set
        fake.enqueueOkReply(JdwpPayloads.classesBySignatureReply(
                (byte) 'L', CLASS_ID, 0x01));
        fake.enqueueOkReply(JdwpPayloads.sourceFileReply("Foo.java"));
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(METHOD_ID, "doIt", "()V", 0x0009),
        }));
        fake.enqueueOkReply(JdwpPayloads.lineTableReply(0L, 0x1000L, 0x55L, LINE));
        fake.enqueueOkReply(JdwpPayloads.eventRequestSetReply(REQUEST_ID));

        // 3. retryPending:sourceFile basename 匹配 "Foo.java"
        loc.retryPending(CLASS_ID, "Foo.java");
        assertEquals(Breakpoint.State.VERIFIED, bp.state);
        assertEquals(REQUEST_ID, bp.requestId);
        assertEquals(0, loc.pendingCount());
        assertEquals(1, notifyCount.get());
    }

    @Test
    public void retryPending_failedInstallKeepsPending() throws Exception {
        // installBreakpoint 内部抛 IOException → bp 仍留在 pending
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger dbg = new Debugger(fake);
        SourceLocator loc = dbg.sourceLocator();
        AtomicInteger notifyCount = new AtomicInteger(0);
        dbg.addListener(new Debugger.Listener() {
            @Override public void onBreakpointChanged(@NonNull Breakpoint bp) {
                notifyCount.incrementAndGet();
            }
        });
        Breakpoint bp = new Breakpoint(1L, "Foo.java", LINE, null);
        loc.installBreakpoint(bp);
        assertEquals(Breakpoint.State.PENDING, bp.state);

        // 队列为空 → FakeJdwpClient 会抛 IOException
        // (failOnMissingResponder 默认 true)
        loc.retryPending(CLASS_ID, "Foo.java");
        // bp 仍 PENDING,仍留在 pending 列表中
        assertEquals(Breakpoint.State.PENDING, bp.state);
        assertEquals(1, loc.pendingCount());
        assertEquals(0, notifyCount.get());
    }

    // ----------------------------------------------------------------
    // getStackFrames / resumeAll / suspendAll / step
    // ----------------------------------------------------------------

    @Test
    public void getStackFrames_errorReturnsEmptyList() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueErrorReply((short) 30);
        assertTrue(newLocator(fake).getStackFrames(0x42L, 0, 1).isEmpty());
        assertEquals(1, fake.commandCount());
    }

    @Test
    public void getStackFrames_framesReplyIsParsed() throws Exception {
        // 给一个完整的 happy path:1 个 frame,GetValues 返 1 个 int
        FakeJdwpClient fake = new FakeJdwpClient();
        // 1. Frames
        fake.enqueueOkReply(JdwpPayloads.framesReply(
                0x99L, (byte) 'L', CLASS_ID, METHOD_ID, 0L));
        // 2. Methods(在 readMethodName 内部)
        fake.enqueueOkReply(JdwpPayloads.methodsReply(new Method[] {
                new Method(METHOD_ID, "doIt", "()V", 0x0009),
        }));
        // 3. Signature(在 readClassSignature 内部)
        fake.enqueueOkReply(JdwpPayloads.signatureReply("Lcom/example/Foo;"));
        // 4. LineTable(在 readLineNumber 内部)
        fake.enqueueOkReply(JdwpPayloads.lineTableReply(0L, 0x1000L, 0L, 7));
        // 5. SourceFile(在 readSourceFile 内部)
        fake.enqueueOkReply(JdwpPayloads.sourceFileReply("Foo.java"));
        // 6. VariableTable(在 readVariables 内部)
        fake.enqueueOkReply(JdwpPayloads.variableTableReply(new Var[] {
                new Var(0L, "x", "I", 1, 0),
        }));
        // 7. GetValues
        fake.enqueueOkReply(JdwpPayloads.getValuesReply(
                (byte) 'I', JdwpPayloads.intValue(123)));
        // 8. ThisObject(static method 也走一遍;static 时 tag 不会是 'L',我们用 'L' 模拟非 static)
        fake.enqueueOkReply(JdwpPayloads.thisObjectReply((byte) 'L', 0x1234L));
        // 9. ObjectReference.ReferenceType (取 this.typeSig)
        fake.enqueueOkReply(JdwpPayloads.referenceTypeReply((byte) 'L', CLASS_ID + 1));
        // 10. ReferenceType.Signature
        fake.enqueueOkReply(JdwpPayloads.signatureReply("Lcom/example/Bar;"));

        var frames = newLocator(fake).getStackFrames(0x42L, 0, 1);
        assertEquals(1, frames.size());
        var f = frames.get(0);
        assertEquals(0x99L, f.frameId);
        assertEquals("doIt", f.methodName);
        assertEquals("Lcom/example/Foo;", f.classSignature);
        assertEquals("Foo.java", f.sourceFile);
        assertEquals(7, f.line);
        // readVariables 把 'this' 插到 index 0;int x 跟随其后
        assertTrue(f.variables.size() >= 1);
        // 至少能看到变量 'x'
        boolean hasX = false;
        for (var v : f.variables) {
            if ("x".equals(v.name) && "123".equals(v.value)) hasX = true;
        }
        assertTrue(hasX);
    }

    @Test
    public void resumeAll_sendsResumeCommand() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        newLocator(fake).resumeAll();
        FakeJdwpClient.SentCommand sent = fake.sentCommands().get(0);
        assertEquals(CommandSet.VirtualMachine, sent.commandSet);
        assertEquals(CommandCodes.VirtualMachineCmd.Resume, sent.command);
    }

    @Test
    public void suspendAll_sendsSuspendCommand() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        newLocator(fake).suspendAll();
        FakeJdwpClient.SentCommand sent = fake.sentCommands().get(0);
        assertEquals(CommandSet.VirtualMachine, sent.commandSet);
        assertEquals(CommandCodes.VirtualMachineCmd.Suspend, sent.command);
    }

    @Test
    public void step_sendsStepRequest() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]);
        newLocator(fake).step(0x42L, (byte) 0 /* StepDepth.INTO */, (byte) 1 /* StepSize.LINE */);
        FakeJdwpClient.SentCommand sent = fake.sentCommands().get(0);
        assertEquals(CommandSet.EventRequest, sent.commandSet);
        assertEquals(CommandCodes.EventRequestCmd.Set, sent.command);
        ByteBuf in = new ByteBuf(sent.data);
        assertEquals((byte) 0x4A, in.readByte()); // SINGLE_STEP
        assertEquals((byte) 2, in.readByte());    // JdwpEvents.SuspendPolicy.ALL
        int modCount = in.readInt();
        assertEquals(1, modCount);
        assertEquals(10 /* JdwpEvents.ModKind.STEP */, in.readByte());
    }

    // ----------------------------------------------------------------
    // 杂项
    // ----------------------------------------------------------------

    @Test
    public void pendingCount_startsAtZero() {
        assertEquals(0, newLocator(new FakeJdwpClient()).pendingCount());
    }

    @Test
    public void pendingCount_growsWithUnknownExtensions() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        SourceLocator loc = newLocator(fake);
        loc.installBreakpoint(new Breakpoint(1L, "a.txt", 1, null));
        loc.installBreakpoint(new Breakpoint(2L, "b.md", 1, null));
        assertEquals(2, loc.pendingCount());
    }
}
