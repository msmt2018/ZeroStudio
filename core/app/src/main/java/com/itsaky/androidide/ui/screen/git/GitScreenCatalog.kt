package com.itsaky.androidide.ui.screen.git

import androidx.compose.runtime.Composable

/** Navigation-neutral registry for the ten image-matched Git Compose screen shells. */
object GitScreenCatalog {
    val screens = listOf(
        GitScreenSpec("diff", "代码对比") { GitDiffScreen() },
        GitScreenSpec("branches", "分支列表") { GitBranchesScreen() },
        GitScreenSpec("history", "提交历史") { GitHistoryScreen() },
        GitScreenSpec("changes", "当前修改") { GitChangesScreen() },
        GitScreenSpec("stash", "暂存箱") { GitStashScreen() },
        GitScreenSpec("conflict", "冲突解决") { GitConflictScreen() },
        GitScreenSpec("repositories", "仓库管理") { GitRepositoriesScreen() },
        GitScreenSpec("settings", "设置中心") { GitSettingsScreen() },
        GitScreenSpec("tags", "标签管理") { GitTagsScreen() },
        GitScreenSpec("interactive-rebase", "交互式变基") { GitInteractiveRebaseScreen() },
    )
}

data class GitScreenSpec(
    val route: String,
    val title: String,
    val content: @Composable () -> Unit,
)
