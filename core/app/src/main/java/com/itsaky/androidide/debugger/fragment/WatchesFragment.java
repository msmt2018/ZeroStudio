/*
 *  ZeroStudio IDE - 监视表达式 Fragment
 *
 *  PR-4: 列表展示用户添加的表达式。程序暂停时尝试通过 EvalEngine
 *  对每个表达式求值；本机 stub 实现里 EvalEngine.getFrameVariables
 *  是空，所以这里用最简单的方式：缓存原始表达式 + 「无法求值」占位。
 *  等 PR-5/6 接通表达式求值后，只需要替换 updateValues() 内部实现。
 *
 *  顶部 + 按钮：弹窗输入表达式；
 *  顶部 清空：一键删除全部；
 *  item 长按：删除单个。
 */

package com.itsaky.androidide.debugger.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.DebugSessionState;
import com.itsaky.androidide.debugger.DebuggerController;
import com.itsaky.androidide.debugger.adapter.WatchesAdapter;
import com.itsaky.androidide.debugger.model.WatchStore;

public class WatchesFragment extends Fragment
        implements DebugSessionState.Listener {

    private RecyclerView list;
    private View emptyView;
    private WatchesAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_watches, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.watches_list);
        emptyView = view.findViewById(R.id.watches_empty);

        adapter = new WatchesAdapter();
        adapter.setListener(new WatchesAdapter.Listener() {
            @Override
            public void onItemLongClick(int position, @NonNull String expr) {
                WatchStore.getInstance().remove(position);
                refresh();
            }
        });
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        view.findViewById(R.id.watches_add).setOnClickListener(v -> showAddDialog());
        view.findViewById(R.id.watches_clear).setOnClickListener(v -> {
            WatchStore.getInstance().clear();
            refresh();
        });

        DebuggerController.getInstance().sessionState().addListener(this);
        refresh();
    }

    @Override
    public void onDestroyView() {
        DebuggerController.getInstance().sessionState().removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onStateChanged(@NonNull DebugSessionState state) {
        // PR-5 之前：占位为 "..." 或 "无法求值"。
        adapter.markAll(state.isSuspended()
                ? getString(R.string.debugger_watches_value_pending)
                : getString(R.string.debugger_watches_value_pending));
    }

    private void refresh() {
        adapter.submit(WatchStore.getInstance().all());
        boolean empty = adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showAddDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.debugger_watches_dialog_hint);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.debugger_watches_dialog_title)
                .setView(input)
                .setPositiveButton(R.string.debugger_watches_add, (d, w) -> {
                    String expr = input.getText().toString().trim();
                    if (!expr.isEmpty()) {
                        WatchStore.getInstance().add(expr);
                        refresh();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
