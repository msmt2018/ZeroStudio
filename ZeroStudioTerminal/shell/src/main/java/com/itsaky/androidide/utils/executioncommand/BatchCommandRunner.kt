package com.itsaky.androidide.utils.executioncommand

import android.content.Context

/**
 * 批量/队列静默命令执行器 (基于协程)。
 *
 * <p>
 * <b>功能与用途：</b> 解决多步骤 Shell 构建流程。比如项目的环境初始化：建文件夹、给权限、下代码、装依赖。 它会在后台按顺序执行，一旦某一步 `exitCode !=
 * 0`，立刻熔断停止，保护执行现场。
 *
 * <p>
 * <b>工作流程：</b> 传入指令队列 -> [executeBatch] -> 迭代挂起执行单条指令 -> 检查状态 -> (成功则继续 | 失败则跳出并返回中断节点)
 *
 * <p>
 * <b>使用示例：</b>
 * <pre>{@code
 *  val batchRunner = BatchCommandRunner(context)
 *  val isAllSuccess = batchRunner.executeBatch(
 *      BatchCommandRunner.Task("Update", "pkg", arrayOf("update", "-y")),
 *      BatchCommandRunner.Task("Install Git", "pkg", arrayOf("install", "git", "-y")),
 *      BatchCommandRunner.Task("Clone Repo", "git", arrayOf("clone", "url..."))
 *  )
 * }</pre>
 *
 * @author android_zero
 */
class BatchCommandRunner(private val context: Context) {

  data class Task(
      val label: String,
      val executable: String,
      val args: Array<String>? = null,
      val workingDir: String? = null,
  )

  /**
   * 顺序执行一批任务，返回执行报告
   *
   * @param tasks 任务参数列表
   * @return 如果全部成功返回 true，遭遇失败立即熔断并返回 false
   */
  suspend fun executeBatch(vararg tasks: Task): Boolean {
    for ((index, task) in tasks.withIndex()) {
      val result =
          TermuxCommand.run(context) {
            label(task.label)
            executable(task.executable)
            task.args?.let { args(*it) }
            task.workingDir?.let { workingDir(it) }
          }

      if (!result.isSuccess) {
        // 发生错误，触发短路熔断机制
        com.termux.shared.logger.Logger.logError(
            "BatchCommandRunner",
            "Batch interrupted at task [${index + 1}/${tasks.size}]: ${task.label}\nError: ${result.stderr}",
        )
        return false
      }
    }
    // 全队列执行完毕
    return true
  }
}
