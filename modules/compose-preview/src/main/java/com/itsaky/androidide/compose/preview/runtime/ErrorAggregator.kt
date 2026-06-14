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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * v2.2 P7 错误聚合 — 错误分类.
 *
 * - [K2_COMPILE]      K2JVMCompiler 编译错误 (语法/类型/未解析符号)
 * - [D8_DEX]          D8 dex 阶段错误 (class format / method limit / R 类)
 * - [CLASSLOADER_SWAP] ComposeClassLoader.swapProjectDex 失败 (ClassNotFound/NoSuchMethod)
 * - [OTHER]           未识别的错误
 *
 * 通过 [ErrorAggregator.classifyByMessage] 自动嗅探; 显式 [PreviewErrorInfo.category] 优先.
 */
enum class ErrorCategory {
    K2_COMPILE,
    D8_DEX,
    CLASSLOADER_SWAP,
    OTHER;

    companion object {
        /**
         * 根据 error message 字符串嗅探 category. 大小写不敏感.
         *
         * 匹配规则 (按顺序):
         * 1. 含 "dex" / "d8" → [D8_DEX]
         * 2. 含 "swap" / "ClassLoader" / "ClassNotFound" / "NoClassDefFound" / "NoSuchMethod" → [CLASSLOADER_SWAP]
         * 3. 含 "compile" / "K2" / "CompilationException" → [K2_COMPILE]
         * 4. 其他 → [OTHER]
         */
        fun classifyByMessage(message: String?): ErrorCategory {
            val m = message?.lowercase() ?: return OTHER
            return when {
                "dex" in m || "d8" in m -> D8_DEX
                "swap" in m || "classloader" in m ||
                    "classnotfound" in m || "noclassdeffound" in m ||
                    "nosuchmethod" in m -> CLASSLOADER_SWAP
                "compile" in m || "k2" in m || "compilation" in m -> K2_COMPILE
                else -> OTHER
            }
        }
    }
}

/**
 * v2.2 P7 错误严重度. 从 [com.itsaky.androidide.compose.preview.compiler.CompileDiagnostic.Severity] 简化:
 * Preview 阶段只关心 ERROR / WARNING 两种.
 */
enum class ErrorSeverity { ERROR, WARNING }

/**
 * v2.2 P7 结构化错误信息.
 *
 * 由 [LiveEditCallback.classifyError] 产出, 在 [LiveEditCoordinator] 中传给 [ErrorAggregator].
 * 与 [com.itsaky.androidide.compose.preview.compiler.CompileDiagnostic] 字段对齐,
 * 但放在 runtime 包以避免循环依赖.
 */
data class PreviewErrorInfo(
    val category: ErrorCategory,
    val severity: ErrorSeverity,
    val file: String?,
    val line: Int?,
    val column: Int?,
    val message: String,
    val sourceHash: Int,
) {
    companion object {
        /**
         * 工厂: 仅给定 message + sourceHash 时, 用 [ErrorCategory.classifyByMessage] 推断 category.
         */
        fun fromMessage(
            message: String?,
            sourceHash: Int,
            file: String? = null,
            line: Int? = null,
            column: Int? = null,
            severity: ErrorSeverity = ErrorSeverity.ERROR,
        ): PreviewErrorInfo {
            val category = ErrorCategory.classifyByMessage(message)
            return PreviewErrorInfo(
                category = category,
                severity = severity,
                file = file,
                line = line,
                column = column,
                message = message ?: "Unknown error",
                sourceHash = sourceHash,
            )
        }
    }
}

/**
 * v2.2 P7 折叠后的单条错误记录.
 *
 * 同一 (category, file, line) 的多次错误合并为一条, [count] 累加.
 * 排序: 按 (category, lastTs 降序).
 */
data class AggregatedError(
    val category: ErrorCategory,
    val severity: ErrorSeverity,
    val file: String?,
    val line: Int?,
    val column: Int?,
    val message: String,
    val count: Int,
    val firstTs: Long,
    val lastTs: Long,
    val sourceHash: Int,
)

/**
 * v2.2 P7 错误聚合器.
 *
 * 线程安全: 用 [ConcurrentHashMap.compute] 做原子折叠, 无锁读.
 *
 * ## 用法
 *
 * ```kotlin
 * val aggregator = ErrorAggregator()
 * aggregator.add(PreviewErrorInfo.fromMessage("DEX failed: ...", sourceHash = 0x100))
 * aggregator.add(PreviewErrorInfo.fromMessage("DEX failed: ...", sourceHash = 0x101))  // 同 key 折叠, count=2
 * val errors = aggregator.snapshot()  // 按 (category, lastTs 降序)
 * aggregator.clear()
 * ```
 *
 * ## key 设计
 *
 * `"${category}:${file ?: ""}:${line ?: -1}"` —— 同一 file:line 的同分类错误合并.
 * 无 file:line 的错误 (e.g. swap failed) 各自独立.
 */
class ErrorAggregator {

    private val map = ConcurrentHashMap<String, AggregatedError>()
    private val totalAddRef = AtomicLong(0L)
    private val totalErrorRef = AtomicLong(0L)

    /**
     * 添加一条错误. 同一 (category, file, line) 折叠, count++.
     */
    fun add(info: PreviewErrorInfo) {
        val key = "${info.category}:${info.file ?: ""}:${info.line ?: -1}"
        val now = System.currentTimeMillis()
        map.compute(key) { _, prev ->
            if (prev == null) {
                AggregatedError(
                    category = info.category,
                    severity = info.severity,
                    file = info.file,
                    line = info.line,
                    column = info.column,
                    message = info.message,
                    count = 1,
                    firstTs = now,
                    lastTs = now,
                    sourceHash = info.sourceHash,
                )
            } else {
                prev.copy(
                    severity = info.severity, // 用最新
                    column = info.column ?: prev.column,
                    message = info.message, // 用最新
                    count = prev.count + 1,
                    lastTs = now,
                    sourceHash = info.sourceHash,
                )
            }
        }
        totalAddRef.incrementAndGet()
        if (info.severity == ErrorSeverity.ERROR) {
            totalErrorRef.incrementAndGet()
        }
    }

    /**
     * 清空所有聚合错误. (成功 hot-reload 时调用)
     */
    fun clear() {
        map.clear()
        totalAddRef.set(0L)
        totalErrorRef.set(0L)
    }

    /**
     * 拉取当前快照. 按 (category 升序, lastTs 降序) 排序.
     */
    fun snapshot(): List<AggregatedError> {
        return map.values.sortedWith(
            compareBy({ it.category.ordinal }, { -it.lastTs })
        )
    }

    /**
     * 总添加次数 (含 warning, 即使 count 折叠后只有 1 条).
     */
    fun totalAdds(): Long = totalAddRef.get()

    /**
     * 总 ERROR 严重度添加次数.
     */
    fun totalErrors(): Long = totalErrorRef.get()

    /**
     * 按 category 分组统计. 用于 DebugDrawer 顶部 summary.
     */
    fun summaryByCategory(): Map<ErrorCategory, Int> {
        val result = EnumSeverityMap()
        for (e in map.values) {
            result.add(e.category, e.count)
        }
        return result.toMap()
    }
}

/**
 * 内部辅助: 累加 category → count.
 */
private class EnumSeverityMap {
    private val data = EnumMap<ErrorCategory, Int>()
    fun add(cat: ErrorCategory, count: Int) {
        data[cat] = (data[cat] ?: 0) + count
    }
    fun toMap(): Map<ErrorCategory, Int> = data.toMap()
}

/**
 * EnumMap 的轻量替代, 避免引入 java.util.EnumMap 在 Android 性能争议.
 */
private class EnumMap<K : Enum<K>, V> {
    private val backing = HashMap<K, V>()
    operator fun get(key: K): V? = backing[key]
    operator fun set(key: K, value: V) { backing[key] = value }
    fun toMap(): Map<K, V> = backing.toMap()
}

/**
 * v2.2 P7 全局 ErrorAggregator registry.
 *
 * 模式与 v2.2 P3 [LiveEditStatsRegistry] 一致: atomic install + lazy snapshot.
 */
object ErrorAggregatorRegistry {
    private val ref = AtomicReference<ErrorAggregator?>(null)

    fun install(aggregator: ErrorAggregator) {
        ref.set(aggregator)
    }

    fun get(): ErrorAggregator? = ref.get()

    fun snapshotOrEmpty(): List<AggregatedError> =
        ref.get()?.snapshot() ?: emptyList()

    fun summaryOrEmpty(): Map<ErrorCategory, Int> =
        ref.get()?.summaryByCategory() ?: emptyMap()

    fun clear() {
        ref.get()?.clear()
    }

    fun add(info: PreviewErrorInfo) {
        ref.get()?.add(info)
    }
}
