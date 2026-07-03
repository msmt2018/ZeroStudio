/*
 *  ZeroStudio IDE - 断点 gutter 集成器 (Phase 20 重构)
 *
 *  把 BreakpointColumnView 绑定到 CodeEditor 之上：
 *   - 动态调整 column 的宽度 / X 偏移 (与 sora-editor 行号列 1:1 对齐)
 *   - 监听 CodeEditor 滚动 / 内容 / 缩放 事件刷新
 *   - 单击/长按回调 -> BreakpointTypePickerDialog (高斯模糊磨砂) / BreakpointDetailDialog
 *   - CodeEditor 销毁时清理
 *
 *  替代旧版:用 BreakpointSidebar (没有同步行号列的 layout,没有命中行水平高亮)
 *
 *  使用方式:
 *   BreakpointGutterManager.attach(editor, file).setOnActionListener(listener);
 *   BreakpointGutterManager.detach(editor);
 */

package com.itsaky.androidide.debugger.view;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentActivity;
import com.itsaky.androidide.debugger.DebugSessionState;
import com.itsaky.androidide.debugger.DebuggerController;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.debugger.model.BreakpointTypeCatalog;
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

    private static final Map<CodeEditor, BreakpointGutterManager> attached =
            new ConcurrentHashMap<>();

    public interface OnBreakpointActionListener {
        /** 单击空白行 (无断点) -> 弹出 BreakpointTypePickerDialog。 */
        void onAddBreakpoint(@NonNull String file, int line,
                             @NonNull BreakpointTypeCatalog.Entry entry,
                             float screenX, float screenY);
        /** 单击已存在断点 -> 弹出 BreakpointDetailDialog。 */
        void onEditBreakpoint(@NonNull IdeBreakpoint bp, float screenX, float screenY);
        /** 长按已有断点 -> 弹菜单 (禁用/启用/删除)。 */
        void onBreakpointLongClick(@NonNull IdeBreakpoint bp, float screenX, float screenY);
    }

    /** 简易默认实现:弹出 frosted glass dialogs。 */
    public static abstract class DefaultActionListener implements OnBreakpointActionListener {
        @Override
        public void onAddBreakpoint(@NonNull String file, int line,
                                    @NonNull BreakpointTypeCatalog.Entry entry,
                                    float screenX, float screenY) {
            // 默认:由调用方在 IDE 端 (CodeEditorFragment) 重写以使用真实 FragmentActivity
            // 此 fallback 仅在测试 / 非 FragmentActivity 场景下生效
        }
        @Override
        public void onEditBreakpoint(@NonNull IdeBreakpoint bp, float screenX, float screenY) {}
        @Override
        public void onBreakpointLongClick(@NonNull IdeBreakpoint bp, float screenX, float screenY) {}
    }

    @NonNull
    public static BreakpointGutterManager attach(@NonNull CodeEditor editor,
                                                 @NonNull String file) {
        BreakpointGutterManager existing = attached.get(editor);
        if (existing != null) {
            existing.rebindFile(file);
            return existing;
        }
        BreakpointGutterManager m = new BreakpointGutterManager(editor, file);
        attached.put(editor, m);
        m.show();
        return m;
    }

    public static void detach(@NonNull CodeEditor editor) {
        BreakpointGutterManager m = attached.remove(editor);
        if (m != null) m.unbind();
    }

    public static void refreshAll() {
        for (BreakpointGutterManager m : attached.values()) {
            if (m.column != null) m.column.refresh();
        }
    }

    @Nullable
    public static BreakpointGutterManager get(@NonNull CodeEditor editor) {
        return attached.get(editor);
    }

    @NonNull private final CodeEditor editor;
    @Nullable private BreakpointColumnView column;
    @Nullable private OnBreakpointActionListener actionListener;
    @NonNull private String fileCanonical;
    private final List<SubscriptionReceipt<?>> subscriptions = new ArrayList<>();
    @Nullable private DebugSessionState.Listener sessionListener;

    private BreakpointGutterManager(@NonNull CodeEditor editor, @NonNull String file) {
        this.editor = editor;
        this.fileCanonical = BreakpointManager.normalize(file);
    }

    public void setOnActionListener(@Nullable OnBreakpointActionListener l) {
        this.actionListener = l;
    }

    public void rebindFile(@NonNull String newFile) {
        this.fileCanonical = BreakpointManager.normalize(newFile);
        if (column != null) column.rebindFile(newFile);
    }

    public void refresh() {
        if (column != null) column.refresh();
    }

    public void show() {
        if (column != null) return;
        if (!(editor.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) editor.getParent();
        // 移除旧 column
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            if (c instanceof BreakpointColumnView) {
                parent.removeView(c);
            }
        }
        BreakpointColumnView v = new BreakpointColumnView(editor.getContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        lp.gravity = android.view.Gravity.START;
        v.setLayoutParams(lp);
        parent.addView(v);
        v.bind(editor, fileCanonical);
        v.setOnBreakpointActionListener(new BreakpointColumnView.OnBreakpointActionListener() {
            @Override
            public void onBreakpointClick(@NonNull String file, int line,
                                          float screenX, float screenY) {
                if (actionListener == null) return;
                // 弹 BreakpointTypePickerDialog — 由 IDE CodeEditor 端的 listener 处理
                // 这里走默认行为:弹 picker
                FragmentActivity activity = findActivity(editor);
                if (activity == null) return;
                BreakpointTypePickerDialog.show(activity, file, line, screenX, screenY,
                        (entry, f, l, x, y) -> {
                            if (actionListener != null) {
                                actionListener.onAddBreakpoint(f, l, entry, x, y);
                            }
                        });
            }
            @Override
            public void onBreakpointLongClick(@NonNull IdeBreakpoint bp,
                                              float screenX, float screenY) {
                if (actionListener != null) {
                    actionListener.onBreakpointLongClick(bp, screenX, screenY);
                }
            }
            @Override
            public void onBreakpointExistingClick(@NonNull IdeBreakpoint bp,
                                                  float screenX, float screenY) {
                if (actionListener != null) {
                    actionListener.onEditBreakpoint(bp, screenX, screenY);
                }
            }
        });
        this.column = v;
        subscribeEditorEvents();
        subscribeSessionState();
        v.layoutToMatchLineColumn();
        v.refresh();
    }

    public void hide() {
        if (column == null) return;
        if (column.getParent() instanceof ViewGroup) {
            ((ViewGroup) column.getParent()).removeView(column);
        }
        column.unbind();
        column = null;
    }

    private void unbind() {
        unsubscribeEditorEvents();
        unsubscribeSessionState();
        hide();
        attached.remove(editor);
    }

    private void subscribeEditorEvents() {
        try {
            SubscriptionReceipt<?> r1 = editor.subscribeEvent(ScrollEvent.class,
                    (event, sub) -> {
                        if (column != null) {
                            column.layoutToMatchLineColumn();
                            column.refresh();
                        }
                    });
            if (r1 != null) subscriptions.add(r1);
        } catch (Throwable ignored) {}
        try {
            SubscriptionReceipt<?> r2 = editor.subscribeEvent(ContentChangeEvent.class,
                    (event, sub) -> {
                        if (column != null) column.refresh();
                    });
            if (r2 != null) subscriptions.add(r2);
        } catch (Throwable ignored) {}
    }

    private void unsubscribeEditorEvents() {
        for (SubscriptionReceipt<?> r : subscriptions) {
            try { r.unsubscribe(); } catch (Throwable ignored) {}
        }
        subscriptions.clear();
    }

    private void subscribeSessionState() {
        try {
            DebugSessionState st = DebuggerController.getInstance().sessionState();
            sessionListener = s -> {
                if (column != null) column.refresh();
            };
            st.addListener(sessionListener);
        } catch (Throwable ignored) {}
    }

    private void unsubscribeSessionState() {
        if (sessionListener == null) return;
        try { DebuggerController.getInstance().sessionState().removeListener(sessionListener); }
        catch (Throwable ignored) {}
        sessionListener = null;
    }

    @Nullable
    private static FragmentActivity findActivity(@NonNull android.view.View v) {
        android.content.Context c = v.getContext();
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof FragmentActivity) return (FragmentActivity) c;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }
}
