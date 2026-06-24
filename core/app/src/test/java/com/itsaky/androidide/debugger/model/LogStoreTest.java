/*
 *  ZeroStudio IDE - LogStore 单元测试 (PR-D8.1)
 *
 *  覆盖:
 *    - append / snapshot / size 基础操作
 *    - 容量上限 (DEFAULT_CAPACITY / 自定义)
 *    - clear
 *    - listener 派发(append / clear)
 *    - 多 listener 全部收到事件
 *    - listener 异常不影响其它 listener
 *
 *  使用 Robolectric 让 HandlerThread / Handler 真实执行。
 */

package com.itsaky.androidide.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = [33])
public class LogStoreTest {

    private LogStore store;

    @Before
    public void setUp() {
        store = new LogStore();
        store.setCapacity(100);  // 测例用小容量
    }

    @Test
    public void append_andSnapshot() {
        store.append("a");
        store.append("b");
        store.append("c");
        List<LogStore.Entry> snap = store.snapshot();
        assertEquals(3, snap.size());
        assertEquals("a", snap.get(0).text);
        assertEquals("b", snap.get(1).text);
        assertEquals("c", snap.get(2).text);
    }

    @Test
    public void size_tracksAppendCount() {
        assertEquals(0, store.size());
        store.append("a");
        assertEquals(1, store.size());
        store.append("b");
        assertEquals(2, store.size());
    }

    @Test
    public void append_withSourceInfo() {
        store.append("Main.java", 42, "value=10");
        List<LogStore.Entry> snap = store.snapshot();
        assertEquals(1, snap.size());
        LogStore.Entry e = snap.get(0);
        assertEquals("Main.java", e.sourceFile);
        assertEquals(42, e.line);
        assertEquals("value=10", e.text);
    }

    @Test
    public void append_capacity_evictsOldest() {
        store.setCapacity(3);
        for (int i = 0; i < 5; i++) store.append("e" + i);
        assertEquals(3, store.size());
        // 最早的被驱逐,保留 e2, e3, e4
        List<LogStore.Entry> snap = store.snapshot();
        assertEquals("e2", snap.get(0).text);
        assertEquals("e3", snap.get(1).text);
        assertEquals("e4", snap.get(2).text);
    }

    @Test
    public void setCapacity_truncatesIfLargerThanCurrent() {
        for (int i = 0; i < 10; i++) store.append("e" + i);
        assertEquals(10, store.size());
        store.setCapacity(3);
        assertEquals(3, store.size());
    }

    @Test
    public void setCapacity_ignoresInvalid() {
        for (int i = 0; i < 5; i++) store.append("e" + i);
        store.setCapacity(0);
        assertEquals(5, store.size());  // 不变
        store.setCapacity(-1);
        assertEquals(5, store.size());  // 不变
    }

    @Test
    public void clear_emptiesStore() {
        for (int i = 0; i < 5; i++) store.append("e" + i);
        store.clear();
        assertEquals(0, store.size());
        assertTrue(store.snapshot().isEmpty());
    }

    @Test
    public void listener_receivesAppend() throws Exception {
        List<LogStore.Entry> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        store.addListener(new LogStore.Listener() {
            @Override
            public void onLogAppended(LogStore.Entry entry) {
                received.add(entry);
                latch.countDown();
            }
            @Override
            public void onLogCleared() {}
        });
        store.append("a");
        store.append("b");
        ShadowLooper.idleMainLooper();
        assertTrue("listener 应在 2s 内收到 2 条", latch.await(2, TimeUnit.SECONDS));
        assertEquals(2, received.size());
    }

    @Test
    public void listener_receivesClear() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<Boolean> cleared = new ArrayList<>();
        store.addListener(new LogStore.Listener() {
            @Override
            public void onLogAppended(LogStore.Entry entry) {}
            @Override
            public void onLogCleared() {
                cleared.add(true);
                latch.countDown();
            }
        });
        store.append("a");
        store.clear();
        ShadowLooper.idleMainLooper();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, cleared.size());
    }

    @Test
    public void multipleListeners_allReceive() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        store.addListener(new LogStore.Listener() {
            @Override
            public void onLogAppended(LogStore.Entry entry) { latch.countDown(); }
            @Override
            public void onLogCleared() {}
        });
        store.addListener(new LogStore.Listener() {
            @Override
            public void onLogAppended(LogStore.Entry entry) { latch.countDown(); }
            @Override
            public void onLogCleared() {}
        });
        store.addListener(new LogStore.Listener() {
            @Override
            public void onLogAppended(LogStore.Entry entry) { latch.countDown(); }
            @Override
            public void onLogCleared() {}
        });
        store.append("a");
        ShadowLooper.idleMainLooper();
        assertTrue("3 个 listener 全部应收到", latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void listener_exception_doesNotBreakOthers() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<Boolean> received = new ArrayList<>();
        // 第一个 listener 抛异常
        store.addListener(new LogStore.Listener() {
            @Override
            public void onLogAppended(LogStore.Entry entry) {
                throw new RuntimeException("boom");
            }
            @Override
            public void onLogCleared() {}
        });
        // 第二个 listener 应能正常收到
        store.addListener(new LogStore.Listener() {
            @Override
            public void onLogAppended(LogStore.Entry entry) {
                received.add(true);
                latch.countDown();
            }
            @Override
            public void onLogCleared() {}
        });
        store.append("a");
        ShadowLooper.idleMainLooper();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, received.size());
    }

    @Test
    public void defaultCapacity_isTenThousand() {
        // 验证常量值 (防止被误改)
        assertEquals(10_000, LogStore.DEFAULT_CAPACITY);
    }
}
