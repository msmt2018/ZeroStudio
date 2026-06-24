/*
 *  ZeroStudio IDE - 变量 Fragment
 *
 *  PR-4: 显示当前栈帧的所有局部变量与 'this'。
 *  切换栈帧 / 程序恢复时自动刷新。
 *
 *  数据来源：StackFrameInfo.variables 字段；
 *  若引擎返回空，则回退到 EvalEngine.getFrameVariable 单独取（保留扩展点）。
 */

package com.itsaky.androidide.debugger.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.DebugSessionState;
import com.itsaky.androidide.debugger.DebuggerController;
import com.itsaky.androidide.debugger.adapter.VariablesAdapter;
import com.zerostudio.debugger.api.StackFrameInfo;
import com.zerostudio.debugger.api.VariableInfo;
import java.util.Collections;
import java.util.List;

public class VariablesFragment extends Fragment
        implements DebugSessionState.Listener {

    private RecyclerView list;
    private View emptyView;
    private View loadingView;
    private VariablesAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_variables, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.vars_list);
        emptyView = view.findViewById(R.id.vars_empty);
        loadingView = view.findViewById(R.id.vars_loading);

        adapter = new VariablesAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        DebuggerController.getInstance().sessionState().addListener(this);
        onStateChanged(DebuggerController.getInstance().sessionState());
    }

    @Override
    public void onDestroyView() {
        DebuggerController.getInstance().sessionState().removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onStateChanged(@NonNull DebugSessionState state) {
        if (adapter == null) return;
        if (!state.isSuspended()) {
            adapter.submit(Collections.emptyList());
            showEmpty(true, R.string.debugger_variables_empty);
            return;
        }
        StackFrameInfo frame = state.currentFrame();
        if (frame == null) {
            adapter.submit(Collections.emptyList());
            showEmpty(true, R.string.debugger_variables_empty);
            return;
        }
        List<VariableInfo> vars = frame.variables == null
                ? Collections.emptyList()
                : frame.variables;
        adapter.submit(vars);
        showEmpty(vars.isEmpty(), R.string.debugger_variables_empty);
    }

    private void showEmpty(boolean empty, int msgRes) {
        if (emptyView != null) {
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) emptyView.setText(msgRes);
        }
        if (loadingView != null) loadingView.setVisibility(View.GONE);
        if (list != null) list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
