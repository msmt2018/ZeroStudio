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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.catpuppyapp.puppygit.ui.theme.InitContent
import com.itsaky.androidide.R
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.resources.R as ResR

/**
 * 所有 Git 子页面的基类。
 *
 * @author android_zero
 */
abstract class BaseGitPageFragment : Fragment() {

  private val uiEventViewModel: GitUiEventViewModel by lazy {
    ViewModelProvider(requireActivity())[GitUiEventViewModel::class.java]
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    runCatching { GitRuntimeBootstrap.ensureLoaded() }

    setupToolbar()
  }

  /** 子类必须实现此方法来配置自己的工具栏按钮。 */
  abstract fun setupToolbar()

  /**
   * 向工具栏添加一个图标按钮。
   *
   * @param iconRes 图标资源 ID
   * @param tooltip 提示文本
   * @param onClick 点击回调
   * @return 返回创建的按钮 View，以便设置长按监听等其他属性
   */
  protected fun addToolbarAction(iconRes: Int, tooltip: String, onClick: () -> Unit): View {
    val context = requireContext()

    val button =
        ImageButton(context).apply {
          layoutParams =
              LinearLayout.LayoutParams(
                  resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_size),
                  resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_size),
              )
          setImageResource(iconRes)
          // background = null // 透明背景
          val outValue = android.util.TypedValue()
          context.theme.resolveAttribute(
              android.R.attr.selectableItemBackgroundBorderless,
              outValue,
              true,
          )
          setBackgroundResource(outValue.resourceId)

          contentDescription = tooltip
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            tooltipText = tooltip
          }
          setOnClickListener { onClick() }

          val padding = resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_padding)
          setPadding(padding, padding, padding, padding)

          val typedValue = android.util.TypedValue()
          context.theme.resolveAttribute(
              com.google.android.material.R.attr.colorOnSurface,
              typedValue,
              true,
          )
          setColorFilter(typedValue.data)
        }

    findToolbarContainer()?.addView(button)
    return button
  }

  protected fun addToolbarSeparator() {
    val context = requireContext()
    val separator = View(context).apply {
      layoutParams = LinearLayout.LayoutParams(
          1.dpToPx(),
          resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_size),
      ).apply {
        val margin = 4.dpToPx()
        marginStart = margin
        marginEnd = margin
      }
      setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, ResR.color.git_toolbar_separator))
    }
    findToolbarContainer()?.addView(separator)
  }

  protected fun addToolbarSectionLabel(text: String) {
    val context = requireContext()
    val label = android.widget.TextView(context).apply {
      layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_size),
      ).apply {
        val margin = 8.dpToPx()
        marginStart = margin
        marginEnd = margin
      }
      setText(text)
      setTextColor(androidx.core.content.ContextCompat.getColor(context, ResR.color.git_toolbar_label))
      textSize = 10f
      gravity = android.view.Gravity.CENTER_VERTICAL
    }
    findToolbarContainer()?.addView(label)
  }

  private fun Int.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
  }

  protected fun addToolbarCustomView(view: View) {
    findToolbarContainer()?.addView(view)
  }

  protected fun openExternalLink(url: String, errorTip: String = "No browser available") {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(requireContext(), errorTip, Toast.LENGTH_SHORT).show()
      uiEventViewModel.emit(GitUiEvent.Error(errorTip))
    }
  }

  /**
   * 打开 web 链接,如果没有则弹出 toast。
   * 2a2-B 共享:替换 3 个 fragment 中重复的 openIfAny 私有方法。
   */
  protected fun openWebLinkOrToast(url: String?, emptyMsg: String = "No link available") {
    if (url.isNullOrBlank()) {
      Toast.makeText(requireContext(), emptyMsg, Toast.LENGTH_SHORT).show()
      return
    }
    openExternalLink(url)
  }

  protected fun emitGitOperation(section: String, action: String) {
    uiEventViewModel.emit(GitUiEvent.Operation(section, action))
  }

  override fun onDestroyView() {
    findToolbarContainer()?.removeAllViews()
    super.onDestroyView()
  }

  /**
   * 创建一个承载 puppygit Compose 内容的 [ComposeView]。
   *
   * `setContent` 块内部会先调 [PuppyGitIntegration.ensureReady] 触发
   * `AppModel.init_forPreview()`，再包一层 [InitContent] 提供 Theme /
   * LocalActivity 等 CompositionLocal。
   *
   * 调用方负责把返回的 [ComposeView] 放到布局里（2a2 之后 fragment 改用
   * puppygit screen 时，会把现有 RecyclerView 替换成这个 ComposeView）。
   *
   * @param content puppygit 的 Composable 内容（如 `BranchListScreen(...)`）
   * @return 配置好的 [ComposeView]
   */
  protected fun setGitContent(content: @Composable () -> Unit): ComposeView {
    val ctx = requireContext()
    return ComposeView(ctx).apply {
      setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        PuppyGitIntegration.ensureReady()
        InitContent(context = ctx.applicationContext) { content() }
      }
    }
  }

  /**
   * 创建一个承载**独立设计** Compose 内容的 [ComposeView]。
   *
   * 与 [setGitContent] 的区别:
   * - 不调 [PuppyGitIntegration.ensureReady] / 不包 puppygit 的 [InitContent]
   * - 不渲染 puppygit Screen (避免 "俄罗斯套娃" —— 双 toolbar / 双返回按钮)
   * - 只用 AndroidIDE 自己的 [MaterialTheme] 包裹, git core 数据加载通过
   *   [Libgit2Helper] 直接调用 (与 puppygit screen 一比一复刻调用方式)
   *
   * 用法:
   * ```
   * val compose = setIdeContent { CommitHistoryScreen(workdir) }
   * binding.gitContentContainer.addView(compose)
   * ```
   *
   * @param content 独立设计的 Composable 内容
   * @return 配置好的 [ComposeView]
   */
  protected fun setIdeContent(content: @Composable () -> Unit): ComposeView {
    val ctx = requireContext()
    return ComposeView(ctx).apply {
      setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        androidx.compose.material3.MaterialTheme { content() }
      }
    }
  }

  private fun findToolbarContainer(): LinearLayout? {
    val rootView = view ?: return null
    val scrollView = rootView.findViewById<HorizontalScrollView>(R.id.git_mini_toolbar_scroll)
    return scrollView?.findViewById(R.id.git_mini_toolbar_container)
  }

  /**
   * 解析当前打开的工程目录绝对路径。
   *
   * 2a2 之后多个子 fragment 都需要拿到 workdir 才能让 puppygit 解析 repoId，
   * 把这段逻辑从 `GitChangesFragment` / `GitBranchesFragment` 上提到基类共享。
   *
   * <p>治本修复（v20260610 之后）：此方法不再需要任何 `runCatching` 包裹。
   * 历史版本里 `IProjectManager.projectDirPath` getter 在工程未打开时抛
   * `IllegalStateException("Cannot get project directory. Path has not been set.")`，
   * 导致 `GitChangesFragment.startChangesWatcher` 协程在第一个 tick 整条死掉。
   * 修复后 `projectDirPath` / `getWorkspace()` 都返回 nullable，可以用干净的 null chain。
   *
   * <p>性能：消除 `runCatching` 嵌套（原先是 2 层 runCatching 套娃），
   * 单次 tick 的开销从 ~2 个 try/catch 帧降为 0。
   *
   * @return 工程目录绝对路径；当前没有打开工程时返回 `null`
   */
  protected fun resolveWorkspaceDirPath(): String? {
    val projectManager = IProjectManager.getInstance()
    // 优先从 workspace 拿（最常见的已打开工程路径）；拿不到再回退到 projectDir。
    // 两者现在都是 nullable，可以直接用 safe-call 链。
    return projectManager.getWorkspace()?.getProjectDir()?.path
        ?.takeIf { it.isNotBlank() }
        ?: projectManager.projectDirPath?.takeIf { it.isNotBlank() }
  }
}
