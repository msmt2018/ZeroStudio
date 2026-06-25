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
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
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
}
