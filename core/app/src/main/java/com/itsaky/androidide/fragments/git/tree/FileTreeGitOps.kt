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
package com.itsaky.androidide.fragments.git.tree

import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.data.entity.RepoEntity
import com.catpuppyapp.puppygit.git.BranchNameAndTypeDto
import com.catpuppyapp.puppygit.git.IgnoreItem
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Branch
import com.github.git24j.core.Repository
import com.itsaky.androidide.projects.IProjectManager
import java.io.File

/**
 * 文件树页面 git 操作的统一封装层 — 复刻 puppygit 源码 API
 * (core/git/src/main/java/com/catpuppyapp/puppygit/utils/Libgit2Helper.kt)。
 *
 * 所有方法都是阻塞的（不切换线程），调用方应在协程 IO 调度器中执行。
 * 每个方法返回 [GitOpResult] 表示成功 / 失败 / 是否在 git 仓库中。
 *
 * 设计原则:
 *  1. 单仓库 — 使用当前打开的工程目录作为仓库根。AndroidIDE 同时只能开
 *     一个工程，所以"当前项目 = 当前 git 仓库"是合理的简化。
 *  2. 防御性 — 任何步骤失败都不抛异常, 全部转为 [GitOpResult.Err]。
 *  3. 强制用 `Repository.use { }` — libgit2 指针必须 close。
 *
 * @author android_zero
 */
object FileTreeGitOps {

  // ---------- 数据类 ----------

  /** git 操作结果。success=data 有效；err=msg 描述失败原因。 */
  sealed class GitOpResult<out T> {
    data class Ok<T>(val data: T) : GitOpResult<T>()
    data class Err(val msg: String, val exception: Throwable? = null) : GitOpResult<Nothing>()
  }

  /** [GitOpResult.Ok] 简写。 */
  fun <T> ok(data: T): GitOpResult<T> = GitOpResult.Ok(data)
  /** [GitOpResult.Err] 简写。 */
  fun err(msg: String, e: Throwable? = null): GitOpResult<Nothing> = GitOpResult.Err(msg, e)

  // ---------- 仓库定位 ----------

  /** 获取当前打开工程的根目录绝对路径。可能为 null。 */
  fun currentProjectDir(): String? = IProjectManager.getInstance().projectDirPath

  /** 当前项目是否位于 git 仓库内（使用 libgit2 真实判定，非简单判 .git 文件夹存在）。 */
  fun isGitRepo(): Boolean {
    val dir = currentProjectDir() ?: return false
    return Libgit2Helper.isValidGitRepo(dir)
  }

  /**
   * 在 try-with-resources 中执行 [block]，自动 close 仓库指针。
   * 仓库无效（非 git 仓库 / 未打开项目）时返回 [GitOpResult.Err]。
   */
  inline fun <T> withRepo(block: (Repository) -> T): GitOpResult<T> {
    val dir = currentProjectDir()
      ?: return err("No project opened")
    if (!Libgit2Helper.isValidGitRepo(dir)) {
      return err("Current project is not a git repository")
    }
    return try {
      Repository.open(dir).use { repo -> ok(block(repo)) }
    } catch (e: Exception) {
      err(e.localizedMessage ?: e.javaClass.simpleName, e)
    }
  }

  // ---------- 路径换算 ----------

  /**
   * 把当前项目下的 [file] 转换为仓库内的相对路径。
   * 文件必须在当前项目目录内（否则返回 null）。
   */
  fun relativePathUnderRepo(file: File): String? {
    val projectDir = currentProjectDir()?.let(::File) ?: return null
    val abs = file.absolutePath
    val root = projectDir.absolutePath
    if (!abs.startsWith(root)) return null
    val rel = abs.substring(root.length).trimStart(File.separatorChar)
    return rel.replace(File.separatorChar, '/')
  }

  /**
   * 把文件转为 [IgnoreItem] (libgit2 `removeFromIndex` 使用)。
   * 注意: 仅对**已 stage 的文件/目录**调用 remove, 不会删除 worktree 文件。
   */
  fun toIgnoreItem(file: File): IgnoreItem? {
    val rel = relativePathUnderRepo(file) ?: return null
    return IgnoreItem(pathspec = rel, isFile = !file.isDirectory)
  }

  // ---------- 文件级操作 ----------

  /**
   * git add <file>
   * 把工作区的修改加入 index (暂存区)。
   */
  fun addFile(file: File): GitOpResult<Unit> = withRepo { repo ->
    val rel = relativePathUnderRepo(file)
      ?: throw IllegalArgumentException("File is not inside project dir: ${file.absolutePath}")
    Libgit2Helper.addToIndexThenWriteToDisk(repo, listOf(rel))
  }

  /**
   * git restore --staged <file>
   * 取消暂存（从 index 移除，不影响工作区文件内容）。
   */
  fun unstageFile(file: File): GitOpResult<Unit> = withRepo { repo ->
    val item = toIgnoreItem(file)
      ?: throw IllegalArgumentException("File is not inside project dir: ${file.absolutePath}")
    Libgit2Helper.removePathSpecListFromIndexThenWriteToDisk(repo, listOf(item))
  }

  /**
   * git checkout HEAD -- <file>  (丢弃工作区修改, 还原到 index/HEAD)
   *
   * @param force true → 用 SAFE | RECREATE | ALLOW_CONFLICTS 策略,
   *              强制覆盖未保存的修改。
   */
  fun revertFile(file: File, force: Boolean = false): GitOpResult<Unit> = withRepo { repo ->
    val rel = relativePathUnderRepo(file)
      ?: throw IllegalArgumentException("File is not inside project dir: ${file.absolutePath}")
    Libgit2Helper.checkoutFiles(repo, Cons.git_IndexCommitHash, listOf(rel), force)
  }

  /**
   * git checkout -- <file>  从指定 commit 还原文件。
   *
   * @param targetCommitHash commit hash; 传 `""` 表示用 HEAD。
   */
  fun revertFileFromCommit(file: File, targetCommitHash: String, force: Boolean = false): GitOpResult<Unit> =
    withRepo { repo ->
      val rel = relativePathUnderRepo(file)
        ?: throw IllegalArgumentException("File is not inside project dir: ${file.absolutePath}")
      val hash = targetCommitHash.ifBlank { "HEAD" }
      Libgit2Helper.checkoutFiles(repo, hash, listOf(rel), force)
    }

  // ---------- 仓库级状态 ----------

  /** 当前分支短名 (如 `main`)。detached HEAD 返回空串。 */
  fun currentBranchShortName(): GitOpResult<String> = withRepo { repo ->
    Libgit2Helper.getRepoCurBranchShortRefSpec(repo)
  }

  /** 当前 HEAD commit 短 hash (7 位)。失败返回空串。 */
  fun currentShortHeadHash(): GitOpResult<String> = withRepo { repo ->
    val headRef = repo.head()
    val oid = headRef?.id()?.toString().orEmpty()
    Libgit2Helper.getShortOidStrByFull(oid)
  }

  // ---------- 分支操作 ----------

  /**
   * 列出本地分支。返回的 DTO 包含 oid / isCurrent / ahead / behind / upstream 等。
   */
  fun listLocalBranches(): GitOpResult<List<BranchNameAndTypeDto>> = withRepo { repo ->
    Libgit2Helper.getBranchList(repo, Branch.BranchType.LOCAL)
  }

  /**
   * 列出远程分支 (如 refs/remotes/origin/main)。
   */
  fun listRemoteBranches(): GitOpResult<List<BranchNameAndTypeDto>> = withRepo { repo ->
    Libgit2Helper.getBranchList(repo, Branch.BranchType.REMOTE, excludeRemoteHead = true)
  }

  /**
   * 切换到指定分支。
   *
   * @param branchName 短名 (如 `main`) 或长名 (如 `refs/heads/main` 或 `origin/main`)
   * @param force 强制切换 (会丢弃本地未提交修改)
   */
  fun switchBranch(branchName: String, force: Boolean = false): GitOpResult<Unit> = withRepo { repo ->
    val name = branchName.trim()
    if (name.isEmpty()) throw IllegalArgumentException("branch name is empty")

    // 远程分支短名: origin/main → 走 checkoutRemoteBranchThenDetachHead
    // 本地分支短名: main     → 走 checkoutLocalBranchThenUpdateHead
    val ret = if (name.contains('/') && !name.startsWith("refs/")) {
      Libgit2Helper.checkoutRemoteBranchThenDetachHead(repo, name, force)
    } else {
      Libgit2Helper.checkoutLocalBranchThenUpdateHead(repo, name, force)
    }
    if (ret.hasError()) {
      throw RuntimeException(ret.msg)
    }
  }

  // ---------- 提交 ----------

  /**
   * git commit -m "msg"
   * 注意: 暂存区必须非空且要 commit 的文件必须已 stage (add 之后才能 commit)。
   * 本方法只 commit index 中已有的内容, 不会自动 add。
   */
  fun commit(
    msg: String,
    username: String = "AndroidIDE",
    email: String = "androidide@local",
  ): GitOpResult<String> = withRepo { repo ->
    val ret = Libgit2Helper.createCommit(
      repo = repo,
      msg = msg,
      username = username,
      email = email,
      cleanRepoStateIfSuccess = true,
      settings = SettingsUtil.getSettingsSnapshot(),
    )
    if (ret.hasError()) {
      throw RuntimeException(ret.msg)
    }
    ret.data?.toString().orEmpty()
  }

  // ---------- 远程操作 (push / pull / fetch) ----------

  /**
   * git push  (推送当前分支到 upstream)
   * @param force 强制推送 (覆盖远程历史)
   */
  fun push(force: Boolean = false): GitOpResult<Unit> = withRepo { repo ->
    val branchName = Libgit2Helper.getRepoCurBranchShortRefSpec(repo)
    if (branchName.isBlank()) {
      throw RuntimeException("HEAD is detached, cannot push")
    }
    val upstream = Libgit2Helper.getUpstreamOfBranch(repo, branchName)
    if (upstream.remote.isBlank() || upstream.branchRefsHeadsFullRefSpec.isBlank()) {
      throw RuntimeException("No upstream configured for branch '$branchName'")
    }
    Libgit2Helper.pushSingleBranch(repo, upstream, null, force)
  }

  /**
   * git fetch (从所有 remote 拉取, 不合并)。
   * 注意: fetch 通常需要凭据, 暂用 null credential, 若仓库是公开的匿名可访问就能成功。
   */
  fun fetch(): GitOpResult<Unit> = withRepo { repo ->
    val remoteNames = Libgit2Helper.getRemoteList(repo)
    if (remoteNames.isEmpty()) {
      throw RuntimeException("No remote configured")
    }
    val entity = dummyRepoEntity()
    for (remoteName in remoteNames) {
      // 没传凭据, 对匿名仓库 OK, 私有仓库会失败 — 用户需在完整 git UI 中配置 credential
      Libgit2Helper.fetchRemoteForRepo(repo, remoteName, null, entity)
    }
  }

  /**
   * git pull = fetch + merge
   * 实现: 先 fetch, 再把 upstream merge 到 HEAD。
   */
  fun pull(): GitOpResult<Unit> = withRepo { repo ->
    fetch()
    val branchName = Libgit2Helper.getRepoCurBranchShortRefSpec(repo)
    if (branchName.isBlank()) {
      throw RuntimeException("HEAD is detached, cannot pull")
    }
    val upstream = Libgit2Helper.getUpstreamOfBranch(repo, branchName)
    if (upstream.remote.isBlank() || upstream.branchRefsHeadsFullRefSpec.isBlank()) {
      throw RuntimeException("No upstream configured for branch '$branchName'")
    }
    // merge 上游到当前 HEAD
    val mergeRet = Libgit2Helper.mergeOrRebase(
      repo = repo,
      targetRefName = upstream.remoteBranchShortRefSpec,
      username = "AndroidIDE",
      email = "androidide@local",
      requireMergeByRevspec = true,
      revspec = upstream.remoteBranchShortRefSpec,
      trueMergeFalseRebase = true,
      settings = SettingsUtil.getSettingsSnapshot(),
    )
    if (mergeRet.hasError()) {
      throw RuntimeException(mergeRet.msg)
    }
  }

  // ---------- 内部辅助 ----------

  /**
   * puppygit 的 fetchRemoteForRepo 需要 RepoEntity (用于记录最近 fetch 时间等)。
   * AndroidIDE 这边没维护 PuppyGitDatabase 里的 RepoEntity 记录，
   * 所以临时构造一个最小可用的 stub。
   */
  private fun dummyRepoEntity(): RepoEntity {
    val dir = currentProjectDir().orEmpty()
    return RepoEntity(
      repoName = File(dir).name.ifBlank { "repo" },
      fullSavePath = dir,
      branch = "",
    )
  }
}
