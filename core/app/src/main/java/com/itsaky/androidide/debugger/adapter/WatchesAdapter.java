/*
 *  ZeroStudio IDE - 监视表达式 RecyclerView 适配器
 *
 *  PR-4:  左列表达式,右列当前值. 长按 item 触发删除.
 *  PR-E1: 升级到 ListAdapter + DiffUtil + stableIds, 优化大量 watch
 *         时的 diff 开销.
 *  PR-D4: 重构为 WatchEntry 单一数据源,修复"items 数组与 values 数组
 *         长度不一致导致值错位"的 bug。统一通过 setValuesAll() 原子
 *         地更新,不再用两个独立的 list 维护。
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class WatchesAdapter extends ListAdapter<WatchesAdapter.WatchEntry, WatchesAdapter.VH> {

    /** 列表的单一数据源,PR-D4 重构后保留 expr 文本和当前值。 */
    public static final class WatchEntry {
        @NonNull public final String expression;
        @NonNull public String value;
        public WatchEntry(@NonNull String expression) {
            this(expression, "");
        }
        public WatchEntry(@NonNull String expression, @NonNull String value) {
            this.expression = expression;
            this.value = value;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WatchEntry)) return false;
            WatchEntry w = (WatchEntry) o;
            return expression.equals(w.expression) && value.equals(w.value);
        }
        @Override public int hashCode() {
            return Objects.hash(expression, value);
        }
    }

    public interface Listener {
        void onItemLongClick(int position, @NonNull String expr);
        /** PR-E1: 单击 -> 编辑 watch 表达式. */
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
                    return a.value.equals(b.value);
                }
            };

    @Nullable private Listener listener;

    public WatchesAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    /**
     * 设置监视表达式列表(只关心表达式本身,值置空)。
     */
    public void submit(@NonNull List<String> exprs) {
        List<WatchEntry> entries = new ArrayList<>(exprs.size());
        for (String e : exprs) entries.add(new WatchEntry(e, ""));
        submitList(entries);
    }

    /**
     * 把当前所有项的值标记为 {@code value},用于"程序未暂停"时显示占位。
     */
    public void markAll(@NonNull String value) {
        List<WatchEntry> current = new ArrayList<>(getCurrentList());
        for (int i = 0; i < current.size(); i++) {
            current.set(i, new WatchEntry(current.get(i).expression, value));
        }
        submitList(current);
    }

    /**
     * 一次性设置所有项的值,长度必须与当前项数一致。如果不一致,
     * 多余的项被截断,不足的项用空串填充。这是 PR-D4 修复 items/values
     * 错位后的原子操作,内部直接构造新的 WatchEntry 列表后通过
     * DiffUtil 增量更新,避免 notifyDataSetChanged 的全局刷新。
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
        WatchEntry entry = getItem(position);
        h.expr.setText(entry.expression);
        h.value.setText(entry.value);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(position, entry.expression);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(position, entry.expression);
                return true;
            }
            return false;
        });
    }

    public List<String> expressions() {
        List<String> r = new ArrayList<>(getCurrentList().size());
        for (WatchEntry e : getCurrentList()) r.add(e.expression);
        return Collections.unmodifiableList(r);
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
