/*
 *  ZeroStudio IDE - CallStackAdapter 单元测试 (PR-D9.5 #37)
 *
 *  覆盖:
 *    - shortName 静态方法
 *    - submit(frames, currentFrameId) 记录 currentFrameId
 *    - getItemId 基于 frameId
 *    - 同一 frameId 跨 submit 稳定
 */

package com.itsaky.androidide.debugger.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import androidx.test.core.app.ApplicationProvider;
import com.zerostudio.debugger.api.StackFrameInfo;
import com.zerostudio.debugger.api.VariableInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class CallStackAdapterTest {

    @Test
    public void shortName_simple() {
        assertEquals("Foo.java", CallStackAdapter.shortName("Foo.java"));
    }

    @Test
    public void shortName_withPath() {
        assertEquals("Main.java", CallStackAdapter.shortName("/src/com/example/Main.java"));
        assertEquals("Main.java", CallStackAdapter.shortName("src/com/example/Main.java"));
    }

    @Test
    public void shortName_windowsPath() {
        assertEquals("Main.java", CallStackAdapter.shortName("C:\\src\\Main.java"));
    }

    @Test
    public void shortName_nullOrEmpty() {
        assertEquals("?", CallStackAdapter.shortName(null));
        assertEquals("?", CallStackAdapter.shortName(""));
    }

    @Test
    public void submit_recordsCurrentFrame() {
        CallStackAdapter adapter = new CallStackAdapter();
        StackFrameInfo f1 = makeFrame(1L, "foo", "Foo.java", 10);
        StackFrameInfo f2 = makeFrame(2L, "bar", "Bar.java", 20);
        adapter.submit(Arrays.asList(f1, f2), 2L);
        // getItemId 直接读 frameId
        assertEquals(1L, adapter.getItemId(0));
        assertEquals(2L, adapter.getItemId(1));
    }

    @Test
    public void submit_emptyList() {
        CallStackAdapter adapter = new CallStackAdapter();
        adapter.submit(Collections.<StackFrameInfo>emptyList(), -1L);
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void submit_replacesList() {
        CallStackAdapter adapter = new CallStackAdapter();
        StackFrameInfo f1 = makeFrame(1L, "foo", "Foo.java", 10);
        StackFrameInfo f2 = makeFrame(2L, "bar", "Bar.java", 20);
        adapter.submit(Arrays.asList(f1, f2), 1L);
        StackFrameInfo f3 = makeFrame(3L, "baz", "Baz.java", 30);
        adapter.submit(Collections.singletonList(f3), 3L);
        assertEquals(1, adapter.getItemCount());
        assertEquals(3L, adapter.getItemId(0));
    }

    @Test
    public void getItemId_stableAcrossSubmits() {
        CallStackAdapter adapter = new CallStackAdapter();
        StackFrameInfo f1 = makeFrame(1L, "foo", "Foo.java", 10);
        StackFrameInfo f2 = makeFrame(2L, "bar", "Bar.java", 20);
        adapter.submit(Arrays.asList(f1, f2), 1L);
        long id0 = adapter.getItemId(0);
        long id1 = adapter.getItemId(1);
        // 第二次 submit 同样 frameId 在前, id 应稳定
        adapter.submit(Arrays.asList(
                makeFrame(1L, "fooV2", "Foo.java", 11),  // 内容变了但 frameId 相同
                makeFrame(2L, "barV2", "Bar.java", 21),
                makeFrame(3L, "baz", "Baz.java", 30)
        ), 2L);
        assertEquals("frameId 稳定", id0, adapter.getItemId(0));
        assertEquals("frameId 稳定", id1, adapter.getItemId(1));
    }

    @Test
    public void setListener_doesNotThrow() {
        CallStackAdapter adapter = new CallStackAdapter();
        adapter.setListener(new CallStackAdapter.Listener() {
            @Override
            public void onFramePicked(@NonNull StackFrameInfo frame) {}
        });
        adapter.setListener(null);
    }

    @Test
    public void frameIdsAreUnique() {
        CallStackAdapter adapter = new CallStackAdapter();
        StackFrameInfo f1 = makeFrame(1L, "a", "A.java", 1);
        StackFrameInfo f2 = makeFrame(2L, "b", "B.java", 2);
        StackFrameInfo f3 = makeFrame(3L, "c", "C.java", 3);
        adapter.submit(Arrays.asList(f1, f2, f3), 1L);
        long id0 = adapter.getItemId(0);
        long id1 = adapter.getItemId(1);
        long id2 = adapter.getItemId(2);
        assertTrue(id0 != id1);
        assertTrue(id1 != id2);
        assertTrue(id0 != id2);
    }

    @NonNull
    private static StackFrameInfo makeFrame(long frameId, @androidx.annotation.NonNull String method,
                                            @androidx.annotation.NonNull String sourceFile, int line) {
        return new StackFrameInfo(
                frameId, 1L, 0L, 0L, 0L, line,
                method, "L" + method + ";", sourceFile,
                Collections.<VariableInfo>emptyList());
    }

    @SuppressWarnings("unused")
    private static final Object UNUSED = ApplicationProvider.getApplicationContext();
}
