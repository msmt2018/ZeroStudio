/*
 *  ZeroStudio IDE - WatchesAdapter 单元测试 (PR-D9.5 #37)
 *
 *  覆盖:
 *    - WatchEntry equals / hashCode 基于 expression + value
 *    - submit(exprs) 保留已有 value
 *    - setValues 长度自适应
 *    - Listener 安装 / 卸载
 */

package com.itsaky.androidide.debugger.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import androidx.test.core.app.ApplicationProvider;
import com.itsaky.androidide.debugger.adapter.WatchesAdapter.WatchEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class WatchesAdapterTest {

    @Test
    public void watchEntry_equals_sameExprAndValue() {
        WatchEntry a = new WatchEntry("x", "1");
        WatchEntry b = new WatchEntry("x", "1");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void watchEntry_equals_differentValue() {
        WatchEntry a = new WatchEntry("x", "1");
        WatchEntry b = new WatchEntry("x", "2");
        assertNotEquals(a, b);
    }

    @Test
    public void watchEntry_equals_differentExpr() {
        WatchEntry a = new WatchEntry("x", "1");
        WatchEntry b = new WatchEntry("y", "1");
        assertNotEquals(a, b);
    }

    @Test
    public void watchEntry_nullValueTreatedAsEmpty() {
        WatchEntry a = new WatchEntry("x", null);
        WatchEntry b = new WatchEntry("x", "");
        assertEquals("null value 应等于 empty value",
                a.value, b.value);
    }

    @Test
    public void submit_preservesExistingValues() {
        WatchesAdapter adapter = new WatchesAdapter();
        adapter.submit(Arrays.asList("x", "y"));
        adapter.setValues(new String[]{"1", "2"});
        // 再 submit 时已有的 value 应保留
        adapter.submit(Arrays.asList("x", "y", "z"));
        assertEquals(3, adapter.getItemCount());
        assertEquals("1", adapter.getCurrentList().get(0).value);
        assertEquals("2", adapter.getCurrentList().get(1).value);
        // 新加的 z 没有 value
        assertEquals("", adapter.getCurrentList().get(2).value);
    }

    @Test
    public void submit_emptyList_clears() {
        WatchesAdapter adapter = new WatchesAdapter();
        adapter.submit(Arrays.asList("x", "y"));
        adapter.submit(Collections.<String>emptyList());
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void setValues_lengthTruncated() {
        WatchesAdapter adapter = new WatchesAdapter();
        adapter.submit(Arrays.asList("a", "b", "c"));
        // setValues 长度 < entry 数: 多的位置 value 留旧值还是变空? 看实现: 多于 current 的 values 被截断, 少于的补空
        adapter.setValues(new String[]{"1"});
        assertEquals("1", adapter.getCurrentList().get(0).value);
        // b, c 的 value 应该是 "" (被新 entry 覆盖)
        assertEquals("", adapter.getCurrentList().get(1).value);
        assertEquals("", adapter.getCurrentList().get(2).value);
    }

    @Test
    public void setValues_lengthPaddedWithEmpty() {
        WatchesAdapter adapter = new WatchesAdapter();
        adapter.submit(Arrays.asList("a"));
        // setValues 长度 > entry 数: 超出 current 的部分被忽略
        adapter.setValues(new String[]{"1", "2", "3"});
        assertEquals(1, adapter.getItemCount());
        assertEquals("1", adapter.getCurrentList().get(0).value);
    }

    @Test
    public void setListener_doesNotThrow() {
        WatchesAdapter adapter = new WatchesAdapter();
        adapter.setListener(new WatchesAdapter.Listener() {
            @Override
            public void onItemLongClick(int position, @NonNull String expr) {}
        });
        adapter.setListener(null);
    }

    @Test
    public void getItemId_stableAcrossSubmits() {
        WatchesAdapter adapter = new WatchesAdapter();
        adapter.submit(Arrays.asList("x", "y"));
        long id0 = adapter.getItemId(0);
        long id1 = adapter.getItemId(1);
        adapter.submit(Arrays.asList("x", "y", "z"));
        // 同一 expression 在不同 submit 后 id 稳定
        assertEquals(id0, adapter.getItemId(0));
        assertEquals(id1, adapter.getItemId(1));
        // x 和 y 的 id 不相等
        assertTrue(id0 != id1);
    }

    @SuppressWarnings("unused")
    private static final Object UNUSED = ApplicationProvider.getApplicationContext();
}
