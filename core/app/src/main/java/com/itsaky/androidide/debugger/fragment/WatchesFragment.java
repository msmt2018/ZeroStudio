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
import com.zerostudio.debugger.api.EvalResult;
import com.zerostudio.debugger.api.StackFrameInfo;
import java.util.Arrays;

public class WatchesFragment extends Fragment
        implements DebugSessionState.Listener {

    private RecyclerView list;
    private View emptyView;
    private WatchesAdapter adapter;
    /** PR-D6 batch 2/3: 基于 frameId 的表达式求值缓存。同一 frame 重复进入
     *  (例如切换后又切回) 不重复求值,只刷新 UI。resume / 新的 pause 时清空。 */
    private long cachedFrameId = -1L;
    private String[] cachedValues;
    /** PR-D6 batch 2/3: 序列号,每次启动 evaluate 时递增,完成时只回填
     *  与启动时同号的那一次,避免被中途的新 evaluate 顶掉。 */
    private int evaluateSeq = 0;

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

            @Override
            public void onItemClick(int position, @NonNull String expr) {
                // PR-D4: 点击监视项 -> 弹出编辑对话框,允许修改表达式
                showEditDialog(position, expr);
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
        // 当程序未暂停或没有当前栈帧时，所有值都显示 "—"
        if (!state.isSuspended()) {
            adapter.markAll(getString(R.string.debugger_watches_value_pending));
            cachedFrameId = -1L;
            cachedValues = null;
            return;
        }
        StackFrameInfo frame = state.currentFrame();
        if (frame == null) {
            adapter.markAll(getString(R.string.debugger_watches_value_pending));
            cachedFrameId = -1L;
            cachedValues = null;
            return;
        }
        // PR-D6 batch 2/3: 切到后台线程对每个 watch 表达式并行
        // 求值,避免主线程被阻塞。结束后通过 postMain 在主线程上
        // 一次性回填 adapter;若 fragment 已 detach 则跳过。
        final com.zerostudio.debugger.api.Debugger dbg =
                com.itsaky.androidide.debugger.DebuggerController.getInstance().debugger();
        if (dbg == null) {
            adapter.markAll(getString(R.string.debugger_watches_value_pending));
            return;
        }
        final java.util.List<String> exprs = WatchStore.getInstance().all();
        if (exprs.isEmpty()) {
            adapter.setValues(new String[0]);
            return;
        }
        // 缓存命中:同一 frame 重复进入,直接显示缓存值,不再 evaluate。
        if (frame.frameId == cachedFrameId
                && cachedValues != null
                && cachedValues.length == exprs.size()) {
            adapter.setValues(cachedValues);
            return;
        }
        // 先把现有 list 标记为 "求值中",避免空白闪现。
        String[] pending = new String[exprs.size()];
        java.util.Arrays.fill(pending, getString(R.string.debugger_watches_value_pending));
        adapter.setValues(pending);
        // 提交到后台: 串行 evaluate (顺序保留),完成后主线程回填。
        final long targetFrameId = frame.frameId;
        final long targetThreadId = frame.threadId;
        final int mySeq = ++evaluateSeq;
        com.itsaky.androidide.debugger.DebuggerController.getInstance()
                .bgExecutor().execute(() -> {
            final String[] values = new String[exprs.size()];
            for (int i = 0; i < exprs.size(); i++) {
                String expr = exprs.get(i);
                EvalResult r = dbg.eval().evaluate(targetThreadId, targetFrameId, expr);
                if (r.isError()) {
                    values[i] = getString(R.string.debugger_watches_error, r.error);
                } else if (r.displayValue == null) {
                    values[i] = "<" + r.tag.name().toLowerCase() + ">";
                } else {
                    values[i] = r.displayValue;
                }
            }
            // 序列号不匹配:说明 evaluate 期间又来了一次 onStateChanged,
            // 当前结果已不是最新,丢弃(让最新那次负责回填)。
            if (mySeq != evaluateSeq) return;
            if (!isAdded()) return;
            cachedFrameId = targetFrameId;
            cachedValues = values;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || mySeq != evaluateSeq) return;
                adapter.setValues(values);
            });
        });
    }

    private void refresh() {
        adapter.submit(WatchStore.getInstance().all());
        boolean empty = adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showAddDialog() {
        final EditText input = new EditText(requireContext());
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

    /**
     * PR-D4: 点击监视项后弹出编辑对话框,允许用户修改表达式。
     * 改完后原地替换 (通过 remove + add) 保持顺序与索引稳定。
     */
    private void showEditDialog(int position, @NonNull String currentExpr) {
        final EditText input = new EditText(requireContext());
        input.setText(currentExpr);
        input.setHint(R.string.debugger_watches_dialog_hint);
        input.setSelection(currentExpr.length());
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.debugger_watches_dialog_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newExpr = input.getText().toString().trim();
                    if (newExpr.isEmpty() || newExpr.equals(currentExpr)) {
                        return;
                    }
                    WatchStore.getInstance().set(position, newExpr);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
