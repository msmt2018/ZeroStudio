/*
 *  ZeroStudio IDE - 变量 RecyclerView 适配器
 *
 *  PR-4: 显示变量的 名字 : 类型 = 值.
 *  PR-E1: 升级到 DiffUtil + stableIds, 支持 click-to-set-value (设置值).
 *
 *  数据来源: Debugger.fetchVariables(threadId, frameId) 返回的 List<VariableInfo>
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
import com.zerostudio.debugger.api.VariableInfo;
import java.util.List;
import java.util.Objects;

public class VariablesAdapter extends ListAdapter<VariableInfo, VariablesAdapter.VH> {

    public interface Listener {
        /** PR-D4: 短按 -> 弹出 set-value 对话框（仅对非 final 变量可写） */
        default void onItemClick(@NonNull VariableInfo variable) {}
        void onVariableLongClick(@NonNull VariableInfo variable);
    }

    private static final DiffUtil.ItemCallback<VariableInfo> DIFF =
            new DiffUtil.ItemCallback<VariableInfo>() {
                @Override
                public boolean areItemsTheSame(@NonNull VariableInfo a, @NonNull VariableInfo b) {
                    return a.name.equals(b.name) && a.typeSignature.equals(b.typeSignature);
                }
                @Override
                public boolean areContentsTheSame(@NonNull VariableInfo a, @NonNull VariableInfo b) {
                    return a.isPrimitive == b.isPrimitive
                            && Objects.equals(a.value, b.value);
                }
            };

    @Nullable private Listener listener;
    private long highlightedId = -1L;

    public VariablesAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    public void setHighlighted(long objectId) { this.highlightedId = objectId; }

    /**
     * Java-friendly alias of {@link #submitList(List)}. Mirrors the
     * {@code WatchesAdapter.submit} helper so callers in
     * {@code VariablesFragment} can keep a single naming convention.
     */
    public void submit(@NonNull List<VariableInfo> vars) {
        submitList(new java.util.ArrayList<>(vars));
    }

    @Override
    public long getItemId(int position) {
        // Hash name + typeSignature; will be stable across submits
        VariableInfo v = getItem(position);
        return Objects.hash(v.name, v.typeSignature);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_variables_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        VariableInfo v = getItem(position);
        h.name.setText(v.name);
        h.type.setText(humanType(v.typeSignature));
        h.value.setText(v.value == null ? "null" : v.value);
        // PR-D8.2: 错误状态时,值文本用 colorError 高亮, 方便用户
        // 一眼看出哪个变量求值失败。
        if (v.isError) {
            h.value.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                            h.itemView.getContext(),
                            com.google.android.material.R.color.material_error));
        } else {
            h.value.setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                            h.itemView, com.google.android.material.R.attr.colorOnSurface));
        }
        // PR-D8.2: ref 徽标文字用 string resource 而不是硬编码 "obj"。
        if (!v.isPrimitive) {
            h.refBadge.setText(R.string.debugger_var_ref_obj);
            h.refBadge.setVisibility(View.VISIBLE);
        } else {
            h.refBadge.setVisibility(View.GONE);
        }
        h.itemView.setSelected(false);
        h.itemView.setOnClickListener(vw -> {
            if (listener != null) listener.onItemClick(v);
        });
        h.itemView.setOnLongClickListener(vw -> {
            if (listener != null) {
                listener.onVariableLongClick(v);
                return true;
            }
            return false;
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView type;
        final TextView value;
        final TextView refBadge;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.var_name);
            type = v.findViewById(R.id.var_type);
            value = v.findViewById(R.id.var_value);
            refBadge = v.findViewById(R.id.var_ref);
        }
    }

    /** "Ljava/lang/String;" -> "String", "I" -> "int" etc. */
    public static String humanType(@Nullable String sig) {
        if (sig == null || sig.isEmpty()) return "?";
        char c = sig.charAt(0);
        switch (c) {
            case 'V': return "void";
            case 'Z': return "boolean";
            case 'B': return "byte";
            case 'C': return "char";
            case 'S': return "short";
            case 'I': return "int";
            case 'J': return "long";
            case 'F': return "float";
            case 'D': return "double";
            case 'L': {
                if (sig.length() < 2) return "object";
                String s = sig.substring(1, sig.length() - 1);
                int slash = s.lastIndexOf('/');
                if (slash >= 0) s = s.substring(slash + 1);
                return s;
            }
            case '[': return sig + " (array)";
            default: return sig;
        }
    }
}
