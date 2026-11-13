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

package android.zero.studio.images.preview

import android.graphics.Bitmap
import android.util.Log
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kotlin wrapper for ThorVG Native Engine. Handles lifecycle of the native context and provides
 * methods to control rendering.
 *
 * @author android_zero
 */
class ThorVG : Closeable {

  private var nativeHandle: Long = 0
  private val isClosed = AtomicBoolean(false)

  // Image Information
  var width: Float = 0f
    private set

  var height: Float = 0f
    private set

  var totalFrames: Float = 0f
    private set

  var duration: Float = 0f
    private set

  val isAnimation: Boolean
    get() = totalFrames > 1.0f

  init {
    nativeHandle = nativeCreate()
  }

  /**
   * Load an image file (SVG, Lottie, PNG, JPG, WebP, etc).
   *
   * @return true if successful.
   */
  fun load(path: String): Boolean {
    check(!isClosed.get()) { "ThorVG context is closed" }
    val success = nativeLoad(nativeHandle, path)
    if (success) {
      val info = nativeGetInfo(nativeHandle)
      if (info != null && info.size >= 4) {
        width = info[0]
        height = info[1]
        totalFrames = info[2]
        duration = info[3]
      }
    }
    return success
  }

  /**
   * Render current state to bitmap.
   *
   * @param bitmap Mutable bitmap (Config.ARGB_8888).
   * @param backgroundColor ARGB color to fill background (0 for transparent).
   */
  fun render(bitmap: Bitmap, backgroundColor: Int = 0): Boolean {
    check(!isClosed.get()) { "ThorVG context is closed" }
    return nativeRender(nativeHandle, bitmap, backgroundColor)
  }

  /**
   * Seek animation to specific progress.
   *
   * @param progress 0.0 ~ 1.0
   */
  fun seek(progress: Float): Boolean {
    check(!isClosed.get()) { "ThorVG context is closed" }
    if (!isAnimation) return false
    return nativeSeek(nativeHandle, progress)
  }

  /**
   * Calculate color histogram (Peak Map) from a Bitmap. Static utility, doesn't require instance
   * handle.
   */
  fun getHistogram(bitmap: Bitmap): IntArray? {
    return nativeGetHistogram(bitmap)
  }

  override fun close() {
    if (isClosed.compareAndSet(false, true)) {
      nativeDestroy(nativeHandle)
      nativeHandle = 0
    }
  }

  protected fun finalize() {
    if (!isClosed.get()) {
      close()
    }
  }

  // --- Native Methods ---
  private external fun nativeCreate(): Long

  private external fun nativeDestroy(handle: Long)

  private external fun nativeLoad(handle: Long, path: String): Boolean

  private external fun nativeRender(handle: Long, bitmap: Bitmap, bgColor: Int): Boolean

  private external fun nativeSeek(handle: Long, progress: Float): Boolean

  private external fun nativeGetInfo(handle: Long): FloatArray? // [w, h, frames, duration]

  // Static util (passed bitmap directly)
  private external fun nativeGetHistogram(bitmap: Bitmap): IntArray?

  companion object {
    init {
      try {
        System.loadLibrary("image-preview")
        nativeInit()
      } catch (e: UnsatisfiedLinkError) {
        Log.e("ThorVG", "Failed to load native library", e)
      }
    }

    @JvmStatic private external fun nativeInit()

    @JvmStatic private external fun nativeTerm()
  }
}
