package android.zero.studio.termux.ui.fragments

import android.zero.studio.termux.service.SessionService

/**
 * 终端宿主接口 — 提供 [SessionService.SessionBinder] 访问能力。
 *
 * 解耦终端 UI 代码对 [android.zero.studio.termux.ui.activities.terminal.MainActivity]
 * 的硬依赖。终端 UI 代码 (TerminalScreen / MkSession / TerminalBackEnd 等) 只需要
 * 访问 `sessionBinder`, 不关心宿主是 Activity 还是 Fragment。
 *
 * 实现者:
 * - [android.zero.studio.termux.ui.activities.terminal.MainActivity] — Activity 宿主
 * - [TerminalHostFragment] — Fragment 宿主 (用于在 IDE sidebar 中嵌入终端)
 *
 * @author android_zero
 */
interface TerminalHost {
    /** SessionService 的绑定器, 可能为 null (尚未绑定时)。 */
    val sessionBinder: SessionService.SessionBinder?
}
