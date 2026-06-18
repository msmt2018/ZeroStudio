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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Recomposition 计数器 v3.4.
 *
 * 真实计数 — 用 [SideEffect] 在每次成功组合后 (首次 + 每次 recompose) tick.
 * 之前 v3.3 误用 `LaunchedEffect(Unit) { tick() }`, 但 `Unit` 永远不变, 整个
 * LaunchedEffect 只在首次组合时跑一次, 然后永不重启. 也就是说 v3.3 之前
 * RecompositionCounter 永远不会动.
 *
 * v3.4 修法: 用 [SideEffect] 替代 — SideEffect 在每次成功完成组合后 (含首次
 * 与每次 recompose) 都会跑. 失败组合 (failed composition) 不触发. 这跟
 * 官方 recomposition 高亮的行为一致.
 *
 * 不依赖反射 [androidx.compose.runtime.RecomposeScope] 内部字段 (字段名
 * `invalidations` / `invalidationCount` 在不同 Compose 版本会变, 反射不稳).
 * 只用一个简单 `count` 总组合次数. 如果需要"严格 recompose 次数 (排除首次)",
 * 自行在外部用 `counter.count - 1` 估算.
 *
 * 用法:
 *
 * ```kotlin
 * val counter = rememberRecompositionCounter()
 * Column {
 *     counter.bind()  // 任何组合 (首次 + recompose) 都会让 counter.tick
 *     MyComposable()
 * }
 * // 上层 UI:
 * Text("Compositions: ${counter.count}")
 * ```
 *
 * @see androidx.compose.runtime.SideEffect
 */
@Stable
class RecompositionCounter {
    /**
     * 总组合次数 (首次 + 每次 recompose). 用 [mutableIntStateOf] 让外部
     * `Text(count)` 自动 recompose.
     */
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

/**
 * 把当前 Composable 块绑定到 counter, 每次成功组合 (首次 + recompose) 都会
 * 让 counter tick 一次.
 *
 * v3.4 实现: 用 [SideEffect] 在每次成功组合后执行 [RecompositionCounter.tick].
 *
 * 注意: [bind] 自身每次组合都会重新执行函数体, 但函数体里只调 [SideEffect]
 * 注册一个"成功后调"的回调 — 不会立即 tick. SideEffect 不会在 failed
 * composition 后跑, 但 bind 自己的状态读/写也没改任何 state, 所以不会触发
 * 不必要的额外 recompose.
 */
@Composable
fun RecompositionCounter.bind() {
    SideEffect {
        tick()
    }
}
