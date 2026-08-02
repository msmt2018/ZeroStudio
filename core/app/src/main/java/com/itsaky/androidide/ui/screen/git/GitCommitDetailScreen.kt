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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Commit 详情界面 — 一比一还原 SVG 设计图 "代码追溯 (File Blame)" 结合 commit 详情。
 *
 * Header(commit hash + Blame Mode) -> Commit 信息卡 -> Changed Files 列表 -> Blame 区(彩色边条 gutter)。
 * 使用静态 mock 数据展示布局。
 */
@Composable
fun GitCommitDetailScreen() {
    val changedFiles = listOf(
        Triple("MainActivity.kt", "app/src/main/...", 'M'),
        Triple("colors.xml", "app/src/res/...", 'M'),
        Triple("OldUtils.java", "", 'D'),
    )

    Column(modifier = Modifier.fillMaxSize().background(GitColors.bg).verticalScroll(rememberScrollState())) {
        // Header: 高 60dp, 左 commit hash(monoPrimary) + 右 "Blame Mode"(primary) + #333 分隔线
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(GitColors.surfaceContainer)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "a1b2c3d", style = GitTypography.monoPrimary)
                Text(text = "Blame Mode", style = GitTypography.bodySmall, color = GitColors.primary, fontWeight = FontWeight.Bold)
            }
            GitDivider()
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Commit 信息卡
            GitCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Refactor UI components",
                        style = GitTypography.titleMedium,
                        color = GitColors.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AndroidZero",
                            style = GitTypography.body,
                            color = GitColors.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "2h ago", style = GitTypography.body, color = GitColors.outline)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "a1b2c3d4e5f6...",
                        style = GitTypography.mono,
                        color = GitColors.outline,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Changed Files (3)", style = GitTypography.bodySmall, color = GitColors.outline)
            Spacer(modifier = Modifier.height(8.dp))

            // 文件列表
            changedFiles.forEach { (name, path, status) ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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

            Spacer(modifier = Modifier.height(16.dp))

            // Blame 区
            BlameBlock(
                edge = GitColors.primary,
                author = "AndroidZero",
                time = "2h ago",
                hash = "a1b2c3d",
                code = "    binding = ActivityMainBinding.inflate(layoutInflater)",
            )
            Spacer(modifier = Modifier.height(8.dp))
            BlameBlock(
                edge = GitColors.success,
                author = "devuser",
                time = "5h ago",
                hash = "8e7f6d5",
                code = "    setContentView(binding.root)",
            )
        }
    }
}

/** 单个 Blame 块: 100dp gutter(#2C2C2C 底 + 2px 彩色边条) + 右侧代码行。 */
@Composable
private fun BlameBlock(edge: Color, author: String, time: String, hash: String, code: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .background(GitColors.gutterBlame),
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(edge),
                )
                Column(modifier = Modifier.padding(6.dp)) {
                    Text(text = author, style = GitTypography.bodySmall, color = GitColors.onSurface, fontWeight = FontWeight.Bold)
                    Text(text = time, style = GitTypography.bodySmall, color = GitColors.outline)
                    Text(text = hash, style = GitTypography.mono.copy(color = edge))
                }
            }
        }
        Text(
            text = code,
            style = GitTypography.mono,
            color = GitColors.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        )
    }
}
