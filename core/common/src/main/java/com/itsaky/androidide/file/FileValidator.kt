package com.itsaky.androidide.file

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * <pre>
 *     author: android_zero
 * </pre>
 */
object FileValidator {

    private const val TAG = "FileValidator"

    // ============================================================================================
    //                                  图片 (Image) 判断
    // ============================================================================================

    /**
     * 判断是否为图片
     * 原理：请求系统解码器读取图片尺寸。如果能读出宽/高，说明是合法的图片。
     */
    fun isImage(file: File?): Boolean {
        if (file == null || !file.exists() || file.length() == 0L) return false

        val options = BitmapFactory.Options()
        // 只解码边界，不加载图片到内存
        options.inJustDecodeBounds = true
        
        try {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            // 某些损坏文件可能导致Native层异常
            return false
        }

        // 如果 outMimeType 不为空 且 宽高大于0，则是有效图片
        return options.outMimeType != null && options.outWidth > 0 && options.outHeight > 0
    }

    // ============================================================================================
    //                                  视频/音频 (Video/Audio) 判断
    // ============================================================================================

    /**
     * 判断是否为视频或音频
     * 原理：使用 Android MediaExtractor 解析媒体轨道。
     * 性能：只分析媒体容器结构（Container Structure），不解码帧。
     *
     * @return 1: Video, 2: Audio, 0: Unknown/Neither
     */
    fun getMediaType(file: File?): Int {
        if (file == null || !file.exists() || file.length() == 0L) return 0

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val numTracks = extractor.trackCount
            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/")) {
                    return 1 // Video
                }
                // 注意：很多视频文件也包含 audio 轨道，一旦发现 video 轨道立即返回 Video
                // 如果循环结束只有 audio 轨道，则返回 Audio
            }
            
            // 如果没有视频轨道，再次检查是否有音频轨道
            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    return 2 // Audio
                }
            }

        } catch (e: IOException) {
            // setDataSource 失败，说明系统无法识别该媒体格式
            return 0
        } catch (e: Exception) {
            return 0
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
        return 0
    }

    fun isVideo(file: File?): Boolean {
        return getMediaType(file) == 1
    }

    fun isAudio(file: File?): Boolean {
        return getMediaType(file) == 2
    }

    // ============================================================================================
    //                                  归档/压缩包 (Archive) 判断
    // ============================================================================================

    /**
     * 判断是否为归档文件 (Zip, Jar, Apk, Docx, etc.)
     * 原理：尝试作为 ZipInputStream 打开并读取第一个 Entry。
     * 读取头部结构。
     */
    fun isArchive(file: File?): Boolean {
        if (file == null || !file.exists() || file.length() < 4) return false // Zip header 至少4字节

        if (!isSignatureZip(file)) return false

        // 进一步验证：尝试读取流结构，防止只是头对了但文件坏了
        var zis: ZipInputStream? = null
        try {
            zis = ZipInputStream(FileInputStream(file))
            // 尝试读取下一个条目，如果成功读取到 Entry，说明是有效的压缩包结构
            val entry = zis.nextEntry
            return entry != null
        } catch (e: Exception) {
            return false
        } finally {
            try {
                zis?.closeEntry()
                zis?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // 是否可能是ZIP
    private fun isSignatureZip(file: File): Boolean {
        var fis: FileInputStream? = null
        try {
            fis = FileInputStream(file)
            val header = ByteArray(4)
            if (fis.read(header) != 4) return false
            // 0x50 4B 03 04 是标准ZIP头 (PK..)
            return header[0] == 0x50.toByte() && 
                   header[1] == 0x4B.toByte() && 
                   header[2] == 0x03.toByte() && 
                   header[3] == 0x04.toByte()
        } catch (e: Exception) {
            return false
        } finally {
            try { fis?.close() } catch (e: IOException) {}
        }
    }
    
    /**
     * 【高精度】判断是否为纯文本文件
     * 原理：读取前段字节，检查是否存在“非法二进制字符”。
     * 纯文本文件（UTF-8/ASCII等）不应包含 NULL 字节 (0x00) 或大量不可见控制字符。
     * 
     * 读取最多 2KB 数据。
     */
    fun isTextFile(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        if (file.length() == 0L) return true

        if (isImage(file)) return false

        var fis: FileInputStream? = null
        try {
            fis = FileInputStream(file)
            val buffer = ByteArray(2048) // 采样 2KB
            val readLen = fis.read(buffer)
            if (readLen < 1) return true

            var binaryCharCount = 0
            val len = readLen
            
            for (i in 0 until len) {
                val b = buffer[i].toInt() and 0xFF
                
                // 判断逻辑：
                // 0x00 (NULL)
                if (b == 0) return false

                // 0x00-0x08 和 0x0E-0x1F 是控制字符 (Control Characters)
                // 但我们要排除常见的空白符：0x09(TAB), 0x0A(LF), 0x0D(CR)
                if ((b < 0x09) || (b in 0x0E..0x1F)) {
                    binaryCharCount++
                }
            }
            if (len > 0 && binaryCharCount.toFloat() / len > 0.3f) {
                return false
            }

            return true
        } catch (e: Exception) {
            return false
        } finally {
            try { fis?.close() } catch (e: IOException) {}
        }
    }
    
    enum class FileCategory {
        IMAGE, VIDEO, AUDIO, ARCHIVE, TEXT, UNKNOWN
    }

    /**
     * 全自动判断文件类别
     */
    fun getCategory(file: File?): FileCategory {
        if (file == null || !file.exists()) return FileCategory.UNKNOWN
        if (isImage(file)) return FileCategory.IMAGE
        if (isArchive(file)) return FileCategory.ARCHIVE
        if (isTextFile(file)) {
             val mediaType = getMediaType(file)
             if (mediaType == 1) return FileCategory.VIDEO
             if (mediaType == 2) return FileCategory.AUDIO
            return FileCategory.TEXT
        }
        val mediaType = getMediaType(file)
        if (mediaType == 1) return FileCategory.VIDEO
        if (mediaType == 2) return FileCategory.AUDIO

        return FileCategory.UNKNOWN
    }
}