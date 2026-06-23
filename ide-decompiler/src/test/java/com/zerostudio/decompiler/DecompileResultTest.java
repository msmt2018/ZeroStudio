package com.zerostudio.decompiler;

import com.zerostudio.decompiler.api.DecompileResult;
import org.junit.Test;
import static org.junit.Assert.*;

public class DecompileResultTest {
    @Test
    public void reverseByOffsetBasic() {
        DecompileResult r = DecompileResult.ok("X", "class X {}",
                java.util.Map.of(1, 0L, 2, 5L, 3, 10L));
        var reversed = r.reverseByOffset();
        assertEquals(java.util.Optional.of(1), java.util.Optional.ofNullable(reversed.get(0L)));
        assertEquals(java.util.Optional.of(3), java.util.Optional.ofNullable(reversed.get(10L)));
    }

    @Test
    public void sourceLineForOffset() {
        DecompileResult r = DecompileResult.ok("X", "",
                java.util.Map.of(1, 0L, 5, 20L, 10, 50L));
        assertEquals(1, r.sourceLineForOffset(0L));
        assertEquals(1, r.sourceLineForOffset(15L));
        assertEquals(5, r.sourceLineForOffset(20L));
        assertEquals(10, r.sourceLineForOffset(50L));
        assertEquals(10, r.sourceLineForOffset(100L));
    }

    @Test
    public void isOk() {
        assertTrue(DecompileResult.ok("X", "code", null).isOk());
        assertFalse(DecompileResult.fail("X", "err").isOk());
    }
}
