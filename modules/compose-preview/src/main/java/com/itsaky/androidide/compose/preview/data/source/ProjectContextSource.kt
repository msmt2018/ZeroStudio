package com.itsaky.androidide.compose.preview.data.source

import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.android.AndroidModule
import org.slf4j.LoggerFactory
import java.io.File

data class ProjectContext(
    val modulePath: String?,
    val variantName: String,
    val compileClasspaths: List<File>,
    val intermediateClasspaths: Set<File>,
    val projectDexFiles: List<File>,
    val needsBuild: Boolean
)

class ProjectContextSource {

    companion object {
        private val LOG = LoggerFactory.getLogger(ProjectContextSource::class.java)
    }

    fun resolveContext(filePath: String): ProjectContext {
        if (filePath.isBlank()) {
            LOG.info("Empty file path, returning default context")
            return ProjectContext(
                modulePath = null,
                variantName = "debug",
                compileClasspaths = emptyList(),
                intermediateClasspaths = emptySet(),
                projectDexFiles = emptyList(),
                needsBuild = false
            )
        }

        val file = File(filePath)
        LOG.info("Resolving project context for file: {}", file.absolutePath)

        val projectManager = IProjectManager.getInstance()
        val module = projectManager.findModuleForFile(file)

        if (module == null) {
            LOG.info("No module found for file")
            return ProjectContext(
                modulePath = null,
                variantName = "debug",
                compileClasspaths = emptyList(),
                intermediateClasspaths = emptySet(),
                projectDexFiles = emptyList(),
                needsBuild = false
            )
        }

        LOG.info("Found module: {} (type: {})", module.name, module.javaClass.simpleName)

        val intermediateClasspaths = module.getIntermediateClasspaths()
        val compileClasspaths = (module.getCompileClasspaths() + intermediateClasspaths).distinct()

        val projectDexFiles = module.getRuntimeDexFiles().toList()
        val variantName = (module as? AndroidModule)?.getSelectedVariant()?.name ?: "debug"
        // gradle-dex 已经是唯一路径. 这里判定 "项目还没构建过" 的策略：
        // 1) 中间产物 (intermediateClasspaths): `assembleDebug` 走通后会写到
        //    `build/tmp/kotlin-classes/<variant>` 与 `build/intermediates/javac/<variant>/classes`
        // 2) 运行期 dex (projectDexFiles): AGP mergeDex 后会写到
        //    `build/intermediates/dex/<variant>` 与 `build/intermediates/project_dex_archive/<variant>`
        //
        // 之前只看 intermediateClasspaths 会在两种情况下误判 needsBuild=true, 跟
        // 用户反馈的 bug 完全一致：
        //   - 增量构建: AGP 复用上一次的 class 缓存, intermediateClasspaths 路径下
        //     没有任何文件 (仅 jar 缓存命中), 但 dex 已经被重新生成出来
        //   - 纯 K2 cache: 只跑过 KSP / K2 流水线, javac 中间产物被 skip, 但 dex
        //     仍然在 project_dex_archive 下可见
        //
        // 因此只要 intermediateClasspaths 或 projectDexFiles 任意一组非空, 都视为
        // "项目已经构建过, 可以走 dex 加载路径", 避免一直停在 NeedsBuild 状态。
        val hasIntermediateArtifacts = intermediateClasspaths.isNotEmpty()
        val hasRuntimeDex = projectDexFiles.isNotEmpty()
        val needsBuild = !hasIntermediateArtifacts && !hasRuntimeDex

        LOG.info("Found {} total classpaths ({} compile, {} intermediate) for module: {}",
            compileClasspaths.size,
            compileClasspaths.size - intermediateClasspaths.size,
            intermediateClasspaths.size,
            module.name)
        LOG.info("Found {} project DEX files for runtime loading", projectDexFiles.size)
        LOG.info(
            "Module path: {}, variant: {}, needsBuild: {}",
            module.path,
            variantName,
            needsBuild,
        )

        if (!needsBuild) {
            intermediateClasspaths.forEach { cp ->
                LOG.info("  Intermediate: {} (exists: {})", cp.absolutePath, cp.exists())
            }
            projectDexFiles.forEach { dex ->
                LOG.info("  Project DEX: {} (exists: {})", dex.absolutePath, dex.exists())
            }
        }

        return ProjectContext(
            modulePath = module.path,
            variantName = variantName,
            compileClasspaths = compileClasspaths,
            intermediateClasspaths = intermediateClasspaths,
            projectDexFiles = projectDexFiles,
            needsBuild = needsBuild
        )
    }
}
