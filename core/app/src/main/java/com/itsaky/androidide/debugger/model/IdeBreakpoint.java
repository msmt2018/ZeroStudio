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
        /** 普通行断点 */
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
        HIT,
        /** 异常断点 - 全局捕获指定异常 */
        EXCEPTION,
        /** 字段/变量监视断点 - 读取或写入时暂停 */
        FIELD_WATCHPOINT,
        /** 方法/函数断点 - 进入或退出方法时暂停 */
        METHOD,
        /** 依赖/触发性断点 - 由另一个断点激活 */
        DEPENDENT,
        /** 一次性断点 - 命中后自动移除 */
        TEMPORARY
    }

    public enum Kind {
        LINE,
        EXCEPTION,
        FIELD_WATCHPOINT,
        METHOD
    }

    /** 稳定 ID,用于跨编辑会话标识断点。 */
    @NonNull public final String id;
    /** 源文件绝对路径。 */
    @NonNull public final String file;
    /** 1-based 行号。全局异常断点可为 0。 */
    public final int line;
    /** 断点绑定维度:行/异常/字段/方法。 */
    @NonNull public Kind kind = Kind.LINE;
    /** 一次性断点:命中后自动移除。 */
    public boolean temporary;
    /** 字段监视点:读取时触发。 */
    public boolean watchAccess;
    /** 字段监视点:修改时触发。 */
    public boolean watchModification = true;
    /** 方法断点:进入方法时触发。 */
    public boolean methodEntry = true;
    /** 方法断点:退出方法时触发。 */
    public boolean methodExit;
    /** 异常断点:捕获已处理异常。 */
    public boolean catchCaught = true;
    /** 异常断点:捕获未处理异常。 */
    public boolean catchUncaught = true;
    /** 依赖断点 ID;非空时需等待该断点先命中。 */
    @Nullable public String dependsOnBreakpointId;
    /** 代码元素名称,如异常类名、字段名或方法名。 */
    @Nullable public String elementName;
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

    /** 是否是仍然绑定到行号的行断点扩展。 */
    public boolean isLineBased() {
        return kind == Kind.LINE;
    }

    /** 按配置刷新可视状态。 */
    public void refreshStateFromOptions() {
        if (state == State.DISABLED || state == State.HIT || state == State.INVALID) return;
        if (kind == Kind.EXCEPTION) state = State.EXCEPTION;
        else if (kind == Kind.FIELD_WATCHPOINT) state = State.FIELD_WATCHPOINT;
        else if (kind == Kind.METHOD) state = State.METHOD;
        else if (temporary) state = State.TEMPORARY;
        else if (dependsOnBreakpointId != null && !dependsOnBreakpointId.isEmpty()) state = State.DEPENDENT;
        else if (logMessage != null && !logMessage.isEmpty()) state = State.LOG;
        else if (condition != null && !condition.isEmpty()) state = State.CONDITION;
        else state = State.NORMAL;
    }

    /** 切换禁用状态。 */
    public void toggleDisabled() {
        state = (state == State.DISABLED) ? State.NORMAL : State.DISABLED;
    }

    /** 设置条件;附带条件会自动把状态切为 CONDITION。 */
    public void setCondition(@Nullable String expr) {
        this.condition = expr;
        refreshStateFromOptions();
    }

    /** 设置日志消息;附带日志消息会自动把状态切为 LOG。 */
    public void setLogMessage(@Nullable String expr) {
        this.logMessage = expr;
        refreshStateFromOptions();
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
        refreshStateFromOptions();
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
