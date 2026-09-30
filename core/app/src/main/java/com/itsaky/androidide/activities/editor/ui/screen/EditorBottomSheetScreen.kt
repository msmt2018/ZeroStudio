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
 *  along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.activities.editor.ui.screen

import android.zero.studio.widget.editor.symbolinput.AdvancedSymbolInputView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.itsaky.androidide.ui.EdgeSnapBubbleView

/**
 * `header_container` (ViewFlipper) 第一页: `layout_editor_build_status`。
 * 对应 EditorBottomSheet.CHILD_HEADER。
 */
const val EDITOR_BOTTOM_SHEET_CHILD_HEADER = 0

/**
 * `header_container` (ViewFlipper) 第二页: `layout_editor_bottom_action`。
 * 对应 EditorBottomSheet.CHILD_ACTION。
 */
const val EDITOR_BOTTOM_SHEET_CHILD_ACTION = 1

/**
 * `layout_editor_bottom_sheet.xml` 的 Jetpack Compose 一比一复刻。
 *
 * 结构映射 (XML → Compose):
 *
 * 根 `LinearLayout` (vertical, 透明背景, clipChildren=false, clipToPadding=false)
 * ├─ `floating_header_area` (悬浮 Header, vertical)
 * │  ├─ `page_switch_gesture_bubble` (EdgeSnapBubbleView, 24dp, 透明背景)
 * │  │   → [AndroidView] 互操作, 通过 [configureBubble] 注入手势/形态配置
 * │  ├─ `header_content_wrapper` (FrameLayout, clipChildren=true, 3D 抽拉动画容器)
 * │  │   → [headerContentVisible] 控制 + clipToBounds
 * │  │   └─ `header_inner_content` (RelativeLayout, background=colorSurface)
 * │  │      ├─ `cardView` (MaterialCardView, cornerRadius=24dp, marginH=16dp,
 * │  │      │    strokeWidth=0, elevation=0, scale=0.9, 无内容 → 0 高装饰卡片)
 * │  │      ├─ `border` (layout_divider_horizontal, 0.1dp, colorOutline)
 * │  │      ├─ `header_container` (ViewFlipper, below=border, toStartOf=tv_cursor_position)
 * │  │      │   ├─ include `layout_editor_build_status` → [EditorBuildStatusScreen]
 * │  │      │   └─ include `layout_editor_bottom_action` → [EditorBottomActionScreen]
 * │  │      │      (由 [headerPage] 切换, 对应 ViewFlipper.displayedChild)
 * │  │      └─ `tv_cursor_position` (alignParentEnd, marginEnd=16dp,
 * │  │           monospace, BodySmall, colorOutline)
 * │  ├─ `header_divider` (0.5dp, colorOutlineVariant)
 * │  └─ `external_symbol_input_view` (AdvancedSymbolInputView, elevation=4dp,
 * │        background=colorSurface) → [AndroidView] 互操作
 * └─ `drawer_content_area` (RelativeLayout, background=colorSurface)
 *    ├─ `tabs` (TabLayout, AppTheme.TabLayout[scrollable/start/3dp 底部指示器])
 *    │   → [ScrollableTabRow] + 自绘 3dp 底部指示器
 *    ├─ `pager` (ViewPager2, isUserInputEnabled=false, background=colorBackground)
 *    │   → userScrollEnabled=false 的 [HorizontalPager] + [pagerContent] 内容槽
 *    └─ `space_bottom` (Space, alignParentBottom, 高度运行时动态设置)
 *        → 高度为 [bottomInset] 的 [Spacer]
 */
@Composable
fun EditorBottomSheetScreen(
    modifier: Modifier = Modifier,
    headerPage: Int = EDITOR_BOTTOM_SHEET_CHILD_HEADER,
    statusText: String = "",
    actionText: String = "",
    actionProgress: Int = 0,
    cursorPosition: String = "1:1",
    headerContentVisible: Boolean = true,
    configureBubble: (EdgeSnapBubbleView) -> Unit = {},
    configureSymbolInput: (AdvancedSymbolInputView) -> Unit = {},
    tabTitles: List<String> = emptyList(),
    selectedTabIndex: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    bottomInset: Dp = 0.dp,
    pagerContent: @Composable (Int) -> Unit = {},
) {
  val tabCount = tabTitles.size

  // 内部 tab 状态: 外部 selectedTabIndex 变化时同步; 点击 tab 时先更新内部再回调外部。
  // 越界 clamp 参考 EditorToolboxFragment 的教训 (空列表/越界会让 ScrollableTabRow
  // 在 indicatorPositions[selectedTabIndex] 上 OOB)。
  var currentTab by remember {
    mutableIntStateOf(selectedTabIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0)))
  }
  LaunchedEffect(selectedTabIndex, tabCount) {
    currentTab = selectedTabIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
  }

  // pager: ViewPager2, isUserInputEnabled=false
  val pagerState = rememberPagerState(initialPage = currentTab) { tabCount }
  LaunchedEffect(currentTab) {
    if (pagerState.currentPage != currentTab) {
      pagerState.scrollToPage(currentTab)
    }
  }

  // 根 LinearLayout(vertical, 透明背景, clipChildren=false — Compose 默认不裁剪子内容)
  Column(modifier = modifier.fillMaxSize()) {
    // ==== Floating Header Area ====
    Column(modifier = Modifier.fillMaxWidth()) {
      // page_switch_gesture_bubble: EdgeSnapBubbleView(match_parent × 24dp, 透明背景)
      AndroidView(
          factory = { context -> EdgeSnapBubbleView(context).also(configureBubble) },
          modifier = Modifier.fillMaxWidth().height(24.dp))

      // header_content_wrapper: FrameLayout(clipChildren=true), 承载 3D 抽拉显示/隐藏
      if (headerContentVisible) {
        Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
          // header_inner_content: RelativeLayout(background=?attr/colorSurface)
          Box(
              modifier =
                  Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                // cardView: 装饰性 MaterialCardView(corner=24dp, marginH=16dp, scale=0.9,
                // strokeWidth=0, elevation=0, colorSurface)。XML 中 wrap_content 且无
                // 子内容 → 实际高度为 0, 仅作为抽拉动画的 alpha 同步目标保留结构。
                Surface(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp)
                            .graphicsLayer {
                              scaleX = 0.9f
                              scaleY = 0.9f
                            },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp) {}

                Column(modifier = Modifier.fillMaxWidth()) {
                  // border: layout_divider_horizontal 高度覆写为 0.1dp(colorOutline)
                  Box(
                      modifier =
                          Modifier.fillMaxWidth()
                              .height(0.1.dp)
                              .background(MaterialTheme.colorScheme.outline))

                  // header_container(ViewFlipper, below=border, toStartOf=tv_cursor_position)
                  // + tv_cursor_position(alignParentEnd, marginEnd=16dp)
                  Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                      when (headerPage) {
                        EDITOR_BOTTOM_SHEET_CHILD_ACTION ->
                            EditorBottomActionScreen(
                                actionText = actionText, actionProgress = actionProgress)
                        else -> EditorBuildStatusScreen(statusText = statusText)
                      }
                    }
                    // tv_cursor_position: monospace, BodySmall, colorOutline, 垂直居中
                    Text(
                        text = cursorPosition,
                        modifier =
                            Modifier.align(Alignment.CenterVertically).padding(end = 16.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                  }
                }
              }
        }
      }

      // header_divider: 细分隔线(0.5dp, colorOutlineVariant)
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .height(0.5.dp)
                  .background(MaterialTheme.colorScheme.outlineVariant))

      // external_symbol_input_view: AdvancedSymbolInputView(match_parent × wrap_content,
      // elevation=4dp, background=colorSurface)。Compose 中后组合的兄弟节点天然绘制在
      // 上层, 与 XML 的 elevation 遮挡语义一致。
      AndroidView(
          factory = { context ->
            AdvancedSymbolInputView(context).also(configureSymbolInput)
          },
          modifier =
              Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface))
    }

    // ==== Drawer Content Area ====
    // drawer_content_area: RelativeLayout(match_parent × match_parent, colorSurface)
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)) {
          Column(modifier = Modifier.fillMaxSize()) {
            // tabs: TabLayout(AppTheme.TabLayout: scrollable + start + 3dp 底部指示器
            // + 非全宽指示器)。空列表时不渲染, 规避 ScrollableTabRow 越界崩溃。
            if (tabCount > 0) {
              ScrollableTabRow(
                  selectedTabIndex = currentTab.coerceIn(0, tabCount - 1),
                  modifier = Modifier.fillMaxWidth(),
                  edgePadding = 0.dp,
                  indicator = { tabPositions ->
                    // tabIndicatorHeight=3dp, tabIndicatorGravity=bottom,
                    // tabIndicatorFullWidth=false
                    if (currentTab < tabPositions.size) {
                      val position = tabPositions[currentTab]
                      Box(
                          modifier =
                              Modifier.fillMaxWidth()
                                  .wrapContentSize(Alignment.BottomStart)
                                  .offset(x = position.left)
                                  .width(position.width)
                                  .height(3.dp)
                                  .background(MaterialTheme.colorScheme.primary))
                    }
                  }) {
                    tabTitles.forEachIndexed { index, title ->
                      Tab(
                          selected = currentTab == index,
                          onClick = {
                            currentTab = index
                            onTabSelected(index)
                          },
                          text = {
                            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                          })
                    }
                  }
            }

            // pager: ViewPager2(below=tabs, above=space_bottom, colorBackground,
            // isUserInputEnabled=false)
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background),
                userScrollEnabled = false) { page ->
                  pagerContent(page)
                }

            // space_bottom: Space(alignParentBottom, 高度由运行时 WindowInsets/IME 动态设置)
            Spacer(modifier = Modifier.fillMaxWidth().height(bottomInset))
          }
        }
  }
}
