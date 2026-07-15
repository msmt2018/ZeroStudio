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
package com.itsaky.androidide.ui.screen.git

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.itsaky.androidide.fragments.git.PuppyGitIntegration

/**
 * Compose 宿主 Fragment — 替代旧 [com.itsaky.androidide.fragments.git.GitHostFragment]。
 *
 * 仅做两件事:
 * 1. 在 [onCreateView] 中初始化 puppygit 运行时 ([PuppyGitIntegration.ensureReadyForAndroidIDE])
 * 2. 挂载 [GitHostScreen] Composable
 *
 * 不再使用 ViewPager2 + TabLayout + 11 个子 Fragment 的套娃架构。
 * 全部 git 页面直接使用 puppygit 的 Compose Screen, 通过 Compose Navigation 管理页面跳转。
 *
 * @author android_zero
 */
class ComposeHostFragment : Fragment() {

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    // 初始化 puppygit 运行时 (native 库 + dbContainer + 真实路径)
    // navController/scrollBehavior 由 GitHostScreen 在 Composable 中设置
    PuppyGitIntegration.ensureReadyForAndroidIDE(requireContext())

    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(
          ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
      )
      setContent {
        GitHostScreen()
      }
    }
  }
}
