/*
 *  ZeroStudio IDE - 日志点输出 Fragment (PR-6)
 *
 *  显示 LogStore 中累积的日志条目。来自 ide-debugger 的
 *  DebugEventBus 推送通过 DebuggerController 转写到 LogStore;
 *  本 Fragment 直接订阅 LogStore 来更新 RecyclerView。
 *
 *  - 顶部 "清空" 按钮清空 LogStore。
 *  - "自动滚动" CheckBox 决定新增条目时是否滚动到底。
 */

package com.itsaky.androidide.debugger.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.adapter.LogpointAdapter;
import com.itsaky.androidide.debugger.model.LogStore;

public class LogpointFragment extends Fragment implements LogStore.Listener {

    private RecyclerView list;
    private View emptyView;
    private LogpointAdapter adapter;
    private CheckBox autoScroll;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_logpoint, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.log_list);
        emptyView = view.findViewById(R.id.log_empty);
        autoScroll = view.findViewById(R.id.log_autoscroll);
        Button clear = view.findViewById(R.id.log_clear);

        adapter = new LogpointAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        clear.setOnClickListener(v -> LogStore.getInstance().clear());

        LogStore.getInstance().addListener(this);
        // Restore previous entries
        adapter.submit(LogStore.getInstance().snapshot());
        updateEmpty();
    }

    @Override
    public void onDestroyView() {
        LogStore.getInstance().removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onLogAppended(@NonNull LogStore.Entry entry) {
        if (!isAdded() || adapter == null) return;
        adapter.append(entry);
        updateEmpty();
        if (autoScroll != null && autoScroll.isChecked() && list != null) {
            list.post(() -> {
                if (list == null) return;
                int last = adapter.getItemCount() - 1;
                if (last >= 0) list.smoothScrollToPosition(last);
            });
        }
    }

    @Override
    public void onLogCleared() {
        if (!isAdded() || adapter == null) return;
        adapter.clear();
        updateEmpty();
    }

    private void updateEmpty() {
        boolean empty = adapter == null || adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
