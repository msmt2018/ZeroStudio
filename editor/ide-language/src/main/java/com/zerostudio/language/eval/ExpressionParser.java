package com.zerostudio.language.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * 极简表达式语法解析器（递归下降）：支持字面量、标识符、二元运算、函数调用、
 * 数组访问、成员访问、强制类型转换。可被 EvalEngine 用于解析 watch 表达式。
 *
 * 文法：
 *   expr        := ternary
 *   ternary     := logicalOr ('?' expr ':' expr)?
 *   logicalOr   := logicalAnd ('||' logicalAnd)*
 *   logicalAnd  := equality ('&&' equality)*
 *   equality    := comparison (('==' | '!=') comparison)*
 *   comparison  := additive (('<' | '>' | '<=' | '>=') additive)*
 *   additive    := multiplicative (('+' | '-') multiplicative)*
 *   multiplicative := unary (('*' | '/' | '%') unary)*
 *   unary       := ('!' | '-' | '+')? cast
 *   cast        := '(' type ')' cast | postfix
 *   postfix     := primary ('[' expr ']' | '.' IDENT | '(' args ')')*
 *   primary     := NUMBER | STRING | IDENT | '(' expr ')'
 */
public final class ExpressionParser {

    public enum NodeKind {
        NUMBER, STRING, IDENT, BINARY, UNARY, CALL, MEMBER, INDEX, CAST, ARRAY_LITERAL
    }

    public static class Node {
        public final NodeKind kind;
        public final String value;       // 原始字面量 / 操作符
        public final List<Node> children;
        public final String typeName;    // for CAST

        private Node(NodeKind kind, String value, List<Node> children, String typeName) {
            this.kind = kind;
            this.value = value;
            this.children = children;
            this.typeName = typeName;
        }

        public static Node number(String s) { return new Node(NodeKind.NUMBER, s, null, null); }
        public static Node string(String s) { return new Node(NodeKind.STRING, s, null, null); }
        public static Node ident(String s) { return new Node(NodeKind.IDENT, s, null, null); }
        public static Node binary(String op, Node l, Node r) {
            List<Node> c = new ArrayList<>(); c.add(l); c.add(r);
            return new Node(NodeKind.BINARY, op, c, null);
        }
        public static Node unary(String op, Node e) {
            List<Node> c = new ArrayList<>(); c.add(e);
            return new Node(NodeKind.UNARY, op, c, null);
        }
        public static Node call(Node callee, List<Node> args) {
            List<Node> c = new ArrayList<>();
            c.add(callee);
            if (args != null) c.addAll(args);
            return new Node(NodeKind.CALL, "", c, null);
        }
        public static Node member(Node target, String name) {
            List<Node> c = new ArrayList<>(); c.add(target);
            return new Node(NodeKind.MEMBER, name, c, null);
        }
        public static Node index(Node target, Node idx) {
            List<Node> c = new ArrayList<>(); c.add(target); c.add(idx);
            return new Node(NodeKind.INDEX, "", c, null);
        }
        public static Node cast(Node expr, String type) {
            List<Node> c = new ArrayList<>(); c.add(expr);
            return new Node(NodeKind.CAST, "", c, type);
        }
        public static Node ternary(Node cond, Node thenBranch, Node elseBranch) {
            List<Node> c = new ArrayList<>();
            c.add(cond); c.add(thenBranch); c.add(elseBranch);
            return new Node(NodeKind.BINARY, "?:", c, null);
        }

        @Override public String toString() {
            return kind + "(" + value + ")" + (children != null ? children : "");
        }
    }

    private final String src;
    private int pos;

    public ExpressionParser(String source) { this.src = source; this.pos = 0; }

    public Node parse() {
        skipWs();
        Node n = parseTernary();
        skipWs();
        if (pos < src.length()) {
            throw new RuntimeException("Unexpected character at " + pos + ": '" + src.charAt(pos) + "'");
        }
        return n;
    }

    private Node parseTernary() {
        Node cond = parseLogicalOr();
        skipWs();
        // Elvis operator: cond ?: defaultValue （仅当 ? 后面紧跟 :）
        if (peek() == '?' && pos + 1 < src.length() && src.charAt(pos + 1) == ':') {
            pos += 2;
            Node def = parseTernary();
            List<Node> c = new ArrayList<>();
            c.add(cond); c.add(def);
            return new Node(NodeKind.BINARY, "?:", c, null);
        }
        if (peek() == '?') {
            pos++;
            Node t = parseTernary();
            skipWs();
            expect(':');
            Node f = parseTernary();
            return Node.ternary(cond, t, f);
        }
        return cond;
    }

    private Node parseLogicalOr() {
        Node l = parseLogicalAnd();
        while (true) {
            skipWs();
            if (pos + 1 < src.length() && src.charAt(pos) == '|' && src.charAt(pos + 1) == '|') {
                pos += 2;
                Node r = parseLogicalAnd();
                l = Node.binary("||", l, r);
            } else break;
        }
        return l;
    }

    private Node parseLogicalAnd() {
        Node l = parseEquality();
        while (true) {
            skipWs();
            if (pos + 1 < src.length() && src.charAt(pos) == '&' && src.charAt(pos + 1) == '&') {
                pos += 2;
                Node r = parseEquality();
                l = Node.binary("&&", l, r);
            } else break;
        }
        return l;
    }

    private Node parseEquality() {
        Node l = parseComparison();
        while (true) {
            skipWs();
            if (pos + 1 < src.length()
                    && (src.charAt(pos) == '=' || src.charAt(pos) == '!')
                    && src.charAt(pos + 1) == '=') {
                String op = src.substring(pos, pos + 2);
                pos += 2;
                Node r = parseComparison();
                l = Node.binary(op, l, r);
            } else break;
        }
        return l;
    }

    private Node parseComparison() {
        Node l = parseAdditive();
        while (true) {
            skipWs();
            if (pos + 1 < src.length()
                    && (src.charAt(pos) == '<' || src.charAt(pos) == '>')
                    && src.charAt(pos + 1) == '=') {
                String op = src.substring(pos, pos + 2);
                pos += 2;
                Node r = parseAdditive();
                l = Node.binary(op, l, r);
            } else if (pos < src.length() && (src.charAt(pos) == '<' || src.charAt(pos) == '>')) {
                String op = String.valueOf(src.charAt(pos));
                pos++;
                Node r = parseAdditive();
                l = Node.binary(op, l, r);
            } else break;
        }
        return l;
    }

    private Node parseAdditive() {
        Node l = parseMultiplicative();
        while (true) {
            skipWs();
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                String op = String.valueOf(src.charAt(pos));
                pos++;
                Node r = parseMultiplicative();
                l = Node.binary(op, l, r);
            } else break;
        }
        return l;
    }

    private Node parseMultiplicative() {
        Node l = parseUnary();
        while (true) {
            skipWs();
            if (pos < src.length()
                    && (src.charAt(pos) == '*' || src.charAt(pos) == '/' || src.charAt(pos) == '%')) {
                String op = String.valueOf(src.charAt(pos));
                pos++;
                Node r = parseUnary();
                l = Node.binary(op, l, r);
            } else break;
        }
        return l;
    }

    private Node parseUnary() {
        skipWs();
        if (pos < src.length() && (src.charAt(pos) == '!' || src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
            String op = String.valueOf(src.charAt(pos));
            pos++;
            return Node.unary(op, parseCast());
        }
        return parseCast();
    }

    private Node parseCast() {
        skipWs();
        if (peek() == '(') {
            int save = pos;
            pos++;
            skipWs();
            String type = tryParseTypeName();
            if (type != null) {
                skipWs();
                if (peek() == ')') {
                    pos++;
                    skipWs();
                    if (peek() == '(' || isAlpha(peek())) {
                        // it was indeed a cast
                        return Node.cast(parseCast(), type);
                    }
                }
            }
            pos = save; // not a cast, backtrack
        }
        return parsePostfix();
    }

    private String tryParseTypeName() {
        int start = pos;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (isAlphaNumeric(c) || c == '.' || c == '_' || c == '$') {
                sb.append(c);
                pos++;
            } else break;
        }
        String s = sb.toString();
        if (s.isEmpty()) return null;
        // must contain at least one uppercase or be a primitive
        boolean isPrimitive = s.equals("int") || s.equals("long") || s.equals("short")
                || s.equals("byte") || s.equals("float") || s.equals("double")
                || s.equals("boolean") || s.equals("char");
        if (!isPrimitive && !Character.isUpperCase(s.charAt(0))) {
            pos = start;
            return null;
        }
        return s;
    }

    private Node parsePostfix() {
        Node base = parsePrimary();
        while (true) {
            skipWs();
            char c = peek();
            if (c == '.') {
                pos++;
                String name = readIdent();
                base = Node.member(base, name);
            } else if (c == '[') {
                pos++;
                Node idx = parseTernary();
                skipWs();
                expect(']');
                base = Node.index(base, idx);
            } else if (c == '(') {
                pos++;
                List<Node> args = parseArgList();
                base = Node.call(base, args);
            } else break;
        }
        return base;
    }

    private List<Node> parseArgList() {
        List<Node> out = new ArrayList<>();
        skipWs();
        if (peek() == ')') { pos++; return out; }
        out.add(parseTernary());
        while (true) {
            skipWs();
            if (peek() == ',') { pos++; out.add(parseTernary()); }
            else if (peek() == ')') { pos++; return out; }
            else break;
        }
        return out;
    }

    private Node parsePrimary() {
        skipWs();
        char c = peek();
        if (c == '(') {
            pos++;
            Node inner = parseTernary();
            skipWs();
            expect(')');
            return inner;
        }
        if (c == '"' || c == '\'') {
            return parseString();
        }
        if (Character.isDigit(c) || c == '.') {
            return parseNumber();
        }
        if (isAlpha(c) || c == '_' || c == '$') {
            String name = readIdent();
            if (name.equals("true") || name.equals("false")) return Node.ident(name);
            if (name.equals("null")) return Node.ident("null");
            return Node.ident(name);
        }
        throw new RuntimeException("Unexpected character at " + pos + ": '" + c + "'");
    }

    private Node parseNumber() {
        int start = pos;
        if (peek() == '.') { pos++; }
        else {
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            if (peek() == '.') pos++;
        }
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        // suffix
        if (pos < src.length() && (src.charAt(pos) == 'L' || src.charAt(pos) == 'l'
                || src.charAt(pos) == 'f' || src.charAt(pos) == 'F'
                || src.charAt(pos) == 'd' || src.charAt(pos) == 'D')) {
            pos++;
        }
        return Node.number(src.substring(start, pos));
    }

    private Node parseString() {
        char quote = src.charAt(pos);
        pos++;
        int start = pos;
        while (pos < src.length() && src.charAt(pos) != quote) pos++;
        String s = src.substring(start, pos);
        if (pos < src.length()) pos++; // closing quote
        return Node.string(s);
    }

    private String readIdent() {
        int start = pos;
        while (pos < src.length() && isAlphaNumeric(src.charAt(pos)) || pos < src.length() && src.charAt(pos) == '_') {
            pos++;
        }
        return src.substring(start, pos);
    }

    private char peek() { return pos < src.length() ? src.charAt(pos) : '\0'; }

    private void expect(char c) {
        if (peek() != c) throw new RuntimeException("Expected '" + c + "' at " + pos);
        pos++;
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private static boolean isAlpha(char c) { return Character.isLetter(c) || c == '_' || c == '$'; }
    private static boolean isAlphaNumeric(char c) { return Character.isLetterOrDigit(c) || c == '_' || c == '$'; }
}
