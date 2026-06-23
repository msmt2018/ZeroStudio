/*
 *  ZeroStudio IDE - DebuggerExecutor 单元测试 (Phase H2)
 *
 *  覆盖 DebuggerExecutor:
 *    - execute() 运行 Runnable
 *    - submit() 返回 Future
 *    - shutdown() 终止
 *    - getActiveCount() 返回活跃线程数
 *    - 任务在后台线程运行
 */

package com.zerostudio.debugger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DebuggerExecutorTest {

    @Test
    public void execute_runsRunnable() throws Exception {
        DebuggerExecutor ex = new DebuggerExecutor();
        try {
            AtomicBoolean ran = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            ex.execute(() -> {
                ran.set(true);
                latch.countDown();
            });
            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertTrue(ran.get());
        } finally {
            ex.shutdown();
        }
    }

    @Test
    public void submit_returnsFutureWithCorrectValue() throws Exception {
        DebuggerExecutor ex = new DebuggerExecutor();
        try {
            var future = ex.submit(() -> {
                Thread.sleep(50);
                return 42;
            });
            assertEquals(42, future.get(5, TimeUnit.SECONDS).intValue());
        } finally {
            ex.shutdown();
        }
    }

    @Test
    public void execute_multipleTasks_allRun() throws Exception {
        DebuggerExecutor ex = new DebuggerExecutor();
        try {
            int taskCount = 10;
            AtomicInteger counter = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(taskCount);
            for (int i = 0; i < taskCount; i++) {
                ex.execute(() -> {
                    counter.incrementAndGet();
                    latch.countDown();
                });
            }
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(taskCount, counter.get());
        } finally {
            ex.shutdown();
        }
    }

    @Test
    public void shutdown_terminatesGracefully() throws Exception {
        DebuggerExecutor ex = new DebuggerExecutor();
        AtomicBoolean finished = new AtomicBoolean(false);
        ex.execute(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            finished.set(true);
        });
        ex.shutdown();
        // shutdown() waits up to 5 seconds
        assertTrue(ex.isShutdown());
        assertTrue(finished.get());
    }

    @Test
    public void isShutdown_trueAfterShutdown() throws Exception {
        DebuggerExecutor ex = new DebuggerExecutor();
        assertFalse(ex.isShutdown());
        ex.shutdown();
        assertTrue(ex.isShutdown());
    }

    @Test
    public void getActiveCount_returnsCorrectCount() throws Exception {
        DebuggerExecutor ex = new DebuggerExecutor();
        try {
            AtomicInteger counter = new AtomicInteger(0);
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch block = new CountDownLatch(1);
            // Submit 2 long-running tasks
            for (int i = 0; i < 2; i++) {
                ex.execute(() -> {
                    counter.incrementAndGet();
                    started.countDown();
                    try { block.await(); } catch (InterruptedException ignored) {}
                });
            }
            assertTrue(started.await(3, TimeUnit.SECONDS));
            // At least 2 should be active
            int active = ex.getActiveCount();
            assertTrue("Expected >= 2 active, got " + active, active >= 2);
            block.countDown();
        } finally {
            ex.shutdown();
        }
    }

    @Test
    public void executor_usesDaemonThreads() throws Exception {
        DebuggerExecutor ex = new DebuggerExecutor();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            ex.execute(() -> {
                Thread t = Thread.currentThread();
                assertTrue("Thread should be daemon", t.isDaemon());
                assertTrue("Thread name should start with DebuggerBg-",
                           t.getName().startsWith("DebuggerBg-"));
                latch.countDown();
            });
            assertTrue(latch.await(3, TimeUnit.SECONDS));
        } finally {
            ex.shutdown();
        }
    }
}
