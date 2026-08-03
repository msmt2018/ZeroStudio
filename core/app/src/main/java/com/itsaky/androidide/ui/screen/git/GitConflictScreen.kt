package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 6. 冲突解决：仅展示冲突块，提供 Mine/Theirs/手动合并大按钮 UI 空壳。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitConflictScreen() {
    val lines = listOf(
        "1 // conflict block" to null,
        "2 <<<<<<< HEAD" to null,
        "3 const a = 1045;" to GitMockColor.redBg,
        "4 =======" to null,
        "5 const a = 2091;" to GitMockColor.greenBg,
        "6 >>>>>>> remote/feature" to null,
    )

    GitPhoneScreen {
        GitTopBar("冲突解决 (2)")
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
                Text(
                    line.first,
                    style = GitMockText.mono,
                    modifier = Modifier.fillMaxWidth().background(line.second ?: GitMockColor.panel).padding(5.dp),
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 1,
        ) {
            listOf("[保留我的 (Mine)]", "[保留远程 (Theirs)]", "[手动合并]").forEach { action ->
                GitPill(action, color = GitMockColor.blueDark, modifier = Modifier.fillMaxWidth())
            }
        }
        GitBottomHandle()
    }
}

private typealias ConflictLine = Pair<String, Color?>