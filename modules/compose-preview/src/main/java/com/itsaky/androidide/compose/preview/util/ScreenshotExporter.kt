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

package com.itsaky.androidide.compose.preview.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.View
import android.widget.Toast
import androidx.core.view.drawToBitmap
import com.itsaky.androidide.compose.preview.runtime.PreviewRenderEngine
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 截图导出器 v3.3.
 *
 * 把当前 preview 内容导出为 PNG 文件.
 *
 * 路径:
 * - Android 10+ (Q+): MediaStore Pictures/ComposePreview/
 * - Android < 10: getExternalStoragePublicDirectory(Pictures) + ComposePreview/
 *
 * 文件名: `compose_preview_yyyyMMdd_HHmmss.png`.
 */
object ScreenshotExporter {

    private val LOG = LoggerFactory.getLogger(ScreenshotExporter::class.java)

    /**
     * 截取 [view] 的内容, 保存到 Pictures/ComposePreview, 然后通过 [onResult] 回传.
     *
     * @param onResult (success, 文件 uri 或 null, message)
     */
    fun export(
        context: Context,
        view: View?,
        onResult: (Boolean, Uri?, String) -> Unit,
    ) {
        if (view == null) {
            onResult(false, null, "无预览内容可截图")
            return
        }
        val bitmap = runCatching { view.drawToBitmap() }.getOrElse { e ->
            LOG.error("drawToBitmap failed", e)
            onResult(false, null, "截图失败: ${e.message}")
            return
        }

        val fileName = "compose_preview_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bitmap, fileName, onResult)
            } else {
                saveToPublicPictures(context, bitmap, fileName, onResult)
            }
        } catch (e: Throwable) {
            LOG.error("export failed", e)
            onResult(false, null, "保存失败: ${e.message}")
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        onResult: (Boolean, Uri?, String) -> Unit,
    ) {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ComposePreview")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore insert returned null")
        try {
            resolver.openOutputStream(uri)?.use { os ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            } ?: throw IllegalStateException("openOutputStream returned null")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            LOG.info("Screenshot saved to MediaStore: {}", uri)
            onResult(true, uri, "已保存到 Pictures/ComposePreview/$fileName")
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicPictures(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        onResult: (Boolean, Uri?, String) -> Unit,
    ) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "ComposePreview",
        )
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Failed to create dir: ${dir.absolutePath}")
        }
        val file = File(dir, fileName)
        FileOutputStream(file).use { os ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
        }
        val uri = Uri.fromFile(file)
        LOG.info("Screenshot saved to file: {}", file.absolutePath)
        onResult(true, uri, "已保存到 ${file.absolutePath}")
    }
}
