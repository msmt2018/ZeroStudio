/*
 *  ZeroStudio IDE - 断点调试器触觉反馈辅助
 *
 *  PR-D5: 给断点命中、force-stop 失败、step 成功等关键事件提供短震反馈,
 *  提升操作可感知性(在真机/调试机上尤其重要,IDE 本身没声音的时候)。
 *
 *  设计:
 *   - 单例 + 静态方法,无状态,无外部依赖,易于在 Java 端调用。
 *   - 在没有 attachedActivity 时静默 no-op,不做 toast / flash(避免和
 *     已有 flashInfo 双倍提示)。
 *   - 用 {@link android.view.HapticFeedbackConstants#LONG_PRESS}
 *     作为"重要事件已发生"的提示 — 短按({@code VIRTUAL_KEY}) 会过于频繁。
 */

package com.itsaky.androidide.debugger;

import android.app.Activity;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class DebuggerHaptics {

    private DebuggerHaptics() {
        // no instances
    }

    /**
     * 在宿主 Activity 的根 View 上触发一次"长按"级别的短震。
     * 没有可用的 View 时静默 no-op。
     */
    public static void tick(@Nullable Activity activity) {
        if (activity == null) return;
        View root = activity.getWindow() == null ? null
                : activity.getWindow().getDecorView();
        if (root == null) return;
        try {
            root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } catch (Throwable ignored) {
            // 部分设备没有震动器 / 系统禁用 — 不致命
        }
    }

    /**
     * 在指定 View 上触发"软键短按"级别的极轻反馈(20ms 短震)。
     * 适合高频事件,例如"切换断点"、"set-value 成功"。
     */
    public static void tap(@Nullable View view) {
        if (view == null) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Throwable ignored) {
            // no-op
        }
    }

    /**
     * 在指定 View 上触发"长按"级别的反馈(40ms 短震)。
     * 适合低频重要事件,例如"断点命中"、"stop 成功"、"连接成功"。
     */
    public static void strong(@Nullable View view) {
        if (view == null) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } catch (Throwable ignored) {
            // no-op
        }
    }

    /**
     * 在指定 View 上触发"拒绝"反馈(60ms 双短震),用于"操作被拒"的场景,
     * 例如"未连接调试器时按了 step over"。
     */
    public static void reject(@Nullable View view) {
        if (view == null) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT);
        } catch (Throwable ignored) {
            // no-op
        }
    }

    /** {@link #tick(Activity)} 的 {@link NonNull} 便利重载。 */
    public static void tick(@NonNull Activity activity) {
        tick((Activity) activity);
    }
}
