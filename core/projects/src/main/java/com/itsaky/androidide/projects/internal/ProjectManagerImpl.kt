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
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.projects.internal

import androidx.annotation.RestrictTo
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.google.auto.service.AutoService
import com.google.common.collect.ImmutableList
import com.itsaky.androidide.eventbus.events.EventReceiver
import com.itsaky.androidide.eventbus.events.editor.DocumentSaveEvent
import com.itsaky.androidide.eventbus.events.file.FileCreationEvent
import com.itsaky.androidide.eventbus.events.file.FileDeletionEvent
import com.itsaky.androidide.eventbus.events.file.FileEvent
import com.itsaky.androidide.eventbus.events.file.FileRenameEvent
import com.itsaky.androidide.eventbus.events.project.ProjectInitializedEvent
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.IWorkspace
import com.itsaky.androidide.projects.ModuleProject
import com.itsaky.androidide.projects.R
import com.itsaky.androidide.projects.android.AndroidModule
import com.itsaky.androidide.projects.bsp.ProjectBspRegistry
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.tasks.executeAsync
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.models.BuildVariantInfo
import com.itsaky.androidide.utils.DocumentUtils
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.GradleFileParser
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.withStopWatch
import java.io.File
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.LoggerFactory

/**
 * 项目管理器内部实现类 ([IProjectManager])。
 *
 * <p>该类负责真正执行项目配置的初始化（Setup），协调事件总线，执行代码生成（如 AGP DataBinding/AIDL 生成），并管理文件系统的变更通知。</p>
 *
 * @author Akash Yadav
 */
@AutoService(IProjectManager::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
class ProjectManagerImpl : IProjectManager, EventReceiver {

  private var _workspace: WorkspaceImpl? = null
  private var _projectDir: File? = null

  /** 用于缓存判断当前项目是否为插件项目。 */
  @Volatile internal var pluginProjectCached: Boolean? = null

  var projectInitialized: Boolean = false
  var cachedInitResult: InitializeResult? = null

  override val projectDir: File
    get() = checkNotNull(_projectDir) { "Cannot get project directory. Path has not been set." }

  override val projectSyncIssues: ProjectSyncIssues?
    get() = getWorkspace()?.getProjectSyncIssues()

  override fun getWorkspace(): IWorkspace? {
    return _workspace
  }

  override fun openProject(directory: File) {
    // IMP: Always use canonical path
    this._projectDir = directory.canonicalFile
    // 清除上一个项目的缓存状态
    this.pluginProjectCached = null
  }

  override suspend fun isGradleSyncNeeded(projectDir: File): Boolean {
    return withContext(Dispatchers.IO) {
      !File(projectDir, ".gradle").exists() || !File(projectDir, "build").exists()
    }
  }

  override suspend fun setupProject() {
    val bspService =
        checkNotNull(ProjectBspRegistry.lookup()) {
          "BSP build service is unavailable. Project setup now requires BSP workspace/buildTargets."
        }
    // 缓存插件项目标志
    pluginProjectCached =
        withContext(Dispatchers.IO) {
          File(projectDir, Environment.PLUGIN_API_JAR_RELATIVE_PATH).exists()
        }

    this._workspace =
        withStopWatch("Transform project proxy") {
          withContext(Dispatchers.IO) {
            BspWorkspaceModelBuilder.build(projectDir, bspService)
          }
        }

    val rootProject = this.getWorkspace() ?: return

    // build variants must be updated before the sources and classpaths are indexed
    updateBuildVariants { buildVariants -> _workspace!!.setVariantSelections(buildVariants) }

    log.info(
        "Found {} project sync issues: {}",
        rootProject.getProjectSyncIssues().syncIssues.size,
        rootProject.getProjectSyncIssues().syncIssues,
    )

    withStopWatch("Setup project") {
      val indexerScope = CoroutineScope(Dispatchers.Default)
      val modulesFlow = flow {
        rootProject.getSubProjects().filterIsInstance<ModuleProject>().forEach { emit(it) }
      }

      val jobs = modulesFlow.map { module ->
        indexerScope.async {
          module.indexSourcesAndClasspaths()
          if (module is AndroidModule) {
            module.readResources()
          }
        }
      }

      // wait for the indexing to finish
      jobs.toList().awaitAll()
    }
    

  }


  override fun getAndroidModules(): List<AndroidModule> {
    val workspace = this.getWorkspace() ?: return emptyList()
    return workspace.androidProjects().toList()
  }

  override fun getAndroidAppModules(): List<AndroidModule> {
    return getAndroidModules().filter { it.isApplication }
  }

  override fun getAndroidLibraryModules(): List<AndroidModule> {
    return getAndroidModules().filter { it.isLibrary }
  }

  override fun findModuleForFile(file: File, checkExistence: Boolean): ModuleProject? {
    val ws = this.getWorkspace() ?: return null
    return ws.findModuleForFile(file, checkExistence)
  }


  override fun destroy() {
    log.info("Destroying project manager")

    this._workspace?.setVariantSelections(emptyMap())
    this._workspace = null

    this._projectDir = null
    this.cachedInitResult = null
    this.projectInitialized = false
    this.pluginProjectCached = null
  }

  @JvmOverloads
  fun generateSources(
      builder: BuildService? = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
  ) {
    if (builder == null) {
      log.warn("Cannot generate sources. BuildService is null.")
      return
    }

    if (!builder.isToolingServerStarted()) {
      flashError(R.string.msg_tooling_server_unavailable)
      return
    }

    if (builder.isBuildInProgress) {
      return
    }

    val tasks =
        getWorkspace()
            ?.androidProjects()
            ?.flatMap { module ->
              val variant = module.getSelectedVariant()
              if (variant == null) {
                log.error("Selected build variant for project '{}' not found", module.path)
                return@flatMap emptyList()
              }

              val mainArtifact = variant.mainArtifact
              val variantNameCapitalized =
                  variant.name.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                  }

              return@flatMap listOf(
                      mainArtifact.resGenTaskName,
                      mainArtifact.sourceGenTaskName,
                      if (module.viewBindingOptions.isEnabled)
                          "dataBindingGenBaseClasses$variantNameCapitalized"
                      else null,
                      "process${variantNameCapitalized}Resources",
                  )
                  .mapNotNull { it?.let { "${module.path}:${it}" } }
            }
            ?.toList() ?: emptyList()

    builder.executeTasks(*tasks.toTypedArray()).whenComplete { result, taskErr ->
      if (result == null || !result.isSuccessful || taskErr != null) {
        log.warn("Execution for tasks failed: {} {}", tasks, taskErr ?: "")
      } else {
        notifyProjectUpdate()
      }
    }
  }

  fun notifyProjectUpdate() {

    executeAsync {
      getWorkspace()?.apply {
        getSubProjects().forEach { subproject ->
          if (subproject is ModuleProject) {
            subproject.indexSources()
          }
        }
      }

      val event = ProjectInitializedEvent()
      event.put(IWorkspace::class.java, getWorkspace())
      EventBus.getDefault().post(event)
    }
  }

  private fun updateBuildVariants(onUpdated: (Map<String, BuildVariantInfo>) -> Unit = {}) {
    val rootProject =
        checkNotNull(this.getWorkspace()) {
          "Cannot update build variants. Root project model is null."
        }

    val buildVariants = mutableMapOf<String, BuildVariantInfo>()
    rootProject.getSubProjects().forEach { subproject ->
      if (subproject is AndroidModule) {

        // variant names are not expected to be modified
        val variantNames =
            ImmutableList.builder<String>()
                .addAll(subproject.variants.map { variant -> variant.name })
                .build()

        val variantName = subproject.configuredVariant?.name ?: IAndroidProject.DEFAULT_VARIANT

        val moduleDir = File(projectDir, subproject.path.replace(":", File.separator))
        val gradleInfo = GradleFileParser.parseModuleBuildGradle(moduleDir)

        buildVariants[subproject.path] =
            BuildVariantInfo(
                projectPath = subproject.path,
                buildVariants = variantNames,
                selectedVariant = variantName,
                versionName = gradleInfo?.versionName,
                versionCode = gradleInfo?.versionCode,
                minSdk = gradleInfo?.minSdk,
                targetSdk = gradleInfo?.targetSdk,
                compileSdk = gradleInfo?.compileSdk,
            )
      }
    }

    onUpdated(buildVariants)
  }

  private fun generateSourcesIfNecessary(event: FileEvent) {
    val builder = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE) ?: return
    val file = event.file
    if (getWorkspace()?.isAndroidResource(file) != true) {
      return
    }

    generateSources(builder)
  }

  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onFileSaved(event: DocumentSaveEvent) {
    event.file.apply {
      if (isDirectory()) {
        return@apply
      }

      if (extension != "xml") {
        return@apply
      }

      val module = getWorkspace()?.findModuleForFile(this, false) ?: return@apply
      if (module !is AndroidModule) {
        return@apply
      }

      val isResource =
          module.mainSourceSet?.sourceProvider?.resDirectories?.any {
            this.pathString.contains(it.path)
          } ?: false

      if (isResource) {
        module.updateResourceTable()
      }
    }
  }

  override fun notifyFileCreated(file: File) {
    onFileCreated(FileCreationEvent(file))
  }

  override fun notifyFileDeleted(file: File) {
    onFileDeleted(FileDeletionEvent(file))
  }

  override fun notifyFileRenamed(from: File, to: File) {
    onFileRenamed(FileRenameEvent(from, to))
  }

  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun onFileCreated(event: FileCreationEvent) {
    generateSourcesIfNecessary(event)

    if (DocumentUtils.isJavaFile(event.file.toPath())) {
      getWorkspace()?.findModuleForFile(event.file, false)?.let {
        val sourceRoot = it.findSourceRoot(event.file) ?: return@let

        // add the source node entry
        it.compileJavaSourceClasses.append(event.file.toPath(), sourceRoot)
      }
    }
  }

  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun onFileDeleted(event: FileDeletionEvent) {
    generateSourcesIfNecessary(event)

    // Remove the source node entry
    // Do not check for Java file DocumentUtils.isJavaFile(...) as it checks for file existence as
    // well. As the file is already deleted, it will always return false
    if (event.file.extension == "java") {
      getWorkspace()
          ?.findModuleForFile(event.file, false)
          ?.compileJavaSourceClasses
          ?.findSource(event.file.toPath())
          ?.let { it.parent?.removeChild(it) }
    }
  }

  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun onFileRenamed(event: FileRenameEvent) {
    generateSourcesIfNecessary(event)

    // Do not check for Java file DocumentUtils.isJavaFile(...) as it checks for file existence as
    // well. As the file is already renamed to another filename, it will always return false
    if (event.file.extension == "java") {
      // remove the source node entry
      getWorkspace()
          ?.findModuleForFile(event.file, false)
          ?.compileJavaSourceClasses
          ?.findSource(event.file.toPath())
          ?.let { it.parent?.removeChild(it) }
    }

    if (DocumentUtils.isJavaFile(event.newFile.toPath())) {
      getWorkspace()?.findModuleForFile(event.newFile, false)?.let {
        val sourceRoot = it.findSourceRoot(event.newFile) ?: return@let
        // add the new source node entry
        it.compileJavaSourceClasses.append(event.newFile.toPath(), sourceRoot)
      }
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(ProjectManagerImpl::class.java)

    @JvmStatic
    fun getInstance(): ProjectManagerImpl = IProjectManager.getInstance() as ProjectManagerImpl
  }
}
import com.itsaky.androidide.tooling.api.IAndroidProject
