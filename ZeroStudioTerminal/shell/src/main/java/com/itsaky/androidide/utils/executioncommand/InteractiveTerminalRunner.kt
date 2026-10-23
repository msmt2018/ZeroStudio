package com.itsaky.androidide.utils.executioncommand

import android.content.Context
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.terminal.TerminalSessionClient

/**
 * 抽象的 Termux 前台交互式命令执行器。
 *
 * <p>
 * <b>功能与用途：</b> 用于将命令提交给 Termux 的前台会话引擎。 它会分配伪终端 (pty)，可以在 `TerminalView` 中实时渲染，并允许用户通过软键盘输入。
 *
 * <p>
 * <b>工作流程：</b> 构建 [ExecutionCommand] (Runner=TERMINAL_SESSION) -> 注入 [TermuxShellEnvironment] ->
 * [TermuxSession.execute] (底层生成 `/dev/ptmx` 句柄) -> 返回给 UI 绑定显示。
 *
 * <p>
 * <b>使用示例：</b>
 * <pre>{@code
 *  val interactiveRunner = InteractiveTerminalRunner(context, terminalSessionClient)
 *  val session = interactiveRunner.launchTerminalCommand("Vim Editor", "vim", arrayOf("file.txt"))
 *  // 将返回的 session 传递给 TerminalView.attachSession(session.terminalSession)
 * }</pre>
 *
 * @author android_zero
 */
class InteractiveTerminalRunner(
    private val context: Context,
    private val terminalClient: TerminalSessionClient,
) : TermuxSession.TermuxSessionClient {

  /**
   * 启动前台终端命令
   *
   * @param sessionName 终端会话的显示名称 (如左侧抽屉列表中的名字)
   * @param executable 可执行程序
   * @param arguments 参数
   * @param workingDir 工作目录
   * @return 返回 [TermuxSession]，包含核心的 TerminalSession 用于绑定 UI
   */
  fun launchTerminalCommand(
      sessionName: String,
      executable: String,
      arguments: Array<String>? = null,
      workingDir: String? = null,
  ): TermuxSession? {
    val command =
        ExecutionCommand(
                TermuxShellManager.getNextShellId(),
                executable,
                arguments,
                null,
                workingDir,
                ExecutionCommand.Runner.TERMINAL_SESSION.runnerName,
                false, // 是否进入 failsafe 模式
            )
            .apply {
              this.shellName = sessionName
              this.commandLabel = sessionName
              this.setShellCommandShellEnvironment = true
            }

    return TermuxSession.execute(
            context.applicationContext,
            command,
            terminalClient,
            this, // 作为 TermuxSessionClient 监听退出事件
            TermuxShellEnvironment(),
            null,
            false, // 不需要再退出时缓存 stdout 到 resultData (UI 流已处理)
        )
        .also {
          if (it != null) {
            TermuxShellManager.getShellManager().mTermuxSessions.add(it)
          }
        }
  }

  /** 当该前台会话执行结束时回调 */
  override fun onTermuxSessionExited(termuxSession: TermuxSession?) {
    termuxSession?.let {
      TermuxShellManager.getShellManager().mTermuxSessions.remove(it)
      // 这里可以向外发射事件通知 UI 层移除该会话的 Tab 或 View
    }
  }
}
