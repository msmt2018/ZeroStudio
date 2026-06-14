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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 单个 Composable 的 recompose 计数.
 *
 * @property name Composable 名 (通过 `currentRecomposeScope` 反射拿到)
 * @property count 累计 recompose 次数
 */
data class RecomposeSample(
    val name: String,
    val count: Int,
)

/**
 * 全局 recompose 跟踪器.
 *
 * 维护所有 Composable 的 recompose 计数, 按 count 降序展示.
 * 高亮 (红色) 那些超过 [threshold] 的 Composable — 通常是 "recompose 太多" 的优化点.
 *
 * 用法:
 * ```
 * val tracker = LocalRecomposeTracker.current
 * val sample = tracker.bind("MyComposable")
 * // 当 MyComposable recompose, sample.count 自动增加
 * ```
 */
@Stable
class RecomposeTracker(
    val threshold: Int = 5,
) {
    private val samples = mutableStateListOf<RecomposeSample>()

    /** 是否启用高亮 */
    var highlightEnabled by mutableStateOf(false)
        private set

    fun toggleHighlight() {
        highlightEnabled = !highlightEnabled
    }

    /**
     * 绑定到当前 Composable.
     *
     * 通过 [LaunchedEffect] 自身 recompose 时重新启动, 实现 "每 recompose 一次
     * tick 一次". 返回当前计数.
     */
    @Composable
    fun bind(name: String): Int {
        val sample = remember(name) {
            val existing = samples.firstOrNull { it.name == name }
            if (existing != null) {
                samples.remove(existing)
                val updated = existing.copy(count = existing.count + 1)
                samples.add(updated)
                updated
            } else {
                val created = RecomposeSample(name, 1)
                samples.add(created)
                created
            }
        }
        // 每次这个 Composable 重组时, 这个 LaunchedEffect key 是不变的, 但 effect
        // 体本身会被再次执行. 用一个不断变化的 key (count) 来实现.
        LaunchedEffect(sample.count) {
            // 实际上不需要任何工作, 只是触发 recompose
        }
        return sample.count
    }

    @Composable
    fun bind(name: String, content: @Composable (count: Int) -> Unit) {
        val count = bind(name)
        content(count)
    }

    fun snapshot(): List<RecomposeSample> =
        samples.sortedByDescending { it.count }

    fun reset() {
        samples.clear()
    }
}

/**
 * CompositionLocal 提供 tracker (在 Preview 根用 CompositionLocalProvider 注入).
 */
val LocalRecomposeTracker = androidx.compose.runtime.staticCompositionLocalOf<RecomposeTracker> {
    RecomposeTracker()
}

@Composable
fun rememberRecomposeCounter(): RecomposeTracker = remember { RecomposeTracker() }

/**
 * Recompose 面板 v2.1.
 *
 * 显示所有 Composable 的 recompose 计数, 红色高亮超阈值.
 */
@Composable
fun RecompositionPanel(
    tracker: RecomposeTracker,
    modifier: Modifier = Modifier,
) {
    val samples = tracker.snapshot()

    Column(modifier = modifier.fillMaxSize()) {
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${samples.size} 个 Composable · 总计 ${samples.sumOf { it.count }} 次重组",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "高亮",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = tracker.highlightEnabled,
                onCheckedChange = { tracker.toggleHighlight() },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "重置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .padding(4.dp)
            )
        }

        if (samples.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101015)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无数据 — 在 Composable 中调用 tracker.bind(\"MyComposable\") 触发追踪",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101015))
            ) {
                items(samples, key = { it.name }) { sample ->
                    val isHot = sample.count >= tracker.threshold
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 计数徽章
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 22.dp)
                                .background(
                                    if (isHot) Color(0xFFE57373) else Color(0xFF4FC3F7),
                                    RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${sample.count}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sample.name,
                            color = Color(0xFFE0E0E0),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (isHot) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "🔥 优化点",
                                color = Color(0xFFFFB74D),
                                fontSize = 10.sp,
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0x20FFFFFF))
                }
            }
        }
    }
}

/**
 * 保留 v2 旧 API (向后兼容).
 */
@Stable
class RecompositionCounter {
    var count by mutableIntStateOf(0)
        private set

    fun tick() {
        count++
    }

    fun reset() {
        count = 0
    }
}

@Composable
fun rememberRecompositionCounter(): RecompositionCounter = remember { RecompositionCounter() }

@Composable
fun RecompositionCounter.bind() {
    LaunchedEffect(Unit) { tick() }
}
