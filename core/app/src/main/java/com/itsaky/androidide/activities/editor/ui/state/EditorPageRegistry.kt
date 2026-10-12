// ============================================================================
// FILE: core/app/src/main/java/com/itsaky/androidide/activities/editor/ui/state/EditorPageRegistry.kt
// ============================================================================
/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.activities.editor.ui.state

import android.os.Bundle
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment

/**
 * 现代 Compose 原生页面注册模型（多态支持）。
 * 既支持高性能纯 Compose Screen，也兼容现有存量的 Fragment 工具。
 */
@Immutable
sealed interface EditorPage {
  val id: String
  val title: String

  /** 纯动作触发型 Page（不挂载页面容器，点击即执行动作） */
  data class ActionPage(
      override val id: String,
      override val title: String,
      val onClick: () -> Unit,
  ) : EditorPage

  /** 纯粹的 Compose 声明式界面载体（推荐，性能最高，零内存抖动） */
  data class ScreenPage(
      override val id: String,
      override val title: String,
      val content: @Composable () -> Unit,
  ) : EditorPage

  /** 存量旧体系 Fragment 页面桥接载体（保留兼容） */
  data class FragmentPage(
      override val id: String,
      override val title: String,
      val fragmentClass: Class<out Fragment>,
      val arguments: Bundle? = null,
  ) : EditorPage
}

@Stable
@MainThread
class EditorPageRegistry {
  var pages: List<EditorPage> by mutableStateOf(emptyList())
    private set

  var selectedId: String? by mutableStateOf(null)
    private set

  val selectedPage: EditorPage?
    get() = pages.firstOrNull { it.id == selectedId }

  fun register(page: EditorPage) {
    require(page.id.isNotBlank()) { "Page ID must not be blank" }
    require(pages.none { it.id == page.id }) { "Page already registered: ${page.id}" }
    pages = pages + page
    if (selectedId == null && page !is EditorPage.ActionPage) {
      selectedId = page.id
    }
  }

  fun select(id: String): Boolean {
    val page = pages.firstOrNull { it.id == id } ?: return false
    if (page is EditorPage.ActionPage) {
      page.onClick()
      return true
    }
    selectedId = id
    return true
  }

  fun unregister(id: String): Boolean {
    val index = pages.indexOfFirst { it.id == id }
    if (index < 0) return false
    pages = pages.filterNot { it.id == id }
    if (selectedId == id) {
      selectedId = pages.filterNot { it is EditorPage.ActionPage }.getOrNull(index.coerceAtMost(pages.lastIndex))?.id
    }
    return true
  }

  fun clear() {
    pages = emptyList()
    selectedId = null
  }
}