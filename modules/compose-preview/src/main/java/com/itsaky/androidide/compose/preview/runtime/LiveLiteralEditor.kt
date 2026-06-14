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
 * LiveLiteral 编辑器 v2.2 (P0 + P1).
 *
 * 负责把 [LiveLiteralValue] / [LiveLiteralGroup] 翻译成 int 编码, 写回 LiveLiterals 字段.
 *
 * ## v2.2 P1 升级
 *
 * - 接收 [LiveLiteralEncoder] 注入, 支持 Color / Dp / Sp / Float / Long 完整编码
 * - 接收 density / fontScale 用于 Dp / Sp 编码
 * - 新 API: [updateValue] 接受 [LiveLiteralValue] (类型安全)
 * - 新 API: [attach] 返回 [LiveLiteralGroup] 列表 (配对后), 替代 P0 的 [LiveLiteral] 列表
 * - 保留 [LiveLiteral]-based P0 API 以便向后兼容
 *
 * ## 用法
 *
 * ```kotlin
 * val encoder = LiveLiteralEncoder.fromResources(density = 2.75f, fontScale = 1.0f)
 * val editor = LiveLiteralEditor(scanner, encoder) { composeView.invalidate() }
 * editor.attach(composableClass, functionName)
 *
 * // 列出当前可热替换字面量
 * val groups = editor.currentGroups()
 *
 * // 修改 Color
 * groups.find { it.type == LiveLiteralType.COLOR }?.let { group ->
 *     editor.updateValue(group, LiveLiteralValue.ColorValue(Color.Red))
 * }
 *
 * // 修改 Int
 * groups.find { it.type == LiveLiteralType.INT }?.let { group ->
 *     editor.updateValue(group, LiveLiteralValue.IntValue(42))
 * }
 * ```
 *
 * @see LiveLiteralsScanner
 * @see LiveLiteralEncoder
 */
class LiveLiteralEditor(
    private val scanner: LiveLiteralsScanner,
    /**
     * v2.2 P1 新增: 类型安全编码器.
     */
    private val encoder: LiveLiteralEncoder = LiveLiteralEncoder(),
    /**
     * 重组触发回调. 改完字面量后, 调用 [recompose] 通知 Composable 重新执行.
     */
    private val recompose: () -> Unit = {},
) {
    private val LOG = LoggerFactory.getLogger(LiveLiteralEditor::class.java)

    private val attached = AtomicReference<AttachInfo?>(null)
    private val listeners = CopyOnWriteArrayList<(List<LiveLiteralGroup>) -> Unit>()

    // =============== v2.2 P1: 配对组 (推荐) ===============

    /**
     * 关联 Composable 并扫描配对字面量组.
     */
    fun attach(composableClass: Class<*>, functionName: String, sourceHash: Int = 0) {
        LOG.info("Attaching LiveLiteralEditor to {}#{}", composableClass.name, functionName)
        val groups = scanner.scanAllGroups(composableClass, functionName)
        attached.set(AttachInfo(composableClass, functionName, groups, sourceHash))
        notify(groups)
        // v2.2 P4: 自动恢复持久化的字面量值
        restorePersistedLiterals(groups, sourceHash)
    }

    /**
     * v2.2 P4: 从 [LiveStatePersistenceManager] 读持久化值, 写回 LiveLiterals 静态字段.
     *
     * 由 [attach] 在扫描完成后自动调用. 也可手动调用 (例如热重载时).
     */
    private fun restorePersistedLiterals(groups: List<LiveLiteralGroup>, sourceHash: Int) {
        val mgr = LiveStatePersistenceManager.getActive() ?: return
        val info = attached.get() ?: return
        val liveLiteralsClass = scanner.resolveLiveLiteralsClass(
            info.composableClass, info.functionName, groupIndex = 1,
        ) ?: return

        var restored = 0
        for (group in groups) {
            val persisted = mgr.getLiteral(info.composableClass.name, group.primaryFieldName, sourceHash) ?: continue
            try {
                if (persisted.pairedValue != null) {
                    scanner.setEncodedValueOnGroup(
                        group, liveLiteralsClass,
                        primaryValue = persisted.value,
                        pairedValue = persisted.pairedValue,
                    )
                } else {
                    scanner.setEncodedValueOnGroup(group, liveLiteralsClass, persisted.value)
                }
                restored++
            } catch (e: Throwable) {
                LOG.warn("Failed to restore literal {}: {}", group.primaryFieldName, e.message)
            }
        }
        if (restored > 0) {
            LOG.info("Restored {}/{} persisted literals for {}", restored, groups.size, info.composableClass.name)
        }
    }

    /**
     * 当前 Composable 的字面量组.
     */
    fun currentGroups(): List<LiveLiteralGroup> = attached.get()?.groups ?: emptyList()

    /**
     * 重新扫描 (LiveLiterals 失效后).
     */
    fun rescan() {
        val info = attached.get() ?: return
        attach(info.composableClass, info.functionName)
    }

    /**
     * v2.2 P1: 类型安全更新.
     *
     * 接受 [LiveLiteralValue], 内部用 [encoder] 编码, 写回 (含配对字段).
     */
    fun updateValue(group: LiveLiteralGroup, value: LiveLiteralValue) {
        val encoded = encoder.encode(value)
        writeGroup(group, encoded)

        // v2.2 P4: 持久化
        val mgr = LiveStatePersistenceManager.getActive() ?: return
        val info = attached.get() ?: return
        val (primary, paired) = when (encoded) {
            is EncodedLiteral.Single -> encoded.intValue to null
            is EncodedLiteral.Pair -> encoded.high to encoded.low
        }
        mgr.setLiteral(
            className = info.composableClass.name,
            groupKey = group.primaryFieldName,
            value = primary,
            pairedValue = paired,
            type = value.type.name,
            sourceHash = info.sourceHash,
        )
        mgr.scheduleFlush()
    }

    /**
     * v2.2 P1: 写编码后的字面量.
     */
    private fun writeGroup(group: LiveLiteralGroup, encoded: EncodedLiteral) {
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
        when (encoded) {
            is EncodedLiteral.Single -> {
                scanner.setEncodedValueOnGroup(group, liveLiteralsClass, encoded.intValue)
                updateGroupInList(group, encoded.intValue, pairedValue = null)
            }
            is EncodedLiteral.Pair -> {
                scanner.setEncodedValueOnGroup(
                    group, liveLiteralsClass,
                    primaryValue = encoded.high,
                    pairedValue = encoded.low,
                )
                updateGroupInList(group, encoded.high, encoded.low)
            }
        }

        // 触发 Composable 重组
        try {
            recompose()
        } catch (e: Throwable) {
            LOG.warn("recompose trigger failed: {}", e.message)
        }
    }

    private fun updateGroupInList(
        group: LiveLiteralGroup,
        primaryValue: Int,
        pairedValue: Int?,
    ) {
        val info = attached.get() ?: return
        val updated = info.groups.map {
            if (it.primaryFieldName == group.primaryFieldName) {
                it.copy(
                    primaryEncodedValue = primaryValue,
                    pairedEncodedValue = pairedValue ?: it.pairedEncodedValue,
                )
            } else it
        }
        attached.set(info.copy(groups = updated))
        notify(updated)
    }

    // =============== v2.2 P0 旧 API (保留) ===============

    /**
     * v2.2 P0: 列出原始字面量 (未配对, 仅供向后兼容).
     */
    @Deprecated("Use currentGroups() for v2.2 P1 paired groups", ReplaceWith("currentGroups()"))
    fun currentLiterals(): List<LiveLiteral> {
        // 降级: 把 group 拆成 primary field
        return currentGroups().map { group ->
            LiveLiteral(
                fieldName = group.primaryFieldName,
                type = group.type,
                currentEncodedValue = group.primaryEncodedValue,
            )
        }
    }

    /**
     * v2.2 P0: 修改 Int 字面量.
     */
    @Deprecated("Use updateValue with LiveLiteralValue.IntValue", ReplaceWith("updateValue(group, LiveLiteralValue.IntValue(value))"))
    fun updateInt(literal: LiveLiteral, value: Int) {
        val group = currentGroups().find { it.primaryFieldName == literal.fieldName }
        if (group != null) {
            updateValue(group, LiveLiteralValue.IntValue(value))
        }
    }

    /**
     * v2.2 P0: 修改 Float 字面量.
     */
    @Deprecated("Use updateValue with LiveLiteralValue.FloatValue")
    fun updateFloat(literal: LiveLiteral, value: Float) {
        val group = currentGroups().find { it.primaryFieldName == literal.fieldName }
        if (group != null) {
            updateValue(group, LiveLiteralValue.FloatValue(value))
        }
    }

    /**
     * v2.2 P0: 修改 Boolean 字面量.
     */
    @Deprecated("Use updateValue with LiveLiteralValue.BooleanValue")
    fun updateBoolean(literal: LiveLiteral, value: Boolean) {
        val group = currentGroups().find { it.primaryFieldName == literal.fieldName }
        if (group != null) {
            updateValue(group, LiveLiteralValue.BooleanValue(value))
        }
    }

    /**
     * v2.2 P0: 修改 Dp 字面量 (简化).
     */
    @Deprecated("Use updateValue with LiveLiteralValue.DpValue")
    fun updateDpValue(literal: LiveLiteral, dpValue: Float) {
        val group = currentGroups().find { it.primaryFieldName == literal.fieldName }
        if (group != null) {
            updateValue(group, LiveLiteralValue.DpValue(dpValue))
        }
    }

    /**
     * v2.2 P0: 修改 Sp 字面量 (简化).
     */
    @Deprecated("Use updateValue with LiveLiteralValue.SpValue")
    fun updateSpValue(literal: LiveLiteral, spValue: Float) {
        val group = currentGroups().find { it.primaryFieldName == literal.fieldName }
        if (group != null) {
            updateValue(group, LiveLiteralValue.SpValue(spValue))
        }
    }

    /**
     * v2.2 P0: 修改 Color 字面量 (ARGB int).
     */
    @Deprecated("Use updateValue with LiveLiteralValue.ColorValue")
    fun updateColorArgb(literal: LiveLiteral, argb: Int) {
        val group = currentGroups().find { it.primaryFieldName == literal.fieldName }
        if (group != null) {
            updateValue(group, LiveLiteralValue.ColorValue(androidx.compose.ui.graphics.Color(argb)))
        }
    }

    // =============== Listeners ===============

    fun addListener(listener: (List<LiveLiteralGroup>) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (List<LiveLiteralGroup>) -> Unit) {
        listeners.remove(listener)
    }

    private fun notify(groups: List<LiveLiteralGroup>) {
        for (l in listeners) {
            runCatching { l(groups) }
                .onFailure { LOG.debug("listener failed: {}", it.message) }
        }
    }

    /**
     * 关联状态.
     */
    private data class AttachInfo(
        val composableClass: Class<*>,
        val functionName: String,
        val groups: List<LiveLiteralGroup>,
        val sourceHash: Int = 0,
    )
}
