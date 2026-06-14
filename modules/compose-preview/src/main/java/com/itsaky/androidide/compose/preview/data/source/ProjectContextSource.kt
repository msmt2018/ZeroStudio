package com.itsaky.androidide.compose.preview.data.source

import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.android.AndroidModule
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Properties

/**
 * v2.3 P0 Multi-module: 单个 module 的元信息.
 *
 * 一个 module 在 Multi-module 拓扑中的标识 + 编译期/运行期 dex 路径.
 * 用于 [ProjectContext.relatedModules] 和 [com.itsaky.androidide.compose.preview.runtime.ModuleClassLoaderRegistry].
 */
data class ModuleInfo(
    /** Gradle 路径, e.g. ":app" / ":feature:foo". */
    val gradlePath: String,
    /** 人类可读 module 名称, e.g. "app" / "feature_foo". */
    val name: String,
    /** 1 跳依赖的 module gradlePath 集合 (排除自身). */
    val directDependencies: Set<String>,
    /** 该 module 的 dex 文件 (来自 getRuntimeDexFiles). */
    val dexFiles: List<File>,
    /** 该 module 的 compileClasspath (来自 getCompileClasspaths + getIntermediateClasspaths). */
    val compileClasspath: List<File>,
)

data class ProjectContext(
    val modulePath: String?,
    val variantName: String,
    val compileClasspaths: List<File>,
    val intermediateClasspaths: Set<File>,
    val projectDexFiles: List<File>,
    val needsBuild: Boolean,
    /**
     * v2.3 P0: Multi-module 拓扑. 包含主 module + 1 跳依赖 module 的 [ModuleInfo].
     * 单 module 项目时此列表只含主 module 一项 (gradlePath = [modulePath]).
     */
    val relatedModules: List<ModuleInfo> = emptyList(),
)

class ProjectContextSource {

    companion object {
        private val LOG = LoggerFactory.getLogger(ProjectContextSource::class.java)
        private const val FORCE_GRADLE_DEXING_KEY = "android.compose.preview.useGradleDexing"
    }

    fun resolveContext(filePath: String): ProjectContext {
        if (filePath.isBlank()) {
            LOG.info("Empty file path, returning default context")
            return defaultContext()
        }

        val file = File(filePath)
        LOG.info("Resolving project context for file: {}", file.absolutePath)

        val projectManager = IProjectManager.getInstance()
        val module = projectManager.findModuleForFile(file)

        if (module == null) {
            LOG.info("No module found for file")
            return defaultContext()
        }

        LOG.info("Found module: {} (type: {})", module.name, module.javaClass.simpleName)

        val intermediateClasspaths = module.getIntermediateClasspaths()
        val compileClasspaths: List<File> = (module.getCompileClasspaths() + intermediateClasspaths).toList().distinct()
        val forceGradleDexing = isGradleDexingForced(projectManager.projectDir, file)

        val projectDexFiles = module.getRuntimeDexFiles().toList()
        val variantName = (module as? AndroidModule)?.getSelectedVariant()?.name ?: "debug"
        val needsBuild = forceGradleDexing || intermediateClasspaths.isEmpty()

        // v2.3 P0 Multi-module: 解析 1 跳依赖, 填到 ProjectContext.relatedModules
        val resolver = MultiModuleContextResolver()
        val relatedModules = runCatching { resolver.resolveRelated(filePath, maxHops = 1) }
            .getOrElse {
                LOG.warn("resolveRelated failed: {}", it.message)
                emptyList()
            }
        // 单 module 兜底: 没解析到任何 related → 把当前 module 自身作为唯一 entry
        val finalRelated = if (relatedModules.isNotEmpty()) relatedModules
        else listOf(
            ModuleInfo(
                gradlePath = module.path,
                name = module.name ?: module.path,
                directDependencies = emptySet(),
                dexFiles = projectDexFiles,
                compileClasspath = compileClasspaths,
            )
        )

        LOG.info("Found {} total classpaths ({} compile, {} intermediate) for module: {}",
            compileClasspaths.size,
            compileClasspaths.size - intermediateClasspaths.size,
            intermediateClasspaths.size,
            module.name)
        LOG.info("Found {} project DEX files for runtime loading", projectDexFiles.size)
        LOG.info(
            "Module path: {}, variant: {}, needsBuild: {}, forceGradleDexing: {}, related: {}",
            module.path,
            variantName,
            needsBuild,
            forceGradleDexing,
            finalRelated.size,
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
            needsBuild = needsBuild,
            relatedModules = finalRelated,
        )
    }

    private fun defaultContext() = ProjectContext(
        modulePath = null,
        variantName = "debug",
        compileClasspaths = emptyList(),
        intermediateClasspaths = emptySet(),
        projectDexFiles = emptyList(),
        needsBuild = false,
    )

    private fun isGradleDexingForced(projectDir: File?, sourceFile: File): Boolean {
        // 治本：projectDir 改 nullable 后，no project ⇒ no gradle.properties ⇒ false
        if (projectDir == null) return false
        val candidates = linkedSetOf<File>()
        var current: File? = sourceFile.parentFile
        while (current != null && current.path.startsWith(projectDir.path)) {
            candidates.add(File(current, "gradle.properties"))
            if (current == projectDir) break
            current = current.parentFile
        }
        candidates.add(File(projectDir, "gradle.properties"))

        for (propertiesFile in candidates) {
            if (!propertiesFile.exists()) continue
            val value =
                runCatching {
                        Properties().apply {
                            propertiesFile.inputStream().use { load(it) }
                        }[FORCE_GRADLE_DEXING_KEY]?.toString()?.trim()
                    }
                    .getOrNull()
                    ?: continue

            if (value.equals("true", ignoreCase = true)) {
                LOG.info("Gradle dexing force-enabled by {}", propertiesFile.absolutePath)
                return true
            }
        }

        return false
    }
}
