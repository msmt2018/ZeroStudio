package com.zerostudio.idea.treesitter

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * @author android_zero
 *
 * 变更说明：
 * 统一加载 assets/tree-sitter/ 目录下的 .scm 与 .wasm/.so 文件。
 *
 * 流程脑图：
 * Assets -> Read Stream -> String (for .scm) / Extract to Cache (for native SO loading)
 */
object TSAssetLoader {

    private const val TS_BASE_DIR = "tree-sitter"

    fun loadScmQuery(context: Context, languageName: String, scmType: String): String {
        val assetPath = "$TS_BASE_DIR/scm/$languageName/$scmType.scm"
        return context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }

    fun loadWasmBytes(context: Context, wasmFileName: String): ByteArray {
        val assetPath = "$TS_BASE_DIR/wasm/$wasmFileName"
        return context.assets.open(assetPath).use { it.readBytes() }
    }

    /**
     * 将 assets 内部编译出的原生 SO 释放到 app 私有存储后直接加载 TSLanguage。
     */
    fun extractAndLoadLibrary(context: Context, libName: String, funcName: String): TSLanguage {
        val targetFile = File(context.cacheDir, libName)
        if (!targetFile.exists()) {
            context.assets.open("$TS_BASE_DIR/libs/$libName").use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return TSLanguage.load(targetFile.absolutePath, funcName)
    }
}