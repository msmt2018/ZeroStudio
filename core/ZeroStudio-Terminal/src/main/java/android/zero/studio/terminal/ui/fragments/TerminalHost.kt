package android.zero.studio.termux.ui.fragments

import android.zero.studio.termux.service.SessionService

/**
 * 全局终端会话绑定器持有者。
 *
 * 终端 UI 代码 (TerminalScreen / MkSession / TerminalBackEnd / KeyShortcutHandler 等)
 * 通过此单例访问 [SessionService.SessionBinder], 不再硬依赖
 * [android.zero.studio.termux.ui.activities.terminal.MainActivity]。
 *
 * 由 [TerminalHostFragment] 在 ServiceConnection 回调中设置/清除。
 *
 * @author android_zero
 */
object TerminalSessionHolder {
    @Volatile
    var sessionBinder: SessionService.SessionBinder? = null
}

/**
 * 终端宿主接口 — 提供 [SessionService.SessionBinder] 访问能力。
 *
 * 解耦终端 UI 代码对 [android.zero.studio.termux.ui.activities.terminal.MainActivity]
 * 的硬依赖。终端 UI 代码 (TerminalScreen / MkSession / TerminalBackEnd 等) 只需要
 * 访问 `sessionBinder`, 不关心宿主是 Activity 还是 Fragment。
 *
 * 实现者:
 * - [android.zero.studio.termux.ui.activities.terminal.MainActivity] — Activity 宿主 (空壳, 承载 [TerminalHostFragment])
 * - [TerminalHostFragment] — Fragment 宿主 (用于在 IDE sidebar 中嵌入终端)
 *
 * @author android_zero
 */
interface TerminalHost {
    /** SessionService 的绑定器, 可能为 null (尚未绑定时)。 */
    val sessionBinder: SessionService.SessionBinder?
        get() = TerminalSessionHolder.sessionBinder
}
