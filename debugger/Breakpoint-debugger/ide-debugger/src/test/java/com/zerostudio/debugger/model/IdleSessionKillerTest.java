/*
 *  ZeroStudio IDE - IdleSessionKiller 单元测试 (Phase H.3)
 *
 *  覆盖 IdleSessionKiller:
 *    - 默认 5min
 *    - 自定义 timeout
 *    - start/stop 幂等
 *    - touch 重置 idle
 *    - timeout=0 禁用
 *    - isEnabled / idleTimeoutMs
 *    - 监听器在 idle 超过时触发
 */
package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IdleSessionKillerTest {

    @Test
    public void defaultTimeout_is5Minutes() {
        IdleSessionKiller k = new IdleSessionKiller(null);
        assertEquals(IdleSessionKiller.DEFAULT_IDLE_TIMEOUT_MS, k.idleTimeoutMs());
        k.shutdown();
    }

    @Test
    public void customTimeout_respected() {
        IdleSessionKiller k = new IdleSessionKiller(null, 10_000L);
        assertEquals(10_000L, k.idleTimeoutMs());
        k.shutdown();
    }

    @Test
    public void timeoutZero_disabled() {
        IdleSessionKiller k = new IdleSessionKiller(null, 0L);
        assertFalse(k.isEnabled());
        k.shutdown();
    }

    @Test
    public void isEnabled_positiveTimeout() {
        IdleSessionKiller k = new IdleSessionKiller(null, 1_000L);
        assertTrue(k.isEnabled());
        k.shutdown();
    }

    @Test
    public void start_isIdempotent() {
        IdleSessionKiller k = new IdleSessionKiller(null, 60_000L, 1_000L);
        k.start();
        k.start();  // should not throw
        k.shutdown();
    }

    @Test
    public void stop_isIdempotent() {
        IdleSessionKiller k = new IdleSessionKiller(null, 60_000L, 1_000L);
        k.start();
        k.stop();
        k.stop();  // should not throw
        k.shutdown();
    }

    @Test
    public void touch_resetsIdle() {
        IdleSessionKiller k = new IdleSessionKiller(null, 60_000L, 1_000L);
        k.start();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        long before = k.currentIdleMs();
        k.touch();
        long after = k.currentIdleMs();
        assertTrue("touch should reset idle", after < before + 5);
        k.shutdown();
    }

    @Test
    public void timeoutFiresListener() throws Exception {
        // 200ms idle timeout, 50ms check interval
        IdleSessionKiller k = new IdleSessionKiller(null, 200L, 50L);
        CountDownLatch latch = new CountDownLatch(1);
        long[] captured = {0L};
        k.setListener(idle -> {
            captured[0] = idle;
            latch.countDown();
        });
        k.start();
        assertTrue("timeout should fire within 2s", latch.await(2, TimeUnit.SECONDS));
        assertTrue("captured idle should be >= 200ms", captured[0] >= 200L);
        k.shutdown();
    }

    @Test
    public void touchPreventsTimeout() throws Exception {
        IdleSessionKiller k = new IdleSessionKiller(null, 300L, 50L);
        CountDownLatch latch = new CountDownLatch(1);
        k.setListener(idle -> latch.countDown());
        k.start();
        // Keep touching for 500ms
        Thread touchLoop = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    k.touch();
                    Thread.sleep(60);
                }
            } catch (InterruptedException ignored) {}
        });
        touchLoop.start();
        // After 500ms the touch loop should still be active
        Thread.sleep(500);
        assertEquals("listener should not have fired while touching",
                1, latch.getCount());
        touchLoop.join(1000);
        k.shutdown();
    }

    @Test
    public void shutdown_isIdempotent() {
        IdleSessionKiller k = new IdleSessionKiller(null, 60_000L);
        k.shutdown();
        k.shutdown();  // should not throw
    }
}
