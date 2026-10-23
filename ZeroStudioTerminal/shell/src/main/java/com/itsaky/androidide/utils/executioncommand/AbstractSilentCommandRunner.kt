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
 * 抽象的 Termux 静默命令执行器
 *
 * 作用与功能： 提供一个高层、健壮的 API，用于在后台静默执行 shell 脚本 (*.sh) 或系统命令 (如 apt, pkg, git)。 依托于 Termux 原生的
 * [AppShell] 机制，不会触发 UI 终端界面的弹窗或渲染。
 *
 * 工作流程线路图：
 * 1. [调用方发起请求] (支持 Async回调, Sync阻塞, Suspend协程 三种模式)
 * 2. [构建 ExecutionCommand] 配置目标路径、参数、运行目录，并指明 Runner 为 APP_SHELL
 * 3. [装载 TermuxShellEnvironment] 获取正确的 $PREFIX, $PATH 等完整的沙盒环境变量
 * 4. [AppShell.execute] 底层调用 Runtime.exec 并在单独的 StreamGobbler 线程中静默收集 I/O 流
 * 5. [返回结果] 自动解析 exitCode、stdout、stderr，并路由给子类回调或作为数据类返回
 *
 * 上下文关系与父类关系： 实现 [AppShell.AppShellClient] 接口以接收异步执行完成的底层回调。 依赖于 [TermuxShellEnvironment] 以确保
 * pkg/apt/git 等二进制文件能够正确解析自身的库依赖。
 *
 * 引用方法教程文档与示例：
 *
 * ```kotlin
 * // 方式一：基于回调的传统面向对象用法
 * val runner = object : AbstractSilentCommandRunner(context) {
 *     override fun onCommandSuccess(label: String, stdout: String) {
 *         println("Success: $stdout")
 *     }
 *     override fun onCommandFailed(label: String, result: SilentCommandResult) {
 *         println("Failed with code: ${result.exitCode}, error: ${result.stderr}")
 *     }
 * }
 * runner.executeCommandAsync("Install Wget", "apt", arrayOf("install", "-y", "wget"))
 *
 * // 方式二：基于协程的高性能用法 (推荐)
 * lifecycleScope.launch {
 *     val result = runner.executeCommandSuspending("Update Repo", "pkg", arrayOf("update"))
 *     if (result.isSuccess) {
 *         // 更新 UI
 *     }
 * }
 * ```
 *
 * @author android_zero
 */
abstract class AbstractSilentCommandRunner(context: Context) : AppShell.AppShellClient {

  protected val mContext: Context = context.applicationContext

  /** 封装执行结果的数据类 */
  data class SilentCommandResult(
      val commandLabel: String,
      val isSuccess: Boolean,
      val exitCode: Int,
      val stdout: String,
      val stderr: String,
      val internalError: String? = null,
  )

  /**
   * 命令执行成功 (退出码为 0，且无内部异常) 时触发
   *
   * @param commandLabel 命令的标识标签
   * @param stdout 标准输出内容
   */
  protected abstract fun onCommandSuccess(commandLabel: String, stdout: String)

  /**
   * 命令执行失败 (退出码非 0，或进程遭遇强杀、找不到路径等内部异常) 时触发
   *
   * @param commandLabel 命令的标识标签
   * @param result 包含详尽错误信息的结果数据类
   */
  protected abstract fun onCommandFailed(commandLabel: String, result: SilentCommandResult)

  /**
   * 异步静默执行 Shell 脚本 (*.sh)
   *
   * @param scriptLabel 脚本的标识标签
   * @param scriptPath .sh 脚本的绝对路径
   * @param arguments 脚本参数 (可为 null)
   * @param workingDir 工作目录 (可为 null)
   */
  fun executeScriptAsync(
      scriptLabel: String,
      scriptPath: String,
      arguments: Array<String>? = null,
      workingDir: String? = null,
  ) {
    executeInternal(scriptLabel, scriptPath, arguments, workingDir, isSynchronous = false)
  }

  /**
   * 异步静默执行通用的 Termux 命令 (如 apt, git, pkg 等)
   *
   * @param commandLabel 命令的标识标签
   * @param executable 可执行文件名称或路径
   * @param arguments 命令参数 (可为 null)
   * @param workingDir 工作目录 (可为 null)
   */
  fun executeCommandAsync(
      commandLabel: String,
      executable: String,
      arguments: Array<String>? = null,
      workingDir: String? = null,
  ) {
    executeInternal(commandLabel, executable, arguments, workingDir, isSynchronous = false)
  }

  /** 同步静默执行命令 (阻塞当前线程，直到命令结束) 警告：严禁在 Android 主线程 (UI Thread) 调用此方法。 */
  fun executeCommandSync(
      commandLabel: String,
      executable: String,
      arguments: Array<String>? = null,
      workingDir: String? = null,
  ): SilentCommandResult {
    return executeInternal(commandLabel, executable, arguments, workingDir, isSynchronous = true)
  }

  /**
   * 【高性能推荐】使用 Kotlin 协程挂起并静默执行命令 将底层同步的 AppShell.execute 调度到 [Dispatchers.IO] 线程池中执行。 这不仅避免了
   * AppShell 异步模式下反复创建新 Thread 的开销，还完美契合现代 Android 架构。
   */
  suspend fun executeCommandSuspending(
      commandLabel: String,
      executable: String,
      arguments: Array<String>? = null,
      workingDir: String? = null,
  ): SilentCommandResult =
      withContext(Dispatchers.IO) {
        executeInternal(commandLabel, executable, arguments, workingDir, isSynchronous = true)
      }

  /** 核心调度引擎：配置执行命令、挂载环境体系、触发底层进程 */
  private fun executeInternal(
      label: String,
      executable: String,
      arguments: Array<String>?,
      workingDir: String?,
      isSynchronous: Boolean,
  ): SilentCommandResult {
    val command =
        ExecutionCommand(
                TermuxShellManager.getNextShellId(),
                executable,
                arguments,
                null, // 不使用标准输入交互
                workingDir,
                ExecutionCommand.Runner.APP_SHELL.runnerName,
                false,
            )
            .apply {
              commandLabel = label
              backgroundCustomLogLevel = Logger.LOG_LEVEL_VERBOSE
            }

    // 装载完整的 Termux Shell 环境
    val environment = TermuxShellEnvironment()

    // 提交给 Termux 原生后台进程管理器执行
    val appShell =
        AppShell.execute(
            mContext,
            command,
            if (isSynchronous) null else this, // 同步模式不需要传递接口自身，异步模式则需要接收回调
            environment,
            HashMap(), // 附加环境变量，默认留空
            isSynchronous,
        )

    // 如果底层启动失败（比如找不到二进制文件），直接构造错误返回
    if (appShell == null) {
      val exitCode = command.resultData?.exitCode ?: -1
      val errorResult =
          SilentCommandResult(
              commandLabel = label,
              isSuccess = false,
              exitCode = exitCode,
              stdout = command.resultData?.stdout?.toString() ?: "",
              stderr = "Failed to start AppShell execution. Executable might not exist.",
              internalError =
                  if (command.isStateFailed)
                      ResultData.getErrorsListMinimalString(command.resultData)
                  else null,
          )
      if (!isSynchronous) {
        onCommandFailed(label, errorResult)
      }
      return errorResult
    }

    // 如果是同步模式，执行到这里时流已经被 StreamGobbler 吸收完毕
    return if (isSynchronous) {
      parseResultAndDispatch(command)
    } else {
      // 异步模式下先返回一个空的占位标识（实际结果从 onAppShellExited 回调获取）
      SilentCommandResult(label, true, 0, "", "")
    }
  }

  /** 处理 [AppShell] 异步退出的原生回调 */
  override fun onAppShellExited(appShell: AppShell?) {
    val command = appShell?.executionCommand ?: return
    parseResultAndDispatch(command)
  }

  /** 统一解析 [ExecutionCommand] 的执行结果并进行分发 */
  private fun parseResultAndDispatch(command: ExecutionCommand): SilentCommandResult {
    val resultData = command.resultData
    val exitCode = resultData?.exitCode ?: -1
    val stdoutStr = resultData?.stdout?.toString()?.trim() ?: ""
    val stderrStr = resultData?.stderr?.toString()?.trim() ?: ""

    val isSuccess = command.isSuccessful && exitCode == 0

    val internalError =
        if (command.isStateFailed) {
          ResultData.getErrorsListMinimalString(resultData)
        } else {
          null
        }

    val result =
        SilentCommandResult(
            commandLabel = command.commandLabel ?: "Unknown",
            isSuccess = isSuccess,
            exitCode = exitCode,
            stdout = stdoutStr,
            stderr = stderrStr,
            internalError = internalError,
        )

    if (isSuccess) {
      onCommandSuccess(result.commandLabel, result.stdout)
    } else {
      onCommandFailed(result.commandLabel, result)
    }

    return result
  }
}
