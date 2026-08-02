package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Diff 对比界面 (Unified View) — 一比一还原 SVG 设计图 "差异对比"。
 *
 * Header(文件名 + Diff: HEAD) -> 代码内容区(行号 gutter + 上下文/增/删行) -> 底部 Action Bar(DISCARD / STAGE FILE)。
 * 使用静态 mock 代码行展示布局。
 */
@Composable
fun GitDiffScreen() {
    // type: 0=上下文, 1=删除, 2=新增
    val diffLines = listOf(
        DiffLine(0, "45", "fun onCreate(savedInstanceState: Bundle?) {"),
        DiffLine(0, "46", "    super.onCreate(savedInstanceState)"),
        DiffLine(0, "47", "    // setup view"),
        DiffLine(1, "48", "    setContentView(R.layout.activity_main)"),
        DiffLine(1, "49", "    val toolbar = findViewById<Toolbar>(R.id.toolbar)"),
        DiffLine(2, "48", "    binding = ActivityMainBinding.inflate(layoutInflater)"),
        DiffLine(2, "49", "    setContentView(binding.root)"),
    )

    Column(modifier = Modifier.fillMaxSize().background(GitColors.bg)) {
        // Header
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
                Text(text = "MainActivity.kt", style = GitTypography.titleMedium, color = GitColors.onSurface)
                Text(text = "Diff: HEAD", style = GitTypography.mono, color = GitColors.outline)
            }
            GitDivider()
        }

        // 代码内容区
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            diffLines.forEach { line ->
                DiffRow(line)
            }
        }

        // 底部 Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .height(56.dp)
                .background(GitColors.surfaceContainer, RoundedCornerShape(28.dp))
                .border(1.dp, Color(0xFF444444), RoundedCornerShape(28.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "DISCARD",
                    style = GitTypography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = GitColors.error,
                )
            }
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF444444)))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "STAGE FILE",
                    style = GitTypography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = GitColors.primary,
                )
            }
        }
    }
}

/** 单行 Diff 数据。type: 0=上下文, 1=删除, 2=新增。 */
private data class DiffLine(val type: Int, val lineNo: String, val code: String)

@Composable
private fun DiffRow(line: DiffLine) {
    val lineBg: Color
    val gutterBg: Color
    val lineNoColor: Color
    val codeColor: Color
    val prefix: String
    val codeStyle = GitTypography.mono
    when (line.type) {
        1 -> {
            lineBg = GitColors.diffDelBg
            gutterBg = GitColors.diffDelText.copy(alpha = 0.20f)
            lineNoColor = GitColors.diffDelText
            codeColor = GitColors.diffDelText
            prefix = "- "
        }
        2 -> {
            lineBg = GitColors.diffAddBg
            gutterBg = GitColors.diffAddText.copy(alpha = 0.20f)
            lineNoColor = GitColors.diffAddText
            codeColor = GitColors.diffAddText
            prefix = "+ "
        }
        else -> {
            lineBg = Color.Transparent
            gutterBg = GitColors.surfaceContainer
            lineNoColor = GitColors.outline
            codeColor = GitColors.outline
            prefix = "  "
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(lineBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(24.dp)
                .background(gutterBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = line.lineNo, style = codeStyle, color = lineNoColor, maxLines = 1)
        }
        Text(
            text = prefix + line.code,
            style = codeStyle.copy(fontFamily = FontFamily.Monospace),
            color = codeColor,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
