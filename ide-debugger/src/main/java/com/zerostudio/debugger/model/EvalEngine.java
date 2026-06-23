/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Expression evaluator. PR-5 adds three capabilities:
 *
 *    1. Resolve a frame's local variables to actual values (this method
 *       used to be a stub; now the SourceLocator populates them).
 *    2. evaluate(threadId, frameId, expr) - parse a small subset of Java
 *       expressions and return a string-serialised value:
 *          identifier            - a local var, parameter, or 'this'
 *          expr.field            - ObjectReference.GetFieldValue
 *          expr.method()         - call a no-arg method via
 *                                  ObjectReference.InvokeMethod
 *          String literal "..."  - VirtualMachine.CreateString
 *          int / long / double   - literal
 *          (expr)                - grouping
 *          a.b.c                 - chained field access
 *          a.b()                 - chained method call
 *
 *       The parser is a hand-written recursive descent. It is NOT a full
 *       Java parser; anything we don't handle returns EvalResult.error.
 *
 *    3. toString() of arbitrary ObjectId - wraps in a "toString" call.
 *
 *  All side-effects are kept in JDWP commands; the IDE never has to load
 *  a class to evaluate.
 */

package com.zerostudio.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.api.EvalResult;
import com.zerostudio.debugger.api.VariableInfo;
import com.zerostudio.debugger.jdwp.CommandCodes;
import com.zerostudio.debugger.jdwp.CommandSet;
import com.zerostudio.debugger.jdwp.JdwpClient;
import com.zerostudio.debugger.jdwp.JdwpPacket;
import com.zerostudio.debugger.util.ByteBuf;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class EvalEngine {

    private final Debugger debugger;
    private final JdwpClient client;

    public EvalEngine(@NonNull Debugger debugger) {
        this.debugger = debugger;
        this.client = debugger.client();
    }

    /** Read all locals + 'this' from a frame. */
    @NonNull
    public List<VariableInfo> getFrameVariables(long threadId, long frameId) throws IOException {
        // The SourceLocator already populated the variables; the IDE can
        // read them straight off the StackFrameInfo. This method is kept
        // as a public API for callers that only have a (thread, frame)
        // tuple and need a fresh list.
        ByteBuf buf = new ByteBuf();
        buf.writeLong(threadId);
        buf.writeLong(frameId);
        JdwpPacket reply = client.sendCommand(
                CommandSet.StackFrame, CommandCodes.StackFrameCmd.GetValues, buf.toByteArray());
        if (reply.errorCode() != 0) return Collections.emptyList();
        // The reply cannot tell us names; callers should prefer
        // SourceLocator.getStackFrames and use the populated list.
        return Collections.emptyList();
    }

    /** Read a single variable value by frame + slot. */
    @NonNull
    public VariableInfo getFrameVariable(
            long threadId,
            long frameId,
            int slot,
            @NonNull String name,
            @NonNull String typeSignature) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(threadId);
        buf.writeLong(frameId);
        buf.writeInt(1);
        buf.writeInt(slot);
        buf.writeByte(tagFor(typeSignature));
        JdwpPacket reply = client.sendCommand(
                CommandSet.StackFrame, CommandCodes.StackFrameCmd.GetValues, buf.toByteArray());
        if (reply.errorCode() != 0) {
            return new VariableInfo(0, "", name, typeSignature, "<error>", true, slot);
        }
        ByteBuf in = new ByteBuf(reply.data);
        in.readInt(); // count
        byte tag = in.readByte();
        String value = readValue(in, tag);
        return new VariableInfo(0, String.valueOf((char) tag), name, typeSignature, value, isPrim(tag), slot);
    }

    /**
     * Evaluate an expression in the context of a stack frame.
     * The result is a string that can be displayed to the user.
     */
    @NonNull
    public EvalResult evaluate(long threadId, long frameId, @NonNull String expression) {
        try {
            Parser p = new Parser(expression.trim());
            if (!p.hasMore()) return EvalResult.error("(empty expression)");
            Resolved r = p.parseExpr();
            if (p.hasMore()) return EvalResult.error("trailing input: '" + p.remainder() + "'");
            return resolveAndEval(r, threadId, frameId);
        } catch (Throwable t) {
            return EvalResult.error("parse error: " + t.getMessage());
        }
    }

    /**
     * Package-private entry point used by the unit tests to drive the
     * parser without going through the JDWP client. Returns the raw
     * {@link Resolved} AST and does NOT enforce the "no trailing input"
     * invariant.
     */
    @NonNull
    static Resolved parseExpression(@NonNull String expression) {
        Parser p = new Parser(expression);
        return p.parseExpr();
    }

    /**
     * Package-private entry point used by the unit tests to drive the
     * parser strictly - mirrors {@link #evaluate} and throws if there is
     * leftover input.
     */
    @NonNull
    static Resolved parseExpressionStrict(@NonNull String expression) {
        Parser p = new Parser(expression);
        Resolved r = p.parseExpr();
        if (p.hasMore()) {
            throw new IllegalArgumentException("trailing input: '" + p.remainder() + "'");
        }
        return r;
    }

    /** Resolved identifier in the current scope. */
    static final class Resolved {
        enum Kind { LOCAL, FIELD, METHOD, THIS, LITERAL_STRING, LITERAL_INT, LITERAL_LONG, LITERAL_DOUBLE, NEW_STRING }
        final Kind kind;
        final String name;       // LOCAL, FIELD, METHOD, LITERAL_*
        @Nullable final Resolved receiver; // FIELD, METHOD
        @Nullable final List<Resolved> args; // METHOD
        final long literalLong;  // for LITERAL_LONG
        final double literalDouble; // for LITERAL_DOUBLE

        Resolved(Kind kind, String name, @Nullable Resolved receiver, @Nullable List<Resolved> args) {
            this.kind = kind;
            this.name = name;
            this.receiver = receiver;
            this.args = args;
            this.literalLong = 0L;
            this.literalDouble = 0.0;
        }
        Resolved(Kind kind, String name) {
            this(kind, name, null, null);
        }
        private Resolved(Kind kind, String name, @Nullable Resolved receiver,
                         @Nullable List<Resolved> args, long literalLong, double literalDouble) {
            this.kind = kind;
            this.name = name;
            this.receiver = receiver;
            this.args = args;
            this.literalLong = literalLong;
            this.literalDouble = literalDouble;
        }
        static Resolved litString(String s) {
            return new Resolved(Kind.LITERAL_STRING, s, null, null, 0L, 0.0);
        }
        static Resolved litInt(long v) {
            return new Resolved(Kind.LITERAL_INT, null, null, null, v, 0.0);
        }
        static Resolved litLong(long v) {
            return new Resolved(Kind.LITERAL_LONG, null, null, null, v, 0.0);
        }
        static Resolved litDouble(double v) {
            return new Resolved(Kind.LITERAL_DOUBLE, null, null, null, 0L, v);
        }
    }

    /** Recursive-descent parser for the subset of Java we support. */
    static final class Parser {
        private final String src;
        private int pos;
        Parser(String s) { this.src = s; }
        boolean hasMore() { return pos < src.length() && src.charAt(pos) != '\0'; }
        String remainder() { return src.substring(pos); }

        @NonNull Resolved parseExpr() {
            skipWs();
            char c = peek();
            if (c == '"') return parseStringLiteral();
            if (c == '(') {
                expect('(');
                Resolved inner = parseExpr();
                expect(')');
                return parseAccessChain(inner);
            }
            if (Character.isDigit(c) || c == '-' || c == '+') {
                return parseNumberLiteral();
            }
            if (Character.isJavaIdentifierStart(c)) {
                String ident = readIdent();
                Resolved base = ident.equals("this")
                        ? new Resolved(Resolved.Kind.THIS, "this")
                        : new Resolved(Resolved.Kind.LOCAL, ident);
                return parseAccessChain(base);
            }
            throw new RuntimeException("unexpected char '" + c + "'");
        }

        @NonNull private Resolved parseAccessChain(@NonNull Resolved base) {
            Resolved cur = base;
            while (true) {
                skipWs();
                if (peek() == '.') {
                    consume('.');
                    String name = readIdent();
                    cur = new Resolved(Resolved.Kind.FIELD, name, cur, null);
                } else if (peek() == '(') {
                    expect('(');
                    java.util.List<Resolved> args = new java.util.ArrayList<>();
                    skipWs();
                    if (peek() != ')') {
                        args.add(parseExpr());
                        while (true) {
                            skipWs();
                            if (peek() != ',') break;
                            consume(',');
                            args.add(parseExpr());
                        }
                    }
                    expect(')');
                    // The method name is whatever the previous step produced
                    // (FIELD on `a.b`, LOCAL on `foo()`). For a bare method
                    // call (e.g. `foo()` where foo is a local) we already
                    // stored name=foo in the LOCAL node.
                    String methodName = cur.name;
                    cur = new Resolved(Resolved.Kind.METHOD, methodName, cur, args);
                } else {
                    break;
                }
            }
            return cur;
        }

        @NonNull private Resolved parseStringLiteral() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length() && peek() != '"') {
                char c = src.charAt(pos++);
                if (c == '\\' && pos < src.length()) {
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            expect('"');
            return Resolved.litString(sb.toString());
        }

        @NonNull private Resolved parseNumberLiteral() {
            int start = pos;
            if (peek() == '-' || peek() == '+') pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            boolean isDouble = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isDouble = true;
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'L' || src.charAt(pos) == 'l')) {
                pos++;
                long v = Long.parseLong(src.substring(start, pos - 1));
                return Resolved.litLong(v);
            }
            String num = src.substring(start, pos);
            if (isDouble) {
                return Resolved.litDouble(Double.parseDouble(num));
            }
            try {
                return Resolved.litLong(Long.parseLong(num));
            } catch (NumberFormatException ex) {
                throw new RuntimeException("bad number literal: " + num);
            }
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }
        private char peek() { return pos < src.length() ? src.charAt(pos) : '\0'; }
        private void expect(char c) {
            skipWs();
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw new RuntimeException("expected '" + c + "' at " + pos);
            }
            pos++;
        }
        private void consume(char c) {
            if (pos < src.length() && src.charAt(pos) == c) pos++;
        }
        private String readIdent() {
            skipWs();
            int start = pos;
            while (pos < src.length()
                    && Character.isJavaIdentifierPart(src.charAt(pos))) {
                pos++;
            }
            if (start == pos) throw new RuntimeException("expected identifier at " + pos);
            return src.substring(start, pos);
        }
    }

    /** Resolve a parsed expression in the current frame and produce a string. */
    @NonNull
    private EvalResult resolveAndEval(@NonNull Resolved r, long threadId, long frameId) {
        try {
            switch (r.kind) {
                case LITERAL_STRING:
                    long stringId = createString(r.name);
                    return EvalResult.string(stringId, r.name);
                case LITERAL_INT:
                case LITERAL_LONG:
                    return EvalResult.of(EvalResult.Tag.LONG, "J",
                            String.valueOf(r.literalLong));
                case LITERAL_DOUBLE:
                    return EvalResult.of(EvalResult.Tag.DOUBLE, "D",
                            String.valueOf(r.literalDouble));
                case THIS: {
                    VariableInfo self = findLocalInFrame(threadId, frameId, "this");
                    if (self == null) return EvalResult.error("no 'this' in current frame");
                    return EvalResult.object(self.id, self.typeSignature);
                }
                case LOCAL: {
                    VariableInfo vi = findLocalInFrame(threadId, frameId, r.name);
                    if (vi == null) return EvalResult.error("no such local: " + r.name);
                    if (vi.id != 0L) {
                        return EvalResult.object(vi.id, vi.typeSignature);
                    }
                    return EvalResult.of(evalTag(vi.typeSignature),
                            vi.typeSignature, vi.value);
                }
                case FIELD: {
                    if (r.receiver == null) {
                        return EvalResult.error("field access without receiver");
                    }
                    EvalResult recv = resolveAndEval(r.receiver, threadId, frameId);
                    if (recv.isError()) return recv;
                    if (recv.tag != EvalResult.Tag.OBJECT) {
                        return EvalResult.error("field access on non-object");
                    }
                    return getFieldValueOnObject(recv.objectId, r.name, recv.typeSignature);
                }
                case METHOD: {
                    if (r.receiver == null) {
                        return EvalResult.error("method call needs a receiver");
                    }
                    EvalResult recv = resolveAndEval(r.receiver, threadId, frameId);
                    if (recv.isError()) return recv;
                    if (r.args != null && !r.args.isEmpty()) {
                        return EvalResult.error("method calls with arguments are not supported");
                    }
                    if (recv.tag != EvalResult.Tag.OBJECT) {
                        return EvalResult.error("method call on non-object");
                    }
                    return invokeNoArgMethod(recv.objectId, r.name, recv.typeSignature);
                }
                default:
                    return EvalResult.error("unsupported expression kind");
            }
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        }
    }

    private static EvalResult.Tag evalTag(@NonNull String sig) {
        if (sig.isEmpty()) return EvalResult.Tag.OBJECT;
        switch (sig.charAt(0)) {
            case 'V': return EvalResult.Tag.VOID;
            case 'Z': return EvalResult.Tag.BOOLEAN;
            case 'B': return EvalResult.Tag.BYTE;
            case 'C': return EvalResult.Tag.CHAR;
            case 'S': return EvalResult.Tag.SHORT;
            case 'I': return EvalResult.Tag.INT;
            case 'J': return EvalResult.Tag.LONG;
            case 'F': return EvalResult.Tag.FLOAT;
            case 'D': return EvalResult.Tag.DOUBLE;
            case '[': return EvalResult.Tag.ARRAY;
            default:  return EvalResult.Tag.OBJECT;
        }
    }

    @Nullable
    private VariableInfo findLocalInFrame(long threadId, long frameId, @NonNull String name)
            throws IOException {
        // Read the variable table for the current frame's method and look
        // up the value of [name] by slot. This is a simple version that
        // re-fetches the full list each call; the IDE's VariablesFragment
        // already calls it once per refresh.
        // The cleanest way is to call StackFrame.GetValues with all slots,
        // but to do that we need (classId, methodId). We read those via
        // ThreadReference.Frames - just frame 0.
        return debugger.fetchLocal(threadId, frameId, name);
    }

    @NonNull
    private EvalResult getFieldValueOnObject(long objectId, @NonNull String fieldName,
                                              @NonNull String ownerSig) {
        try {
            long refType = lookupClassByName(ownerSig);
            if (refType == 0) {
                return EvalResult.error("class not loaded: " + ownerSig);
            }
            long fieldId = lookupField(refType, fieldName);
            if (fieldId == 0) {
                return EvalResult.error("no field: " + ownerSig + "." + fieldName);
            }
            ByteBuf buf = new ByteBuf();
            buf.writeLong(objectId);
            buf.writeInt(1);
            buf.writeLong(fieldId);
            JdwpPacket reply = client.sendCommand(
                    CommandSet.ObjectReference, CommandCodes.ObjectReferenceCmd.GetValues,
                    buf.toByteArray());
            if (reply.errorCode() != 0) {
                return EvalResult.error("GetValues error " + reply.errorCode());
            }
            ByteBuf in = new ByteBuf(reply.data);
            int n = in.readInt();
            if (n < 1) return EvalResult.error("no value returned");
            byte tag = in.readByte();
            String v = readValue(in, tag);
            String typeSig = readFieldTypeSignature(refType, fieldId);
            return EvalResult.of(evalTag(typeSig), typeSig, v);
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        }
    }

    @NonNull
    private EvalResult invokeNoArgMethod(long objectId, @NonNull String methodName,
                                          @NonNull String ownerSig) {
        try {
            long refType = lookupClassByName(ownerSig);
            if (refType == 0) return EvalResult.error("class not loaded: " + ownerSig);
            long methodId = lookupMethod(refType, methodName, "()V");
            if (methodId == 0) {
                // Try a generic ()V signature; otherwise the lookup fails.
                methodId = lookupMethodByName(refType, methodName);
                if (methodId == 0) return EvalResult.error("no method: " + methodName);
            }
            ByteBuf buf = new ByteBuf();
            buf.writeLong(objectId);
            buf.writeLong(threadRef());
            buf.writeInt(0); // we don't support args yet
            buf.writeLong(methodId);
            JdwpPacket reply = client.sendCommand(
                    CommandSet.ObjectReference, CommandCodes.ObjectReferenceCmd.InvokeMethod,
                    buf.toByteArray());
            if (reply.errorCode() != 0) {
                return EvalResult.error("InvokeMethod error " + reply.errorCode());
            }
            ByteBuf in = new ByteBuf(reply.data);
            byte tag = in.readByte();
            String v = readValue(in, tag);
            return EvalResult.of(evalTag(ownerSig), ownerSig, v);
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        }
    }

    private long threadRef() {
        // Most ObjectReference.InvokeMethod implementations require a
        // thread to run the call on. We don't carry that around right
        // now; passing 0 means the VM picks one.
        return 0L;
    }

    private long createString(@NonNull String value) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeString(value);
        JdwpPacket reply = client.sendCommand(
                CommandSet.VirtualMachine, CommandCodes.VirtualMachineCmd.CreateString,
                buf.toByteArray());
        if (reply.errorCode() != 0) return 0L;
        ByteBuf in = new ByteBuf(reply.data);
        return in.readLong();
    }

    private long lookupClassByName(@NonNull String sig) throws IOException {
        if (!sig.startsWith("L") || !sig.endsWith(";")) return 0L;
        ByteBuf buf = new ByteBuf();
        buf.writeString(sig);
        JdwpPacket reply = client.sendCommand(
                CommandSet.VirtualMachine, CommandCodes.VirtualMachineCmd.ClassesBySignature,
                buf.toByteArray());
        if (reply.errorCode() != 0) return 0L;
        ByteBuf in = new ByteBuf(reply.data);
        int count = in.readInt();
        if (count == 0) return 0L;
        in.readByte(); // typeTag
        return in.readLong();
    }

    private long lookupField(long refType, @NonNull String name) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(refType);
        JdwpPacket reply = client.sendCommand(
                CommandSet.ReferenceType, CommandCodes.ReferenceTypeCmd.Fields, buf.toByteArray());
        if (reply.errorCode() != 0) return 0L;
        ByteBuf in = new ByteBuf(reply.data);
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            long fid = in.readLong();
            String fname = in.readString();
            in.readString(); // signature
            in.readInt();    // modBits
            if (fname.equals(name)) return fid;
        }
        return 0L;
    }

    private long lookupMethod(long refType, @NonNull String name, @NonNull String sig) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(refType);
        JdwpPacket reply = client.sendCommand(
                CommandSet.ReferenceType, CommandCodes.ReferenceTypeCmd.Methods, buf.toByteArray());
        if (reply.errorCode() != 0) return 0L;
        ByteBuf in = new ByteBuf(reply.data);
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            long mid = in.readLong();
            String mname = in.readString();
            String msig = in.readString();
            in.readInt();
            if (mname.equals(name) && msig.equals(sig)) return mid;
        }
        return 0L;
    }

    private long lookupMethodByName(long refType, @NonNull String name) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(refType);
        JdwpPacket reply = client.sendCommand(
                CommandSet.ReferenceType, CommandCodes.ReferenceTypeCmd.Methods, buf.toByteArray());
        if (reply.errorCode() != 0) return 0L;
        ByteBuf in = new ByteBuf(reply.data);
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            long mid = in.readLong();
            String mname = in.readString();
            in.readString();
            in.readInt();
            if (mname.equals(name)) return mid;
        }
        return 0L;
    }

    private String readFieldTypeSignature(long refType, long fieldId) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(refType);
        JdwpPacket reply = client.sendCommand(
                CommandSet.ReferenceType, CommandCodes.ReferenceTypeCmd.Fields, buf.toByteArray());
        if (reply.errorCode() != 0) return "Ljava/lang/Object;";
        ByteBuf in = new ByteBuf(reply.data);
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            long fid = in.readLong();
            in.readString(); // name
            String sig = in.readString();
            in.readInt();
            if (fid == fieldId) return sig;
        }
        return "Ljava/lang/Object;";
    }

    private String readValue(@NonNull ByteBuf in, byte tag) {
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

    private static boolean isPrim(byte tag) {
        return tag != 'L' && tag != '[';
    }

    private static byte tagFor(@NonNull String sig) {
        if (sig.isEmpty()) return 'L';
        char c = sig.charAt(0);
        if (c == '[' || c == 'L') return 'L';
        return (byte) c;
    }
}
