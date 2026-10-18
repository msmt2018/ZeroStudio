package com.zerostudio.ide.treesitter

/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 1. 紧凑型零分配（Zero-Allocation）包装器，直接操作 JNI 下发的原始 IntArray。
 * 2. 避免为成千上万个高亮分词（Token）单独构造 Java/Kotlin 对象，实现 GC 零负担。
 * 3. 采用结构体内存跨步模型（Stride = 4），提供内联的高性能遍历。
 *
 * 内存分布规则：
 * [Index * 4 + 0] : 起始字节偏移 (StartByte)
 * [Index * 4 + 1] : 结束字节偏移 (EndByte)
 * [Index * 4 + 2] : 语义捕获序号 (CaptureId)
 * [Index * 4 + 3] : 规则模式索引 (PatternIndex)
 */
class HighlightSpanArray(val rawData: IntArray) {

    /**
     * 高亮区间总数。
     */
    val spanCount: Int get() = rawData.size / 4

    inline fun getStartByte(index: Int): Int = rawData[index * 4]
    inline fun getEndByte(index: Int): Int = rawData[index * 4 + 1]
    inline fun getCaptureId(index: Int): Int = rawData[index * 4 + 2]
    inline fun getPatternIndex(index: Int): Int = rawData[index * 4 + 3]

    /**
     * 高性能内联遍历高亮项。
     * @param action 接收 (startByte, endByte, captureId, patternIndex) 的执行回调
     */
    inline fun forEachSpan(action: (startByte: Int, endByte: Int, captureId: Int, patternIndex: Int) -> Unit) {
        val count = spanCount
        for (i in 0 until count) {
            val base = i * 4
            action(
                rawData[base],
                rawData[base + 1],
                rawData[base + 2],
                rawData[base + 3]
            )
        }
    }
}