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

import androidx.compose.runtime.Composable
import com.catpuppyapp.puppygit.jni.LibLoader
import com.catpuppyapp.puppygit.utils.AppModel
import com.github.git24j.core.Libgit2
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

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
 * **native 库不可用时的降级**：当 prebuilt native libs 缺失（构建配置跳过 CMake），
 * [LibLoader] 会捕获 `UnsatisfiedLinkError` 并记录失败状态。本类通过
 * [LibLoader.isLoaded] 检测：失败时不调 `Libgit2.init`（那也会抛
 * `UnsatisfiedLinkError`），[nativeLoaded] 保持 `false`，[loadFailure] 记录原因。
 * 上层（[BaseGitPageFragment]）据此显示降级 UI 而非崩溃。
 *
 * @author android_zero
 */
object PuppyGitIntegration {

  private val log = LoggerFactory.getLogger(PuppyGitIntegration::class.java)

  private val nativeLoaded = AtomicBoolean(false)
  private val inited = AtomicBoolean(false)

  /**
   * native 库加载失败时的异常，`null` 表示未尝试加载或加载成功。
   * 上层通过此字段判断是否需要降级。
   */
  @Volatile var loadFailure: Throwable? = null
    private set

  /** AppModel 全部字段就绪时返回 `true`（包括 navController / scrollBehavior）。 */
  fun isReady(): Boolean = inited.get()

  /** native 库（LibLoader + Libgit2）已加载时返回 `true`。 */
  fun isNativeLoaded(): Boolean = nativeLoaded.get()

  /**
   * 同步、非-Composable。仅加载 native 库。
   *
   * 供 [GitRuntimeBootstrap.ensureLoaded] 之类的老入口保持向后兼容。
   * 不触发 [AppModel] 字段初始化。
   *
   * native 库缺失时不抛异常，[nativeLoaded] 保持 `false`，[loadFailure] 记录原因。
   */
  fun ensureNativeLoaded() {
    if (nativeLoaded.get()) return
    synchronized(this) {
      if (nativeLoaded.get()) return

      try {
        LibLoader.load()
        if (!LibLoader.isLoaded()) {
          // LibLoader 逐个加载时捕获了 UnsatisfiedLinkError，记录失败但不抛。
          val failedLib = LibLoader.getFailedLib()
          loadFailure =
              UnsatisfiedLinkError(
                  "puppygit native library '$failedLib' not available; git features disabled")
          log.warn(
              "puppygit native libs not available (first failure: {}). " +
                  "Git features will be disabled. This is expected when prebuilt " +
                  "native libs are not bundled in the build.",
              failedLib)
          return
        }
        Libgit2.init()
        // 关闭 dirs owner 校验，让 libgit2 能访问 /sdcard 等外部存储路径
        Libgit2.optsGitOptSetOwnerValidation(false)
        nativeLoaded.set(true)
      } catch (e: UnsatisfiedLinkError) {
        loadFailure = e
        log.error("Failed to load puppygit native libraries", e)
      } catch (e: Throwable) {
        loadFailure = e
        log.error("Unexpected error loading puppygit native libraries", e)
      }
    }
  }

  /**
   * Composable。首次调用跑 [AppModel.init_forPreview]，后续调用 no-op。
   *
   * 必须在 Composable 作用域内调用（`setContent { ... }` 块、`@Composable`
   * 函数体等）。`init_forPreview` 内部用 `LocalContext.current` 拿 app context，
   * 所以接受 `ctx` 参数没意义—调用栈上一定有 LocalContext。
   *
   * **注意**：native 库未加载时此方法会抛 [IllegalStateException]——调用方
   * （如 [BaseGitPageFragment.setGitContent]）应先检查 [isNativeLoaded]。
   */
  @Composable
  fun ensureReady() {
    if (inited.get()) return
    check(nativeLoaded.get()) {
      "ensureReady() called but native libs are not loaded. " +
          "Caller must check isNativeLoaded() first. Failure: $loadFailure"
    }
    // 触发 AppModel 全部字段初始化（dbContainer / realAppContext /
    // masterPassword / paths / navController / scrollBehavior 等）。
    AppModel.init_forPreview()
    inited.set(true)
  }
}
