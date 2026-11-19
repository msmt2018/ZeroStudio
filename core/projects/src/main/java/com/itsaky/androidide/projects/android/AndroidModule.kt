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

package com.itsaky.androidide.projects.android

import com.android.SdkConstants
import com.android.aaptcompiler.AaptResourceType
import com.android.builder.model.v2.ide.LibraryType.ANDROID_LIBRARY
import com.android.builder.model.v2.ide.LibraryType.JAVA_LIBRARY
import com.android.builder.model.v2.ide.LibraryType.PROJECT
import com.android.builder.model.v2.ide.ProjectType
import com.itsaky.androidide.builder.model.DefaultAndroidGradlePluginProjectFlags
import com.itsaky.androidide.builder.model.DefaultJavaCompileOptions
import com.itsaky.androidide.builder.model.DefaultLibrary
import com.itsaky.androidide.builder.model.DefaultSourceSetContainer
import com.itsaky.androidide.builder.model.DefaultViewBindingOptions
import com.itsaky.androidide.builder.model.UNKNOWN_PACKAGE
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.IWorkspace
import com.itsaky.androidide.projects.ModuleProject
import com.itsaky.androidide.projects.classpath.VersionCatalogClasspathProvider
import com.itsaky.androidide.tooling.api.ProjectType.Android
import com.itsaky.androidide.tooling.api.models.BasicAndroidVariantMetadata
import com.itsaky.androidide.tooling.api.models.GradleTask
import com.itsaky.androidide.tooling.api.util.findPackageName
import com.itsaky.androidide.utils.withStopWatch
import com.itsaky.androidide.xml.res.IResourceTable
import com.itsaky.androidide.xml.resources.ResourceTableRegistry
import com.itsaky.androidide.xml.versions.ApiVersions
import com.itsaky.androidide.xml.versions.ApiVersionsRegistry
import com.itsaky.androidide.xml.widgets.WidgetTable
import com.itsaky.androidide.xml.widgets.WidgetTableRegistry
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory

/**
 * A [GradleProject] model implementation for Android modules which is exposed to other modules and
 * provides additional helper methods.
 *
 * @author Akash Yadav
 * @author android_zero (Contribution: Bug fixes, Refactoring, Compose Preview support)
 */
open class AndroidModule(
    name: String,
    description: String,
    path: String,
    projectDir: File,
    buildDir: File,
    buildScript: File,
    tasks: List<GradleTask>,
    val resourcePrefix: String?,
    val namespace: String?,
    val androidTestNamespace: String?,
    val testFixtureNamespace: String?,
    val projectType: ProjectType,
    val mainSourceSet: DefaultSourceSetContainer?,
    val flags: DefaultAndroidGradlePluginProjectFlags,
    override val compilerSettings: DefaultJavaCompileOptions,
    val viewBindingOptions: DefaultViewBindingOptions,
    val bootClassPaths: Collection<File>,
    val libraries: Set<String>,
    val libraryMap: Map<String, DefaultLibrary>,
    val lintCheckJars: List<File>,
    val variants: List<BasicAndroidVariantMetadata> = listOf(),
    val configuredVariant: BasicAndroidVariantMetadata?,
    val classesJar: File?,
) : ModuleProject(name, description, path, projectDir, buildDir, buildScript, tasks) {

  /** Whether this project is an Android library project. */
  val isLibrary: Boolean
    get() = this.projectType == ProjectType.LIBRARY

  /** Whether this project is an Android application project. */
  val isApplication: Boolean
    get() = this.projectType == ProjectType.APPLICATION

  companion object {
    private val log = LoggerFactory.getLogger(AndroidModule::class.java)
  }

  init {
    type = Android
  }

  fun getGeneratedJar(): File {
    return classesJar ?: File("does-not-exist.jar")
  }

  override fun getClassPaths(): Set<File> {
    return getModuleClasspaths()
  }

  fun getVariant(name: String): BasicAndroidVariantMetadata? {
    return this.variants.firstOrNull { it.name == name }
  }

  fun getResourceDirectories(): Set<File> {
    if (mainSourceSet == null) {
      log.error("No main source set found in application module: {}", name)
      return emptySet()
    }

    val dirs = mutableSetOf<File>()
    if (mainSourceSet.sourceProvider.resDirectories != null) {
      dirs.addAll(mainSourceSet.sourceProvider.resDirectories!!)
    }

    val dependencies = getCompileModuleProjects().filterIsInstance<AndroidModule>()

    for (dependency in dependencies) {
      dirs.addAll(dependency.getResourceDirectories())
    }

    return dirs
  }

  override fun getSourceDirectories(): Set<File> {
    if (mainSourceSet == null) {
      log.warn(
          "No main source set is available for project {}. Cannot get source directories.",
          name,
      )
      return mutableSetOf()
    }

    // src/main/java
    val sources = mainSourceSet.sourceProvider.javaDirectories.toMutableSet()

    // src/main/kotlin
    sources.addAll(mainSourceSet.sourceProvider.kotlinDirectories)

    // build/generated/**
    val selectedVariant = getSelectedVariant()
    if (selectedVariant != null) {
      sources.addAll(selectedVariant.mainArtifact.generatedSourceFolders)
    }
    return sources
  }

  override fun getCompileSourceDirectories(): Set<File> {
    val dirs = mutableSetOf<File>()
    dirs.addAll(getSourceDirectories())
    getCompileModuleProjects().forEach { dirs.addAll(it.getSourceDirectories()) }
    return dirs
  }

  override fun getModuleClasspaths(): Set<File> {
    return mutableSetOf<File>().apply {
      add(getGeneratedJar())
      addAll(getSelectedVariant()?.mainArtifact?.classJars ?: emptyList())
    }
  }

  override fun getCompileClasspaths(): Set<File> {
    val project = IProjectManager.getInstance().getWorkspace() ?: return emptySet()
    val result = mutableSetOf<File>()
    result.addAll(getModuleClasspaths())

    val modelDepsCount = result.size
    collectLibraries(project, this.libraries, result)
    log.debug("AGP model provided {} dependencies", result.size - modelDepsCount)

    // Build a map of already loaded dependencies by group:name
    val existingDeps = buildDependencyMap(result)
    log.debug("Existing dependency map contains {} entries", existingDeps.size)

    // Get catalog dependencies that don't conflict
    val catalogDeps = getVersionCatalogDependencies(existingDeps)
    if (catalogDeps.isNotEmpty()) {
      val beforeCatalog = result.size
      result.addAll(catalogDeps)
      log.info(
          "Added {} new dependencies from version catalog (total now: {})",
          result.size - beforeCatalog,
          result.size,
      )
    }

    return result
  }

  /**
   * @return A set of File objects pointing to directories and JARs containing intermediate build
   *   artifacts.
   * @author android_zero
   * @description [Compose Preview Support] Gets intermediate classpaths like compiled .class files
   *   and R.jar, which are essential for Compose Preview to dynamically load and render
   *   user-defined Composable functions from the source code.
   */
  override fun getIntermediateClasspaths(): Set<File> {
    val result = mutableSetOf<File>()
    val variant = getSelectedVariant()?.name ?: "debug"
    val buildDirectory = buildDir // Inherited from ModuleProject

    // Path to compiled Kotlin classes
    val kotlinClasses = File(buildDirectory, "tmp/kotlin-classes/$variant")
    if (kotlinClasses.exists()) {
      result.add(kotlinClasses)
    }

    // Path to compiled Java classes
    val javaClassesDir = File(buildDirectory, "intermediates/javac/$variant")
    if (javaClassesDir.exists()) {
      javaClassesDir
          .walkTopDown()
          .filter { it.name == "classes" && it.isDirectory }
          .forEach { result.add(it) }
    }

    // Path to the R class JAR
    val rClassJar =
        File(
            buildDirectory,
            "intermediates/compile_and_runtime_not_namespaced_r_class_jar/$variant/R.jar",
        )
    if (rClassJar.exists()) {
      result.add(rClassJar)
    }

    return result
  }

  /**
   * @return A set of File objects pointing to the .dex files.
   * @author android_zero
   * @description [Compose Preview & Layout Inspector Support] Retrieves the paths to the generated
   *   DEX files for the current variant. These files are crucial for dynamic class loading
   *   mechanisms used by tools like Layout Inspector and Compose Preview to render live UI
   *   components.
   */
  override fun getRuntimeDexFiles(): Set<File> {
    val result = mutableSetOf<File>()
    val variant = getSelectedVariant()?.name ?: "debug"
    val buildDirectory = buildDir

    log.info(
        "getRuntimeDexFiles: Searching in buildDir='{}' for variant='{}'",
        buildDirectory.absolutePath,
        variant,
    )

    // Standard DEX output directory used by AGP
    val dexDir = File(buildDirectory, "intermediates/dex/$variant")
    if (dexDir.exists()) {
      dexDir
          .walkTopDown()
          .filter { it.isFile && it.extension == "dex" }
          .forEach {
            log.debug("Found DEX file: {}", it.absolutePath)
            result.add(it)
          }
    }

    // Merged project DEX archive directory
    val mergeProjectDexDir = File(buildDirectory, "intermediates/project_dex_archive/$variant")
    if (mergeProjectDexDir.exists()) {
      mergeProjectDexDir
          .walkTopDown()
          .filter { it.isFile && it.extension == "dex" }
          .forEach {
            log.debug("Found DEX file in project_dex_archive: {}", it.absolutePath)
            result.add(it)
          }
    }

    log.info("Total DEX files found for module '{}': {}", name, result.size)
    return result
  }

  /**
   * Build a map of existing dependencies from classpath files. Key format: "group:name", Value:
   * File path
   */
  private fun buildDependencyMap(classpath: Set<File>): Map<String, File> {
    val depMap = mutableMapOf<String, File>()
    val gradleCachePattern =
        """.gradle/caches/modules-2/files-2.1/([^/]+)/([^/]+)/([^/]+)/""".toRegex()

    for (file in classpath) {
      if (!file.exists()) continue

      val path = file.absolutePath
      val match = gradleCachePattern.find(path)

      if (match != null) {
        val group = match.groupValues[1]
        val name = match.groupValues[2]
        val key = "$group:$name"

        if (!depMap.containsKey(key)) {
          depMap[key] = file
          log.trace("Mapped dependency: {} -> version {}", key, match.groupValues[3])
        }
      }
    }

    return depMap
  }

  /** [FIXED] Get dependencies from version catalog that don't conflict with existing ones. */
  private fun getVersionCatalogDependencies(existingDeps: Map<String, File>): Set<File> {
    // The `projectDir` of this AndroidModule is the module's directory.
    // The new VersionCatalogClasspathProvider handles finding the root project dir itself.
    return try {
      log.debug("Resolving version catalog dependencies for module: {}", path)

      // Directly call the static (companion object) method with the module's `projectDir`.
      val jars = VersionCatalogClasspathProvider.getClasspathFromCatalog(projectDir, existingDeps)

      log.info("Resolved {} JARs from version catalog for module: {}", jars.size, path)
      jars
    } catch (e: Exception) {
      log.error("Failed to resolve version catalog dependencies for module {}", path, e)
      emptySet()
    }
  }

  private fun collectLibraries(
      root: IWorkspace,
      libraries: Set<String>,
      result: MutableSet<File>,
      visited: MutableSet<String> = mutableSetOf(),
  ) {
    for (library in libraries) {
      if (!visited.add(library)) continue
      val lib = this.libraryMap[library] ?: continue
      if (lib.type == PROJECT) {
        val module = root.findProject(lib.projectInfo!!.projectPath) ?: continue
        if (module !is ModuleProject) {
          continue
        }

        result.addAll(module.getCompileClasspaths())
      } else if (lib.type == ANDROID_LIBRARY) {
        result.addAll(lib.androidLibraryData!!.compileJarFiles)
      } else if (lib.type == JAVA_LIBRARY) {
        result.add(lib.artifact!!)
      }

      collectLibraries(root, lib.dependencies, result, visited)
    }
  }

  override fun getCompileModuleProjects(): List<ModuleProject> {
    val root = IProjectManager.getInstance().getWorkspace() ?: return emptyList()
    val result = mutableListOf<ModuleProject>()

    for (library in this.libraries) {
      val lib = this.libraryMap[library] ?: continue
      if (lib.type != PROJECT) {
        continue
      }

      val module = root.findProject(lib.projectInfo!!.projectPath) ?: continue
      if (module !is ModuleProject) {
        continue
      }

      result.add(module)
      result.addAll(module.getCompileModuleProjects())
    }

    return result
  }

  /**
   * Reads the resource files are creates the [com.android.aaptcompiler.ResourceTable] instances for
   * the corresponding resource directories.
   */
  suspend fun readResources() {
    withStopWatch("Read resources for module : $path") {
      val resourceReaderScope =
          CoroutineScope(Dispatchers.IO + CoroutineName("ResourceReader($path)"))

      val resourceFlow = flow {
        emit(getFrameworkResourceTable())
        emit(getResourceTable())
        emit(getDependencyResourceTables())
        emit(getApiVersions())
        emit(getWidgetTable())
      }

      val jobs = resourceFlow.map { result -> resourceReaderScope.async { result } }

      jobs.toList().awaitAll()
    }
  }

  /**
   * Get the [ApiVersions] instance for this module.
   *
   * @return The [ApiVersions] for this module.
   */
  fun getApiVersions(): ApiVersions? {
    val platformDir = getPlatformDir()
    if (platformDir != null) {
      return ApiVersionsRegistry.getInstance().forPlatformDir(platformDir)
    }

    return null
  }

  /**
   * Get the [WidgetTable] instance for this module.
   *
   * @return The [WidgetTable] for this module.
   */
  fun getWidgetTable(): WidgetTable? {
    val platformDir = getPlatformDir()
    if (platformDir != null) {
      return WidgetTableRegistry.getInstance().forPlatformDir(platformDir)
    }

    return null
  }

  /** Get the resource table for this module i.e. without resource tables for dependent modules. */
  fun getResourceTable(): IResourceTable? {
    val namespace = this.namespace ?: return null

    val resDirs = mainSourceSet?.sourceProvider?.resDirectories ?: return null
    return ResourceTableRegistry.getInstance().forPackage(namespace, *resDirs.toTypedArray())
  }

  /** Updates the resource table for this module. */
  fun updateResourceTable() {
    if (this.namespace == null) {
      return
    }

    CompletableFuture.runAsync {
      val tableRegistry = ResourceTableRegistry.getInstance()
      val resDirs = mainSourceSet?.sourceProvider?.resDirectories ?: return@runAsync
      tableRegistry.removeTable(this.namespace)
      tableRegistry.forPackage(this.namespace, *resDirs.toTypedArray())
    }
  }

  /**
   * Get the [IResourceTable] instance for this module's compile SDK.
   *
   * @return The [ApiVersions] for this module.
   */
  fun getFrameworkResourceTable(): IResourceTable? {
    val platformDir = getPlatformDir()
    if (platformDir != null) {
      return ResourceTableRegistry.getInstance().forPlatformDir(platformDir)
    }

    return null
  }

  /**
   * Get the resource tables for this module as well as it's dependent modules.
   *
   * @return The set of resource tables. Empty when project is not initalized.
   */
  fun getSourceResourceTables(): Set<IResourceTable> {
    val set = mutableSetOf(getResourceTable() ?: return emptySet())
    getCompileModuleProjects().filterIsInstance<AndroidModule>().forEach {
      it.getResourceTable()?.also { table -> set.add(table) }
    }
    return set
  }

  /** Get the resource tables for external dependencies (not local module project dependencies). */
  fun getDependencyResourceTables(): Set<IResourceTable> {
    return mutableSetOf<IResourceTable>().also {
      var deps: Int
      it.addAll(
          libraryMap.values
              .filter { library ->
                library.type == ANDROID_LIBRARY &&
                    library.androidLibraryData!!.resFolder.exists() &&
                    library.findPackageName() != UNKNOWN_PACKAGE
              }
              .also { libs -> deps = libs.size }
              .mapNotNull { library ->
                ResourceTableRegistry.getInstance().let { registry ->
                  registry.isLoggingEnabled = false
                  registry
                      .forPackage(
                          library.packageName,
                          library.androidLibraryData!!.resFolder,
                      )
                      .also { registry.isLoggingEnabled = true }
                }
              }
      )

      log.info("Created {} resource tables for {} dependencies of module '{}'", it.size, deps, path)
    }
  }

  /**
   * Checks all the resource tables from this module and returns if any of the tables contain
   * resources for the the given package.
   *
   * @param pck The package to look for.
   */
  fun findResourceTableForPackage(
      pck: String,
      hasGroup: AaptResourceType? = null,
  ): IResourceTable? {
    return findAllResourceTableForPackage(pck, hasGroup).let {
      if (it.isNotEmpty()) {
        return it.first()
      } else null
    }
  }

  /**
   * Checks all the resource tables from this module and returns if any of the tables contain
   * resources for the the given package.
   *
   * @param pck The package to look for.
   */
  fun findAllResourceTableForPackage(
      pck: String,
      hasGroup: AaptResourceType? = null,
  ): List<IResourceTable> {
    if (pck == SdkConstants.ANDROID_PKG) {
      return getFrameworkResourceTable()?.let { listOf(it) } ?: emptyList()
    }

    val tables: List<IResourceTable> =
        mutableListOf<IResourceTable>().apply {
          getResourceTable()?.let { add(it) }
          addAll(getSourceResourceTables())
          addAll(getDependencyResourceTables())
        }

    val result = mutableListOf<IResourceTable>()
    for (table in tables) {
      val resPck = table.findPackage(pck) ?: continue
      if (hasGroup == null) {
        result.add(table)
        continue
      }
      if (resPck.findGroup(hasGroup) != null) {
        result.add(table)
        continue
      }
    }

    return emptyList()
  }

  /**
   * Returns all the resource tables associated with this module (including the framework resource
   * table).
   *
   * @return The associated resource tables.
   */
  fun getAllResourceTables(): Set<IResourceTable> {
    return mutableSetOf<IResourceTable>().apply {
      getResourceTable()?.let { add(it) }
      getFrameworkResourceTable()?.let { add(it) }
      addAll(getSourceResourceTables())
      addAll(getDependencyResourceTables())
    }
  }

  /** Get the resource table for the attrs_manifest.xml file. */
  fun getManifestAttrTable(): IResourceTable? {
    val platform = getPlatformDir() ?: return null
    return ResourceTableRegistry.getInstance().getManifestAttrTable(platform)
  }

  /** @see ResourceTableRegistry.getActivityActions */
  fun getActivityActions(): List<String> {
    return ResourceTableRegistry.getInstance()
        .getActivityActions(getPlatformDir() ?: return emptyList())
  }

  /** @see ResourceTableRegistry.getBroadcastActions */
  fun getBroadcastActions(): List<String> {
    return ResourceTableRegistry.getInstance()
        .getBroadcastActions(getPlatformDir() ?: return emptyList())
  }

  /** @see ResourceTableRegistry.getServiceActions */
  fun getServiceActions(): List<String> {
    return ResourceTableRegistry.getInstance()
        .getServiceActions(getPlatformDir() ?: return emptyList())
  }

  /** @see ResourceTableRegistry.getCategories */
  fun getCategories(): List<String> {
    return ResourceTableRegistry.getInstance().getCategories(getPlatformDir() ?: return emptyList())
  }

  /** @see ResourceTableRegistry.getFeatures */
  fun getFeatures(): List<String> {
    return ResourceTableRegistry.getInstance().getFeatures(getPlatformDir() ?: return emptyList())
  }

  /**
   * Returns the build variant that is selected by the user. This may return `null` in some
   * misconfiguration scenarios.
   */
  fun getSelectedVariant(): BasicAndroidVariantMetadata? {
    val projectManager = IProjectManager.getInstance()

    val info =
        (projectManager.getWorkspace()?.getAndroidVariantSelections() ?: emptyMap())[this.path]
    if (info == null) {
      log.error("Failed to find selected build variant for module: '{}'", this.path)
      return null
    }

    val variant = this.getVariant(info.selectedVariant)
    if (variant == null) {
      log.error("Build variant with name '{}' not found.", info.selectedVariant)
      return null
    }

    return variant
  }

  /**
   * Get the Android SDK platform directory for this Android module.
   *
   * @return The Android SDK platform directory for this Android module, or `null` if none is found.
   */
  fun getPlatformDir() = bootClassPaths.firstOrNull { it.name == "android.jar" }?.parentFile
}
