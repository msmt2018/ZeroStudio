package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 5. 暂存箱：一键 stash/pop/drop 的移动端便签式列表 UI 空壳。 */
@Composable
fun GitStashScreen() {
    val stashes = listOf("README.md" to "Changes to dialogs", "Useful change" to "Changes to readme", "README.md" to "Changes in changes")

    GitPhoneScreen {
        GitTopBar("暂存箱 (Stash)")
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(stashes, key = { index, stash -> "${stash.first}-$index" }) { index, stash ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stash.first, style = GitMockText.body)
                        Text("T0HA 35 at 12:${35 + index}", style = GitMockText.small)
                        Text(stash.second, style = GitMockText.small)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("⟲\nPop", style = GitMockText.small.copy(color = GitMockColor.blue))
                        Text("■\nDrop", style = GitMockText.small.copy(color = GitMockColor.red))
                    }
                }
                GitDividerLine()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GitPill("Stash Current") }
        GitBottomHandle()
    }
}
