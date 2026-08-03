package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 1. 代码对比：移动端统一 Diff、行级暂存按钮 UI 空壳。 */
@Composable
fun GitDiffScreen() {
    val rows = listOf(
        GitDiffRow("15", "function main() {", null),
        GitDiffRow("16", "-   const pi = 3.14159;", GitMockColor.redBg),
        GitDiffRow("17", "-   const enabled = false;", GitMockColor.redBg),
        GitDiffRow("18", "+   const pi = 3.14;", GitMockColor.greenBg),
        GitDiffRow("19", "+   const enabled = true;", GitMockColor.greenBg),
        GitDiffRow("20", "}", null),
    )

    GitPhoneScreen {
        GitTopBar(title = "app.js", back = true)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rows, key = { it.lineNumber }) { row ->
                Row(Modifier.fillMaxWidth().background(row.background ?: GitMockColor.panel).padding(6.dp)) {
                    Text(row.lineNumber, style = GitMockText.mono, color = GitMockColor.muted)
                    Spacer(Modifier.width(12.dp))
                    Text(row.code, style = GitMockText.mono)
                }
            }
        }
        GitPill("Stage Line/Hunk", modifier = Modifier.fillMaxWidth())
    }
}

private data class GitDiffRow(
    val lineNumber: String,
    val code: String,
    val background: Color?,
)
