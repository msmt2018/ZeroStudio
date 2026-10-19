package com.zerostudio.ide.treesitter.tsh

import android.content.Context
import com.zerostudio.ide.treesitter.NativeHighlightEngine
import java.util.concurrent.ConcurrentHashMap

/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 统一资产与引擎管理器：
 * 1. 优先加载 `assets/tree-sitter/*.tsh`，编译并常驻内存（单文件替代所有 SCM + 配色）。
 * 2. 同时完全保留原始 `assets/tree-sitter/scm/*.scm` 加载能力，无缝降级兼容。
 */
object TSAssetManager {
    private const val TS_BASE = "tree-sitter"
    private val tshCache = ConcurrentHashMap<String, TshCompiledResult>()
    private val rawScmCache = ConcurrentHashMap<String, String>()

    fun loadTsh(context: Context, languageName: String): TshCompiledResult {
        return tshCache.getOrPut(languageName) {
            val assetPath = "$TS_BASE/$languageName.tsh"
            val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }

            val lexer = TshLexer(text)
            val tokens = lexer.tokenize()

            val parser = TshParser(tokens)
            val doc = parser.parse() ?: throw IllegalStateException("Parse error in $assetPath: ${parser.errors}")

            val validator = TshValidator()
            val errors = validator.validate(doc)
            if (errors.isNotEmpty()) {
                throw IllegalStateException("TSH validation errors in $assetPath:\n" + errors.joinToString("\n"))
            }

            val compiler = TshCompiler()
            compiler.compile(doc)
        }
    }

    fun loadRawScm(context: Context, languageName: String, queryType: String): String {
        val key = "$languageName/$queryType"
        return rawScmCache.getOrPut(key) {
            val path = "$TS_BASE/scm/$languageName/$queryType.scm"
            context.assets.open(path).bufferedReader().use { it.readText() }
        }
    }

    fun applyToEngine(context: Context, engine: NativeHighlightEngine, languageName: String) {
        val hasTsh = try {
            context.assets.open("$TS_BASE/$languageName.tsh").close()
            true
        } catch (_: Exception) {
            false
        }

        if (hasTsh) {
            val compiled = loadTsh(context, languageName)
            engine.setQuery(compiled.standardScmQuery)
            engine.setCaptureStyleTable(compiled.flatStyleTable, compiled.captureCount)
        } else {
            val scmText = loadRawScm(context, languageName, "highlights")
            engine.setQuery(scmText)
        }
    }

    fun clearCache() {
        tshCache.clear()
        rawScmCache.clear()
    }
}