/*
 *  ZeroStudio IDE - 调用栈 RecyclerView 适配器
 *
 *  PR-4: 每行显示「方法名 @ file:line」,当前选中帧高亮.
 *  PR-E1: 升级到 ListAdapter + DiffUtil + stableIds.
 *         同时支持 frameId 稳定的 diff (而不是用 equals 整个对象).
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
import com.zerostudio.debugger.api.StackFrameInfo;
import java.util.List;
import java.util.Objects;

public class CallStackAdapter extends ListAdapter<StackFrameInfo, CallStackAdapter.VH> {

    public interface Listener {
        void onFramePicked(@NonNull StackFrameInfo frame);
    }

    private static final DiffUtil.ItemCallback<StackFrameInfo> DIFF =
            new DiffUtil.ItemCallback<StackFrameInfo>() {
                @Override
                public boolean areItemsTheSame(@NonNull StackFrameInfo a, @NonNull StackFrameInfo b) {
                    return a.frameId == b.frameId;
                }
                @Override
                public boolean areContentsTheSame(@NonNull StackFrameInfo a, @NonNull StackFrameInfo b) {
                    return Objects.equals(a.methodName, b.methodName)
                            && Objects.equals(a.sourceFile, b.sourceFile)
                            && a.lineNumber == b.lineNumber;
                }
            };

    private long currentFrameId = -1L;
    @Nullable private Listener listener;

    public CallStackAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    public void submit(@NonNull List<StackFrameInfo> frames, long currentFrameId) {
        submitList(frames);
        this.currentFrameId = currentFrameId;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).frameId;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_callstack_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        StackFrameInfo frame = getItem(position);
        h.method.setText(frame.methodName == null || frame.methodName.isEmpty()
                ? "<no method>" : frame.methodName);
        h.location.setText(h.itemView.getContext().getString(
                R.string.debugger_callstack_frame_at,
                shortName(frame.sourceFile),
                frame.lineNumber));
        h.itemView.setSelected(frame.frameId == currentFrameId);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onFramePicked(frame);
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView method;
        final TextView location;
        VH(@NonNull View v) {
            super(v);
            method = v.findViewById(R.id.cs_method);
            location = v.findViewById(R.id.cs_location);
        }
    }

    public static String shortName(@Nullable String full) {
        if (full == null || full.isEmpty()) return "?";
        int idx = Math.max(full.lastIndexOf('/'), full.lastIndexOf('\\'));
        return idx >= 0 ? full.substring(idx + 1) : full;
    }
}
