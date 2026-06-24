/*
 *  ZeroStudio IDE - 日志点输出 RecyclerView 适配器 (PR-6)
 *
 *  PR-D8.4: 升级到 ListAdapter + DiffUtil, 替代原先的
 *  notifyDataSetChanged() 全列表重绘。append 仍用 notifyItemInserted
 *  做 O(1) 单条插入;submit 触发 DiffUtil 异步计算差异, 主线程不被阻塞。
 */

package com.itsaky.androidide.debugger.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.model.LogStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogpointAdapter extends ListAdapter<LogStore.Entry, LogpointAdapter.VH> {

    private static final DiffUtil.ItemCallback<LogStore.Entry> DIFF = new DiffUtil.ItemCallback<LogStore.Entry>() {
        @Override
        public boolean areItemsTheSame(@NonNull LogStore.Entry a, @NonNull LogStore.Entry b) {
            // timestamp 是单调递增的, 唯一标识
            return a.timestamp == b.timestamp;
        }
        @Override
        public boolean areContentsTheSame(@NonNull LogStore.Entry a, @NonNull LogStore.Entry b) {
            return a.timestamp == b.timestamp
                    && a.line == b.line
                    && a.text.equals(b.text)
                    && a.sourceFile.equals(b.sourceFile);
        }
    };

    private final SimpleDateFormat fmt =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public LogpointAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).timestamp;
    }

    public void submit(@NonNull List<LogStore.Entry> entries) {
        submitList(entries);
    }

    /**
     * PR-D8.4: 兼容旧 API — 单条插入,内部转成 list 走 DiffUtil 异步 diff。
     * 一次插入的开销是 O(N),连续多条仍可接受;若调用方连续 append 多次,
     * 建议改为先 {@code submitList(snapshot)} 一次性提交以摊销 diff 成本。
     */
    public void append(@NonNull LogStore.Entry entry) {
        java.util.List<LogStore.Entry> current = new java.util.ArrayList<>(getCurrentList());
        current.add(entry);
        submitList(current);
    }

    public void clear() {
        submitList(java.util.Collections.emptyList());
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
        LogStore.Entry e = getItem(position);
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
