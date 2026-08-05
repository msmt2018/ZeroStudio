package com.itsaky.androidide.debugger.root

import android.zero.studio.shell.common.domain.model.OutputLine
import android.zero.studio.shell.common.domain.repository.ShellRepository
import com.topjohnwu.superuser.Shell
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
 * 落实 spec §4.3.1：统一探测 标准 su / KernelSU / Magisk / APatch。
 *
 * 探测与执行入口（双轨）：
 * - **标准 su**：复用 [ShellRepository.hasRootAccess]（Runtime.exec("su") 探测）+ [ShellRepository.executeRootCommand]
 * - **libsu 轨**：使用 [Shell.getShell] 检测 root shell 可用性，[Shell.cmd] 执行命令；
 *   对 KernelSU/Magisk/APatch 通过 `ls /data/adb/` 识别后再用 libsu 执行（管理器自带的 su 包装）。
 *
 * 暴露 [rootState] 给状态通道系统与 Root 卡片消费。
 */
@Singleton
class RootManager @Inject constructor(
    private val shellRepository: ShellRepository,
) {

    private val _rootState = MutableStateFlow<RootState>(RootState.Idle)
    val rootState: StateFlow<RootState> = _rootState.asStateFlow()

    init {
        // 配置 libsu：使用全局 Shell 池，失败时回退到标准 su
        runCatching {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(15)
            )
        }
    }

    /**
     * 探测 root 状态。落实 spec §4.3.1。
     *
     * 双轨探测策略：
     * 1. 先用 libsu [Shell.getShell] 尝试获取 root shell，[Shell.isRoot] 为 true 即确认 root 可用
     * 2. 若 libsu 不可用（未安装管理器或被拒），回退到 [ShellRepository.hasRootAccess]（标准 su）
     * 3. 确认 root 后，用 root shell 列 `/data/adb/` 识别具体管理器类型
     *
     * 探测期间 [rootState] 为 [RootState.Probing]；
     * 完成后变为 [RootState.Granted] / [RootState.Denied] / [RootState.Error]。
     */
    suspend fun probe() = withContext(Dispatchers.IO) {
        _rootState.value = RootState.Probing
        runCatching {
            // 轨道 1: libsu 检测（KSU/Magisk/APatch 都会被 libsu 识别为 root shell）
            val libsuRoot = runCatching {
                Shell.getShell().isRoot
            }.getOrDefault(false)

            // 轨道 2: 标准 su 检测（libsu 失败时回退）
            val standardSuRoot = if (libsuRoot) true else shellRepository.hasRootAccess()

            if (!standardSuRoot) {
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
     * 按指定管理器类型执行差异化授权探测。落实 spec §4.3.1。
     *
     * 用户在 [RootManagerPickerSheet] 选中某个管理器后调用此方法。
     * 会先验证该管理器是否真实可用，再尝试授权。
     *
     * - **STANDARD_SU**：直接执行 `su -c id` 验证
     * - **KERNEL_SU**：检测 `/system/bin/ksud` 或 `/data/adb/ksu` 存在，再走 su 授权
     * - **MAGISK**：检测 `/data/adb/magisk` 存在，再走 su 授权
     * - **APATCH**：检测 `/data/adb/apatch` 存在，再走 su 授权
     *
     * @param type 用户选择的管理器类型
     */
    suspend fun probeAs(type: RootManagerType) = withContext(Dispatchers.IO) {
        _rootState.value = RootState.Probing
        runCatching {
            // 先验证管理器是否真实存在
            val managerAvailable = checkManagerAvailable(type)
            if (!managerAvailable) {
                _rootState.value = RootState.Error("${type.displayName} 未检测到")
                return@runCatching
            }
            // 执行授权（所有管理器最终都通过 su 执行 `id` 验证）
            val authorized = runCatching {
                val result = Shell.cmd("id").exec()
                result.isSuccess
            }.getOrDefault(false)

            if (!authorized) {
                _rootState.value = RootState.Denied
                return@runCatching
            }
            _rootState.value = RootState.Granted(type)
        }.onFailure { e ->
            _rootState.value = RootState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 检测指定管理器是否可用。落实 spec §4.3.1。
     *
     * 通过文件系统检查（在 root shell 下）确认管理器目录/二进制是否存在。
     *
     * @return true 表示该管理器已安装
     */
    private suspend fun checkManagerAvailable(type: RootManagerType): Boolean = withContext(Dispatchers.IO) {
        // 先确认有 root shell（任意管理器可用前提）
        val hasRootShell = runCatching { Shell.getShell().isRoot }.getOrDefault(false) ||
            shellRepository.hasRootAccess()
        if (!hasRootShell) return@withContext false

        val checkCmd = when (type) {
            RootManagerType.STANDARD_SU -> "which su" // 标准 su 总是可用（已确认 root shell）
            RootManagerType.KERNEL_SU -> "ls /system/bin/ksud /data/adb/ksu 2>/dev/null"
            RootManagerType.MAGISK -> "ls /data/adb/magisk 2>/dev/null"
            RootManagerType.APATCH -> "ls /data/adb/apatch 2>/dev/null"
        }
        val output = runCatching {
            Shell.cmd(checkCmd).exec().out.joinToString("\n")
        }.getOrElse {
            runRootCommandCapture(checkCmd)
        }
        when (type) {
            RootManagerType.STANDARD_SU -> output.contains("/su") || output.isNotEmpty()
            RootManagerType.KERNEL_SU -> output.contains("ksu")
            RootManagerType.MAGISK -> output.contains("magisk")
            RootManagerType.APATCH -> output.contains("apatch")
        }
    }

    /**
     * 探测所有已安装的管理器。供 [RootManagerPickerSheet] 显示可用性。
     *
     * @return 已检测到的管理器集合（可能为空）
     */
    suspend fun detectAvailableManagers(): Set<RootManagerType> = withContext(Dispatchers.IO) {
        val hasRootShell = runCatching { Shell.getShell().isRoot }.getOrDefault(false) ||
            shellRepository.hasRootAccess()
        if (!hasRootShell) return@withContext emptySet()

        val result = mutableSetOf<RootManagerType>()
        RootManagerType.values().forEach { type ->
            if (checkManagerAvailable(type)) result.add(type)
        }
        result
    }

    /**
     * 识别当前生效的 Root 管理器类型。已确认有 root 权限后调用。
     *
     * 通过 `ls /data/adb/` 输出判断：ksu/magisk/apatch 目录命中即对应管理器，
     * 否则视为标准 su。
     *
     * 优先使用 libsu [Shell.cmd] 执行（更稳定，能识别管理器注入的环境变量），
     * 失败时回退到 [ShellRepository.executeRootCommand]。
     */
    private suspend fun detectManagerType(): RootManagerType {
        val output = runCatching {
            // 优先用 libsu（管理器环境变量更完整）
            val result = Shell.cmd("ls /data/adb/ 2>/dev/null").exec()
            result.out.joinToString("\n")
        }.getOrElse {
            // 回退到标准 su
            runRootCommandCapture("ls /data/adb/ 2>/dev/null")
        }
        return when {
            output.contains("ksu") -> RootManagerType.KERNEL_SU
            output.contains("magisk") -> RootManagerType.MAGISK
            output.contains("apatch") -> RootManagerType.APATCH
            else -> RootManagerType.STANDARD_SU
        }
    }

    /**
     * 在 root shell 中执行命令，返回 Flow<OutputLine>。
     *
     * 供 RootAdbBridge 与 Root 卡片的「申请权限」流程复用。
     * 委托给 [ShellRepository.executeRootCommand]（标准 su 轨）。
     */
    suspend fun executeRootCommand(command: String): Flow<OutputLine> =
        shellRepository.executeRootCommand(command)

    /**
     * 用 libsu 执行 root 命令，返回同步结果字符串。
     *
     * 落实 spec §4.3.1「KSU / Magisk 走 libsu Shell.cmd(...).exec()」。
     * 适用于需要一次性获取完整输出的场景（如 `adb devices`、`adb connect`）。
     *
     * @return 输出文本（stdout + stderr），失败时抛异常
     */
    suspend fun executeLibsuCapture(command: String): String = withContext(Dispatchers.IO) {
        val result = Shell.cmd(command).exec()
        buildString {
            if (result.out.isNotEmpty()) append(result.out.joinToString("\n"))
            if (result.err.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append(result.err.joinToString("\n"))
            }
        }
    }

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