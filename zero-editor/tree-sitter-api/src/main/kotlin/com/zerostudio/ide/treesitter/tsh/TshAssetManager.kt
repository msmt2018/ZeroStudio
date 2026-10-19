package com.zerostudio.ide.treesitter.tsh

import android.content.Context
import com.zerostudio.ide.treesitter.NativeHighlightEngine
import java.io.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 1. 双模式自动装配：无缝兼容 `assets/tree-sitter/*.tsh` 与 `assets/tree-sitter/scm/*.scm`。
 * 2. 引入 `.tshc` 二进制冷启动缓存机制：首次编译后将 AST 结果序列化落盘到 App 私有 Cache 目录，
 *    下次冷启动直接读取序列化对象，跳过所有词法与语法分析，达到微秒级启动。
 */
object TSAssetManager {
    private const val TS_BASE = "tree-sitter"
    private val memoryCache = ConcurrentHashMap<String, TshCompiledResult>()

    /**
     * 加载 *.tsh 配置（优先读内存 -> 次选读磁盘 .tshc 缓存 -> 最后从资产文本编译）
     */
    fun loadTsh(context: Context, languageName: String): TshCompiledResult {
        return memoryCache.getOrPut(languageName) {
            val cacheFile = File(context.cacheDir, "$languageName.tshc")
            
            // 1. 尝试从本地编译缓存文件还原
            if (cacheFile.exists()) {
                try {
                    ObjectInputStream(FileInputStream(cacheFile)).use { ois ->
                        return@getOrPut ois.readObject() as TshCompiledResult
                    }
                } catch (_: Exception) {
                    cacheFile.delete()
                }
            }

            // 2. 首次编译源文件
            val assetPath = "$TS_BASE/$languageName.tsh"
            val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }

            val lexer = TshLexer(text)
            val tokens = lexer.tokenize()

            val parser = TshParser(tokens)
            val doc = parser.parse() ?: throw IllegalStateException("Parse error in $assetPath: ${parser.errors}")

            val compiler = TshCompiler()
            val result = compiler.compile(doc)

            // 3. 异步写入缓存文件
            try {
                ObjectOutputStream(FileOutputStream(cacheFile)).use { oos ->
                    oos.writeObject(result)
                }
            } catch (_: Exception) {
                // 忽略缓存落盘异常
            }

            result
        }
    }

    /**
     * 读取保留在 assets/tree-sitter/scm/ 中的原始 SCM
     */
    fun loadRawScm(context: Context, languageName: String, queryType: String): String {
        val path = "$TS_BASE/scm/$languageName/$queryType.scm"
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    /**
     * 引擎装配核心：将编译出的 SCM 与样式表下发给 C++ 核心
     */
    fun applyToEngine(context: Context, engine: NativeHighlightEngine, languageName: String) {
        val hasTsh = try {
            context.assets.open("$TS_BASE/$languageName.tsh").close()
            true
        } catch (_: Exception) {
            false
        }

        if (hasTsh) {
            val bundle = loadTsh(context, languageName)
            engine.setQuery(bundle.standardScmQuery)
            engine.setCaptureStyleTable(bundle.flatStyleTable, bundle.captureCount)
        } else {
            // 优雅回退到旧有的 scm 目录
            val rawScm = loadRawScm(context, languageName, "highlights")
            engine.setQuery(rawScm)
        }
    }

    fun clearCache(context: Context) {
        memoryCache.clear()
        context.cacheDir.listFiles { file -> file.name.endsWith(".tshc") }?.forEach { it.delete() }
    }
}