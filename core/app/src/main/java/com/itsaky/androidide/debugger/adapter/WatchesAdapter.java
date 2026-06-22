/*
 *  ZeroStudio IDE - 监视表达式 RecyclerView 适配器
 *
 *  PR-4: 左列表达式，右列当前值。长按 item 触发删除。
 */

package com.itsaky.androidide.debugger.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.itsaky.androidide.R;
import java.util.ArrayList;
import java.util.List;

public class WatchesAdapter extends RecyclerView.Adapter<WatchesAdapter.VH> {

    public interface Listener {
        void onItemLongClick(int position, @NonNull String expr);
    }

    private final List<String> data = new ArrayList<>();
    private final List<String> values = new ArrayList<>();
    @Nullable private Listener listener;

    public void setListener(@Nullable Listener l) { this.listener = l; }

    public void submit(@NonNull List<String> exprs) {
        data.clear();
        data.addAll(exprs);
        values.clear();
        for (int i = 0; i < exprs.size(); i++) values.add("");
        notifyDataSetChanged();
    }

    public void markAll(@NonNull String value) {
        for (int i = 0; i < values.size(); i++) values.set(i, value);
        notifyDataSetChanged();
    }

    public void setValues(@NonNull String[] vs) {
        values.clear();
        for (String s : vs) values.add(s);
        // pad / trim to data size
        while (values.size() < data.size()) values.add("");
        if (values.size() > data.size()) {
            while (values.size() > data.size()) values.remove(values.size() - 1);
        }
        notifyDataSetChanged();
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
        String expr = data.get(position);
        h.expr.setText(expr);
        String val = position < values.size() ? values.get(position) : "";
        h.value.setText(val);
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(position, expr);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

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
