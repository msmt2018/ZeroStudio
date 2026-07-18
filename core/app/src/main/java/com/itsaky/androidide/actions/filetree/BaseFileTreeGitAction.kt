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
package com.itsaky.androidide.actions.filetree

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.fragments.git.tree.FileTreeGitOps

/**
 * 文件树 git 操作的基类 — 在 [BaseFileTreeAction.prepare] 基础上额外判断
 * "当前项目是否在 git 仓库中"，非 git 仓库时把自身隐藏。
 *
 * 所有 git 相关 action 都应继承此类。这样当用户在非 git 工程里长按文件时，
 * 弹出的菜单不会显示任何 git 相关条目。
 *
 * @author android_zero
 */
abstract class BaseFileTreeGitAction(
    context: Context,
    @StringRes labelRes: Int? = null,
    @DrawableRes iconRes: Int? = null,
) : BaseFileTreeAction(context, labelRes, iconRes) {

  override fun prepare(data: ActionData) {
    super.prepare(data)
    if (!visible) return  // super 已经标 invisible, 直接退出

    // 非 git 仓库时把所有 git 相关 action 隐藏。
    if (!FileTreeGitOps.isGitRepo()) {
      markInvisible()
      return
    }

    visible = true
    enabled = true
  }
}
