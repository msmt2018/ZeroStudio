package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Git UI 主题颜色定义。
 *
 * 一比一还原 core/git/UI设计概念图/ 中 SVG 设计图的 MD3 Dark Theme 配色：
 * - 背景/表面：纯黑 #000000 + 深灰表面层级
 * - 主色：紫色 #D0BCFF (primary)，激活底色 #4F378B (primaryDim)
 * - 状态色：success 绿 / warning 橙 / error 红 / info 蓝
 * - Diff：新增绿底 / 删除红底
 * - 分隔线 #333333
 *
 * 所有 Git Compose Screen 共享此配色，确保视觉一致性。
 */
object GitColors {
    val bg = Color(0xFF000000)
    val surface = Color(0xFF1E1E1E)
    val surfaceContainer = Color(0xFF252525)
    val surfaceBright = Color(0xFF383838)
    val popupBg = Color(0xFF2B2930)

    val primary = Color(0xFFD0BCFF)
    val primaryDim = Color(0xFF4F378B)
    val onSurface = Color(0xFFE6E1E5)
    val onSurfaceVariant = Color(0xFFCAC4D0)
    val outline = Color(0xFF8E8E93)
    val outlineVariant = Color(0xFF49454F)

    val success = Color(0xFF81C784)
    val warning = Color(0xFFFFB74D)
    val error = Color(0xFFF28B82)
    val info = Color(0xFF64B5F6)
    val badgeRed = Color(0xFFB3261E)

    // Diff 行颜色
    val diffAddBg = Color(0x2681C784) // rgba(129,199,132,0.15)
    val diffDelBg = Color(0x26E57373) // rgba(229,115,115,0.15)
    val diffAddText = Color(0xFF81C784)
    val diffDelText = Color(0xFFE57373)

    // 冲突颜色
    val theirsBg = Color(0x1AFFB74D) // rgba(255,183,77,0.1)
    val yoursBg = Color(0x1A64B5F6)  // rgba(100,181,246,0.1)

    val divider = Color(0xFF333333)
    val gutterBlame = Color(0xFF2C2C2C)
}

/** Git UI 字体样式，对应 SVG 的 Roboto sans-serif + Roboto Mono monospace。 */
object GitTypography {
    val title = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontSize = 13.sp)
    val bodySmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val mono = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    val monoPrimary = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = GitColors.primary)
}

/**
 * Git 卡片容器：surfaceContainer 底色，rx=8 圆角。
 * 对应 SVG 设计中所有列表项/PR卡/Pipeline卡的统一样式。
 */
@Composable
fun GitCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(GitColors.surfaceContainer, RoundedCornerShape(8.dp))
    ) {
        content()
    }
}

/** #333 分隔线，对应 SVG 设计中列表项之间的分隔线。 */
@Composable
fun GitDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(1.dp).fillMaxWidth().background(GitColors.divider))
}

/**
 * 文件状态徽章：M(修改/蓝) / D(删除/红) / A(新增/绿)。
 * 对应 SVG 变更列表中文件右侧的状态标记。
 */
@Composable
fun StatusBadge(status: Char, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        'M' -> GitColors.info to GitColors.info
        'D' -> GitColors.error to GitColors.error
        'A' -> GitColors.success to GitColors.success
        else -> GitColors.outline to GitColors.outline
    }
    Box(
        modifier = modifier
            .size(width = 20.dp, height = 16.dp)
            .background(bg.copy(alpha = 0.1f), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = status.toString(), style = GitTypography.bodySmall.copy(color = fg))
    }
}

/**
 * 顶部 Tab 栏：对应 SVG 设计中所有界面的顶部 Tab (项目/变更/历史/分支/...)。
 * 激活 Tab 文字 primary 紫色 + 下方 primary 指示线。
 */
@Composable
fun GitTabBar(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = GitColors.surfaceContainer,
        contentColor = GitColors.primary,
        indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(positions[selectedTabIndex]),
                color = GitColors.primary
            )
        },
        divider = {},
        modifier = modifier
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = index == selectedTabIndex,
                onClick = { onTabSelected(index) },
                selectedContentColor = GitColors.primary,
                unselectedContentColor = GitColors.outline
            ) {
                Text(
                    text = title,
                    style = GitTypography.bodySmall,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}
