/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.ui.screen.git

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.constants.LineNum
import com.catpuppyapp.puppygit.screen.BranchListScreen
import com.catpuppyapp.puppygit.screen.CloneScreen
import com.catpuppyapp.puppygit.screen.CommitListScreen
import com.catpuppyapp.puppygit.screen.CredentialManagerScreen
import com.catpuppyapp.puppygit.screen.CredentialNewOrEdit
import com.catpuppyapp.puppygit.screen.CredentialRemoteListScreen
import com.catpuppyapp.puppygit.screen.DiffScreen
import com.catpuppyapp.puppygit.screen.DomainCredentialListScreen
import com.catpuppyapp.puppygit.screen.ErrorListScreen
import com.catpuppyapp.puppygit.screen.FileChooserScreen
import com.catpuppyapp.puppygit.screen.FileHistoryScreen
import com.catpuppyapp.puppygit.screen.IndexScreen
import com.catpuppyapp.puppygit.screen.ReflogListScreen
import com.catpuppyapp.puppygit.screen.RemoteListScreen
import com.catpuppyapp.puppygit.screen.StashListScreen
import com.catpuppyapp.puppygit.screen.SubPageEditor
import com.catpuppyapp.puppygit.screen.SubmoduleListScreen
import com.catpuppyapp.puppygit.screen.TagListScreen
import com.catpuppyapp.puppygit.screen.TreeToTreeChangeListScreen
import com.catpuppyapp.puppygit.screen.shared.CommitListFrom
import com.catpuppyapp.puppygit.screen.shared.DiffFromScreen
import com.catpuppyapp.puppygit.screen.shared.FileChooserType
import com.catpuppyapp.puppygit.ui.theme.InitContent
import com.catpuppyapp.puppygit.utils.AppModel
import com.catpuppyapp.puppygit.utils.cache.NaviCache
import com.itsaky.androidide.fragments.git.RepoIdResolver
import com.itsaky.androidide.projects.IProjectManager
import kotlinx.coroutines.launch

/**
 * puppygit 路由: git 首页(标签页宿主)。本身不在 [Cons] 中定义,
 * 因为这是 AndroidIDE 侧的"聚合入口", puppygit 原生导航里没有对应页面。
 */
private const val NAV_GIT_HOME = "git_home"

/**
 * Git 功能聚合入口 Composable — 替代旧的 [com.itsaky.androidide.fragments.git.GitHostFragment]。
 *
 * 旧架构: ViewPager2 + TabLayout + 11 个子 Fragment (ChangesFragment / CommitFragment /
 * BranchFragment / ...) 的套娃, 每个 Fragment 各自维护生命周期与状态。
 *
 * 新架构:
 *  1. 本 Composable 作为唯一宿主, 在内部用 Compose Navigation ([NavHost]) 管理所有
 *     puppygit 页面的跳转栈。
 *  2. 起始页 [NAV_GIT_HOME] 是一个 [GitHomePage], 内部用 [ScrollableTabRow] +
 *     [HorizontalPager] 展示 8 个主要 git 标签页(变更/历史/分支/Stash/Tags/远程/Reflog/子模块)。
 *  3. 标签页里展示的 puppygit Screen(BranchListScreen 等)直接复用, 不再包裹 Fragment。
 *  4. 子页面(CommitList / Diff / CredentialManager 等复杂页) 通过 [NavHost] 的
 *     composable 路由跳转, 而不是塞进 HorizontalPager。
 *
 * 初始化约定:
 *  - [AppModel.navController] 与 [AppModel.homeTopBarScrollBehavior] 在本 Composable
 *    内赋值, 供 puppygit 各 Screen 内部读取(puppygit Screen 默认从 [AppModel] 取这两个对象)。
 *  - puppygit 运行时(native 库 + dbContainer)由上游 [com.itsaky.androidide.ui.screen.git.ComposeHostFragment]
 *    调用 [com.itsaky.androidide.fragments.git.PuppyGitIntegration.ensureReadyForAndroidIDE] 完成。
 *
 * @author android_zero
 */
@Composable
fun GitHostScreen() {
  // 1. 给 puppygit 的 AppModel 注入 navController 与首页 TopBar 滚动行为。
  //    puppygit 的各 Screen (例如 SubmoduleListScreen) 内部直接读
  //    AppModel.navController / AppModel.homeTopBarScrollBehavior, 必须先于它们设置。
  val navController = rememberNavController()
  AppModel.navController = navController
  AppModel.homeTopBarScrollBehavior =
      TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

  // 2. 解析当前打开工程路径 -> puppygit repoId。
  //    workdir 为 null 表示当前没有打开工程; repoId 为 null 表示尚未解析完成或解析失败。
  val workdir =
      remember { IProjectManager.getInstance().getWorkspace()?.getProjectDir()?.path }
  val repoId = RepoIdResolver.rememberRepoIdForWorkdir(workdir)

  // 3. NavHost: 以 puppygit 的 Cons.nav_* 常量作为路由, 统一管理页面跳转栈。
  //    路由注册与 puppygit AppScreenNavigator 保持一致, 使各 Screen 内部的
  //    navController.navigate(Cons.nav_xxx) 调用能正确匹配。
  //    不注册 HomeScreen (AndroidIDE 不需要 puppygit 的仓库列表页)。
  val editorPageLastFilePath = rememberSaveable { mutableStateOf("") }

  InitContent(LocalContext.current) {
    NavHost(navController = navController, startDestination = NAV_GIT_HOME) {
      // 起始页: 8 个标签页聚合入口
      composable(NAV_GIT_HOME) {
        GitHomePage(repoId = repoId, navController = navController)
      }

      // ---- 以下路由与 puppygit AppScreenNavigator 一致 ----

      // 提交历史 (从分支/标签/变更页跳转)
      composable(
          Cons.nav_CommitListScreen +
              "/{repoId}/{isHEAD}/{from}/{fullOidCacheKey}/{shortBranchNameCacheKey}") { entry ->
        val args = entry.arguments
        val fullOidCacheKey = args?.getString("fullOidCacheKey") ?: ""
        val shortBranchNameCacheKey = args?.getString("shortBranchNameCacheKey") ?: ""
        CommitListScreen(
            repoId = args?.getString("repoId") ?: "",
            isHEAD = args?.getString("isHEAD") != "0",
            fullOidCacheKey = fullOidCacheKey,
            shortBranchNameCacheKey = shortBranchNameCacheKey,
            from =
                CommitListFrom.fromCode(args?.getString("from") ?: "")
                    ?: CommitListFrom.OTHER,
            naviUp = {
              navController.navigateUp()
              NaviCache.del(fullOidCacheKey)
              NaviCache.del(shortBranchNameCacheKey)
            },
        )
      }

      // Diff (从变更页/Stash/提交历史点击文件差异跳转)
      composable(
          Cons.nav_DiffScreen +
              "/{repoId}/{fromTo}/{treeOid1Str}/{treeOid2Str}/{isDiffToLocal}/{curItemIndexAtDiffableList}/{localAtDiffRight}/{fromScreen}/{diffableListCacheKey}/{isMultiMode}") { entry ->
        val args = entry.arguments
        val diffableListCacheKey = args?.getString("diffableListCacheKey") ?: ""
        DiffScreen(
            repoId = args?.getString("repoId") ?: "",
            fromTo = args?.getString("fromTo") ?: "",
            treeOid1Str = args?.getString("treeOid1Str") ?: "",
            treeOid2Str = args?.getString("treeOid2Str") ?: "",
            localAtDiffRight = (args?.getString("localAtDiffRight")?.toInt() ?: 0) != 0,
            isDiffToLocal = (args?.getString("isDiffToLocal")?.toInt() ?: 0) != 0,
            fromScreen = DiffFromScreen.fromCode(args?.getString("fromScreen")!!)!!,
            diffableListCacheKey = diffableListCacheKey,
            isMultiMode = args?.getString("isMultiMode") == "1",
            curItemIndexAtDiffableItemList =
                try {
                  (args?.getString("curItemIndexAtDiffableList") ?: "").toInt()
                } catch (_: Exception) {
                  -1
                },
            naviUp = {
              navController.navigateUp()
              NaviCache.del(diffableListCacheKey)
            },
        )
      }

      // 凭据管理 (从远程页跳转)
      composable(Cons.nav_CredentialManagerScreen + "/{remoteId}") { entry ->
        val remoteId = entry.arguments?.getString("remoteId") ?: ""
        CredentialManagerScreen(
            remoteId = if (remoteId == Cons.dbInvalidNonEmptyId) "" else remoteId,
            naviUp = { navController.navigateUp() },
        )
      }

      // 新建/编辑凭据
      composable(Cons.nav_CredentialNewOrEditScreen + "/{credentialId}") { entry ->
        CredentialNewOrEdit(
            credentialId = entry.arguments?.getString("credentialId") ?: "",
            naviUp = { navController.navigateUp() },
        )
      }

      // 凭据关联的远程列表
      composable(Cons.nav_CredentialRemoteListScreen + "/{credentialId}/{isShowLink}") { entry ->
        CredentialRemoteListScreen(
            credentialId = entry.arguments?.getString("credentialId") ?: "",
            isShowLink = entry.arguments?.getString("isShowLink") != "0",
            naviUp = { navController.navigateUp() },
        )
      }

      // 域名凭据列表
      composable(Cons.nav_DomainCredentialListScreen) {
        DomainCredentialListScreen(naviUp = { navController.navigateUp() })
      }

      // 错误列表
      composable(Cons.nav_ErrorListScreen + "/{repoId}") { entry ->
        ErrorListScreen(
            entry.arguments?.getString("repoId") ?: "",
            naviUp = { navController.navigateUp() },
        )
      }

      // Clone (从子模块等页面跳转)
      composable(Cons.nav_CloneScreen + "/{repoId}") { entry ->
        CloneScreen(
            entry.arguments?.getString("repoId") ?: "",
            naviUp = { navController.navigateUp() },
        )
      }

      // 两个 commit/tree 之间的变更列表
      composable(
          Cons.nav_TreeToTreeChangeListScreen +
              "/{repoId}/{commit1OidStrCacheKey}/{commit2OidStrCacheKey}/{commitForQueryParentsCacheKey}/{titleCacheKey}") { entry ->
        val args = entry.arguments
        val commit1OidStrCacheKey = args?.getString("commit1OidStrCacheKey") ?: ""
        val commit2OidStrCacheKey = args?.getString("commit2OidStrCacheKey") ?: ""
        val commitForQueryParentsCacheKey = args?.getString("commitForQueryParentsCacheKey") ?: ""
        val titleCacheKey = args?.getString("titleCacheKey") ?: ""
        TreeToTreeChangeListScreen(
            repoId = args?.getString("repoId") ?: "",
            commit1OidStrCacheKey = commit1OidStrCacheKey,
            commit2OidStrCacheKey = commit2OidStrCacheKey,
            commitForQueryParentsCacheKey = commitForQueryParentsCacheKey,
            titleCacheKey = titleCacheKey,
            naviUp = {
              navController.navigateUp()
              NaviCache.del(commit1OidStrCacheKey)
              NaviCache.del(commit2OidStrCacheKey)
              NaviCache.del(commitForQueryParentsCacheKey)
              NaviCache.del(titleCacheKey)
            },
        )
      }

      // 文件历史
      composable(Cons.nav_FileHistoryScreen + "/{repoId}/{fileRelativePathKey}") { entry ->
        val fileRelativePathKey = entry.arguments?.getString("fileRelativePathKey") ?: ""
        FileHistoryScreen(
            repoId = entry.arguments?.getString("repoId") ?: "",
            fileRelativePathKey = fileRelativePathKey,
            naviUp = {
              navController.navigateUp()
              NaviCache.del(fileRelativePathKey)
            },
        )
      }

      // 子页面编辑器 (编辑 commit message / .gitignore 等)
      composable(
          Cons.nav_SubPageEditor +
              "/{goToLine}/{initMergeMode}/{initReadOnly}/{filePathKey}") { entry ->
        val args = entry.arguments
        val filePathKey = args?.getString("filePathKey") ?: ""
        SubPageEditor(
            goToLine =
                try {
                  (args?.getString("goToLine") ?: "").toInt()
                } catch (_: Exception) {
                  LineNum.lastPosition
                },
            initMergeMode = args?.getString("initMergeMode") == "1",
            initReadOnly = args?.getString("initReadOnly") == "1",
            editorPageLastFilePath = editorPageLastFilePath,
            filePathKey = filePathKey,
            naviUp = {
              navController.navigateUp()
              NaviCache.del(filePathKey)
            },
        )
      }

      // 文件选择器
      composable(Cons.nav_FileChooserScreen + "/{type}") { entry ->
        FileChooserScreen(
            type = FileChooserType.fromCode(entry.arguments!!.getString("type")!!)!!,
            naviUp = { navController.navigateUp() },
        )
      }
    }
  }
}

/**
 * git 首页: [ScrollableTabRow] + [HorizontalPager] 组合的 8 标签页。
 *
 * 标签页与对应 puppygit Screen:
 *  - 变更   -> [IndexScreen] (工作区改动, naviUp 为 `() -> Unit`)
 *  - 历史   -> [CommitListScreen] (isHEAD=true, from=FOLLOW_HEAD, 屏幕内部自动 resolve HEAD)
 *  - 分支   -> [BranchListScreen]
 *  - Stash  -> [StashListScreen]
 *  - Tags   -> [TagListScreen]
 *  - 远程   -> [RemoteListScreen]
 *  - Reflog -> [ReflogListScreen]
 *  - 子模块 -> [SubmoduleListScreen]
 *
 * 标签页内的 [naviUp] 一律返回 `false`: 标签页本身不在导航栈中, 不应触发 popBackStack。
 *
 * @param repoId puppygit 仓库 id; 为 null 时各标签页显示"未打开项目"占位。
 * @param navController 用于标签页内需要进一步跳转子路由(如点开某条 commit)的场景。
 */
@Composable
private fun GitHomePage(repoId: String?, navController: NavHostController) {
  val tabs =
      listOf("变更", "历史", "分支", "Stash", "Tags", "远程", "Reflog", "子模块")
  val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
  val scope = rememberCoroutineScope()

  Column(modifier = Modifier.fillMaxSize()) {
    ScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        edgePadding = 0.dp,
    ) {
      tabs.forEachIndexed { index, title ->
        Tab(
            selected = pagerState.currentPage == index,
            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
            text = {
              Text(
                  text = title,
                  maxLines = 1,
                  style = MaterialTheme.typography.labelSmall,
              )
            },
        )
      }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
      when (page) {
        // 变更: IndexScreen 只接受 naviUp: () -> Unit, 无 repoId
        0 ->
            if (repoId != null) {
              IndexScreen(naviUp = { /* 标签页内, 不向上导航 */ })
            } else {
              NoProjectPlaceholder()
            }

        // 历史: CommitListScreen, isHEAD=true 时屏幕内部自动 resolve HEAD,
        // 故 fullOidCacheKey/shortBranchNameCacheKey 传空串即可。
        // from=FOLLOW_HEAD 表示"跟随 HEAD 的提交历史"(与 puppygit 从变更页进入语义一致)。
        1 ->
            if (repoId != null) {
              CommitListScreen(
                  repoId = repoId,
                  from = CommitListFrom.FOLLOW_HEAD,
                  isHEAD = true,
                  fullOidCacheKey = "",
                  shortBranchNameCacheKey = "",
                  naviUp = { /* 标签页内, 不向上导航 */ },
              )
            } else {
              NoProjectPlaceholder()
            }

        // 分支
        2 ->
            if (repoId != null) {
              BranchListScreen(repoId = repoId, naviUp = { false })
            } else {
              NoProjectPlaceholder()
            }

        // Stash
        3 ->
            if (repoId != null) {
              StashListScreen(repoId = repoId, naviUp = { false })
            } else {
              NoProjectPlaceholder()
            }

        // Tags
        4 ->
            if (repoId != null) {
              TagListScreen(repoId = repoId, naviUp = { false })
            } else {
              NoProjectPlaceholder()
            }

        // 远程
        5 ->
            if (repoId != null) {
              RemoteListScreen(repoId = repoId, naviUp = { false })
            } else {
              NoProjectPlaceholder()
            }

        // Reflog
        6 ->
            if (repoId != null) {
              ReflogListScreen(repoId = repoId, naviUp = { false })
            } else {
              NoProjectPlaceholder()
            }

        // 子模块
        7 ->
            if (repoId != null) {
              SubmoduleListScreen(repoId = repoId, naviUp = { false })
            } else {
              NoProjectPlaceholder()
            }
      }
    }
  }
}

/**
 * "当前未打开项目"占位。当 [repoId] 为 null 时在标签页内显示。
 */
@Composable
private fun NoProjectPlaceholder() {
  Text(
      text = "No project",
      modifier = Modifier.fillMaxSize().padding(16.dp),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.outline,
  )
}
