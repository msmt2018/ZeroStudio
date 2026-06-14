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

package com.itsaky.androidide.compose.preview.runtime

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * LiveLiteral 编辑器 v2.2 (P0).
 *
 * 负责把 [LiveLiteral] 的 int 编码值翻译成业务值 (Dp/Sp/Color/Int/...) 并回写.
 *
 * ## 用法
 *
 * ```kotlin
 * val editor = LiveLiteralEditor(scanner, recomposeTrigger = { ... })
 * editor.attach(composableClass, functionName)
 * val literals = editor.currentLiterals()
 * literals.find { it.fieldName == "Dp$arg-0$callSite-3" }?.let { lit ->
 *     editor.updateDp(lit, 16.dp)  // 自动编码成 int, 写回
 * }
 * ```
 *
 * ## 编码规则 (与 Compose Compiler 对齐)
 *
 * - [LiveLiteralType.INT]      — 直接 int
 * - [LiveLiteralType.LONG]     — high 32 + low 32, 通常跨 2 个字段
 * - [LiveLiteralType.FLOAT]    — `Float.toRawBits`
 * - [LiveLiteralType.BOOLEAN]  — 0/1
 * - [LiveLiteralType.DP]       — `Dp.value * density`, 这里只用 Dp.value 简化
 * - [LiveLiteralType.SP]       — `TextUnit.value * fontScale * density`, 这里只用 .value
 * - [LiveLiteralType.COLOR]    — ULong packed (ARGB), 跨 2 个字段
 *
 * **注意**: 真实场景下应该根据 Composable 调用点的语义编码, 这里是**最简化**实现.
 *
 * @see LiveLiteralsScanner
 */
class LiveLiteralEditor(
    private val scanner: LiveLiteralsScanner,
    /**
     * 重组触发回调. 改完字面量后, 调用 [recompose] 通知 Composable 重新执行.
     */
    private val recompose: () -> Unit = {},
) {
    private val LOG = LoggerFactory.getLogger(LiveLiteralEditor::class.java)

    private val attached = AtomicReference<AttachInfo?>(null)
    private val listeners = CopyOnWriteArrayList<(List<LiveLiteral>) -> Unit>()

    /**
     * 关联一个 Composable, 扫描其字面量.
     */
    fun attach(composableClass: Class<*>, functionName: String) {
        LOG.info("Attaching LiveLiteralEditor to {}#{}", composableClass.name, functionName)
        val literals = scanner.scanAll(composableClass, functionName)
        attached.set(AttachInfo(composableClass, functionName, literals))
        notify(literals)
    }

    /**
     * 获取当前 Composable 的字面量列表.
     */
    fun currentLiterals(): List<LiveLiteral> = attached.get()?.literals ?: emptyList()

    /**
     * 重新扫描 (例如 LiveLiterals 失效后).
     */
    fun rescan() {
        val info = attached.get() ?: return
        attach(info.composableClass, info.functionName)
    }

    /**
     * 修改一个 Int 字面量.
     */
    fun updateInt(literal: LiveLiteral, value: Int) {
        writeField(literal, value)
    }

    /**
     * 修改一个 Float 字面量.
     */
    fun updateFloat(literal: LiveLiteral, value: Float) {
        writeField(literal, java.lang.Float.floatToRawIntBits(value))
    }

    /**
     * 修改一个 Boolean 字面量.
     */
    fun updateBoolean(literal: LiveLiteral, value: Boolean) {
        writeField(literal, if (value) 1 else 0)
    }

    /**
     * 修改一个 Dp 字面量.
     *
     * 简化: 写为 `Dp.value` 的 int 形式 (调用方需要按其 Composable 实际预期调整).
     */
    fun updateDpValue(literal: LiveLiteral, dpValue: Float) {
        writeField(literal, dpValue.toRawBits().let { bits ->
            // Dp 在 Compose runtime 是 Dp(value: Float) → packed long
            // 这里我们用 bits 简化, 用户界面提示实际效果需要 recompile
            java.lang.Float.floatToRawIntBits(dpValue)
        })
    }

    /**
     * 修改一个 Sp 字面量 (简化, 同 Dp).
     */
    fun updateSpValue(literal: LiveLiteral, spValue: Float) {
        updateDpValue(literal, spValue)
    }

    /**
     * 修改一个 Color 字面量 (ARGB int).
     *
     * 注意: Compose 中 Color 是 ULong, 编译为两个 int 字段.
     * 本函数只更新 first (high 32) 字段, 假设字段已按 `Color$arg-i$callSite-j` 配对.
     */
    fun updateColorArgb(literal: LiveLiteral, argb: Int) {
        writeField(literal, argb)
    }

    /**
     * 写入字面量 + 触发重组.
     */
    private fun writeField(literal: LiveLiteral, encodedValue: Int) {
        val info = attached.get() ?: run {
            LOG.warn("LiveLiteralEditor not attached, dropping write")
            return
        }
        val liveLiteralsClass = scanner.resolveLiveLiteralsClass(
            info.composableClass, info.functionName, groupIndex = 1,
        ) ?: run {
            LOG.warn("LiveLiterals class not resolved, dropping write")
            return
        }
        scanner.setIntValue(literal, liveLiteralsClass, encodedValue)

        // 更新本地缓存
        val updated = info.literals.map {
            if (it.fieldName == literal.fieldName) it.copy(currentEncodedValue = encodedValue) else it
        }
        attached.set(info.copy(literals = updated))
        notify(updated)

        // 触发 Composable 重组
        try {
            recompose()
        } catch (e: Throwable) {
            LOG.warn("recompose trigger failed: {}", e.message)
        }
    }

    /**
     * 注册字面量变更监听器 (DebugDrawer 用).
     */
    fun addListener(listener: (List<LiveLiteral>) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (List<LiveLiteral>) -> Unit) {
        listeners.remove(listener)
    }

    private fun notify(literals: List<LiveLiteral>) {
        for (l in listeners) {
            runCatching { l(literals) }
                .onFailure { LOG.debug("listener failed: {}", it.message) }
        }
    }

    /**
     * 关联状态.
     */
    private data class AttachInfo(
        val composableClass: Class<*>,
        val functionName: String,
        val literals: List<LiveLiteral>,
    )
}
