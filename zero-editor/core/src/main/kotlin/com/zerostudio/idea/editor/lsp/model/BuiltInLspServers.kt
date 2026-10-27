package com.zerostudio.idea.editor.lsp.model

import android.content.Context
import com.zerostudio.idea.editor.lsp.connection.LspConnectionConfig
import com.zerostudio.idea.editor.lsp.registry.LspRegistry
import com.zerostudio.idea.editor.lsp.registry.LspServer
import java.io.File

class ClangdLspServer(val binDir: File, val libDir: File) : LspServer() {
    override val id = "clangd"
    override val languageName = "C/C++"
    override val serverName = "Clangd Language Server"
    override val supportedExtensions = listOf("c", "cpp", "cc", "cxx", "h", "hpp")

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.AndroidProcess(
            command = arrayOf("clangd", "--background-index", "--clang-tidy"),
            localBinDir = binDir,
            localLibDir = libDir
        )
    }

    override suspend fun isInstalled(context: Context): Boolean = File(binDir, "clangd").exists()
}

class PyrightLspServer(val nodeModulesDir: File) : LspServer() {
    override val id = "pyright"
    override val languageName = "Python"
    override val serverName = "Pyright Language Server"
    override val supportedExtensions = listOf("py", "pyi")

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(
            command = arrayOf("node", File(nodeModulesDir, "pyright/langserver.index.js").absolutePath, "--stdio")
        )
    }

    override suspend fun isInstalled(context: Context): Boolean =
        File(nodeModulesDir, "pyright/langserver.index.js").exists()
}

class RustAnalyzerLspServer(val binDir: File) : LspServer() {
    override val id = "rust_analyzer"
    override val languageName = "Rust"
    override val serverName = "Rust-Analyzer"
    override val supportedExtensions = listOf("rs")

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(
            command = arrayOf(File(binDir, "rust-analyzer").absolutePath)
        )
    }

    override suspend fun isInstalled(context: Context): Boolean =
        File(binDir, "rust-analyzer").exists()
}

object BuiltInLspManager {
    fun installDefaults(context: Context) {
        val binDir = File(context.filesDir, "bin")
        val libDir = File(context.filesDir, "lib")
        LspRegistry.registerBuiltInServer(ClangdLspServer(binDir, libDir))
        LspRegistry.registerBuiltInServer(RustAnalyzerLspServer(binDir))
        LspRegistry.registerBuiltInServer(PyrightLspServer(File(context.filesDir, "node_modules")))
    }
}