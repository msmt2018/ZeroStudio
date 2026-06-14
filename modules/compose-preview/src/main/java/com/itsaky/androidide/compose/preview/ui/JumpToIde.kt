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

package com.itsaky.androidide.compose.preview.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.slf4j.LoggerFactory

/**
 * v2.2 P7 错误跳转 IDE 协议.
 *
 * ## URL Scheme
 *
 * ```
 * androidide://open?file=<absolute-path>&line=<int>&column=<int>
 * ```
 *
 * IDE 注册 `<intent-filter>` 接收后打开对应文件并定位. 与 v2.2 P8 / v2.3 / v2.5
 * 跳转协议统一, 避免分散.
 *
 * ## 用法
 *
 * ```kotlin
 * JumpToIde.jumpToFile(context, file = "/path/to/MyKt.kt", line = 42, column = 7)
 * ```
 *
 * 安全: 失败时 (IDE 未注册) 静默返回 false, 不抛.
 */
object JumpToIde {
    private val LOG = LoggerFactory.getLogger(JumpToIde::class.java)

    private const val SCHEME = "androidide"
    private const val HOST = "open"

    /**
     * 构造跳转 URI. 不实际跳转, 给测试用.
     */
    fun buildUri(file: String, line: Int?, column: Int?): Uri {
        val builder = Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("file", file)
        if (line != null) builder.appendQueryParameter("line", line.toString())
        if (column != null) builder.appendQueryParameter("column", column.toString())
        return builder.build()
    }

    /**
     * 跳转到 IDE 打开 file.
     *
     * @return true 启动成功, false 失败 (ActivityNotFound 等)
     */
    fun jumpToFile(
        context: Context,
        file: String,
        line: Int? = null,
        column: Int? = null,
    ): Boolean {
        val uri = buildUri(file, line, column)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            LOG.info("Jumped to IDE: {}", uri)
            true
        } catch (e: ActivityNotFoundException) {
            LOG.warn("No Activity to handle {}: {}", uri, e.message)
            false
        } catch (e: SecurityException) {
            LOG.warn("SecurityException on {}: {}", uri, e.message)
            false
        }
    }
}
