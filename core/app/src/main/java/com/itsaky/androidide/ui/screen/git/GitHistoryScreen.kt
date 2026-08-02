package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Commit 历史界面 — 一比一还原 SVG 设计图 "3. 历史记录 (History)"。
 *
 * 顶部 Tab 栏 -> 极简时间轴(垂直主线 + 圆点 + 提交信息), 含合并/侧分支绿色连线与右侧标签。
 * 使用静态 mock 数据展示布局。
 */
@Composable
fun GitHistoryScreen() {
    val tabs = listOf("项目", "变更", "历史", "分支", "协作", "搜索")

    val commits = listOf(
        HistoryCommit(
            title = "Refactor UI components",
            hash = "a1b2c3d",
            meta = "2h ago • AndroidZero",
            hashColor = GitColors.primary,
            type = NodeType.NORMAL,
            label = null,
        ),
        HistoryCommit(
            title = "Merge branch 'dev'",
            hash = "8e7f6d5",
            meta = null,
            hashColor = GitColors.primary,
            type = NodeType.MERGE_IN,
            label = null,
        ),
        HistoryCommit(
            title = "Update dependencies",
            hash = "4a5b6c",
            meta = null,
            hashColor = GitColors.success,
            type = NodeType.NORMAL,
            label = "origin/main",
        ),
        HistoryCommit(
            title = "Initial Commit",
            hash = "a000001",
            meta = null,
            hashColor = GitColors.primary,
            type = NodeType.MERGE_BACK,
            label = null,
        ),
    )

    Column(modifier = Modifier.fillMaxSize().background(GitColors.bg)) {
        GitTabBar(tabs = tabs, selectedTabIndex = 2, onTabSelected = {})

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            commits.forEach { commit ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 左侧时间轴 gutter: 垂直主线 + 圆点 + (可选)绿色侧分支
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight(),
                    ) {
                        TimelineCanvas(type = commit.type)
                    }
                    // 右侧提交信息
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, bottom = 20.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = commit.title,
                                style = GitTypography.titleMedium,
                                color = GitColors.onSurface,
                            )
                            if (commit.label != null) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 60.dp, height = 18.dp)
                                        .border(1.dp, Color(0xFF555555), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = commit.label,
                                        style = GitTypography.mono,
                                        color = GitColors.outline,
                                    )
                                }
                            }
                        }
                        Text(
                            text = commit.hash,
                            style = GitTypography.mono.copy(color = commit.hashColor),
                        )
                        if (commit.meta != null) {
                            Text(
                                text = commit.meta,
                                style = GitTypography.bodySmall,
                                color = GitColors.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 时间轴节点类型。 */
private enum class NodeType { NORMAL, MERGE_IN, MERGE_BACK }

/** 单条提交数据。 */
private data class HistoryCommit(
    val title: String,
    val hash: String,
    val meta: String?,
    val hashColor: Color,
    val type: NodeType,
    val label: String?,
)

/** 绘制时间轴: 2dp primary 主线 + r5 primary 圆点(黑描边) + 合并/回流的绿色侧分支。 */
@Composable
private fun TimelineCanvas(type: NodeType) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val dotR = 5.dp.toPx()
        val dotY = 10.dp.toPx()
        val strokePx = 2.dp.toPx()

        // 主线
        drawLine(
            color = GitColors.primary,
            start = Offset(cx, 0f),
            end = Offset(cx, size.height),
            strokeWidth = strokePx,
        )

        // 绿色侧分支
        val greenPath = Path()
        when (type) {
            NodeType.MERGE_IN -> {
                // dev 分支从左上方汇入圆点
                greenPath.moveTo(0f, dotY - 12.dp.toPx())
                greenPath.cubicTo(
                    cx, dotY - 12.dp.toPx(),
                    cx, dotY - 4.dp.toPx(),
                    cx, dotY,
                )
                drawPath(greenPath, GitColors.success, style = Stroke(strokePx))
            }
            NodeType.MERGE_BACK -> {
                // 分支从左下方回流汇入主线
                greenPath.moveTo(0f, dotY + 14.dp.toPx())
                greenPath.cubicTo(
                    cx, dotY + 14.dp.toPx(),
                    cx, dotY + 4.dp.toPx(),
                    cx, dotY,
                )
                drawPath(greenPath, GitColors.success, style = Stroke(strokePx))
            }
            else -> Unit
        }

        // 圆点: primary 填充 + 黑色描边
        drawCircle(GitColors.primary, dotR, Offset(cx, dotY))
        drawCircle(
            color = Color.Black,
            radius = dotR,
            center = Offset(cx, dotY),
            style = Stroke(1.dp.toPx()),
        )
    }
}
