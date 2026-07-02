/*
 *  ZeroStudio IDE - tooling/plugin
 *
 *  子项目 10 - BuildTimeInjector 注入器生成器。
 *
 *  提供 build-time .kt 源文件生成器, 在 host app build 时把 IDE 端配置
 *  (IDE_DEBUGGER_VERSION / LOCAL_SERVER_NAME / HELLO_PROTOCOL_EXTRA_FIELDS
 *  / BUILD_TIMESTAMP_MS) + BreakpointLocation data class + PREHEAT_BREAKPOINTS
 *  + init(application) API 写到 generated-sources/IdeDebuggerBootstrap.kt。
 *
 *  所有函数都是纯函数 (不依赖 Gradle / Project), 便于单测。
 *
 *  依赖 spec: docs/superpowers/specs/2026-07-02-debugger-injection-generator.md
 */

package com.itsaky.androidide.gradle

/**
 * 渲染用的断点描述 (跟生成的 BreakpointLocation 字段一一对应)。
 *
 * 命名 `Rendered` 是为了跟 IDE 端内部的 `BreakpointRequest` 区分 (后者是 IDE
 * 端动态设置 bp 时用的运行时数据结构, 不会注入到 host app)。
 */
data class RenderedBreakpoint(
    val sourceFile: String,
    val line: Int,
    val column: Int = 0,
)

/**
 * Parse Gradle property `ideDebuggerPreheatBreakpoints` 字符串为 bp 列表。
 *
 * 格式: `src=<file>:<line>:<column>;src=<file2>:<line2>:<column2>;...`
 *
 * 限制:
 *   - `<file>` 不能含 `;` 或 `:` (Android source file 一般没有)
 *   - `<line>` / `<column>` 必须是非负整数
 *   - 格式错抛 IllegalArgumentException
 *
 * @param prop Gradle property value (e.g. `project.findProperty("...")`)
 * @return 解析出的 bp 列表 (空列表如果 prop 是 null/空)
 * @throws IllegalArgumentException 如果 entry 格式错
 */
internal fun parsePreheatBreakpoints(prop: String?): List<RenderedBreakpoint> {
    if (prop.isNullOrBlank()) return emptyList()
    val result = mutableListOf<RenderedBreakpoint>()
    for (raw in prop.split(';')) {
        val entry = raw.trim()
        if (entry.isEmpty()) continue
        if (!entry.startsWith("src=")) {
            throw IllegalArgumentException(
                "ideDebuggerPreheatBreakpoints entry must start with 'src=': $entry"
            )
        }
        val body = entry.removePrefix("src=")
        if (body.contains(';')) {
            throw IllegalArgumentException(
                "ideDebuggerPreheatBreakpoints entry file must not contain ';': $body"
            )
        }
        val parts = body.split(':')
        if (parts.size != 3) {
            throw IllegalArgumentException(
                "ideDebuggerPreheatBreakpoints entry must have format 'src=<file>:<line>:<column>': $entry"
            )
        }
        val file = parts[0]
        if (file.isEmpty() || file.contains(':')) {
            throw IllegalArgumentException(
                "ideDebuggerPreheatBreakpoints entry file must be non-empty and not contain ':': $entry"
            )
        }
        val line = parts[1].toIntOrNull()
        if (line == null || line < 0) {
            throw IllegalArgumentException(
                "ideDebuggerPreheatBreakpoints entry line must be a non-negative integer: $entry"
            )
        }
        val column = parts[2].toIntOrNull()
        if (column == null || column < 0) {
            throw IllegalArgumentException(
                "ideDebuggerPreheatBreakpoints entry column must be a non-negative integer: $entry"
            )
        }
        result.add(RenderedBreakpoint(file, line, column))
    }
    return result
}

/**
 * 把字符串转义成 Kotlin 字符串字面量 (e.g. `Foo "Bar"` -> `"Foo \"Bar\""`)。
 *
 * 规则:
 *   - `\` -> `\\`
 *   - `"` -> `\"`
 *   - 换行 `\n` / 回车 `\r` / tab `\t` 走 Kotlin 转义符
 *   - ASCII 0 (NUL) 走 `\\u0000` (Kotlin 字符串字面量支持)
 *   - `$` -> `\$` (避免 Kotlin 字符串插值)
 *   - 其他字符 (含 UTF-8) 保持原样
 *
 * 用法: `"\"${escapeKtStringLiteral(s)}\""` 拼成 Kotlin 字符串字面量。
 */
internal fun escapeKtStringLiteral(s: String): String {
    val sb = StringBuilder(s.length + 16)
    for (c in s) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '\$' -> sb.append("\\$")
            else -> {
                if (c.code < 0x20 || c.code == 0x7F) {
                    sb.append("\\u%04x".format(c.code))
                } else {
                    sb.append(c)
                }
            }
        }
    }
    return sb.toString()
}

/**
 * 渲染 `IdeDebuggerBootstrap.kt` 源文件内容 (完整 .kt 源文件, 不带包名路径)。
 *
 * 4 段结构:
 *   1) IDE 端配置快照常量 (4 个: VERSION / LOCAL_SERVER_NAME / EXTRAS / BUILD_TS)
 *   2) BreakpointLocation data class (line + column)
 *   3) PREHEAT_BREAKPOINTS 列表 (默认空)
 *   4) init(application) 显式 API (幂等)
 *
 * @param ideVersion BuildInfo.VERSION_NAME
 * @param localServerName per-project 唯一 LocalServerSocket 名字
 * @param extras HELLO 协议额外字段 (e.g. `"sdk=33"`)
 * @param buildTimestampMs build 时戳 (System.currentTimeMillis())
 * @param preheatBreakpoints 预热 bp 列表 (空列表 -> emptyList())
 * @return 完整 .kt 源文件文本
 */
internal fun renderIdeDebuggerBootstrapKt(
    ideVersion: String,
    localServerName: String,
    extras: String,
    buildTimestampMs: Long,
    preheatBreakpoints: List<RenderedBreakpoint>,
): String {
    val v = escapeKtStringLiteral(ideVersion)
    val lsn = escapeKtStringLiteral(localServerName)
    val e = escapeKtStringLiteral(extras)
    val preheatLiteral = if (preheatBreakpoints.isEmpty()) {
        "emptyList()"
    } else {
        preheatBreakpoints.joinToString(
            prefix = "listOf(\n        ",
            postfix = ",\n    )",
            separator = ",\n        ",
        ) { bp ->
            "BreakpointLocation(\n" +
                "            sourceFile = \"${escapeKtStringLiteral(bp.sourceFile)}\",\n" +
                "            line = ${bp.line},\n" +
                "            column = ${bp.column},\n" +
                "        )"
        }
    }
    return """
        // AUTO-GENERATED by IdeDebuggerInitScriptPlugin (sub-project 10)
        // DO NOT MODIFY — Regenerated on every build from current IDE debugger version
        package com.itsaky.androidide.zerostudio.ide.debugger.host.generated

        import android.app.Application
        import android.util.Log
        import com.itsaky.androidide.zerostudio.ide.debugger.host.HostAttachAgentBootstrap
        import java.util.concurrent.atomic.AtomicBoolean

        /**
         * 子项目 10: Build-time generated IDE debugger bootstrap API.
         *
         * 与 HostAttachAgentBootstrap (子项目 9c) 互补:
         *   - ContentProvider: host app 启动时早于 Application.onCreate 自动反连
         *   - 本类 init(application): host app 开发者显式调用入口, 幂等
         *
         * IDE 升级时本文件自动重新生成, 保证 host app 编译期拿到最新配置。
         */
        object IdeDebuggerBootstrap {
            private const val TAG = "IdeDebuggerBootstrap"

            // ---- 1) IDE 端配置快照 (build-time 注入, 保证新鲜) ----

            /** IDE debugger version that generated this file (BuildInfo.VERSION_NAME) */
            const val IDE_DEBUGGER_VERSION: String = "$v"

            /** Per-project unique LocalServerSocket name (与 Manifest placeholder 同步) */
            const val LOCAL_SERVER_NAME: String = "$lsn"

            /** HELLO 协议额外字段 (e.g. "sdk=33") */
            const val HELLO_PROTOCOL_EXTRA_FIELDS: String = "$e"

            /** Build 时戳 (用于日志关联, 验证"新鲜度") */
            const val BUILD_TIMESTAMP_MS: Long = $buildTimestampMs

            // ---- 2) bp 位置数据类型 (host app 端类型安全构造 bp) ----

            /**
             * 断点位置: sourceFile + line + column 三元组。
             * column = 0 表示不指定列 (按整行匹配, 兼容老式 IDE)。
             * 反映 IDE 端 bp 系统的"每行每列"语义。
             */
            data class BreakpointLocation(
                val sourceFile: String,
                val line: Int,
                val column: Int = 0,
            )

            // ---- 3) 预热 bp 列表常量 (build-time 注入, 默认空, 留扩展点) ----

            /**
             * IDE 端配置的"预热 bp"列表。在 build-time 注入, host app 启动时自动
             * 装上这些 bp (省去 IDE 端每次重设的开销)。当前默认空数组; IDE 端
             * 后续可扩展 (方案 B 路径)。
             */
            val PREHEAT_BREAKPOINTS: List<BreakpointLocation> = $preheatLiteral

            // ---- 4) 显式 init API (与 ContentProvider 互补) ----

            private val initialized = AtomicBoolean(false)

            /**
             * 显式 init: 与 ContentProvider 互补。多次调用幂等。
             *
             * @param application host app 的 Application 实例
             */
            @JvmStatic
            @Synchronized
            fun init(application: Application) {
                if (initialized.getAndSet(true)) return
                Log.i(TAG, "init: ideDebuggerVersion=${'$'}IDE_DEBUGGER_VERSION " +
                        "localServer=${'$'}LOCAL_SERVER_NAME " +
                        "extras=${'$'}HELLO_PROTOCOL_EXTRA_FIELDS " +
                        "buildTs=${'$'}BUILD_TIMESTAMP_MS " +
                        "preheatBpCount=${'$'}{PREHEAT_BREAKPOINTS.size}")
                HostAttachAgentBootstrap.startReverseConnectThread(application, LOCAL_SERVER_NAME)
            }
        }
    """.trimIndent() + "\n"
}
