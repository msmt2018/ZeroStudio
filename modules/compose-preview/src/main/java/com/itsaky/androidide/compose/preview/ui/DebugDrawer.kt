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

package com.itsaky.androidide.compose.preview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 调试面板 v2.1.
 *
 * 一个 [ModalBottomSheet] 容器, 包含 4 个 tab:
 * 1. **Inspector** — Component 节点树 (来自 [ComponentInspectorPanel])
 * 2. **Recompose** — Recompose 计数 (来自 [RecompositionPanel])
 * 3. **Logcat** — 拦截的 stdout/stderr (来自 [LogcatPanel])
 * 4. **Stats** — Build phase 计时 + ClassLoader 缓存命中率 (内嵌 [StatsPanel])
 *
 * ## 接入示例
 *
 * ```kotlin
 * val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
 * var showDrawer by remember { mutableStateOf(false) }
 *
 * // 顶栏的 "Bug" 按钮:
 * IconButton(onClick = { showDrawer = !showDrawer }) { Icon(Icons.Filled.BugReport, ...) }
 *
 * if (showDrawer) {
 *     DebugDrawer(
 *         sheetState = sheetState,
 *         onDismiss = { showDrawer = false },
 *         inspectorNodes = inspectorNodes,
 *         recomposeTracker = recomposeTracker,
 *         logcat = PreviewLog.sink,
 *         stats = currentStats,
 *     )
 * }
 * ```
 *
 * @param sheetState ModalBottomSheet 状态
 * @param onDismiss 关闭回调
 * @param inspectorNodes 当前节点列表 (Inspector tab)
 * @param recomposeTracker 全局 RecomposeTracker (Recompose tab)
 * @param logcat 日志源 (Logcat tab)
 * @param stats 构建统计 (Stats tab)
 * @param modifier Modifier
 */
@Composable
fun DebugDrawer(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    inspectorNodes: List<NodeInfo>,
    recomposeTracker: RecomposeTracker,
    logcat: PreviewLogcatSink,
    stats: BuildStats,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(DebugTab.Inspector) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        contentWindowInsets = { WindowInsets.systemBars },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // 头部: 标题 + 关闭
            DebugDrawerHeader(
                tab = selectedTab,
                onClose = onDismiss,
            )

            HorizontalDivider()

            // 主体
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (selectedTab) {
                    DebugTab.Inspector -> ComponentInspectorPanel(
                        nodes = inspectorNodes,
                        modifier = Modifier.fillMaxSize(),
                    )
                    DebugTab.Recompose -> RecompositionPanel(
                        tracker = recomposeTracker,
                        modifier = Modifier.fillMaxSize(),
                    )
                    DebugTab.Logcat -> LogcatPanel(
                        sink = logcat,
                        modifier = Modifier.fillMaxSize(),
                    )
                    DebugTab.Stats -> StatsPanel(
                        stats = stats,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            HorizontalDivider()

            // 底部 NavigationBar
            DebugDrawerNavBar(
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
        }
    }
}

/** 4 个 tab 枚举. */
enum class DebugTab(
    val label: String,
    val icon: ImageVector,
) {
    Inspector("Inspect", Icons.Filled.Layers),
    Recompose("Recomp", Icons.Filled.BugReport),
    Logcat("Log", Icons.Filled.Terminal),
    Stats("Stats", Icons.Filled.Analytics),
}

/**
 * 头部: 标题 + 当前 tab 名 + 关闭按钮.
 */
@Composable
private fun DebugDrawerHeader(
    tab: DebugTab,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Debug · ${tab.label}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "关闭",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .padding(4.dp),
        )
    }
}

/**
 * 底部 NavigationBar: 4 tab 切换.
 */
@Composable
private fun DebugDrawerNavBar(
    selected: DebugTab,
    onSelect: (DebugTab) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
    ) {
        DebugTab.values().forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(text = tab.label, fontSize = 10.sp)
                },
            )
        }
    }
}

// =============================================================================
// BuildStats (P1-UI-05 统计面板数据)
// =============================================================================

/**
 * 一次构建 / 一次加载的统计 v2.1.
 *
 * 由 ViewModel / ComposableRenderer 在每次 build 完成时生成新实例, 推到 [DebugDrawer].
 * 所有数字累计而非单次 (启动到现在的总量).
 *
 * @property phaseTimings 4 个关键 build 阶段的耗时 (ms)
 * @property loadedClassCount ClassLoader 累计 loadClass 次数
 * @property cacheHitCount 命中数
 * @property cacheMissCount 未命中数 (= loadedClassCount - cacheHitCount)
 * @property classCacheSize 当前 classCache 大小
 * @property activeLoaderCount 当前 DexClassLoader pool 大小
 * @property lastCompileMs 最近一次 compile 耗时
 * @property totalCompileMs 累计 compile 耗时
 * @property lastRenderMs 最近一次 render 耗时 (从 setContent 到首帧)
 * @property totalRenderMs 累计 render 耗时
 */
data class BuildStats(
    val phaseTimings: BuildPhaseTimings = BuildPhaseTimings(),
    val loadedClassCount: Long = 0,
    val cacheHitCount: Long = 0,
    val cacheMissCount: Long = 0,
    val classCacheSize: Int = 0,
    val activeLoaderCount: Int = 0,
    val lastCompileMs: Long = 0,
    val totalCompileMs: Long = 0,
    val lastRenderMs: Long = 0,
    val totalRenderMs: Long = 0,
) {
    /**
     * 缓存命中率, 范围 [0f, 1f]. 0 当 loadedClassCount == 0.
     */
    val cacheHitRate: Float
        get() = if (loadedClassCount == 0L) 0f
                else cacheHitCount.toFloat() / loadedClassCount.toFloat()

    companion object {
        val EMPTY = BuildStats()
    }
}

/**
 * 4 个关键 build 阶段耗时.
 *
 * - [init]   Assets 解压 + AndroidJar 定位
 * - [compile] K2JVMCompiler 调用 + 输出 .class
 * - [dex]    D8 转换 .class -> .dex
 * - [load]   DexClassLoader 加载首类
 */
data class BuildPhaseTimings(
    val init: Long = 0,
    val compile: Long = 0,
    val dex: Long = 0,
    val load: Long = 0,
) {
    val total: Long get() = init + compile + dex + load
}

/**
 * 统计面板 (Stats tab) v2.1.
 *
 * 展示 [BuildStats] 内的数字:
 * - 缓存命中率 (LinearProgressIndicator + 百分比)
 * - 4 个 build phase 的耗时条
 * - ClassLoader pool / cache 状态
 * - 上次 / 累计 compile / render 耗时
 */
@Composable
fun StatsPanel(
    stats: BuildStats,
    modifier: Modifier = Modifier,
) {
    val hitRatePercent = (stats.cacheHitRate * 100).coerceIn(0f, 100f)
    val hitColor = when {
        hitRatePercent >= 80f -> Color(0xFF81C784)
        hitRatePercent >= 50f -> Color(0xFFFFB74D)
        else -> Color(0xFFE57373)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101015)),
    ) {
        // 缓存命中率
        item {
            StatsSection(title = "ClassLoader 缓存命中率") {
                Text(
                    text = String.format("%.1f%%", hitRatePercent),
                    color = hitColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (stats.cacheHitRate).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = hitColor,
                    trackColor = Color(0xFF2A2A35),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${stats.cacheHitCount} 命中 / ${stats.cacheMissCount} 未命中 / 累计 ${stats.loadedClassCount} 次",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // 4 个 build phase
        item {
            StatsSection(title = "Build 阶段耗时 (ms)") {
                PhaseRow(label = "init", ms = stats.phaseTimings.init, total = stats.phaseTimings.total)
                PhaseRow(label = "compile", ms = stats.phaseTimings.compile, total = stats.phaseTimings.total)
                PhaseRow(label = "dex", ms = stats.phaseTimings.dex, total = stats.phaseTimings.total)
                PhaseRow(label = "load", ms = stats.phaseTimings.load, total = stats.phaseTimings.total)
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = Color(0x20FFFFFF))
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "total",
                        color = Color(0xFFE0E0E0),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${stats.phaseTimings.total} ms",
                        color = Color(0xFFE0E0E0),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // ClassLoader pool
        item {
            StatsSection(title = "ClassLoader pool") {
                StatsKeyValue("classCache 大小", "${stats.classCacheSize}")
                StatsKeyValue("DexClassLoader 数量", "${stats.activeLoaderCount}")
            }
        }

        // 编译 / 渲染累计
        item {
            StatsSection(title = "编译 / 渲染累计") {
                StatsKeyValue("上次 compile", "${stats.lastCompileMs} ms")
                StatsKeyValue("累计 compile", "${stats.totalCompileMs} ms")
                StatsKeyValue("上次 render", "${stats.lastRenderMs} ms")
                StatsKeyValue("累计 render", "${stats.totalRenderMs} ms")
            }
        }

        // 说明
        item {
            StatsSection(title = "说明") {
                Text(
                    text = "• 缓存命中率 = cacheHit / loadedClassCount\n" +
                        "• 命中率 <50% 表示冷启动; 持续走低考虑加 LRU 上限\n" +
                        "• 4 个 phase 总和 = 一次完整 build 耗时\n" +
                        "• 累计数据从应用启动开始",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * 一个分区 (标题 + 内容).
 */
@Composable
private fun StatsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
    HorizontalDivider(color = Color(0x10FFFFFF))
}

/**
 * 一个 phase 的进度行.
 */
@Composable
private fun PhaseRow(
    label: String,
    ms: Long,
    total: Long,
) {
    val fraction = if (total <= 0L) 0f else (ms.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val color = when (label) {
        "init" -> Color(0xFF4FC3F7)
        "compile" -> Color(0xFFFFB74D)
        "dex" -> Color(0xFFBA68C8)
        "load" -> Color(0xFF81C784)
        else -> Color.Gray
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.padEnd(8),
            color = color,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(70.dp),
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = Color(0xFF2A2A35),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${ms} ms",
            color = Color(0xFFE0E0E0),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(70.dp),
        )
    }
}

/**
 * 一个 key-value 行.
 */
@Composable
private fun StatsKeyValue(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = key,
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = value,
            color = Color(0xFFE0E0E0),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * `String.padEnd` polyfill (Kotlin stdlib 没有).
 */
private fun String.padEnd(width: Int): String =
    if (length >= width) this else this + " ".repeat(width - length)
