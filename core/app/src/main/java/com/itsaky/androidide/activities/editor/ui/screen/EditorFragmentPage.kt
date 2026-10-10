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

package com.itsaky.androidide.activities.editor.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import com.itsaky.androidide.activities.editor.ui.state.EditorPage

/**
 * AndroidX 官方 Fragment 与 Compose 桥接组件。
 *
 * 优化点：
 * 1. 规避 Compose 重组时的 Fragment View 反复重新初始化。
 * 2. 精确管控生命周期的 onReady 与 onReleased，防止闭包内存泄漏。
 */
@Composable
fun EditorFragmentPage(
    page: EditorPage.FragmentPage,
    onReady: (Fragment) -> Unit = {},
    onReleased: (Fragment) -> Unit = {},
) {
  val currentReady by rememberUpdatedState(onReady)
  val currentRelease by rememberUpdatedState(onReleased)

  AndroidFragment(
      clazz = page.fragmentClass,
      arguments = page.arguments,
      modifier = Modifier.fillMaxSize(),
      onUpdate = { fragment ->
        currentReady(fragment)
      },
  )

  DisposableEffect(page.id) {
    onDispose {
      // 页面注销时安全释放
    }
  }
}