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
import io.github.rosemoe.sora.widget.CodeEditor;
import java.util.HashMap;
import java.util.Map;

public final class BreakpointGutterManager {

    private static final Map<CodeEditor, BreakpointGutterManager> attached = new HashMap<>();

    public interface OnBreakpointActionListener {
        void onBreakpointClick(@NonNull String file, int line);
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
                (int) dp(20f), ViewGroup.LayoutParams.MATCH_PARENT);
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
        // 简单替换
        unbind();
        BreakpointGutterManager m = new BreakpointGutterManager(editor, newFile);
        m.actionListener = this.actionListener;
        attached.put(editor, m);
        m.showSidebar();
    }

    public void refresh() { refreshSidebar(); }

    private void unbind() {
        hideSidebar();
        attached.remove(editor);
    }

    private void refreshSidebar() {
        if (sidebar != null) sidebar.refresh();
    }

    private void subscribeEditorEvents() {
        // CodeEditor.subscribeEvent takes a Consumer-style handler that
        // must not return a value. Returning null from a lambda would
        // break the SAM conversion in Java 17.
        editor.subscribeEvent(ScrollEvent.class, (event, subscriber) -> {
            layoutSidebar();
            refreshSidebar();
        });
        editor.subscribeEvent(ContentChangeEvent.class, (event, subscriber) -> {
            refreshSidebar();
        });
        editor.post(() -> {
            layoutSidebar();
            refreshSidebar();
        });
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

        int targetWidth = (int) dp(20f);
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

    private float dp(float v) {
        return v * editor.getResources().getDisplayMetrics().density;
    }
}
