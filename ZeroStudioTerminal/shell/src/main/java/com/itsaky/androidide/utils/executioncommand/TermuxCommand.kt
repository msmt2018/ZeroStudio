package com.itsaky.androidide.utils.executioncommand

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.result.ResultData
import com.termux.shared.shell.command.runner.app.AppShell
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotlin DSL 风格的 Termux 后台命令构建器。
 *
 * <p>
 * <b>功能与用途：</b> 提供极其优雅的语法糖，告别繁琐的构造函数。允许开发者以链式和闭包的方式配置命令。
 *
 * <p>
 * <b>工作流程：</b> [TermuxCommandBuilder] -> 收集配置 -> [build] -> 生成 [ExecutionCommand] -> 挂载至
 * [Dispatchers.IO] -> [AppShell.execute] -> 返回 [CommandResult]
 *
 * <p>
 * <b>使用示例：</b>
 * <pre>{@code
 *  lifecycleScope.launch {
 *      val result = TermuxCommand.run(context) {
 *          label("Install Python")
 *          executable("apt")
 *          args("install", "-y", "python")
 *          workingDir(TermuxConstants.TERMUX_HOME_DIR_PATH)
 *      }
 *      if (result.isSuccess) println(result.stdout)
 *  }
 * }</pre>
 *
 * @author android_zero
 */
object TermuxCommand {

  data class CommandResult(
      val isSuccess: Boolean,
      val exitCode: Int,
      val stdout: String,
      val stderr: String,
  )

  class Builder(private val context: Context) {
    private var label: String = "DSL Command"
    private var executable: String = "sh"
    private var args: Array<String>? = null
    private var workingDir: String? = null
    private var stdin: String? = null

    fun label(label: String) = apply { this.label = label }

    fun executable(executable: String) = apply { this.executable = executable }

    fun args(vararg args: String) = apply { this.args = arrayOf(*args) }

    fun workingDir(dir: String) = apply { this.workingDir = dir }

    fun stdin(input: String) = apply { this.stdin = input }

    /** 挂起函数：在 IO 线程池中安全地同步执行并返回结果 */
    suspend fun execute(): CommandResult =
        withContext(Dispatchers.IO) {
          val command =
              ExecutionCommand(
                      TermuxShellManager.getNextShellId(),
                      executable,
                      args,
                      stdin,
                      workingDir,
                      ExecutionCommand.Runner.APP_SHELL.runnerName,
                      false,
                  )
                  .apply {
                    commandLabel = label
                    backgroundCustomLogLevel = Logger.LOG_LEVEL_VERBOSE
                  }

          val appShell =
              AppShell.execute(
                  context.applicationContext,
                  command,
                  null,
                  TermuxShellEnvironment(),
                  HashMap(),
                  true,
              )

          if (appShell == null) {
            return@withContext CommandResult(false, -1, "", "Failed to start execution.")
          }

          val resultData: ResultData? = command.resultData
          val exitCode = resultData?.exitCode ?: -1
          CommandResult(
              isSuccess = command.isSuccessful && exitCode == 0,
              exitCode = exitCode,
              stdout = resultData?.stdout?.toString()?.trim() ?: "",
              stderr = resultData?.stderr?.toString()?.trim() ?: "",
          )
        }
  }

  /** 入口函数，使用 Kotlin 闭包配置 DSL */
  suspend inline fun run(context: Context, block: Builder.() -> Unit): CommandResult {
    return Builder(context).apply(block).execute()
  }
}
