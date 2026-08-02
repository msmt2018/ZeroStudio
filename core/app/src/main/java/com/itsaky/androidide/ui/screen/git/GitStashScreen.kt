package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp

/**
 * Stash 暂存管理界面。
 *
 * 对应 SVG 设计图 "暂存管理"：
 * 顶部 "暂存" 标题 Header + primary 紫色下划线，工具栏显示 "Saved Changes (3)"
 * 计数与 "Clear All" 按钮，下方为 Stash 卡片列表（stash@{0/1/2}），每张卡片含
 * 标题、message、modified 信息以及 Apply / Drop 操作按钮。使用静态 mock 数据。
 */
@Composable
fun GitStashScreen() {
    val stashes = listOf(
        StashItem("stash@{0}", "Just now", "WIP on feature: Login UI crash fix", "3 files modified", true),
        StashItem("stash@{1}", "2 days ago", "WIP on main: Refactor adapters", "2 files modified", false),
        StashItem("stash@{2}", "5 days ago", "WIP on develop: Update dependencies", "5 files modified", false)
    )

    Column(modifier = Modifier.fillMaxSize().background(GitColors.bg)) {
        // Header：暂存 居中 + primary 下划线
        Column(modifier = Modifier.fillMaxWidth().height(40.dp).background(GitColors.surfaceContainer)) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂存",
                    color = GitColors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(GitColors.primary))
        }

        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(GitColors.surfaceContainer)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Changes (3)",
                style = GitTypography.bodySmall.copy(color = GitColors.outline)
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 28.dp)
                    .background(GitColors.surfaceBright, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Clear All",
                    color = GitColors.onSurface,
                    fontSize = 11.sp
                )
            }
        }
        // 工具栏底部分隔线
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GitColors.divider))

        // 内容区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            stashes.forEach { item ->
                StashCard(item)
            }
        }
    }
}

private data class StashItem(
    val name: String,
    val time: String,
    val message: String,
    val modified: String,
    val isLatest: Boolean
)

/**
 * 单个 Stash 卡片：header（名称 + 时间）+ message + modified 信息 + Apply / Drop 按钮。
 */
@Composable
private fun StashCard(item: StashItem) {
    GitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = if (item.isLatest) {
                        GitTypography.monoPrimary.copy(fontWeight = FontWeight.Bold)
                    } else {
                        GitTypography.mono.copy(color = GitColors.onSurface)
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = item.time,
                    style = GitTypography.bodySmall.copy(color = GitColors.outline)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.message,
                style = GitTypography.body.copy(color = GitColors.onSurface)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.modified,
                style = GitTypography.bodySmall.copy(color = GitColors.outline)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Apply
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 28.dp)
                        .background(GitColors.primary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Apply",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                // Drop
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 28.dp)
                        .background(GitColors.error, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Drop",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
