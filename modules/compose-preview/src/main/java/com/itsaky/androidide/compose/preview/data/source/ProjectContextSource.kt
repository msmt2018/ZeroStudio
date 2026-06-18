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

/**
 * 应用图标 + 标签 (PR-C 桌面 launcher 用).
 *
 * - [iconResName] 资源名 (例如 `ic_launcher`), 用来在 res/mipmap 目录找 png.
 * - [iconResId] 暂时保留 0, 真正解析用 [findAppIconFile] 路径.
 * - [label] 来自 `<application android:label="...">`.
 * - [packageName] 来自 `<manifest package="...">`.
 */
data class ApplicationIconInfo(
    val iconResName: String?,
    val label: String?,
    val packageName: String?,
)

class ProjectContextSource {

    companion object {
        private val LOG = LoggerFactory.getLogger(ProjectContextSource::class.java)
    }

    /**
     * 【PR-C】从模块的 AndroidManifest.xml 解析 application 图标 + 标签 + 包名.
     *
     * 找不到 manifest 或解析失败时返回 null. 不会抛异常, 上层 [DesktopLauncher] 走
     * Material icon fallback 即可.
     */
    fun loadApplicationIcon(modulePath: String?): ApplicationIconInfo? {
        if (modulePath.isNullOrBlank()) return null
        val moduleDir = File(modulePath)
        if (!moduleDir.isDirectory) return null

        // 优先 src/main/AndroidManifest.xml, 找不到再 fallback 到模块根目录.
        val manifestFile = File(moduleDir, "src/main/AndroidManifest.xml")
            .takeIf { it.isFile }
            ?: File(moduleDir, "AndroidManifest.xml").takeIf { it.isFile }
            ?: return null

        val info = ManifestIconLoader.load(manifestFile) ?: return null
        return ApplicationIconInfo(
            iconResName = info.applicationIconResName,
            label = info.applicationLabel,
            packageName = info.packageName,
        )
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
        // 【关键修复】之前只看 intermediateClasspaths.isEmpty(), 但 build 成功后
        // intermediateClasspaths 仍然可能为空 (variant artifact 没扫到), 导致
        // needsBuild 永远为 true, 预览页一直在 NeedsBuild / Ready 之间反复横跳.
        // 现在的判定 (只要任一为真就重建):
        //   1) forceGradleDexing 强制重建
        //   2) intermediateClasspaths 为空
        //   3) projectDexFiles 为空 (没找到任何 dex, 一定没构建)
        //   4) 任何一个 dex 文件不存在 (构建中断/失败留下的)
        val anyDexMissing = projectDexFiles.any { dexFile ->
            !dexFile.exists()
        }
        val needsBuild = forceGradleDexing ||
            intermediateClasspaths.isEmpty() ||
            projectDexFiles.isEmpty() ||
            anyDexMissing

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
