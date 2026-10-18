package com.zerostudio.ide.treesitter

/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 负责 libzerostudio_treesitter.so 的预加载。
 * 2. 导出引擎核心编译版本号，便于关于页面展示。
 */
object TreeSitter {
    init {
        System.loadLibrary("zerostudio_treesitter")
    }

    @JvmStatic
    external fun nativeGetEngineVersion(): String

    @JvmStatic
    external fun nativeGetLanguageVersion(): Int

    @JvmStatic
    external fun nativeGetMinCompatibleLanguageVersion(): Int

    val engineVersion: String get() = nativeGetEngineVersion()
    val languageVersion: Int get() = nativeGetLanguageVersion()
    val minCompatibleLanguageVersion: Int get() = nativeGetMinCompatibleLanguageVersion()
}