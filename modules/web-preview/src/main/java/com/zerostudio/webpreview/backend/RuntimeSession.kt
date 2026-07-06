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
 * 后端运行时会话。
 *
 * 由 [BackendRuntime.startService] 返回, 持有运行中服务的元信息 + 停止回调。
 * 调用 [stop] 终止服务进程。
 *
 * @property pid 进程 ID (可能为 0, 如果无法获取)
 * @property port 监听端口
 * @property workDir 工作目录
 * @property runtime 运行时名 (如 "node" / "python" / "php")
 * @property command 完整启动命令
 * @property startedAt 启动时间戳 (毫秒)
 */
data class RuntimeSession(
    val pid: Int,
    val port: Int,
    val workDir: File,
    val runtime: String,
    val command: String,
    val startedAt: Long = System.currentTimeMillis(),
    private val stopCallback: () -> Unit,
) {
    /** 终止服务进程。 */
    fun stop() = stopCallback()

    /** 构造 http://localhost:port URL。 */
    fun toLocalUrl(): String = "http://localhost:$port"
}
