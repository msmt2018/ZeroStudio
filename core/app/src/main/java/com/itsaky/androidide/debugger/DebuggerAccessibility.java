/*
 *  ZeroStudio IDE - 调试器无障碍 (TalkBack) 辅助器 (Phase E5)
 *
 *  集中提供:
 *    - buildBreakpointContentDescription: 把 IdeBreakpoint 折叠成
 *      TalkBack 可读的句子
 *    - announce*: 在 View 上 announceForAccessibility 调用,
 *      调用方传入 View 与字符串资源 id
 *    - buildWatchContentDescription: 监视项 a11y 文案
 *    - buildFrameContentDescription: 栈帧 a11y 文案
 *
 *  设计原则:
 *    - 纯函数 + 资源 id 解析,便于在适配器 / Fragment 中调用
 *    - 不持有 View 引用,避免内存泄漏
 */

package com.itsaky.androidide.debugger;

import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.adapter.BreakpointListAdapter;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
import java.io.File;

public final class DebuggerAccessibility {

    private DebuggerAccessibility() {}

    @NonNull
    public static String buildBreakpointContentDescription(@NonNull Context ctx,
                                                           @NonNull IdeBreakpoint bp) {
        int stateRes = BreakpointListAdapter.stateLabelResId(bp.state);
        if (stateRes == 0) stateRes = R.string.debugger_bp_state_normal;
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.getString(stateRes));
        sb.append(' ');
        sb.append(shortenPath(bp.file));
        sb.append(' ');
        sb.append(ctx.getString(R.string.debugger_a11y_line_prefix, bp.line));
        if (bp.condition != null && !bp.condition.isEmpty()) {
            sb.append(", ");
            sb.append(ctx.getString(R.string.debugger_a11y_condition_prefix, bp.condition));
        }
        if (bp.logMessage != null && !bp.logMessage.isEmpty()) {
            sb.append(", ");
            sb.append(ctx.getString(R.string.debugger_a11y_log_prefix, bp.logMessage));
        }
        if (bp.hitCountReceived > 0) {
            sb.append(", ");
            sb.append(ctx.getString(R.string.debugger_bp_hit_received, bp.hitCountReceived));
        }
        return sb.toString();
    }

    @NonNull
    public static String buildFrameContentDescription(@NonNull Context ctx,
                                                      @NonNull String methodName,
                                                      @NonNull String file,
                                                      int line) {
        return ctx.getString(R.string.debugger_a11y_frame_at,
                methodName, file, line);
    }

    @NonNull
    public static String buildWatchContentDescription(@NonNull Context ctx,
                                                      @NonNull String expr,
                                                      @NonNull String value,
                                                      boolean hasError) {
        if (hasError) {
            return ctx.getString(R.string.debugger_a11y_watch_error, expr, value);
        }
        return ctx.getString(R.string.debugger_a11y_watch_value, expr, value);
    }

    /** 在 View 上发布一条 TalkBack announcement。 */
    public static void announce(@NonNull View anchor, @NonNull CharSequence text) {
        anchor.announceForAccessibility(text);
    }

    public static void announce(@NonNull View anchor,
                                @NonNull Context ctx,
                                @StringRes int resId,
                                Object... args) {
        anchor.announceForAccessibility(ctx.getString(resId, args));
    }

    // ------- 静态辅助: 事件 announce -------

    public static void announcePaused(@NonNull View anchor, @NonNull Context ctx,
                                      @NonNull String file, int line) {
        announce(anchor, ctx, R.string.debugger_a11y_paused, shortenPath(file), line);
    }

    public static void announceResumed(@NonNull View anchor, @NonNull Context ctx) {
        announce(anchor, ctx, R.string.debugger_a11y_resumed);
    }

    public static void announceConnected(@NonNull View anchor, @NonNull Context ctx) {
        announce(anchor, ctx, R.string.debugger_a11y_connected);
    }

    public static void announceDisconnected(@NonNull View anchor, @NonNull Context ctx) {
        announce(anchor, ctx, R.string.debugger_a11y_disconnected);
    }

    public static void announceBreakpointHit(@NonNull View anchor, @NonNull Context ctx,
                                             @NonNull String file, int line) {
        announce(anchor, ctx, R.string.debugger_a11y_bp_hit,
                shortenPath(file) + ":" + line);
    }

    // ------- helpers -------

    private static String shortenPath(@NonNull String full) {
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
