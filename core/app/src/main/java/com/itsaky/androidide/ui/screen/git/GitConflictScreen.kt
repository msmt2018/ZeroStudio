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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 冲突解决界面 (Conflict Resolver)。
 *
 * 对应 SVG 设计图 "冲突解决 (Conflict Resolver)"：
 * 顶部红色警告横条 + 文件名/计数，中部 THEIRS / YOURS 代码版本对比区块与冲突标记
 * 分隔条，底部 Result Preview 占位以及 "Keep Yours" / "Keep Theirs" 两个胶囊操作按钮。
 * 使用静态 mock 数据展示布局，无真实功能逻辑。
 */
@Composable
fun GitConflictScreen() {
    Column(modifier = Modifier.fillMaxSize().background(GitColors.bg)) {
        // 警告 Header：顶部 40dp 红色横条 + 文件名/计数行
        Column(modifier = Modifier.fillMaxWidth().background(GitColors.surfaceContainer)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(GitColors.error),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "! 1 CONFLICT DETECTED",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UserAdapter.kt",
                    style = GitTypography.titleMedium.copy(color = GitColors.onSurface)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "1/1",
                    style = GitTypography.bodySmall.copy(color = GitColors.outline)
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // THEIRS 区块
            VersionBlock(
                label = "THEIRS",
                labelColor = GitColors.warning,
                refName = "origin/main",
                refColor = GitColors.warning,
                bg = GitColors.theirsBg,
                borderColor = GitColors.warning,
                codeLines = listOf("- old implementation", "- return null")
            )

            // 冲突标记分隔条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(GitColors.divider),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CONFLICT MARKER",
                    style = GitTypography.bodySmall.copy(color = GitColors.outline)
                )
            }

            // YOURS 区块
            VersionBlock(
                label = "YOURS",
                labelColor = GitColors.info,
                refName = "HEAD",
                refColor = GitColors.info,
                bg = GitColors.yoursBg,
                borderColor = GitColors.info,
                codeLines = listOf("+ new implementation", "+ return adapter")
            )

            // Result Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(GitColors.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF555555), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "// Choose a version...",
                    style = GitTypography.mono.copy(color = GitColors.outline)
                )
            }
        }

        // 底部 Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(GitColors.bg)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Keep Yours
            Box(
                modifier = Modifier
                    .size(width = 156.dp, height = 48.dp)
                    .background(GitColors.info.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .border(1.dp, GitColors.info, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keep Yours",
                    color = GitColors.info,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            // Keep Theirs
            Box(
                modifier = Modifier
                    .size(width = 156.dp, height = 48.dp)
                    .background(GitColors.warning.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .border(1.dp, GitColors.warning, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keep Theirs",
                    color = GitColors.warning,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * THEIRS / YOURS 版本对比区块：左上角彩色标签 + 右上 ref 名 + 底部代码行。
 */
@Composable
private fun VersionBlock(
    label: String,
    labelColor: Color,
    refName: String,
    refColor: Color,
    bg: Color,
    borderColor: Color,
    codeLines: List<String>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
    ) {
        // 左上角标签
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 20.dp)
                .background(labelColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
        // 右上 ref 名
        Text(
            text = refName,
            color = refColor,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        )
        // 代码行
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            codeLines.forEach { line ->
                Text(text = line, style = GitTypography.mono.copy(color = GitColors.outline))
            }
        }
    }
}
