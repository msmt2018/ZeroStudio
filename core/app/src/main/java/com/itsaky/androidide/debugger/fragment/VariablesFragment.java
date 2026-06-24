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
import android.text.InputType;
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
import com.itsaky.androidide.debugger.adapter.VariablesAdapter;
import com.itsaky.androidide.utils.FlashbarActivityUtilsKt;
import com.zerostudio.debugger.api.EvalResult;
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
    /** PR-D6 batch 2/3: 上次显示的 frameId,用于检测 frame 切换并主动 evaluate。 */
    private long lastFrameId = -1L;
    /** PR-D6 batch 2/3: refreshVariables 序列号,避免 stale 结果覆盖新 frame。 */
    private int refreshSeq = 0;

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
        // PR-D4: 单击 -> 弹出 set-value 对话框;
        // PR-D5: 长按 -> 弹 popup menu(复制名/复制值/添加监视/跳转声明)。
        adapter.setListener(new VariablesAdapter.Listener() {
            @Override
            public void onItemClick(@NonNull VariableInfo variable) {
                showSetValueDialog(variable);
            }
            @Override
            public void onVariableLongClick(@NonNull VariableInfo variable) {
                showVariablePopupMenu(variable);
            }
        });
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
            lastFrameId = -1L;
            return;
        }
        StackFrameInfo frame = state.currentFrame();
        if (frame == null) {
            adapter.submit(Collections.emptyList());
            showEmpty(true, R.string.debugger_variables_empty);
            lastFrameId = -1L;
            return;
        }
        // PR-D6 batch 2/3: frame 切换或刚暂停时,frame.variables 可能
        // 是空 — 主动 evaluate 后再渲染,避免切换 frame 时显示旧变量。
        if (frame.frameId != lastFrameId
                || frame.variables == null
                || frame.variables.isEmpty()) {
            com.zerostudio.debugger.api.Debugger dbg =
                    DebuggerController.getInstance().debugger();
            if (dbg != null) {
                lastFrameId = frame.frameId;
                refreshVariables(dbg, frame);
                // 先给个 loading 占位,等 refreshVariables 回来再覆盖。
                adapter.submit(Collections.emptyList());
                showEmpty(true, R.string.debugger_variables_loading);
                return;
            }
        }
        lastFrameId = frame.frameId;
        List<VariableInfo> vars = frame.variables == null
                ? Collections.emptyList()
                : frame.variables;
        adapter.submit(vars);
        showEmpty(vars.isEmpty(), R.string.debugger_variables_empty);
    }

    /**
     * PR-D4: 弹窗让用户输入新值,调用 Debugger.eval().assign()
     * 把表达式赋值给目标变量,赋值后通过 sessionState 重新拉取变量。
     */
    private void showSetValueDialog(@NonNull VariableInfo variable) {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(variable.name);
        input.setText(variable.value == null ? "" : variable.value);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.debugger_set_value_title,
                        variable.name, humanType(variable.typeSignature)))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newValue = input.getText().toString();
                    assignVariable(variable, newValue);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void assignVariable(@NonNull VariableInfo variable, @NonNull String newValue) {
        StackFrameInfo frame = DebuggerController.getInstance()
                .sessionState().currentFrame();
        if (frame == null) {
            flash(R.string.debugger_set_value_not_suspended);
            return;
        }
        com.zerostudio.debugger.api.Debugger dbg =
                DebuggerController.getInstance().debugger();
        if (dbg == null) {
            flash(R.string.debugger_set_value_not_connected);
            return;
        }
        // PR-D6 batch 2/3: 切到后台线程,避免主线程被 setLocal
        // (可能涉及 JDWP round-trip + 类型解析) 阻塞。完成后
        // 通过 postMain 在主线程上回填。
        final long threadId = frame.threadId;
        final long frameId = frame.frameId;
        final int slot = variable.slot;
        final String sig = variable.typeSignature;
        DebuggerController.getInstance().bgExecutor().execute(() -> {
            try {
                dbg.setLocalValueAsync(threadId, frameId, slot, sig, newValue,
                        result -> onSetValueResult(variable, newValue, result));
            } catch (Throwable t) {
                requireActivity().runOnUiThread(() ->
                        flash(getString(R.string.debugger_set_value_error, t.getMessage())));
            }
        });
    }

    /**
     * PR-D6 batch 2/3: setLocal 后台回调。在主线程上做 UI 反馈 + 列表刷新。
     */
    private void onSetValueResult(@NonNull VariableInfo variable,
                                  @NonNull String requestedValue,
                                  @NonNull com.zerostudio.debugger.api.EvalResult result) {
        if (!isAdded()) return;
        if (result.isError()) {
            flash(getString(R.string.debugger_set_value_error, result.error));
            return;
        }
        flash(R.string.debugger_set_value_ok);
        // 重新拉取当前帧的变量,让 UI 立刻反映新值。
        com.zerostudio.debugger.api.Debugger dbg =
                DebuggerController.getInstance().debugger();
        StackFrameInfo frame = DebuggerController.getInstance()
                .sessionState().currentFrame();
        if (dbg != null && frame != null) {
            refreshVariables(dbg, frame);
        }
    }

    private void refreshVariables(com.zerostudio.debugger.api.Debugger dbg,
                                  @NonNull StackFrameInfo frame) {
        // PR-D6 batch 2/3: 切到后台,避免主线程被 getFrameVariables 阻塞。
        // 用序列号保证只有最新一次 refresh 的结果会回填 UI。
        final int mySeq = ++refreshSeq;
        final long targetFrameId = frame.frameId;
        DebuggerController.getInstance().bgExecutor().execute(() -> {
            final java.util.List<VariableInfo> fresh;
            try {
                fresh = dbg.eval().getFrameVariables(frame.threadId, targetFrameId);
            } catch (Throwable t) {
                return; // 取不到也不致命,下一个状态变化时会自然刷新。
            }
            if (mySeq != refreshSeq) return; // 已被新的 refresh 顶掉
            if (adapter == null || !isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (mySeq != refreshSeq || adapter == null || !isAdded()) return;
                adapter.submit(fresh);
                showEmpty(fresh == null || fresh.isEmpty(),
                        R.string.debugger_variables_empty);
            });
        });
    }

    private void showEmpty(boolean empty, int msgRes) {
        if (emptyView != null) {
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty && emptyView instanceof android.widget.TextView) {
                ((android.widget.TextView) emptyView).setText(msgRes);
            }
        }
        if (loadingView != null) loadingView.setVisibility(View.GONE);
        if (list != null) list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * PR-D5: 变量项长按弹窗菜单(复制名/复制值/添加为监视/跳转声明)。
     * 4 个动作;其中"跳转声明"在断点未命中当前变量对应的源码时仅占位
     * (后续 PR 接到 FindDefinition 即可),不弹错误。
     */
    private void showVariablePopupMenu(@NonNull VariableInfo variable) {
        androidx.appcompat.widget.PopupMenu menu = new androidx.appcompat.widget.PopupMenu(
                requireContext(), list);
        menu.getMenu().add(0, 1, 0, R.string.debugger_var_action_copy_name);
        menu.getMenu().add(0, 2, 1, R.string.debugger_var_action_copy_value);
        menu.getMenu().add(0, 3, 2, R.string.debugger_var_action_add_watch);
        menu.getMenu().add(0, 4, 3, R.string.debugger_var_action_open_declaration);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                copyToClipboard(variable.name);
                flashCopied(variable.name);
            } else if (id == 2) {
                if (variable.value == null) {
                    flash(R.string.debugger_var_no_value_to_copy);
                } else {
                    copyToClipboard(variable.value);
                    flashCopied(variable.name);
                }
            } else if (id == 3) {
                com.itsaky.androidide.debugger.model.WatchStore.getInstance()
                        .add(variable.name);
                flash(getString(R.string.debugger_var_watch_added, variable.name));
                DebuggerHaptics.strong(requireActivity());
            } else if (id == 4) {
                // "跳转到声明" — 留作 FindDefinition / R4 扩展点。
                // PR-D5 仅做动作分发,不在此实现(避免 placeholder bug)。
            }
            return true;
        });
        menu.show();
    }

    private void copyToClipboard(@NonNull String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(android.content.ClipData.newPlainText("debugger", text));
    }

    private void flashCopied(@NonNull String name) {
        flash(getString(R.string.debugger_var_copied, name));
        DebuggerHaptics.tap(requireActivity());
    }

    private void flash(int resId) {
        FlashbarActivityUtilsKt.flashInfo(requireActivity(), getString(resId));
    }

    private void flash(@NonNull String msg) {
        FlashbarActivityUtilsKt.flashInfo(requireActivity(), msg);
    }

    private static String humanType(@Nullable String sig) {
        return VariablesAdapter.humanType(sig);
    }
}
