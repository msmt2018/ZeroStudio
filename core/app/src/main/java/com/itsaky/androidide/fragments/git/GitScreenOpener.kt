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
package com.itsaky.androidide.fragments.git

import androidx.fragment.app.FragmentActivity
import com.itsaky.androidide.ui.screen.git.ComposeHostFragment

/**
 * 用于从任何位置 (action / button / menu) 打开 git 完整 UI 的统一入口。
 *
 * 行为: 替换当前 activity 的内容 fragment 为 [ComposeHostFragment]。
 *
 * @author android_zero
 */
object GitScreenOpener {

  /** 打开 git 完整 UI (status / branch / log / diff 等都在这里)。 */
  fun openGitUi(activity: FragmentActivity) {
    val frag = ComposeHostFragment()
    activity.supportFragmentManager.beginTransaction()
        .replace(android.R.id.content, frag, "GitComposeHost")
        .addToBackStack("git")
        .commit()
  }
}
