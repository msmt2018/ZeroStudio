package com.itsaky.androidide.templates.util

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Utility object for extracting `.tar.xz` archives.
 * 
 * @author android_zero
 */
object TarXzExtractor {

    /**
     * 解压 `.tar.xz` 文件到指定目录。
     * 
     * @param inputStream 来自 Assets 或 File 的输入流
     * @param destDir 目标解压目录
     * @param stripPaths 忽略压缩包内前 N 层的目录路径 (默认为0, 保留原结构)
     */
    fun extract(inputStream: InputStream, destDir: File, stripPaths: Int = 0) {
        XZInputStream(inputStream).use { xzIn ->
            TarArchiveInputStream(xzIn).use { tarIn ->
                var entry = tarIn.nextTarEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val entryName = stripPathSegments(entry.name, stripPaths)
                        if (entryName.isNotEmpty()) {
                            val destFile = File(destDir, entryName)
                            // 确保父级目录存在
                            destFile.parentFile?.mkdirs()
                            
                            FileOutputStream(destFile).use { out ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                while (tarIn.read(buffer).also { len = it } != -1) {
                                    out.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                    entry = tarIn.nextTarEntry
                }
            }
        }
    }

    private fun stripPathSegments(path: String, stripCount: Int): String {
        if (stripCount <= 0) return path
        val segments = path.split("/")
        if (segments.size <= stripCount) return ""
        return segments.drop(stripCount).joinToString("/")
    }
}