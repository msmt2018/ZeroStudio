/*
 *  ZeroStudio IDE - 断点类型选择小弹窗
 *
 *  用户点击行号 gutter 时,弹出一个 ListPopupWindow,列出 3 种断点类型:
 *    1. 普通断点 (Line breakpoint)
 *    2. 条件断点 (Conditional — 命中条件时才暂停)
 *    3. 日志点 (Logpoint — 命中时只打印日志,不暂停)
 *
 *  之前版本 (PR-2) 实现了 BreakpointGutterManager 但从未在 EditorHandlerActivity
 *  中 attach 到 CodeEditor,也从未注册 OnBreakpointActionListener,所以点击
 *  gutter 没有反应。PR-D4 在 openFileAndGetIndex() 中 attach,并通过本类
 *  把 OnBreakpointActionListener 接到类型选择 popup,让用户点击行号后可以
 *  实际选择断点类型;ListPopupWindow 选完后 dismiss 自身,符合 Android 弹窗
 *  的标准交互。
 *
 *  与 [BreakpointConditionDialog] 的关系:
 *    - 本类负责"先选类型" (在添加断点之前)
 *    - BreakpointConditionDialog 负责"再配条件" (选完条件断点后才弹出)
 *    - 日志点 (Logpoint) 直接走 BreakpointManager.addLogpoint(...)
 *    - 普通断点直接走 BreakpointManager.toggleAt(file, line)
 *
 *  @author android_zero
 */
package com.itsaky.androidide.debugger.view;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.BreakpointConditionDialog;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.utils.FlashbarActivityUtilsKt;

/**
 * Helper for the "select breakpoint type" popup.
 *
 * <p>All public methods are static and side-effect-free apart from the popup
 * itself, so the host activity can invoke it from a gutter click handler
 * without holding any per-instance state.
 */
public final class BreakpointTypePicker {

    /**
     * The three breakpoint types the user can pick from the popup.
     * Index order matches the labels in {@link #LABELS}.
     */
    public enum Kind {
        /** Plain line breakpoint — suspends the VM on hit. */
        LINE,
        /** Conditional breakpoint — suspends only when an expression evaluates to true. */
        CONDITIONAL,
        /** Logpoint — prints a message but does not suspend. */
        LOGPOINT
    }

    private static final String[] LABELS = new String[] {
            "Line breakpoint",
            "Conditional breakpoint",
            "Logpoint"
    };

    private BreakpointTypePicker() {
        // no instances
    }

    /**
     * Show the type-selection popup anchored to {@code anchor}.
     *
     * <p>After the user taps an item, the popup dismisses itself and the
     * corresponding {@link BreakpointManager} call is dispatched:
     * <ul>
     *   <li>{@link Kind#LINE}        → {@code BreakpointManager.toggle(file, line)}</li>
     *   <li>{@link Kind#CONDITIONAL} → {@code BreakpointConditionDialog.showDialog(fm, bpId)}</li>
     *   <li>{@link Kind#LOGPOINT}    → {@code BreakpointManager.addLogpoint(file, line, "")}</li>
     * </ul>
     *
     * <p>If the user taps outside the popup, the popup dismisses itself
     * and <em>no</em> breakpoint is added. This is the standard Android
     * ListPopupWindow contract — no extra logic is needed on the dismiss
     * path, which is what fixes the original "popup won't close" bug:
     * ListPopupWindow dismisses correctly out of the box; the bug was
     * that the gutter click handler was never wired up to call this
     * method, so the popup never even opened.
     *
     * @param activity used as the {@link FragmentActivity} for the
     *                condition dialog and for resolving the host
     *                {@link Context}. Must not be {@code null}.
     * @param anchor   view the popup is anchored to (typically the row in
     *                 the gutter that was tapped). Must not be {@code null}.
     * @param file     absolute path of the file the breakpoint belongs to.
     * @param line     1-based line number.
     */
    public static void show(@NonNull FragmentActivity activity,
                            @NonNull View anchor,
                            @NonNull String file,
                            int line) {
        showInternal(activity, anchor, file, line, /*anchorOnLine=*/ false);
    }

    /**
     * PR-D4: 增强重载 —— 接受屏幕坐标 (anchorX, anchorY) 并把弹窗定位
     * 到那一行,而不是整个编辑器顶部。
     *
     * <p>实现策略:ListPopupWindow 只能锚定到某个 View,所以先在
     * {@code parent} 视图树里放一个不可见的 1x1 "ghost" View,把它
     * 摆到目标位置,再锚定到它。弹窗关闭后通过 OnDismissListener 移除
     * 该 ghost,避免持续占用 View 树。
     */
    public static void showAtPosition(@NonNull FragmentActivity activity,
                                      @NonNull View parent,
                                      int anchorX, int anchorY,
                                      @NonNull String file,
                                      int line) {
        final View ghost = new View(parent.getContext());
        ghost.setLayoutParams(new android.view.ViewGroup.LayoutParams(1, 1));
        if (parent instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) parent).addView(ghost);
        }
        ghost.setX(anchorX);
        ghost.setY(anchorY);
        showInternal(activity, ghost, file, line, /*anchorOnLine=*/ true);
    }

    private static void showInternal(@NonNull FragmentActivity activity,
                                     @NonNull View anchor,
                                     @NonNull String file,
                                     int line,
                                     boolean anchorOnLine) {
        final ListPopupWindow popup = new ListPopupWindow(activity);
        popup.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_list_item_1, LABELS));
        popup.setAnchorView(anchor);
        popup.setModal(true);
        // Width: 280dp is comfortable for the three labels we use.
        popup.setContentWidth((int) (280 * activity.getResources()
                .getDisplayMetrics().density));
        popup.setOnItemClickListener((parent, view, position, id) -> {
            final Kind kind = Kind.values()[position];
            final BreakpointManager mgr = BreakpointManager.getInstance();
            switch (kind) {
                case LINE:
                    mgr.toggle(file, line);
                    break;
                case CONDITIONAL:
                    // Add a plain breakpoint first so the user can edit
                    // its condition via the dialog (which expects an
                    // existing breakpoint id).
                    mgr.toggle(file, line);
                    final String bpId = mgr.idAt(file, line);
                    if (bpId != null) {
                        BreakpointConditionDialog.showDialog(
                                activity.getSupportFragmentManager(), bpId);
                    }
                    break;
                case LOGPOINT:
                    mgr.addLogpoint(file, line, "");
                    flashInfo(activity, R.string.debugger_msg_logpoint_added);
                    break;
            }
            popup.dismiss();
        });
        // On dismiss (item picked OR outside tap), do nothing — the popup
        // is already gone and we have either dispatched the action above
        // (item picked) or chosen to add no breakpoint (outside tap).
        popup.setOnDismissListener(() -> {
            // 如果是 anchorOnLine 模式,弹窗关闭后立即把 ghost anchor 移除,
            // 避免它持续占据编辑器的 View 树。
            if (anchorOnLine
                    && anchor.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) anchor.getParent()).removeView(anchor);
            }
        });
        popup.show();
    }

    private static void flashInfo(@NonNull android.app.Activity activity, int resId) {
        FlashbarActivityUtilsKt.flashInfo(activity, activity.getString(resId));
    }
}
