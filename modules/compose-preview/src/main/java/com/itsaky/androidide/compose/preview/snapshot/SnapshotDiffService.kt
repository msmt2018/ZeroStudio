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

import org.slf4j.LoggerFactory

/**
 * v2.3 P3: 一次 snapshot 校验的输出.
 */
data class SnapshotVerification(
    val functionName: String,
    val profileId: String,
    val hasBaseline: Boolean,
    val diff: ImageDiffResult?,
    /**
     * CI mode (环境变量 CI=true) 时: diff > [CI_DIFF_THRESHOLD] 视为失败
     * Non-CI mode: 仅记录不报错
     */
    val failed: Boolean,
    val note: String = "",
)

/**
 * v2.3 P3: Snapshot / Image Diff 编排服务.
 *
 * 流程:
 * 1. [BaselineStore] 读基线 (无 → 返回 hasBaseline=false, failed=false)
 * 2. [verifySingle] 对比基线与当前 PNG
 * 3. [verifyAll] 批量 (一组 function × profile)
 * 4. CI mode (env CI=true): diff > [CI_DIFF_THRESHOLD] → failed=true → 调用方决定 exit code
 *
 * 此服务不负责"截图", 上层 (ComposableRenderer 端) 调用 PixelCopy 拿 PNG 后传给本服务做 diff.
 */
class SnapshotDiffService(
    private val store: BaselineStore,
    private val pngToRgb: (ByteArray) -> Triple<ByteArray, Int, Int>,
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(SnapshotDiffService::class.java)

        /** CI 模式下 diff% 阈值. 超过 → 失败. */
        const val CI_DIFF_THRESHOLD = 0.05  // 5%

        /**
         * CI mode 检查. env `CI=true` → true.
         */
        fun isCiMode(): Boolean = System.getenv("CI")?.equals("true", ignoreCase = true) == true
    }

    /**
     * 验证单个 snapshot.
     *
     * @param functionName @Preview 函数名
     * @param profileId 设备 profile id
     * @param currentPng 当前截屏 PNG bytes
     * @return [SnapshotVerification]
     */
    fun verifySingle(
        functionName: String,
        profileId: String,
        currentPng: ByteArray,
    ): SnapshotVerification {
        val baseline = store.readBaseline(functionName, profileId)
            ?: return SnapshotVerification(
                functionName = functionName,
                profileId = profileId,
                hasBaseline = false,
                diff = null,
                failed = false,
                note = "no baseline (first run, will write)",
            )

        return compareInternal(functionName, profileId, currentPng, baseline)
    }

    /**
     * 与已加载基线比较 (无基线 → 失败). 不读盘, 适用于循环内的快速重 diff.
     */
    fun compareWithBaseline(
        functionName: String,
        profileId: String,
        currentPng: ByteArray,
        baselinePng: ByteArray,
    ): SnapshotVerification {
        return compareInternal(functionName, profileId, currentPng, baselinePng)
    }

    private fun compareInternal(
        functionName: String,
        profileId: String,
        currentPng: ByteArray,
        baselinePng: ByteArray,
    ): SnapshotVerification {
        val (rgbCur, wCur, hCur) = try {
            pngToRgb(currentPng)
        } catch (e: Throwable) {
            return SnapshotVerification(
                functionName, profileId, hasBaseline = true,
                diff = null, failed = true, note = "decode current PNG failed: ${e.message}",
            )
        }
        val (rgbBase, wBase, hBase) = try {
            pngToRgb(baselinePng)
        } catch (e: Throwable) {
            return SnapshotVerification(
                functionName, profileId, hasBaseline = true,
                diff = null, failed = true, note = "decode baseline PNG failed: ${e.message}",
            )
        }

        val result = ImageDiffer.diff(rgbCur, wCur, hCur, rgbBase, wBase, hBase)
        val failed = isCiMode() && result.diffPercentage > CI_DIFF_THRESHOLD
        val note = if (failed) "CI mode: diff ${"%.4f".format(result.diffPercentage * 100)}% > ${CI_DIFF_THRESHOLD * 100}%"
        else ""
        LOG.info("Verify {}@{}: {}", functionName, profileId, result.toReport())
        return SnapshotVerification(
            functionName, profileId, hasBaseline = true,
            diff = result, failed = failed, note = note,
        )
    }

    /**
     * 接受 (cumulative failures) / (total) 比.
     */
    fun summary(verifications: List<SnapshotVerification>): String {
        val total = verifications.size
        val withBaseline = verifications.count { it.hasBaseline }
        val failed = verifications.count { it.failed }
        val avgDiff = verifications.mapNotNull { it.diff?.diffPercentage }
            .average().let { if (it.isNaN()) 0.0 else it }
        return "Snapshot summary: $total total, $withBaseline with baseline, $failed failed, avg diff ${"%.4f".format(avgDiff * 100)}%"
    }
}
