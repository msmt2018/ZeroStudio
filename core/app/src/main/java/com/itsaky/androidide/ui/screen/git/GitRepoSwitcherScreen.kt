package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 仓库切换 / 项目列表界面 (Projects)。
 *
 * 对应 SVG 设计图 "1. 项目列表 (Projects)"：
 * 顶部 GitTabBar（项目/变更/历史/分支/协作/搜索，默认选中"项目"），下方为项目卡片
 * 列表。每张卡片左侧可选 primary 紫色边条 + 圆角图标区（首字母），右侧展示项目名、
 * 路径与状态标签（Ahead / Modified / Clean tree）。使用静态 mock 数据。
 */
@Composable
fun GitRepoSwitcherScreen() {
    Column(modifier = Modifier.fillMaxSize().background(GitColors.bg)) {
        GitTabBar(
            tabs = listOf("项目", "变更", "历史", "分支", "协作", "搜索"),
            selectedTabIndex = 0,
            onTabSelected = {}
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProjectCard(
                letter = "A",
                letterColor = GitColors.primary,
                iconBg = GitColors.primary.copy(alpha = 0.15f),
                showEdge = true,
                title = "AndroidIDE-Core",
                path = "~/Projects/IDE",
                tags = listOf(
                    "↑1 Ahead" to GitColors.success,
                    "● 2 Modified" to GitColors.warning
                )
            )
            ProjectCard(
                letter = "L",
                letterColor = GitColors.outline,
                iconBg = GitColors.divider,
                showEdge = false,
                title = "lib-utils",
                path = "~/Projects/libs",
                tags = listOf("Clean tree" to GitColors.outline)
            )
        }
    }
}

/**
 * 项目卡片：左侧可选 primary 边条 + 圆角图标区（首字母）+ 右侧标题/路径/状态标签。
 */
@Composable
private fun ProjectCard(
    letter: String,
    letterColor: Color,
    iconBg: Color,
    showEdge: Boolean,
    title: String,
    path: String,
    tags: List<Pair<String, Color>>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(GitColors.surfaceContainer, RoundedCornerShape(8.dp))
    ) {
        // 左侧 primary 紫色边条
        if (showEdge) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(GitColors.primary)
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标区
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBg, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    color = letterColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 右侧文字
            Column {
                Text(
                    text = title,
                    style = GitTypography.titleMedium.copy(color = GitColors.onSurface)
                )
                Text(
                    text = path,
                    style = GitTypography.bodySmall.copy(color = GitColors.outline)
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    tags.forEach { (label, color) ->
                        Text(text = label, color = color, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
