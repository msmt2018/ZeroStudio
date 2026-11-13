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

package com.itsaky.androidide.cursor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * 光标历史记录管理器。 用于实现类似撤销/重做的光标位置回退与前进功能。
 *
 * @author android_zero
 */
object CursorHistoryManager {

  class Tracker(editor: CodeEditor) {
    private val editorRef = WeakReference(editor)
    private val receipt: SubscriptionReceipt<SelectionChangeEvent>
    val history = mutableListOf<CharPosition>()
    var currentIndex = -1
    var isNavigating = false

    init {
      receipt =
          editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            onSelectionChanged(event)
          }
      // 强行记录初始位置，防止全新文件刚打开时无记录
      recordPosition(editor.cursor.left().fromThis())
    }

    private fun onSelectionChanged(event: SelectionChangeEvent) {
      if (isNavigating) return
      if (event.cause == SelectionChangeEvent.CAUSE_TEXT_MODIFICATION) {
        return
      }
      recordPosition(event.left.fromThis())
    }

    private fun recordPosition(pos: CharPosition) {
      if (currentIndex >= 0 && currentIndex < history.size) {
        val last = history[currentIndex]
        // 连续重复位置不重复入栈
        if (last.line == pos.line && last.column == pos.column) {
          return
        }
      }
      // 如果中途发生跳转，裁剪掉未来的记录
      if (currentIndex < history.size - 1 && currentIndex >= 0) {
        history.subList(currentIndex + 1, history.size).clear()
      }
      history.add(pos)
      if (history.size > 100) {
        history.removeAt(0)
      } else {
        currentIndex++
      }

      notifyStateChanged()
    }

    fun goBack() {
      if (currentIndex > 0) {
        currentIndex--
        navigate()
        notifyStateChanged()
      }
    }

    fun goForward() {
      if (currentIndex < history.size - 1) {
        currentIndex++
        navigate()
        notifyStateChanged()
      }
    }

    private fun navigate() {
      val editor = editorRef.get() ?: return
      isNavigating = true
      val pos = history[currentIndex]
      if (editor.text.isValidPosition(pos)) {
        editor.setSelection(pos.line, pos.column)
        editor.ensurePositionVisible(pos.line, pos.column)
      }
      isNavigating = false
    }

    private fun notifyStateChanged() {
      editorRef.get()?.context?.findActivity()?.invalidateOptionsMenu()
    }

    fun canGoBack() = currentIndex > 0

    fun canGoForward() = currentIndex >= 0 && currentIndex < history.size - 1

    fun release() {
      receipt.unsubscribe()
      history.clear()
      currentIndex = -1
    }
  }

  private val trackers = WeakHashMap<CodeEditor, Tracker>()

  fun getTracker(editor: CodeEditor): Tracker {
    var tracker = trackers[editor]
    if (tracker == null) {
      tracker = Tracker(editor)
      trackers[editor] = tracker
    }
    return tracker
  }

  fun removeTracker(editor: CodeEditor) {
    trackers.remove(editor)?.release()
  }
}

private tailrec fun Context.findActivity(): Activity? {
  return when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
}
