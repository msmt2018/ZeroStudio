/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Expression evaluator. PR-5/6/9 + Phase A1:
 *
 *    1. Resolve a frame's local variables to actual values (this method
 *       used to be a stub; now the SourceLocator populates them).
 *    2. evaluate(threadId, frameId, expr) - parse a small subset of Java
 *       expressions and return a string-serialised value:
 *          identifier            - a local var, parameter, or 'this'
 *          expr.field            - ObjectReference.GetFieldValue
 *          expr.method(args)     - call a method via
 *                                  ObjectReference.InvokeMethod
 *          String literal "..."  - VirtualMachine.CreateString
 *          int / long / double   - literal
 *          (expr)                - grouping
 *          a.b.c                 - chained field access
 *          a.b(args)             - chained method call
 *          a + b, a - b          - Phase A1 numeric arithmetic
 *          a * b, a / b, a % b   - Phase A1 numeric arithmetic
 *          1 + 2 * 3             - Phase A1 left-associative precedence
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
            enum Kind {
                LOCAL, FIELD, METHOD, THIS,
                LITERAL_STRING, LITERAL_INT, LITERAL_LONG, LITERAL_DOUBLE, NEW_STRING,
                // Phase A1: arithmetic binary operators. left/right carry the
                // operands; name carries the operator string ("+", "-", "*", "/", "%").
                BINARY,
                // Phase A6: array index. left carries the array
                // expression; right carries the index expression.
                // name is unused.
                INDEX,
                // Phase A7: ternary `cond ? thenExpr : elseExpr`.
                // left = condition, right = thenExpr, args = [elseExpr].
                TERNARY
            }
        final Kind kind;
        final String name;       // LOCAL, FIELD, METHOD, LITERAL_*, BINARY (op)
        @Nullable final Resolved receiver; // FIELD, METHOD
        @Nullable final List<Resolved> args; // METHOD, TERNARY ([elseExpr])
        @Nullable final Resolved left, right; // BINARY
        final long literalLong;  // for LITERAL_LONG
        final double literalDouble; // for LITERAL_DOUBLE

        Resolved(Kind kind, String name, @Nullable Resolved receiver, @Nullable List<Resolved> args) {
            this.kind = kind;
            this.name = name;
            this.receiver = receiver;
            this.args = args;
            this.left = null;
            this.right = null;
            this.literalLong = 0L;
            this.literalDouble = 0.0;
        }
        Resolved(Kind kind, String name) {
            this(kind, name, null, null);
        }
        // BINARY: left/right are the operands, name is the operator.
        private Resolved(Kind kind, String name, @Nullable Resolved left, @Nullable Resolved right) {
            this.kind = kind;
            this.name = name;
            this.receiver = null;
            this.args = null;
            this.left = left;
            this.right = right;
            this.literalLong = 0L;
            this.literalDouble = 0.0;
        }
        // LITERAL_*: literalLong/literalDouble carry the value, name is null.
        private Resolved(Kind kind, String name, @Nullable Resolved receiver,
                         @Nullable List<Resolved> args, long literalLong, double literalDouble) {
            this.kind = kind;
            this.name = name;
            this.receiver = receiver;
            this.args = args;
            this.left = null;
            this.right = null;
            this.literalLong = literalLong;
            this.literalDouble = literalDouble;
        }
        static Resolved litString(String s) {
            return new Resolved(Kind.LITERAL_STRING, s, null, null);
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
        /** Phase A1: build a binary-operation AST node. */
        static Resolved binop(String op, @NonNull Resolved left, @NonNull Resolved right) {
            return new Resolved(Kind.BINARY, op, left, right);
        }

        /** Phase A6: build an array-index node {@code array[index]}. */
        static Resolved index(@NonNull Resolved array, @NonNull Resolved idx) {
            return new Resolved(Kind.INDEX, null, array, idx);
        }

        /** Phase A7: build a ternary node {@code cond ? then : else}. */
        static Resolved ternary(@NonNull Resolved cond,
                                 @NonNull Resolved thenExpr,
                                 @NonNull Resolved elseExpr) {
            return new Resolved(Kind.TERNARY, null, cond,
                    java.util.Arrays.asList(thenExpr, elseExpr));
        }
    }

    /** Recursive-descent parser for the subset of Java we support. */
    static final class Parser {
        private final String src;
        private int pos;
        Parser(String s) { this.src = s; }
        boolean hasMore() { return pos < src.length() && src.charAt(pos) != '\0'; }
        String remainder() { return src.substring(pos); }

        /**
         * Top of the expression precedence chain.
         * <ul>
         *   <li>Phase A7: ternary {@code cond ? then : else} (parseTernary)
         *   <li>Phase A2: {@code == != &lt; &gt; &lt;= &gt;=} and
         *       {@code && ||} (parseLogicalOr, parseLogicalAnd, parseEquality,
         *       parseRelational)
         *   <li>Phase A1: {@code + - * / %} (parseAdditive, parseMultiplicative)
         * </ul>
         * Future phases will add a real {@code parseUnary} for
         * {@code -x} / {@code !x}.
         */
        @NonNull Resolved parseExpr() {
            return parseTernary();
        }

        /**
         * Phase A7: ternary expression. Right-associative: {@code a
         * ? b : c ? d : e} parses as {@code a ? b : (c ? d : e)}. The
         * then-branch recurses through {@code parseExpr} so it can
         * itself be a ternary; the else-branch recurses through
         * {@code parseTernary} (not {@code parseExpr}) so that the
         * right-associativity doesn't accidentally pull in additional
         * ternaries that belong to the outer call.
         */
        @NonNull private Resolved parseTernary() {
            Resolved cond = parseLogicalOr();
            skipWs();
            if (peek() != '?') return cond;
            pos++; // consume '?'
            Resolved thenExpr = parseExpr();
            expect(':');
            Resolved elseExpr = parseTernary();
            return Resolved.ternary(cond, thenExpr, elseExpr);
        }

        /**
         * Phase A2: logical-or. {@code ||} is the loosest binary
         * operator. Left-associative.
         */
        @NonNull private Resolved parseLogicalOr() {
            Resolved left = parseLogicalAnd();
            while (true) {
                skipWs();
                if (!tryConsume2("||")) break;
                Resolved right = parseLogicalAnd();
                left = Resolved.binop("||", left, right);
            }
            return left;
        }

        /**
         * Phase A2: logical-and. {@code &&} binds tighter than
         * {@code ||}. Left-associative.
         */
        @NonNull private Resolved parseLogicalAnd() {
            Resolved left = parseEquality();
            while (true) {
                skipWs();
                if (!tryConsume2("&&")) break;
                Resolved right = parseEquality();
                left = Resolved.binop("&&", left, right);
            }
            return left;
        }

        /**
         * Phase A2: equality. {@code ==} and {@code !=}. Two-character
         * operators; the parser must NOT greedily consume a single
         * {@code =} (we don't have assignment).
         */
        @NonNull private Resolved parseEquality() {
            Resolved left = parseRelational();
            while (true) {
                skipWs();
                char c = peek();
                String op = null;
                if (c == '=' && peekAt(pos + 1) == '=') {
                    pos += 2;
                    op = "==";
                } else if (c == '!' && peekAt(pos + 1) == '=') {
                    pos += 2;
                    op = "!=";
                }
                if (op == null) break;
                Resolved right = parseRelational();
                left = Resolved.binop(op, left, right);
            }
            return left;
        }

        /**
         * Phase A2: relational. {@code <}, {@code >}, {@code <=},
         * {@code >=}. All two-character forms are looked up here.
         */
        @NonNull private Resolved parseRelational() {
            Resolved left = parseAdditive();
            while (true) {
                skipWs();
                char c = peek();
                String op = null;
                if (c == '<') {
                    if (peekAt(pos + 1) == '=') {
                        pos += 2;
                        op = "<=";
                    } else {
                        pos++;
                        op = "<";
                    }
                } else if (c == '>') {
                    if (peekAt(pos + 1) == '=') {
                        pos += 2;
                        op = ">=";
                    } else {
                        pos++;
                        op = ">";
                    }
                }
                if (op == null) break;
                Resolved right = parseAdditive();
                left = Resolved.binop(op, left, right);
            }
            return left;
        }

        /**
         * Phase A1: additive level. {@code +} and {@code -} bind looser
         * than {@code * / %} so they live at the top of the chain.
         * Left-associative: {@code a - b - c} is {@code (a - b) - c}.
         */
        @NonNull private Resolved parseAdditive() {
            Resolved left = parseMultiplicative();
            while (true) {
                skipWs();
                char c = peek();
                if (c != '+' && c != '-') break;
                pos++;
                Resolved right = parseMultiplicative();
                left = Resolved.binop(String.valueOf(c), left, right);
            }
            return left;
        }

        /**
         * Phase A1: multiplicative level. {@code *}, {@code /} and
         * {@code %} bind tighter than {@code + -}.
         */
        @NonNull private Resolved parseMultiplicative() {
            Resolved left = parsePrimary();
            while (true) {
                skipWs();
                char c = peek();
                if (c != '*' && c != '/' && c != '%') break;
                pos++;
                Resolved right = parsePrimary();
                left = Resolved.binop(String.valueOf(c), left, right);
            }
            return left;
        }

        /**
         * Bottom of the precedence chain: literals, identifiers and
         * parenthesised sub-expressions. {@code -5} / {@code +5} are
         * still consumed here by {@link #parseNumberLiteral} (the parser
         * stores the sign in the numeric value); a true unary minus
         * operator (e.g. {@code -(a + b)}) is a future-phase enhancement.
         */
        @NonNull private Resolved parsePrimary() {
            skipWs();
            char c = peek();
            if (c == '"') return parseStringLiteral();
            if (c == '(') {
                pos++;
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
                } else if (peek() == '[') {
                    // Phase A6: `arr[i]` -> INDEX. The index can be
                    // any expression; we route through parseExpr so
                    // things like `arr[i + 1]` work transparently.
                    consume('[');
                    Resolved idx = parseExpr();
                    expect(']');
                    cur = Resolved.index(cur, idx);
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
        /**
         * Look ahead one character without consuming. {@code pos == src.length()}
         * returns {@code '\0'} (sentinel for EOF).
         */
        private char peekAt(int idx) {
            return idx < src.length() ? src.charAt(idx) : '\0';
        }
        /**
         * Phase A2: try to consume a 2-character operator. Returns true
         * if the next two characters match {@code op} and advances
         * {@code pos}; otherwise leaves {@code pos} untouched.
         */
        private boolean tryConsume2(@NonNull String op) {
            if (op.length() != 2) return false;
            if (pos + 1 >= src.length()) return false;
            if (src.charAt(pos) == op.charAt(0) && src.charAt(pos + 1) == op.charAt(1)) {
                pos += 2;
                return true;
            }
            return false;
        }
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
                    if (vi == null) {
                        // Phase A4: if the name is not a local, try to
                        // resolve it as a class reference. The parser
                        // can't tell the difference at parse time, so
                        // we always attempt the local lookup first
                        // and fall through to class resolution. This
                        // enables `Foo.COUNT` and `Math.max(a, b)`
                        // without any parser changes.
                        return resolveClassReference(r.name);
                    }
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
                    // Phase A4: a Tag.CLASS receiver means the access
                    // is a static-field read. Use ReferenceType.GetValues
                    // instead of ObjectReference.GetValues.
                    if (recv.tag == EvalResult.Tag.CLASS) {
                        return getStaticFieldValueOnClass(recv.objectId, r.name,
                                recv.typeSignature);
                    }
                    if (recv.tag != EvalResult.Tag.OBJECT) {
                        return EvalResult.error("field access on non-object");
                    }
                    // Phase A6: arrays expose their length through
                    // ArrayReference.Length rather than a regular field
                    // read. If the receiver's signature starts with `[`
                    // and the user typed `.length`, route to the array
                    // path. Other field reads (incl. normal "length"
                    // field on Strings, etc.) keep using the existing
                    // instance-field path.
                    if ("length".equals(r.name) && isArrayType(recv.typeSignature)) {
                        return getArrayLength(recv.objectId, recv.typeSignature);
                    }
                    return getFieldValueOnObject(recv.objectId, r.name, recv.typeSignature);
                }
                case INDEX: {
                    // Phase A6: `arr[i]` -> ArrayReference.GetValues.
                    if (r.left == null || r.right == null) {
                        return EvalResult.error("array index without array or index");
                    }
                    EvalResult arr = resolveAndEval(r.left, threadId, frameId);
                    if (arr.isError()) return arr;
                    if (!isArrayType(arr.typeSignature)) {
                        return EvalResult.error("index on non-array (sig="
                                + arr.typeSignature + ")");
                    }
                    EvalResult idx = resolveAndEval(r.right, threadId, frameId);
                    if (idx.isError()) return idx;
                    if (!isNumeric(idx)) {
                        return EvalResult.error("array index must be numeric");
                    }
                    int i = (int) parseLong(idx);
                    return getArrayElement(arr.objectId, i, arr.typeSignature);
                }
                case TERNARY: {
                    // Phase A7: `cond ? thenExpr : elseExpr`. Short-
                    // circuit: only the chosen branch is evaluated.
                    if (r.left == null || r.args == null || r.args.size() != 2) {
                        return EvalResult.error("malformed ternary");
                    }
                    EvalResult c = resolveAndEval(r.left, threadId, frameId);
                    if (c.isError()) return c;
                    return isTruthy(c)
                            ? resolveAndEval(r.args.get(0), threadId, frameId)
                            : resolveAndEval(r.args.get(1), threadId, frameId);
                }
                case METHOD: {
                    if (r.receiver == null) {
                        return EvalResult.error("method call needs a receiver");
                    }
                    EvalResult recv = resolveAndEval(r.receiver, threadId, frameId);
                    if (recv.isError()) return recv;
                    // PR-9: evaluate every argument first, then hand them to
                    // invokeMethod. Argument values must be encodable as JDWP
                    // values; if any arg evaluation fails the whole call fails.
                    java.util.List<EvalResult> argResults = new java.util.ArrayList<>();
                    if (r.args != null) {
                        for (Resolved arg : r.args) {
                            EvalResult a = resolveAndEval(arg, threadId, frameId);
                            if (a.isError()) return a;
                            argResults.add(a);
                        }
                    }
                    // Phase A5: a Tag.CLASS receiver means the call is a
                    // static method invocation. Use ClassType.InvokeMethod
                    // (which takes a refTypeId) instead of
                    // ObjectReference.InvokeMethod.
                    if (recv.tag == EvalResult.Tag.CLASS) {
                        return invokeStaticMethod(recv.objectId, r.name,
                                recv.typeSignature, argResults);
                    }
                    if (recv.tag != EvalResult.Tag.OBJECT) {
                        return EvalResult.error("method call on non-object");
                    }
                    return invokeMethod(recv.objectId, r.name, recv.typeSignature, argResults);
                }
                case BINARY: {
                    String op = r.name;
                    // Phase A2: short-circuit && and ||. The right
                    // operand is only evaluated when needed.
                    if ("&&".equals(op)) {
                        EvalResult l = resolveAndEval(r.left, threadId, frameId);
                        if (l.isError()) return l;
                        if (!isTruthy(l)) {
                            return EvalResult.of(EvalResult.Tag.BOOLEAN, "Z", "false");
                        }
                        EvalResult rhs = resolveAndEval(r.right, threadId, frameId);
                        if (rhs.isError()) return rhs;
                        return EvalResult.of(EvalResult.Tag.BOOLEAN, "Z",
                                isTruthy(rhs) ? "true" : "false");
                    }
                    if ("||".equals(op)) {
                        EvalResult l = resolveAndEval(r.left, threadId, frameId);
                        if (l.isError()) return l;
                        if (isTruthy(l)) {
                            return EvalResult.of(EvalResult.Tag.BOOLEAN, "Z", "true");
                        }
                        EvalResult rhs = resolveAndEval(r.right, threadId, frameId);
                        if (rhs.isError()) return rhs;
                        return EvalResult.of(EvalResult.Tag.BOOLEAN, "Z",
                                isTruthy(rhs) ? "true" : "false");
                    }
                    // Phase A2: comparison operators (== != < > <= >=)
                    if (isComparisonOp(op)) {
                        EvalResult l = resolveAndEval(r.left, threadId, frameId);
                        if (l.isError()) return l;
                        EvalResult rhs = resolveAndEval(r.right, threadId, frameId);
                        if (rhs.isError()) return rhs;
                        return applyComparisonOp(op, l, rhs);
                    }
                    // Phase A3: `+` with at least one String operand is
                    // string concatenation. We must detect this BEFORE
                    // applyBinaryOp (which would reject the String).
                    if ("+".equals(op)) {
                        EvalResult l = resolveAndEval(r.left, threadId, frameId);
                        if (l.isError()) return l;
                        EvalResult rhs = resolveAndEval(r.right, threadId, frameId);
                        if (rhs.isError()) return rhs;
                        if (isStringLike(l) || isStringLike(rhs)) {
                            return stringConcat(l, rhs);
                        }
                        return applyBinaryOp(op, l, rhs);
                    }
                    // Phase A1: arithmetic - * / %.
                    EvalResult l = resolveAndEval(r.left, threadId, frameId);
                    if (l.isError()) return l;
                    EvalResult rhs = resolveAndEval(r.right, threadId, frameId);
                    if (rhs.isError()) return rhs;
                    return applyBinaryOp(op, l, rhs);
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

    /**
     * Phase A1: apply an arithmetic binary operator to two evaluated
     * operands. Operators supported: {@code + - * / %}. String concat
     * is Phase A3; comparison / logical operators are Phase A2.
     *
     * <p>Widening rules:
     * <ul>
     *   <li>If either operand is float/double, the result is double.
     *   <li>Otherwise the result is long. Bytes / shorts / ints / longs
     *       are all coerced to long and the result is long.
     * </ul>
     *
     * <p>If either operand is non-numeric (object, array, string, void,
     * boolean) the call returns an error result.
     */
    @NonNull
    static EvalResult applyBinaryOp(@NonNull String op,
                                    @NonNull EvalResult l,
                                    @NonNull EvalResult r) {
        if (!isNumeric(l)) {
            return EvalResult.error("operator '" + op
                    + "' requires numeric left operand (got " + l.typeSignature + ")");
        }
        if (!isNumeric(r)) {
            return EvalResult.error("operator '" + op
                    + "' requires numeric right operand (got " + r.typeSignature + ")");
        }
        boolean isDouble = "D".equals(l.typeSignature) || "F".equals(l.typeSignature)
                || "D".equals(r.typeSignature) || "F".equals(r.typeSignature);
        if (isDouble) {
            double a = parseDouble(l);
            double b = parseDouble(r);
            switch (op) {
                case "+": return EvalResult.of(EvalResult.Tag.DOUBLE, "D", String.valueOf(a + b));
                case "-": return EvalResult.of(EvalResult.Tag.DOUBLE, "D", String.valueOf(a - b));
                case "*": return EvalResult.of(EvalResult.Tag.DOUBLE, "D", String.valueOf(a * b));
                case "/":
                    if (b == 0.0) return EvalResult.error("division by zero");
                    return EvalResult.of(EvalResult.Tag.DOUBLE, "D", String.valueOf(a / b));
                case "%":
                    if (b == 0.0) return EvalResult.error("modulo by zero");
                    return EvalResult.of(EvalResult.Tag.DOUBLE, "D", String.valueOf(a % b));
                default:
                    return EvalResult.error("unsupported binary operator: " + op);
            }
        }
        long a = parseLong(l);
        long b = parseLong(r);
        switch (op) {
            case "+": return EvalResult.of(EvalResult.Tag.LONG, "J", String.valueOf(a + b));
            case "-": return EvalResult.of(EvalResult.Tag.LONG, "J", String.valueOf(a - b));
            case "*": return EvalResult.of(EvalResult.Tag.LONG, "J", String.valueOf(a * b));
            case "/":
                if (b == 0L) return EvalResult.error("division by zero");
                return EvalResult.of(EvalResult.Tag.LONG, "J", String.valueOf(a / b));
            case "%":
                if (b == 0L) return EvalResult.error("modulo by zero");
                return EvalResult.of(EvalResult.Tag.LONG, "J", String.valueOf(a % b));
            default:
                return EvalResult.error("unsupported binary operator: " + op);
        }
    }

    /**
     * Phase A1 helper: true if {@code r} carries a numeric type signature
     * ({@code B S I J F D}). The display value is consumed lazily by
     * {@link #parseLong} / {@link #parseDouble}.
     */
    private static boolean isNumeric(@NonNull EvalResult r) {
        if (r.typeSignature == null || r.typeSignature.isEmpty()) return false;
        char c = r.typeSignature.charAt(0);
        return c == 'B' || c == 'S' || c == 'I' || c == 'J' || c == 'F' || c == 'D';
    }

    /**
     * Phase A2 helper: true if {@code op} is one of the comparison
     * operators. Arithmetic / logical operators return false.
     */
    private static boolean isComparisonOp(@NonNull String op) {
        switch (op) {
            case "==": case "!=": case "<": case ">": case "<=": case ">=":
                return true;
            default:
                return false;
        }
    }

    /**
     * Phase A2 helper: true if {@code r} is a truthy value. Used by
     * short-circuit {@code &&} and {@code ||}. Object references are
     * always truthy; numeric / boolean values follow Java rules; null
     * display values are falsy.
     */
    private static boolean isTruthy(@NonNull EvalResult r) {
        if (r == null || r.isError()) return false;
        String sig = r.typeSignature == null ? "" : r.typeSignature;
        String v = r.displayValue == null ? "" : r.displayValue;
        if (sig.isEmpty()) return !v.isEmpty() && !v.equals("false") && !v.equals("0");
        switch (sig.charAt(0)) {
            case 'Z': return v.equals("true");
            case 'B': case 'S': case 'I': case 'J':
                try { return Long.parseLong(v) != 0L; } catch (NumberFormatException e) { return false; }
            case 'F': case 'D':
                try { return Double.parseDouble(v) != 0.0; } catch (NumberFormatException e) { return false; }
            case 'L': case '[':
                return r.objectId != 0L;
            default:
                return !v.isEmpty();
        }
    }

    /**
     * Phase A2: apply a comparison operator to two evaluated operands.
     * The result is always boolean ({@code Tag.BOOLEAN}, signature
     * {@code "Z"}).
     *
     * <ul>
     *   <li>{@code ==} and {@code !=} work on any pair of values;
     *       primitives compare by parsed value, references compare by
     *       {@link EvalResult#objectId}.
     *   <li>{@code <}, {@code >}, {@code <=}, {@code >=} require
     *       numeric operands.
     * </ul>
     */
    @NonNull
    static EvalResult applyComparisonOp(@NonNull String op,
                                         @NonNull EvalResult l,
                                         @NonNull EvalResult r) {
        switch (op) {
            case "==":
                return EvalResult.of(EvalResult.Tag.BOOLEAN, "Z",
                        equalsValue(l, r) ? "true" : "false");
            case "!=":
                return EvalResult.of(EvalResult.Tag.BOOLEAN, "Z",
                        equalsValue(l, r) ? "false" : "true");
            case "<": case ">": case "<=": case ">=":
                if (!isNumeric(l)) {
                    return EvalResult.error("operator '" + op
                            + "' requires numeric left operand (got " + l.typeSignature + ")");
                }
                if (!isNumeric(r)) {
                    return EvalResult.error("operator '" + op
                            + "' requires numeric right operand (got " + r.typeSignature + ")");
                }
                boolean isDouble = "D".equals(l.typeSignature) || "F".equals(l.typeSignature)
                        || "D".equals(r.typeSignature) || "F".equals(r.typeSignature);
                boolean res;
                if (isDouble) {
                    double a = parseDouble(l);
                    double b = parseDouble(r);
                    switch (op) {
                        case "<":  res = a < b;  break;
                        case ">":  res = a > b;  break;
                        case "<=": res = a <= b; break;
                        case ">=": res = a >= b; break;
                        default: return EvalResult.error("unsupported op: " + op);
                    }
                } else {
                    long a = parseLong(l);
                    long b = parseLong(r);
                    switch (op) {
                        case "<":  res = a < b;  break;
                        case ">":  res = a > b;  break;
                        case "<=": res = a <= b; break;
                        case ">=": res = a >= b; break;
                        default: return EvalResult.error("unsupported op: " + op);
                    }
                }
                return EvalResult.of(EvalResult.Tag.BOOLEAN, "Z",
                        res ? "true" : "false");
            default:
                return EvalResult.error("unsupported comparison op: " + op);
        }
    }

    /**
     * Phase A2 helper: structural equality between two evaluated
     * values. Reference-typed values compare by {@link EvalResult#objectId};
     * primitives compare by parsed numeric / boolean displayValue.
     */
    private static boolean equalsValue(@NonNull EvalResult l, @NonNull EvalResult r) {
        String lsig = l.typeSignature == null ? "" : l.typeSignature;
        String rsig = r.typeSignature == null ? "" : r.typeSignature;
        // Reference types compare by objectId.
        boolean lref = !lsig.isEmpty() && (lsig.charAt(0) == 'L' || lsig.charAt(0) == '[');
        boolean rref = !rsig.isEmpty() && (rsig.charAt(0) == 'L' || rsig.charAt(0) == '[');
        if (lref || rref) {
            // If both are refs, objectId must match. If one is ref and
            // the other is primitive, they cannot be equal.
            if (lref && rref) return l.objectId == r.objectId;
            return false;
        }
        String lv = l.displayValue == null ? "" : l.displayValue;
        String rv = r.displayValue == null ? "" : r.displayValue;
        if (!lsig.isEmpty() && lsig.charAt(0) == 'Z') {
            return lv.equals(rv);
        }
        if (!lsig.isEmpty() && (lsig.charAt(0) == 'F' || lsig.charAt(0) == 'D')) {
            try { return Double.parseDouble(lv) == Double.parseDouble(rv); }
            catch (NumberFormatException e) { return false; }
        }
        try { return Long.parseLong(lv) == Long.parseLong(rv); }
        catch (NumberFormatException e) { return false; }
    }

    /**
     * Phase A3 helper: true if {@code r} is string-typed. Either the
     * {@code Tag.STRING} tag (set by {@link LITERAL_STRING} evaluation)
     * or the {@code Ljava/lang/String;} type signature.
     */
    private static boolean isStringLike(@NonNull EvalResult r) {
        if (r == null) return false;
        if (r.tag == EvalResult.Tag.STRING) return true;
        String sig = r.typeSignature;
        return sig != null && sig.equals("Ljava/lang/String;");
    }

    /**
     * Phase A3: concatenate the display forms of two evaluated
     * operands and create a fresh JDWP string for the result. The
     * non-string side is converted to its display string (no toString
     * call; we don't have the precise class info on hand). Returns an
     * error if the JDWP {@code CreateString} call fails.
     */
    @NonNull
    private EvalResult stringConcat(@NonNull EvalResult l, @NonNull EvalResult r) {
        String combined = toConcatString(l) + toConcatString(r);
        try {
            long stringId = createString(combined);
            return EvalResult.string(stringId, combined);
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        }
    }

    /**
     * Phase A3 helper: convert an evaluated operand to the string used
     * for concatenation. {@code null} renders as {@code "null"};
     * objects fall back to their displayValue or a synthetic id-based
     * string. We do not call {@code toString()} on remote objects
     * because that would require a JDWP round-trip and the type
     * information is not always available.
     */
    @NonNull
    private static String toConcatString(@NonNull EvalResult r) {
        if (r == null) return "null";
        if (r.displayValue != null && !r.displayValue.isEmpty()) return r.displayValue;
        if (r.objectId != 0L) return "<object " + r.objectId + ">";
        return "null";
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
    private EvalResult invokeMethod(long objectId, @NonNull String methodName,
                                     @NonNull String ownerSig,
                                     @NonNull java.util.List<EvalResult> argResults) {
        try {
            long refType = lookupClassByName(ownerSig);
            if (refType == 0) return EvalResult.error("class not loaded: " + ownerSig);
            // We don't know the return type up front, so try the most common
            // convention `(... )V` first, then fall back to a name + arity
            // search across the class's method table.
            long methodId = lookupMethod(refType, methodName, buildSignature(argResults));
            if (methodId == 0) {
                methodId = lookupMethodByNameAndArity(refType, methodName, argResults.size());
                if (methodId == 0) return EvalResult.error("no method: " + methodName);
            }
            ByteBuf buf = new ByteBuf();
            buf.writeLong(objectId);
            buf.writeLong(threadRef());
            buf.writeInt(argResults.size());
            for (EvalResult a : argResults) {
                encodeValue(buf, a);
            }
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
            // PR-9: the actual return type comes from the InvokeMethod
            // response's tag, not the receiver's owner signature.
            String returnSig = tagToSignature(tag);
            return EvalResult.of(evalTag(returnSig), returnSig, v);
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        }
    }

    /**
     * Phase A5: invoke a static method. Same arg-eval flow as
     * {@link #invokeMethod}, but uses {@code ClassType.InvokeMethod}
     * (command set 3, command 3) which takes a refTypeId instead of
     * an objectId. We restrict the method lookup to methods that
     * actually have the {@code ACC_STATIC} modifier set; without
     * that, a static call could accidentally bind to an instance
     * overload with the same name and arity.
     */
    @NonNull
    private EvalResult invokeStaticMethod(long refType, @NonNull String methodName,
                                           @NonNull String ownerSig,
                                           @NonNull java.util.List<EvalResult> argResults) {
        try {
            long methodId = lookupStaticMethodByNameAndArity(refType, methodName, argResults.size());
            if (methodId == 0) {
                return EvalResult.error("no static method: "
                        + ownerSig + "." + methodName);
            }
            ByteBuf buf = new ByteBuf();
            buf.writeLong(refType);
            buf.writeLong(threadRef());
            buf.writeInt(argResults.size());
            for (EvalResult a : argResults) {
                encodeValue(buf, a);
            }
            buf.writeLong(methodId);
            JdwpPacket reply = client.sendCommand(
                    CommandSet.ClassType, CommandCodes.ClassTypeCmd.InvokeMethod,
                    buf.toByteArray());
            if (reply.errorCode() != 0) {
                return EvalResult.error("ClassType.InvokeMethod error " + reply.errorCode());
            }
            ByteBuf in = new ByteBuf(reply.data);
            byte tag = in.readByte();
            String v = readValue(in, tag);
            String returnSig = tagToSignature(tag);
            return EvalResult.of(evalTag(returnSig), returnSig, v);
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        }
    }

    /**
     * Phase A5: like {@link #lookupMethodByNameAndArity} but only
     * returns methods with {@code ACC_STATIC} (0x0008) set. Used by
     * {@link #invokeStaticMethod} so we never accidentally dispatch a
     * static call to an instance overload.
     */
    private long lookupStaticMethodByNameAndArity(long refType, @NonNull String name, int arity)
            throws IOException {
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
            int modBits = in.readInt();
            if (mname.equals(name) && countArity(msig) == arity
                    && (modBits & 0x0008) != 0) return mid;
        }
        return 0L;
    }

    /**
     * Build a JDWP method signature from a list of argument {@link EvalResult}s.
     * The return type is hard-coded to {@code V} because the caller doesn't
     * know it; the lookup falls back to name + arity when an exact match
     * fails. This signature is only used as a hint.
     */
    @NonNull
    static String buildSignature(@NonNull java.util.List<EvalResult> argResults) {
        StringBuilder sb = new StringBuilder("(");
        for (EvalResult a : argResults) {
            sb.append(a.typeSignature);
        }
        sb.append(")V");
        return sb.toString();
    }

    /**
     * Write [arg] into [buf] as a JDWP value, in the order JDWP expects:
     * a one-byte tag is NOT written here; the caller already knows the
     * tag from {@code arg.typeSignature}. The bytes match the wire format
     * for {@code ObjectReference.InvokeMethod}.
     */
    static void encodeValue(@NonNull ByteBuf buf, @NonNull EvalResult arg) {
        String sig = arg.typeSignature;
        if (sig.isEmpty()) {
            // Best-effort fallback: assume the arg is an object reference.
            buf.writeLong(arg.objectId);
            return;
        }
        char c = sig.charAt(0);
        switch (c) {
            case 'Z': buf.writeByte(parseBoolean(arg) ? 1 : 0); break;
            case 'B': buf.writeByte(parseByte(arg)); break;
            case 'C': buf.writeShort((int) parseChar(arg)); break;
            case 'S': buf.writeShort((int) parseShort(arg)); break;
            case 'I': buf.writeInt((int) parseLong(arg)); break;
            case 'J': buf.writeLong(parseLong(arg)); break;
            case 'F': buf.writeFloat((float) parseDouble(arg)); break;
            case 'D': buf.writeDouble(parseDouble(arg)); break;
            case 'L':
            case '[':
                buf.writeLong(arg.objectId);
                break;
            default:
                throw new IllegalStateException("unsupported arg type: " + sig);
        }
    }

    private static boolean parseBoolean(@NonNull EvalResult r) {
        return r.displayValue != null && r.displayValue.equals("true");
    }

    private static byte parseByte(@NonNull EvalResult r) {
        try { return Byte.parseByte(r.displayValue); }
        catch (NumberFormatException e) { return 0; }
    }

    private static char parseChar(@NonNull EvalResult r) {
        if (r.displayValue == null || r.displayValue.isEmpty()) return '\0';
        return r.displayValue.charAt(0);
    }

    private static short parseShort(@NonNull EvalResult r) {
        try { return Short.parseShort(r.displayValue); }
        catch (NumberFormatException e) { return 0; }
    }

    private static long parseLong(@NonNull EvalResult r) {
        try { return Long.parseLong(r.displayValue); }
        catch (NumberFormatException e) { return 0L; }
    }

    private static double parseDouble(@NonNull EvalResult r) {
        try { return Double.parseDouble(r.displayValue); }
        catch (NumberFormatException e) { return 0.0; }
    }

    /**
     * Convert a JDWP value tag byte to the corresponding type signature.
     * Object tags map to {@code Ljava/lang/Object;} because we don't know
     * the actual class; the caller is expected to widen with a follow-up
     * {@code ObjectReference.ReferenceType} if the precise type matters.
     */
    @NonNull
    static String tagToSignature(byte tag) {
        switch (tag) {
            case 'V': return "V";
            case 'Z': return "Z";
            case 'B': return "B";
            case 'C': return "C";
            case 'S': return "S";
            case 'I': return "I";
            case 'J': return "J";
            case 'F': return "F";
            case 'D': return "D";
            case 'L': return "Ljava/lang/Object;";
            case '[': return "[Ljava/lang/Object;";
            default:  return "Ljava/lang/Object;";
        }
    }

    /**
     * Count the number of type parameters in a JDWP method signature, e.g.
     * {@code "(II)V" -> 2}, {@code "(Ljava/lang/String;J)V" -> 2},
     * {@code "()V" -> 0}.
     */
    static int countArity(@NonNull String methodSig) {
        int open = methodSig.indexOf('(');
        int close = methodSig.indexOf(')');
        if (open < 0 || close < 0 || close <= open) return 0;
        String params = methodSig.substring(open + 1, close);
        if (params.isEmpty()) return 0;
        int count = 0;
        int i = 0;
        while (i < params.length()) {
            char c = params.charAt(i);
            if (c == 'L') {
                int semi = params.indexOf(';', i);
                if (semi < 0) break;
                i = semi + 1;
            } else if (c == '[') {
                i++;
                if (i < params.length() && params.charAt(i) == 'L') {
                    int semi = params.indexOf(';', i);
                    if (semi < 0) break;
                    i = semi + 1;
                } else if (i < params.length()) {
                    i++;
                }
            } else {
                i++;
            }
            count++;
        }
        return count;
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

    /**
     * Phase A4: try to resolve a bare name (no local matches) as a
     * class reference. The class is looked up by signature; we accept
     * both dotted form ({@code com.example.Foo}) and slashed form
     * ({@code com/example/Foo}). The returned {@link EvalResult} has
     * tag {@code Tag.CLASS} and carries the refTypeId in
     * {@code objectId}.
     */
    @NonNull
    private EvalResult resolveClassReference(@NonNull String name) {
        // Accept both fully-qualified class names and bare names.
        // For bare names we don't have a package, so we try the
        // slashed form first (Java's canonical form) and then the
        // dotted form.
        String[] attempts = {
            "L" + name.replace('.', '/') + ";",
            "L" + name + ";",
        };
        for (String sig : attempts) {
            try {
                long refType = lookupClassByName(sig);
                if (refType != 0L) {
                    return EvalResult.klass(refType, sig);
                }
            } catch (IOException ex) {
                return EvalResult.error("io: " + ex.getMessage());
            }
        }
        return EvalResult.error("no such local or class: " + name);
    }

    /**
     * Phase A4: read a static field value. Equivalent to
     * {@link #getFieldValueOnObject} but uses
     * {@code ReferenceType.GetValues} (which takes a refTypeId) instead
     * of {@code ObjectReference.GetValues} (which takes an objectId).
     */
    @NonNull
    private EvalResult getStaticFieldValueOnClass(long refType, @NonNull String fieldName,
                                                   @NonNull String classSig) {
        try {
            long fieldId = lookupField(refType, fieldName);
            if (fieldId == 0) {
                return EvalResult.error("no static field: " + classSig + "." + fieldName);
            }
            ByteBuf buf = new ByteBuf();
            buf.writeLong(refType);
            buf.writeInt(1);
            buf.writeLong(fieldId);
            JdwpPacket reply = client.sendCommand(
                    CommandSet.ReferenceType, CommandCodes.ReferenceTypeCmd.GetValues,
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

    /**
     * Phase A6: true if {@code sig} is an array type signature
     * (starts with {@code [}).
     */
    static boolean isArrayType(@NonNull String sig) {
        return !sig.isEmpty() && sig.charAt(0) == '[';
    }

    /**
     * Phase A6: extract the element type signature from an array
     * type signature. For {@code [I} returns {@code I}, for
     * {@code [[Ljava/lang/String;} returns {@code [Ljava/lang/String;}.
     * (The caller can call this recursively for nested arrays.)
     */
    @NonNull
    static String arrayElementSignature(@NonNull String sig) {
        if (sig.length() < 2) return sig;
        return sig.substring(1);
    }

    /**
     * Phase A6: read the length of an array via
     * {@code ArrayReference.Length} (command set 13, command 1).
     */
    @NonNull
    private EvalResult getArrayLength(long arrayId, @NonNull String arraySig) {
        try {
            ByteBuf buf = new ByteBuf();
            buf.writeLong(arrayId);
            JdwpPacket reply = client.sendCommand(
                    CommandSet.ArrayReference, CommandCodes.ArrayReferenceCmd.Length,
                    buf.toByteArray());
            if (reply.errorCode() != 0) {
                return EvalResult.error("ArrayReference.Length error " + reply.errorCode());
            }
            ByteBuf in = new ByteBuf(reply.data);
            int len = in.readInt();
            return EvalResult.of(EvalResult.Tag.INT, "I", String.valueOf(len));
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        }
    }

    /**
     * Phase A6: read a single element of an array via
     * {@code ArrayReference.GetValues} (command set 13, command 2).
     * The element type is derived from the array's signature; the
     * result is then returned with that element type and the value
     * read from the wire.
     */
    @NonNull
    private EvalResult getArrayElement(long arrayId, int index, @NonNull String arraySig) {
        String elemSig = arrayElementSignature(arraySig);
        byte elemTag;
        if (elemSig.isEmpty()) {
            return EvalResult.error("malformed array signature: " + arraySig);
        }
        switch (elemSig.charAt(0)) {
            case 'Z': elemTag = 'Z'; break;
            case 'B': elemTag = 'B'; break;
            case 'C': elemTag = 'C'; break;
            case 'S': elemTag = 'S'; break;
            case 'I': elemTag = 'I'; break;
            case 'J': elemTag = 'J'; break;
            case 'F': elemTag = 'F'; break;
            case 'D': elemTag = 'D'; break;
            case 'L': case '[': elemTag = 'L'; break;
            default:
                return EvalResult.error("unsupported array element type: " + elemSig);
        }
        try {
            ByteBuf buf = new ByteBuf();
            buf.writeLong(arrayId);
            buf.writeInt(1);
            buf.writeInt(index);
            JdwpPacket reply = client.sendCommand(
                    CommandSet.ArrayReference, CommandCodes.ArrayReferenceCmd.GetValues,
                    buf.toByteArray());
            if (reply.errorCode() != 0) {
                return EvalResult.error("ArrayReference.GetValues error " + reply.errorCode());
            }
            ByteBuf in = new ByteBuf(reply.data);
            byte tag = in.readByte();
            String v = readValue(in, tag);
            return EvalResult.of(evalTag(elemSig), elemSig, v);
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        }
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

    /**
     * PR-9: find a method by name + arity. Useful as a fallback when the
     * full method signature can't be reconstructed from the evaluated
     * arguments (e.g. when the parser stores all int-like literals as
     * {@code J}).
     */
    private long lookupMethodByNameAndArity(long refType, @NonNull String name, int arity)
            throws IOException {
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
            if (mname.equals(name) && countArity(msig) == arity) return mid;
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

    /**
     * Phase B3: encode a primitive value of the given {@code sig} for
     * a SetValues payload. The output is the raw bytes that follow
     * the {@code (tag, value...)} pair in a Set request. For 'L'
     * and '[' this writes an 8-byte object id; for 'V' nothing is
     * written.
     */
    static void writeValue(@NonNull ByteBuf out, @NonNull String sig, @NonNull String value) {
        if (sig.isEmpty()) return;
        switch (sig.charAt(0)) {
            case 'V': break;
            case 'Z': out.writeByte(Boolean.parseBoolean(value) ? 1 : 0); break;
            case 'B': out.writeByte(Byte.parseByte(value)); break;
            case 'C': out.writeShort((int) value.charAt(0)); break;
            case 'S': out.writeShort(Short.parseShort(value)); break;
            case 'I': out.writeInt(Integer.parseInt(value)); break;
            case 'J': out.writeLong(Long.parseLong(value)); break;
            case 'F': out.writeFloat(Float.parseFloat(value)); break;
            case 'D': out.writeDouble(Double.parseDouble(value)); break;
            case 'L': case '[': out.writeLong(Long.parseLong(value)); break;
        }
    }

    /**
     * Phase B3: write a single element of an array via
     * {@code ArrayReference.SetValues} (command set 13, command 3).
     * The element's type is derived from the array's signature; the
     * value is encoded as a string. The method is best-effort and
     * never throws — failures are returned as [EvalResult.error].
     */
    @NonNull
    public EvalResult setArrayElement(long arrayId, int index, @NonNull String arraySig,
                                      @NonNull String value) {
        String elemSig = arrayElementSignature(arraySig);
        if (elemSig.isEmpty()) {
            return EvalResult.error("malformed array signature: " + arraySig);
        }
        try {
            ByteBuf buf = new ByteBuf();
            buf.writeLong(arrayId);
            buf.writeInt(1);     // one element
            buf.writeInt(index);
            buf.writeByte(tagFor(elemSig));
            writeValue(buf, elemSig, value);
            JdwpPacket reply = client.sendCommand(
                    CommandSet.ArrayReference, CommandCodes.ArrayReferenceCmd.SetValues,
                    buf.toByteArray());
            if (reply.errorCode() != 0) {
                return EvalResult.error("ArrayReference.SetValues error " + reply.errorCode());
            }
            return EvalResult.of(evalTag(elemSig), elemSig, value);
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        } catch (NumberFormatException ex) {
            return EvalResult.error("bad value: " + ex.getMessage());
        }
    }

    /**
     * Phase B3: write a single local variable of the current frame via
     * {@code StackFrame.SetValues} (command set 16, command 2). The
     * caller supplies the local's slot + type signature + value.
     */
    @NonNull
    public EvalResult setLocal(long threadId, long frameId, int slot,
                               @NonNull String sig, @NonNull String value) {
        try {
            ByteBuf buf = new ByteBuf();
            buf.writeLong(threadId);
            buf.writeLong(frameId);
            buf.writeInt(1);     // one value
            buf.writeInt(slot);
            buf.writeByte(tagFor(sig));
            writeValue(buf, sig, value);
            JdwpPacket reply = client.sendCommand(
                    CommandSet.StackFrame, CommandCodes.StackFrameCmd.SetValues,
                    buf.toByteArray());
            if (reply.errorCode() != 0) {
                return EvalResult.error("StackFrame.SetValues error " + reply.errorCode());
            }
            return EvalResult.of(evalTag(sig), sig, value);
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        } catch (NumberFormatException ex) {
            return EvalResult.error("bad value: " + ex.getMessage());
        }
    }

    /**
     * Phase B3: write a single static field of a class via
     * {@code ClassType.SetValues} (command set 3, command 2). The
     * caller supplies the class refTypeId, the fieldId, signature,
     * and string-encoded value.
     */
    @NonNull
    public EvalResult setStaticField(long classId, long fieldId,
                                     @NonNull String sig, @NonNull String value) {
        try {
            ByteBuf buf = new ByteBuf();
            buf.writeLong(classId);
            buf.writeInt(1);     // one value
            buf.writeLong(fieldId);
            buf.writeByte(tagFor(sig));
            writeValue(buf, sig, value);
            JdwpPacket reply = client.sendCommand(
                    CommandSet.ClassType, CommandCodes.ClassTypeCmd.SetValues,
                    buf.toByteArray());
            if (reply.errorCode() != 0) {
                return EvalResult.error("ClassType.SetValues error " + reply.errorCode());
            }
            return EvalResult.of(evalTag(sig), sig, value);
        } catch (IOException ex) {
            return EvalResult.error("io: " + ex.getMessage());
        } catch (NumberFormatException ex) {
            return EvalResult.error("bad value: " + ex.getMessage());
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
