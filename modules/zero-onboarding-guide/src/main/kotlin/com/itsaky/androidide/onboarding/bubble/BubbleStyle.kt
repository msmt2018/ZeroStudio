/*
 *  This file is part of ZeroStudio.
 *
 *  ZeroStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ZeroStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with ZeroStudio.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.onboarding.bubble

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 气泡样式 (Bubble style).
 *
 * 所有尺寸 / 颜色 / 文本样式 / 模糊强度都在这里配置.
 * 设计参考 dribbble 顶级设计师的 "磨砂半透明" 风格:
 *  - 偏白半透明底 (white-ish) + 强模糊 + 微颗粒
 *  - 高对比度的标题 + 灰色低饱和度副标题
 *  - 边角微高光 / 微投影 / 微内阴影 增强层次
 *
 * @property glassTint 玻璃底色, 默认偏白半透明
 * @property glassBlurRadius 模糊强度, 单位 dp (Haze 会映射到 BlurEffect)
 * @property grainAlpha 颗粒噪点透明度, [0f, 1f]
 * @property borderColor 描边颜色
 * @property borderWidth 描边宽度
 * @property shadowColor 阴影颜色
 * @property shadowElevation 阴影高度
 * @property contentPadding 内部 padding
 * @property elevation 整体 z 轴高度
 * @property titleStyle 主标题文字样式
 * @property subtitleStyle 副标题文字样式
 * @property maxWidth 最大宽度
 * @property minWidth 最小宽度
 * @property height 高度 (null 表示自适应)
 */
data class BubbleStyle(
  // === 玻璃外观 ===
  val glassTint: Color = Color(0xFFFFFFFF),         // 偏白半透明底
  val glassAlpha: Float = 0.55f,                    // 玻璃底色透明度
  val glassBlurRadius: Dp = 24.dp,                  // 高斯模糊半径
  val grainAlpha: Float = 0.06f,                    // 微颗粒强度

  // === 描边与投影 ===
  val borderColor: Color = Color(0x66FFFFFF),       // 半透明白色描边
  val borderWidth: Dp = 1.dp,
  val shadowColor: Color = Color(0x33000000),       // 微投影
  val shadowElevation: Dp = 12.dp,

  // === 内部布局 ===
  val contentPadding: Dp = 20.dp,
  val elevation: Dp = 8.dp,
  val maxWidth: Dp = 360.dp,
  val minWidth: Dp = 120.dp,
  val height: Dp? = null,                            // null = 自适应

  // === 文本样式 ===
  val titleStyle: TextStyle? = null,                 // null = BubbleDefaults 内部默认
  val subtitleStyle: TextStyle? = null,
  val titleColor: Color = Color(0xFF1A1A1A),
  val subtitleColor: Color = Color(0xB3000000),

  // === 视觉风格变体 ===
  val variant: Variant = Variant.Light,

  // === 内阴影 (高级磨砂效果) ===
  val innerHighlight: Boolean = true,               // 顶部 1px 高光内描边
  val innerHighlightColor: Color = Color(0x55FFFFFF),

  // === 形状相关 ===
  val cornerRadius: Dp = 20.dp,                     // 默认圆角 (RoundedRectangle 用)
) {
  enum class Variant {
    Light,   // 偏白磨砂
    Dark,    // 偏黑磨砂
    Accent,  // 主色调磨砂
    Neutral, // 中性灰磨砂
  }

  companion object {
    /** 默认轻量级气泡样式 */
    val Default = BubbleStyle()

    /** 紧凑型气泡样式 */
    val Compact = BubbleStyle(
      contentPadding = 12.dp,
      maxWidth = 280.dp,
      minWidth = 80.dp,
      cornerRadius = 16.dp,
    )

    /** 顶部长条 (StretchedBar) 推荐样式 */
    val TopBar = BubbleStyle(
      maxWidth = 520.dp,
      minWidth = 200.dp,
      glassBlurRadius = 28.dp,
      grainAlpha = 0.05f,
      cornerRadius = 20.dp,
    )

    /** 底部长条 (StretchedBar) 推荐样式 */
    val BottomBar = BubbleStyle(
      maxWidth = 520.dp,
      minWidth = 200.dp,
      glassBlurRadius = 28.dp,
      grainAlpha = 0.05f,
      cornerRadius = 24.dp,
    )

    /** 大圆角宽卡片 (WideCard) 推荐样式 */
    val WideCard = BubbleStyle(
      maxWidth = 420.dp,
      contentPadding = 24.dp,
      glassBlurRadius = 32.dp,
      grainAlpha = 0.07f,
      cornerRadius = 32.dp,
    )

    /** 小圆点提示 (Circle) 推荐样式 */
    val Tooltip = BubbleStyle(
      maxWidth = 240.dp,
      minWidth = 80.dp,
      contentPadding = 12.dp,
      glassBlurRadius = 20.dp,
      grainAlpha = 0.05f,
      cornerRadius = 14.dp,
    )
  }
}
