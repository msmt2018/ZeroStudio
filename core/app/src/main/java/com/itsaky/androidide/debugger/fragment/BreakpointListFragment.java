/*
 *  ZeroStudio IDE - 断点列表 Fragment
 *
 *  集成到 EditorBottomSheet，作为 "断点" Tab 显示。
 *  - 顶部工具栏：清空、全部启用、全部禁用
 *  - 列表：所有文件的所有断点（按文件分组）
 *  - 点击：跳转到断点所在行
 *  - 长按：弹出 PopupMenu（编辑条件 / 切换启用 / 删除）
 */

package com.itsaky.androidide.debugger.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.itsaky.androidide.R;
import com.itsaky.androidide.activities.editor.BaseEditorActivity;
import com.itsaky.androidide.debugger.BreakpointConditionDialog;
import com.itsaky.androidide.debugger.adapter.BreakpointListAdapter;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.debugger.model.BreakpointTypeCatalog;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
import com.itsaky.androidide.debugger.view.BreakpointDetailDialog;
import com.itsaky.androidide.ui.CodeEditorView;
import com.itsaky.androidide.utils.FlashbarActivityUtilsKt;
import java.util.List;

public class BreakpointListFragment extends Fragment
        implements BreakpointManager.Listener {

    private RecyclerView list;
    private BreakpointListAdapter adapter;
    private View emptyView;
    private View clearAll;
    private View enableAll;
    private View disableAll;
    private View addSpecial;   // Phase 20: 顶栏 "+" 按钮 — 第三类断点 (Exception / Symbolic / Dependent)

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_breakpoint_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.bp_list);
        emptyView = view.findViewById(R.id.bp_empty);
        clearAll = view.findViewById(R.id.bp_action_clear);
        enableAll = view.findViewById(R.id.bp_action_enable_all);
        disableAll = view.findViewById(R.id.bp_action_disable_all);
        addSpecial = view.findViewById(R.id.bp_action_add_special);

        adapter = new BreakpointListAdapter();
        adapter.setListener(new BreakpointListAdapter.Listener() {
            @Override
            public void onItemClick(@NonNull IdeBreakpoint bp) {
                navigateToBreakpoint(bp);
            }

            @Override
            public void onItemLongClick(@NonNull IdeBreakpoint bp) {
                showItemMenu(bp);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        if (clearAll != null) clearAll.setOnClickListener(v ->
                BreakpointManager.getInstance().clear());
        if (enableAll != null) enableAll.setOnClickListener(v ->
                BreakpointManager.getInstance().enableAll());
        if (disableAll != null) disableAll.setOnClickListener(v ->
                BreakpointManager.getInstance().disableAll());
        if (addSpecial != null) addSpecial.setOnClickListener(v -> showAddSpecialMenu());

        BreakpointManager.getInstance().addListener(this);
        onBreakpointsChanged(BreakpointManager.getInstance().snapshot());
    }

    @Override
    public void onDestroyView() {
        BreakpointManager.getInstance().removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onBreakpointsChanged(@NonNull List<IdeBreakpoint> all) {
        if (adapter != null) adapter.submit(all);
        if (emptyView != null && list != null) {
            boolean empty = all.isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onBreakpointStateChanged(@NonNull IdeBreakpoint bp) {
        onBreakpointsChanged(BreakpointManager.getInstance().snapshot());
    }

    private void showItemMenu(@NonNull IdeBreakpoint bp) {
        View anchor = requireView();
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        MenuInflater inflater = menu.getMenuInflater();
        inflater.inflate(R.menu.menu_breakpoint_item, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_bp_toggle) {
                BreakpointManager.getInstance().setEnabled(bp.id,
                        bp.state == IdeBreakpoint.State.DISABLED);
                return true;
            }
            if (id == R.id.menu_bp_edit_condition
                    || id == R.id.menu_bp_edit_log) {
                // PR-E2: 统一合并到 BreakpointConditionDialog,
                // 在对话框内通过 "类型" 单选切换条件/日志。
                BreakpointConditionDialog.showDialog(getChildFragmentManager(), bp.id);
                return true;
            }
            if (id == R.id.menu_bp_delete) {
                BreakpointManager.getInstance().remove(bp.id);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void navigateToBreakpoint(@NonNull IdeBreakpoint bp) {
        if (!(requireActivity() instanceof BaseEditorActivity)) {
            FlashbarActivityUtilsKt.flashInfo(requireActivity(), "无法跳转：当前页面非编辑器");
            return;
        }
        BaseEditorActivity activity = (BaseEditorActivity) requireActivity();
        com.itsaky.androidide.models.Range range =
                new com.itsaky.androidide.models.Range(
                        new com.itsaky.androidide.models.Position(
                                bp.line - 1, 0),
                        new com.itsaky.androidide.models.Position(
                                bp.line - 1, 0));
        activity.openFileAndSelect(new java.io.File(bp.file), range);
    }

    /**
     * Phase 20: 顶栏 "+" → 弹第三类断点菜单 (Exception / Symbolic / Dependent)。
     * 选择后通过 BreakpointDetailDialog (高斯模糊磨砂) 走标准流程。
     */
    private void showAddSpecialMenu() {
        View anchor = addSpecial != null ? addSpecial : requireView();
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.debugger_bp_add_exception);
        menu.getMenu().add(0, 2, 1, R.string.debugger_bp_add_symbolic);
        menu.getMenu().add(0, 3, 2, R.string.debugger_bp_add_dependent);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                // 异常断点 — 必须 element = 异常类名,故用 showForNew + preset element=null
                BreakpointDetailDialog.showForNew(
                        requireActivity(),
                        "Global", 1,
                        BreakpointTypeCatalog.ENTRY_EXCEPTION,
                        null, null);
                return true;
            }
            if (id == 2) {
                BreakpointDetailDialog.showForNew(
                        requireActivity(),
                        "Global", 1,
                        BreakpointTypeCatalog.ENTRY_SYMBOLIC,
                        null, null);
                return true;
            }
            if (id == 3) {
                // 依赖断点 — 用户需要先有一个行断点,这里引导"选择一个已存在的"
                List<IdeBreakpoint> existing = BreakpointManager.getInstance().snapshot();
                if (existing.isEmpty()) {
                    FlashbarActivityUtilsKt.flashInfo(requireActivity(),
                            getString(R.string.debugger_bpd_dependent_no_existing));
                    return true;
                }
                // 简化: 直接打开详情 dialog 选第一个作为主断点
                IdeBreakpoint first = existing.get(0);
                BreakpointDetailDialog.showForNew(
                        requireActivity(),
                        first.file, first.line,
                        BreakpointTypeCatalog.ENTRY_DEPENDENT,
                        first.id, null);
                return true;
            }
            return false;
        });
        menu.show();
    }
}
