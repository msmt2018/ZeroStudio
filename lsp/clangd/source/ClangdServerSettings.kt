/*
 * This file is part of ZeroStudio.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */


package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.util.PrefBasedServerSettings

/**
 * Clangd 语言服务器配置管理器。
 *
 * 功能与用途：
 * 继承自 AndroidIDE 的 [PrefBasedServerSettings]，自动代理和读取 AndroidIDE 的全局编辑器偏好设置。
 * 它决定了当前 Clangd 语言服务器是否应该响应代码补全、格式化、代码诊断等行为。
 * 
 * 工作流程线路图：
 * [AndroidIDE 设置界面] -> (修改选项: 关闭代码补全) -> [PreferenceManager]
 *        |
 *        v
 * [ClangdServerSettings] (读取全局配置状态)
 *        |
 *        v
 * [ClangdLanguageServer] (根据状态决定是否向底层 C++ 进程发送 LSP 请求)
 *
 * 上下文与父类关系：
 * - 继承自 [PrefBasedServerSettings]，自动复用了对全局开关（例如 completionsEnabled()）的读取能力。
 * - 作为依赖被注入到 [ClangdLanguageServer] 中。
 * 
 * @author android_zero
 */
class ClangdServerSettings : PrefBasedServerSettings() {

    /**
     * 可以在此处针对 C/C++ 语言进行特定的配置覆盖。
     * 例如，如果 C++ 解析异常昂贵，可以在低端设备上强制重写诊断开关。
     * 目前保持默认，完全遵循 AndroidIDE 的全局设定。
     */

    override fun codeAnalysisEnabled(): Boolean {
        // 调用父类读取用户偏好，并可加入额外的 C/C++ 专属控制逻辑
        return super.codeAnalysisEnabled()
    }

    override fun completionsEnabled(): Boolean {
        return super.completionsEnabled()
    }
}