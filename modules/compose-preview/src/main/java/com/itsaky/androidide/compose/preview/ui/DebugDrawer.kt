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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.compose.preview.bytecode.BinderStats
import com.itsaky.androidide.compose.preview.bytecode.BinderStatsRegistry
import com.itsaky.androidide.compose.preview.compiler.CompilationCacheHolder
import com.itsaky.androidide.compose.preview.compiler.CompilationCacheStats
import com.itsaky.androidide.compose.preview.compiler.DexCacheHolder
import com.itsaky.androidide.compose.preview.compiler.DexCacheStats
import com.itsaky.androidide.compose.preview.runtime.LiveLiteralEditor
import com.itsaky.androidide.compose.preview.runtime.LiveLiteralEncoder
import com.itsaky.androidide.compose.preview.runtime.LiveLiteralGroup
import com.itsaky.androidide.compose.preview.runtime.LiveLiteralType
import com.itsaky.androidide.compose.preview.runtime.LiveLiteralValue
import com.itsaky.androidide.compose.preview.runtime.LiveLiteralsHolder
import com.itsaky.androidide.compose.preview.runtime.AggregatedError
import com.itsaky.androidide.compose.preview.runtime.ErrorAggregatorRegistry
import com.itsaky.androidide.compose.preview.runtime.ErrorCategory
import com.itsaky.androidide.compose.preview.runtime.ErrorSeverity
import com.itsaky.androidide.compose.preview.runtime.LiveEditCoordinator
import com.itsaky.androidide.compose.preview.runtime.LiveEditState
import com.itsaky.androidide.compose.preview.runtime.LiveEditStatsRegistry
import com.itsaky.androidide.compose.preview.runtime.MultiPreviewRegistry
import com.itsaky.androidide.compose.preview.runtime.PreviewDisplayMode
import com.itsaky.androidide.compose.preview.runtime.PreviewSlot
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                    DebugTab.LiveLiterals -> LiveLiteralsPanel(
                        editor = LiveLiteralsHolder.currentEditor(),
                        modifier = Modifier.fillMaxSize(),
                    )
                    DebugTab.Gallery -> GalleryPanel(modifier = Modifier.fillMaxSize())
                    DebugTab.LiveEdit -> LiveEditPanel(modifier = Modifier.fillMaxSize())
                    DebugTab.Errors -> ErrorsPanel(modifier = Modifier.fillMaxSize())
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

/** 8 个 tab 枚举. */
enum class DebugTab(
    val label: String,
    val icon: ImageVector,
) {
    Inspector("Inspect", Icons.Filled.Layers),
    Recompose("Recomp", Icons.Filled.BugReport),
    Logcat("Log", Icons.Filled.Terminal),
    Stats("Stats", Icons.Filled.Analytics),
    LiveLiterals("LiveLit", Icons.Filled.Tune),
    Gallery("Gallery", Icons.Filled.GridView),
    LiveEdit("LiveEdit", Icons.Filled.Bolt),
    Errors("Errors", Icons.Filled.ErrorOutline),
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
    /**
     * 字节码 binder 统计 (P3 字节码加速).
     *
     * 默认 [BinderStats.EMPTY], 调用方可传入 [BinderStatsRegistry.snapshot] 的结果.
     * 留空时 [StatsPanel] 内部会自行周期性采集.
     */
    val binderStats: BinderStats = BinderStats.EMPTY,
    /**
     * 编译缓存统计 (P4 增量编译).
     *
     * 默认全零, 调用方可传入 [CompilationCacheHolder.statsOrEmpty] 的结果.
     * 留空时 [StatsPanel] 内部会自行周期性采集.
     */
    val compileCacheStats: CompilationCacheStats = CompilationCacheStats(),
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
 * - ClassLoader 缓存命中率 (LinearProgressIndicator + 百分比)
 * - 4 个 build phase 的耗时条
 * - ClassLoader pool / cache 状态
 * - 上次 / 累计 compile / render 耗时
 * - 字节码 binder 统计 (P3) — FieldAccessor / K2 / LayoutNode binder 命中率
 *
 * binder 统计每 [BINDER_STATS_POLL_MS] ms 刷新一次, 反映实时 MethodHandle 命中率.
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

    // P3 字节码 binder 统计: 每 500ms 采集一次
    var binderStats by remember { mutableStateOf(stats.binderStats) }
    // P4 编译缓存统计: 每 500ms 采集一次
    var compileCacheStats by remember { mutableStateOf(stats.compileCacheStats) }
    // P5 dex 缓存统计: 每 500ms 采集一次
    var dexCacheStats by remember { mutableStateOf(stats.dexCacheStats) }
    LaunchedEffect(Unit) {
        while (true) {
            binderStats = BinderStatsRegistry.snapshot()
            compileCacheStats = CompilationCacheHolder.statsOrEmpty()
            dexCacheStats = DexCacheHolder.statsOrEmpty()
            delay(BINDER_STATS_POLL_MS)
        }
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

        // P3 字节码 binder 统计
        item {
            BinderStatsSection(binderStats = binderStats)
        }

        // P4 编译缓存统计
        item {
            CompilationCacheSection(cacheStats = compileCacheStats)
        }

        // P5 dex 缓存统计
        item {
            DexCacheSection(dexStats = dexCacheStats)
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
                        "• 累计数据从应用启动开始\n" +
                        "• P3 binder 命中率应稳定 > 90% (FieldAccessor 缓存命中)\n" +
                        "• P4 编译缓存命中时跳过 K2JVMCompiler, 单次省 1-4s\n" +
                        "• P5 dex 缓存命中时跳过 K2 + D8, 单次省 1.5-5s",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** P3 binder 统计刷新周期 (ms). */
private const val BINDER_STATS_POLL_MS = 500L

/**
 * P3 binder 统计展示区.
 *
 * 内部包含 3 个子区:
 * 1. FieldAccessor 缓存 (singleton)
 * 2. K2StaticBinder (per-classloader)
 * 3. LayoutNodeBinder (per-classloader)
 */
@Composable
private fun BinderStatsSection(binderStats: BinderStats) {
    val hitPercent = (binderStats.fieldAccessorHitRate * 100).coerceIn(0.0, 100.0)
    val hitColor = when {
        hitPercent >= 90.0 -> Color(0xFF81C784)  // 优秀
        hitPercent >= 70.0 -> Color(0xFFFFB74D)  // 可接受
        else -> Color(0xFFE57373)                 // 冷启动或异常
    }

    StatsSection(title = "P3 Binder · 字节码加速") {
        // 头部: 命中率 + 进度条
        Text(
            text = String.format("FieldAccessor 命中率 %.1f%%", hitPercent),
            color = hitColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (binderStats.fieldAccessorHitRate).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = hitColor,
            trackColor = Color(0xFF2A2A35),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${binderStats.fieldAccessorHits} 命中 / ${binderStats.fieldAccessorMisses} 未命中 / 缓存 ${binderStats.fieldAccessorSize} 条",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Color(0x15FFFFFF))
        Spacer(Modifier.height(6.dp))

        // K2StaticBinder
        Text(
            text = "K2StaticBinder",
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(2.dp))
        StatsKeyValue("实例数", "${binderStats.k2BinderCount}")
        StatsKeyValue("累计 exec", "${binderStats.k2CumulativeExecs}")
        StatsKeyValue("累计 newInstance", "${binderStats.k2CumulativeNewInstances}")

        Spacer(Modifier.height(6.dp))

        // LayoutNodeBinder
        Text(
            text = "LayoutNodeBinder",
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(2.dp))
        StatsKeyValue("实例数", "${binderStats.layoutBinderCount}")
        StatsKeyValue("累计绑定字段", "${binderStats.layoutBinderTotalBoundFields}")
    }
}

/**
 * P4 编译缓存统计展示区.
 *
 * 展示:
 * - 命中率 (颜色编码: 绿 ≥70% / 黄 ≥40% / 红 <40%)
 * - 命中 / 未命中 / put / 淘汰 / 过期数
 * - 累计节省编译时间 (ms)
 * - 当前条目数 / 总占用字节
 */
@Composable
private fun CompilationCacheSection(cacheStats: CompilationCacheStats) {
    val hitPercent = (cacheStats.hitRate * 100).coerceIn(0.0, 100.0)
    val hitColor = when {
        hitPercent >= 70.0 -> Color(0xFF81C784)  // 优秀
        hitPercent >= 40.0 -> Color(0xFFFFB74D)  // 可接受
        else -> Color(0xFFE57373)                 // 缓存未生效
    }
    val totalMb = cacheStats.totalSizeBytes / 1024.0 / 1024.0

    StatsSection(title = "P4 CompilationCache · 增量编译") {
        // 头部: 命中率 + 进度条
        Text(
            text = String.format("命中率 %.1f%%", hitPercent),
            color = hitColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (cacheStats.hitRate).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = hitColor,
            trackColor = Color(0xFF2A2A35),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${cacheStats.hits} 命中 / ${cacheStats.misses} 未命中 / put ${cacheStats.puts}",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Color(0x15FFFFFF))
        Spacer(Modifier.height(6.dp))

        StatsKeyValue("累计节省 compile", "${cacheStats.savedCompileMsTotal} ms")
        StatsKeyValue("当前条目数", "${cacheStats.entryCount}")
        StatsKeyValue("总占用", String.format("%.2f MB", totalMb))
        StatsKeyValue("淘汰数", "${cacheStats.evictions}")
        StatsKeyValue("过期清理数", "${cacheStats.expiredRemovals}")
    }
}

/**
 * P5 dex 缓存统计展示区.
 *
 * 与 [CompilationCacheSection] 字段对齐, 展示端到端缓存效果.
 */
@Composable
private fun DexCacheSection(dexStats: DexCacheStats) {
    val hitPercent = (dexStats.hitRate * 100).coerceIn(0.0, 100.0)
    val hitColor = when {
        hitPercent >= 70.0 -> Color(0xFF81C784)
        hitPercent >= 40.0 -> Color(0xFFFFB74D)
        else -> Color(0xFFE57373)
    }
    val totalMb = dexStats.totalSizeBytes / 1024.0 / 1024.0

    StatsSection(title = "P5 DexCache · 端到端缓存") {
        Text(
            text = String.format("命中率 %.1f%%", hitPercent),
            color = hitColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (dexStats.hitRate).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = hitColor,
            trackColor = Color(0xFF2A2A35),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${dexStats.hits} 命中 / ${dexStats.misses} 未命中 / put ${dexStats.puts}",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Color(0x15FFFFFF))
        Spacer(Modifier.height(6.dp))

        StatsKeyValue("累计节省 dex", "${dexStats.savedDexMsTotal} ms")
        StatsKeyValue("当前条目数", "${dexStats.entryCount}")
        StatsKeyValue("总占用", String.format("%.2f MB", totalMb))
        StatsKeyValue("淘汰数", "${dexStats.evictions}")
        StatsKeyValue("过期清理数", "${dexStats.expiredRemovals}")
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

/**
 * Gallery 调试面板 v2.2 (P2).
 *
 * 展示当前源文件的所有 `@Preview` Composable, 支持:
 * - 模式切换 (Single / Gallery)
 * - SINGLE 模式下选 slot
 * - 切换每个 slot 的 visible
 * - 状态统计
 */
@Composable
fun GalleryPanel(
    modifier: Modifier = Modifier,
) {
    var slots by remember { mutableStateOf(MultiPreviewRegistry.slots()) }
    var mode by remember { mutableStateOf(MultiPreviewRegistry.displayMode()) }
    var selectedIndex by remember { mutableStateOf(MultiPreviewRegistry.selectedIndex()) }

    LaunchedEffect(Unit) {
        MultiPreviewRegistry.addListener {
            slots = MultiPreviewRegistry.slots()
            mode = MultiPreviewRegistry.displayMode()
            selectedIndex = MultiPreviewRegistry.selectedIndex()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101015)),
    ) {
        // 模式 + 计数
        item {
            StatsSection(title = "Gallery · 多 Preview 容器") {
                StatsKeyValue("总 preview 数", "${slots.size}")
                StatsKeyValue("可见数", "${slots.count { it.visible }}")
                StatsKeyValue("当前模式", mode.displayName)
                if (mode == PreviewDisplayMode.SINGLE) {
                    StatsKeyValue("选中索引", "$selectedIndex")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "• Single: 渲染选中 1 个 Composable (默认)\n" +
                        "• Gallery: 同时渲染所有 visible 的 Composable\n" +
                        "• 切换 visible: 在 Gallery 模式用于过滤\n" +
                        "• SINGLE/GALLERY 切换由调用方触发",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // 模式切换按钮
        item {
            StatsSection(title = "切换模式") {
                Row {
                    ModePill(
                        text = "Single",
                        selected = mode == PreviewDisplayMode.SINGLE,
                    ) { MultiPreviewRegistry.setDisplayMode(PreviewDisplayMode.SINGLE) }
                    Spacer(Modifier.width(4.dp))
                    ModePill(
                        text = "Gallery",
                        selected = mode == PreviewDisplayMode.GALLERY,
                    ) { MultiPreviewRegistry.setDisplayMode(PreviewDisplayMode.GALLERY) }
                }
            }
        }

        // 单选 slot 列表 (SINGLE 模式下可选)
        if (slots.isEmpty()) {
            item {
                StatsSection(title = "Slot 列表") {
                    Text(
                        text = "(空) 当前源文件没有 @Preview 标注的 Composable",
                        color = Color(0xFFE57373),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        } else {
            item {
                StatsSection(title = "Slot 列表 (${slots.size})") {
                    slots.forEach { slot ->
                        GallerySlotRow(
                            slot = slot,
                            isSelected = slot.index == selectedIndex,
                            mode = mode,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GallerySlotRow(
    slot: PreviewSlot,
    isSelected: Boolean,
    mode: PreviewDisplayMode,
) {
    val bgColor = when {
        isSelected && mode == PreviewDisplayMode.SINGLE -> Color(0xFF3F51B5).copy(alpha = 0.2f)
        !slot.visible -> Color(0xFF1A1A22)
        else -> Color(0xFF1E1E26)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(bgColor)
            .clickable {
                if (mode == PreviewDisplayMode.SINGLE) {
                    MultiPreviewRegistry.select(slot.index)
                }
                // 切换 visible
                val updated = MultiPreviewRegistry.slots().map {
                    if (it.functionName == slot.functionName) it.copy(visible = !it.visible)
                    else it
                }
                MultiPreviewRegistry.bind(updated)
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = "#${slot.index + 1}",
            color = if (isSelected) Color(0xFF80CBC4) else Color(0xFF888888),
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.width(32.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slot.functionName,
                color = Color(0xFFE0E0E0),
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            if (slot.widthDp != null || slot.heightDp != null) {
                Text(
                    text = "w=${slot.widthDp ?: "—"} h=${slot.heightDp ?: "—"}",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
        Text(
            text = if (slot.visible) "ON" else "OFF",
            color = if (slot.visible) Color(0xFFA5D6A7) else Color(0xFF555555),
            fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

@Composable
private fun ModePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) Color(0xFF3F51B5) else Color(0xFF2A2A35))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}

/**
 * LiveLiterals 调试面板 v2.2 (P0 + P1).
 *
 * 展示当前 Composable 的字面量组, 支持:
 * - INT / LONG: 整数输入
 * - FLOAT: 小数输入
 * - BOOLEAN: 开关
 * - COLOR: 颜色块 + ColorPicker (弹窗)
 * - DP / SP: 数值输入 (density 注入)
 *
 * - 配对字段 (LONG / COLOR) 一次写 2 字段
 * - 字段名过长省略前后缀
 * - 无 editor 时显示提示
 */
@Composable
fun LiveLiteralsPanel(
    editor: LiveLiteralEditor?,
    modifier: Modifier = Modifier,
) {
    var groups by remember { mutableStateOf<List<LiveLiteralGroup>>(emptyList()) }

    LaunchedEffect(editor) {
        if (editor == null) {
            groups = emptyList()
            return@LaunchedEffect
        }
        editor.addListener { newGroups -> groups = newGroups }
        groups = editor.currentGroups()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101015)),
    ) {
        if (editor == null) {
            item {
                StatsSection(title = "LiveLiterals · 热替换字面量") {
                    Text(
                        text = "未启用: 请先调用 LiveLiteralsHolder.install(scanner, editor)\n" +
                            "并 attach 到当前 Composable.\n\n" +
                            "前置条件:\n" +
                            "• compose-compiler liveLiterals = true\n" +
                            "• Compose Compiler ≥ 1.5.x",
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            return@LazyColumn
        }

        item {
            StatsSection(title = "LiveLiterals · 热替换字面量 (v2.2 P1)") {
                val stats = LiveLiteralsHolder.statsOrEmpty()
                StatsKeyValue("扫描次数", "${stats.scanCount}")
                StatsKeyValue("缓存命中", "${stats.cacheHits}")
                StatsKeyValue("缓存未命中", "${stats.cacheMisses}")
                StatsKeyValue("写入次数", "${stats.setCount}")
                StatsKeyValue("已缓存类", "${stats.cachedClasses}")
                StatsKeyValue("已缓存字段", "${stats.cachedFields}")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "• v2.2 P1: 字段配对 (LONG/COLOR 跨 2 字段) + 类型安全编码\n" +
                        "• COLOR 字面量支持 ColorPicker\n" +
                        "• Dp/Sp 注入 density 正确编码\n" +
                        "• 改值后自动触发 Composable 重组",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        if (groups.isEmpty()) {
            item {
                StatsSection(title = "字面量组列表") {
                    Text(
                        text = "(空) 当前 Composable 没有可热替换的字面量.\n" +
                            "可能原因:\n" +
                            "• compose-compiler liveLiterals 未开启\n" +
                            "• Composable 内无字面量常量\n" +
                            "• LiveLiterals 类名命名空间不匹配",
                        color = Color(0xFFE57373),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        } else {
            item {
                StatsSection(title = "字面量组 (${groups.size})") {
                    groups.forEach { group ->
                        LiveLiteralGroupRow(
                            group = group,
                            editor = editor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个字面量组行: 类型 / 字段名 / 当前解码值 / 编辑控件.
 */
@Composable
private fun LiveLiteralGroupRow(
    group: LiveLiteralGroup,
    editor: LiveLiteralEditor,
) {
    val typeColor = when (group.type) {
        LiveLiteralType.INT -> Color(0xFF80CBC4)
        LiveLiteralType.LONG -> Color(0xFFB39DDB)
        LiveLiteralType.FLOAT -> Color(0xFFFFCC80)
        LiveLiteralType.BOOLEAN -> Color(0xFFA5D6A7)
        LiveLiteralType.DP -> Color(0xFF90CAF9)
        LiveLiteralType.SP -> Color(0xFF9FA8DA)
        LiveLiteralType.COLOR -> Color(0xFFF48FB1)
        LiveLiteralType.UNKNOWN -> Color(0xFF888888)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = group.type.displayName + if (group.isPaired) " (paired)" else "",
                color = typeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = group.callSiteHash?.let { "#$it" } ?: "—",
                color = Color(0xFF555555),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = group.primaryFieldName + (group.pairedFieldName?.let { " + $it" } ?: ""),
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        LiveLiteralGroupEditor(
            group = group,
            editor = editor,
        )
    }
}

/**
 * 类型特化的编辑控件.
 */
@Composable
private fun LiveLiteralGroupEditor(
    group: LiveLiteralGroup,
    editor: LiveLiteralEditor,
) {
    when (group.type) {
        LiveLiteralType.COLOR -> ColorLiteralRow(group, editor)
        LiveLiteralType.BOOLEAN -> BooleanLiteralRow(group, editor)
        LiveLiteralType.INT, LiveLiteralType.LONG -> IntLiteralRow(group, editor)
        LiveLiteralType.FLOAT -> FloatLiteralRow(group, editor)
        LiveLiteralType.DP -> DpLiteralRow(group, editor)
        LiveLiteralType.SP -> SpLiteralRow(group, editor)
        LiveLiteralType.UNKNOWN -> IntLiteralRow(group, editor)
    }
}

@Composable
private fun IntLiteralRow(group: LiveLiteralGroup, editor: LiveLiteralEditor) {
    val initial = group.primaryEncodedValue
    var text by remember(group.primaryFieldName) { mutableStateOf(initial.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == '-' } },
            label = { Text("Int", color = Color(0xFF80CBC4), fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f).height(48.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = {
                val v = text.toIntOrNull() ?: return@Button
                editor.updateValue(group, LiveLiteralValue.IntValue(v))
            },
            modifier = Modifier.height(40.dp),
        ) { Text("set", fontSize = 11.sp) }
    }
}

@Composable
private fun FloatLiteralRow(group: LiveLiteralGroup, editor: LiveLiteralEditor) {
    val encoder = LiveLiteralEncoder()
    val decoded = encoder.decode(group.type, group.primaryEncodedValue) as? LiveLiteralValue.FloatValue
    var text by remember(group.primaryFieldName) { mutableStateOf(decoded?.value?.toString() ?: "0.0") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
            label = { Text("Float", color = Color(0xFFFFCC80), fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f).height(48.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = {
                val v = text.toFloatOrNull() ?: return@Button
                editor.updateValue(group, LiveLiteralValue.FloatValue(v))
            },
            modifier = Modifier.height(40.dp),
        ) { Text("set", fontSize = 11.sp) }
    }
}

@Composable
private fun BooleanLiteralRow(group: LiveLiteralGroup, editor: LiveLiteralEditor) {
    val current = group.primaryEncodedValue != 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (current) "true" else "false",
            color = Color(0xFFA5D6A7),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = current,
            onCheckedChange = { editor.updateValue(group, LiveLiteralValue.BooleanValue(it)) },
        )
    }
}

@Composable
private fun DpLiteralRow(group: LiveLiteralGroup, editor: LiveLiteralEditor) {
    val encoder = LiveLiteralEncoder()
    val decoded = encoder.decode(group.type, group.primaryEncodedValue) as? LiveLiteralValue.DpValue
    var text by remember(group.primaryFieldName) { mutableStateOf(decoded?.value?.toString() ?: "0.0") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
            label = { Text("Dp", color = Color(0xFF90CAF9), fontSize = 10.sp) },
            suffix = { Text("dp", color = Color(0xFF90CAF9), fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f).height(48.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = {
                val v = text.toFloatOrNull() ?: return@Button
                editor.updateValue(group, LiveLiteralValue.DpValue(v))
            },
            modifier = Modifier.height(40.dp),
        ) { Text("set", fontSize = 11.sp) }
    }
}

@Composable
private fun SpLiteralRow(group: LiveLiteralGroup, editor: LiveLiteralEditor) {
    val encoder = LiveLiteralEncoder()
    val decoded = encoder.decode(group.type, group.primaryEncodedValue) as? LiveLiteralValue.SpValue
    var text by remember(group.primaryFieldName) { mutableStateOf(decoded?.value?.toString() ?: "0.0") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
            label = { Text("Sp", color = Color(0xFF9FA8DA), fontSize = 10.sp) },
            suffix = { Text("sp", color = Color(0xFF9FA8DA), fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f).height(48.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = {
                val v = text.toFloatOrNull() ?: return@Button
                editor.updateValue(group, LiveLiteralValue.SpValue(v))
            },
            modifier = Modifier.height(40.dp),
        ) { Text("set", fontSize = 11.sp) }
    }
}

@Composable
private fun ColorLiteralRow(group: LiveLiteralGroup, editor: LiveLiteralEditor) {
    val encoder = LiveLiteralEncoder()
    val decoded = encoder.decode(
        group.type, group.primaryEncodedValue, group.pairedEncodedValue,
    ) as? LiveLiteralValue.ColorValue
    val currentColor = decoded?.value ?: androidx.compose.ui.graphics.Color.Black
    var showPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(currentColor)
                .clickable { showPicker = true },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "#%08X".format(currentColor.toArgb()),
            color = Color(0xFFF48FB1),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { showPicker = true },
            modifier = Modifier.height(40.dp),
        ) { Text("pick", fontSize = 11.sp) }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = currentColor,
            onDismiss = { showPicker = false },
            onConfirm = { newColor ->
                editor.updateValue(group, LiveLiteralValue.ColorValue(newColor))
                showPicker = false
            },
        )
    }
}

/**
 * 简易 ColorPicker 弹窗 (v2.2 P1): 4 个 Slider 控制 ARGB.
 *
 * 完整版可换成 ColorPicker 库, 这里只做基础控件.
 */
@Composable
private fun ColorPickerDialog(
    initial: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
    onConfirm: (androidx.compose.ui.graphics.Color) -> Unit,
) {
    var a by remember { mutableStateOf((initial.alpha * 255).toInt()) }
    var r by remember { mutableStateOf((initial.red * 255).toInt()) }
    var g by remember { mutableStateOf((initial.green * 255).toInt()) }
    var b by remember { mutableStateOf((initial.blue * 255).toInt()) }
    val current = androidx.compose.ui.graphics.Color(a, r, g, b)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color Picker", color = Color(0xFFF48FB1)) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(current),
                )
                Spacer(Modifier.height(8.dp))
                ChannelSlider("A", a) { a = it }
                ChannelSlider("R", r) { r = it }
                ChannelSlider("G", g) { g = it }
                ChannelSlider("B", b) { b = it }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(current) }) { Text("set") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("cancel") }
        },
    )
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(16.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            color = Color(0xFFE0E0E0),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(36.dp),
        )
    }
}

// =====================================================================
// v2.2 P3 Live Edit Panel
// =====================================================================

/**
 * v2.2 P3 Live Edit 调试面板.
 *
 * 显示 [LiveEditStatsRegistry] 的实时快照:
 * - 累计 reload / error 次数
 * - 上次 reload 耗时 + 滚动平均
 * - 当前 state (Idle / Debouncing / Compiling / Dexing / Swapping / Rendering / Error)
 * - Paused 状态
 *
 * 操作:
 * - **Force Reload**: 立刻触发一次 reload (即使 paused)
 * - **Pause / Resume**: 临时禁用 hot reload (暂停时 source change 事件被忽略)
 * - 状态 pill: 复用 [LiveEditIndicator] 颜色 / 图标
 */
@Composable
fun LiveEditPanel(modifier: Modifier = Modifier) {
    val coordinator = remember { LiveEditCoordinator.getOrCreate() }
    val scope = rememberCoroutineScope()
    // 500ms 拉取一次 stats (与 BinderStatsSection / DexCacheSection 一致)
    var stats by remember { mutableStateOf(LiveEditStatsRegistry.snapshotOrEmpty()) }
    var state by remember { mutableStateOf<LiveEditState>(coordinator.state.value) }

    LaunchedEffect(Unit) {
        while (true) {
            stats = LiveEditStatsRegistry.snapshotOrEmpty()
            state = coordinator.state.value
            delay(500L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 状态卡 (复用 LiveEditStatusCard)
        LiveEditStatusCard(
            state = state,
            paused = stats.paused,
            reloadCount = stats.reloadCount,
            errorCount = stats.errorCount,
            lastReloadMs = stats.lastReloadMs,
            avgReloadMs = stats.avgReloadMs,
            lastError = stats.lastError,
        )

        // 操作行
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = {
                coordinator.setPaused(!stats.paused)
                stats = LiveEditStatsRegistry.snapshotOrEmpty()
            }) {
                Icon(
                    imageVector = if (stats.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (stats.paused) "Resume" else "Pause")
            }

            Button(onClick = {
                // 触发 force reload (沿用最近一次 source)
                scope.launch { coordinator.forceReload() }
            }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Force Reload")
            }
        }

        HorizontalDivider()

        // Stats 表格
        Text(
            text = "Statistics",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LiveEditStatRow("Status", liveEditStateLabel(state, stats.paused))
        LiveEditStatRow("Source hash", "0x%08X".format(stats.lastSourceHash))
        LiveEditStatRow("Last reload ts",
            if (stats.lastReloadTs > 0) formatRelativeTime(stats.lastReloadTs) else "—")
    }
}

@Composable
private fun LiveEditStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun liveEditStateLabel(state: LiveEditState, paused: Boolean): String {
    if (paused) return "Paused"
    return when (state) {
        is LiveEditState.Idle -> "Idle"
        is LiveEditState.Debouncing -> "Debouncing"
        is LiveEditState.Compiling -> "Compiling"
        is LiveEditState.Dexing -> "Dexing"
        is LiveEditState.Swapping -> "Swapping"
        is LiveEditState.Rendering -> "Rendering"
        is LiveEditState.Error -> "Error: ${state.message}"
    }
}

private fun formatRelativeTime(ts: Long): String {
    val deltaMs = System.currentTimeMillis() - ts
    return when {
        deltaMs < 1000 -> "just now"
        deltaMs < 60_000 -> "${deltaMs / 1000}s ago"
        deltaMs < 3_600_000 -> "${deltaMs / 60_000}m ago"
        else -> "${deltaMs / 3_600_000}h ago"
    }
}

// =====================================================================
// v2.2 P7 错误聚合面板
// =====================================================================

/**
 * v2.2 P7 错误聚合 DebugDrawer 面板.
 *
 * 顶部 summary: 总错误数 + 按 category 分组计数.
 * 中部列表: 折叠后的 [AggregatedError] (按 category 升序, lastTs 降序).
 * 每条: 分类 chip + severity + file:line + message + count + 跳转 IDE 按钮.
 *
 * 500ms 拉取一次 [ErrorAggregatorRegistry.snapshotOrEmpty] (与 [LiveEditPanel] 一致).
 */
@Composable
fun ErrorsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var errors by remember { mutableStateOf<List<AggregatedError>>(emptyList()) }
    var summary by remember { mutableStateOf<Map<ErrorCategory, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        while (true) {
            errors = ErrorAggregatorRegistry.snapshotOrEmpty()
            summary = ErrorAggregatorRegistry.summaryOrEmpty()
            delay(500L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Summary
        Text(
            text = "Errors (v2.2 P7)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${errors.size} unique · ${summary.values.sum()} total occurrences",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Category breakdown
        if (summary.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ErrorCategory.values().forEach { cat ->
                    val count = summary[cat] ?: 0
                    if (count > 0) {
                        CategoryChip(category = cat, count = count)
                    }
                }
            }
        }

        // 操作按钮
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ErrorAggregatorRegistry.clear() }) {
                Text("Clear all")
            }
        }

        HorizontalDivider()

        // 错误列表
        if (errors.isEmpty()) {
            Text(
                text = "No errors. ✓",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(errors, key = { "${it.category}:${it.file ?: ""}:${it.line ?: -1}" }) { err ->
                    ErrorRow(error = err, onJump = { file, line, col ->
                        JumpToIde.jumpToFile(context, file, line, col)
                    })
                }
            }
        }
    }
}

/**
 * 单条错误行. 显示 category chip + severity + file:line + msg + count + 跳转按钮.
 */
@Composable
private fun ErrorRow(
    error: AggregatedError,
    onJump: (file: String, line: Int?, col: Int?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryChip(category = error.category, count = error.count)
            Spacer(Modifier.width(6.dp))
            SeverityChip(severity = error.severity)
            Spacer(Modifier.weight(1f))
            Text(
                text = formatRelativeTime(error.lastTs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // file:line
        if (error.file != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fileLabel(error.file, error.line, error.column),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (error.line != null) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = "Jump to IDE",
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                onJump(error.file, error.line, error.column)
                            }
                            .padding(2.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        // message (最多 3 行, 截断)
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
        )
    }
}

private fun fileLabel(file: String, line: Int?, column: Int?): String {
    // 仅显示文件名, 不显示完整路径 (节省空间)
    val name = file.substringAfterLast('/')
    val sb = StringBuilder(name)
    if (line != null) {
        sb.append(":").append(line)
        if (column != null) sb.append(":").append(column)
    }
    return sb.toString()
}

@Composable
private fun CategoryChip(category: ErrorCategory, count: Int) {
    val (label, color) = when (category) {
        ErrorCategory.K2_COMPILE -> "K2" to Color(0xFFEF5350)
        ErrorCategory.D8_DEX -> "D8" to Color(0xFFFFA726)
        ErrorCategory.CLASSLOADER_SWAP -> "SWAP" to Color(0xFFAB47BC)
        ErrorCategory.OTHER -> "?" to Color(0xFF78909C)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "$label×$count",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SeverityChip(severity: ErrorSeverity) {
    val (label, color) = when (severity) {
        ErrorSeverity.ERROR -> "ERROR" to Color(0xFFD32F2F)
        ErrorSeverity.WARNING -> "WARN" to Color(0xFFF9A825)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
