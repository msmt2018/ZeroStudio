package com.itsaky.androidide.fragments.debugger.console

import android.zero.studio.commandexamples.data.local.model.CommandEntity
import android.zero.studio.commandexamples.domain.repository.CommandRepository
import android.zero.studio.core.domain.model.SortType
import android.zero.studio.shell.common.domain.model.OutputLine
import android.zero.studio.shell.common.domain.repository.ShellRepository
import android.zero.studio.shell.otg_adb_shell.domain.model.OtgConnection
import android.zero.studio.shell.otg_adb_shell.domain.model.OtgState
import android.zero.studio.shell.otg_adb_shell.domain.repository.OtgRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsaky.androidide.debugger.connection.status.ChannelStatus
import com.itsaky.androidide.debugger.connection.status.ConnectionStatusAggregator
import com.itsaky.androidide.debugger.root.RootAdbBridge
import com.itsaky.androidide.debugger.root.RootManager
import com.itsaky.androidide.debugger.root.RootState
import com.itsaky.androidide.ui.theme.deviceconnection.DcChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 命令执行通道。决定命令走哪个后端。 */
enum class AdbChannel(val displayName: String) {
    BASIC("本机 sh"),
    SHIZUKU("Shizuku"),
    ROOT("Root ADB"),
    OTG("OTG ADB"),
}

/** 命令示范列表排序方式。 */
enum class CommandSortType(val label: String) {
    AZ("A→Z"),
    ZA("Z→A"),
    MOST_USED("最常用"),
    LEAST_USED("最少用"),
    FAVORITE("收藏"),
    ;

    /** 映射到 connection 模块 [SortType] 常量。 */
    fun toSortTypeConstant(): Int = when (this) {
        AZ -> SortType.AZ
        ZA -> SortType.ZA
        MOST_USED -> SortType.MOST_USED
        LEAST_USED -> SortType.LEAST_USED
        FAVORITE -> SortType.AZ // FAVORITE 走单独分支，不会真正用到此值
    }
}

/**
 * adb 命令执行页 ViewModel。
 *
 * 职责：
 * - 暴露当前可用通道（依据 Shizuku / Root / OTG / WiFi ADB 状态动态计算）
 * - 维护活动通道选择，命令执行按活动通道路由到对应后端
 * - 流式收集命令输出，按行批量推送给 UI（避免每行触发重组）
 * - 与 [CommandRepository] 交互，提供命令示范列表的搜索 / 排序 / 收藏 / 使用计数
 * - FAB 菜单：加载预置命令 / 添加自定义命令 / 仅看收藏
 *
 * 命令执行后端映射（见 spec §6.6）：
 * - BASIC → [ShellRepository.executeBasicCommand]
 * - SHIZUKU → [ShellRepository.executeShizukuCommand]
 * - ROOT → [RootAdbBridge.execOnActiveDevice]
 * - OTG → [OtgRepository.runOtgCommand]
 *
 * 输出批量策略（spec §6.6）：每 100 行或 250ms 推送一次。
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AdbConsoleViewModel @Inject constructor(
    private val shellRepository: ShellRepository,
    private val otgRepository: OtgRepository,
    private val rootAdbBridge: RootAdbBridge,
    private val rootManager: RootManager,
    private val commandRepository: CommandRepository,
    private val aggregator: ConnectionStatusAggregator,
) : ViewModel() {

    /** 所有通道的当前状态（用于顶部活动连接条 + 通道选择器可用性判断）。 */
    val statuses: StateFlow<List<ChannelStatus>> = aggregator.allStatuses
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前可用通道列表。BASIC 始终可用，其它依据连接状态过滤。 */
    val availableChannels: StateFlow<List<AdbChannel>> = combine(
        shellRepository.shizukuPermissionState(),
        rootManager.rootState,
        rootAdbBridge.deviceList,
        OtgConnection.state,
    ) { shizuku, root, devices, otg ->
        buildList {
            add(AdbChannel.BASIC)
            if (shizuku) add(AdbChannel.SHIZUKU)
            if (root is RootState.Granted && devices.any { it.isActive }) {
                add(AdbChannel.ROOT)
            }
            if (otg is OtgState.Connected) add(AdbChannel.OTG)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf(AdbChannel.BASIC))

    /** 活动通道。默认 BASIC。 */
    private val _activeChannel = MutableStateFlow(AdbChannel.BASIC)
    val activeChannel: StateFlow<AdbChannel> = _activeChannel.asStateFlow()

    /** 命令输入。 */
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    /** 输出行流。 */
    private val _output = MutableStateFlow<List<OutputLine>>(emptyList())
    val output: StateFlow<List<OutputLine>> = _output.asStateFlow()

    /** 是否正在执行命令。 */
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** 历史命令（最多 50 条）。 */
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    /** 示范命令搜索关键字。 */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 示范命令排序方式。 */
    private val _sortType = MutableStateFlow(CommandSortType.AZ)
    val sortType: StateFlow<CommandSortType> = _sortType.asStateFlow()

    /** 当前选中的标签（null 表示不按标签筛选）。落实 spec §6.5「筛选 labels」。 */
    private val _selectedLabel = MutableStateFlow<String?>(null)
    val selectedLabel: StateFlow<String?> = _selectedLabel.asStateFlow()

    /**
     * 命令示范列表。根据搜索 + 排序 + 标签筛选实时计算。落实 spec §6.5。
     *
     * 优先级：搜索框 > 标签筛选 > 排序。
     * - 搜索框有内容：走 [CommandRepository.searchCommands]（匹配 command/description/labels）
     * - 选了标签：走 [CommandRepository.searchCommands]（用标签名作为查询，命中 labels 字段）
     * - 仅收藏：走 [CommandRepository.getFavoriteCommands]
     * - 其它排序：走 [CommandRepository.getSortedCommands]
     */
    val commandExamples: StateFlow<List<CommandEntity>> = combine(
        _query,
        _sortType,
        _selectedLabel,
    ) { q, sort, label -> Triple(q, sort, label) }
        .flatMapLatest { (q, sort, label) ->
            when {
                q.isNotBlank() -> commandRepository.searchCommands(q)
                label != null -> commandRepository.searchCommands(label) // 复用 search 命中 labels 字段
                sort == CommandSortType.FAVORITE -> commandRepository.getFavoriteCommands()
                else -> commandRepository.getSortedCommands(sort.toSortTypeConstant())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 所有可用 label（用于筛选 chip）。 */
    val allLabels: StateFlow<List<String>> = commandRepository.getAllLabels()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 预置命令加载进度（0~1），null 表示未在加载。 */
    private val _loadProgress = MutableStateFlow<Float?>(null)
    val loadProgress: StateFlow<Float?> = _loadProgress.asStateFlow()

    /** 最近一次操作的提示信息（供 UI snackbar 显示）。 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** 消费 toast。 */
    fun consumeToast() {
        _toast.value = null
    }

    /** 当前执行任务，便于取消。 */
    private var execJob: Job? = null

    /** 是否存在可用连接（除 BASIC 外任一通道可用）。 */
    val hasUsableConnection: StateFlow<Boolean> = availableChannels
        .map { it.any { ch -> ch != AdbChannel.BASIC } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // 落实 spec §6.4：选了通道但通道掉线时自动回退到 BASIC + 提示
        viewModelScope.launch {
            availableChannels.collect { channels ->
                val current = _activeChannel.value
                if (current != AdbChannel.BASIC && !channels.contains(current)) {
                    _activeChannel.value = AdbChannel.BASIC
                    _toast.value = "通道 $current 已掉线，已回退到本机 sh"
                }
            }
        }
    }

    /** 设置活动通道。 */
    fun setActiveChannel(channel: AdbChannel) {
        if (availableChannels.value.contains(channel)) {
            _activeChannel.value = channel
        }
    }

    /** 设置命令输入。 */
    fun setInput(text: String) {
        _input.value = text
    }

    /** 设置搜索关键字。 */
    fun setQuery(text: String) {
        _query.value = text
    }

    /** 设置排序方式。 */
    fun setSortType(type: CommandSortType) {
        _sortType.value = type
    }

    /**
     * 设置标签筛选。传入 null 清除筛选。落实 spec §6.5「筛选 labels」。
     */
    fun setSelectedLabel(label: String?) {
        _selectedLabel.value = label
    }

    /**
     * 刷新所有通道状态。落实 spec §7.4：TopBar 刷新按钮触发并行刷新。
     */
    fun refreshAll() {
        viewModelScope.launch {
            runCatching {
                shellRepository.refreshShizukuPermission()
                rootManager.probe()
                rootAdbBridge.refreshDevices()
            }
        }
    }

    /**
     * 执行当前输入框中的命令。
     *
     * - 无可用通道时直接 emit 一条错误提示
     * - 执行前清空输出区，emit 命令回显（`$ <cmd>`）
     * - 流式收集输出，每 100 行或 250ms 批量推送一次（避免高频重组）
     * - 完成后 emit `exit=<code>`（若可获取）
     */
    fun runCommand() {
        val command = _input.value.trim()
        if (command.isEmpty() || _running.value) return
        val channel = _activeChannel.value
        if (!availableChannels.value.contains(channel)) {
            appendOutput(OutputLine("通道 $channel 不可用，请先在设备连接页建立连接", isError = true))
            return
        }

        // 记录历史
        _history.value = (_history.value + command).takeLast(50)

        execJob?.cancel()
        _running.value = true
        _output.value = listOf(OutputLine("$ $command", isError = false))

        execJob = viewModelScope.launch {
            // 批量缓冲：250ms 或 100 行触发一次推送
            val buffer = mutableListOf<OutputLine>()
            var lastFlush = System.currentTimeMillis()

            // 启动 flush 协程
            val flushJob = launch {
                while (true) {
                    delay(50)
                    val now = System.currentTimeMillis()
                    if (buffer.isNotEmpty() && (buffer.size >= 100 || now - lastFlush >= 250)) {
                        flushBuffer(buffer)
                        buffer.clear()
                        lastFlush = now
                    }
                }
            }

            val flow = when (channel) {
                AdbChannel.BASIC -> shellRepository.executeBasicCommand(command)
                AdbChannel.SHIZUKU -> shellRepository.executeShizukuCommand(command)
                AdbChannel.ROOT -> rootAdbBridge.execOnActiveDevice(command)
                AdbChannel.OTG -> otgRepository.runOtgCommand(command)
            }
            runCatching {
                flow.collect { line ->
                    buffer.add(line)
                    // 即时触发：达到 100 行立即 flush
                    if (buffer.size >= 100) {
                        flushBuffer(buffer)
                        buffer.clear()
                        lastFlush = System.currentTimeMillis()
                    }
                }
            }.onFailure { e ->
                buffer.add(OutputLine("执行出错: ${e.message ?: e.javaClass.simpleName}", isError = true))
            }

            // 收尾：把剩余缓冲 flush 出去
            flushJob.cancel()
            if (buffer.isNotEmpty()) flushBuffer(buffer)
            _running.value = false
        }
    }

    /** 把一批输出行原子追加到 [_output]。 */
    private fun flushBuffer(lines: List<OutputLine>) {
        if (lines.isEmpty()) return
        _output.value = _output.value + lines
    }

    /** 停止当前命令。 */
    fun stopCommand() {
        execJob?.cancel()
        execJob = null
        runCatching {
            when (_activeChannel.value) {
                AdbChannel.BASIC, AdbChannel.SHIZUKU -> shellRepository.stopCommand()
                AdbChannel.ROOT -> rootAdbBridge.stopCommand()
                AdbChannel.OTG -> otgRepository.stopCommand()
            }
        }
        _running.value = false
        appendOutput(OutputLine("^C", isError = true))
    }

    /** 清空输出。 */
    fun clearOutput() {
        _output.value = emptyList()
    }

    /** 选用历史命令填充输入框。 */
    fun pickHistory(command: String) {
        _input.value = command
    }

    /** 切换命令收藏状态。 */
    fun toggleFavorite(command: CommandEntity) {
        viewModelScope.launch {
            commandRepository.updateFavoriteStatus(command.id, !command.isFavourite)
        }
    }

    /** 命令被点击：填充输入框 + 增加使用计数。 */
    fun useCommand(command: CommandEntity) {
        _input.value = command.command
        viewModelScope.launch {
            commandRepository.incrementUseCount(command.id)
        }
    }

    /**
     * 加载预置命令到数据库。落实 spec §6.7 FAB 菜单「加载预置命令」。
     */
    fun loadDefaultCommands() {
        if (_loadProgress.value != null) return
        viewModelScope.launch {
            _loadProgress.value = 0f
            runCatching {
                commandRepository.loadDefaultCommandsWithProgress().collect { p ->
                    _loadProgress.value = p
                }
            }.onSuccess {
                _toast.value = "预置命令已加载"
            }.onFailure { e ->
                _toast.value = "加载失败：${e.message}"
            }
            _loadProgress.value = null
        }
    }

    /**
     * 添加自定义命令。落实 spec §6.7 FAB 菜单「添加自定义命令」。
     */
    fun addCustomCommand(command: String, description: String, labels: List<String>) {
        if (command.isBlank()) {
            _toast.value = "命令不能为空"
            return
        }
        viewModelScope.launch {
            commandRepository.insertCommand(
                CommandEntity(
                    command = command,
                    description = description.ifBlank { "自定义命令" },
                    labels = labels,
                )
            )
            _toast.value = "已添加自定义命令"
        }
    }

    /**
     * 切换「仅看收藏」模式。落实 spec §6.7 FAB 菜单「书签」。
     */
    fun toggleFavoriteOnly() {
        _sortType.value = if (_sortType.value == CommandSortType.FAVORITE) {
            CommandSortType.AZ
        } else {
            CommandSortType.FAVORITE
        }
    }

    /** 删除命令。 */
    fun deleteCommand(id: Int) {
        viewModelScope.launch { commandRepository.deleteCommand(id) }
    }

    /** 追加输出（线程安全）。 */
    private fun appendOutput(line: OutputLine) {
        _output.value = _output.value + line
    }

    /** 活动通道对应的状态（用于顶部活动连接条）。 */
    fun statusForChannel(channel: AdbChannel): ChannelStatus? {
        val all = statuses.value
        return when (channel) {
            AdbChannel.BASIC -> ChannelStatus(DcChannel.SHIZUKU, com.itsaky.androidide.ui.theme.deviceconnection.DcStatusLevel.GREEN, "本机 sh")
            AdbChannel.SHIZUKU -> all.firstOrNull { it.channel == DcChannel.SHIZUKU }
            AdbChannel.ROOT -> all.firstOrNull { it.channel == DcChannel.ROOT }
            AdbChannel.OTG -> all.firstOrNull { it.channel == DcChannel.OTG }
        }
    }

    override fun onCleared() {
        super.onCleared()
        execJob?.cancel()
        runCatching { shellRepository.stopCommand() }
        runCatching { otgRepository.stopCommand() }
        runCatching { rootAdbBridge.stopCommand() }
    }
}