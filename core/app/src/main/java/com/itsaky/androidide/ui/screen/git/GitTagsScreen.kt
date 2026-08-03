package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 9. 标签管理：版本标签列表和新建标签入口 UI 空壳。 */
@Composable
fun GitTagsScreen() {
    val tags = listOf("v1.0.1", "v1.0.2", "v1.0.3", "v1.0.4", "v1.0.5")

    GitPhoneScreen {
        GitTopBar("[新] 标签管理")
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(tags, key = { it }) { tag ->
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    GitFileIcon("◇", GitMockColor.blue)
                    Spacer(Modifier.weight(.12f))
                    Column(Modifier.weight(1f)) {
                        Text(tag, style = GitMockText.body)
                        Text("commit IDs", style = GitMockText.small)
                        Text("release notes at this project", style = GitMockText.small)
                    }
                    Text("⋮", style = GitMockText.body)
                }
                GitDividerLine()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GitPill("＋ 新建标签") }
        GitBottomHandle()
    }
}
