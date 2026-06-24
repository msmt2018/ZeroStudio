/*
 *  ZeroStudio IDE - DebugSession 单元测试 (Phase F3)
 *
 *  覆盖:
 *    - 初始状态为 IDLE
 *    - 6 种 State 之间的转换
 *    - isRunning: RUNNING / STEPPING 为 true,其它 false
 *    - isSuspended: 仅 SUSPENDED 为 true
 *    - 状态可被并发读写 (volatile 语义验证)
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.model.DebugSession.State;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DebugSessionTest {

    private DebugSession session;

    @Before
    public void setUp() {
        session = new DebugSession();
    }

    @Test
    public void initialStateIsIdle() {
        assertEquals(State.IDLE, session.getState());
        assertFalse(session.isRunning());
        assertFalse(session.isSuspended());
    }

    @Test
    public void isRunningTrueForRunning() {
        session.setState(State.RUNNING);
        assertTrue(session.isRunning());
        assertFalse(session.isSuspended());
    }

    @Test
    public void isRunningTrueForStepping() {
        session.setState(State.STEPPING);
        assertTrue(session.isRunning());
        assertFalse(session.isSuspended());
    }

    @Test
    public void isRunningFalseForConnected() {
        session.setState(State.CONNECTED);
        assertFalse(session.isRunning());
    }

    @Test
    public void isRunningFalseForIdle() {
        assertFalse(session.isRunning());
    }

    @Test
    public void isSuspendedOnlyForSuspended() {
        for (State s : State.values()) {
            session.setState(s);
            assertEquals("state=" + s, s == State.SUSPENDED, session.isSuspended());
        }
    }

    @Test
    public void stateTransitions() {
        session.setState(State.CONNECTED);
        assertEquals(State.CONNECTED, session.getState());
        session.setState(State.RUNNING);
        assertEquals(State.RUNNING, session.getState());
        session.setState(State.SUSPENDED);
        assertEquals(State.SUSPENDED, session.getState());
        session.setState(State.STEPPING);
        assertEquals(State.STEPPING, session.getState());
        session.setState(State.DISCONNECTED);
        assertEquals(State.DISCONNECTED, session.getState());
    }

    @Test
    public void stateIsVolatileAcrossThreads() throws Exception {
        // 简单的并发: 1000 次写,1000 次读
        int iterations = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(2);

            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        session.setState(State.RUNNING);
                        session.setState(State.SUSPENDED);
                    }
                } catch (Throwable t) { failure.set(t); }
                done.countDown();
            });
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        State s = session.getState();
                        // 只读,不应抛错
                        if (s == null) {
                            failure.set(new AssertionError("state was null"));
                        }
                    }
                } catch (Throwable t) { failure.set(t); }
                done.countDown();
            });

            start.countDown();
            assertTrue("Timed out", done.await(5, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError(failure.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void stateEnumHas6Values() {
        // 防止有人在 enum 中意外增删
        assertEquals(6, State.values().length);
    }
}
