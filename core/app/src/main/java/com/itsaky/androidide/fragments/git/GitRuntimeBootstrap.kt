package com.itsaky.androidide.fragments.git

import com.catpuppyapp.puppygit.jni.LibLoader
import com.github.git24j.core.Libgit2
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 统一初始化 git native runtime，避免直接调用 Repository.open 时出现 jniOpen 未加载。
 */
object GitRuntimeBootstrap {
  private val loaded = AtomicBoolean(false)

  @Synchronized
  fun ensureLoaded() {
    if (loaded.get()) return

    LibLoader.load()
    Libgit2.init()
    loaded.set(true)
  }
}
