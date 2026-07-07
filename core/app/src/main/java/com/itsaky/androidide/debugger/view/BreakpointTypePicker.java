/*
 *  ZeroStudio IDE - 断点类型选择器 (PR-D6)
 *
 *  当用户点击 CodeEditor 的 gutter 区域想添加新断点时,弹一个
 *  ListPopupWindow 让用户选择:
 *
 *    - 普通断点 (toggle 行)
 *    - 条件断点 (命中时求值,真则停下)
 *    - 日志点   (命中时输出日志,不暂停)
 *
 *  入口: {@link #showAtPosition(View anchor, int x, int y, Callback cb)}
 *        通过 1x1 的 ghost anchor 定位到任意屏幕坐标。
 *
 *  这个类不持有对 CodeEditor / BreakpointManager 的硬引用,
 *  选完类型后通过 {@link Callback} 回调给调用方处理。
 */

package com.itsaky.androidide.debugger.view;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.itsaky.androidide.R;
import com.itsaky.androidide.utils.ILogger;

public final class BreakpointTypePicker {

    private static final String TAG = "BreakpointTypePicker";

    public enum Type {
        NORMAL(R.string.debugger_bp_picker_normal),
        CONDITION(R.string.debugger_bp_picker_condition),
        LOGPOINT(R.string.debugger_bp_picker_logpoint),
        // Phase 23 续: 高级类型入口 — 跳到 BreakpointTypePickerDialog (高斯模糊磨砂),
        // 那是 Phase 22 引入的 4 类断点选择器, 支持 EXCEPTION / SYMBOLIC / WATCHPOINT /
        // DEPENDENT 等高级类型。BreakpointTypePicker 自己只显示 3 个常用 + MORE。
        MORE(R.string.debugger_bp_picker_more);

        @StringRes public final int labelRes;
        Type(@StringRes int r) { this.labelRes = r; }
    }

    public interface Callback {
        /**
         * 用户选了一个类型(可能为 null,表示取消)。
         */
        void onTypePicked(@NonNull Type type);
    }

    private final Context ctx;
    @NonNull private final ListPopupWindow popup;
    @NonNull private final ArrayAdapter<String> adapter;


    public static void show(
            @NonNull Context context,
            @NonNull View anchor,
            @NonNull String file,
            int line) {
        showAtPosition(context, anchor, 0, 0, file, line);
    }

    public static void showAtPosition(
            @NonNull Context context,
            @NonNull View anchor,
            int x,
            int y,
            @NonNull String file,
            int line) {
        BreakpointTypePicker picker = new BreakpointTypePicker(context);
        picker.showAtPosition(anchor, x, y, type -> {
            com.itsaky.androidide.debugger.model.BreakpointManager manager =
                    com.itsaky.androidide.debugger.model.BreakpointManager.getInstance();
            if (type == Type.NORMAL) {
                manager.toggle(file, line);
            } else if (type == Type.MORE) {
                // Phase 23 续: 用户选 "More..." → 弹 BreakpointTypePickerDialog (高斯模糊磨砂),
                // 那是 Phase 22 引入的 4 类断点选择器,支持 EXCEPTION / SYMBOLIC / WATCHPOINT
                // / DEPENDENT 等高级类型。BreakpointTypePicker 自己只显示 3 个常用类型,
                // 高级类型通过 MORE 入口跳转 — 这条路径保留给"在 gutter 弹快速选择 + 还想进
                // 高级 dialog"的场景。
                if (context instanceof androidx.fragment.app.FragmentActivity) {
                    BreakpointTypePickerDialog.show(
                            (androidx.fragment.app.FragmentActivity) context,
                            file, line, x, y,
                            (entry, f, l, x2, y2) -> {
                                // BreakpointTypePickerDialog 内部已经走 BreakpointDetailDialog
                                // (Phase 22). 这里只做日志,实际添加由 dialog 完成。
                                ILogger.ROOT.debug(TAG + ": Forwarded to BreakpointTypePickerDialog: " + entry);
                            });
                }
            } else {
                com.itsaky.androidide.debugger.model.IdeBreakpoint bp =
                        manager.findAt(file, line);
                if (bp == null) {
                    bp = new com.itsaky.androidide.debugger.model.IdeBreakpoint(file, line);
                    manager.add(bp);
                }
                if (type == Type.CONDITION) {
                    com.itsaky.androidide.debugger.BreakpointConditionDialog.showDialog(
                            ((androidx.fragment.app.FragmentActivity) context).getSupportFragmentManager(),
                            bp.id);
                } else if (type == Type.LOGPOINT) {
                    bp.setLogMessage("");
                    com.itsaky.androidide.debugger.BreakpointConditionDialog.showDialog(
                            ((androidx.fragment.app.FragmentActivity) context).getSupportFragmentManager(),
                            bp.id);
                }
            }
        });
    }

    public BreakpointTypePicker(@NonNull Context context) {
        this.ctx = context;
        this.popup = new ListPopupWindow(context);
        String[] items = new String[Type.values().length];
        for (int i = 0; i < Type.values().length; i++) {
            items[i] = context.getString(Type.values()[i].labelRes);
        }
        this.adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, items);
        popup.setAnchorView(makeGhostAnchor(context));
        popup.setAdapter(adapter);
        popup.setModal(true);
        popup.setContentWidth(measureContentWidth());
        popup.setOnItemClickListener((parent, view, position, id) -> {
            Type t = Type.values()[position];
            if (currentCb != null) {
                try { currentCb.onTypePicked(t); } catch (Throwable th) {
                    ILogger.ROOT.warn(TAG + ": " + "onTypePicked threw: " + th.getMessage());
                }
            }
            dismiss();
        });
        popup.setOnDismissListener(() -> currentCb = null);
    }

    @Nullable private Callback currentCb;

    /**
     * 显示选择器。
     *
     * @param anchor any view already attached to a window (used to find a
     *               display to attach the popup to)
     * @param x      screen X coordinate (px) of the click
     * @param y      screen Y coordinate (px) of the click
     * @param cb     callback;called on UI thread. May be null.
     */
    public void showAtPosition(@NonNull View anchor, int x, int y, @Nullable Callback cb) {
        // 1x1 ghost anchor,定位到用户实际点击处。
        View ghost = popup.getAnchorView();
        if (ghost == null) {
            ghost = makeGhostAnchor(anchor.getContext());
            popup.setAnchorView(ghost);
        }
        ViewGroup.LayoutParams lp = ghost.getLayoutParams();
        if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
            // 已有 margin layout 不会影响实际显示(只是用作 anchor reference)
            ((android.view.ViewGroup.MarginLayoutParams) lp).topMargin = y;
            ((android.view.ViewGroup.MarginLayoutParams) lp).leftMargin = x;
        }
        ghost.setLayoutParams(lp);
        // ListPopupWindow 实际上需要 anchor 已在 window 内;若 anchor 不是
        // 真实可见的 view,使用 setTouchModal + dismissOnTouch + 手动 show:
        // 我们的 ghost 用 1x1 加 onDetachedFromWindow 处理。
        if (ghost.getParent() == null) {
            try {
                android.app.Activity activity = scanActivity(anchor);
                if (activity != null) {
                    android.view.ViewGroup root =
                            activity.findViewById(android.R.id.content);
                    if (root != null) {
                        android.widget.FrameLayout.LayoutParams p =
                                new android.widget.FrameLayout.LayoutParams(1, 1);
                        p.gravity = Gravity.TOP | Gravity.START;
                        p.leftMargin = x;
                        p.topMargin = y;
                        ghost.setLayoutParams(p);
                        // Phase 20: 用 WeakReference 跟踪,onDismiss 时一定清理,
                        // 避免 Activity 销毁后 ghost 仍在 root 上导致 WindowLeaked。
                        ghost.setTag(new java.lang.ref.WeakReference<>(root));
                        root.addView(ghost);
                    }
                }
            } catch (Throwable t) {
                ILogger.ROOT.warn(TAG + ": " + "Could not add ghost anchor: " + t.getMessage());
            }
        }
        currentCb = cb;
        try {
            popup.show();
        } catch (Throwable t) {
            ILogger.ROOT.warn(TAG + ": " + "popup.show failed: " + t.getMessage());
        }
    }

    public void dismiss() {
        try { popup.dismiss(); } catch (Throwable ignored) {}
        // 清理 ghost anchor — Phase 20: 即便 popup.dismiss 抛错也尝试清理。
        try {
            View ghost = popup.getAnchorView();
            if (ghost != null && ghost.getParent() instanceof ViewGroup) {
                try { ((ViewGroup) ghost.getParent()).removeView(ghost); } catch (Throwable ignored) {}
            } else if (ghost != null) {
                // Phase 20: 从 tag 拿 root 引用,二次清理
                Object tag = ghost.getTag();
                if (tag instanceof java.lang.ref.WeakReference) {
                    Object root = ((java.lang.ref.WeakReference<?>) tag).get();
                    if (root instanceof ViewGroup) {
                        try { ((ViewGroup) root).removeView(ghost); } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        currentCb = null;
    }

    public boolean isShowing() {
        try { return popup.isShowing(); } catch (Throwable t) { return false; }
    }

    private int measureContentWidth() {
        // 用屏幕宽度的 60% 但不超过 320dp
        int maxPx = (int) (320 * ctx.getResources().getDisplayMetrics().density);
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int target = (int) (screenWidth * 0.6f);
        return Math.min(target, maxPx);
    }

    private static View makeGhostAnchor(@NonNull Context ctx) {
        // 1x1 透明 view,作为 popup 的 anchor 用来定位。
        View v = new View(ctx);
        v.setLayoutParams(new android.view.ViewGroup.LayoutParams(1, 1));
        v.setAlpha(0f);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    @Nullable
    private static android.app.Activity scanActivity(@NonNull View anchor) {
        try {
            android.content.Context c = anchor.getContext();
            while (c instanceof android.content.ContextWrapper) {
                if (c instanceof android.app.Activity) {
                    return (android.app.Activity) c;
                }
                c = ((android.content.ContextWrapper) c).getBaseContext();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
