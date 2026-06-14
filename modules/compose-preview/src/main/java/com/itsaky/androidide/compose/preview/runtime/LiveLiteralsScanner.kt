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

package com.itsaky.androidide.compose.preview.runtime

import com.itsaky.androidide.compose.preview.bytecode.FieldAccessor
import com.itsaky.androidide.compose.preview.bytecode.FieldAccessorCache
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * LiveLiterals 扫描器 v2.2 (P0).
 *
 * ## 背景
 *
 * Compose Compiler 1.5+ 开启 `liveLiterals = true` 时, 会为每个 Composable
 * 内的字面量生成 `LiveLiterals$FqName$Function$GroupIndex` 类, 包含若干 `int` 静态字段.
 * 运行时改写这些 int 字段, 即可让 Composable 读到新值, **无需重新编译**.
 *
 * 例如对于:
 * ```kotlin
 * @Composable
 * fun Greeting() {
 *     Text("Hello", color = Color(0xFF6650a4), fontSize = 16.sp)
 * }
 * ```
 *
 * 编译后生成 `LiveLiterals$com.example.GreetingKt$Greeting$1` 类, 包含:
 * - `Int$arg-0$callSite-1` (fontSize.sp 的 encoded value)
 * - `Int$arg-1$callSite-2` (color 的 0xFF6650a4 packed long 的 high int)
 * - `Int$arg-2$callSite-2` (color 的 0xFF6650a4 packed long 的 low int)
 *
 * ## 职责
 *
 * 1. 扫描 Composable 对应的 `LiveLiterals$*` 类
 * 2. 列出所有可热替换的字段 (类型 + 当前值)
 * 3. 暴露 [setIntValue] / [reset] API
 *
 * ## 注意
 *
 * - **依赖**: `compose-compiler` 必须开 `liveLiterals = true`
 * - **范围**: 仅处理 int / long (其它类型 compose 编译器不生成)
 * - **触发 recomposition**: 改完值后需调用方主动触发
 *
 * @see LiveLiteralEditor
 */
class LiveLiteralsScanner(
    private val classLoader: ClassLoader,
) {
    private val LOG = LoggerFactory.getLogger(LiveLiteralsScanner::class.java)

    /**
     * LiveLiterals 类缓存: (宿主 Composable 类, group index) -> LiveLiterals 类.
     *
     * 例: `("com.example.GreetingKt", "Greeting", 1)` -> `LiveLiterals$com.example.GreetingKt$Greeting$1`
     */
    private val cache = ConcurrentHashMap<LiveLiteralClassKey, Class<*>?>()

    /**
     * 字段访问器缓存: (LiveLiterals 类, 字段名) -> FieldAccessor.
     *
     * 复用 P3 [FieldAccessorCache] 基础设施, 避免重复反射.
     */
    private val fieldCache = ConcurrentHashMap<String, FieldAccessor>()

    private val scanCount = AtomicLong(0)
    private val hitCount = AtomicLong(0)
    private val missCount = AtomicLong(0)
    private val setCount = AtomicLong(0)

    /**
     * 扫描 Composable 关联的 [groupIndex] 个 LiveLiterals 类的所有字面量.
     *
     * @param composableClass Composable 所在类 (通常是顶层文件类, 如 `FooKt`)
     * @param functionName Composable 函数名
     * @param groupIndex LiveLiterals group index (通常从 1 开始, 0 表示非分组)
     * @return 字面量列表, 按字段名排序
     */
    fun scan(
        composableClass: Class<*>,
        functionName: String,
        groupIndex: Int = 1,
    ): List<LiveLiteral> {
        val clazz = resolveLiveLiteralsClass(composableClass, functionName, groupIndex)
            ?: return emptyList()
        return scanClass(clazz)
    }

    /**
     * 扫描所有可能的 LiveLiterals 类 (group index 1..max).
     *
     * @param composableClass Composable 所在类
     * @param functionName Composable 函数名
     * @param maxGroup 最多尝试多少个 group (默认 8, 足够覆盖常见情况)
     */
    fun scanAll(
        composableClass: Class<*>,
        functionName: String,
        maxGroup: Int = 8,
    ): List<LiveLiteral> {
        val out = ArrayList<LiveLiteral>()
        for (g in 1..maxGroup) {
            out.addAll(scan(composableClass, functionName, g))
        }
        return out
    }

    /**
     * 解析 (composableClass, functionName, groupIndex) -> LiveLiterals 类.
     *
     * 命名规则 (Compose Compiler 1.5+):
     * `LiveLiterals$<composableClass.fqn>$<functionName>$<groupIndex>`
     *
     * 例如:
     * `LiveLiterals$com.example.GreetingKt$Greeting$1`
     */
    fun resolveLiveLiteralsClass(
        composableClass: Class<*>,
        functionName: String,
        groupIndex: Int,
    ): Class<*>? {
        val key = LiveLiteralClassKey(composableClass, functionName, groupIndex)
        cache[key]?.let { hitCount.incrementAndGet(); return it }
        missCount.incrementAndGet()

        val composableFqName = composableClass.name
        val liveLiteralsName = "LiveLiterals\$$composableFqName\$$functionName\$$groupIndex"
        val liveLiteralsClass: Class<*>? = try {
            classLoader.loadClass(liveLiteralsName)
        } catch (e: ClassNotFoundException) {
            null
        }
        cache[key] = liveLiteralsClass
        if (liveLiteralsClass == null) {
            LOG.debug("LiveLiterals class not found: {}", liveLiteralsName)
        } else {
            LOG.info("Resolved LiveLiterals class: {}", liveLiteralsName)
        }
        return liveLiteralsClass
    }

    /**
     * 扫描一个 LiveLiterals 类的所有 int / long 字面量.
     */
    private fun scanClass(clazz: Class<*>): List<LiveLiteral> {
        scanCount.incrementAndGet()
        val out = ArrayList<LiveLiteral>()
        for (field in clazz.declaredFields) {
            // 只关心静态 int 字段
            if (!isStaticIntField(field)) continue
            val accessor = fieldForField(clazz, field.name)
            val value = runCatching { accessor.getInt(null) }.getOrElse { 0 }
            out.add(
                LiveLiteral(
                    fieldName = field.name,
                    type = classifyField(field.name),
                    currentEncodedValue = value,
                ),
            )
        }
        return out.sortedBy { it.fieldName }
    }

    // =============== v2.2 P1: 字段配对 + Group ===============

    /**
     * 扫描 Composable 的字面量, 按 callSite hash 配对.
     *
     * 配对规则: Compose Compiler 跨字段类型 (LONG / COLOR) 会生成
     * `Int$arg-0$callSite-<hash>` + `Int$arg-1$callSite-<hash>` 两个字段,
     * **同 hash** 表明来自同一 call site.
     *
     * @param composableClass Composable 所在类
     * @param functionName Composable 函数名
     * @param groupIndex LiveLiterals group index (默认 1)
     * @return 配对后的字面量列表
     */
    fun scanGroups(
        composableClass: Class<*>,
        functionName: String,
        groupIndex: Int = 1,
    ): List<LiveLiteralGroup> {
        val literals = scan(composableClass, functionName, groupIndex)
        return pairLiterals(literals)
    }

    /**
     * 扫描所有 group index 的字面量, 配对后返回.
     */
    fun scanAllGroups(
        composableClass: Class<*>,
        functionName: String,
        maxGroup: Int = 8,
    ): List<LiveLiteralGroup> {
        val out = ArrayList<LiveLiteralGroup>()
        for (g in 1..maxGroup) {
            out.addAll(scanGroups(composableClass, functionName, g))
        }
        return out
    }

    /**
     * 把字面量按 callSite hash 配对.
     *
     * 配对前:
     * - `Int$arg-0$callSite-3` (high 32, 类型推断为 LONG/COLOR)
     * - `Int$arg-1$callSite-3` (low 32)
     *
     * 配对后:
     * - `LiveLiteralGroup(primary, paired, type=LONG, primaryValue, pairedValue)`
     */
    private fun pairLiterals(literals: List<LiveLiteral>): List<LiveLiteralGroup> {
        // 按 callSite hash 分组
        val byCallSite = LinkedHashMap<String, MutableList<LiveLiteral>>()
        val singles = ArrayList<LiveLiteral>()

        for (lit in literals) {
            val hash = extractCallSiteHash(lit.fieldName)
            if (hash != null && (lit.type == LiveLiteralType.LONG || lit.type == LiveLiteralType.COLOR)) {
                byCallSite.getOrPut(hash) { ArrayList() }.add(lit)
            } else {
                singles.add(lit)
            }
        }

        val out = ArrayList<LiveLiteralGroup>(literals.size)

        // 配对组: 配成 PairedField, 单个变 SingleField
        for ((hash, group) in byCallSite) {
            if (group.size >= 2) {
                // 按 arg-N 排序, arg-0 是 high 32
                group.sortBy { extractArgIndex(it.fieldName) ?: 0 }
                val primary = group[0]
                val secondary = group[1]
                out.add(
                    LiveLiteralGroup(
                        primaryFieldName = primary.fieldName,
                        pairedFieldName = secondary.fieldName,
                        type = primary.type,
                        primaryEncodedValue = primary.currentEncodedValue,
                        pairedEncodedValue = secondary.currentEncodedValue,
                        callSiteHash = hash,
                    ),
                )
            } else {
                // 仅一个, 退化
                val single = group[0]
                out.add(
                    LiveLiteralGroup(
                        primaryFieldName = single.fieldName,
                        type = single.type,
                        primaryEncodedValue = single.currentEncodedValue,
                        callSiteHash = hash,
                    ),
                )
            }
        }

        // 单字段组
        for (s in singles) {
            out.add(
                LiveLiteralGroup(
                    primaryFieldName = s.fieldName,
                    type = s.type,
                    primaryEncodedValue = s.currentEncodedValue,
                ),
            )
        }

        return out.sortedBy { it.primaryFieldName }
    }

    /**
     * 从字段名提取 callSite hash.
     *
     * 例: `Int$arg-0$callSite-3a7b9c2d` -> `"3a7b9c2d"`
     */
    private fun extractCallSiteHash(name: String): String? {
        val marker = "$"
        val callSiteIdx = name.indexOf("callSite-")
        if (callSiteIdx < 0) return null
        val hashStart = callSiteIdx + "callSite-".length
        // 截到字符串末尾或下一个 $
        var hashEnd = name.length
        for (i in hashStart until name.length) {
            if (name[i] == '$') {
                hashEnd = i
                break
            }
        }
        return name.substring(hashStart, hashEnd).takeIf { it.isNotEmpty() }
    }

    /**
     * 从字段名提取 arg index.
     *
     * 例: `Int$arg-0$callSite-3` -> `0`
     */
    private fun extractArgIndex(name: String): Int? {
        val marker = "arg-"
        val idx = name.indexOf(marker)
        if (idx < 0) return null
        val argStart = idx + marker.length
        val sb = StringBuilder()
        for (i in argStart until name.length) {
            val c = name[i]
            if (c.isDigit()) {
                sb.append(c)
            } else {
                break
            }
        }
        return sb.toString().toIntOrNull()
    }

    /**
     * 写入配对字段值 (LONG / COLOR 需要写 2 个字段).
     */
    fun setEncodedValueOnGroup(
        group: LiveLiteralGroup,
        clazz: Class<*>,
        primaryValue: Int,
        pairedValue: Int? = null,
    ) {
        fieldForField(clazz, group.primaryFieldName).set(null, primaryValue)
        if (group.pairedFieldName != null && pairedValue != null) {
            fieldForField(clazz, group.pairedFieldName).set(null, pairedValue)
        }
        setCount.incrementAndGet()
    }

    /**
     * 修改一个字段的 int 值 (在 LiveLiterals 类的 static int 字段上).
     *
     * 写完后, 调用方需要主动触发 Composable 重组 (例如 `composeView.setContent { ... }`).
     */
    fun setIntValue(literal: LiveLiteral, clazz: Class<*>, newValue: Int) {
        val accessor = fieldForField(clazz, literal.fieldName)
        runCatching { accessor.set(null, newValue) }
            .onSuccess { setCount.incrementAndGet() }
            .onFailure { LOG.warn("Failed to set LiveLiteral {} = {}: {}", literal.fieldName, newValue, it.message) }
    }

    /**
     * 解析字面量原始类型 (从字段名推断).
     *
     * 规则: Compose Compiler 生成的字段名前缀
     * - `Int$...`   -> INT
     * - `Long$...`  -> LONG (但实际上 compiler 用两个 int 表示 long)
     * - `Float$...` -> FLOAT
     * - `Boolean$...` -> BOOLEAN (用 int 0/1 表示)
     * - `Dp$...`    -> DP (用 int 表示像素值)
     * - `Sp$...`    -> SP (用 int 表示像素值)
     * - `Color$...` -> COLOR (用两个 int 表示 packed long, high 32 / low 32)
     *
     * 注意: 实际上 Compose Compiler 只生成 `int` 字段, 类型由调用方上下文决定.
     * 这里我们按字段名前缀猜, 错的情况由用户在 UI 上调整.
     */
    private fun classifyField(name: String): LiveLiteralType = when {
        name.startsWith("Int") -> LiveLiteralType.INT
        name.startsWith("Long") -> LiveLiteralType.LONG
        name.startsWith("Float") -> LiveLiteralType.FLOAT
        name.startsWith("Boolean") -> LiveLiteralType.BOOLEAN
        name.startsWith("Dp") -> LiveLiteralType.DP
        name.startsWith("Sp") -> LiveLiteralType.SP
        name.startsWith("Color") -> LiveLiteralType.COLOR
        else -> LiveLiteralType.UNKNOWN
    }

    private fun isStaticIntField(field: java.lang.reflect.Field): Boolean {
        val mods = field.modifiers
        return java.lang.reflect.Modifier.isStatic(mods) && field.type == Int::class.javaPrimitiveType
    }

    private fun fieldForField(clazz: Class<*>, name: String): FieldAccessor {
        val key = "${clazz.name}#$name"
        return fieldCache.getOrPut(key) {
            FieldAccessorCache.getOrCreate(clazz, name)
        }
    }

    /**
     * 统计快照.
     */
    fun stats(): ScannerStats = ScannerStats(
        scanCount = scanCount.get(),
        cacheHits = hitCount.get(),
        cacheMisses = missCount.get(),
        setCount = setCount.get(),
        cachedClasses = cache.size,
        cachedFields = fieldCache.size,
    )

    /**
     * 清空全部缓存 (切换 Composable 时调用).
     */
    fun clear() {
        cache.clear()
        fieldCache.clear()
    }

    private data class LiveLiteralClassKey(
        val composableClass: Class<*>,
        val functionName: String,
        val groupIndex: Int,
    )

    data class ScannerStats(
        val scanCount: Long = 0,
        val cacheHits: Long = 0,
        val cacheMisses: Long = 0,
        val setCount: Long = 0,
        val cachedClasses: Int = 0,
        val cachedFields: Int = 0,
    )
}

/**
 * 单个字面量条目.
 *
 * @property fieldName Compose Compiler 生成的字段名 (e.g. `Int$arg-0$callSite-3`)
 * @property type 推断的字面量类型
 * @property currentEncodedValue 当前 int-encoded 值 (实际含义取决于 [type])
 */
data class LiveLiteral(
    val fieldName: String,
    val type: LiveLiteralType,
    val currentEncodedValue: Int,
)

/**
 * 字面量类型.
 *
 * 推断规则基于 Compose Compiler 生成的字段名, 实际语义由调用方 (Composable) 决定.
 */
enum class LiveLiteralType(val displayName: String) {
    INT("Int"),
    LONG("Long"),
    FLOAT("Float"),
    BOOLEAN("Boolean"),
    DP("Dp"),
    SP("Sp"),
    COLOR("Color"),
    UNKNOWN("Unknown");
}

/**
 * 配对后的字面量组 v2.2 (P1).
 *
 * 跨字段类型 (LONG / COLOR) 需要 2 个 int 字段, Compose Compiler 生成:
 * - `Int$arg-0$callSite-<hash>` (primary / high 32)
 * - `Int$arg-1$callSite-<hash>` (paired / low 32)
 *
 * **同 callSiteHash** 配对, [pairedFieldName] / [pairedEncodedValue] 填齐.
 * 单字段类型 (INT / FLOAT / ...) 只填 primary, paired 字段为 null.
 *
 * @property primaryFieldName 主字段名 (通常 arg-0)
 * @property pairedFieldName 配对字段名 (通常 arg-1), null 表示单字段
 * @property type 字面量类型 (单/配对都用)
 * @property primaryEncodedValue 主字段当前 int 值
 * @property pairedEncodedValue 配对字段当前 int 值, null 表示单字段
 * @property callSiteHash 来自字段名的 callSite 标识, 用于调试
 */
data class LiveLiteralGroup(
    val primaryFieldName: String,
    val type: LiveLiteralType,
    val primaryEncodedValue: Int,
    val pairedFieldName: String? = null,
    val pairedEncodedValue: Int? = null,
    val callSiteHash: String? = null,
) {
    /**
     * 是否跨字段 (LONG / COLOR 等).
     */
    val isPaired: Boolean get() = pairedFieldName != null
}
