/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.zerostudio.webpreview.engine

import java.io.File as JvmFile

/**
 * WebView 加载内容类型。
 *
 * 三种模式:
 * - [Url]: 加载远程 URL 或 localhost dev server (http://localhost:3000)
 * - [File]: 加载本地 .html 文件 (file:// scheme)
 * - [Data]: 内联 HTML (loadDataWithBaseURL)
 */
sealed class WebContent {
    /** 远程 URL 或本地 dev server URL。 */
    data class Url(val url: String) : WebContent()

    /** 本地 HTML 文件, 用 file:// scheme 加载。 */
    data class File(val file: JvmFile) : WebContent() {
        /** 转换为 file:// URI 字符串。 */
        fun toFileUri(): String = android.net.Uri.fromFile(file).toString()
    }

    /** 内联 HTML 内容, [baseUrl] 为相对资源解析基准 (可为 null)。 */
    data class Data(val html: String, val baseUrl: String? = null, val mimeType: String = "text/html") : WebContent()
}
