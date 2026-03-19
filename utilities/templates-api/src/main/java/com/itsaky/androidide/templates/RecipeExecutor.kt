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

package com.itsaky.androidide.templates

import androidx.appcompat.app.AlertDialog
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ThreadUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import com.itsaky.androidide.templates.util.TarXzExtractor

/**
 * Handles execution of template recipe.
 *
 * @author Akash Yadav
 * @author android_zero
 */
interface RecipeExecutor {

  /**
   * Get the project template data. This is available only while creating modules in an existing project.
   *
   * @return The project template data or `null` if the not available.
   */
  fun projectData(): ProjectTemplateData? = null

  /**
   * @return The [ProjectTemplateData] if available, throws [IllegalStateException] otherwise.
   * @see projectData
   */
  fun requireProjectData(): ProjectTemplateData = checkNotNull(projectData())

  /**
   * Copy the [source] file to [dest].
   */
  fun copy(source: File, dest: File)

  /**
   * Save the [source] to [dest].
   */
  fun save(source: String, dest: File)

  /**
   * Open the given asset path.
   *
   * @return The [InputStream] for the asset.
   */
  fun openAsset(path: String): InputStream

  /**
   * Copies the asset at the given path to the specified destination.
   *
   * @param path The path of the asset.
   * @param dest The destination path.
   */
  fun copyAsset(path: String, dest: File)

  /**
   * Copies the asset directory path to the specified destination directory.
   *
   * @param path The asset path.
   * @param destDir The destination directory.
   */
  fun copyAssetsRecursively(path: String, destDir: File)

  /**
   * Extract the .tar.xz archive file from the Asset folder to the specified directory.
   * 
   * @param Path to the Asset file
   * @param destDir target unzip directory
   * @param `stripPaths` ignores the first N directory paths within the compressed file (default is 0).
   *
   *Usage demonstration:
   '''kotlin 
   val mainDir = File(data.projectDir, "src/main")
   executor.extractAsset("template/imgui/jni/imgui-NdkSource-Jni.tar.xz", mainDir, stripPaths = 1)
   '''
   */
  fun extractAsset(path: String, destDir: File, stripPaths: Int = 0) {
    openAsset(path).use { inputStream ->
      TarXzExtractor.extract(inputStream, destDir, stripPaths)
    }
  }

  /**
   * Unzip public method with force wait dialog.
   * Prioritize blocking background threads and wait for the UI rendering dialog to complete before performing decompression.
   * Regardless of whether the decompression is successful or failed, the dialog box will be automatically destroyed. If an exception occurs, it will block subsequent project builds (preventing intent jumps).
   *
   * @param path Asset file path
   * @param destDir target unzip directory
   * @param stripPaths to remove the number of layers of the compressed package
   * @param dialogTitle dialog box title
   * @param dialogMessage dialog box message content
   */
  fun extractAssetWithDialog(
      path: String,
      destDir: File,
      stripPaths: Int = 0,
      dialogTitle: String = "Extracting Files",
      dialogMessage: String = "Please wait while files are being extracted..."
  ) {
      val topActivity = ActivityUtils.getTopActivity()
      var dialog: AlertDialog? = null

      // 弹出不可取消的解压对话框
      if (topActivity != null) {
          val latch = CountDownLatch(1)
          ThreadUtils.runOnUiThread {
              dialog = MaterialAlertDialogBuilder(topActivity)
                  .setTitle(dialogTitle)
                  .setMessage(dialogMessage)
                  .setCancelable(false) // 强制显示，不可手动取消
                  .create()
              dialog?.show()
              latch.countDown()
          }
          // 阻塞当前后台构建线程，直到对话框成功显示在屏幕上
          latch.await() 
      }

      // 同步执行解压并捕获异常，确保对话框一定能被安全关闭
      try {
          extractAsset(path, destDir, stripPaths)
      } catch (e: Exception) {
          // 抛出异常会立刻被 IDE 的错误处理器捕获，中断并终止创建流程，阻止 Intent 页面跳转
          throw IllegalStateException("Extraction failed: ${e.message}", e)
      } finally {
          // 无论成功与否，解压结束后都必须关闭对话框
          if (dialog != null) {
              ThreadUtils.runOnUiThread {
                  dialog?.dismiss()
              }
          }
      }
  }
}