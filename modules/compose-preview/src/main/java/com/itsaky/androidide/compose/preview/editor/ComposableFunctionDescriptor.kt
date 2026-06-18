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

package com.itsaky.androidide.compose.preview.editor

/**
 * Compose 属性编辑 v3.3.1 数据模型.
 *
 * 端到端流程: dex → smali → java → 解析属性 → 映射 .kt 源码 → 修改 → build.
 *
 * ## 工作原理
 *
 * 用户在 preview 中选中一个节点, 调用 [ComposeAttributeEditor.editAttribute] 修改属性
 * (e.g. `Text("Hello")` → `Text("World")`):
 * 1. 用 [com.android.tools.smali:smali-dexlib2] 把 dex 转成 smali
 * 2. 找到目标 @Composable 函数, 解析其方法内的 invoke call sites
 * 3. 用 CFR 把 smali 涉及的类反编译为 java (或者直接用 ASM 分析)
 * 4. 用 ANTLR Java 词法分析解析 java 源码, 找到对应的属性设置位置
 *    (`new Text("Hello", ...)` 或 `TextKt.Text("Hello", ...)`)
 * 5. 通过 FQN 映射回 .kt 源文件 + 函数名 + 行号
 * 6. 用 Kotlin 词法分析定位 .kt 源码中对应的 token, 修改
 * 7. 调用 BuildService.executeTasks("assembleDebug") 重新编译
 * 8. 重新加载新 dex, 渲染新版 compose UI
 *
 * ## 反编译后映射的精度
 *
 * 因为 kotlin → dex 后, 调试信息 (line numbers) 是保留的 (R8/D8 不会 strip 除非 minify),
 * 加上 @Composable 编译后会生成 `Composer.startReplaceableGroup(<原始行号>)`,
 * 我们可以从 dex 的 line table 反查回 .kt 行号.
 */

/**
 * 单个 @Composable 函数的属性设置点.
 *
 * @param className 含此函数的 class 的 FQN (e.g. `com.example.MainActivity$ComposableSingletons$MainKt`).
 * @param methodName 函数名 (e.g. `getMain$lambda$1` 或 `Main`).
 * @param methodDesc 方法签名 (e.g. `(Landroidx/compose/runtime/Composer;I)V`).
 * @param sourceFile dex 中的 source file 字段 (e.g. `Main.kt`).
 * @param lineStart 方法起始行号 (dex line table).
 * @param lineEnd 方法结束行号.
 * @param calls 方法内调用的 compose 函数列表 — 每个是一个 (composable FQN, call site line, args).
 */
data class ComposableFunctionDescriptor(
    val className: String,
    val methodName: String,
    val methodDesc: String,
    val sourceFile: String?,
    val lineStart: Int,
    val lineEnd: Int,
    val calls: List<ComposableCallSite>,
)

/**
 * 单个 compose 函数调用点.
 *
 * @param composableName 调用的 compose 函数名 (e.g. `Text`, `Button`, `HelloCompose`).
 * @param composableFqn 完整 FQN (e.g. `androidx.compose.material3.Text`).
 * @param line 调用点行号.
 * @param parameterAssignments 参数赋值列表 — 每个描述一个 named parameter (e.g. `text = "Hello"`).
 */
data class ComposableCallSite(
    val composableName: String,
    val composableFqn: String,
    val line: Int,
    val parameterAssignments: List<NamedParameter>,
)

/**
 * 单个命名参数赋值.
 *
 * @param name 参数名 (e.g. `text`).
 * @param value 参数值 (字符串形式, e.g. `"Hello"`, `16.sp`, `Color.Red`).
 * @param valueType 值类型提示 (`string` / `number` / `color` / `dimen` / `other`).
 * @param offsetInLine 在该行内的列偏移 (用于精确定位, 修改时无歧义).
 */
data class NamedParameter(
    val name: String,
    val value: String,
    val valueType: ValueType = ValueType.OTHER,
    val offsetInLine: Int = -1,
) {
    enum class ValueType {
        STRING, NUMBER, COLOR, DIMEN, BOOLEAN, NULL, OTHER,
    }
}

/**
 * 一次属性编辑操作.
 *
 * @param className 目标 class FQN.
 * @param methodName 目标方法名.
 * @param callLine 目标调用行.
 * @param parameterName 要修改的参数名.
 * @param oldValue 旧值 (用于校验).
 * @param newValue 新值 (字符串形式).
 */
data class AttributeEdit(
    val className: String,
    val methodName: String,
    val callLine: Int,
    val parameterName: String,
    val oldValue: String,
    val newValue: String,
)

/**
 * 编辑结果.
 */
sealed class AttributeEditResult {
    /** 成功 — .kt 已修改, build 已触发, dex 将更新. */
    data class Success(
        val ktFile: String,
        val line: Int,
        val oldSource: String,
        val newSource: String,
        val taskName: String,
    ) : AttributeEditResult()

    /** 失败 — 错误信息. */
    data class Failure(val reason: String) : AttributeEditResult()
}
