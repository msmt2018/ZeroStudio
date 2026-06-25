/*
 *  ZeroStudio IDE - DebuggerWatchdog 单元测试 (Phase H.2)
 *
 *  覆盖 DebuggerWatchdog:
 *    - budgetMs() 默认 30s
 *    - begin() 返回不同的 handle
 *    - complete() 清除 inflight
 *    - onTimeout 触发 listener
 *    - shutdown 干净退出
 *    - listener 设置/清空
 */
package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DebuggerWatchdogTest {

    @Test
    public void defaultBudget_is30Seconds() {
        DebuggerWatchdog wd = new DebuggerWatchdog();
        assertEquals(DebuggerWatchdog.DEFAULT_BUDGET_MS, wd.budgetMs());
        wd.shutdown();
    }

    @Test
    public void customBudget_respected() {
        DebuggerWatchdog wd = new DebuggerWatchdog(1234L);
        assertEquals(1234L, wd.budgetMs());
        wd.shutdown();
    }

    @Test
    public void begin_returnsDistinctHandles() {
        DebuggerWatchdog wd = new DebuggerWatchdog(60_000L);
        long h1 = wd.begin("call1");
        long h2 = wd.begin("call2");
        assertNotEquals(h1, h2);
        wd.complete(h1);
        wd.complete(h2);
        wd.shutdown();
    }

    @Test
    public void complete_clearsInflight() {
        DebuggerWatchdog wd = new DebuggerWatchdog(60_000L);
        long h = wd.begin("call");
        assertEquals(1, wd.inflightCount());
        wd.complete(h);
        assertEquals(0, wd.inflightCount());
        wd.shutdown();
    }

    @Test
    public void timeout_firesListener() throws Exception {
        // Use a short budget so the test runs fast.
        DebuggerWatchdog wd = new DebuggerWatchdog(100L);
        CountDownLatch latch = new CountDownLatch(1);
        String[] captured = {null};
        wd.setListener((name, elapsed) -> {
            captured[0] = name;
            latch.countDown();
        });
        wd.begin("stuckCall");
        assertTrue("timeout should fire within 2s", latch.await(2, TimeUnit.SECONDS));
        assertEquals("stuckCall", captured[0]);
        wd.shutdown();
    }

    @Test
    public void complete_preventsTimeout() throws Exception {
        DebuggerWatchdog wd = new DebuggerWatchdog(100L);
        CountDownLatch latch = new CountDownLatch(1);
        wd.setListener((name, elapsed) -> latch.countDown());
        long h = wd.begin("call");
        wd.complete(h);
        assertEquals("complete should prevent timeout", 0, wd.inflightCount());
        // Wait a bit and confirm no timeout fired
        latch.await(500, TimeUnit.MILLISECONDS);
        assertEquals("listener should not have been called",
                1, latch.getCount());
        wd.shutdown();
    }

    @Test
    public void setListener_canBeCleared() {
        DebuggerWatchdog wd = new DebuggerWatchdog(60_000L);
        wd.setListener((name, elapsed) -> {});
        wd.setListener(null);
        // No exception thrown is sufficient
        wd.shutdown();
    }

    @Test
    public void shutdown_isIdempotent() {
        DebuggerWatchdog wd = new DebuggerWatchdog(60_000L);
        wd.shutdown();
        wd.shutdown();  // should not throw
    }

    @Test
    public void inflightCount_zeroInitially() {
        DebuggerWatchdog wd = new DebuggerWatchdog(60_000L);
        assertEquals(0, wd.inflightCount());
        wd.shutdown();
    }

    @Test
    public void complete_unknownHandle_isNoop() {
        DebuggerWatchdog wd = new DebuggerWatchdog(60_000L);
        wd.complete(999999L);  // unknown handle
        assertEquals(0, wd.inflightCount());
        wd.shutdown();
    }
}
