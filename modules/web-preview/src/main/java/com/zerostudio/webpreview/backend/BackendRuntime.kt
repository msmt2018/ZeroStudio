/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.zerostudio.webpreview.backend

import java.io.File

/**
 * 后端运行时抽象。
 *
 * 抽象不同后端运行环境 (Termux / Proot Linux 容器 / 直接 APK 内置运行时),
 * 让 WebPreviewFragment 不关心具体后端实现, 只通过本接口启动/停止服务。
 *
 * 实现类:
 * - [TermuxBackendRuntime]: 通过项目内置 Termux 模块同进程执行 (用户 `pkg install nodejs` 按需装环境)
 * - 未来: ProotBackendRuntime — 集成 proot Linux 容器, 支持完整 Linux 生态
 */
interface BackendRuntime {

    /** 运行时显示名 (如 "Termux" / "Proot")。 */
    val name: String

    /** 运行时是否可用 (检查环境是否就绪)。 */
    fun isAvailable(): Boolean

    /**
     * 启动后端服务。
     *
     * @param workDir 工作目录 (用户项目目录)
     * @param command 启动命令 (如 "node server.js" / "python -m http.server 8000")
     * @param port 监听端口 (用于 WebView 连接 localhost:port)
     * @return 运行时会话
     */
    suspend fun startService(workDir: File, command: String, port: Int): RuntimeSession

    /**
     * 停止后端服务。
     *
     * @param session 之前 [startService] 返回的会话
     */
    fun stopService(session: RuntimeSession)
}
