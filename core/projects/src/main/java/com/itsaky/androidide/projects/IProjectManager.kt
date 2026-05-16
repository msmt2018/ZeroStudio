// FILE: core/projects/src/main/java/com/itsaky/androidide/projects/IProjectManager.kt
/*
 *  This file is part of AndroidIDE.
 *  @author Akash Yadav
 *  @author android_zero
 */

package com.itsaky.androidide.projects

import androidx.annotation.RestrictTo
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.android.AndroidModule
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.utils.ServiceLoader
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ProjectFlowBus {
  private val _events = MutableSharedFlow<Any>(extraBufferCapacity = 64)
  val events = _events.asSharedFlow()

  fun post(event: Any) = _events.tryEmit(event)
}

interface IProjectManager {

  companion object {
    private var projectManager: IProjectManager? = null

    @JvmStatic
    fun getInstance(): IProjectManager {
      return projectManager
          ?: ServiceLoader.load(IProjectManager::class.java).findFirstOrThrow().also {
            projectManager = it
          }
    }
  }

  val projectDirPath: String
    get() = projectDir.path

  val projectDir: File
  val projectSyncIssues: ProjectSyncIssues?

  fun openProject(directory: File)

  fun openProject(path: String) = openProject(File(path))

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
  suspend fun setupProject(
      project: IProject? = Lookup.getDefault().lookup(BuildService.KEY_PROJECT_PROXY)
  )

  suspend fun isGradleSyncNeeded(projectDir: File): Boolean

  fun getWorkspace(): IWorkspace?

  fun requireWorkspace(): IWorkspace = getWorkspace() ?: throw IWorkspace.NotConfiguredException()

  fun getAndroidModules(): List<AndroidModule>

  fun getAndroidAppModules(): List<AndroidModule>

  fun getAndroidLibraryModules(): List<AndroidModule>

  fun findModuleForFile(file: File, checkExistence: Boolean = true): ModuleProject?

  fun findModuleForFile(file: Path, checkExistence: Boolean = true): ModuleProject? =
      findModuleForFile(file.toFile(), checkExistence)

  fun notifyFileCreated(file: File)

  fun notifyFileDeleted(file: File)

  fun notifyFileRenamed(from: File, to: File)

  fun destroy()
}

fun IProjectManager.isPluginProject(): Boolean {
  val cached =
      (this as? com.itsaky.androidide.projects.internal.ProjectManagerImpl)?.pluginProjectCached
  if (cached != null) return cached

  val result =
      File(projectDir, com.itsaky.androidide.utils.Environment.PLUGIN_API_JAR_RELATIVE_PATH)
          .exists()
  if (this is com.itsaky.androidide.projects.internal.ProjectManagerImpl) {
    this.pluginProjectCached = result
  }
  return result
}
