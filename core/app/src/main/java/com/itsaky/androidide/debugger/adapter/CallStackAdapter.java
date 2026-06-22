/*
 *  ZeroStudio IDE - 调用栈 RecyclerView 适配器
 *
 *  PR-4: 每行显示「方法名 @ file:line」，当前选中帧高亮。
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
import com.zerostudio.debugger.api.StackFrameInfo;
import java.util.ArrayList;
import java.util.List;

public class CallStackAdapter extends RecyclerView.Adapter<CallStackAdapter.VH> {

    public interface Listener {
        void onFramePicked(@NonNull StackFrameInfo frame);
    }

    private final List<StackFrameInfo> data = new ArrayList<>();
    private long currentFrameId = -1L;
    @Nullable private Listener listener;

    public void setListener(@Nullable Listener l) { this.listener = l; }

    public void submit(@NonNull List<StackFrameInfo> frames, long currentFrameId) {
        data.clear();
        data.addAll(frames);
        this.currentFrameId = currentFrameId;
        notifyDataSetChanged();
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
        StackFrameInfo frame = data.get(position);
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

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView method;
        final TextView location;
        VH(@NonNull View v) {
            super(v);
            method = v.findViewById(R.id.cs_method);
            location = v.findViewById(R.id.cs_location);
        }
    }

    private static String shortName(@Nullable String full) {
        if (full == null || full.isEmpty()) return "?";
        int idx = Math.max(full.lastIndexOf('/'), full.lastIndexOf('\\'));
        return idx >= 0 ? full.substring(idx + 1) : full;
    }
}
