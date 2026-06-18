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

package com.itsaky.androidide.compose.preview.data.source

import org.slf4j.LoggerFactory
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileReader

/**
 * 解析 AndroidManifest.xml, 拿 application android:icon 等关键属性 (PR-C).
 *
 * 用来在桌面 launcher 里展示真实 app icon. 比 gradle model 快 (不依赖 tooling server),
 * 直接扫 `src/main/AndroidManifest.xml` 即可.
 *
 * 用法:
 * ```
 * val info = ManifestIconLoader.load(File(modulePath, "src/main/AndroidManifest.xml"))
 * if (info != null) {
 *     val appIcon = info.applicationIconResName   // e.g. "ic_launcher"
 *     val packageName = info.packageName
 *     val appLabel = info.applicationLabel
 * }
 * ```
 */
object ManifestIconLoader {

    private val LOG = LoggerFactory.getLogger(ManifestIconLoader::class.java)

    /**
     * 解析结果.
     *
     * @param packageName 顶级 package
     * @param applicationIconResName `<application android:icon="@mipmap/ic_launcher"/>` 中的资源名
     *                              (注意: 返回 `ic_launcher`, 不带 `@mipmap/` 前缀).
     * @param applicationLabel application 标签 (来自 `android:label`)
     */
    data class ManifestInfo(
        val packageName: String?,
        val applicationIconResName: String?,
        val applicationLabel: String?,
    )

    /**
     * 加载并解析 [manifestFile]. null = 解析失败.
     */
    fun load(manifestFile: File): ManifestInfo? {
        if (!manifestFile.isFile) {
            LOG.debug("Manifest not found: {}", manifestFile.absolutePath)
            return null
        }

        return runCatching {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            FileReader(manifestFile).use { reader ->
                parser.setInput(reader)
            }

            var packageName: String? = null
            var appIcon: String? = null
            var appLabel: String? = null

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "manifest" -> {
                            packageName = parser.getAttributeValue(null, "package")
                        }
                        "application" -> {
                            // 找 application 的 android:icon (走 namespace)
                            appIcon = parser.getAttributeValue(ANDROID_NS, "icon")
                                ?: parser.getAttributeValue(null, "icon")
                            appLabel = parser.getAttributeValue(ANDROID_NS, "label")
                                ?: parser.getAttributeValue(null, "label")
                            // 只取第一个 application, 跳出
                            break
                        }
                    }
                }
                eventType = parser.next()
            }

            ManifestInfo(
                packageName = packageName,
                applicationIconResName = stripResourcePrefix(appIcon),
                applicationLabel = appLabel,
            )
        }.onFailure { LOG.warn("Failed to parse manifest {}", manifestFile.absolutePath, it) }
            .getOrNull()
    }

    /**
     * 把 `@mipmap/ic_launcher` / `@drawable/foo` / `@android:drawable/...` 转成裸资源名.
     * 带 `@android:` 前缀的返回 null — 因为是系统资源, 不在项目里, 加载不到.
     */
    private fun stripResourcePrefix(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (!value.startsWith("@")) return value
        val body = value.substring(1)
        if (body.startsWith("android:")) return null
        val slash = body.indexOf('/')
        return if (slash >= 0) body.substring(slash + 1) else body
    }

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
}
