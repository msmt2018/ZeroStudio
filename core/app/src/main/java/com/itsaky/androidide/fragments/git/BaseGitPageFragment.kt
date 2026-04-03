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
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.itsaky.androidide.R

/**
 * 所有 Git 子页面的基类。
 *
 * @author android_zero
 */
abstract class BaseGitPageFragment : Fragment() {

  protected var toolbarContainer: LinearLayout? = null
  private val uiEventViewModel: GitUiEventViewModel by lazy {
    ViewModelProvider(requireActivity())[GitUiEventViewModel::class.java]
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    runCatching { GitRuntimeBootstrap.ensureLoaded() }

    val scrollView = view.findViewById<HorizontalScrollView>(R.id.git_mini_toolbar_scroll)
    toolbarContainer = scrollView?.findViewById(R.id.git_mini_toolbar_container)

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
                  resources.getDimensionPixelSize(R.dimen.git_toolbar_icon_size),
                  resources.getDimensionPixelSize(R.dimen.git_toolbar_icon_size),
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

          val padding = resources.getDimensionPixelSize(R.dimen.git_toolbar_icon_padding)
          setPadding(padding, padding, padding, padding)

          val typedValue = android.util.TypedValue()
          context.theme.resolveAttribute(
              com.google.android.material.R.attr.colorOnSurface,
              typedValue,
              true,
          )
          setColorFilter(typedValue.data)
        }

    toolbarContainer?.addView(button)
    return button
  }

  protected fun addToolbarCustomView(view: View) {
    toolbarContainer?.addView(view)
  }

  protected fun openExternalLink(url: String, errorTip: String = "No browser available") {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(requireContext(), errorTip, Toast.LENGTH_SHORT).show()
      uiEventViewModel.emit(GitUiEvent.Error(errorTip))
    }
  }

  protected fun emitGitOperation(section: String, action: String) {
    uiEventViewModel.emit(GitUiEvent.Operation(section, action))
  }
}
