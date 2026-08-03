package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 10. 交互式变基：提交队列、拖拽句柄和 squash/pick/edit UI 空壳。 */
@Composable
fun GitInteractiveRebaseScreen() {
    GitPhoneScreen {
        GitTopBar("交互式变基", back = true)
        listOf("commit 1" to false, "commit 2" to false, "main_act.kt" to true, "commit 3" to false, "commit 4" to false).forEach { item ->
            Row(Modifier.fillMaxWidth().height(if (item.second) 94.dp else 54.dp), verticalAlignment = Alignment.Top) {
                GitTimelineDot()
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    if (item.second) {
                        GitCard(Modifier.fillMaxWidth()) { RebaseContent(item.first, true) }
                    } else {
                        RebaseContent(item.first, false)
                    }
                }
                Text("☰", style = GitMockText.body.copy(color = GitMockColor.secondary), modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun RebaseContent(title: String, expanded: Boolean) {
    Column(Modifier.padding(10.dp)) {
        Text(title, style = GitMockText.body)
        Text(if (title == "main_act.kt") "Commit at 15:3:33" else "Commit at 15:3:37", style = GitMockText.small)
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GitPill("Squash", color = GitMockColor.blueDark)
                GitPill("pick", color = GitMockColor.blueDark)
                GitPill("edit", color = GitMockColor.blueDark)
            }
        }
    }
}
