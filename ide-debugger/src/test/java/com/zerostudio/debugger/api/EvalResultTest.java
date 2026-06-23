/*
 *  ZeroStudio IDE - EvalResult 工厂方法单元测试 (PR-7)
 *
 *  覆盖所有工厂方法的字段填充:
 *    - of(Tag, sig, value)
 *    - object(id, sig)
 *    - string(id, value)
 *    - error(msg)
 *  以及 isError() 的语义。
 */

package com.zerostudio.debugger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.api.EvalResult.Tag;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EvalResultTest {

    @Test
    public void of_primitive() {
        EvalResult r = EvalResult.of(Tag.INT, "I", "42");
        assertEquals(Tag.INT, r.tag);
        assertEquals("I", r.typeSignature);
        assertEquals("42", r.displayValue);
        assertNull(r.error);
        assertEquals(0L, r.objectId);
        assertFalse(r.isError());
    }

    @Test
    public void of_string() {
        EvalResult r = EvalResult.of(Tag.STRING, "Ljava/lang/String;", "hello");
        assertEquals(Tag.STRING, r.tag);
        assertEquals("hello", r.displayValue);
        assertFalse(r.isError());
    }

    @Test
    public void object_hasObjectId() {
        EvalResult r = EvalResult.object(0xdeadbeefL, "Ljava/util/List;");
        assertEquals(Tag.OBJECT, r.tag);
        assertEquals(0xdeadbeefL, r.objectId);
        assertEquals("Ljava/util/List;", r.typeSignature);
        assertNull(r.displayValue);
        assertNull(r.error);
        assertFalse(r.isError());
    }

    @Test
    public void string_combinesObjectAndDisplay() {
        EvalResult r = EvalResult.string(0xcafebabeL, "world");
        assertEquals(Tag.STRING, r.tag);
        assertEquals(0xcafebabeL, r.objectId);
        assertEquals("world", r.displayValue);
        assertFalse(r.isError());
    }

    @Test
    public void error_setsErrorField() {
        EvalResult r = EvalResult.error("no such local: x");
        assertEquals(Tag.OBJECT, r.tag);
        assertNotNull(r.error);
        assertEquals("no such local: x", r.error);
        assertTrue(r.isError());
        // error 结果不应附带 objectId
        assertEquals(0L, r.objectId);
    }

    @Test
    public void isError_onlyWhenErrorNonNull() {
        assertFalse(EvalResult.of(Tag.INT, "I", "1").isError());
        assertFalse(EvalResult.object(1L, "Ljava/lang/Object;").isError());
        assertFalse(EvalResult.string(1L, "x").isError());
        assertTrue(EvalResult.error("x").isError());
    }
}
