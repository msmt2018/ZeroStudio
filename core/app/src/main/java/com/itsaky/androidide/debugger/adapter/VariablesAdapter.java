/*
 *  ZeroStudio IDE - 变量 RecyclerView 适配器
 *
 *  PR-4: 显示变量的 名字 : 类型 = 值。
 *  对象类型（typeSignature 以 'L' 或 '[' 开头）显示「object」徽标。
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
import com.zerostudio.debugger.api.VariableInfo;
import java.util.ArrayList;
import java.util.List;

public class VariablesAdapter extends RecyclerView.Adapter<VariablesAdapter.VH> {

    private final List<VariableInfo> data = new ArrayList<>();

    public void submit(@NonNull List<VariableInfo> vars) {
        data.clear();
        data.addAll(vars);
        notifyDataSetChanged();
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
        VariableInfo v = data.get(position);
        h.name.setText(v.name);
        h.type.setText(humanType(v.typeSignature));
        h.value.setText(v.value == null ? "null" : v.value);
        h.refBadge.setVisibility(v.isPrimitive ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() { return data.size(); }

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
    private static String humanType(@Nullable String sig) {
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
