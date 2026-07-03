/*
 *  ZeroStudio IDE - 变量右键菜单 (Phase 20 重构)
 *
 *  第二类断点的入口: 在 Variables / Watches / Variables(Variables 本身) 面板
 *  右键某行,弹出菜单:
 *
 *    - Break on Modification  (Watchpoint / 修改时暂停)
 *    - Break on Access        (Watchpoint / 读取时暂停)
 *    - Filter by this instance (Instance Filter — 仅对 object 类型变量可见)
 *    - Set Value              (原有 PR-D6 功能,仅在非 final 字段可见)
 *    - Copy Value
 *    - Add to Watches         (PR-D3 已有)
 *
 *  每个选项对应 BreakpointTypeCatalog 的 1 个子类型,跳到 BreakpointDetailDialog。
 *  Watchpoint 需要提供"字段路径" (varName), Instance Filter 需要提供 objectId
 *  + 绑定行(默认当前 paused 帧的最新一个 LINE_BREAKPOINT)。
 *
 *  本类不持有 Dialog 引用,只负责"组装 + 启动",真正的 frosted glass UI
 *  在 BreakpointDetailDialog 中实现。
 */

package com.itsaky.androidide.debugger.view;

import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentActivity;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.DebugSessionState;
import com.itsaky.androidide.debugger.DebuggerController;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.debugger.model.BreakpointTypeCatalog;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
import com.zerostudio.debugger.api.VariableInfo;
import java.util.List;

public final class VariableContextMenu {

    private VariableContextMenu() {}

    /**
     * 弹出右键菜单 (在变量行 anchorView 旁)。
     *
     * @param activity  Activity 用于后续 dialog 启动
     * @param anchor    触发的 view (变量行)
     * @param variable  该行变量
     */
    public static void show(
            @NonNull FragmentActivity activity,
            @NonNull View anchor,
            @NonNull VariableInfo variable) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        PopupMenu menu = new PopupMenu(activity, anchor);
        // Phase 20: 简化策略 — 仅按"是否对象"显示。
        // 真实生产中: 还要看 declaringClass.isInstanceMember / isStaticField,
        // 但当前 VariableInfo 还没暴露 isField/isStaticField/objectId 字段,
        // 暂时按 slot tag 判断 (this=0, primitive=单 tag, object=tag 'L' 或 '[')
        boolean isObject = !variable.isPrimitive
                && !variable.typeSignature.equals("Ljava/lang/String;");
        // 字段 watch: 任何非 primitive 变量 (局部变量 / 字段 / 数组元素)
        // 真正的 JDDI FieldAccessWatchpoint 需要 declaringClass.fieldName,
        // 这里由用户在 BreakpointDetailDialog 的 element 输入框里填,
        // 我们在弹窗提示时把 type + name 作为预设值。
        if (!variable.isPrimitive) {
            menu.getMenu().add(0, R.id.debugger_var_ctx_watch_mod, 0,
                    R.string.debugger_var_ctx_watch_mod);
            menu.getMenu().add(0, R.id.debugger_var_ctx_watch_acc, 1,
                    R.string.debugger_var_ctx_watch_acc);
        }
        if (isObject) {
            menu.getMenu().add(0, R.id.debugger_var_ctx_instance_filter, 2,
                    R.string.debugger_var_ctx_instance_filter);
        }
        if (menu.getMenu().size() == 0) return;
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.debugger_var_ctx_watch_mod) {
                openWatchDialog(activity, variable, /* accessOnly= */ false);
                return true;
            }
            if (id == R.id.debugger_var_ctx_watch_acc) {
                openWatchDialog(activity, variable, /* accessOnly= */ true);
                return true;
            }
            if (id == R.id.debugger_var_ctx_instance_filter) {
                openInstanceFilterDialog(activity, variable);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private static void openWatchDialog(
            @NonNull FragmentActivity activity,
            @NonNull VariableInfo variable,
            boolean accessOnly) {
        // Watchpoint 的 entryPoint: GUTTER_LINE_CLICK 是错的 (那是普通行),
        // 这里我们要的是 Variables 类下的子项 (字段 watch)。
        // 我们直接传 BreakpointTypeCatalog.EntryPoint.VAR_FIELD_RIGHT_CLICK_MOD
        // 或 VAR_FIELD_RIGHT_CLICK_ACC。
        BreakpointTypeCatalog.Entry entry = accessOnly
                ? BreakpointTypeCatalog.ENTRY_WATCH_ACC
                : BreakpointTypeCatalog.ENTRY_WATCH_MOD;
        // elementName = "className.fieldName" (Java 反射 / JDI FieldAccessWatchpoint 需要)
        String elementName = encodeWatchpointElement(variable);
        // 显示 BreakpointDetailDialog,只填 element,其它配置默认 (watch-mod / watch-acc)
        BreakpointDetailDialog.showForNew(activity,
                /* file= */ guessContainingFile(),
                /* line= */ 1,
                entry,
                elementName,
                bp -> { /* 已通过 dialog 写入 */ });
    }

    private static void openInstanceFilterDialog(
            @NonNull FragmentActivity activity,
            @NonNull VariableInfo variable) {
        BreakpointTypeCatalog.Entry entry = BreakpointTypeCatalog.ENTRY_INSTANCE_FILTER;
        // elementName = "instanceId=@variableName" (variable 还没有 objectId 字段)
        // 真实生产中: 把当前栈帧的"this.objectId"绑定到行断点
        String idStr = "instanceId=@" + variable.name;
        String file = guessContainingFile();
        int line = 1;
        DebugSessionState st = DebuggerController.getInstance().sessionState();
        if (st.isSuspended() && st.currentFrame() != null) {
            com.zerostudio.debugger.api.StackFrameInfo frame = st.currentFrame();
            if (frame.sourceFile != null && !frame.sourceFile.isEmpty()) {
                file = frame.sourceFile;
                line = Math.max(1, frame.lineNumber);
            }
        }
        BreakpointDetailDialog.showForNew(activity, file, line, entry, idStr, bp -> {});
    }

    /** 把 VariableInfo 编码成 JDI 用的 "className.fieldName" 形式。 */
    @NonNull
    public static String encodeWatchpointElement(@NonNull VariableInfo v) {
        // 真实生产: 这里要从 Debugger.fetchSourceFile + FrameInfo 得到 declaringClass,
        // 然后与 v.name 拼接。本 Phase 20 提供占位实现,实际对接 ide-debugger
        // 帧的 declaringClass 字段。fallback: 留 rawName 让用户在 dialog 里改。
        String type = v.typeSignature;
        if (type.startsWith("L") && type.endsWith(";")) {
            type = type.substring(1, type.length() - 1);
        }
        return type + "." + v.name;
    }

    /** 当前调试器暂停的帧所在源文件 (best-effort)。 */
    @NonNull
    private static String guessContainingFile() {
        try {
            DebugSessionState st = DebuggerController.getInstance().sessionState();
            if (st.isSuspended() && st.currentFrame() != null) {
                com.zerostudio.debugger.api.StackFrameInfo frame = st.currentFrame();
                if (frame.sourceFile != null && !frame.sourceFile.isEmpty()) return frame.sourceFile;
            }
        } catch (Throwable ignored) {}
        // 找最近一个行断点所在文件
        List<IdeBreakpoint> all = BreakpointManager.getInstance().snapshot();
        for (IdeBreakpoint bp : all) {
            if (bp.file != null && !bp.file.isEmpty()) return bp.file;
        }
        return "Unknown.java";
    }
}
