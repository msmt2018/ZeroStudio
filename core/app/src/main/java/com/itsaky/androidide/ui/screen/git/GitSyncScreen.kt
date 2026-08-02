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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.sp

/**
 * Push/Pull 同步 / 协作 (Collaboration) 界面。
 *
 * 对应 SVG 设计图 "协作 (Collaboration)" 的 PR 视图：
 * 顶部 "协作 (Remote)" Header，下方分段控件（Pull Requests / Pipelines）含红色
 * PR 徽章，内容区展示 "Open Requests (2)" 标题与 PR 卡片列表（标题、编号/作者、
 * 构建状态、审核状态、头像堆叠）。使用静态 mock 数据。
 */
@Composable
fun GitSyncScreen() {
    Column(modifier = Modifier.fillMaxSize().background(GitColors.bg)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(GitColors.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "协作 (Remote)",
                style = GitTypography.titleMedium.copy(color = GitColors.onSurface)
            )
        }

        // 分段控件
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 16.dp)
                .background(GitColors.popupBg, RoundedCornerShape(18.dp))
                .border(1.dp, GitColors.outlineVariant, RoundedCornerShape(18.dp))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 激活段 Pull Requests + PR 徽章
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 32.dp)
                    .background(GitColors.primaryDim, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Pull Requests",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // PR 徽章
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(GitColors.badgeRed, RoundedCornerShape(50))
                            .border(1.dp, GitColors.popupBg, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "2",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }
            // 非激活段 Pipelines
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Pipelines",
                    color = GitColors.outline,
                    fontSize = 11.sp
                )
            }
        }

        // 内容区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Open Requests (2)",
                style = GitTypography.bodySmall.copy(color = GitColors.outline)
            )
            PrCard(
                title = "Implement Dark Mode",
                meta = "#45 • opened by AndroidZero",
                showStatus = true,
                showAvatars = true
            )
            PrCard(
                title = "Refactor build scripts",
                meta = "#44 • opened by Contributor",
                showStatus = false,
                showAvatars = false
            )
        }
    }
}

/**
 * PR 卡片：标题/编号/作者，可选构建/审核状态行与头像堆叠。
 */
@Composable
private fun PrCard(
    title: String,
    meta: String,
    showStatus: Boolean,
    showAvatars: Boolean
) {
    GitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = GitTypography.titleMedium.copy(color = GitColors.onSurface)
                    )
                    Text(
                        text = meta,
                        style = GitTypography.bodySmall.copy(color = GitColors.outline)
                    )
                }
                if (showAvatars) {
                    // avatar stack：紫色 AZ 圆 + 灰色空圆
                    Row {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(GitColors.primary, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AZ",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = (-6).dp)
                                .background(GitColors.surfaceBright, RoundedCornerShape(50))
                                .border(1.dp, GitColors.surfaceContainer, RoundedCornerShape(50))
                        )
                    }
                }
            }
            if (showStatus) {
                Spacer(modifier = Modifier.height(8.dp))
                GitDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // success 绿圆 + 对勾
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(GitColors.success.copy(alpha = 0.2f), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            color = GitColors.success,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Build Passing",
                        color = GitColors.success,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "⚠ Needs 1 Review",
                        color = GitColors.warning,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
