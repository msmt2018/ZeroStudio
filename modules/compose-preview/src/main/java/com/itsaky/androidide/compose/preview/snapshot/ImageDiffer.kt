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

package com.itsaky.androidide.compose.preview.snapshot

/**
 * v2.3 P3: 逐像素 RGB diff 结果.
 *
 * @param width 图片宽
 * @param height 图片高
 * @param totalPixels width × height
 * @param diffPixelCount RGB 任一通道差 > [DIFF_THRESHOLD] 的像素数
 * @param maxChannelDelta 最大单通道差 (0-255)
 * @param diffRegions 简单方框区域 (用于 UI 高亮)
 */
data class ImageDiffResult(
    val width: Int,
    val height: Int,
    val totalPixels: Long,
    val diffPixelCount: Long,
    val maxChannelDelta: Int,
    val diffRegions: List<DiffRegion>,
) {
    val diffPercentage: Double
        get() = if (totalPixels == 0L) 0.0
        else diffPixelCount.toDouble() / totalPixels.toDouble()

    val isClean: Boolean get() = diffPixelCount == 0L

    fun toReport(): String = buildString {
        append("ImageDiff ${width}x${height} diff=${diffPixelCount}/${totalPixels} (")
        append("%.4f".format(diffPercentage * 100))
        append("%) maxDelta=$maxChannelDelta regions=${diffRegions.size}")
    }
}

/**
 * 一个 diff 区域的方框表示.
 */
data class DiffRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Int get() = width * height
}

/**
 * v2.3 P3 逐像素 RGB diff 算法.
 *
 * 比较两个 RGB 字节数组. 字节布局: width*height*3 (R,G,B).
 * 阈值 [DIFF_THRESHOLD] = 30: 任一通道绝对差 > 30 判为 diff 像素.
 * 区域聚合: 相邻 diff 像素合并到 bounding box (简化为: 全图一个 box).
 *
 * 复杂度: O(width × height × channels). 1080×2400 全图 ~7.7M 像素, 50ms 内可完成.
 */
object ImageDiffer {

    /** 单通道差阈值. */
    const val DIFF_THRESHOLD = 30

    /**
     * 比较两幅 RGB 图像. 输入: width*height*3 字节 (R,G,B 交错).
     * 输出: [ImageDiffResult].
     *
     * 尺寸不匹配 → 返回第一张全 diff (按 max(widthA*heightA, widthB*heightB) 算 totalPixels).
     */
    fun diff(
        rgbA: ByteArray, widthA: Int, heightA: Int,
        rgbB: ByteArray, widthB: Int, heightB: Int,
    ): ImageDiffResult {
        if (widthA != widthB || heightA != heightB) {
            // 尺寸不匹配 → 整张判 diff
            val totalA = widthA.toLong() * heightA.toLong()
            return ImageDiffResult(
                width = widthA,
                height = heightA,
                totalPixels = totalA,
                diffPixelCount = totalA,
                maxChannelDelta = 255,
                diffRegions = listOf(DiffRegion(0, 0, widthA, heightA)),
            )
        }

        val width = widthA
        val height = heightA
        val totalPixels = width.toLong() * height.toLong()
        val pixels = width * height

        var diffCount = 0L
        var maxDelta = 0
        // 找 diff 像素的 bounding box (简化: 全部一起)
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        var i = 0
        while (i < pixels) {
            val px = i % width
            val py = i / width
            val byteIdx = i * 3
            val dr = (rgbA[byteIdx].toInt() and 0xff) - (rgbB[byteIdx].toInt() and 0xff)
            val dg = (rgbA[byteIdx + 1].toInt() and 0xff) - (rgbB[byteIdx + 1].toInt() and 0xff)
            val db = (rgbA[byteIdx + 2].toInt() and 0xff) - (rgbB[byteIdx + 2].toInt() and 0xff)

            val adr = if (dr < 0) -dr else dr
            val adg = if (dg < 0) -dg else dg
            val adb = if (db < 0) -db else db

            val maxChan = if (adr > adg) adr else adg
            val maxChan2 = if (maxChan > adb) maxChan else adb

            if (maxChan2 > DIFF_THRESHOLD) {
                diffCount++
                if (maxChan2 > maxDelta) maxDelta = maxChan2
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
            }
            i++
        }

        val regions = if (diffCount == 0L) emptyList()
        else listOf(DiffRegion(minX, minY, maxX + 1, maxY + 1))

        return ImageDiffResult(
            width = width,
            height = height,
            totalPixels = totalPixels,
            diffPixelCount = diffCount,
            maxChannelDelta = maxDelta,
            diffRegions = regions,
        )
    }
}
