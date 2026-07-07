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

import android.view.View;
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
import com.itsaky.androidide.editor.ui.IDEEditor;
import com.itsaky.androidide.editor.ui.gutter.BreakpointGutterDelegate;
import com.itsaky.androidide.editor.ui.gutter.BreakpointGutterStates;
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
            m.refresh();
        }
    }

    @Nullable
    public static BreakpointGutterManager get(@NonNull CodeEditor editor) {
        return attached.get(editor);
    }

    @NonNull private final CodeEditor editor;
    @Nullable private View column;  // BreakpointColumnView 或 BreakpointSidebar (按 useLegacySidebar 切换)
    @Nullable private OnBreakpointActionListener actionListener;
    @NonNull private String fileCanonical;
    private final List<SubscriptionReceipt<?>> subscriptions = new ArrayList<>();
    @Nullable private DebugSessionState.Listener sessionListener;
    /** Phase 25: renderer delegate 模式标志 (替代 overlay View) */
    private boolean delegateAttached = false;

    /**
     * Phase 23 续: 设 true 用 {@link BreakpointSidebar} 替代默认的
     * {@link BreakpointColumnView}。两边事件 dispatch 接口保持兼容,
     * IDE 端 listener 无感。Phase 24 可加设置项 + 持久化偏好。
     *
     * <p>区别:
     * <ul>
     *   <li>BreakpointColumnView: 简洁, 跟 sora-editor 行号列 1:1 对齐, 性能更好
     *   <li>BreakpointSidebar: 早期版本, 自定义 View 覆盖在 gutter 区域, 视觉差异
     *       更细 (DISABLED 斜线、CONDITION 菱形等), a11y (AccessibilityNodeInfo) 更强
     * </ul>
     */
    public static boolean useLegacySidebar = false;

    private BreakpointGutterManager(@NonNull CodeEditor editor, @NonNull String file) {
        this.editor = editor;
        this.fileCanonical = BreakpointManager.normalize(file);
    }

    public void setOnActionListener(@Nullable OnBreakpointActionListener l) {
        this.actionListener = l;
    }

    // 兼容旧 import (PR-D4 之前用 setOnBreakpointClickListener, Phase 22 改用 setOnActionListener,
    // 这里保留 alias 让两个 API 都能用,跟 BreakpointSidebar.setOnBreakpointClickListener 区分)
    public void setOnBreakpointClickListener(@Nullable OnBreakpointActionListener l) {
        setOnActionListener(l);
    }

    public void rebindFile(@NonNull String newFile) {
        this.fileCanonical = BreakpointManager.normalize(newFile);
        if (column instanceof BreakpointColumnView) {
            ((BreakpointColumnView) column).rebindFile(newFile);
        } else if (column instanceof BreakpointSidebar) {
            ((BreakpointSidebar) column).rebindFile(newFile);
        }
    }

    public void refresh() {
        if (delegateAttached) {
            // renderer delegate 模式: invalidate editor 让 renderer 重绘
            editor.postInvalidate();
            return;
        }
        if (column == null) return;
        if (column instanceof BreakpointColumnView) {
            ((BreakpointColumnView) column).refresh();
        } else if (column instanceof BreakpointSidebar) {
            ((BreakpointSidebar) column).refresh();
        }
    }

    public void show() {
        if (column != null || delegateAttached) return;

        // Phase 25: 优先使用 IDEEditorRenderer (直接在编辑器 Canvas 上绘制断点列,
        // 与行号 1:1 同步, 无 overlay View 吞事件问题)。
        // 仅当 editor 是 IDEEditor 且 useLegacySidebar=false 时启用。
        if (!useLegacySidebar && editor instanceof IDEEditor) {
            attachRendererDelegate((IDEEditor) editor);
        }

        // 如果 renderer delegate 不可用, 回退到 overlay View 方案
        if (!delegateAttached) {
            if (!(editor.getParent() instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) editor.getParent();
            // 移除旧 column (避免重复挂)
            for (int i = 0; i < parent.getChildCount(); i++) {
                View c = parent.getChildAt(i);
                if (c instanceof BreakpointColumnView || c instanceof BreakpointSidebar) {
                    parent.removeView(c);
                }
            }
            // Phase 23 续: 根据 useLegacySidebar flag 选择 gutter View
            if (useLegacySidebar) {
                attachLegacySidebar(parent);
            } else {
                attachColumnView(parent);
            }
        }
        subscribeEditorEvents();
        subscribeSessionState();
        // BreakpointColumnView 需要 layoutToMatchLineColumn; BreakpointSidebar 不需要
        if (column instanceof BreakpointColumnView) {
            ((BreakpointColumnView) column).layoutToMatchLineColumn();
        }
        if (column instanceof BreakpointColumnView) {
            ((BreakpointColumnView) column).refresh();
        } else if (column instanceof BreakpointSidebar) {
            ((BreakpointSidebar) column).refresh();
        } else if (delegateAttached) {
            // renderer delegate 模式: invalidate editor 让 renderer 重绘
            editor.postInvalidate();
        }
    }

    /**
     * Phase 25: 通过 IDEEditorRenderer 绘制断点列 (不使用 overlay View)。
     *
     * <p>把 [BreakpointGutterDelegate] 设置到 [IDEEditor] 上,
     * IDEEditorRenderer 会通过 delegate 获取断点数据, 在 drawLineNumber /
     * drawLineNumberBackground 中直接绘制断点圆点 + 命中行高亮。
     *
     * <p>触摸事件由 IDEEditor.onTouchEvent 拦截并路由到 delegate 的回调,
     * delegate 再转发给 [actionListener] (弹 BreakpointTypePickerDialog 等)。
     */
    private void attachRendererDelegate(@NonNull IDEEditor ideEditor) {
        ideEditor.setBreakpointGutterDelegate(new BreakpointGutterDelegateImpl(ideEditor));
        delegateAttached = true;
    }

    /**
     * Phase 23 续: 挂载默认 BreakpointColumnView。事件流:
     *  1) BreakpointColumnView 内 GestureDetector 派发 onBreakpointClick / onBreakpointExistingClick / onBreakpointLongClick
     *  2) 包装层: onBreakpointClick 默认弹 BreakpointTypePickerDialog (高斯模糊磨砂)
     *  3) picker 选择后, 回调 (entry, file, line, x, y) → 转发给 IDE 端 actionListener.onAddBreakpoint
     *  4) 其它两个直接转发给 actionListener.onEditBreakpoint / onBreakpointLongClick
     */
    private void attachColumnView(@NonNull ViewGroup parent) {
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
    }

    /**
     * Phase 23 续: 挂载早期版 BreakpointSidebar。事件流:
     *  1) BreakpointSidebar 内 GestureDetector 派发 onBreakpointClick (空白行) /
     *     onBreakpointExistingClick (已有断点) / onBreakpointLongClick
     *  2) 包装层: 同样默认弹 BreakpointTypePickerDialog (4 类断点选择), 选中后转发给 IDE 端 listener
     *  3) onBreakpointExistingClick 走 IDE 端的 onEditBreakpoint (弹 BreakpointDetailDialog)
     *  4) onBreakpointLongClick 走 IDE 端的 onBreakpointLongClick (弹 BreakpointContextMenu)
     *
     * <p>跟 BreakpointColumnView 的事件流一致, IDE 端 listener 接口完全不变。
     */
    private void attachLegacySidebar(@NonNull ViewGroup parent) {
        BreakpointSidebar v = new BreakpointSidebar(editor.getContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) (28 * editor.getResources().getDisplayMetrics().density),
                FrameLayout.LayoutParams.MATCH_PARENT);
        lp.gravity = android.view.Gravity.START;
        v.setLayoutParams(lp);
        parent.addView(v);
        v.bind(editor, fileCanonical);
        v.setOnBreakpointClickListener(new BreakpointSidebar.OnBreakpointClickListener() {
            @Override
            public void onBreakpointClick(@NonNull String file, int line,
                                          float screenX, float screenY) {
                if (actionListener == null) return;
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
            public void onBreakpointExistingClick(@NonNull IdeBreakpoint bp,
                                                  float screenX, float screenY) {
                if (actionListener != null) {
                    actionListener.onEditBreakpoint(bp, screenX, screenY);
                }
            }
            @Override
            public void onBreakpointLongClick(@NonNull IdeBreakpoint bp,
                                              float screenX, float screenY) {
                if (actionListener != null) {
                    actionListener.onBreakpointLongClick(bp, screenX, screenY);
                }
            }
        });
        this.column = v;
    }

    public void hide() {
        // Phase 25: 清除 renderer delegate
        if (delegateAttached && editor instanceof IDEEditor) {
            ((IDEEditor) editor).setBreakpointGutterDelegate(null);
            delegateAttached = false;
            editor.postInvalidate();
        }
        if (column == null) return;
        if (column.getParent() instanceof ViewGroup) {
            ((ViewGroup) column.getParent()).removeView(column);
        }
        if (column instanceof BreakpointColumnView) {
            ((BreakpointColumnView) column).unbind();
        } else if (column instanceof BreakpointSidebar) {
            ((BreakpointSidebar) column).unbind();
        }
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
                        if (column instanceof BreakpointColumnView) {
                            ((BreakpointColumnView) column).layoutToMatchLineColumn();
                            ((BreakpointColumnView) column).refresh();
                        } else if (column instanceof BreakpointSidebar) {
                            ((BreakpointSidebar) column).refresh();
                        }
                    });
            if (r1 != null) subscriptions.add(r1);
        } catch (Throwable ignored) {}
        try {
            SubscriptionReceipt<?> r2 = editor.subscribeEvent(ContentChangeEvent.class,
                    (event, sub) -> {
                        if (column instanceof BreakpointColumnView) {
                            ((BreakpointColumnView) column).refresh();
                        } else if (column instanceof BreakpointSidebar) {
                            ((BreakpointSidebar) column).refresh();
                        }
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
            sessionListener = new DebugSessionState.Listener() {
                @Override
                public void onStateChanged(@NonNull DebugSessionState s) {
                    if (delegateAttached) {
                        editor.postInvalidate();
                    } else if (column instanceof BreakpointColumnView) {
                        ((BreakpointColumnView) column).refresh();
                    } else if (column instanceof BreakpointSidebar) {
                        ((BreakpointSidebar) column).refresh();
                    }
                }
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

    /**
     * Phase 25: [BreakpointGutterDelegate] 实现, 桥接 [BreakpointManager] /
     * [DebuggerController] 数据到 [IDEEditorRenderer]。
     *
     * <p>触摸回调转发给 [actionListener] (弹 BreakpointTypePickerDialog /
     * BreakpointDetailDialog)。
     */
    private class BreakpointGutterDelegateImpl implements BreakpointGutterDelegate {

        private final IDEEditor ideEditor;

        BreakpointGutterDelegateImpl(@NonNull IDEEditor editor) {
            this.ideEditor = editor;
        }

        @Override
        public String currentFile() {
            return fileCanonical;
        }

        @Override
        public int breakpointStateForLine(int line) {
            if (fileCanonical == null) return BreakpointGutterStates.NONE;
            IdeBreakpoint bp = BreakpointManager.getInstance().findAt(fileCanonical, line);
            if (bp == null) return BreakpointGutterStates.NONE;
            return mapState(bp.state);
        }

        @Override
        public int hitLine() {
            try {
                DebugSessionState st = DebuggerController.getInstance().sessionState();
                if (st.isSuspended() && st.currentFrame() != null) {
                    return st.currentFrame().lineNumber;
                }
            } catch (Throwable ignored) {}
            return -1;
        }

        @Override
        public void onGutterClick(int line, float screenX, float screenY) {
            if (actionListener == null) return;
            FragmentActivity activity = findActivity(ideEditor);
            if (activity == null) return;
            // 弹 BreakpointTypePickerDialog (4 类断点选择), 选中后回调 actionListener.onAddBreakpoint
            BreakpointTypePickerDialog.show(activity, fileCanonical, line, screenX, screenY,
                    (entry, f, l, x, y) -> {
                        if (actionListener != null) {
                            actionListener.onAddBreakpoint(f, l, entry, x, y);
                        }
                    });
        }

        @Override
        public void onGutterExistingClick(int line, float screenX, float screenY) {
            if (actionListener == null) return;
            IdeBreakpoint bp = BreakpointManager.getInstance().findAt(fileCanonical, line);
            if (bp != null) {
                actionListener.onEditBreakpoint(bp, screenX, screenY);
            }
        }

        @Override
        public void onGutterLongClick(int line, float screenX, float screenY) {
            if (actionListener == null) return;
            // 长按: 找最近的断点 (±2 行), 弹菜单
            IdeBreakpoint nearest = findNearestBreakpoint(fileCanonical, line);
            if (nearest != null) {
                actionListener.onBreakpointLongClick(nearest, screenX, screenY);
            } else {
                // 空白处长按 = 等同于单击 (弹 picker)
                FragmentActivity activity = findActivity(ideEditor);
                if (activity == null) return;
                BreakpointTypePickerDialog.show(activity, fileCanonical, line, screenX, screenY,
                        (entry, f, l, x, y) -> {
                            if (actionListener != null) {
                                actionListener.onAddBreakpoint(f, l, entry, x, y);
                            }
                        });
            }
        }

        @Nullable
        private IdeBreakpoint findNearestBreakpoint(@NonNull String file, int row) {
            IdeBreakpoint best = null;
            int bestDelta = Integer.MAX_VALUE;
            for (IdeBreakpoint bp : BreakpointManager.getInstance().forFile(file)) {
                int d = Math.abs(bp.line - row);
                if (d < bestDelta && d <= 2) {
                    bestDelta = d;
                    best = bp;
                }
            }
            return best;
        }

        /** 把 IdeBreakpoint.State 映射到 BreakpointGutterStates 常量。 */
        private int mapState(@NonNull IdeBreakpoint.State state) {
            switch (state) {
                case NORMAL:         return BreakpointGutterStates.NORMAL;
                case INVALID:        return BreakpointGutterStates.INVALID;
                case VERIFIED:       return BreakpointGutterStates.VERIFIED;
                case CONDITION:      return BreakpointGutterStates.CONDITION;
                case LOG:            return BreakpointGutterStates.LOG;
                case DISABLED:       return BreakpointGutterStates.DISABLED;
                case HIT:            return BreakpointGutterStates.HIT;
                case EXCEPTION:      return BreakpointGutterStates.EXCEPTION;
                case FIELD_WATCHPOINT: return BreakpointGutterStates.FIELD_WATCHPOINT;
                case METHOD:         return BreakpointGutterStates.METHOD;
                case DEPENDENT:      return BreakpointGutterStates.DEPENDENT;
                case TEMPORARY:      return BreakpointGutterStates.TEMPORARY;
                default:             return BreakpointGutterStates.NORMAL;
            }
        }
    }
}
