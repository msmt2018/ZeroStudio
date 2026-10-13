package com.itsaky.androidide.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily

/**
 * 现代面向 Jetpack Compose 的 Action 视觉规格描述
 *
 * 封装了标题排版、矢量/动效图标、动态着色、自定义字体及触觉交互。
 */
data class ComposeActionVisuals(
    val imageVector: ImageVector? = null,
    val iconTint: Color? = null,
    val titleColor: Color? = null,
    val fontSizeSp: Float = 13f,
    val fontFamily: FontFamily? = null,
    val enablePressHaptic: Boolean = true,
    val enableScaleAnimation: Boolean = true,
)

/**
 * 现代 Compose 侧边栏 Action 规范接口。
 *
 * 替代旧版基于 [androidx.fragment.app.Fragment] 反射构建的 [SidebarActionItem]，
 * 页面生命周期和视图直接受 Jetpack Compose 与状态机驱动，消除额外布局嵌套与反射开销。
 *
 * @author android_zero
 */
interface ComposeSidebarActionItem : ActionItem {

  /** 提供现代 Compose 视觉与排版样式 */
  val visuals: ComposeActionVisuals
    // get() = ComposeActionVisuals()

  /**
   * 侧边栏抽屉展开后渲染的满屏主界面。
   *
   * @param modifier 外层满屏修饰符
   */
  @Composable
  fun Content(modifier: Modifier)
}