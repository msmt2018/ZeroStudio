package com.zerostudio.idea.editor.core.layout

/**
 * 超长单行虚拟断行切片
 */
data class VirtualLineSlice(
    val physicalLine: Int,
    val sliceIndex: Int,
    val startColumn: Int,
    val endColumn: Int,
    val length: Int
)

/**
 * 工业级超长单行防崩切片引擎
 * 拦截超长无换行字符流，防止底层 Skia 文本测量缓冲区溢出
 */
object ZeroLongLineChunker {

    // 单个虚拟分块的最大字符安全阈值（规避 HarfBuzz 塑造和 GPU 纹理限制）
    const val SAFE_LINE_CHUNK_LIMIT = 2048

    /**
     * 判断单行是否属于危险超长行
     */
    fun isDangerousLongLine(lineLength: Int): Boolean {
        return lineLength > SAFE_LINE_CHUNK_LIMIT
    }

    /**
     * 将物理超长行切分为若干个逻辑虚拟行片段
     */
    fun chunkLongLine(physicalLine: Int, lineText: CharSequence): List<VirtualLineSlice> {
        val totalLen = lineText.length
        if (totalLen <= SAFE_LINE_CHUNK_LIMIT) {
            return listOf(VirtualLineSlice(physicalLine, 0, 0, totalLen, totalLen))
        }

        val slices = mutableListOf<VirtualLineSlice>()
        var start = 0
        var sliceIdx = 0

        while (start < totalLen) {
            var end = minOf(start + SAFE_LINE_CHUNK_LIMIT, totalLen)

            // 智能避开标点或空格，若附近有合适断点则尽量在此处切分，保持可读性
            if (end < totalLen) {
                var searchBack = end
                val searchFloor = maxOf(start, end - 128)
                while (searchBack > searchFloor) {
                    val ch = lineText[searchBack]
                    if (ch == ' ' || ch == ',' || ch == ';' || ch == '}' || ch == ']') {
                        end = searchBack + 1
                        break
                    }
                    searchBack--
                }
            }

            slices.add(VirtualLineSlice(physicalLine, sliceIdx++, start, end, end - start))
            start = end
        }

        return slices
    }
}