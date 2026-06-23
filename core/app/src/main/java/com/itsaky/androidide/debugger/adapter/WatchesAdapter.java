/*
 *  ZeroStudio IDE - 监视表达式 RecyclerView 适配器
 *
 *  PR-4: 左列表达式,右列当前值. 长按 item 触发删除.
 *  PR-E1: 升级到 ListAdapter + DiffUtil + stableIds, 优化大量 watch
 *         时的 diff 开销.
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

public class WatchesAdapter extends ListAdapter<String, WatchesAdapter.VH> {

    public interface Listener {
        void onItemLongClick(int position, @NonNull String expr);
        /** PR-E1: 单击 -> 编辑 watch 表达式. */
        default void onItemClick(int position, @NonNull String expr) {}
    }

    private static final DiffUtil.ItemCallback<String> DIFF =
            new DiffUtil.ItemCallback<String>() {
                @Override
                public boolean areItemsTheSame(@NonNull String a, @NonNull String b) {
                    return a.equals(b);
                }
                @Override
                public boolean areContentsTheSame(@NonNull String a, @NonNull String b) {
                    return a.equals(b);
                }
            };

    private final List<String> values = new ArrayList<>();
    @Nullable private Listener listener;

    public WatchesAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    public void submit(@NonNull List<String> exprs) {
        submitList(new ArrayList<>(exprs));
        synchronized (values) {
            values.clear();
            for (int i = 0; i < exprs.size(); i++) values.add("");
        }
        notifyDataSetChanged();
    }

    public void markAll(@NonNull String value) {
        synchronized (values) {
            for (int i = 0; i < values.size(); i++) values.set(i, value);
        }
        notifyDataSetChanged();
    }

    public void setValues(@NonNull String[] vs) {
        synchronized (values) {
            values.clear();
            for (String s : vs) values.add(s);
            // pad / trim to data size
            while (values.size() < getCurrentList().size()) values.add("");
            if (values.size() > getCurrentList().size()) {
                while (values.size() > getCurrentList().size())
                    values.remove(values.size() - 1);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return Objects.hashCode(getItem(position));
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
        String expr = getItem(position);
        h.expr.setText(expr);
        String val;
        synchronized (values) {
            val = position < values.size() ? values.get(position) : "";
        }
        h.value.setText(val);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(position, expr);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(position, expr);
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
