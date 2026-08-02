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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 变更状态界面 (Changes) — 一比一还原 SVG 设计图 "2. 变更状态 (Changes)"。
 *
 * 顶部 Tab 栏 -> 工具栏 (Revert / Push) -> Staged / Unstaged 文件列表 -> 底部 Commit 区。
 * 使用静态 mock 数据展示布局，无真实功能逻辑。
 */
@Composable
fun GitChangesScreen() {
    val tabs = listOf("项目", "变更", "历史", "分支", "协作", "搜索")

    // mock 文件: (文件名, 路径, 状态, 是否选中)
    val unstagedFiles = listOf(
        Triple("MainActivity.kt", "app/src/main/...", 'M') to false,
        Triple("colors.xml", "app/src/res/values/...", 'M') to true,
        Triple("OldUtils.java", "", 'D') to false,
    )

    Column(modifier = Modifier.fillMaxSize().background(GitColors.bg)) {
        GitTabBar(tabs = tabs, selectedTabIndex = 1, onTabSelected = {})

        // 工具栏: 高 40dp, surfaceContainer 半透明, 右侧两个 28x28 图标按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(GitColors.surfaceContainer.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIconButton(text = "↺")
            Spacer(modifier = Modifier.width(8.dp))
            ToolIconButton(text = "↑")
        }

        // 内容区
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Staged
            Text(text = "▼ Staged (0)", style = GitTypography.body, color = GitColors.outline)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No staged files",
                style = GitTypography.bodySmall,
                color = GitColors.outline,
                modifier = Modifier.padding(start = 20.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Unstaged
            Text(text = "▼ Unstaged (3)", style = GitTypography.body, color = GitColors.outline)
            Spacer(modifier = Modifier.height(8.dp))

            unstagedFiles.forEach { (file, checked) ->
                val (name, path, status) = file
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckBox(checked = checked)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = name, style = GitTypography.body, color = GitColors.onSurface)
                            if (path.isNotEmpty()) {
                                Text(text = path, style = GitTypography.bodySmall, color = GitColors.outline)
                            }
                        }
                        StatusBadge(status = status)
                    }
                    GitDivider()
                }
            }
        }

        // 底部 Commit 区
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(GitColors.surfaceContainer, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF444444), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = "Commit message...",
                    style = GitTypography.body,
                    color = GitColors.outline,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(GitColors.primary, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Commit (1)",
                    style = GitTypography.titleMedium,
                    color = Color.Black,
                )
            }
        }
    }
}

/** 28x28 rx=4 #383838 底色的工具图标按钮。 */
@Composable
private fun ToolIconButton(text: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(GitColors.surfaceBright, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = GitTypography.body, color = GitColors.onSurface)
    }
}

/** 16x16 复选框, stroke primary; 选中时 primary 底色 + 黑色对勾。 */
@Composable
private fun CheckBox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(
                if (checked) GitColors.primary else Color.Transparent,
                RoundedCornerShape(2.dp),
            )
            .border(1.dp, GitColors.primary, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(text = "✓", style = GitTypography.bodySmall, color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
