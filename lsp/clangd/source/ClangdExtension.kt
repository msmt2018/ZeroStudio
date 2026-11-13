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

import com.itsaky.androidide.lsp.api.ILanguageServerRegistry

/**
 * Clangd 扩展注册器。
 *
 * 功能与用途：
 * 负责在 AndroidIDE 初始化阶段（或项目打开时），将自定义的 C/C++ 语言服务器 (Clangd)
 * 注册并挂载到 AndroidIDE 标准的 LSP 生态中。
 *
 * 工作流程线路图：
 * [AndroidIDE 启动 / ProjectOpenedEvent]
 *        |
 *        v
 * [ClangdExtension.initialize]
 *        | (实例化 Server 和 Settings)
 *        v
 * [ILanguageServerRegistry.register] (注册到系统)
 *        |
 *        v
 * [AndroidIDE] (系统检测到新 Server，开始监听生命周期，调用 setupWorkspace 等)
 *
 * 上下文关系：
 * 需要在 Application 的 onCreate 或 ProjectManager 初始化时调用 [ClangdExtension.initialize]。
 * 
 * @author android_zero
 */
object ClangdExtension {

    /**
     * 将 ClangdLanguageServer 注入到系统环境。
     * 推荐在应用程序启动时，或收到 `ProjectInitializedEvent` 时调用一次。
     */
    fun initialize() {
        val registry = ILanguageServerRegistry.getDefault()
        
        // 防止重复注册
        if (registry.getServer("clangd-native") != null) {
            return
        }

        // 实例化语言服务器和配置管理器
        val server = ClangdLanguageServer()
        val settings = ClangdServerSettings()
        
        // 绑定配置
        server.applySettings(settings)

        // 注册到系统的多语言中心
        registry.register(server)
    }
}