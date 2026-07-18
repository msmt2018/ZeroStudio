/*
 *  ZeroStudio IDE - 调试器 Action 菜单
 *
 *  统一收拢到一个「断点调试器」(debugger_menu_root) 总入口 SubMenu 下,
 *  其内嵌套 4 个二级子菜单 + 1 个独立动作:
 *
 *    断点调试器
 *      ├── 按行号添加断点…  (独立动作, 弹输入框 -> 选断点类型 -> 应用)
 *      ├── 运行控制  (▶️ 恢复, ⏸️ 暂停, ⏹️ 停止)
 *      ├── 单步      (⤵️ StepOver, ⬇️ StepInto, ⤴️ StepOut, 👉 RunToCursor)
 *      ├── 断点      (🪲 调试 [Attach / Detach], 启用全部, 禁用全部, 清空)
 *      └── 视图      (转到当前断点, 转到异常, 显示当前帧, 刷新, 添加监视)
 *
 *  使用 MenuProvider 模式（AndroidX Core 1.7+），菜单可同时显示在 Toolbar
 *  (toolbar) 和底部 ActionMode (bottom bar) 中。
 *
 *  本类只做"菜单到方法"的映射，真正的逻辑委托给 DebuggerController
 *  （由 PR-2 注入 com.zerostudio.debugger.api.Debugger）。
 */

package com.itsaky.androidide.debugger.menu;

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentActivity;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.DebuggerController;
import com.itsaky.androidide.debugger.view.AddBreakpointByLineDialog;
import com.itsaky.androidide.ui.CodeEditorView;
import com.itsaky.androidide.utils.FlashbarActivityUtilsKt;
import java.io.File;

public class DebuggerActionMenuProvider implements MenuProvider {

    public interface Host {
        @NonNull Context requireContext();
        @Nullable CodeEditorView getCurrentEditor();
    }

    private final Host host;

    public DebuggerActionMenuProvider(@NonNull Host host) {
        this.host = host;
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // === 总入口: 「断点调试器」 ===
        // 4 个二级子菜单 + 1 个独立动作全部嵌套在这个根 SubMenu 下,
        // 避免 toolbar 被调试器菜单占满.
        SubMenu root = menu.addSubMenu(R.string.debugger_menu_root);
        root.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        // 0. 独立动作: 按行号添加断点 (在子菜单之前, 作为快捷入口)
        root.add(0, R.id.dbg_action_add_bp_by_line, 0, R.string.debugger_action_add_bp_by_line)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // 分隔 (用一个空标题的 group 占位不可靠, 这里靠 order 隔开即可)

        // 1. 运行控制
        SubMenu run = root.addSubMenu(R.string.debugger_menu_run);
        run.add(0, R.id.dbg_action_resume, 0, R.string.debugger_action_resume)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        run.add(0, R.id.dbg_action_pause, 1, R.string.debugger_action_pause)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        run.add(0, R.id.dbg_action_stop, 2, R.string.debugger_action_stop)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // 2. 单步
        SubMenu step = root.addSubMenu(R.string.debugger_menu_step);
        step.add(0, R.id.dbg_action_step_over, 0, R.string.debugger_action_step_over)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        step.add(0, R.id.dbg_action_step_into, 1, R.string.debugger_action_step_into)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        step.add(0, R.id.dbg_action_step_out, 2, R.string.debugger_action_step_out)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        step.add(0, R.id.dbg_action_run_to_cursor, 3, R.string.debugger_action_run_to_cursor)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // 3. 断点
        SubMenu bp = root.addSubMenu(R.string.debugger_menu_breakpoints);
        bp.add(0, R.id.dbg_action_debug_toggle, 0, R.string.debugger_action_debug_toggle)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        bp.add(0, R.id.dbg_action_bp_enable_all, 1, R.string.debugger_action_bp_enable_all)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        bp.add(0, R.id.dbg_action_bp_disable_all, 2, R.string.debugger_action_bp_disable_all)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        bp.add(0, R.id.dbg_action_bp_clear, 3, R.string.debugger_action_bp_clear)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // 4. 视图
        SubMenu view = root.addSubMenu(R.string.debugger_menu_view);
        view.add(0, R.id.dbg_action_goto_current_bp, 0, R.string.debugger_action_goto_current_bp)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        view.add(0, R.id.dbg_action_goto_exception, 1, R.string.debugger_action_goto_exception)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        view.add(0, R.id.dbg_action_show_current_frame, 2, R.string.debugger_action_show_current_frame)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        view.add(0, R.id.dbg_action_refresh, 3, R.string.debugger_action_refresh)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        view.add(0, R.id.dbg_action_add_watch, 4, R.string.debugger_action_add_watch)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        DebuggerController ctl = DebuggerController.getInstance();
        int id = item.getItemId();
        if (id == R.id.dbg_action_add_bp_by_line) {
            showAddBreakpointByLineDialog();
            return true;
        } else if (id == R.id.dbg_action_resume) {
            ctl.resume();
            return true;
        } else if (id == R.id.dbg_action_pause) {
            ctl.pause();
            return true;
        } else if (id == R.id.dbg_action_stop) {
            ctl.stop();
            return true;
        } else if (id == R.id.dbg_action_step_over) {
            ctl.stepOver();
            return true;
        } else if (id == R.id.dbg_action_step_into) {
            ctl.stepInto();
            return true;
        } else if (id == R.id.dbg_action_step_out) {
            ctl.stepOut();
            return true;
        } else if (id == R.id.dbg_action_run_to_cursor) {
            ctl.runToCursor();
            return true;
        } else if (id == R.id.dbg_action_debug_toggle) {
            ctl.toggleDebugConnection();
            return true;
        } else if (id == R.id.dbg_action_bp_enable_all) {
            com.itsaky.androidide.debugger.model.BreakpointManager.getInstance().enableAll();
            return true;
        } else if (id == R.id.dbg_action_bp_disable_all) {
            com.itsaky.androidide.debugger.model.BreakpointManager.getInstance().disableAll();
            return true;
        } else if (id == R.id.dbg_action_bp_clear) {
            com.itsaky.androidide.debugger.model.BreakpointManager.getInstance().clear();
            return true;
        } else if (id == R.id.dbg_action_goto_current_bp) {
            ctl.gotoCurrentBreakpoint();
            return true;
        } else if (id == R.id.dbg_action_goto_exception) {
            ctl.gotoException();
            return true;
        } else if (id == R.id.dbg_action_show_current_frame) {
            ctl.showCurrentFrame();
            return true;
        } else if (id == R.id.dbg_action_refresh) {
            // 通知监听器重新加载（state 自身未变，fire 一次以触发刷新）
            ctl.sessionState().selectFrame(ctl.sessionState().currentFrameId());
            final android.app.Activity act = (android.app.Activity) host.requireContext();
            FlashbarActivityUtilsKt.flashInfo(act, act.getString(R.string.debugger_action_refresh));
            return true;
        } else if (id == R.id.dbg_action_add_watch) {
            ctl.promptAddWatch();
            return true;
        }
        return false;
    }

    /**
     * 弹出「按行号添加断点」输入弹窗. 使用当前编辑器打开的文件路径作为
     * 目标文件; 如果没有打开的文件, 弹窗内部会提示用户.
     */
    private void showAddBreakpointByLineDialog() {
        Context ctx = host.requireContext();
        if (!(ctx instanceof FragmentActivity)) {
            return;
        }
        String filePath = null;
        CodeEditorView editor = host.getCurrentEditor();
        if (editor != null) {
            File f = editor.getFile();
            if (f != null) {
                filePath = f.getAbsolutePath();
            }
        }
        AddBreakpointByLineDialog.show((FragmentActivity) ctx, filePath);
    }
}
