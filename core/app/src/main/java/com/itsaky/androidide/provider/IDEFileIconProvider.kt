/*
 * This file is part of ZeroStudio.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */

package com.itsaky.androidide.provider

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.zero.studio.view.filetree.interfaces.FileIconProvider
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.provider.file
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.itsaky.androidide.R
import com.itsaky.androidide.models.FileExtension
import java.io.File

/**
 * 文件图标提供器。
 *
 * @author android_zero
 */
class IDEFileIconProvider(private val context: Context) : FileIconProvider {
  private val navigationIconTint: Int
    get() {
      val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
      return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
        0xFFFFFFFF.toInt()
      } else {
        0xFF1F2937.toInt()
      }
    }

  private fun getTintedNavigationIcon(resId: Int): Drawable? {
    return ContextCompat.getDrawable(context, resId)?.mutate()?.also { drawable ->
      DrawableCompat.setTint(drawable, navigationIconTint)
    }
  }

  override fun getIcon(node: Node<FileObject>): Drawable? {
    val fileObj =
        extractNativeFile(node.value)
            ?: return ContextCompat.getDrawable(context, R.drawable.ic_file_type_unknown)

    val iconRes =
        if (node.value.isDirectory()) {
          FileExtension.Factory.forDirectoryName(node.value.getName()).icon
        } else {
          FileExtension.Factory.forFile(fileObj).icon
        }
    return ContextCompat.getDrawable(context, iconRes)
  }

  override fun getChevronRight(): Drawable? = getTintedNavigationIcon(R.drawable.ic_chevron_right)

  override fun getExpandMore(): Drawable? = getTintedNavigationIcon(R.drawable.ic_chevron_down)

  companion object {
    fun extractNativeFile(fileObj: FileObject): File? {
      // 支持 android.zero.studio.view.filetree 的原生 file 以及 DataTree 里的自定义虚拟类
      if (fileObj is file) return fileObj.getNativeFile()
      if (fileObj is File) return fileObj
      // 通过反射或路径降级提取
      return File(fileObj.getAbsolutePath())
    }
  }
}
