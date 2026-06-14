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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Recomposition 计数器.
 *
 * 通过 `currentRecomposeScope` 配合 [LaunchedEffect] 捕获每次 recompose.
 * 用法:
 *
 * ```kotlin
 * val counter = rememberRecompositionCounter()
 * Column(Modifier.recompositionAware(counter)) {
 *     counter.bind()  // 任何 recompose 都会让 counter.tick
 *     MyComposable()
 * }
 * // 上层 UI:
 * Text("Recompositions: ${counter.count}")
 * ```
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
