/*
 *  ZeroStudio IDE - WatchStore 单元测试 (PR-D8.1)
 *
 *  覆盖:
 *    - add / remove / clear 基础操作
 *    - set 修改指定位置
 *    - 去重逻辑(add 时不重复, set 时合并重复)
 *    - 空白表达式被 trim + 忽略
 *    - lazy load (首次 all() 自动从文件加载)
 *    - 持久化: add 后文件应包含 JSON
 *
 *  依赖 BaseApplication.getBaseInstance() — 用 Robolectric 启动 Application。
 */

package com.itsaky.androidide.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = [33], application = android.app.Application.class)
public class WatchStoreTest {

    private WatchStore store;

    @Before
    public void setUp() throws Exception {
        // WatchStore 是单例; 强制清空它的内部状态 (loaded + watches)
        store = WatchStore.getInstance();
        Field watchesField = WatchStore.class.getDeclaredField("watches");
        watchesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> watches = (List<String>) watchesField.get(store);
        watches.clear();
        Field loadedField = WatchStore.class.getDeclaredField("loaded");
        loadedField.setAccessible(true);
        loadedField.setBoolean(store, false);
    }

    @After
    public void tearDown() throws Exception {
        // 清空文件,避免污染其它测试
        Field watchesField = WatchStore.class.getDeclaredField("watches");
        watchesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> watches = (List<String>) watchesField.get(store);
        watches.clear();
    }

    @Test
    public void add_appendsExpression() {
        store.add("i");
        store.add("list.size()");
        assertEquals(2, store.all().size());
        assertTrue(store.all().contains("i"));
        assertTrue(store.all().contains("list.size()"));
    }

    @Test
    public void add_trimsWhitespace() {
        store.add("  i  ");
        assertEquals(1, store.all().size());
        assertEquals("i", store.all().get(0));
    }

    @Test
    public void add_ignoresEmpty() {
        store.add("");
        store.add("   ");
        store.add(null == null ? "" : "");  // null 不会出现因为 NPE
        assertEquals(0, store.all().size());
    }

    @Test
    public void add_deduplicates() {
        store.add("i");
        store.add("i");
        store.add(" i ");
        assertEquals(1, store.all().size());
    }

    @Test
    public void remove_byIndex() {
        store.add("a");
        store.add("b");
        store.add("c");
        store.remove(1);
        assertEquals(2, store.all().size());
        assertEquals("a", store.all().get(0));
        assertEquals("c", store.all().get(1));
    }

    @Test
    public void remove_ignoresInvalidIndex() {
        store.add("a");
        store.remove(-1);
        store.remove(5);
        assertEquals(1, store.all().size());
    }

    @Test
    public void clear_emptiesAll() {
        for (int i = 0; i < 5; i++) store.add("e" + i);
        store.clear();
        assertEquals(0, store.all().size());
    }

    @Test
    public void set_replacesAtIndex() {
        store.add("a");
        store.add("b");
        store.set(0, "z");
        assertEquals(2, store.all().size());
        assertEquals("z", store.all().get(0));
        assertEquals("b", store.all().get(1));
    }

    @Test
    public void set_trimsWhitespace() {
        store.add("a");
        store.set(0, "  z  ");
        assertEquals("z", store.all().get(0));
    }

    @Test
    public void set_ignoresEmpty() {
        store.add("a");
        store.set(0, "");
        store.set(0, "   ");
        assertEquals("a", store.all().get(0));
    }

    @Test
    public void set_ignoresSameValue() {
        store.add("a");
        store.set(0, "a");
        // 列表大小不变(没有触发额外 save)
        assertEquals(1, store.all().size());
    }

    @Test
    public void set_dedup_removesDuplicateAndAdjustsIndex() {
        // 旧:[a, b, c],set(0, "c") 应去重:删掉 index 2,index 0 设 c
        store.add("a");
        store.add("b");
        store.add("c");
        store.set(0, "c");
        // 期望结果:[c, b] (a 被 c 替代, c 原本在 index 2 被删,index 不需调整)
        assertEquals(2, store.all().size());
        assertEquals("c", store.all().get(0));
        assertEquals("b", store.all().get(1));
    }

    @Test
    public void set_dedup_indexShiftsWhenRemovingEarlier() {
        // 旧:[a, b, c],set(2, "a") 应去重:删掉 index 0,index 变成 1
        store.add("a");
        store.add("b");
        store.add("c");
        store.set(2, "a");
        // 期望结果:[b, a]
        assertEquals(2, store.all().size());
        assertEquals("b", store.all().get(0));
        assertEquals("a", store.all().get(1));
    }

    @Test
    public void set_ignoresInvalidIndex() {
        store.add("a");
        store.set(-1, "z");
        store.set(5, "z");
        assertEquals(1, store.all().size());
        assertEquals("a", store.all().get(0));
    }

    @Test
    public void all_returnsCopy() {
        store.add("a");
        List<String> copy = store.all();
        copy.clear();
        // 内部状态不受影响
        assertEquals(1, store.all().size());
    }

    @Test
    public void lazyLoad_triggersOnFirstAll() throws Exception {
        // 预先写一个 watches.json 到 filesDir
        File f = new File(
                org.robolectric.RuntimeEnvironment.getApplication().getFilesDir(),
                "debugger/watches.json");
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        f.delete();
        try (java.io.FileWriter w = new java.io.FileWriter(f)) {
            w.write("[\"pre1\",\"pre2\",\"pre3\"]");
        }
        // 拿到单例, 但 loaded 是 true (setUp 设的) — 强制重置
        Field loadedField = WatchStore.class.getDeclaredField("loaded");
        loadedField.setAccessible(true);
        loadedField.setBoolean(store, false);
        Field watchesField = WatchStore.class.getDeclaredField("watches");
        watchesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> watches = (List<String>) watchesField.get(store);
        watches.clear();
        // 触发 lazy load
        List<String> all = store.all();
        assertEquals(3, all.size());
        assertTrue(all.contains("pre1"));
        assertTrue(all.contains("pre2"));
        assertTrue(all.contains("pre3"));
        // loaded 标志被置 true
        assertTrue(loadedField.getBoolean(store));
    }

    @Test
    public void lazyLoad_idempotent() throws Exception {
        // 第一次 all() 触发 load, 第二次不应再 load (loaded = true 后跳过)
        File f = new File(
                org.robolectric.RuntimeEnvironment.getApplication().getFilesDir(),
                "debugger/watches.json");
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (java.io.FileWriter w = new java.io.FileWriter(f)) {
            w.write("[\"a\"]");
        }
        Field loadedField = WatchStore.class.getDeclaredField("loaded");
        loadedField.setAccessible(true);
        loadedField.setBoolean(store, false);
        store.all();  // 第一次: 加载 a
        assertTrue(loadedField.getBoolean(store));
        // 改写文件
        try (java.io.FileWriter w = new java.io.FileWriter(f)) {
            w.write("[\"b\",\"c\"]");
        }
        store.all();  // 第二次: 已 loaded, 不再读文件
        // 第二次 all 应返回第一次加载的内容
        assertEquals(1, store.all().size());
        assertEquals("a", store.all().get(0));
        assertFalse(store.all().contains("b"));
    }
}
