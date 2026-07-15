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
package com.itsaky.androidide.fragments.git

import android.content.Context
import androidx.compose.runtime.Composable
import com.catpuppyapp.puppygit.jni.LibLoader
import com.catpuppyapp.puppygit.utils.AppModel
import com.github.git24j.core.Libgit2
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 接入 puppygit (`com.catpuppyapp.puppygit.*`) 运行时的唯一入口。
 *
 * 负责两件事：
 *  1. 加载 native 库（[LibLoader] + [Libgit2.init]）— 同步、非-Composable。
 *  2. 初始化 [AppModel] 字段（DB / settings / paths / master password /
 *     navController / scrollBehavior 等）— Composable，因为 puppygit 的
 *     `init_forPreview()` 内部用 `LocalContext.current`。
 *
 * 单例模式 (`object`)，双重检查 + `synchronized` 防并发。多次调用幂等。
 *
 * @author android_zero
 */
object PuppyGitIntegration {

  private val nativeLoaded = AtomicBoolean(false)
  private val inited = AtomicBoolean(false)

  /** AppModel 全部字段就绪时返回 `true`（包括 navController / scrollBehavior）。 */
  fun isReady(): Boolean = inited.get()

  /** native 库（LibLoader + Libgit2）已加载时返回 `true`。 */
  fun isNativeLoaded(): Boolean = nativeLoaded.get()

  /**
   * 同步、非-Composable。仅加载 native 库。
   *
   * 供 [GitRuntimeBootstrap.ensureLoaded] 之类的老入口保持向后兼容。
   * 不触发 [AppModel] 字段初始化。
   */
  fun ensureNativeLoaded() {
    if (nativeLoaded.get()) return
    synchronized(this) {
      if (nativeLoaded.get()) return
      LibLoader.load()
      Libgit2.init()
      // 关闭 dirs owner 校验，让 libgit2 能访问 /sdcard 等外部存储路径
      Libgit2.optsGitOptSetOwnerValidation(false)
      nativeLoaded.set(true)
    }
  }

  /**
   * Composable。首次调用跑 [AppModel.init_forPreview]，后续调用 no-op。
   *
   * 必须在 Composable 作用域内调用（`setContent { ... }` 块、`@Composable`
   * 函数体等）。`init_forPreview` 内部用 `LocalContext.current` 拿 app context，
   * 所以接受 `ctx` 参数没意义—调用栈上一定有 LocalContext。
   */
  @Composable
  fun ensureReady() {
    if (inited.get()) return
    // native 库先加载；init_forPreview 内部也会跑，但幂等且成本极低。
    ensureNativeLoaded()
    // 触发 AppModel 全部字段初始化（dbContainer / realAppContext /
    // masterPassword / paths / navController / scrollBehavior 等）。
    AppModel.init_forPreview()
    inited.set(true)
  }

  /**
   * AndroidIDE 专用 — 非 Composable, 使用真实 app 路径初始化。
   *
   * 与 [ensureReady] 的区别:
   * - 非 Composable, 可在 Fragment.onCreateView 等普通方法中调用
   * - 调用 [AppModel.init_forAndroidIDE] 使用真实 app 目录 (非假路径)
   * - 不初始化 navController/scrollBehavior (由调用方 Composable 设置)
   *
   * 调用后, 调用方应在 Composable 中:
   * ```
   * PuppyGitIntegration.ensureReadyForAndroidIDE(requireContext())
   * // 在 @Composable 中:
   * AppModel.navController = rememberNavController()
   * AppModel.homeTopBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
   * ```
   */
  fun ensureReadyForAndroidIDE(context: Context) {
    if (inited.get()) return
    ensureNativeLoaded()
    AppModel.init_forAndroidIDE(context)
    inited.set(true)
  }
}
