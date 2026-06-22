/*
 *  ZeroStudio IDE - 日志点输出 RecyclerView 适配器 (PR-6)
 */

package com.itsaky.androidide.debugger.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.model.LogStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogpointAdapter extends RecyclerView.Adapter<LogpointAdapter.VH> {

    private final List<LogStore.Entry> data = new ArrayList<>();
    private final SimpleDateFormat fmt =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public void submit(@NonNull List<LogStore.Entry> entries) {
        data.clear();
        data.addAll(entries);
        notifyDataSetChanged();
    }

    public void append(@NonNull LogStore.Entry entry) {
        data.add(entry);
        notifyItemInserted(data.size() - 1);
    }

    public void clear() {
        data.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_logpoint_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LogStore.Entry e = data.get(position);
        String meta = fmt.format(new Date(e.timestamp));
        if (!e.sourceFile.isEmpty()) {
            String name = e.sourceFile;
            int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
            if (slash >= 0) name = name.substring(slash + 1);
            meta = meta + "  " + name + ":" + e.line;
        }
        h.meta.setText(meta);
        h.text.setText(e.text);
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView meta;
        final TextView text;
        VH(@NonNull View v) {
            super(v);
            meta = v.findViewById(R.id.log_item_meta);
            text = v.findViewById(R.id.log_item_text);
        }
    }
}
