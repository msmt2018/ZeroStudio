/*
 *  ZeroStudio IDE - 变量 Fragment
 *
 *  PR-4: 显示当前栈帧的所有局部变量与 'this'。
 *  切换栈帧 / 程序恢复时自动刷新。
 *
 *  数据来源：StackFrameInfo.variables 字段；
 *  若引擎返回空，则回退到 EvalEngine.getFrameVariable 单独取（保留扩展点）。
 *
 *  PR-D6: 长按变量行 -> "set value" 对话框。
 *  用 Debugger.setLocalValueAsync 写入 JDWP (实际是 EvalEngine.setLocal)。
 *  仅对 primitive + String 支持;object field 在 PR-D7 做。
 */

package com.itsaky.androidide.debugger.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.DebugSessionState;
import com.itsaky.androidide.debugger.DebuggerController;
import com.itsaky.androidide.debugger.adapter.VariablesAdapter;
import com.itsaky.androidide.utils.ILogger;
import com.itsaky.androidide.utils.flashInfo;
import com.zerostudio.debugger.api.StackFrameInfo;
import com.zerostudio.debugger.api.VariableInfo;
import java.util.Collections;
import java.util.List;

public class VariablesFragment extends Fragment
        implements DebugSessionState.Listener {

    private static final String TAG = "VariablesFragment";

    private RecyclerView list;
    private TextView emptyView;
    private TextView loadingView;
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
        // PR-D6: 长按 -> 弹 "set value" 对话框 (仅 primitive + String)。
        adapter.setListener(v -> showSetValueDialog(v));
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

    /**
     * PR-D6: "设置值" 对话框。仅对 primitive / String 工作,其它类型显示提示。
     */
    private void showSetValueDialog(@NonNull VariableInfo v) {
        if (!v.isPrimitive && !v.typeSignature.equals("Ljava/lang/String;")) {
            requireActivity().flashInfo(
                    getString(R.string.debugger_var_set_value_error,
                            "暂仅支持 primitive 与 String"));
            return;
        }
        // 用 View 包裹 EditText 让 padding 正常
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.debugger_var_set_value_hint);
        input.setText(v.value == null ? "" : stripQuotes(v.value));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.debugger_var_set_value_title, v.name))
                .setView(container)
                .setPositiveButton(R.string.debugger_var_set_value_btn_set, (d, w) -> {
                    String newValue = input.getText().toString().trim();
                    if (newValue.isEmpty()) return;
                    commitSetValue(v, newValue);
                })
                .setNegativeButton(R.string.debugger_var_set_value_btn_cancel, null)
                .show();
    }

    private void commitSetValue(@NonNull VariableInfo v, @NonNull String newValue) {
        com.zerostudio.debugger.api.Debugger dbg =
                DebuggerController.getInstance().debugger();
        if (dbg == null) {
            requireActivity().flashInfo(
                    getString(R.string.debugger_var_set_value_error, "未连接调试器"));
            return;
        }
        DebugSessionState st = DebuggerController.getInstance().sessionState();
        if (!st.isSuspended()) {
            requireActivity().flashInfo(
                    getString(R.string.debugger_var_set_value_error, "程序未暂停"));
            return;
        }
        long threadId = st.pausedThreadId();
        StackFrameInfo frame = st.currentFrame();
        if (frame == null) {
            requireActivity().flashInfo(
                    getString(R.string.debugger_var_set_value_error, "无可用栈帧"));
            return;
        }
        // String 类型:setter 需要的是 string 内容 (去引号),JDWP
        // StackFrame.SetValues 对 'L' (对象) 需要 object id。
        // 我们走 EvalEngine.setLocal 路径:它对 'L' 走 Long.parseLong,
        // 所以这里对 String 不支持 (新值没法作为 object id 传入)。
        // 真正写 String 应该是先 CreateString 再 SetValues;留给 PR-D7。
        if (v.typeSignature.equals("Ljava/lang/String;")) {
            requireActivity().flashInfo(
                    getString(R.string.debugger_var_set_value_error,
                            "String set value 待 PR-D7 (需先 CreateString)"));
            return;
        }
        dbg.setLocalValueAsync(threadId, frame.frameId, v.slot, v.typeSignature,
                newValue, result -> {
                    // setLocalValueAsync 回调在 bg executor 线程,
                    // 切到主线程做 UI 更新。
                    android.os.Handler h = new android.os.Handler(
                            android.os.Looper.getMainLooper());
                    h.post(() -> {
                        if (result.isError()) {
                            ILogger.warn(TAG, "setLocal failed: " + result.error);
                            requireActivity().flashInfo(
                                    getString(R.string.debugger_var_set_value_error,
                                            result.error));
                            return;
                        }
                        requireActivity().flashInfo(
                                getString(R.string.debugger_var_set_value_ok,
                                        v.name, newValue));
                        // 刷新当前帧的变量
                        DebuggerController.getInstance().sessionState().selectFrame(frame.frameId);
                    });
                });
    }

    private static String stripQuotes(@NonNull String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
