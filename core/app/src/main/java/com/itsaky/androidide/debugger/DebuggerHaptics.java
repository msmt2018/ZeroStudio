/*
 *  ZeroStudio IDE - 调试器触觉反馈 (Phase D7)
 *
 *  集中提供调试相关动作的震动反馈:
 *    - onPaused         : 命中断点/暂停目标线程
 *    - onResumed        : 恢复目标线程
 *    - onBreakpointToggled: 切换断点 (true=添加/启用, false=删除/禁用)
 *    - onStop           : 强制停止目标进程
 *
 *  设计:
 *    - 全部使用 VibrationEffect.createOneShot/createWaveform (API 26+);
 *      minSdk 已是 26+ 故无需旧 API fallback
 *    - 静默失败:设备没有 vibrator / 没有权限 / 任何异常都吞掉,
 *      不能让震动失败阻塞调试主流程
 *    - 系统设置里"触感反馈"被关掉时由 framework 自动忽略,无需应用层处理
 */

package com.itsaky.androidide.debugger;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

public final class DebuggerHaptics {

    private DebuggerHaptics() {}

    /** 命中断点/暂停。短促 30ms,中度振幅。 */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public static void onPaused(@Nullable Context ctx) {
        vibrate(ctx, 30L, 160);
    }

    /** 恢复目标线程。20ms 轻量。 */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public static void onResumed(@Nullable Context ctx) {
        vibrate(ctx, 20L, 120);
    }

    /** 切换断点。添加/启用: 双短 12+18ms;删除/禁用: 单一 20ms。 */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public static void onBreakpointToggled(@Nullable Context ctx, boolean enabled) {
        if (enabled) {
            vibrate(ctx, new long[]{0L, 12L, 30L, 18L}, 100);
        } else {
            vibrate(ctx, 20L, 100);
        }
    }

    /** 强制停止目标进程。50ms 长震。 */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public static void onStop(@Nullable Context ctx) {
        vibrate(ctx, 50L, 200);
    }

    // ----------------- internal -----------------

    @RequiresPermission(android.Manifest.permission.VIBRATE)
    private static void vibrate(@Nullable Context ctx, long ms, int amplitude) {
        Vibrator v = resolveVibrator(ctx);
        if (v == null || !v.hasVibrator()) return;
        try {
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude));
        } catch (Throwable t) {
            // silent: never break debugger flow on haptics failure
        }
    }

    @RequiresPermission(android.Manifest.permission.VIBRATE)
    private static void vibrate(@Nullable Context ctx, @NonNull long[] pattern, int amplitude) {
        Vibrator v = resolveVibrator(ctx);
        if (v == null || !v.hasVibrator()) return;
        try {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (Throwable t) {
            // silent
        }
    }

    @Nullable
    private static Vibrator resolveVibrator(@Nullable Context ctx) {
        if (ctx == null) return null;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                VibratorManager vm =
                        (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) return vm.getDefaultVibrator();
            }
            return (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable t) {
            return null;
        }
    }
}
