package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 6. 冲突解决：仅展示冲突块，提供 Mine/Theirs/手动合并大按钮 UI 空壳。 */
@Composable
fun GitConflictScreen() {
    GitPhoneScreen {
        GitTopBar("冲突解决 (2)")
        listOf("1 // conflict block", "2 <<<<<<< HEAD", "3 const a = 1045;", "4 =======", "5 const a = 2091;", "6 >>>>>>> remote/feature").forEachIndexed { index, line ->
            val bg = when (index) {
                2 -> GitMockColor.redBg
                4 -> GitMockColor.greenBg
                else -> GitMockColor.panel
            }
            Text(line, style = GitMockText.mono, modifier = Modifier.fillMaxWidth().background(bg).padding(5.dp))
        }
        Spacer(Modifier.weight(1f))
        listOf("[保留我的 (Mine)]", "[保留我的 (Mine)]", "[保留远程 (Theirs)]", "[手动合并]", "[手动合并]").forEach { action ->
            GitPill(action, color = GitMockColor.blueDark, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        GitBottomHandle()
    }
}
