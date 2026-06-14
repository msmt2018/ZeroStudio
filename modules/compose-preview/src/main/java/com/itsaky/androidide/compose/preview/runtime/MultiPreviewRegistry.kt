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
 * Multi-Preview 基础设施 v2.2 (P2).
 *
 * 一次编辑可包含多个 `@Composable` + `@Preview` 标注.
 * v2.1 之前只渲染 [parsedSource.previewConfigs.firstOrNull] (首个).
 * v2.2 P2 起, 维护一组 [PreviewSlot], 全部渲染为 "Gallery" 视图.
 *
 * ## 数据流
 *
 * ```
 * Source 编辑
 *   ↓
 * ComposePreviewRepository 解析 previewConfigs
 *   ↓
 * MultiPreviewRegistry.bind(previews)
 *   ↓
 *   ├─ SinglePreviewHost: 渲染 selectedIndex
 *   └─ GalleryPreviewHost: 渲染全部
 * ```
 *
 * ## 核心类型
 *
 * - [PreviewSlot] — 单个 preview 槽位 (composable + 状态)
 * - [MultiPreviewRegistry] — 全局 holder, 跨组件共享
 * - [PreviewDisplayMode] — SINGLE / GALLERY
 *
 * @see PreviewDisplayMode
 */
object MultiPreviewRegistry {

    private val LOG = LoggerFactory.getLogger(MultiPreviewRegistry::class.java)

    private val slotsRef = AtomicReference<List<PreviewSlot>>(emptyList())
    private val modeRef = AtomicReference(PreviewDisplayMode.SINGLE)
    private val selectedIndexRef = AtomicReference(0)

    private val listeners = CopyOnWriteArrayList<RegistryListener>()

    /**
     * 绑定一组 preview 槽位.
     *
     * 一般由 [com.itsaky.androidide.compose.preview.data.repository.ComposePreviewRepository]
     * 在 parse 完源文件后调用.
     */
    @JvmStatic
    fun bind(newSlots: List<PreviewSlot>) {
        LOG.info("Binding {} preview slots: {}", newSlots.size, newSlots.map { it.functionName })
        slotsRef.set(newSlots)
        // 重置 selectedIndex 到合法范围
        val cur = selectedIndexRef.get()
        if (cur >= newSlots.size) selectedIndexRef.set(0)
        notifyListeners()
    }

    /**
     * 当前所有 preview 槽位.
     */
    @JvmStatic
    fun slots(): List<PreviewSlot> = slotsRef.get()

    /**
     * 当前显示模式.
     */
    @JvmStatic
    fun displayMode(): PreviewDisplayMode = modeRef.get()

    /**
     * 切换显示模式.
     */
    @JvmStatic
    fun setDisplayMode(mode: PreviewDisplayMode) {
        if (modeRef.get() != mode) {
            modeRef.set(mode)
            notifyListeners()
        }
    }

    /**
     * 当前选中的 preview 索引 (SINGLE 模式下使用).
     */
    @JvmStatic
    fun selectedIndex(): Int = selectedIndexRef.get()

    /**
     * 切换选中 preview.
     */
    @JvmStatic
    fun select(index: Int) {
        val list = slotsRef.get()
        if (index in list.indices) {
            selectedIndexRef.set(index)
            notifyListeners()
        }
    }

    /**
     * 当前选中的 slot.
     */
    @JvmStatic
    fun selectedSlot(): PreviewSlot? {
        val list = slotsRef.get()
        val idx = selectedIndexRef.get()
        return list.getOrNull(idx)
    }

    /**
     * 注册监听器.
     */
    @JvmStatic
    fun addListener(listener: RegistryListener) {
        listeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: RegistryListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (l in listeners) {
            runCatching { l.onRegistryChanged() }
                .onFailure { LOG.debug("listener failed: {}", it.message) }
        }
    }

    @JvmStatic
    fun reset() {
        slotsRef.set(emptyList())
        modeRef.set(PreviewDisplayMode.SINGLE)
        selectedIndexRef.set(0)
        listeners.clear()
    }
}

/**
 * Registry 变化监听器.
 */
fun interface RegistryListener {
    fun onRegistryChanged()
}

/**
 * Preview 显示模式.
 */
enum class PreviewDisplayMode(val displayName: String) {
    SINGLE("Single"),
    GALLERY("Gallery");

    companion object {
        fun fromName(name: String?): PreviewDisplayMode =
            entries.firstOrNull { it.name == name } ?: SINGLE
    }
}

/**
 * 单个 preview 槽位.
 *
 * 每个 `@Composable fun ...` + `@Preview` 对应一个 slot.
 *
 * @property index 在源文件中的索引 (0-based)
 * @property functionName Composable 函数名
 * @property composableClass Composable 所在类 (顶层 Kt 文件类)
 * @property widthDp `@Preview(widthDp = ...)` 可选
 * @property heightDp `@Preview(heightDp = ...)` 可选
 * @property previewAnnotation 原始 [com.itsaky.androidide.compose.preview.ComposePreviewViewModel.PreviewConfig]
 * @property visible 用户是否在 Gallery 中显示
 * @property hasError 该 slot 是否渲染失败
 * @property errorMessage 错误信息
 */
data class PreviewSlot(
    val index: Int,
    val functionName: String,
    val composableClass: Class<*>?,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val previewAnnotation: Map<String, String> = emptyMap(),
    val visible: Boolean = true,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
) {
    /**
     * 用于 UI 显示的简短 label.
     */
    val label: String
        get() = buildString {
            append("#").append(index + 1).append(" ")
            append(functionName)
            widthDp?.let { append(" w=").append(it) }
            heightDp?.let { append(" h=").append(it) }
        }
}
