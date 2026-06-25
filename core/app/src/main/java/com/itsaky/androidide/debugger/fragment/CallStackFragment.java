/*
 *  ZeroStudio IDE - 调用栈 Fragment
 *
 *  PR-4: 列出当前线程的所有栈帧，frame[0] 是当前暂停点。
 *  - 点击栈帧：调用 DebuggerController.selectFrame(frameId)，
 *    触发 VariablesFragment / WatchesFragment 重新加载。
 *  - 监听 DebugSessionState，调试器 suspend / resume 时自动刷新。
 *  PR-D7: ↑/↓ 键盘快捷键切换栈帧（focus on list）。
 */

package com.itsaky.androidide.debugger.fragment;

import android.os.Bundle;
import android.view.KeyEvent;
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
    /** PR-D7: 当前高亮项的位置(用于 ↑/↓ 导航)。 */
    private int selectedPosition = -1;

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
        // PR-D7: 让 RecyclerView 拿到焦点以接收硬件键盘事件
        list.setFocusable(true);
        list.setFocusableInTouchMode(true);
        list.setOnKeyListener(this::onListKey);

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
        // 选中当前栈帧(若存在)
        int newSel = -1;
        for (int i = 0; i < state.frames().size(); i++) {
            if (state.frames().get(i).frameId == state.currentFrameId()) {
                newSel = i;
                break;
            }
        }
        selectedPosition = newSel;
        if (emptyView != null && list != null) {
            boolean empty = state.frames().isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private void onFramePicked(@NonNull StackFrameInfo frame) {
        DebuggerController.getInstance().selectFrame(frame.frameId);
    }

    /**
     * PR-D7: ↑/↓ 切换栈帧。Enter/Space 等其它键让基类处理。
     */
    private boolean onListKey(@NonNull View v, int keyCode, @NonNull KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (lastState == null || lastState.frames().isEmpty()) return false;
        int size = lastState.frames().size();
        int cur = selectedPosition < 0 ? 0 : selectedPosition;
        int next = cur;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_NUMPAD_2) {
            next = Math.min(size - 1, cur + 1);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_NUMPAD_8) {
            next = Math.max(0, cur - 1);
        } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            next = Math.min(size - 1, cur + 5);
        } else if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            next = Math.max(0, cur - 5);
        } else if (keyCode == KeyEvent.KEYCODE_HOME) {
            next = 0;
        } else if (keyCode == KeyEvent.KEYCODE_MOVE_END) {
            next = size - 1;
        } else {
            return false;
        }
        if (next == cur) return true; // 已经到边界,吞掉
        selectedPosition = next;
        StackFrameInfo f = lastState.frames().get(next);
        if (f != null) {
            DebuggerController.getInstance().selectFrame(f.frameId);
        }
        return true;
    }
}
