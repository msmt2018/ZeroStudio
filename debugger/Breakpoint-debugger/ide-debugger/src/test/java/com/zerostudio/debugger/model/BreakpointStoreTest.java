/*
 *  ZeroStudio IDE - BreakpointStore 单元测试 (Phase F2)
 *
 *  覆盖:
 *    - 添加/查询/删除断点的基本 CRUD
 *    - findByLocation 用 file+line 反查
 *    - 一次性断点 (one-shot) 行为
 *    - removeOneShots: 当 SuspendInfo 触发时,只清除 requestId>0 的 one-shot
 *    - clear 全部清空
 *    - snapshot 是不可变列表
 *    - 同一 file+line 添加多个断点,后添加的会覆盖 (按 id 索引)
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.zerostudio.debugger.api.Breakpoint;
import com.zerostudio.debugger.api.SuspendInfo;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BreakpointStoreTest {

    private BreakpointStore store;

    @Before
    public void setUp() {
        store = new BreakpointStore();
    }

    private Breakpoint newBp(long id, String file, int line) {
        return new Breakpoint(id, file, line, null, null,
                Breakpoint.HitCountMode.ALWAYS, 0);
    }

    @Test
    public void addAndGetReturnsSameInstance() {
        Breakpoint bp = newBp(1L, "Foo.java", 10);
        store.add(bp);
        assertSame(bp, store.get(1L));
    }

    @Test
    public void getReturnsNullForUnknownId() {
        assertNull(store.get(999L));
    }

    @Test
    public void removeClearsByIdAndByLocation() {
        store.add(newBp(2L, "Foo.java", 5));
        assertNotNull(store.findByLocation("Foo.java", 5));
        store.remove(2L);
        assertNull(store.get(2L));
        assertNull(store.findByLocation("Foo.java", 5));
    }

    @Test
    public void findByLocationReturnsCorrectBreakpoint() {
        store.add(newBp(3L, "A.java", 1));
        store.add(newBp(4L, "B.java", 2));
        store.add(newBp(5L, "A.java", 3));
        assertEquals(3L, store.findByLocation("A.java", 1).id);
        assertEquals(4L, store.findByLocation("B.java", 2).id);
        assertEquals(5L, store.findByLocation("A.java", 3).id);
        assertNull(store.findByLocation("A.java", 99));
    }

    @Test
    public void findByLocationHandlesPathSeparators() {
        store.add(newBp(1L, "src/main/A.java", 10));
        assertNotNull(store.findByLocation("src/main/A.java", 10));
        // 完全相同的字符串才命中
        assertNull(store.findByLocation("src\\main\\A.java", 10));
    }

    @Test
    public void allIsUnmodifiable() {
        store.add(newBp(1L, "Foo.java", 1));
        try {
            store.all().clear();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void snapshotReturnsIndependentList() {
        store.add(newBp(1L, "Foo.java", 1));
        store.add(newBp(2L, "Foo.java", 2));
        List<Breakpoint> snap = store.snapshot();
        assertEquals(2, snap.size());
        // 修改 store 不应影响 snapshot
        store.add(newBp(3L, "Foo.java", 3));
        assertEquals(2, snap.size());
    }

    @Test
    public void oneShotAddAndQuery() {
        Breakpoint bp = newBp(1L, "Foo.java", 1);
        store.add(bp);
        assertFalse(store.isOneShot(1L));
        store.setOneShot(1L, true);
        assertTrue(store.isOneShot(1L));
        store.setOneShot(1L, false);
        assertFalse(store.isOneShot(1L));
    }

    @Test
    public void oneShotIsIdTracked() {
        // 没有添加断点的 id 不会 panic
        store.setOneShot(999L, true);
        assertTrue(store.isOneShot(999L));
    }

    @Test
    public void removeOneShotsClearsVerifiedOnes() {
        Breakpoint a = newBp(1L, "Foo.java", 1);
        a.requestId = 10; // 已验证
        Breakpoint b = newBp(2L, "Foo.java", 2);
        b.requestId = 0;   // 未验证
        store.add(a);
        store.add(b);
        store.setOneShot(1L, true);
        store.setOneShot(2L, true);

        store.removeOneShots(new SuspendInfo());

        // a 已验证 → 移除
        assertNull(store.get(1L));
        // b 未验证 → 保留
        assertNotNull(store.get(2L));
        // one-shot set 被清空
        assertFalse(store.isOneShot(1L));
        assertFalse(store.isOneShot(2L));
    }

    @Test
    public void removeOneShotsWithNoOneShotsIsNoop() {
        store.add(newBp(1L, "Foo.java", 1));
        store.removeOneShots(new SuspendInfo());
        assertNotNull(store.get(1L));
    }

    @Test
    public void clearRemovesEverything() {
        store.add(newBp(1L, "Foo.java", 1));
        store.add(newBp(2L, "Bar.java", 2));
        store.setOneShot(1L, true);
        store.clear();
        assertNull(store.get(1L));
        assertNull(store.get(2L));
        assertFalse(store.isOneShot(1L));
        assertEquals(0, store.snapshot().size());
    }

    @Test
    public void addSameLocationTwiceReplacesByLocation() {
        // 同一 (file, line) 添加多个 id,byLocation 只保留最新
        Breakpoint a = newBp(1L, "Foo.java", 1);
        Breakpoint b = newBp(2L, "Foo.java", 1);
        store.add(a);
        store.add(b);
        // byId 都有,但 byLocation 指向最后添加的
        assertSame(b, store.findByLocation("Foo.java", 1));
        assertSame(a, store.get(1L));
        assertSame(b, store.get(2L));
    }
}
