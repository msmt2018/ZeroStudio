package com.itsaky.androidide.utils.executioncommand

import android.content.Context
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.runner.app.AppShell
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment

/**
 * 极简的“发射后不管”执行器。
 *
 * <p>
 * <b>功能与用途：</b> 专注于执行无关紧要的收尾、清理、异步预加载命令。调用后立即返回主线程， 底层依靠 AppShell 内部的自毁机制管理内存，无需绑定回调。
 *
 * <p>
 * <b>使用示例：</b>
 * <pre>{@code
 *  FireAndForgetRunner.fire(context, "rm", arrayOf("-rf", "/data/local/tmp/cache"))
 * }</pre>
 *
 * @author android_zero
 */
object FireAndForgetRunner {

  /**
   * @param executable 可执行文件
   * @param args 参数数组
   */
  fun fire(context: Context, executable: String, args: Array<String>? = null) {
    val command =
        ExecutionCommand(
                TermuxShellManager.getNextShellId(),
                executable,
                args,
                null,
                null,
                ExecutionCommand.Runner.APP_SHELL.runnerName,
                false,
            )
            .apply {
              commandLabel = "Fire-And-Forget: $executable"
              backgroundCustomLogLevel =
                  com.termux.shared.logger.Logger.LOG_LEVEL_OFF // 关闭日志以提升极致性能
            }

    // isSynchronous = false 意味着在新 Thread 中执行，AppShell 退出后自动注销。
    AppShell.execute(
        context.applicationContext,
        command,
        null, // 无回调监听
        TermuxShellEnvironment(),
        null,
        false, // 彻底的异步
    )
  }
}
