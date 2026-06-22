/*
 *  ZeroStudio IDE - 断点数据模型
 *
 *  断点的 6 种状态映射到 IDE 中的图标：
 *    - NORMAL    (🔴 普通)   - 已安装但尚未被运行时验证
 *    - INVALID   (⭕ 无效)   - 行号无法解析到字节码位置
 *    - VERIFIED  (🟢 已验证) - 运行时已确认命中位置
 *    - CONDITION (🟡 条件)   - 附带条件表达式
 *    - DISABLED  (🚫 禁用)   - 用户已禁用
 *    - HIT       (🔵 命中)   - 调试会话中已被命中
 *
 *  PR-3: 该模型与 ide-debugger 的 Breakpoint 互为补充 -
 *  这里的 Breakpoint 拥有颜色/图标等 UI 元数据，而
 *  ide-debugger 的 Breakpoint 拥有 JDWP 协议 ID。
 */

package com.itsaky.androidide.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public final class IdeBreakpoint {

    public enum State {
        /** 普通 (🔴) */
        NORMAL,
        /** 无效 (⭕) - JDWP 服务无法解析到字节码位置 */
        INVALID,
        /** 已验证 (🟢) - JDWP 服务返回的 location 有效 */
        VERIFIED,
        /** 条件 (🟡) - 附带命中条件表达式 */
        CONDITION,
        /** 禁用 (🚫) - 用户禁用 */
        DISABLED,
        /** 命中 (🔵) - 调试会话中已被命中 */
        HIT
    }

    /** 稳定 ID，用于跨编辑会话标识断点。 */
    @NonNull public final String id;
    /** 源文件绝对路径。 */
    @NonNull public final String file;
    /** 1-based 行号。 */
    public final int line;
    /** 命中条件表达式；为空时表示无条件。 */
    @Nullable public String condition;
    /** 当前状态。 */
    @NonNull public State state;
    /** 关联的 ide-debugger 断点 ID；-1 表示尚未安装。 */
    public long debuggerBpId;
    /** 命中次数。 */
    public int hitCount;

    public IdeBreakpoint(@NonNull String file, int line) {
        this(UUID.randomUUID().toString(), file, line, null,
                State.NORMAL, -1L, 0);
    }

    public IdeBreakpoint(
            @NonNull String id,
            @NonNull String file,
            int line,
            @Nullable String condition,
            @NonNull State state,
            long debuggerBpId,
            int hitCount) {
        this.id = id;
        this.file = file;
        this.line = line;
        this.condition = condition;
        this.state = state;
        this.debuggerBpId = debuggerBpId;
        this.hitCount = hitCount;
    }

    /** 是否处于“活跃”状态：用户希望调试器关注它。 */
    public boolean isActive() {
        return state != State.DISABLED;
    }

    /** 切换禁用状态。 */
    public void toggleDisabled() {
        state = (state == State.DISABLED) ? State.NORMAL : State.DISABLED;
    }

    /** 设置条件；附带条件会自动把状态切为 CONDITION。 */
    public void setCondition(@Nullable String expr) {
        this.condition = expr;
        if (expr != null && !expr.isEmpty() && state == State.NORMAL) {
            state = State.CONDITION;
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
                + '}';
    }
}
