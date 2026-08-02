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
import com.itsaky.androidide.utils.flashError

/**
 * 用于从任何位置 (action / button / menu) 打开 git 完整 UI 的统一入口。
 *
 * 注: 原先打开的 ComposeHostFragment/GitHostScreen 完整 git UI 已移除,
 * 现保留此 object 仅作占位, 避免调用方编译失败。调用时会提示功能不可用。
 *
 * @author android_zero
 */
object GitScreenOpener {

  /** 打开 git 完整 UI — 完整 git UI 已移除, 当前为占位空操作。 */
  fun openGitUi(activity: FragmentActivity) {
    flashError("完整 Git UI 已移除, 请使用文件树页面的 Git 操作")
  }
}
