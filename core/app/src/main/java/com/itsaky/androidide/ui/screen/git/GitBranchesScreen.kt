package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 分支管理界面 — 一比一还原 SVG 设计图 "分支管理"。
 *
 * Header(分支标题 + 下划线 + 三点菜单) -> 工具栏(All Branches + 新建/搜索) -> LOCAL / REMOTE 分支列表。
 * 使用静态 mock 数据展示布局。
 */
@Composable
fun GitBranchesScreen() {
    Column(modifier = Modifier.fillMaxSize().background(GitColors.bg)) {
        // Header: 高 40dp, "分支" 居中 primary + 下方 primary 下划线 + 右侧三点菜单
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(GitColors.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "分支", style = GitTypography.titleMedium, color = GitColors.primary)
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.width(24.dp).height(2.dp).background(GitColors.primary))
            }
            Text(
                text = "⋮",
                style = GitTypography.body,
                color = GitColors.outline,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }

        // 工具栏: 高 44dp, surfaceContainer + #333 底部分隔线
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(GitColors.surfaceContainer)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "All Branches (5)", style = GitTypography.body, color = GitColors.outline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BranchToolButton(text = "+", tint = GitColors.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    BranchToolButton(text = "⌕", tint = GitColors.onSurface)
                }
            }
            GitDivider()
        }

        // 内容区
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // LOCAL
            Text(text = "LOCAL", style = GitTypography.bodySmall, color = GitColors.primary)
            Spacer(modifier = Modifier.height(8.dp))

            // 当前分支 main
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(GitColors.surfaceContainer, RoundedCornerShape(8.dp))
                    .border(1.dp, GitColors.primaryDim, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "main", style = GitTypography.titleMedium, color = GitColors.primary)
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 20.dp)
                        .background(GitColors.primaryDim, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "Current", style = GitTypography.bodySmall, color = GitColors.onSurface)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 普通分支 dev
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(GitColors.surfaceContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "dev", style = GitTypography.titleMedium, color = GitColors.onSurface)
                Box(
                    modifier = Modifier
                        .size(width = 66.dp, height = 24.dp)
                        .border(1.dp, Color(0xFF555555), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "Checkout", style = GitTypography.bodySmall, color = GitColors.outline)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 简化分支项 feature/ui
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(GitColors.surfaceContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "feature/ui", style = GitTypography.titleMedium, color = GitColors.onSurface)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // REMOTE
            Text(text = "REMOTE", style = GitTypography.bodySmall, color = GitColors.outline)
            Spacer(modifier = Modifier.height(8.dp))

            // 远程分支 origin/main
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(GitColors.surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "origin/main", style = GitTypography.body, color = GitColors.onSurfaceVariant)
            }
        }
    }
}

/** 分支工具栏 28x28 rx=4 #383838 图标按钮。 */
@Composable
private fun BranchToolButton(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(GitColors.surfaceBright, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = GitTypography.body, color = tint)
    }
}
