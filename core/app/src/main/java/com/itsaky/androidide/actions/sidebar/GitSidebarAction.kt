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
import com.itsaky.androidide.fragments.git.GitHostFragment
import com.itsaky.androidide.resources.R
import kotlin.reflect.KClass

/**
 * Sidebar action that opens the mobile-first Git UI container ([GitHostFragment]).
 *
 * 完整 git UI (diff / branches / history / changes / stash / conflict /
 * repositories / settings / tags / interactive-rebase 等多个 tab) 的统一入口,
 * 与文件树页面 ([FileTreeSidebarAction]) 相互独立。
 *
 * @author android_zero
 */
class GitSidebarAction(context: Context, override val order: Int) : AbstractSidebarAction() {

    companion object {
        const val ID = "ide.editor.sidebar.git"
    }

    override val id: String = ID
    override val fragmentClass: KClass<out Fragment> = GitHostFragment::class

    init {
        label = "Git"
        icon = ContextCompat.getDrawable(context, R.drawable.ic_git)
    }
}