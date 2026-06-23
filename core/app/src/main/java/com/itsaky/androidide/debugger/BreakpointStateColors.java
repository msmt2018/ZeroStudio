/*
 *  ZeroStudio IDE - 断点状态颜色解析器 (Phase E3)
 *
 *  把原本散落在 BreakpointListAdapter / BreakpointSidebar 的
 *  switch(state)->0xFF... 统一为资源查询,实现浅/深色主题切换。
 *
 *  单元测试可以直接构造一个 stub Resources 验证 mode->resId 映射,
 *  不再依赖颜色数值。
 */

package com.itsaky.androidide.debugger;

import android.content.Context;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;

public final class BreakpointStateColors {

    private BreakpointStateColors() {}

    @ColorRes
    public static int colorResForState(@NonNull IdeBreakpoint.State state) {
        switch (state) {
            case NORMAL:    return R.color.debugger_bp_normal;
            case INVALID:   return R.color.debugger_bp_invalid;
            case VERIFIED:  return R.color.debugger_bp_verified;
            case CONDITION: return R.color.debugger_bp_condition;
            case LOG:       return R.color.debugger_bp_logpoint;
            case DISABLED:  return R.color.debugger_bp_disabled;
            case HIT:       return R.color.debugger_bp_hit;
            default:        return R.color.debugger_bp_normal;
        }
    }

    @ColorInt
    public static int colorForState(@NonNull Context context, @NonNull IdeBreakpoint.State state) {
        return ContextCompat.getColor(context, colorResForState(state));
    }

    @ColorInt
    public static int hitCountLabelColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.debugger_bp_hit_count_label);
    }

    @ColorInt
    public static int hitCountReceivedLabelColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.debugger_bp_hit_count_received_label);
    }
}
