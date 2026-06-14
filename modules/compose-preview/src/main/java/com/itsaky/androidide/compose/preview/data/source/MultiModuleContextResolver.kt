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

package com.itsaky.androidide.compose.preview.data.source

import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.android.AndroidModule
import org.slf4j.LoggerFactory
import java.io.File

/**
 * v2.3 P0 Multi-module: 解析当前 module 的 1 跳依赖图.
 *
 * 通过 [IProjectManager] 拿到当前 module, 然后 BFS (深度 1) 拿直接依赖的
 * AndroidModule, 把每个 module 的 dex / classpath 收集到 [ModuleInfo].
 *
 * ## 用法
 *
 * ```kotlin
 * val resolver = MultiModuleContextResolver()
 * val related = resolver.resolveRelated(filePath, maxHops = 1)
 * // related[0] = 主 module
 * // related[1..N] = 1 跳依赖
 * ```
 *
 * ## 错误处理
 *
 * - module 不存在 → 返回空列表 (与 [ProjectContextSource.resolveContext] 行为一致)
 * - 依赖 module 解析失败 → 跳过该 module, 记录 warn 日志
 * - dex 路径不存在 → ModuleInfo.dexFiles 仍返回, 由下游 [ComposeClassLoader] 处理
 */
class MultiModuleContextResolver {

    companion object {
        private val LOG = LoggerFactory.getLogger(MultiModuleContextResolver::class.java)
    }

    /**
     * 解析 [filePath] 所在 module 的 1 跳依赖 module 列表.
     *
     * @return [ModuleInfo] 列表, 第 0 项是主 module, 后面按 BFS 顺序.
     *         filePath 无效 / module 不存在 → 返回空列表.
     */
    fun resolveRelated(filePath: String, maxHops: Int = 1): List<ModuleInfo> {
        if (filePath.isBlank()) return emptyList()
        val file = File(filePath)
        val projectManager = IProjectManager.getInstance()
        val mainModule = projectManager.findModuleForFile(file) as? AndroidModule ?: return emptyList()

        val visited = LinkedHashSet<String>()
        val result = mutableListOf<ModuleInfo>()
        val queue = ArrayDeque<Pair<AndroidModule, Int>>()
        queue.addLast(mainModule to 0)

        while (queue.isNotEmpty()) {
            val (module, depth) = queue.removeFirst()
            if (module.path in visited) continue
            visited.add(module.path)

            val dexFiles = runCatching { module.getRuntimeDexFiles() }
                .getOrElse { emptySet() }
                .toList()
            val intermediate = runCatching { module.getIntermediateClasspaths() }
                .getOrElse { emptySet() }
            val compile = runCatching { module.getCompileClasspaths() }
                .getOrElse { emptySet() }
            val allClasspath = (compile + intermediate).toList().distinct()

            val deps = if (depth < maxHops) {
                runCatching { module.getCompileModuleProjects() }
                    .getOrElse { emptyList() }
                    .mapNotNull { it as? AndroidModule }
                    .map { it.path }
                    .toSet()
            } else {
                emptySet()
            }

            result.add(
                ModuleInfo(
                    gradlePath = module.path,
                    name = module.name ?: module.path,
                    directDependencies = deps,
                    dexFiles = dexFiles,
                    compileClasspath = allClasspath,
                )
            )

            if (depth < maxHops) {
                deps.forEach { depPath ->
                    val depModule = runCatching {
                        // 复用 IProjectManager 拿 workspace, 然后 findProject
                        IProjectManager.getInstance().getWorkspace()?.findProject(depPath)
                    }.getOrNull() as? AndroidModule
                    if (depModule != null && depPath !in visited) {
                        queue.addLast(depModule to (depth + 1))
                    }
                }
            }
        }

        LOG.info(
            "resolveRelated: {} module(s) for {} (main={})",
            result.size, file.absolutePath, mainModule.path
        )
        return result
    }
}
