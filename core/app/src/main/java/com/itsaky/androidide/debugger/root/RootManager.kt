package com.itsaky.androidide.debugger.root

import android.zero.studio.shell.common.domain.model.OutputLine
import android.zero.studio.shell.common.domain.repository.ShellRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root 权限统一管理器。
 *
 * 探测与执行入口：
 * - **标准 su**：复用 [ShellRepository.hasRootAccess]（Runtime.exec("su") 探测）+ [ShellRepository.executeRootCommand]
 * - **KernelSU**：探测 `/data/adb/ksu` 目录，执行走 root shell（最终也是 su 入口）
 * - **Magisk**：探测 `/data/adb/magisk` 目录
 * - **APatch**：探测 `/data/adb/apatch` 目录
 *
 * 暴露 [rootState] 给状态通道系统与 Root 卡片消费。
 */
@Singleton
class RootManager @Inject constructor(
    private val shellRepository: ShellRepository,
) {

    private val _rootState = MutableStateFlow<RootState>(RootState.Idle)
    val rootState: StateFlow<RootState> = _rootState.asStateFlow()

    /**
     * 探测 root 状态。先看标准 su 是否可用，再用 root shell 列 `/data/adb/` 识别管理器。
     *
     * 探测期间 [rootState] 为 [RootState.Probing]；完成后变为 [RootState.Granted] / [RootState.Denied] / [RootState.Error]。
     */
    suspend fun probe() = withContext(Dispatchers.IO) {
        _rootState.value = RootState.Probing
        runCatching {
            if (!shellRepository.hasRootAccess()) {
                _rootState.value = RootState.Denied
                return@runCatching
            }
            val manager = detectManagerType()
            _rootState.value = RootState.Granted(manager)
        }.onFailure { e ->
            _rootState.value = RootState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 识别当前生效的 Root 管理器类型。已确认有 root 权限后调用。
     *
     * 通过 `ls /data/adb/` 输出判断：ksu/magisk/apatch 目录命中即对应管理器，
     * 否则视为标准 su。
     */
    private suspend fun detectManagerType(): RootManagerType {
        val output = runRootCommandCapture("ls /data/adb/ 2>/dev/null")
        return when {
            output.contains("ksu") -> RootManagerType.KERNEL_SU
            output.contains("magisk") -> RootManagerType.MAGISK
            output.contains("apatch") -> RootManagerType.APATCH
            else -> RootManagerType.STANDARD_SU
        }
    }

    /**
     * 在 root shell 中执行命令，返回 Flow<OutputLine>。供 RootAdbBridge 与 Root 卡片的「申请权限」流程复用。
     */
    suspend fun executeRootCommand(command: String): Flow<OutputLine> =
        shellRepository.executeRootCommand(command)

    /**
     * 取消当前正在执行的 root 命令。委托给 [ShellRepository.stopCommand]。
     */
    fun stopRootCommand() {
        shellRepository.stopCommand()
    }

    /** 同步获取当前是否已授权。 */
    val isGranted: Boolean
        get() = _rootState.value is RootState.Granted

    /** 同步获取当前管理器类型（已授权时）。 */
    val currentManagerType: RootManagerType?
        get() = (_rootState.value as? RootState.Granted)?.manager

    /**
     * 内部辅助：执行 root 命令并把输出合并为单个字符串。
     */
    private suspend fun runRootCommandCapture(command: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder()
            shellRepository.executeRootCommand(command).collect { line ->
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(line.text)
            }
            sb.toString()
        }.getOrElse { "" }
    }
}