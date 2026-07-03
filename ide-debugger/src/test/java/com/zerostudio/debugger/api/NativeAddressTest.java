/*
 *  ZeroStudio IDE - ide-debugger
 *  Phase 20: NativeAddress 单元测试
 */

package com.zerostudio.debugger.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeAddressTest {

    @Test
    public void toString_includesModuleOffsetFunction() {
        NativeAddress a = new NativeAddress("libfoo.so", 0x100000L, 0x123L, "malloc");
        String s = a.toString();
        assertTrue(s.contains("libfoo.so"));
        assertTrue(s.contains("malloc"));
        assertTrue(s.contains("0x123"));
    }

    @Test
    public void nullFunction_ok() {
        NativeAddress a = new NativeAddress("libbar.so", 0x200000L, 0x456L, null);
        assertNull(a.functionName);
        assertEquals(0x200000L, a.address);
    }
}
