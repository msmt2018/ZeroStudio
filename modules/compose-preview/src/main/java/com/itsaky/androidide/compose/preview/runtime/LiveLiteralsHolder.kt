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

/**
 * LiveLiterals 全局 holder v2.2 (P0).
 *
 * 与 v2.1 P3-P5 的 holder 模式对齐:
 * - [LiveLiteralsScanner] / [LiveLiteralEditor] 由 Repository 注册
 * - DebugDrawer Stats tab 通过 [statsOrEmpty] 拉取运行时数据
 *
 * 单例化原因: 跨多个 ComposeView 共享同一 scanner (避免重复扫描).
 */
object LiveLiteralsHolder {

    private val LOG = LoggerFactory.getLogger(LiveLiteralsHolder::class.java)

    @Volatile
    private var scanner: LiveLiteralsScanner? = null

    @Volatile
    private var editor: LiveLiteralEditor? = null

    @JvmStatic
    fun install(scanner: LiveLiteralsScanner, editor: LiveLiteralEditor) {
        this.scanner = scanner
        this.editor = editor
        LOG.info("LiveLiteralsHolder installed")
    }

    @JvmStatic
    fun currentScanner(): LiveLiteralsScanner? = scanner

    @JvmStatic
    fun currentEditor(): LiveLiteralEditor? = editor

    @JvmStatic
    fun reset() {
        scanner = null
        editor = null
    }

    /**
     * 拉取当前 scanner 统计. 无 scanner 时返回全零.
     */
    @JvmStatic
    fun statsOrEmpty(): LiveLiteralsScanner.ScannerStats =
        scanner?.stats() ?: LiveLiteralsScanner.ScannerStats()
}
