/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.actions.sidebar

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.zero.studio.termux.ui.fragments.TerminalHostFragment
import kotlin.reflect.KClass

/**
 * 侧边栏操作 — 在 IDE sidebar 中嵌入 [TerminalHostFragment] (Compose 终端宿主)。
 *
 * 与 [TerminalSidebarAction] (termux 终端) 相互独立:
 *  - [TerminalSidebarAction] 打开 termux 终端 (基于 TermuxFragment)
 *  - [TerminalHostSidebarAction] 打开 ZeroStudio-Terminal Compose 终端
 *    (基于 [TerminalHostFragment], 拥有完整 Fragment 生命周期, 自持 SessionService 绑定)
 *
 * @author android_zero
 */
class TerminalHostSidebarAction(context: Context, override val order: Int) :
    AbstractSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.terminal_host"
  }

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment> = TerminalHostFragment::class

  init {
    label = context.getString(com.itsaky.androidide.resources.R.string.title_terminal_host)
    icon = ContextCompat.getDrawable(
        context, com.itsaky.androidide.resources.R.drawable.ic_terminal)
  }
}
