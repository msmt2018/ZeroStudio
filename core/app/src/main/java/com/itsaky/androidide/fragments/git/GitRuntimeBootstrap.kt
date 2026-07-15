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

/**
 * 旧版 git native runtime 初始化入口的薄壳。
 *
 * 历史：老实现直接调 [com.catpuppyapp.puppygit.jni.LibLoader.load] +
 * [com.github.git24j.core.Libgit2.init]。
 *
 * 新版（2a1 之后）：委托给 [PuppyGitIntegration.ensureNativeLoaded]，
 * 由它统一管理 native 库的幂等加载。AppModel 字段初始化（DB / settings /
 * paths / navController / scrollBehavior）改在 puppygit Compose 内容
 * 第一次挂载时由 [PuppyGitIntegration.ensureReady] 触发。
 *
 * 保留 `ensureLoaded()` 同步、无参的签名以保持向后兼容。
 *
 * @author android_zero
 */
object GitRuntimeBootstrap {

  @Synchronized
  fun ensureLoaded() {
    PuppyGitIntegration.ensureNativeLoaded()
  }
}
