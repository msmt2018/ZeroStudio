/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Locates source files and converts them into JDWP source locations.
 *  This is the bridge between the IDE's source-level view and the JDWP
 *  server's class/method/line-level view.
 *
 *  Phase G1: now uses JavaSourceParser to extract the package declaration
 *  and class signatures from .java source files, replacing the previous
 *  basename-only heuristic.
 *
 *  Strategy:
 *
 *   1. When the user adds a breakpoint we first try to parse the .java file
 *      with JavaParser to get the exact package + class signature.
 *      If that fails we fall back to the basename heuristic.
 *   2. We then issue `ClassesBySignature` for the resolved class signature.
 *      For each candidate we call `SourceFile` and `LineTable` to verify.
 *   3. If no class is loaded yet we keep the breakpoint in
 *      [Breakpoint.State.PENDING]; we listen for CLASS_PREPARE events and
 *      try again as classes come in.
 *
 *  Phase G1 also adds support for inner classes by parsing nested class
 *  declarations, though currently only the top-level class is used for
 *  breakpoint installation.
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
import com.zerostudio.debugger.jdwp.JdwpPacket;
import com.zerostudio.debugger.jdwp.ModKind;
import com.zerostudio.debugger.jdwp.SuspendPolicy;
import com.zerostudio.debugger.jdwp.EventKind;
import com.zerostudio.debugger.util.ByteBuf;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SourceLocator {

    private final Debugger debugger;
    private final JdwpClient client;
    /** Phase G1: Java source parser for extracting class signatures. Lazily initialized. */
    private final JavaSourceParser sourceParser = new JavaSourceParser();
    /**
     * Phase B1: breakpoints that failed to install because the
     * target class was not yet loaded. We retry each one whenever
     * CLASS_PREPARE fires for a class whose source file matches
     * the breakpoint's path.
     */
    private final List<Breakpoint> pending = new java.util.concurrent.CopyOnWriteArrayList<>();

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
            // Phase B1: no usable class hint -> stash in pending list
            // and wait for a matching CLASS_PREPARE.
            pending.add(bp);
            return;
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
            // Phase B1: the class isn't loaded yet. Keep the
            // breakpoint PENDING and wait for CLASS_PREPARE to
            // retry. We do NOT mark it INVALID; the user expects
            // it to install once the class shows up.
            pending.add(bp);
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
        // Phase E2: count modifiers. We emit at most one Count modifier
        // (the JDWP spec allows at most one). When the user picked MULTIPLE
        // we ask the VM to suspend on the Nth hit and then track further
        // hits client-side; for EQUAL/GREATER_THAN the modifier alone
        // already gives the right behaviour.
        boolean emitCount = bp.hasHitCountFilter();
        int modifierCount = 1 + (emitCount ? 1 : 0);
        ByteBuf buf = new ByteBuf();
        buf.writeByte(EventKind.BREAKPOINT);
        buf.writeByte(SuspendPolicy.ALL);
        buf.writeInt(modifierCount);
        if (emitCount) {
            buf.writeByte(ModKind.COUNT);
            buf.writeInt(bp.hitCount);
        }
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

    /**
     * Phase B1: called from {@link com.zerostudio.debugger.event.DebugEventBus}
     * whenever a CLASS_PREPARE event arrives. Tries to install any
     * pending breakpoint whose source file matches the new class's
     * source file. A breakpoint that still can't be resolved stays in
     * the pending list; one that succeeds is removed.
     *
     * @param classId the refTypeId of the freshly-prepared class
     * @param sourceFile the class's source file attribute (may be null)
     */
    public void retryPending(long classId, @Nullable String sourceFile) {
        if (pending.isEmpty()) return;
        java.util.Iterator<Breakpoint> it = pending.iterator();
        while (it.hasNext()) {
            Breakpoint bp = it.next();
            if (bp.state != Breakpoint.State.PENDING) {
                it.remove();
                continue;
            }
            // Best-effort match: same basename, or classSig-derived
            // path containing the breakpoint's filename. The full
            // class-sig lookup is done inside installBreakpoint so
            // we don't duplicate it here.
            if (sourceFile != null && !sourceFile.endsWith(basename(bp.sourceFile))) {
                continue;
            }
            try {
                installBreakpoint(bp);
                if (bp.state == Breakpoint.State.VERIFIED) {
                    it.remove();
                    debugger.notifyBreakpointChanged(bp);
                }
            } catch (IOException ex) {
                // keep pending; retry on the next CLASS_PREPARE
            }
        }
    }

    /** Phase B1: peek the pending-breakpoint list (test-only). */
    int pendingCount() {
        return pending.size();
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
        // Look up the variable table for the method to discover the locals.
        ByteBuf buf = new ByteBuf();
        buf.writeLong(classId);
        buf.writeLong(methodId);
        JdwpPacket reply = client.sendCommand(
                CommandSet.Method, CommandCodes.MethodCmd.VariableTable, buf.toByteArray());
        if (reply.errorCode() != 0) return Collections.emptyList();
        ByteBuf in = new ByteBuf(reply.data);
        int varCount = in.readInt();

        // We need (slot, name, signature) for each variable so we can later
        // call StackFrame.GetValues once and pick them up. We also need to
        // filter to "this" frame only - the table reports all locals for
        // the method, but the values returned by GetValues already encode
        // the current code index so the receiver side can match them up.
        int[] slots = new int[varCount];
        String[] names = new String[varCount];
        String[] sigs = new String[varCount];
        byte[] tags = new byte[varCount];
        for (int i = 0; i < varCount; i++) {
            in.readLong();               // code index
            names[i] = in.readString();  // name
            sigs[i]  = in.readString();  // signature
            in.readInt();                // length
            slots[i] = in.readInt();     // slot
            tags[i]  = tagFor(sigs[i]);
        }

        // Build the (slots, tags) request for StackFrame.GetValues.
        ByteBuf gv = new ByteBuf();
        gv.writeLong(threadId);
        gv.writeLong(frameId);
        gv.writeInt(varCount);
        for (int i = 0; i < varCount; i++) {
            gv.writeInt(slots[i]);
            gv.writeByte(tags[i]);
        }
        JdwpPacket valReply = client.sendCommand(
                CommandSet.StackFrame, CommandCodes.StackFrameCmd.GetValues, gv.toByteArray());
        List<VariableInfo> out = new ArrayList<>(varCount);
        if (valReply.errorCode() == 0) {
            ByteBuf vin = new ByteBuf(valReply.data);
            int count = Math.min(varCount, vin.readInt());
            for (int i = 0; i < count; i++) {
                byte tag = vin.readByte();
                String value = readTagValue(vin, tag);
                out.add(new VariableInfo(
                        /* id = */ 0L,
                        String.valueOf((char) tag),
                        names[i],
                        sigs[i],
                        value,
                        isPrim(tag),
                        slots[i]));
            }
        }

        // 'this' for non-static methods, via StackFrame.ThisObject.
        VariableInfo thisObj = readThis(threadId, frameId);
        if (thisObj != null) out.add(0, thisObj);
        return out;
    }

    @androidx.annotation.Nullable
    private VariableInfo readThis(long threadId, long frameId) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(threadId);
        buf.writeLong(frameId);
        JdwpPacket reply = client.sendCommand(
                CommandSet.StackFrame, CommandCodes.StackFrameCmd.ThisObject, buf.toByteArray());
        if (reply.errorCode() != 0) return null;
        ByteBuf in = new ByteBuf(reply.data);
        byte tag = in.readByte();
        if (tag == 'L') {
            long id = in.readLong();
            // Ask the reference for its type signature so the UI can show it.
            String typeSig = readObjectTypeSignature(id);
            return new VariableInfo(
                    id, "L", "this", typeSig, "id=" + id, false, -1);
        }
        return null;
    }

    @androidx.annotation.NonNull
    private String readObjectTypeSignature(long objectId) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(objectId);
        JdwpPacket reply = client.sendCommand(
                CommandSet.ObjectReference, CommandCodes.ObjectReferenceCmd.ReferenceType,
                buf.toByteArray());
        if (reply.errorCode() != 0) return "Ljava/lang/Object;";
        ByteBuf in = new ByteBuf(reply.data);
        byte typeTag = in.readByte();
        long classId = in.readLong();
        return readClassSignature(classId);
    }

    private static String readTagValue(@androidx.annotation.NonNull ByteBuf in, byte tag) {
        switch (tag) {
            case 'V': return "void";
            case 'Z': return (in.readByte() != 0) ? "true" : "false";
            case 'B': return String.valueOf(in.readByte());
            case 'C': return String.valueOf((char) in.readUnsignedShort());
            case 'S': return String.valueOf(in.readShort());
            case 'I': return String.valueOf(in.readInt());
            case 'J': return String.valueOf(in.readLong());
            case 'F': return String.valueOf(in.readFloat());
            case 'D': return String.valueOf(in.readDouble());
            case 'L': return "<object id=" + in.readLong() + ">";
            case '[': return "<array id=" + in.readLong() + ">";
            default:  return "?";
        }
    }

    private static byte tagFor(@androidx.annotation.NonNull String sig) {
        if (sig.isEmpty()) return 'L';
        char c = sig.charAt(0);
        if (c == '[' || c == 'L') return 'L';
        return (byte) c;
    }

    private static boolean isPrim(byte tag) {
        return tag != 'L' && tag != '[';
    }

    /**
     * Look up a single local variable by name from the current frame. The
     * call re-issues VariableTable + GetValues for the whole method and
     * then filters; the IDE never hits this path on a hot loop.
     */
    @androidx.annotation.Nullable
    public VariableInfo fetchLocal(long threadId, long frameId, @androidx.annotation.NonNull String name)
            throws java.io.IOException {
        // Fetch the frame's method id and class id, then read the var table.
        ByteBuf fb = new ByteBuf();
        fb.writeLong(threadId);
        fb.writeInt(0);
        fb.writeInt(1);
        JdwpPacket fr = client.sendCommand(
                CommandSet.ThreadReference, CommandCodes.ThreadReferenceCmd.Frames,
                fb.toByteArray());
        if (fr.errorCode() != 0) return null;
        ByteBuf fin = new ByteBuf(fr.data);
        int count = fin.readInt();
        if (count < 1) return null;
        long gotFrameId = fin.readLong();
        if (gotFrameId != frameId) return null;
        fin.readByte(); // typeTag
        long classId = fin.readLong();
        long methodId = fin.readLong();
        fin.readLong(); // codeIndex

        // Read the var table.
        ByteBuf buf = new ByteBuf();
        buf.writeLong(classId);
        buf.writeLong(methodId);
        JdwpPacket reply = client.sendCommand(
                CommandSet.Method, CommandCodes.MethodCmd.VariableTable, buf.toByteArray());
        if (reply.errorCode() != 0) return null;
        ByteBuf in = new ByteBuf(reply.data);
        int varCount = in.readInt();
        if (varCount == 0) return null;
        int matchIdx = -1;
        for (int i = 0; i < varCount; i++) {
            in.readLong();
            String n = in.readString();
            in.readString();
            in.readInt();
            in.readInt();
            if (n.equals(name)) { matchIdx = i; }
        }
        if (matchIdx < 0) {
            // Try 'this' as a fallback.
            return readThis(threadId, frameId);
        }
        // Re-parse: build the (slots, tags) request and send it.
        ByteBuf in2 = new ByteBuf(reply.data);
        in2.readInt();
        int targetSlot = -1;
        byte targetTag = 'L';
        String targetSig = "";
        for (int i = 0; i < varCount; i++) {
            in2.readLong();
            in2.readString();
            String sig = in2.readString();
            in2.readInt();
            int slot = in2.readInt();
            if (i == matchIdx) {
                targetSlot = slot;
                targetTag = tagFor(sig);
                targetSig = sig;
            }
        }
        if (targetSlot < 0) return null;

        ByteBuf gv = new ByteBuf();
        gv.writeLong(threadId);
        gv.writeLong(frameId);
        gv.writeInt(1);
        gv.writeInt(targetSlot);
        gv.writeByte(targetTag);
        JdwpPacket valReply = client.sendCommand(
                CommandSet.StackFrame, CommandCodes.StackFrameCmd.GetValues, gv.toByteArray());
        if (valReply.errorCode() != 0) return null;
        ByteBuf vin = new ByteBuf(valReply.data);
        vin.readInt();
        byte tag = vin.readByte();
        String value = readTagValue(vin, tag);
        return new VariableInfo(0L, String.valueOf((char) tag), name, targetSig, value,
                isPrim(tag), targetSlot);
    }

    /** Best-effort guess of a class signature from a source file name.
     *
     *  Phase G1: now uses JavaSourceParser to extract the exact package +
     *  class name from the source file, falling back to the basename heuristic
     *  only for .kt files or when parsing fails.
     *
     *  @return JVM type signature (e.g., "Lcom/example/MainActivity;") or null */
    @androidx.annotation.Nullable
    private String guessClassSignature(@NonNull String sourceFile) {
        String base = basename(sourceFile);
        if (base.endsWith(".java")) {
            // Phase G1: try to parse the source file for an exact signature
            ParsedSource parsed = sourceParser.parse(sourceFile);
            if (parsed != null) {
                String sig = parsed.topLevelSignature();
                if (sig != null) {
                    return sig;
                }
            }
            // Fallback: use basename without extension
            String name = base.substring(0, base.length() - 5);
            if (!name.isEmpty()) {
                return "L" + name + ";";
            }
            return null;
        } else if (base.endsWith(".kt")) {
            // Kotlin files: JavaParser doesn't handle them, fall back to basename
            String name = base.substring(0, base.length() - 3);
            if (!name.isEmpty()) {
                return "L" + name + ";";
            }
            return null;
        }
        return null;
    }

    private static String basename(@NonNull String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
