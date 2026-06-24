/*
 *  ZeroStudio IDE - 断点数据模型
 *
 *  断点的 7 种状态映射到 IDE 中的图标：
 *    - NORMAL    - 已安装但尚未被运行时验证
 *    - INVALID   - 行号无法解析到字节码位置
 *    - VERIFIED  - 运行时已确认命中位置
 *    - CONDITION - 附带条件表达式(命中时按表达式求值)
 *    - LOG       - 附带日志消息表达式(命中时不暂停)
 *    - DISABLED  - 用户已禁用
 *    - HIT       - 调试会话中已被命中
 *
 *  PR-3: 该模型与 ide-debugger 的 Breakpoint 互为补充 -
 *  这里的 Breakpoint 拥有颜色/图标等 UI 元数据,而
 *  ide-debugger 的 Breakpoint 拥有 JDWP 协议 ID。
 *
 *  PR-6: 新增 logMessage 字段 + LOG 状态,配套支持"日志点"。
 *
 *  Phase E2: 镜像 ide-debugger 的 hitCountMode + hitCount 字段,
 *  并把原 hitCount 计数器重命名为 hitCountReceived,避免与
 *  用户配置的命中阈值同名。
 */

package com.itsaky.androidide.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public final class IdeBreakpoint {

    public enum State {
        /** 普通 */
        NORMAL,
        /** 无效 - JDWP 服务无法解析到字节码位置 */
        INVALID,
        /** 已验证 - JDWP 服务返回的 location 有效 */
        VERIFIED,
        /** 条件 - 附带命中条件表达式 */
        CONDITION,
        /** 日志点 - 附带命中日志消息表达式 */
        LOG,
        /** 禁用 - 用户禁用 */
        DISABLED,
        /** 命中 - 调试会话中已被命中 */
        HIT
    }

    /** 稳定 ID,用于跨编辑会话标识断点。 */
    @NonNull public final String id;
    /** 源文件绝对路径。 */
    @NonNull public final String file;
    /** 1-based 行号。 */
    public final int line;
    /** 命中条件表达式;为空时表示无条件。 */
    @Nullable public String condition;
    /** 命中日志消息表达式(如 "x=" + x)。非空时本断点作为
     * "日志点"使用:命中时求值并写入 LogStore,VM 不暂停。
     */
    @Nullable public String logMessage;
    /**
     * 命中次数策略:ALWAYS / EQUAL / GREATER_THAN / MULTIPLE。
     * 见 {@code com.zerostudio.debugger.api.Breakpoint.HitCountMode}。
     */
    @NonNull public com.zerostudio.debugger.api.Breakpoint.HitCountMode hitCountMode
            = com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS;
    /** 命中次数阈值;仅在 mode != ALWAYS 时生效。 */
    public int hitCount;
    /** 当前状态。 */
    @NonNull public State state;
    /** 关联的 ide-debugger 断点 ID;-1 表示尚未安装。 */
    public long debuggerBpId;
    /** 运行时报告的命中次数(原 hitCount 字段,Phase E2 重命名以避免歧义)。 */
    public int hitCountReceived;

    public IdeBreakpoint(@NonNull String file, int line) {
        this(UUID.randomUUID().toString(), file, line, null, null,
                com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS, 0,
                State.NORMAL, -1L, 0);
    }

    /**
     * Legacy constructor preserved for the persistence layer. The
     * {@code hitCount} parameter is interpreted as the persisted hit-count
     * threshold; the runtime hit counter starts at zero.
     */
    public IdeBreakpoint(
            @NonNull String id,
            @NonNull String file,
            int line,
            @Nullable String condition,
            @NonNull State state,
            long debuggerBpId,
            int hitCount) {
        this(id, file, line, condition, null,
                com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS, hitCount,
                state, debuggerBpId, 0);
    }

    /** Convenience constructor used by BreakpointStore when loading from disk. */
    public IdeBreakpoint(
            @NonNull String file,
            int line,
            @Nullable String condition,
            @Nullable String logMessage,
            @NonNull State state,
            long debuggerBpId,
            int hitCount) {
        this(UUID.randomUUID().toString(), file, line, condition, logMessage,
                com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS, hitCount,
                state, debuggerBpId, 0);
    }

    public IdeBreakpoint(
            @NonNull String id,
            @NonNull String file,
            int line,
            @Nullable String condition,
            @Nullable String logMessage,
            @NonNull State state,
            long debuggerBpId,
            int hitCount) {
        this(id, file, line, condition, logMessage,
                com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS, hitCount,
                state, debuggerBpId, 0);
    }

    /**
     * Phase E2: full-fidelity constructor. The 10-arg form is the canonical
     * entry point; legacy constructors delegate here.
     */
    public IdeBreakpoint(
            @NonNull String id,
            @NonNull String file,
            int line,
            @Nullable String condition,
            @Nullable String logMessage,
            @NonNull com.zerostudio.debugger.api.Breakpoint.HitCountMode hitCountMode,
            int hitCount,
            @NonNull State state,
            long debuggerBpId,
            int hitCountReceived) {
        this.id = id;
        this.file = file;
        this.line = line;
        this.condition = condition;
        this.logMessage = logMessage;
        this.hitCountMode = hitCountMode == null
                ? com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS
                : hitCountMode;
        this.hitCount = hitCount;
        this.state = state;
        this.debuggerBpId = debuggerBpId;
        this.hitCountReceived = hitCountReceived;
    }

    /** 是否处于"活跃"状态:用户希望调试器关注它。 */
    public boolean isActive() {
        return state != State.DISABLED;
    }

    /** 切换禁用状态。 */
    public void toggleDisabled() {
        state = (state == State.DISABLED) ? State.NORMAL : State.DISABLED;
    }

    /** 设置条件;附带条件会自动把状态切为 CONDITION。 */
    public void setCondition(@Nullable String expr) {
        this.condition = expr;
        if (state != State.DISABLED
                && state != State.HIT
                && state != State.LOG) {
            state = State.CONDITION;
        }
    }

    /** 设置日志消息;附带日志消息会自动把状态切为 LOG。 */
    public void setLogMessage(@Nullable String expr) {
        this.logMessage = expr;
        if (state != State.DISABLED
                && state != State.HIT
                && state != State.CONDITION) {
            state = State.LOG;
        }
    }

    /**
     * Phase E2: 设置命中次数策略与阈值。状态切到 NORMAL/CONDITION
     * (取决于是否同时有 condition),DISABLED/HIT/LOG 状态保留。
     */
    public void setHitCount(
            @NonNull com.zerostudio.debugger.api.Breakpoint.HitCountMode mode,
            int count) {
        this.hitCountMode = mode == null
                ? com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS
                : mode;
        this.hitCount = Math.max(0, count);
        if (state == State.DISABLED
                || state == State.HIT
                || state == State.LOG) {
            return;
        }
        if (this.hitCountMode != com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS) {
            // 命中次数过滤本身不算"条件断点",用 NORMAL 展示即可
            return;
        }
        if (this.condition != null && !this.condition.isEmpty()) {
            state = State.CONDITION;
        } else {
            state = State.NORMAL;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdeBreakpoint)) return false;
        IdeBreakpoint that = (IdeBreakpoint) o;
        return line == that.line && Objects.equals(file, that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, line);
    }

    @NonNull
    @Override
    public String toString() {
        return "IdeBreakpoint{"
                + "id=" + id
                + ", file=" + file
                + ", line=" + line
                + ", state=" + state
                + (condition != null ? ", condition=" + condition : "")
                + (logMessage != null ? ", logMessage=" + logMessage : "")
                + (hitCountMode != com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS
                        ? ", hitCount=" + hitCountMode + ":" + hitCount
                        : "")
                + ", hits=" + hitCountReceived
                + '}';
    }
}
