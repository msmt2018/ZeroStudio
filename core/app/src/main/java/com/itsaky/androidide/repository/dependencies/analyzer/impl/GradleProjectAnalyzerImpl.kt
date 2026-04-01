/*
 * @author android_zero
 */
package com.itsaky.androidide.repository.dependencies.analyzer.impl

import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.repository.dependencies.analyzer.ProjectAnalyzer
import com.itsaky.androidide.repository.dependencies.analyzer.internal.*
import com.itsaky.androidide.repository.dependencies.analyzer.network.MavenMetadataFetcher
import com.itsaky.androidide.repository.dependencies.models.datas.*
import com.itsaky.androidide.repository.dependencies.models.enums.RepositoryType
import com.itsaky.androidide.repository.dependencies.models.interfaces.DependencyInfo
import com.itsaky.androidide.repository.dependencies.models.interfaces.RepositoryInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/** <h1>Gradle 项目依赖与仓库分析器 - 最终实现</h1> */
class GradleProjectAnalyzerImpl : ProjectAnalyzer {

  private val linker = DependencyLinker()
  private val tomlParser = TomlCatalogParser()

  override suspend fun extractRepositories(projectDir: File): List<ScopedRepositoryInfo> =
      withContext(Dispatchers.IO) {
        val repos = mutableListOf<ScopedRepositoryInfo>()
        val workspace =
            IProjectManager.getInstance().getWorkspace() ?: return@withContext emptyList()

        //  扫描根项目和所有子项目的构建脚本
        val allProjects = listOfNotNull(workspace.getRootProject()) + workspace.getSubProjects()
        allProjects.forEach { project ->
          val scriptFile = project.buildScript
          if (scriptFile != null && scriptFile.exists()) {
            val analyzer = ScriptAnalyzerFactory.create(scriptFile)
            val (scriptRepos, _) = analyzer.analyze(scriptFile)
            repos.addAll(scriptRepos)
          }
        }

        // 扫描 settings.gradle (用于 pluginManagement)
        val settingsFile =
            File(projectDir, "settings.gradle").takeIf { it.exists() }
                ?: File(projectDir, "settings.gradle.kts").takeIf { it.exists() }

        if (settingsFile != null) {
          val analyzer = ScriptAnalyzerFactory.create(settingsFile)
          val (scriptRepos, _) = analyzer.analyze(settingsFile)
          repos.addAll(scriptRepos.map { it.copy(isPluginRepository = true) })
        }

        // 添加默认兜底仓库
        repos.add(
            ScopedRepositoryInfo(
                "google",
                "https://dl.google.com/dl/android/maven2/",
                RepositoryType.GOOGLE,
                File(projectDir, "build.gradle"),
            )
        )
        repos.add(
            ScopedRepositoryInfo(
                "gradle",
                "https://plugins.gradle.org/m2",
                RepositoryType.MAVEN_CENTRAL,
                File(projectDir, "build.gradle"),
            )
        )

        repos.add(
            ScopedRepositoryInfo(
                "mavenCentral",
                "https://repo1.maven.org/maven2/",
                RepositoryType.MAVEN_CENTRAL,
                File(projectDir, "build.gradle"),
            )
        )

        return@withContext repos.distinctBy { it.url }
      }

  override suspend fun extractDependencies(projectDir: File): List<DependencyInfo> =
      withContext(Dispatchers.IO) {
        val workspace =
            IProjectManager.getInstance().getWorkspace()
                ?: return@withContext emptyList<DependencyInfo>()
        val allRawDependencies = mutableListOf<ScopedDependencyInfo>()
        val catalogMap = mutableMapOf<String, VersionCatalog>()

        // 分析 settings.gradle 获取所有版本目录文件
        val settingsAnalyzer = SettingsDslAnalyzer(projectDir)
        val catalogFilesMap = settingsAnalyzer.extractCatalogs()

        // 解析所有目录文件
        catalogFilesMap.forEach { (alias, file) -> catalogMap[alias] = tomlParser.parse(file) }

        //  遍历所有模块，提取原始依赖声明
        val modules = workspace.getSubProjects()
        modules.forEach { module ->
          val buildScript = module.buildScript
          if (buildScript != null && buildScript.exists()) {
            val analyzer = ScriptAnalyzerFactory.create(buildScript)
            val (_, scriptDeps) = analyzer.analyze(buildScript)
            allRawDependencies.addAll(scriptDeps)
          }
        }

        // 链接
        val linkedDependencies = linker.link(allRawDependencies, catalogMap)

        return@withContext linkedDependencies.distinctBy { it.gav }
      }

  override suspend fun checkUpdates(
      dependencies: List<DependencyInfo>,
      repositories: List<RepositoryInfo>,
  ): List<UpdateReport> =
      withContext(Dispatchers.IO) {

        // 过滤出 ScopedDependencyInfo 类型
        val distinctDeps =
            dependencies.filterIsInstance<ScopedDependencyInfo>().distinctBy { it.gav }

        val tasks = distinctDeps.map { dep ->
          async {
            var bestLatest: String? = null
            val allVersions = mutableListOf<String>()
            val gavPath = "${dep.groupId.replace('.', '/')}/${dep.artifactId}"

            for (repo in repositories) {
              val metadata = MavenMetadataFetcher.fetchMetadata(gavPath, repo.url)
              if (metadata != null) {
                val stableVersions = metadata.versions.filter(::isStableVersion)
                allVersions.addAll(stableVersions)

                val remoteLatest =
                    stableVersions.maxWithOrNull(SemanticVersionComparator)
                        ?: metadata.bestLatest?.takeIf(::isStableVersion)
                        ?: metadata.release?.takeIf(::isStableVersion)
                        ?: metadata.latest?.takeIf(::isStableVersion)

                if (remoteLatest != null && isNewerSemanticVersion(remoteLatest, dep.version)) {
                  bestLatest =
                      listOfNotNull(bestLatest, remoteLatest).maxWithOrNull(
                          SemanticVersionComparator
                      )
                }
              }
            }

            if (bestLatest != null && isNewerSemanticVersion(bestLatest!!, dep.version)) {
              UpdateReport(
                  dep,
                  bestLatest!!,
                  allVersions.distinct().sortedWith(SemanticVersionComparator).reversed(),
              )
            } else {
              null
            }
          }
        }

        return@withContext tasks.awaitAll().filterNotNull()
      }

  private object SemanticVersionComparator : Comparator<String> {
    override fun compare(v1: String, v2: String): Int {
      val parts1 = v1.split(Regex("[.-]")).mapNotNull { it.toIntOrNull() }
      val parts2 = v2.split(Regex("[.-]")).mapNotNull { it.toIntOrNull() }
      val length = maxOf(parts1.size, parts2.size)
      for (i in 0 until length) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) return p1 - p2
      }
      return 0
    }
  }

  private fun isStableVersion(version: String): Boolean {
    val value = version.lowercase()
    return !value.contains("alpha") &&
        !value.contains("beta") &&
        !value.contains("rc") &&
        !value.contains("snapshot")
  }

  private fun isNewerSemanticVersion(latest: String, current: String): Boolean {
    if (latest == current) return false
    return SemanticVersionComparator.compare(latest, current) > 0
  }
}
