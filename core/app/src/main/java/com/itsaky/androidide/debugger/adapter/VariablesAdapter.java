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
        h.refBadge.setVisibility(v.isPrimitive ? View.GONE : View.VISIBLE);
        h.itemView.setSelected(false);
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
