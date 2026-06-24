/*
 *  ZeroStudio IDE - 调用栈 Fragment
 *
 *  PR-4: 列出当前线程的所有栈帧，frame[0] 是当前暂停点。
 *  - 点击栈帧：调用 DebuggerController.selectFrame(frameId)，
 *    触发 VariablesFragment / WatchesFragment 重新加载。
 *  - 监听 DebugSessionState，调试器 suspend / resume 时自动刷新。
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
import com.itsaky.androidide.debugger.adapter.CallStackAdapter;
import com.zerostudio.debugger.api.StackFrameInfo;

public class CallStackFragment extends Fragment
        implements DebugSessionState.Listener {

    private RecyclerView list;
    private View emptyView;
    private CallStackAdapter adapter;
    private DebugSessionState lastState;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_callstack, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.cs_list);
        emptyView = view.findViewById(R.id.cs_empty);

        adapter = new CallStackAdapter();
        adapter.setListener(this::onFramePicked);
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
        lastState = state;
        if (adapter == null) return;
        adapter.submit(state.frames(), state.currentFrameId());
        if (emptyView != null && list != null) {
            boolean empty = state.frames().isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private void onFramePicked(@NonNull StackFrameInfo frame) {
        DebuggerController.getInstance().selectFrame(frame.frameId);
    }
}
