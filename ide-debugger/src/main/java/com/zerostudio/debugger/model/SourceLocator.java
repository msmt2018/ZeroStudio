/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Locates source files and converts them into JDWP source locations.
 *  This is the bridge between the IDE's source-level view and the JDWP
 *  server's class/method/line-level view.
 *
 *  Strategy (simplified for the first cut):
 *
 *   1. When the user adds a breakpoint we issue `ClassesBySignature` for
 *      every class whose name we can guess from the source file's name
 *      and package. For each candidate we then call `SourceFile` and
 *      `LineTable` to find a matching line.
 *   2. If we find one we issue `EventRequest.Set` with the LOCATION
 *      modifier and store the request id on the breakpoint.
 *   3. If no class is loaded yet we keep the breakpoint in
 *      [Breakpoint.State.PENDING]; we listen for CLASS_PREPARE events and
 *      try again as classes come in.
 *
 *  PR-2 ships a working but not fully optimal implementation. A more
 *  sophisticated implementation would cache class signatures, look them
 *  up by SourceFile attribute and handle Java modules.
 */

package com.zerostudio.debugger.model;

import androidx.annotation.NonNull;
import com.zerostudio.debugger.api.Breakpoint;
import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.api.StackFrameInfo;
import com.zerostudio.debugger.api.VariableInfo;
import com.zerostudio.debugger.jdwp.CommandCodes;
import com.zerostudio.debugger.jdwp.CommandSet;
import com.zerostudio.debugger.jdwp.JdwpClient;
import com.zerostudio.debugger.jdwp.JdwpEvents;
import com.zerostudio.debugger.jdwp.JdwpPacket;
import com.zerostudio.debugger.jdwp.ModKind;
import com.zerostudio.debugger.jdwp.StepDepth;
import com.zerostudio.debugger.jdwp.StepSize;
import com.zerostudio.debugger.jdwp.SuspendPolicy;
import com.zerostudio.debugger.jdwp.EventKind;
import com.zerostudio.debugger.util.ByteBuf;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SourceLocator {

    private final Debugger debugger;
    private final JdwpClient client;

    public SourceLocator(@NonNull Debugger debugger) {
        this.debugger = debugger;
        this.client = debugger.client();
    }

    /** Subscribe to CLASS_PREPARE events so pending breakpoints can be installed. */
    public void enableClassPrepare() throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeByte(EventKind.CLASS_PREPARE);
        buf.writeByte(SuspendPolicy.NONE);
        buf.writeInt(0); // no modifiers
        JdwpPacket reply = client.sendCommand(
                CommandSet.EventRequest, CommandCodes.EventRequestCmd.Set, buf.toByteArray());
        if (reply.errorCode() != 0) {
            throw new IOException("EventRequest.Set CLASS_PREPARE failed: " + reply.errorCode());
        }
    }

    /** Subscribe to BREAKPOINT events with thread suspend. */
    public void enableBreakpointEvents() throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeByte(EventKind.BREAKPOINT);
        buf.writeByte(SuspendPolicy.ALL);
        buf.writeInt(0); // no modifiers
        client.sendCommand(CommandSet.EventRequest, CommandCodes.EventRequestCmd.Set,
                buf.toByteArray());
    }

    /** Subscribe to SINGLE_STEP events. */
    public void enableSingleStepEvents() throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeByte(EventKind.SINGLE_STEP);
        buf.writeByte(SuspendPolicy.ALL);
        buf.writeInt(0); // no modifiers
        client.sendCommand(CommandSet.EventRequest, CommandCodes.EventRequestCmd.Set,
                buf.toByteArray());
    }

    public void enableExceptionEvents() throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeByte(EventKind.EXCEPTION);
        buf.writeByte(SuspendPolicy.ALL);
        buf.writeInt(0); // no modifiers
        client.sendCommand(CommandSet.EventRequest, CommandCodes.EventRequestCmd.Set,
                buf.toByteArray());
    }

    public void installBreakpoint(@NonNull Breakpoint bp) throws IOException {
        // PR-2 simplified: a single attempt with the source's basename as
        // a class hint. A production implementation would track all loaded
        // classes via CLASS_PREPARE.
        String classSignature = guessClassSignature(bp.sourceFile);
        if (classSignature == null) {
            return; // wait for CLASS_PREPARE
        }
        // ClassesBySignature
        ByteBuf buf = new ByteBuf();
        buf.writeString(classSignature);
        JdwpPacket reply = client.sendCommand(
                CommandSet.VirtualMachine, CommandCodes.VirtualMachineCmd.ClassesBySignature,
                buf.toByteArray());
        if (reply.errorCode() != 0 || reply.data.length < 1) {
            bp.state = Breakpoint.State.INVALID;
            return;
        }
        ByteBuf in = new ByteBuf(reply.data);
        int count = in.readInt();
        if (count == 0) {
            bp.state = Breakpoint.State.INVALID;
            return;
        }
        // Take the first matching class.
        byte typeTag = in.readByte();
        long classId = in.readLong();
        in.readInt(); // status, ignored
        // SourceFile
        ByteBuf sf = new ByteBuf();
        sf.writeLong(classId);
        JdwpPacket sfReply = client.sendCommand(
                CommandSet.ReferenceType, CommandCodes.ReferenceTypeCmd.SourceFile, sf.toByteArray());
        if (sfReply.errorCode() != 0) {
            bp.state = Breakpoint.State.INVALID;
            return;
        }
        ByteBuf sfIn = new ByteBuf(sfReply.data);
        String sourceFile = sfIn.readString();
        if (!sourceFile.endsWith(basename(bp.sourceFile))) {
            bp.state = Breakpoint.State.INVALID;
            return;
        }
        // Methods
        ByteBuf mBuf = new ByteBuf();
        mBuf.writeLong(classId);
        JdwpPacket mReply = client.sendCommand(
                CommandSet.ReferenceType, CommandCodes.ReferenceTypeCmd.Methods, mBuf.toByteArray());
        if (mReply.errorCode() != 0) {
            bp.state = Breakpoint.State.INVALID;
            return;
        }
        ByteBuf mIn = new ByteBuf(mReply.data);
        int declCount = mIn.readInt();
        // Search methods for a line table containing the breakpoint line.
        for (int i = 0; i < declCount; i++) {
            long mId = mIn.readLong();
            mIn.readString(); // name
            mIn.readString(); // signature
            mIn.readInt();    // mod bits
            // LineTable
            ByteBuf ltBuf = new ByteBuf();
            ltBuf.writeLong(classId);
            ltBuf.writeLong(mId);
            JdwpPacket ltReply = client.sendCommand(
                    CommandSet.Method, CommandCodes.MethodCmd.LineTable, ltBuf.toByteArray());
            if (ltReply.errorCode() != 0) {
                continue;
            }
            ByteBuf ltIn = new ByteBuf(ltReply.data);
            long start = ltIn.readLong();
            long end = ltIn.readLong();
            int lineCount = ltIn.readInt();
            long foundCodeIndex = -1L;
            for (int j = 0; j < lineCount; j++) {
                long code = ltIn.readLong();
                int line = ltIn.readInt();
                if (line == bp.line) {
                    foundCodeIndex = code;
                    break;
                }
            }
            if (foundCodeIndex >= 0) {
                installBreakpointAt(classId, mId, foundCodeIndex, bp);
                return;
            }
        }
        bp.state = Breakpoint.State.INVALID;
    }

    private void installBreakpointAt(
            long classId, long methodId, long codeIndex, @NonNull Breakpoint bp)
            throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeByte(EventKind.BREAKPOINT);
        buf.writeByte(SuspendPolicy.ALL);
        buf.writeInt(1); // one modifier
        buf.writeByte(ModKind.LOCATION);
        // Location: classId, methodId, codeIndex
        buf.writeLong(classId);
        buf.writeLong(methodId);
        buf.writeLong(codeIndex);
        JdwpPacket reply = client.sendCommand(
                CommandSet.EventRequest, CommandCodes.EventRequestCmd.Set, buf.toByteArray());
        if (reply.errorCode() != 0) {
            bp.state = Breakpoint.State.INVALID;
            return;
        }
        ByteBuf in = new ByteBuf(reply.data);
        bp.requestId = in.readInt();
        bp.state = Breakpoint.State.VERIFIED;
    }

    public void uninstallBreakpoint(@NonNull Breakpoint bp) throws IOException {
        if (bp.requestId <= 0) return;
        ByteBuf buf = new ByteBuf();
        buf.writeByte(EventKind.BREAKPOINT);
        buf.writeInt(bp.requestId);
        client.sendCommand(
                CommandSet.EventRequest, CommandCodes.EventRequestCmd.Clear, buf.toByteArray());
    }

    public void resumeAll() throws IOException {
        ByteBuf buf = new ByteBuf();
        client.sendCommand(CommandSet.VirtualMachine, CommandCodes.VirtualMachineCmd.Resume,
                buf.toByteArray());
    }

    public void suspendAll() throws IOException {
        ByteBuf buf = new ByteBuf();
        client.sendCommand(CommandSet.VirtualMachine, CommandCodes.VirtualMachineCmd.Suspend,
                buf.toByteArray());
    }

    public void step(long threadId, byte depth, byte size) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(threadId);
        buf.writeInt(1); // one modifier
        buf.writeByte(ModKind.STEP);
        buf.writeLong(threadId);
        buf.writeInt(1); // step size
        buf.writeByte(size);
        buf.writeByte(depth);
        client.sendCommand(
                CommandSet.EventRequest, CommandCodes.EventRequestCmd.Set, buf.toByteArray());
    }

    @NonNull
    public List<StackFrameInfo> getStackFrames(long threadId, int start, int length)
            throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(threadId);
        buf.writeInt(start);
        buf.writeInt(length);
        JdwpPacket reply = client.sendCommand(
                CommandSet.ThreadReference, CommandCodes.ThreadReferenceCmd.Frames, buf.toByteArray());
        if (reply.errorCode() != 0) {
            return Collections.emptyList();
        }
        ByteBuf in = new ByteBuf(reply.data);
        int count = in.readInt();
        List<StackFrameInfo> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long frameId = in.readLong();
            // location
            byte typeTag = in.readByte();
            long classId = in.readLong();
            long methodId = in.readLong();
            long codeIndex = in.readLong();
            // For each frame we need the method name, class signature, line
            // number, and variables; we do it inline rather than fire more
            // roundtrips.
            String methodName = readMethodName(classId, methodId);
            String classSig = readClassSignature(classId);
            int line = readLineNumber(classId, methodId, codeIndex);
            String sourceFile = readSourceFile(classId);
            List<VariableInfo> variables = readVariables(threadId, frameId, classId, methodId);
            out.add(new StackFrameInfo(
                    frameId, threadId, classId, methodId, codeIndex, line,
                    methodName, classSig, sourceFile, variables));
        }
        return out;
    }

    private String readMethodName(long classId, long methodId) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(classId);
        JdwpPacket reply = client.sendCommand(
                CommandSet.ReferenceType, CommandCodes.ReferenceTypeCmd.Methods, buf.toByteArray());
        if (reply.errorCode() != 0) return "<unknown>";
        ByteBuf in = new ByteBuf(reply.data);
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            long mId = in.readLong();
            String name = in.readString();
            in.readString(); // signature
            in.readInt();    // modifiers
            if (mId == methodId) return name;
        }
        return "<unknown>";
    }

    private String readClassSignature(long classId) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(classId);
        JdwpPacket reply = client.sendCommand(
                CommandSet.ReferenceType, CommandCodes.ReferenceTypeCmd.Signature, buf.toByteArray());
        if (reply.errorCode() != 0) return "<unknown>";
        ByteBuf in = new ByteBuf(reply.data);
        return in.readString();
    }

    private int readLineNumber(long classId, long methodId, long codeIndex) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(classId);
        buf.writeLong(methodId);
        JdwpPacket reply = client.sendCommand(
                CommandSet.Method, CommandCodes.MethodCmd.LineTable, buf.toByteArray());
        if (reply.errorCode() != 0) return -1;
        ByteBuf in = new ByteBuf(reply.data);
        in.readLong();
        in.readLong();
        int n = in.readInt();
        int best = -1;
        long bestCode = Long.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            long code = in.readLong();
            int line = in.readInt();
            if (code <= codeIndex && code > bestCode) {
                bestCode = code;
                best = line;
            }
        }
        return best;
    }

    private String readSourceFile(long classId) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(classId);
        JdwpPacket reply = client.sendCommand(
                CommandSet.ReferenceType, CommandCodes.ReferenceTypeCmd.SourceFile, buf.toByteArray());
        if (reply.errorCode() != 0) return "<unknown>";
        ByteBuf in = new ByteBuf(reply.data);
        return in.readString();
    }

    @NonNull
    private List<VariableInfo> readVariables(
            long threadId, long frameId, long classId, long methodId) throws IOException {
        // Look up the variable table for the method.
        ByteBuf buf = new ByteBuf();
        buf.writeLong(classId);
        buf.writeLong(methodId);
        JdwpPacket reply = client.sendCommand(
                CommandSet.Method, CommandCodes.MethodCmd.VariableTable, buf.toByteArray());
        if (reply.errorCode() != 0) return Collections.emptyList();
        ByteBuf in = new ByteBuf(reply.data);
        int argCount = in.readInt();
        int slotCount = 0;
        for (int i = 0; i < argCount; i++) {
            in.readLong();  // code index
            in.readString(); // name
            in.readString(); // signature
            in.readInt();    // length
            in.readInt();    // slot
        }
        // We don't actually fetch values here; the StackFrameInfo is
        // constructed without values, and the IDE's UI fetches them on
        // demand through EvalEngine. Return empty.
        return Collections.emptyList();
    }

    /** Best-effort guess of a class signature from a source file name. */
    @androidx.annotation.Nullable
    private static String guessClassSignature(@NonNull String sourceFile) {
        String base = basename(sourceFile);
        if (!base.endsWith(".java") && !base.endsWith(".kt")) return null;
        String name = base.substring(0, base.length() - 5);
        if (name.isEmpty()) return null;
        return "L" + name + ";";
    }

    private static String basename(@NonNull String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
