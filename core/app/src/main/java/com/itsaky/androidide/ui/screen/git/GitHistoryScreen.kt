package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 3. Commit 列表：极简时间线历史看板和 reset/revert 快捷按钮 UI 空壳。 */
@Composable
fun GitHistoryScreen() {
    GitPhoneScreen {
        GitTopBar("提交历史")
        listOf("Commit 9" to "aA0295", "Message mass" to "aRAST8", "commit tree#fooot" to "fE0311").forEach { commit ->
            Row(Modifier.fillMaxWidth().height(72.dp)) {
                GitTimelineDot()
                GitCard(Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(commit.first, style = GitMockText.body)
                            Text("4:00.2025 at 1:25:37", style = GitMockText.small)
                            Text(commit.second, style = GitMockText.small.copy(color = GitMockColor.blue))
                        }
                        GitPill("Reset/Revert", color = GitMockColor.blueDark)
                    }
                }
            }
        }
    }
}
