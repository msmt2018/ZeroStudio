package com.zerostudio.idea.treesitter

/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 1. 紧凑型零分配（Zero-Allocation）包装器，直接操作 JNI 下发的原始 IntArray。
 * 2. 避免为成千上万个高亮分词（Token）单独构造 Java/Kotlin 对象，实现 GC 零负担。
 * 3. 采用结构体内存跨步模型（Stride = 5，扩充支持 Priority 与 ActionId）。
 *
 * 内存分布规则：
 * [Index * 5 + 0] : 起始字节偏移 (StartByte)
 * [Index * 5 + 1] : 结束字节偏移 (EndByte)
 * [Index * 5 + 2] : 压缩样式信息 (PackedStyle)
 * [Index * 5 + 3] : 优先级 (Priority)
 * [Index * 5 + 4] : 交互动作序号 (ActionId)
 */
class HighlightSpanArray(val rawData: IntArray) {

    val spanCount: Int get() = rawData.size / 5

    inline fun getStartByte(index: Int): Int = rawData[index * 5]
    inline fun getEndByte(index: Int): Int = rawData[index * 5 + 1]
    inline fun getPackedStyle(index: Int): Int = rawData[index * 5 + 2]
    inline fun getPriority(index: Int): Int = rawData[index * 5 + 3]
    inline fun getActionId(index: Int): Int = rawData[index * 5 + 4]

    inline fun forEachSpan(action: (startByte: Int, endByte: Int, packedStyle: Int, priority: Int, actionId: Int) -> Unit) {
        val count = spanCount
        for (i in 0 until count) {
            val base = i * 5
            action(
                rawData[base],
                rawData[base + 1],
                rawData[base + 2],
                rawData[base + 3],
                rawData[base + 4]
            )
        }
    }
}