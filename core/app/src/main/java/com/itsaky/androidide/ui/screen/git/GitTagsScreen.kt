package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 9. 标签管理：版本标签列表和新建标签入口 UI 空壳。 */
@Composable
fun GitTagsScreen() {
    GitPhoneScreen {
        GitTopBar("[新] 标签管理")
        listOf("v1.0.1", "v1.0.2", "v1.0.3", "v1.0.4", "v1.0.5").forEach { tag ->
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                GitFileIcon("◇", GitMockColor.blue)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tag, style = GitMockText.body)
                    Text("commit IDs", style = GitMockText.small)
                    Text("release notes at this project", style = GitMockText.small)
                }
                Text("⋮", style = GitMockText.body)
            }
            GitDividerLine()
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GitPill("＋ 新建标签") }
        GitBottomHandle()
    }
}
