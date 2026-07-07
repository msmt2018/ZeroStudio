/*
 *  ide-debugger - DebugSessionHeartbeat 单元测试
 *
 *  PR-D5: 校验心跳状态机 (missCount / lastSuccessAt / tick)。
 *  不直接跟 JdwpClient 通讯,只验证状态转换.
 */

package com.zerostudio.debugger.jdwp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.api.Debugger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class DebugSessionHeartbeatTest {

    private Debugger debugger;
    private JdwpClient client;
    private DebugSessionHeartbeat heartbeat;

    @Before
    public void setUp() {
        debugger = new Debugger();
        client = debugger.client();
        // Replace the client with a stub that does nothing
        // We can't easily mock the client without subclassing, so we just
        // verify the API contract on a not-yet-connected client.
    }

    @After
    public void tearDown() {
        if (heartbeat != null) heartbeat.stop();
    }

    @Test
    public void ping_returnsFalseWhenClientNotConnected() {
        heartbeat = new DebugSessionHeartbeat(debugger, 1_000L, 500L, 3, null);
        // Client is created but not connected -> ping() should return false.
        assertFalse(heartbeat.ping());
        assertEquals(0, heartbeat.currentMissCount());
    }

    @Test
    public void defaults_areSane() {
        assertEquals(30_000L, DebugSessionHeartbeat.DEFAULT_INTERVAL_MS);
        assertEquals(3_000L, DebugSessionHeartbeat.DEFAULT_PING_TIMEOUT_MS);
        assertEquals(3, DebugSessionHeartbeat.DEFAULT_MISS_THRESHOLD);
    }

    @Test
    public void startAndStop_doesNotThrow() {
        heartbeat = new DebugSessionHeartbeat(debugger);
        heartbeat.start();
        heartbeat.stop();
        // No assertion - we're just verifying lifecycle is well-formed.
    }

    @Test
    public void missCount_incrementsOnConsecutiveFailures() {
        // We craft a "fake" heartbeat by stubbing the ping() path: we
        // use a single-shot executor that runs tick() manually 4 times.
        AtomicInteger fakePingResult = new AtomicInteger(0);
        heartbeat = new DebugSessionHeartbeat(debugger) {
            @Override
            public boolean ping() {
                return fakePingResult.get() == 1;
            }
        };
        fakePingResult.set(0); // ping fails
        for (int i = 0; i < 4; i++) {
            try {
                java.lang.reflect.Method m = DebugSessionHeartbeat.class
                        .getDeclaredMethod("tick");
                m.setAccessible(true);
                m.invoke(heartbeat);
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }
        // After 4 failed pings, missCount should be 4.
        assertEquals(4, heartbeat.currentMissCount());
    }
}
