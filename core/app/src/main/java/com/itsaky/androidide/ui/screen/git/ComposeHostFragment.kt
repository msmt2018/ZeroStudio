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
import androidx.lifecycle.lifecycleScope
import com.itsaky.androidide.fragments.git.PuppyGitIntegration
import kotlinx.coroutines.launch

/**
 * Compose 宿主 Fragment — 替代旧 GitHostFragment (已删除)。
 *
 * 做三件事:
 * 1. 在 [onCreateView] 中同步初始化 puppygit 运行时第一阶段
 *    ([PuppyGitIntegration.ensureReadyForAndroidIDE] — native 库 + dbContainer + 真实路径)
 * 2. 在协程中异步初始化第二阶段 ([PuppyGitIntegration.ensureReadyForAndroidIDEAsync]
 *    — SettingsUtil/CertMan/Lg2HomeUtils 等基础设施)
 * 3. 挂载 [GitHostScreen] Composable
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
    // 第一阶段: 同步初始化 (native 库 + dbContainer + 真实路径)
    // navController/scrollBehavior 由 GitHostScreen 在 Composable 中设置
    PuppyGitIntegration.ensureReadyForAndroidIDE(requireContext())

    // 第二阶段: 异步初始化 (SettingsUtil/CertMan/Lg2HomeUtils 等)
    // 在协程中执行, 不阻塞 UI; 幂等, Fragment 重建时不会重复执行
    // 注意: 用 Fragment.lifecycleScope 而非 viewLifecycleOwner.lifecycleScope,
    // 因 onCreateView 中 view 尚未创建, viewLifecycleOwner 不可用
    lifecycleScope.launch {
      PuppyGitIntegration.ensureReadyForAndroidIDEAsync()
    }

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
