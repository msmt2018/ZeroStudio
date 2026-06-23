/*
 *  ZeroStudio IDE - EvalEngine.Parser 单元测试 (PR-7)
 *
 *  覆盖以下语法路径:
 *    - 标识符
 *    - this
 *    - 字段访问链 a.b / a.b.c
 *    - 方法调用 a.b() / a.b(c, d)
 *    - 字符串 / 整数 / 长整数 / 双精度 字面量
 *    - 分组 (expr)
 *    - 错误输入:空 / 尾部残留 / 意外字符 / 未闭合括号 / 未闭合字符串
 *
 *  所有测试都走 package-private 的 EvalEngine.parseExpressionStrict(),
 *  该方法不依赖 JDWP 客户端,纯粹测试解析器。
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.zerostudio.debugger.model.EvalEngine.Resolved;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EvalEngineTest {

    // ---------- 标识符 ----------

    @Test
    public void parseIdentifier() {
        Resolved r = EvalEngine.parseExpressionStrict("i");
        assertEquals(Resolved.Kind.LOCAL, r.kind);
        assertEquals("i", r.name);
        assertNull(r.receiver);
        assertNull(r.args);
    }

    @Test
    public void parseUnderscoreIdentifier() {
        Resolved r = EvalEngine.parseExpressionStrict("_count");
        assertEquals(Resolved.Kind.LOCAL, r.kind);
        assertEquals("_count", r.name);
    }

    @Test
    public void parseUnicodeIdentifier() {
        // Java 标识符允许 $ 字符。
        Resolved r = EvalEngine.parseExpressionStrict("$view");
        assertEquals(Resolved.Kind.LOCAL, r.kind);
        assertEquals("$view", r.name);
    }

    @Test
    public void parseThis() {
        Resolved r = EvalEngine.parseExpressionStrict("this");
        assertEquals(Resolved.Kind.THIS, r.kind);
        assertEquals("this", r.name);
    }

    // ---------- 字段访问 ----------

    @Test
    public void parseField() {
        Resolved r = EvalEngine.parseExpressionStrict("user.name");
        assertEquals(Resolved.Kind.FIELD, r.kind);
        assertEquals("name", r.name);
        assertNotNull(r.receiver);
        assertEquals(Resolved.Kind.LOCAL, r.receiver.kind);
        assertEquals("user", r.receiver.name);
    }

    @Test
    public void parseFieldChain() {
        Resolved r = EvalEngine.parseExpressionStrict("a.b.c.d");
        // 最外层是 d
        assertEquals(Resolved.Kind.FIELD, r.kind);
        assertEquals("d", r.name);
        // 上一级是 c
        assertEquals(Resolved.Kind.FIELD, r.receiver.kind);
        assertEquals("c", r.receiver.name);
        // 再上一级是 b
        assertEquals(Resolved.Kind.FIELD, r.receiver.receiver.kind);
        assertEquals("b", r.receiver.receiver.name);
        // 根是 a
        assertEquals(Resolved.Kind.LOCAL, r.receiver.receiver.receiver.kind);
        assertEquals("a", r.receiver.receiver.receiver.name);
    }

    @Test
    public void parseFieldOnThis() {
        Resolved r = EvalEngine.parseExpressionStrict("this.value");
        assertEquals(Resolved.Kind.FIELD, r.kind);
        assertEquals("value", r.name);
        assertEquals(Resolved.Kind.THIS, r.receiver.kind);
    }

    // ---------- 方法调用 ----------

    @Test
    public void parseMethodNoArgs() {
        Resolved r = EvalEngine.parseExpressionStrict("user.getName()");
        assertEquals(Resolved.Kind.METHOD, r.kind);
        assertEquals("getName", r.name);
        assertEquals(Resolved.Kind.LOCAL, r.receiver.kind);
        assertEquals("user", r.receiver.name);
        assertNotNull(r.args);
        assertEquals(0, r.args.size());
    }

    @Test
    public void parseMethodOneArg() {
        Resolved r = EvalEngine.parseExpressionStrict("foo.bar(42)");
        assertEquals(Resolved.Kind.METHOD, r.kind);
        assertEquals("bar", r.name);
        assertNotNull(r.args);
        assertEquals(1, r.args.size());
        assertEquals(Resolved.Kind.LITERAL_LONG, r.args.get(0).kind);
    }

    @Test
    public void parseMethodMultipleArgs() {
        Resolved r = EvalEngine.parseExpressionStrict("a.b(\"x\", 1, 2L)");
        assertEquals(Resolved.Kind.METHOD, r.kind);
        assertEquals(3, r.args.size());
        assertEquals(Resolved.Kind.LITERAL_STRING, r.args.get(0).kind);
        assertEquals(Resolved.Kind.LITERAL_LONG, r.args.get(1).kind);
        assertEquals(Resolved.Kind.LITERAL_LONG, r.args.get(2).kind);
        assertEquals(2L, r.args.get(2).literalLong);
    }

    @Test
    public void parseChainedMethod() {
        // a.b().c().d
        Resolved r = EvalEngine.parseExpressionStrict("a.b().c().d");
        assertEquals(Resolved.Kind.FIELD, r.kind);
        assertEquals("d", r.name);
        // 上一级是 c()
        Resolved prev = r.receiver;
        assertEquals(Resolved.Kind.METHOD, prev.kind);
        assertEquals("c", prev.name);
        // 再上一级是 b()
        prev = prev.receiver;
        assertEquals(Resolved.Kind.METHOD, prev.kind);
        assertEquals("b", prev.name);
        // 根是 a
        prev = prev.receiver;
        assertEquals(Resolved.Kind.LOCAL, prev.kind);
        assertEquals("a", prev.name);
    }

    // ---------- 字面量 ----------

    @Test
    public void parseStringLiteral() {
        Resolved r = EvalEngine.parseExpressionStrict("\"hello\"");
        assertEquals(Resolved.Kind.LITERAL_STRING, r.kind);
        // String literal payload is stored in the name field (per the
        // existing resolveAndEval: r.name is used as the string payload).
        assertEquals("hello", r.name);
    }

    @Test
    public void parseStringLiteralEscapes() {
        Resolved r = EvalEngine.parseExpressionStrict("\"a\\\"b\\\\c\\n\"");
        assertEquals(Resolved.Kind.LITERAL_STRING, r.kind);
        assertEquals("a\"b\\c\n", r.name);
    }

    @Test
    public void parseStringLiteralEmpty() {
        Resolved r = EvalEngine.parseExpressionStrict("\"\"");
        assertEquals(Resolved.Kind.LITERAL_STRING, r.kind);
        assertEquals("", r.name);
    }

    @Test
    public void parseIntLiteral() {
        // PR-5 解析器把普通 int 字面量存成 LITERAL_LONG,以保持统一。
        Resolved r = EvalEngine.parseExpressionStrict("42");
        assertEquals(Resolved.Kind.LITERAL_LONG, r.kind);
        assertEquals(42L, r.literalLong);
    }

    @Test
    public void parseNegativeIntLiteral() {
        // minus sign is consumed by the parser; the literal value is -7.
        Resolved r = EvalEngine.parseExpressionStrict("-7");
        assertEquals(Resolved.Kind.LITERAL_LONG, r.kind);
        assertEquals(-7L, r.literalLong);
    }

    @Test
    public void parsePositiveIntLiteral() {
        Resolved r = EvalEngine.parseExpressionStrict("+5");
        assertEquals(Resolved.Kind.LITERAL_LONG, r.kind);
        assertEquals(5L, r.literalLong);
    }

    @Test
    public void parseLongLiteral() {
        Resolved r = EvalEngine.parseExpressionStrict("100L");
        assertEquals(Resolved.Kind.LITERAL_LONG, r.kind);
        assertEquals(100L, r.literalLong);
    }

    @Test
    public void parseDoubleLiteral() {
        Resolved r = EvalEngine.parseExpressionStrict("3.14");
        assertEquals(Resolved.Kind.LITERAL_DOUBLE, r.kind);
        assertEquals(3.14, r.literalDouble, 0.0001);
    }

    @Test
    public void parseDoubleWithLeadingZero() {
        Resolved r = EvalEngine.parseExpressionStrict("0.5");
        assertEquals(Resolved.Kind.LITERAL_DOUBLE, r.kind);
        assertEquals(0.5, r.literalDouble, 0.0001);
    }

    // ---------- 分组 ----------

    @Test
    public void parseParenthesizedIdentifier() {
        Resolved r = EvalEngine.parseExpressionStrict("(i)");
        // 单个 identifier,括号被吞掉,等价于 i
        assertEquals(Resolved.Kind.LOCAL, r.kind);
        assertEquals("i", r.name);
    }

    @Test
    public void parseParenthesizedString() {
        Resolved r = EvalEngine.parseExpressionStrict("(\"x\")");
        assertEquals(Resolved.Kind.LITERAL_STRING, r.kind);
        assertEquals("x", r.name);
    }

    @Test
    public void parseNestedParens() {
        Resolved r = EvalEngine.parseExpressionStrict("((42))");
        assertEquals(Resolved.Kind.LITERAL_LONG, r.kind);
        assertEquals(42L, r.literalLong);
    }

    // ---------- 错误路径 ----------

    @Test
    public void parseEmptyFails() {
        try {
            EvalEngine.parseExpressionStrict("");
            fail("expected RuntimeException for empty input");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("unexpected"));
        }
    }

    @Test
    public void parseTrailingInputFails() {
        // Phase A1: `i + 1` is a valid binary expression, not trailing
        // input. To still exercise the trailing-input guard we tack a
        // bogus identifier on after a valid expression: `i + 1 j` parses
        // `i + 1` and then sees ` j` as leftover.
        try {
            EvalEngine.parseExpressionStrict("i + 1 j");
            fail("expected IllegalArgumentException for trailing input");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().startsWith("trailing input"));
        }
    }

    @Test
    public void parseTrailingIdentifierFails() {
        try {
            EvalEngine.parseExpressionStrict("i j");
            fail("expected IllegalArgumentException for trailing identifier");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void parseUnexpectedCharFails() {
        try {
            EvalEngine.parseExpressionStrict("@");
            fail("expected RuntimeException for unexpected char");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().startsWith("unexpected char"));
        }
    }

    @Test
    public void parseUnclosedParenFails() {
        try {
            EvalEngine.parseExpressionStrict("(i");
            fail("expected RuntimeException for unclosed paren");
        } catch (RuntimeException expected) {
            // ok
        }
    }

    @Test
    public void parseDotWithoutIdentFails() {
        try {
            EvalEngine.parseExpressionStrict("a.");
            fail("expected RuntimeException for trailing dot");
        } catch (RuntimeException expected) {
            // 期望的是 "expected identifier" / RuntimeException
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void parseUnclosedStringFails() {
        try {
            EvalEngine.parseExpressionStrict("\"unterminated");
            fail("expected RuntimeException for unterminated string");
        } catch (RuntimeException expected) {
            // ok
        }
    }

    @Test
    public void parseBadNumberFails() {
        // parseNumberLiteral 在 isDouble == false 时会尝试 Long.parseLong
        // 数字尾随 '.' 但后面没数字 -> 进入 isDouble 分支,OK
        // 这里给一个真正会让 parseLong 失败的形式: "9999999999999999999999"
        // 超 long 范围会抛 NumberFormatException,被包成 RuntimeException
        try {
            EvalEngine.parseExpressionStrict("99999999999999999999999");
            fail("expected RuntimeException for overflow literal");
        } catch (RuntimeException expected) {
            // ok
        }
    }

    // ---------- 空白处理 ----------

    @Test
    public void parseSkipsLeadingWhitespace() {
        Resolved r = EvalEngine.parseExpressionStrict("    i");
        assertEquals(Resolved.Kind.LOCAL, r.kind);
        assertEquals("i", r.name);
    }

    @Test
    public void parseSkipsInterTokenWhitespace() {
        Resolved r = EvalEngine.parseExpressionStrict("user   .   name");
        assertEquals(Resolved.Kind.FIELD, r.kind);
        assertEquals("name", r.name);
    }

    @Test
    public void parseSkipsWhitespaceInMethodCall() {
        Resolved r = EvalEngine.parseExpressionStrict("foo . bar ( a , b )");
        assertEquals(Resolved.Kind.METHOD, r.kind);
        assertEquals("bar", r.name);
        assertEquals(2, r.args.size());
    }

    // ---------- Phase A1: 算术运算符 ----------

    @Test
    public void parseAdd_twoLiterals() {
        Resolved r = EvalEngine.parseExpressionStrict("1 + 2");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("+", r.name);
        assertNotNull(r.left);
        assertNotNull(r.right);
        assertEquals(Resolved.Kind.LITERAL_LONG, r.left.kind);
        assertEquals(1L, r.left.literalLong);
        assertEquals(Resolved.Kind.LITERAL_LONG, r.right.kind);
        assertEquals(2L, r.right.literalLong);
    }

    @Test
    public void parseSubtract_twoLiterals() {
        Resolved r = EvalEngine.parseExpressionStrict("10 - 3");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("-", r.name);
        assertEquals(10L, r.left.literalLong);
        assertEquals(3L, r.right.literalLong);
    }

    @Test
    public void parseMultiply_twoLiterals() {
        Resolved r = EvalEngine.parseExpressionStrict("4 * 6");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("*", r.name);
    }

    @Test
    public void parseDivide_twoLiterals() {
        Resolved r = EvalEngine.parseExpressionStrict("10 / 2");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("/", r.name);
    }

    @Test
    public void parseModulo_twoLiterals() {
        Resolved r = EvalEngine.parseExpressionStrict("7 % 3");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("%", r.name);
    }

    @Test
    public void parseAdd_localAndLiteral() {
        Resolved r = EvalEngine.parseExpressionStrict("count + 1");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("+", r.name);
        assertEquals(Resolved.Kind.LOCAL, r.left.kind);
        assertEquals("count", r.left.name);
        assertEquals(Resolved.Kind.LITERAL_LONG, r.right.kind);
        assertEquals(1L, r.right.literalLong);
    }

    @Test
    public void parsePrecedence_multiplyBeforeAdd() {
        // 1 + 2 * 3 == 1 + (2 * 3) -> top is `+`, right is `2 * 3`.
        Resolved r = EvalEngine.parseExpressionStrict("1 + 2 * 3");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("+", r.name);
        assertEquals(Resolved.Kind.LITERAL_LONG, r.left.kind);
        assertEquals(1L, r.left.literalLong);
        assertEquals(Resolved.Kind.BINARY, r.right.kind);
        assertEquals("*", r.right.name);
        assertEquals(2L, r.right.left.literalLong);
        assertEquals(3L, r.right.right.literalLong);
    }

    @Test
    public void parsePrecedence_parensOverride() {
        // (1 + 2) * 3 -> top is `*`, left is `1 + 2`.
        Resolved r = EvalEngine.parseExpressionStrict("(1 + 2) * 3");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("*", r.name);
        assertEquals(Resolved.Kind.BINARY, r.left.kind);
        assertEquals("+", r.left.name);
        assertEquals(1L, r.left.left.literalLong);
        assertEquals(2L, r.left.right.literalLong);
        assertEquals(3L, r.right.literalLong);
    }

    @Test
    public void parseLeftAssociative_subtract() {
        // a - b - c is parsed as (a - b) - c.
        Resolved r = EvalEngine.parseExpressionStrict("a - b - c");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("-", r.name);
        assertEquals(Resolved.Kind.BINARY, r.left.kind);
        assertEquals("-", r.left.name);
        assertEquals("a", r.left.left.name);
        assertEquals("b", r.left.right.name);
        assertEquals("c", r.right.name);
    }

    @Test
    public void parseDoubleArithmetic() {
        Resolved r = EvalEngine.parseExpressionStrict("1.5 + 2.5");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("+", r.name);
        assertEquals(Resolved.Kind.LITERAL_DOUBLE, r.left.kind);
        assertEquals(1.5, r.left.literalDouble, 0.0001);
        assertEquals(Resolved.Kind.LITERAL_DOUBLE, r.right.kind);
        assertEquals(2.5, r.right.literalDouble, 0.0001);
    }

    @Test
    public void parseBinaryFollowedByChainedField() {
        // `a + b.field` is still additive on top with a FIELD on the right.
        Resolved r = EvalEngine.parseExpressionStrict("a + b.field");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("+", r.name);
        assertEquals(Resolved.Kind.LOCAL, r.left.kind);
        assertEquals("a", r.left.name);
        assertEquals(Resolved.Kind.FIELD, r.right.kind);
        assertEquals("field", r.right.name);
        assertEquals("b", r.right.receiver.name);
    }

    @Test
    public void parseBinaryInsideMethodArg() {
        // foo.add(1, 2 + 3) -> second arg is a BINARY.
        Resolved r = EvalEngine.parseExpressionStrict("foo.add(1, 2 + 3)");
        assertEquals(Resolved.Kind.METHOD, r.kind);
        assertEquals(2, r.args.size());
        assertEquals(Resolved.Kind.LITERAL_LONG, r.args.get(0).kind);
        assertEquals(Resolved.Kind.BINARY, r.args.get(1).kind);
        assertEquals("+", r.args.get(1).name);
    }

    @Test
    public void parseTrailingOperatorFails() {
        // `1 +` is missing a right operand; parsePrimary sees EOF and
        // throws "unexpected char '\0'".
        try {
            EvalEngine.parseExpressionStrict("1 +");
            fail("expected RuntimeException for trailing operator");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().startsWith("unexpected char"));
        }
    }

    @Test
    public void parseSkipsWhitespaceAroundOperators() {
        Resolved r = EvalEngine.parseExpressionStrict("  1   +   2  ");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("+", r.name);
        assertEquals(1L, r.left.literalLong);
        assertEquals(2L, r.right.literalLong);
    }

    // ---------- Phase A2: 比较与逻辑运算符 ----------

    @Test
    public void parseEquality_doubleEquals() {
        Resolved r = EvalEngine.parseExpressionStrict("a == b");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("==", r.name);
        assertEquals(Resolved.Kind.LOCAL, r.left.kind);
        assertEquals("a", r.left.name);
        assertEquals(Resolved.Kind.LOCAL, r.right.kind);
        assertEquals("b", r.right.name);
    }

    @Test
    public void parseEquality_notEquals() {
        Resolved r = EvalEngine.parseExpressionStrict("a != b");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("!=", r.name);
    }

    @Test
    public void parseRelational_lessThan() {
        Resolved r = EvalEngine.parseExpressionStrict("a < b");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("<", r.name);
    }

    @Test
    public void parseRelational_greaterThan() {
        Resolved r = EvalEngine.parseExpressionStrict("a > b");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals(">", r.name);
    }

    @Test
    public void parseRelational_lessEquals() {
        Resolved r = EvalEngine.parseExpressionStrict("a <= b");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("<=", r.name);
    }

    @Test
    public void parseRelational_greaterEquals() {
        Resolved r = EvalEngine.parseExpressionStrict("a >= b");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals(">=", r.name);
    }

    @Test
    public void parseLogicalAnd() {
        Resolved r = EvalEngine.parseExpressionStrict("a && b");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("&&", r.name);
    }

    @Test
    public void parseLogicalOr() {
        Resolved r = EvalEngine.parseExpressionStrict("a || b");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("||", r.name);
    }

    @Test
    public void parsePrecedence_andOverOr() {
        // a || b && c == a || (b && c)
        Resolved r = EvalEngine.parseExpressionStrict("a || b && c");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("||", r.name);
        assertEquals("a", r.left.name);
        assertEquals(Resolved.Kind.BINARY, r.right.kind);
        assertEquals("&&", r.right.name);
        assertEquals("b", r.right.left.name);
        assertEquals("c", r.right.right.name);
    }

    @Test
    public void parsePrecedence_equalityOverAnd() {
        // a == b && c == d == (a == b) && (c == d)
        Resolved r = EvalEngine.parseExpressionStrict("a == b && c == d");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("&&", r.name);
        assertEquals(Resolved.Kind.BINARY, r.left.kind);
        assertEquals("==", r.left.name);
        assertEquals(Resolved.Kind.BINARY, r.right.kind);
        assertEquals("==", r.right.name);
    }

    @Test
    public void parsePrecedence_relationalOverEquality() {
        // a < b == c < d == (a < b) == (c < d)
        Resolved r = EvalEngine.parseExpressionStrict("a < b == c < d");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("==", r.name);
        assertEquals(Resolved.Kind.BINARY, r.left.kind);
        assertEquals("<", r.left.name);
        assertEquals(Resolved.Kind.BINARY, r.right.kind);
        assertEquals("<", r.right.name);
    }

    @Test
    public void parsePrecedence_additiveOverRelational() {
        // a + b < c == (a + b) < c
        Resolved r = EvalEngine.parseExpressionStrict("a + b < c");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("<", r.name);
        assertEquals(Resolved.Kind.BINARY, r.left.kind);
        assertEquals("+", r.left.name);
        assertEquals("c", r.right.name);
    }

    @Test
    public void parseChainedAndWithComparison() {
        // a > 0 && b > 0
        Resolved r = EvalEngine.parseExpressionStrict("a > 0 && b > 0");
        assertEquals(Resolved.Kind.BINARY, r.kind);
        assertEquals("&&", r.name);
        assertEquals(Resolved.Kind.BINARY, r.left.kind);
        assertEquals(">", r.left.name);
        assertEquals("a", r.left.left.name);
        assertEquals(Resolved.Kind.BINARY, r.right.kind);
        assertEquals(">", r.right.name);
        assertEquals("b", r.right.left.name);
    }

    @Test
    public void parseNotEqualsDoesNotConsumeSingleEquals() {
        // a = b (single =) is not a valid expression. Make sure the
        // parser does NOT silently accept a single `=` as `==`.
        try {
            EvalEngine.parseExpressionStrict("a = b");
            fail("expected RuntimeException for stray '='");
        } catch (RuntimeException expected) {
            // ok
        }
    }

    @Test
    public void parseRelationalWithCompoundOperator() {
        // Make sure <= and >= are NOT split into < + = or > + =.
        Resolved r = EvalEngine.parseExpressionStrict("a <= b");
        assertEquals("<=", r.name);
        r = EvalEngine.parseExpressionStrict("a >= b");
        assertEquals(">=", r.name);
    }
}
