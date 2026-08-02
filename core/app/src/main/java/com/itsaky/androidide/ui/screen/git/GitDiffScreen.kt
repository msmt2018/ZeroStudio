package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 1. 代码对比：移动端统一 Diff、行级暂存按钮 UI 空壳。 */
@Composable
fun GitDiffScreen() {
    val rows = listOf(
        "15  | function main() {" to null,
        "16  -   const pi = 3.14159;" to GitMockColor.redBg,
        "17  -   const enabled = false;" to GitMockColor.redBg,
        "18  +   const pi = 3.14;" to GitMockColor.greenBg,
        "19  +   const enabled = true;" to GitMockColor.greenBg,
        "20  | }" to null,
    )
    GitPhoneScreen {
        GitTopBar(title = "app.js", back = true)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().background(row.second ?: GitMockColor.panel).padding(6.dp)) {
                Text(row.first.take(4), style = GitMockText.mono, color = GitMockColor.muted)
                Spacer(Modifier.width(8.dp))
                Text(row.first.drop(4), style = GitMockText.mono)
            }
        }
        Spacer(Modifier.weight(1f))
        GitPill("Stage Line/Hunk", modifier = Modifier.fillMaxWidth())
    }
}
