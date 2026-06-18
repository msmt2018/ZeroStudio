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
 * v3.3.1 属性编辑数据模型 (去除冗余后).
 *
 * 之前 v3.3.1 把 `ComposableFunctionDescriptor` / `ComposableCallSite` 也放在这里,
 * 但这两类从未被 UI 使用 (DexAnalyzer.analyze() 整体是死代码). 现在只保留
 * UI 真正需要的 3 个类:
 * - [NamedParameter]     - 解析后的单个 named parameter (参数名 + 值 + 类型)
 * - [AttributeEdit]      - 一次属性编辑操作 (目标 class / method / 行 / 参数 / 新值)
 * - [AttributeEditResult]- 编辑结果 (Success / Failure)
 *
 * ## 端到端流程
 *
 * 1. [ComposeAttributeEditor.extractAttributesFromDex] 把 dex 整个喂给
 *    [DexAnalyzer.dexToJava] (单次 smali 拆解, 不再分两步)
 * 2. 在返回的 java 文本里按 methodName 找方法体, 用正则提取 named parameter
 *    (`text = "Hello"` 这种) → [NamedParameter] 列表
 * 3. UI (AttributeEditPanel) 展示参数列表 + AlertDialog 改值
 * 4. 用户提交 → [ComposeAttributeEditor.editKtFile] 改 .kt 源文件
 * 5. ViewModel 触发 `assembleDebug` build
 * 6. 新 dex 写回 → 重新渲染, 用户看到新 UI
 */

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
