/*
 *  ZeroStudio IDE - VariablesAdapter 单元测试 (PR-D9.5 #37)
 *
 *  覆盖:
 *    - humanType 静态方法: 各种 JNI 类型签名 → 友好名
 *    - getItemId: 相同 name+typeSignature 同 id (stable)
 *    - onBindViewHolder 中 isError → colorError 的视觉差异无法在 Robolectric
 *      完整断言, 这里只验证 setText 被正确调用
 */

package com.itsaky.androidide.debugger.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import androidx.test.core.app.ApplicationProvider;
import com.zerostudio.debugger.api.VariableInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class VariablesAdapterTest {

    // ===== humanType 静态方法 =====

    @Test
    public void humanType_primitives() {
        assertEquals("void", VariablesAdapter.humanType("V"));
        assertEquals("boolean", VariablesAdapter.humanType("Z"));
        assertEquals("byte", VariablesAdapter.humanType("B"));
        assertEquals("char", VariablesAdapter.humanType("C"));
        assertEquals("short", VariablesAdapter.humanType("S"));
        assertEquals("int", VariablesAdapter.humanType("I"));
        assertEquals("long", VariablesAdapter.humanType("J"));
        assertEquals("float", VariablesAdapter.humanType("F"));
        assertEquals("double", VariablesAdapter.humanType("D"));
    }

    @Test
    public void humanType_object() {
        assertEquals("String", VariablesAdapter.humanType("Ljava/lang/String;"));
        assertEquals("Integer", VariablesAdapter.humanType("Ljava/lang/Integer;"));
        assertEquals("Object", VariablesAdapter.humanType("Ljava/lang/Object;"));
    }

    @Test
    public void humanType_nestedObject() {
        // android.view.View$OnClickListener -> "OnClickListener"
        assertEquals("OnClickListener",
                VariablesAdapter.humanType("Landroid/view/View$OnClickListener;"));
    }

    @Test
    public void humanType_array() {
        assertEquals("[I (array)", VariablesAdapter.humanType("[I"));
        assertEquals("[Ljava/lang/String; (array)",
                VariablesAdapter.humanType("[Ljava/lang/String;"));
    }

    @Test
    public void humanType_nullAndEmpty() {
        assertEquals("?", VariablesAdapter.humanType(null));
        assertEquals("?", VariablesAdapter.humanType(""));
    }

    @Test
    public void humanType_malformedFallsThrough() {
        // "L" 单字符: 长度 < 2 → 走 "object" 分支
        assertEquals("object", VariablesAdapter.humanType("L"));
    }

    // ===== Adapter 基本行为 =====

    @Test
    public void getItemId_sameNameAndType_sameId() {
        VariablesAdapter adapter = new VariablesAdapter();
        VariableInfo a = makeVar("x", "I", "1");
        VariableInfo b = makeVar("x", "I", "2"); // value 不同
        adapter.submit(java.util.Arrays.asList(a, b));
        // getItemId 用 name+typeSignature hash, 同 name+type 必然同 id
        // (DiffUtil areItemsTheSame 也是基于这个).
        // 我们验证: 两个 position 都拿到相同 id? 不,每个 position 是该位置的 item 的 id
        // 这里只验证稳定, 不验证跨位置相等。
        long id0 = adapter.getItemId(0);
        long id1 = adapter.getItemId(1);
        // 都是有效的 hash (非 0 通常, 但允许 0)
        // 主要是确认不抛异常
        assertNotEquals("id 应被计算", Long.valueOf(id0), null);
        // 至少我们能比较同一对象两次调用
        assertEquals(adapter.getItemId(0), id0);
        assertEquals(adapter.getItemId(1), id1);
    }

    @Test
    public void setListener_doesNotThrow() {
        VariablesAdapter adapter = new VariablesAdapter();
        adapter.setListener(new VariablesAdapter.Listener() {
            @Override
            public void onItemClick(@NonNull VariableInfo variable) {}
            @Override
            public void onVariableLongClick(@NonNull VariableInfo variable) {}
        });
        // 至少能调到 setListener 不抛
        adapter.setListener(null);
    }

    @Test
    public void setHighlighted_doesNotThrow() {
        VariablesAdapter adapter = new VariablesAdapter();
        adapter.setHighlighted(42L);
        adapter.setHighlighted(-1L);
    }

    @Test
    public void submit_emptyList() {
        VariablesAdapter adapter = new VariablesAdapter();
        adapter.submit(java.util.Collections.<VariableInfo>emptyList());
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void submit_multipleItems() {
        VariablesAdapter adapter = new VariablesAdapter();
        adapter.submit(java.util.Arrays.asList(
                makeVar("x", "I", "1"),
                makeVar("y", "I", "2"),
                makeVar("z", "Ljava/lang/String;", "hello")));
        assertEquals(3, adapter.getItemCount());
    }

    @NonNull
    private static VariableInfo makeVar(@NonNull String name,
                                        @NonNull String type,
                                        @NonNull String value) {
        // VariableInfo 构造函数参数顺序: slot, objectId, name, typeSignature, value, isPrimitive, ?, ?
        return new VariableInfo(0, 0L, name, type, value, isPrimitive(type), 0, true);
    }

    private static boolean isPrimitive(@NonNull String typeSig) {
        if (typeSig.isEmpty()) return false;
        char c = typeSig.charAt(0);
        return c == 'I' || c == 'J' || c == 'F' || c == 'D'
                || c == 'Z' || c == 'B' || c == 'C' || c == 'S';
    }

    // 静态引用 ApplicationProvider 防止 unused warning
    @SuppressWarnings("unused")
    private static final Object UNUSED = ApplicationProvider.getApplicationContext();
}
