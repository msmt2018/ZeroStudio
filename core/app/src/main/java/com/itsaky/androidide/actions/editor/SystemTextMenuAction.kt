/*
 * This file is part of ZeroStudio.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */

package com.itsaky.androidide.actions.editor

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorActionItem
import com.itsaky.androidide.actions.requireContext
import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 触发系统自定义文本操作菜单的 Action（自动收集系统应用扩展功能，如翻译、小爱同学等的intent浮动菜单）
 *
 * @author android_zero
 */
class SystemTextMenuAction(context: Context, override val order: Int) : EditorActionItem {

  override val id: String = "ide.editor.selection.system_actions"

  override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS

  override var label: String = "system menus"
  override var visible: Boolean = true
  override var enabled: Boolean = true
  override var icon: Drawable? = null
  override var requiresUIThread: Boolean = true

  init {
    icon = ContextCompat.getDrawable(context, R.drawable.more_vert)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)
  }

  override suspend fun execAction(data: ActionData): Any {
    val editor = data.get(CodeEditor::class.java) ?: return false

    val text = editor.text
    val cursor = editor.cursor

    // 如果有选中文本则获取选中内容，否则传空字符串
    val selectedText =
        if (cursor.isSelected) {
          text.subSequence(cursor.left().index, cursor.right().index).toString()
        } else {
          ""
        }

    // 计算弹窗显示的物理位置（光标底部）
    val leftLine = cursor.leftLine
    val leftCol = cursor.leftColumn

    val layoutOffset = editor.layout.getCharLayoutOffset(leftLine, leftCol)
    val screenPos = IntArray(2)
    editor.getLocationInWindow(screenPos)

    val posX =
        (screenPos[0] + editor.measureTextRegionOffset() + layoutOffset[1] - editor.offsetX).toInt()
    // 加上行高，使其显示在光标下方
    val posY = (screenPos[1] + layoutOffset[0] - editor.offsetY + editor.rowHeight).toInt()

    // 实例化并显示系统动作浮窗，动态从 data 获取 context
    val popup = SystemTextActionsPopup(data.requireContext(), editor, selectedText)
    popup.show(editor, posX, posY)

    return true
  }

  override fun dismissOnAction(): Boolean {
    return true
  }
}
