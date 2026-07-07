/*
 *  ZeroStudio IDE - 断点列渲染委托接口
 *
 *  editor/impl 模块不能依赖 core/app (BreakpointManager / DebuggerController),
 *  所以通过此接口由 core/app 实现, 把断点数据桥接到 IDEEditorRenderer。
 *
 *  断点状态码定义在 [BreakpointGutterStates] 中, 与 core/app 的
 *  IdeBreakpoint.State 枚举一一映射 (由 core/app 端的 delegate 实现完成映射)。
 */

package com.itsaky.androidide.editor.ui.gutter

/**
 * 断点列渲染委托。
 *
 * 实现方 (通常是 core/app 的 BreakpointGutterManager) 需要提供:
 * - 当前文件路径
 * - 每行的断点状态码
 * - 当前命中行
 * - 点击 / 长按回调
 */
interface BreakpointGutterDelegate {

    /** 当前文件路径 (normalized), null 表示未绑定文件 (不绘制断点列)。 */
    fun currentFile(): String?

    /**
     * 指定行 (0-based) 的断点状态码, 见 [BreakpointGutterStates]。
     * 返回 [BreakpointGutterStates.NONE] 表示该行无断点。
     */
    fun breakpointStateForLine(line: Int): Int

    /** 当前命中行 (调试器暂停时), -1 表示无命中行。 */
    fun hitLine(): Int

    /** 点击断点列空白处 (无断点的行)。 */
    fun onGutterClick(line: Int, screenX: Float, screenY: Float)

    /** 点击已有断点的行。 */
    fun onGutterExistingClick(line: Int, screenX: Float, screenY: Float)

    /** 长按断点列。 */
    fun onGutterLongClick(line: Int, screenX: Float, screenY: Float)
}

/**
 * 断点状态码常量。与 core/app 的 [com.itsaky.androidide.debugger.model.IdeBreakpoint.State]
 * 一一映射, 但定义在 editor/impl 中以避免模块循环依赖。
 */
object BreakpointGutterStates {
    const val NONE = 0
    const val NORMAL = 1
    const val INVALID = 2
    const val VERIFIED = 3
    const val CONDITION = 4
    const val LOG = 5
    const val DISABLED = 6
    const val HIT = 7
    const val EXCEPTION = 8
    const val FIELD_WATCHPOINT = 9
    const val METHOD = 10
    const val DEPENDENT = 11
    const val TEMPORARY = 12
}
