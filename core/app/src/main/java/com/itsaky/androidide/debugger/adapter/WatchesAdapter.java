/*
 *  ZeroStudio IDE - 监视表达式 RecyclerView 适配器
 *
 *  PR-D6: 把两套错位的 `List<String> exprs + List<String> values` 合并
 *  为单一 `WatchEntry` 数据源,统一走 DiffUtil 原子更新;删除破坏
 *  ListAdapter 优化的 `notifyDataSetChanged()`。
 *
 *  历史 bug 摘要:
 *    - `submit(exprs)` 调 `notifyDataSetChanged()` 与 `ListAdapter.submitList`
 *      冲突,DiffUtil 完全失效,每次都重画整列。
 *    - `setValues(String[])` 调 `notifyDataSetChanged()` 同上问题。
 *    - `values` 与 `getCurrentList()` 长度不同时,值会错位打到下一行。
 */

package com.itsaky.androidide.debugger.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.itsaky.androidide.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WatchesAdapter extends ListAdapter<WatchesAdapter.WatchEntry, WatchesAdapter.VH> {

    /**
     * PR-D6: 单一数据源,expression + value 同进同出,DiffUtil
     * 按 expression 作 id,value 作内容,避免错位 bug。
     */
    public static final class WatchEntry {
        public final String expression;
        public String value;

        public WatchEntry(@NonNull String expression, @Nullable String value) {
            this.expression = expression;
            this.value = value == null ? "" : value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WatchEntry)) return false;
            WatchEntry that = (WatchEntry) o;
            return Objects.equals(expression, that.expression)
                    && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(expression, value);
        }
    }

    public interface Listener {
        void onItemLongClick(int position, @NonNull String expr);
        /** PR-D6: 单击 -> 编辑 watch 表达式. */
        default void onItemClick(int position, @NonNull String expr) {}
    }

    private static final DiffUtil.ItemCallback<WatchEntry> DIFF =
            new DiffUtil.ItemCallback<WatchEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull WatchEntry a, @NonNull WatchEntry b) {
                    return a.expression.equals(b.expression);
                }
                @Override
                public boolean areContentsTheSame(@NonNull WatchEntry a, @NonNull WatchEntry b) {
                    return a.equals(b);
                }
            };

    @Nullable private Listener listener;

    public WatchesAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    /**
     * 提交新表达式列表(保留现有 value;无 value 的填 "").
     * 与 {@link #setValues(String[])} 不可同时调用 — 后者覆盖前者的 value。
     */
    public void submit(@NonNull List<String> exprs) {
        List<WatchEntry> current = new ArrayList<>(getCurrentList());
        List<WatchEntry> next = new ArrayList<>(exprs.size());
        for (int i = 0; i < exprs.size(); i++) {
            String e = exprs.get(i);
            // 如果当前已存在同 expression,保留旧 value,否则 value 为空
            String prevValue = null;
            for (int j = 0; j < current.size(); j++) {
                if (current.get(j).expression.equals(e)) {
                    prevValue = current.get(j).value;
                    break;
                }
            }
            next.add(new WatchEntry(e, prevValue == null ? "" : prevValue));
        }
        submitList(next);
    }

    /**
     * 原子更新当前每个 entry 的 value(用于 EvalEngine 求值后回填)。
     * 长度自适应:超出当前 entry 数量的 values 截断,不足的补 ""。
     */
    public void setValues(@NonNull String[] vs) {
        List<WatchEntry> current = new ArrayList<>(getCurrentList());
        for (int i = 0; i < current.size(); i++) {
            String v = i < vs.length ? vs[i] : "";
            current.set(i, new WatchEntry(current.get(i).expression, v));
        }
        submitList(current);
    }

    @Override
    public long getItemId(int position) {
        return Objects.hashCode(getItem(position).expression);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_watches_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        WatchEntry e = getItem(position);
        h.expr.setText(e.expression);
        h.value.setText(e.value);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(position, e.expression);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(position, e.expression);
                return true;
            }
            return false;
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView expr;
        final TextView value;
        VH(@NonNull View v) {
            super(v);
            expr = v.findViewById(R.id.watch_expr);
            value = v.findViewById(R.id.watch_value);
        }
    }
}
