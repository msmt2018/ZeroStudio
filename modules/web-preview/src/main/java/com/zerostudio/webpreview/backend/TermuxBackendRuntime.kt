/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.zerostudio.webpreview.backend

import android.content.Context
import com.itsaky.androidide.utils.executioncommand.FireAndForgetRunner
import com.itsaky.androidide.utils.executioncommand.TermuxCommand
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.runner.app.AppShell
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Termux 后端运行时实现。
 *
 * 通过项目内置 Termux 模块同进程执行后端服务命令
 * (如 `node server.js` / `python -m http.server 8000` / `php -S localhost:8000`)。
 *
 * 用户需先在 Termux 里 `pkg install nodejs` / `pkg install python` 等按需装运行时。
 * APK 不打包运行时, 满足轻量化。
 *
 * 启动: 用 [AppShell.execute] 异步执行 (isSynchronous=false), 服务进程常驻。
 * 停止: 用 `pkill -f <command>` 终止匹配的进程。
 *
 * @param context Android Context
 */
class TermuxBackendRuntime(private val context: Context) : BackendRuntime {

    private val log = LoggerFactory.getLogger("TermuxBackendRuntime")

    override val name: String = "Termux"

    /**
     * 检查 Termux 环境是否就绪。
     *
     * 判断标准: Termux HOME 目录存在 (`/data/data/com.itsaky.androidide/files/home`)。
     * 具体运行时 (node/python/php) 是否安装由调用方在启动前自行检查。
     */
    override fun isAvailable(): Boolean {
        val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        return home.exists() && home.isDirectory
    }

    /**
     * 检查指定可执行文件是否在 Termux PATH 中可用。
     *
     * @param executable 可执行文件名 (如 "node" / "python" / "php")
     * @return true 如果 `which <executable>` 返回 0
     */
    suspend fun isExecutableAvailable(executable: String): Boolean = withContext(Dispatchers.IO) {
        val binPath = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, executable)
        if (binPath.exists() && binPath.canExecute()) return@withContext true

        // fallback: 用 which 命令检查
        val result = TermuxCommand.run(context) {
            label("Which: $executable")
            executable("which")
            args(executable)
            workingDir(TermuxConstants.TERMUX_HOME_DIR_PATH)
        }
        result.isSuccess && result.stdout.isNotEmpty()
    }

    override suspend fun startService(workDir: File, command: String, port: Int): RuntimeSession =
        withContext(Dispatchers.IO) {
            // 解析命令字符串: "node server.js" → executable="node", args=["server.js"]
            val parts = command.trim().split("\\s+".toRegex())
            require(parts.isNotEmpty()) { "Command must not be empty" }
            val executable = parts[0]
            val args = if (parts.size > 1) parts.drop(1).toTypedArray() else null
            val runtime = executable  // "node" / "python" / "php"

            log.info("Starting backend: {} (args={}), workDir={}, port={}",
                executable, args?.joinToString(" "), workDir.absolutePath, port)

            val executionCommand = ExecutionCommand(
                TermuxShellManager.getNextShellId(),
                executable,
                args,
                null,  // stdin
                workDir.absolutePath,
                ExecutionCommand.Runner.APP_SHELL.runnerName,
                false,  // not background
            ).apply {
                commandLabel = "WebPreview Backend: $command"
                backgroundCustomLogLevel = com.termux.shared.logger.Logger.LOG_LEVEL_NORMAL
            }

            // 异步执行 (isSynchronous=false → 新线程, AppShell 退出后自动注销)
            val appShell = AppShell.execute(
                context.applicationContext,
                executionCommand,
                null,  // 无回调
                TermuxShellEnvironment(),
                null,
                false,  // 异步
            )

            if (appShell == null) {
                log.error("Failed to start AppShell for command: {}", command)
                throw RuntimeException("Failed to start backend service: $command")
            }

            log.info("Backend started: {}", command)

            RuntimeSession(
                pid = 0,  // PID 难以可靠获取, 用 pkill -f 匹配停止
                port = port,
                workDir = workDir,
                runtime = runtime,
                command = command,
                stopCallback = {
                    // 用 pkill -f 终止匹配命令的进程
                    log.info("Stopping backend: {}", command)
                    FireAndForgetRunner.fire(
                        context = context,
                        executable = "pkill",
                        args = arrayOf("-f", command),
                    )
                },
            )
        }

    override fun stopService(session: RuntimeSession) {
        session.stop()
    }
}
