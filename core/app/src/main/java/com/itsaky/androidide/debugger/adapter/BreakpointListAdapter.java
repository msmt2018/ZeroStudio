/*
 *  ZeroStudio IDE - 断点列表 RecyclerView 适配器
 *
 *  7 种状态对应不同的圆形色块 + 文本标签：
 *   - NORMAL    - 普通
 *   - INVALID   - 无效
 *   - VERIFIED  - 已验证
 *   - CONDITION - 条件
 *   - LOG       - 日志点
 *   - DISABLED  - 禁用
 *   - HIT       - 命中
 *
 *  PR-6: 加入 LOG 状态 + logMessage 文本。
 *  点击：跳转到对应文件/行。
 *  长按：弹出操作菜单（编辑条件/编辑日志点/启用-禁用/删除）。
 */

package com.itsaky.androidide.debugger.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BreakpointListAdapter extends RecyclerView.Adapter<BreakpointListAdapter.VH> {

    public interface Listener {
        void onItemClick(@NonNull IdeBreakpoint bp);
        void onItemLongClick(@NonNull IdeBreakpoint bp);
    }

    private final List<IdeBreakpoint> data = new ArrayList<>();
    @Nullable private Listener listener;

    public void setListener(@Nullable Listener l) { this.listener = l; }

    public void submit(@NonNull List<IdeBreakpoint> bps) {
        data.clear();
        data.addAll(bps);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_breakpoint_list_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        IdeBreakpoint bp = data.get(position);
        h.file.setText(shortenPath(bp.file));
        h.line.setText(String.valueOf(bp.line));
        h.state.setText(stateLabel(bp.state));
        h.state.setTextColor(colorForState(bp.state));
        h.dot.setImageDrawable(makeDot(bp.state));
        boolean hasCondition = bp.condition != null && !bp.condition.isEmpty();
        boolean hasLog = bp.logMessage != null && !bp.logMessage.isEmpty();
        h.condition.setVisibility(hasCondition ? View.VISIBLE : View.GONE);
        h.condition.setText(bp.condition != null ? bp.condition : "");
        h.logMessage.setVisibility(hasLog ? View.VISIBLE : View.GONE);
        h.logMessage.setText(bp.logMessage != null ? bp.logMessage : "");

        // Phase E2: 命中次数提示 (仅在非 ALWAYS 时显示)
        boolean hasHitCount = bp.hitCountMode
                != com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS
                && bp.hitCount > 0;
        if (h.hitCount != null) {
            h.hitCount.setVisibility(hasHitCount ? View.VISIBLE : View.GONE);
            if (hasHitCount) {
                h.hitCount.setText(h.itemView.getContext().getString(
                        hitCountLabelResId(bp.hitCountMode), bp.hitCount));
            }
        }
        // Phase E2: 已命中次数提示
        if (h.hitCountReceived != null) {
            boolean showReceived = bp.hitCountReceived > 0;
            h.hitCountReceived.setVisibility(showReceived ? View.VISIBLE : View.GONE);
            if (showReceived) {
                h.hitCountReceived.setText(h.itemView.getContext().getString(
                        R.string.debugger_bp_hit_received, bp.hitCountReceived));
            }
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(bp);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(bp);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView dot;
        final TextView file;
        final TextView line;
        final TextView state;
        final TextView condition;
        final TextView logMessage;
        @Nullable final TextView hitCount;
        @Nullable final TextView hitCountReceived;

        VH(@NonNull View v) {
            super(v);
            dot = v.findViewById(R.id.bp_dot);
            file = v.findViewById(R.id.bp_file);
            line = v.findViewById(R.id.bp_line);
            state = v.findViewById(R.id.bp_state);
            condition = v.findViewById(R.id.bp_condition);
            logMessage = v.findViewById(R.id.bp_log_message);
            // Phase E2: 命中次数/已命中次数 (可能为 null,容错处理)
            hitCount = v.findViewById(R.id.bp_hit_count);
            hitCountReceived = v.findViewById(R.id.bp_hit_count_received);
        }
    }

    private static String stateLabel(IdeBreakpoint.State state) {
        switch (state) {
            case NORMAL: return "普通";
            case INVALID: return "无效";
            case VERIFIED: return "已验证";
            case CONDITION: return "条件";
            case LOG: return "日志点";
            case DISABLED: return "禁用";
            case HIT: return "命中";
            default: return state.name();
        }
    }

    private static int colorForState(IdeBreakpoint.State state) {
        switch (state) {
            case NORMAL: return 0xFFE53935;
            case INVALID: return 0xFFB71C1C;
            case VERIFIED: return 0xFF43A047;
            case CONDITION: return 0xFFFBC02D;
            case LOG: return 0xFF8E24AA;
            case DISABLED: return 0xFF9E9E9E;
            case HIT: return 0xFF1E88E5;
            default: return 0xFFE53935;
        }
    }

    /**
     * Phase E2: helper, 解析 hit count 模式对应的 i18n 字符串 id。
     * 仅在 onBindViewHolder 中通过 itemView.getContext() 调用,这里
     * 集中逻辑方便单元测试。
     */
    public static int hitCountLabelResId(
            @NonNull com.zerostudio.debugger.api.Breakpoint.HitCountMode mode) {
        switch (mode) {
            case EQUAL: return R.string.debugger_bp_hit_equal;
            case GREATER_THAN: return R.string.debugger_bp_hit_greater;
            case MULTIPLE: return R.string.debugger_bp_hit_multiple;
            default: return R.string.debugger_bp_hit_equal;
        }
    }

    private static GradientDrawable makeDot(IdeBreakpoint.State state) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(colorForState(state));
        g.setStroke((int) (1.5f * 3), adjustAlpha(Color.WHITE, 0.85f));
        return g;
    }

    private static int adjustAlpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * factor);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static String shortenPath(String full) {
        if (full == null) return "";
        File f = new File(full);
        String name = f.getName();
        String parent = f.getParent();
        if (parent == null) return name;
        File p = new File(parent);
        String pname = p.getName();
        if (pname == null || pname.isEmpty()) return full;
        return ".../" + pname + "/" + name;
    }
}
