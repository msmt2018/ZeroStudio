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

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * v2.3 P1: 单个 provider 的预加载数据.
 *
 * - [providerInstance]: 通过反射 `providerClass.getDeclaredConstructor().newInstance()` 创建.
 * - [values]: 调用 `providerInstance.getValues()` 一次, 缓存 `Sequence` 物化为 [List] 便于随机访问.
 * - [currentIndex]: 当前选中的 index, 默认 0.
 */
data class PreviewParameterEntry(
    val providerClass: Class<*>,
    val providerInstance: PreviewParameterProvider<*>,
    val values: List<Any?>,
    val currentIndex: Int = 0,
) {
    val size: Int get() = values.size
    fun currentValue(): Any? = values.getOrNull(currentIndex)
}

/**
 * v2.3 P1: `@PreviewParameter` Provider 集中管理.
 *
 * ## 模式
 *
 * ```kotlin
 * val reg = PreviewParameterRegistry
 * reg.register("MyPreviewFunction", "com.example.MyProvider")  // 启动时预加载
 * val entry = reg.get("MyPreviewFunction")
 * val currentValue = entry?.currentValue()
 * reg.setIndex("MyPreviewFunction", index = 2)  // 切换 index → 触发 reRender
 * ```
 *
 * ## 预加载
 *
 * 启动时通过 [register] 一次性 loadAll, 把 [PreviewParameterProvider.getValues] 物化为 List.
 * 切换 index 不再触发反射 / getValues, 仅查 list.
 *
 * ## 多 Provider
 *
 * 同一 function 可有多个 `@PreviewParameter`. 通过 [registerAll] 一次性注册.
 * 每个 provider 独立 [setIndex], 用 providerClassName 区分.
 */
class PreviewParameterRegistry private constructor() {

    companion object {
        private val LOG = LoggerFactory.getLogger(PreviewParameterRegistry::class.java)

        /**
         * v2.3 P1: 全局单例. 与 v2.2 P3 [LiveEditStatsRegistry] / v2.2 P7 [ErrorAggregatorRegistry]
         * 模式一致 — atomic install, lazy get.
         */
        val instance: PreviewParameterRegistry = PreviewParameterRegistry()

        fun get(): PreviewParameterRegistry = instance

        fun install() {
            // 启动时只标记 active, 不做 load. load 由调用方 register 触发.
            instance.clear()
        }

        fun uninstall() {
            instance.clear()
        }
    }

    /** functionName → (providerClassName → [PreviewParameterEntry]). */
    private val store = ConcurrentHashMap<String, ConcurrentHashMap<String, PreviewParameterEntry>>()

    /**
     * 注册一个 provider. 失败 (class 找不到 / 不是 PreviewParameterProvider / getValues 抛) 静默返回 false.
     */
    fun register(functionName: String, providerClassName: String): Boolean {
        val providerClass = runCatching { Class.forName(providerClassName) }
            .onFailure { LOG.warn("Provider class not found: {}", providerClassName) }
            .getOrNull() ?: return false

        val instance = runCatching {
            val ctor = providerClass.getDeclaredConstructor()
            ctor.isAccessible = true
            ctor.newInstance()
        }.onFailure { LOG.warn("Failed to instantiate provider: {}", providerClassName) }
            .getOrNull() ?: return false

        if (instance !is PreviewParameterProvider<*>) {
            LOG.warn("Provider does not implement PreviewParameterProvider: {}", providerClassName)
            return false
        }

        val values: List<Any?> = runCatching {
            instance.values.toList()
        }.onFailure { LOG.warn("getValues() failed for {}", providerClassName) }
            .getOrElse { emptyList() }

        val entry = PreviewParameterEntry(
            providerClass = providerClass,
            providerInstance = instance,
            values = values,
        )
        store.computeIfAbsent(functionName) { ConcurrentHashMap() }[providerClassName] = entry
        LOG.info("Registered provider: {} → {} ({} values)", functionName, providerClassName, values.size)
        return true
    }

    /**
     * 一次注册多个 providers (同一 function 的多个 @PreviewParameter).
     */
    fun registerAll(functionName: String, providerClassNames: List<String>): Int {
        var ok = 0
        providerClassNames.forEach { name -> if (register(functionName, name)) ok++ }
        return ok
    }

    /**
     * 取回 function 的所有 provider entries.
     */
    fun get(functionName: String): Map<String, PreviewParameterEntry> {
        return store[functionName] ?: emptyMap()
    }

    /**
     * 取回 (functionName, providerClassName) 的 entry.
     */
    fun getEntry(functionName: String, providerClassName: String): PreviewParameterEntry? {
        return store[functionName]?.get(providerClassName)
    }

    /**
     * 切换指定 provider 的 index. 返回更新后的 entry, null 表示没找到.
     *
     * 自动 bound check: 0 ≤ index < size. 越界 clamp 到合法范围.
     */
    fun setIndex(functionName: String, providerClassName: String, index: Int): PreviewParameterEntry? {
        val map = store[functionName] ?: return null
        val old = map[providerClassName] ?: return null
        val newIndex = index.coerceIn(0, (old.size - 1).coerceAtLeast(0))
        val updated = old.copy(currentIndex = newIndex)
        map[providerClassName] = updated
        return updated
    }

    /**
     * 清空所有. UI 销毁时调用.
     */
    fun clear() {
        store.clear()
    }

    fun functionCount(): Int = store.size
    fun totalEntryCount(): Int = store.values.sumOf { it.size }

    /**
     * 列出所有已注册 function 名称. 顺序与 ConcurrentHashMap 一致 (不保证).
     */
    fun functionNames(): Set<String> = store.keys.toSet()
}
