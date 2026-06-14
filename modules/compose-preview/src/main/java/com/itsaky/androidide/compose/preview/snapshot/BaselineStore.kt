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
import java.io.File

/**
 * v2.3 P3 Preview 视觉基线存储.
 *
 * 路径: `<projectDir>/.androidide/preview-snapshots/<funcName>-<profileId>.png`
 *
 * 与 v2.2 P4 `live-state.json` 同目录 (`.androidide/`), gitignore 默认忽略.
 *
 * ## 用法
 *
 * ```kotlin
 * val store = BaselineStore(projectDir)
 * val baselineFile = store.baselineFor("MyPreview", "pixel-7")
 * if (baselineFile.exists()) {
 *     val baseline = baselineFile.readBytes()
 *     // 与当前快照对比
 * }
 * store.writeBaseline("MyPreview", "pixel-7", pngBytes)
 * ```
 */
class BaselineStore(projectDir: File) {

    companion object {
        private val LOG = LoggerFactory.getLogger(BaselineStore::class.java)
        const val SNAPSHOTS_SUBDIR = "preview-snapshots"
        const val BASELINE_EXT = ".png"
    }

    /**
     * `<projectDir>/.androidide/preview-snapshots/`. 不存在则惰性创建.
     */
    val snapshotsDir: File = File(projectDir, ".androidide/$SNAPSHOTS_SUBDIR").apply {
        if (!exists()) mkdirs()
    }

    /**
     * 计算基线文件路径. 文件名: `<funcName>-<profileId>.png`.
     * 不存在 → 返回 File (不创建).
     */
    fun baselineFor(functionName: String, profileId: String): File {
        return File(snapshotsDir, "$functionName-$profileId$BASELINE_EXT")
    }

    /**
     * 写基线 (覆盖). 静默写入, 失败 → 抛 IOException.
     */
    fun writeBaseline(functionName: String, profileId: String, pngBytes: ByteArray) {
        val target = baselineFor(functionName, profileId)
        // 原子写: tmp + rename
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(pngBytes)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            // rename 失败 → 直接覆写
            target.writeBytes(pngBytes)
            tmp.delete()
        }
        LOG.info("Wrote baseline: {} ({} bytes)", target.absolutePath, pngBytes.size)
    }

    /**
     * 读基线. 不存在 → null.
     */
    fun readBaseline(functionName: String, profileId: String): ByteArray? {
        val f = baselineFor(functionName, profileId)
        return if (f.exists()) f.readBytes() else null
    }

    /**
     * 删除某个基线. 不存在 → no-op.
     */
    fun deleteBaseline(functionName: String, profileId: String) {
        val f = baselineFor(functionName, profileId)
        if (f.exists()) {
            f.delete()
            LOG.info("Deleted baseline: {}", f.absolutePath)
        }
    }

    /**
     * 列出所有基线文件.
     */
    fun listBaselines(): List<File> {
        return snapshotsDir.listFiles { f -> f.isFile && f.name.endsWith(BASELINE_EXT) }
            ?.toList() ?: emptyList()
    }

    fun baselineCount(): Int = listBaselines().size
}
