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
        // gradle-dex 已经是唯一路径, 项目没编译过 (intermediateClasspaths 为空) 才需要先 build.
        val needsBuild = intermediateClasspaths.isEmpty()

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
