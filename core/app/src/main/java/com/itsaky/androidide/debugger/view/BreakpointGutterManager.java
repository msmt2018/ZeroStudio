/*
 *  ZeroStudio IDE - 断点 gutter 集成器
 *
 *  把 BreakpointSidebar 绑定到 CodeEditor 之上：
 *   - 动态调整 sidebar 的宽度与 Y 偏移
 *   - 监听 CodeEditor 的滚动、缩放、文本变化、内容变化来刷新 sidebar
 *   - 在 CodeEditor 关闭时清理
 *
 *  使用方式：
 *   BreakpointGutterManager.attach(editor, file).showSidebar();
 *   BreakpointGutterManager.detach(editor);
 */

package com.itsaky.androidide.debugger.view;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BreakpointGutterManager {

    // PR-D6: 改 ConcurrentHashMap。CodeEditor 上的 Sora 事件回调
    // 在 Sora 自己的事件线程里跑,UI 线程会调 attach/detach;非并发
    // HashMap 在两个线程同时读写时会丢更新或死循环。
    private static final Map<CodeEditor, BreakpointGutterManager> attached =
            new ConcurrentHashMap<>();

    public interface OnBreakpointActionListener {
        void onBreakpointClick(@NonNull String file, int line);

        default void onBreakpointClick(@NonNull String file, int line, float x, float y) {
            onBreakpointClick(file, line);
        }

        void onBreakpointLongClick(@NonNull IdeBreakpoint bp);
    }

    @NonNull public static BreakpointGutterManager attach(
            @NonNull CodeEditor editor,
            @NonNull String file) {
        BreakpointGutterManager existing = attached.get(editor);
        if (existing != null) {
            existing.bindFile(file);
            return existing;
        }
        BreakpointGutterManager m = new BreakpointGutterManager(editor, file);
        attached.put(editor, m);
        return m;
    }

    public static void detach(@NonNull CodeEditor editor) {
        BreakpointGutterManager m = attached.remove(editor);
        if (m != null) m.unbind();
    }

    public static void refreshAll() {
        for (BreakpointGutterManager m : attached.values()) m.refreshSidebar();
    }

    @Nullable
    public static BreakpointGutterManager get(@NonNull CodeEditor editor) {
        return attached.get(editor);
    }

    private final CodeEditor editor;
    @Nullable private BreakpointSidebar sidebar;
    @Nullable private OnBreakpointActionListener actionListener;
    private final String fileCanonical;
    // PR-D6: 收集 Sora 事件订阅,unbind() 时统一取消,避免内存泄漏 + 在
    // 销毁 view 上派发事件 NPE。
    private final List<SubscriptionReceipt<?>> subscriptions = new ArrayList<>();

    private BreakpointGutterManager(@NonNull CodeEditor editor, @NonNull String file) {
        this.editor = editor;
        this.fileCanonical = com.itsaky.androidide.debugger.model.BreakpointManager.normalize(file);
    }

    public void setActionListener(@Nullable OnBreakpointActionListener l) {
        this.actionListener = l;
        if (sidebar != null) {
            sidebar.setOnBreakpointClickListener(new BreakpointSidebar.OnBreakpointClickListener() {
                @Override
                public void onBreakpointClick(@NonNull String f, int line) {
                    if (actionListener != null) actionListener.onBreakpointClick(f, line);
                }
                @Override
                public void onBreakpointLongClick(@NonNull IdeBreakpoint bp) {
                    if (actionListener != null) actionListener.onBreakpointLongClick(bp);
                }
            });
        }
    }

    public void showSidebar() {
        if (sidebar != null) return;
        if (!(editor.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) editor.getParent();

        sidebar = new BreakpointSidebar(editor.getContext());
        sidebar.bind(editor, fileCanonical);
        setActionListener(actionListener);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(20f), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = android.view.Gravity.START;
        sidebar.setLayoutParams(lp);
        sidebar.setClickable(true);
        sidebar.setFocusable(true);

        // 插入到 CodeEditor 之上
        parent.addView(sidebar);

        subscribeEditorEvents();
        layoutSidebar();
        sidebar.refresh();
    }

    public void hideSidebar() {
        if (sidebar == null) return;
        if (sidebar.getParent() instanceof ViewGroup) {
            ((ViewGroup) sidebar.getParent()).removeView(sidebar);
        }
        sidebar = null;
    }

    public void bindFile(@NonNull String newFile) {
        String canon = com.itsaky.androidide.debugger.model.BreakpointManager.normalize(newFile);
        if (canon.equals(fileCanonical)) return;
        // PR-D6: 不再创建"新 manager 替换自己"——那会让原 manager 的
        // sidebar 仍然挂在 view 上(短暂),且 subscriptions 无法传递。
        // 直接在当前 manager 上重绑定文件 + 重画即可。
        hideSidebar();
        unsubscribeEditorEvents();
        // 通过反射不可行;用 detach 自身 + 重新 attach 实现完整替换。
        attached.remove(editor);
        BreakpointGutterManager m = new BreakpointGutterManager(editor, newFile);
        m.actionListener = this.actionListener;
        attached.put(editor, m);
        m.showSidebar();
    }

    public void refresh() { refreshSidebar(); }

    private void unbind() {
        unsubscribeEditorEvents();
        hideSidebar();
        attached.remove(editor);
    }

    private void refreshSidebar() {
        if (sidebar != null) sidebar.refresh();
    }

    /**
     * PR-D6: 收集 {@link SubscriptionReceipt},便于 {@link #unsubscribeEditorEvents()}
     * 统一取消。{@code subscribeEvent} 返回的 receipt 默认不持有,会在
     * editor 销毁后继续派发事件 NPE。
     */
    private void subscribeEditorEvents() {
        SubscriptionReceipt<?> r1 = editor.subscribeEvent(ScrollEvent.class, (event, subscriber) -> {
            layoutSidebar();
            refreshSidebar();
        });
        if (r1 != null) subscriptions.add(r1);
        SubscriptionReceipt<?> r2 = editor.subscribeEvent(ContentChangeEvent.class, (event, subscriber) -> {
            refreshSidebar();
        });
        if (r2 != null) subscriptions.add(r2);
        editor.post(() -> {
            layoutSidebar();
            refreshSidebar();
        });
    }

    private void unsubscribeEditorEvents() {
        for (SubscriptionReceipt<?> r : subscriptions) {
            try { r.unsubscribe(); } catch (Throwable ignored) {}
        }
        subscriptions.clear();
    }

    /**
     * 根据 CodeEditor 的 line number margin 起点，定位 sidebar 的位置和高度。
     * 这样 sidebar 就贴在 gutter 的正上方。
     */
    private void layoutSidebar() {
        if (sidebar == null) return;
        ViewGroup.LayoutParams lp = sidebar.getLayoutParams();
        if (!(lp instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;

        int targetWidth = dp(20f);
        // 起点：编辑器自带 lineNumberMarginLeft + 一段填充
        float lineNumberStart = editor.getLineNumberMarginLeft();
        if (lineNumberStart <= 0) lineNumberStart = dp(2f);
        // 在 line number 之前 ~10dp 留白
        float sidebarX = Math.max(0, lineNumberStart - targetWidth);
        flp.leftMargin = (int) sidebarX;
        flp.width = targetWidth;
        flp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        sidebar.setLayoutParams(flp);
    }

    private int dp(float v) {
        return Math.round(v * editor.getResources().getDisplayMetrics().density);
    }
}
