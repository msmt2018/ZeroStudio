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

package com.itsaky.androidide.lsp.kotlin

import android.content.Context
import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.kotlin.events.KotlinLanguageClientImpl
import com.itsaky.androidide.lsp.kotlin.events.KotlinTextDocumentSyncHandler
import com.itsaky.androidide.lsp.kotlin.settings.KotlinServerSettings
import com.itsaky.androidide.lsp.kotlin.ui.KotlinServerConstants
import com.itsaky.androidide.lsp.kotlin.ui.events.LspEventBus
import com.itsaky.androidide.lsp.kotlin.ui.events.LspInstallRequestEvent
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.utils.Environment
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 核心重构：基于 LSP4J 流管道连接的 Kotlin Language Server 进程管理器。
 *
 * @author android_zero
 */
class KotlinServerProcessManager(private val context: Context) {

    private var serverProcess: Process? = null
    private var currentServerImpl: KotlinLanguageServerImpl? = null
    private var launcher: Launcher<org.eclipse.lsp4j.services.LanguageServer>? = null
    
    // 注入的全局 AndroidIDE 客户端实现，用来做 UI 交互（如报错提示框、代码替换）
    private val kotlinClient: KotlinLanguageClientImpl by lazy { KotlinLanguageClientImpl() }
    
    // 强大的 Android 构建系统动态环境路径解析支持
    private var classpathProvider: KotlinClasspathProvider? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        private val log = LoggerFactory.getLogger(KotlinServerProcessManager::class.java)
    }

    fun setClasspathProvider(provider: KotlinClasspathProvider) {
        this.classpathProvider = provider
    }

    /**
     * 启动 Kotlin LSP 服务进程，挂载 LSP4J 通讯协议并向全局注册
     */
    fun startServer() {
        KotlinTextDocumentSyncHandler.init()
        coroutineScope.launch {
            if (checkInstallation()) {
                launchProcessAndRegister()
            } else {
                requestInstallation()
            }
        }
    }

    private fun checkInstallation(): Boolean {
        val binDir = File(Environment.KOTLIN_LSP_HOME, "bin")
        val launcher = File(binDir, KotlinServerConstants.LAUNCHER_SCRIPT_NAME)
        
        if (!launcher.exists()) return false

        val libDir = File(Environment.KOTLIN_LSP_HOME, "lib")
        if (!libDir.exists() || !libDir.isDirectory) return false

        val existingJars = libDir.list()?.toSet() ?: emptySet()
        for (requiredJar in KotlinServerConstants.REQUIRED_LIB_JARS) {
            if (!existingJars.contains(requiredJar)) {
                log.warn("Missing required jar: $requiredJar")
                return false
            }
        }
        return true
    }

    private fun requestInstallation() {
        val installEvent = LspInstallRequestEvent(
            serverId = "kotlin-lsp",
            serverName = "Kotlin Language Server",
            dialogTitle = "Kotlin Language Server",
            dialogMessage = "The Kotlin Language Server is required to provide code completion, diagnostics, and navigation features for Kotlin files. Do you want to download and install it now? (Size: ~50MB)",
            downloadUrl = KotlinServerConstants.DOWNLOAD_URL,
            installPath = Environment.KOTLIN_LSP_HOME,
            onInstallComplete = {
                log.info("Installation completed, starting server...")
                coroutineScope.launch { launchProcessAndRegister() }
            },
            onInstallCancelled = { log.warn("User cancelled the Kotlin LSP installation.") }
        )
        LspEventBus.postInstallRequest(installEvent)
    }

    private suspend fun launchProcessAndRegister() {
        try {
            if (serverProcess?.isAlive == true) {
                log.info("Kotlin LSP process is already running.")
                return
            }

            val binDir = File(Environment.KOTLIN_LSP_HOME, "bin")
            val launcherScript = File(binDir, KotlinServerConstants.LAUNCHER_SCRIPT_NAME)
            launcherScript.setExecutable(true, false)
            
            if (!launcherScript.canExecute()) {
                throw IllegalStateException("Kotlin LSP launcher is not executable: ${launcherScript.absolutePath}")
            }

            // 获取 Android 环境所需的 Classpath
            val androidClasspath = classpathProvider?.getClasspath() ?: ""

            serverProcess = startKotlinLspWithTermuxShellApi(launcherScript, androidClasspath)
            log.info("Kotlin LSP process started with Termux shell API. PID: ${serverProcess?.hashCode()}")
            startStderrMonitor(serverProcess!!)

            // 初始化 LSP4J 通讯协议机制
            currentServerImpl?.shutdown()
            
            val newServerImpl = KotlinLanguageServerImpl(process = serverProcess!!)
            val remoteProxy = createLsp4jProxy(serverProcess!!, newServerImpl)
            newServerImpl.bindRemoteServer(remoteProxy)
            
            newServerImpl.connectClient(kotlinClient)
            currentServerImpl = newServerImpl

            // 全局注册与挂载
            val registry = ILanguageServerRegistry.getDefault()
            registry.register(newServerImpl)
            
            bindWorkspaceWhenReady()
            log.info("KotlinLanguageServerImpl successfully connected to LSP4J and registered to IDE.")
        } catch (e: Exception) {
            log.error("Failed to launch Kotlin LSP process with Termux shell API", e)
        }
    }

    /**
     * 按 Termux Shell API 启动 Kotlin LSP。
     * 要点：
     * 1) 使用 TermuxShellEnvironment 生成专用环境变量；
     * 2) 直接执行 bin 下 LAUNCHER_SCRIPT_NAME；
     * 3) 保持 stderr 与 stdout 分离，避免污染 LSP JSON-RPC 通道。
     */
    private fun startKotlinLspWithTermuxShellApi(
        launcherScript: File,
        androidClasspath: String
    ): Process {
        val executionCommand =
            ExecutionCommand(
                TermuxShellManager.getNextShellId(),
                launcherScript.absolutePath,
                null,
                null,
                Environment.KOTLIN_LSP_HOME.absolutePath,
                ExecutionCommand.Runner.APP_SHELL.runnerName,
                false
            ).apply {
                commandLabel = "Kotlin Language Server"
                shellName = "kotlin-lsp-daemon"
                setShellCommandShellEnvironment = true
            }

        val termuxEnvironment = TermuxShellEnvironment()
        val commandArgs =
            termuxEnvironment.setupShellCommandArguments(
                executionCommand.executable,
                executionCommand.arguments
            )
        val shellEnv =
            HashMap(termuxEnvironment.setupShellCommandEnvironment(context.applicationContext, executionCommand))

        val kotlinLspJvmOptions =
            listOf(
                    shellEnv["KOTLIN_LANGUAGE_SERVER_OPTS"],
                    "-Dsqlite.purejava=true",
                    "-Dorg.sqlite.tmpdir=${context.cacheDir.absolutePath}",
                    "-Djava.io.tmpdir=${context.cacheDir.absolutePath}",
                )
                .filterNotNull()
                .joinToString(" ")
                .trim()

        shellEnv["JAVA_HOME"] = Environment.JAVA_HOME.absolutePath
        shellEnv["KOTLIN_LANGUAGE_SERVER_OPTS"] = kotlinLspJvmOptions
        shellEnv["ORG_SQLITE_PUREJAVA"] = "true"
        shellEnv["KOTLIN_LSP_DISABLE_DEPENDENCY_RESOLUTION"] = "true"
        shellEnv["KOTLIN_LSP_USE_PREDEFINED_CLASSPATH"] = "true"
        shellEnv["KOTLIN_LSP_CLASSPATH"] = androidClasspath
        shellEnv["CLASSPATH"] = androidClasspath

        return ProcessBuilder(*commandArgs)
            .directory(Environment.KOTLIN_LSP_HOME)
            .apply {
                environment().clear()
                environment().putAll(shellEnv)
                redirectErrorStream(false)
            }
            .start()
    }

    /**
     * 核心挂载器：创建并启动 LSP4J 协议桥
     */
    private fun createLsp4jProxy(
        process: Process,
        serverImpl: KotlinLanguageServerImpl
    ): org.eclipse.lsp4j.services.LanguageServer {
        val clientEndpoint = serverImpl.clientEndpoint
        launcher = LSPLauncher.createClientLauncher(
            clientEndpoint,
            process.inputStream,
            process.outputStream
        )
        
        // 启动异步线程窃听服务器返回
        launcher?.startListening()
        
        return launcher!!.remoteProxy
    }

    private fun bindWorkspaceWhenReady() {
        coroutineScope.launch {
            repeat(20) { attempt ->
                val workspace = IProjectManager.getInstance().getWorkspace()
                if (workspace != null) {
                    currentServerImpl?.setupWorkspace(workspace)
                    if (currentServerImpl?.isReady() == true) {
                        currentServerImpl?.applySettings(KotlinServerSettings())
                        log.info("Kotlin LSP workspace has been initialized and synchronized via LSP4J.")
                    } else {
                        log.warn(
                            "Kotlin LSP workspace binding attempted, but server initialization failed: {}",
                            currentServerImpl?.getLastInitError() ?: "unknown error"
                        )
                    }
                    return@launch
                }
                if (attempt < 19) {
                    delay(300)
                }
            }
            log.info("Workspace unavailable while bootstrapping Kotlin LSP. Running in standalone-file mode until project is ready.")
        }
    }

    private fun startStderrMonitor(process: Process) {
        coroutineScope.launch {
            try {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        // 过滤 SLF4J 1.7.x 的 "找不到 StaticLoggerBinder 绑定" 警告.
                        // 原因: kotlin-language-server 自带 slf4j-api-1.7.25.jar,
                        // 但官方发布包里没带 slf4j-nop / slf4j-simple 等绑定实现.
                        // SLF4J 1.7.x 找不到绑定时会 fallback 到 NOP logger,
                        // 这 3 行警告是 SLF4J 1.7.x 启动时的固定行为, 不影响 LSP 功能.
                        // 修根本问题需要重新打包 LSP 包含 slf4j-nop, 但 IDE 侧无
                        // jar 资源, 这里仅在 stderr monitor 中过滤避免日志噪音.
                        if (isSlf4jBindingWarning(line)) return@forEach
                        log.debug("Kotlin LSP stderr: $line")
                    }
                }
            } catch (_: Exception) {
                // ignore stream close during shutdown
            }
        }
    }

    /**
     * 判断 stderr 行是否是 SLF4J 1.7.x 的绑定警告.
     * 这些行在每次 LSP 启动时一定会出现 3 次, 与 IDE 无关.
     */
    private fun isSlf4jBindingWarning(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.startsWith("SLF4J:")) return true
        // SLF4J 1.7.25 还会附一个 URL 提示, 包含 slf4j.org
        if (trimmed.contains("StaticLoggerBinder")) return true
        if (trimmed.contains("slf4j.org/codes.html")) return true
        return false
    }

    fun stopServer() {
        currentServerImpl?.shutdown()
        serverProcess?.destroy()
        serverProcess = null
        currentServerImpl = null
        launcher = null
    }
}
