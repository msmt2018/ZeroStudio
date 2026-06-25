/*
 *  ZeroStudio IDE - 调试器 Action 菜单
 *
 *  4 个一级子菜单 (submenu)：
 *    1. 运行控制  (▶️ 恢复, ⏸️ 暂停, ⏹️ 停止)
 *    2. 单步      (⤵️ StepOver, ⬇️ StepInto, ⤴️ StepOut, 👉 RunToCursor)
 *    3. 断点      (🪲 调试 [Attach / Detach], 启用全部, 禁用全部, 清空)
 *    4. 视图      (转到当前断点, 显示当前帧, 刷新, 添加监视)
 *
 *  使用 MenuProvider 模式（AndroidX Core 1.7+），菜单可同时显示在 Toolbar
 *  (toolbar) 和底部 ActionMode (bottom bar) 中。
 *
 *  本类只做“菜单到方法”的映射，真正的逻辑委托给 DebuggerController
 *  （由 PR-2 注入 com.zerostudio.debugger.api.Debugger）。
 *
 *  PR-4: 新增「刷新」与「添加监视」两个动作，对应侧边面板的 CallStackFragment
 *  / VariablesFragment / WatchesFragment。
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
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.DebuggerController;
import com.itsaky.androidide.utils.FlashbarActivityUtilsKt;

public class DebuggerActionMenuProvider implements MenuProvider {

    public interface Host {
        @NonNull Context requireContext();
        @Nullable com.itsaky.androidide.ui.CodeEditorView getCurrentEditor();
    }

    private final Host host;

    public DebuggerActionMenuProvider(@NonNull Host host) {
        this.host = host;
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // 运行控制
        SubMenu run = menu.addSubMenu(R.string.debugger_menu_run);
        run.add(0, R.id.dbg_action_resume, 0, R.string.debugger_action_resume)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        run.add(0, R.id.dbg_action_pause, 1, R.string.debugger_action_pause)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        run.add(0, R.id.dbg_action_stop, 2, R.string.debugger_action_stop)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // 单步
        SubMenu step = menu.addSubMenu(R.string.debugger_menu_step);
        step.add(0, R.id.dbg_action_step_over, 0, R.string.debugger_action_step_over)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        step.add(0, R.id.dbg_action_step_into, 1, R.string.debugger_action_step_into)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        step.add(0, R.id.dbg_action_step_out, 2, R.string.debugger_action_step_out)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        step.add(0, R.id.dbg_action_run_to_cursor, 3, R.string.debugger_action_run_to_cursor)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        // 断点
        SubMenu bp = menu.addSubMenu(R.string.debugger_menu_breakpoints);
        bp.add(0, R.id.dbg_action_debug_toggle, 0, R.string.debugger_action_debug_toggle)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        bp.add(0, R.id.dbg_action_bp_enable_all, 1, R.string.debugger_action_bp_enable_all)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        bp.add(0, R.id.dbg_action_bp_disable_all, 2, R.string.debugger_action_bp_disable_all)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        bp.add(0, R.id.dbg_action_bp_clear, 3, R.string.debugger_action_bp_clear)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // 视图
        SubMenu view = menu.addSubMenu(R.string.debugger_menu_view);
        view.add(0, R.id.dbg_action_goto_current_bp, 0, R.string.debugger_action_goto_current_bp)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        view.add(0, R.id.dbg_action_goto_exception, 1, R.string.debugger_action_goto_exception)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        view.add(0, R.id.dbg_action_show_current_frame, 2, R.string.debugger_action_show_current_frame)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        // PR-4: 刷新当前帧 / 打开添加监视对话框
        view.add(0, R.id.dbg_action_refresh, 3, R.string.debugger_action_refresh)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        view.add(0, R.id.dbg_action_add_watch, 4, R.string.debugger_action_add_watch)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        // PR-D7: 跳转到异常源。当目标线程在异常处暂停时,会显示该位置。
        view.add(0, R.id.dbg_action_goto_exception, 4, R.string.debugger_action_goto_exception)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        DebuggerController ctl = DebuggerController.getInstance();
        int id = item.getItemId();
        if (id == R.id.dbg_action_resume) {
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
            // `flashInfo` is a Kotlin top-level function bound to `Activity`.
            // From Java we can't import it directly, and the extension receiver
            // type is `Activity` (not `Context`), so cast the host's context
            // to Activity before calling. The host is always an editor
            // activity, so the cast is safe at runtime.
            final android.app.Activity act = (android.app.Activity) host.requireContext();
            FlashbarActivityUtilsKt.flashInfo(act, act.getString(R.string.debugger_action_refresh));
            return true;
        } else if (id == R.id.dbg_action_add_watch) {
            ctl.promptAddWatch();
            return true;
        } else if (id == R.id.dbg_action_goto_exception) {
            ctl.gotoException();
            return true;
        }
        return false;
    }
}
