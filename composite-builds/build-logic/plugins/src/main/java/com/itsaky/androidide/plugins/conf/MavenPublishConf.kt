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

package com.itsaky.androidide.plugins.conf

import com.itsaky.androidide.build.config.ProjectConfig
import com.itsaky.androidide.build.config.publishingVersion
import com.vanniktech.maven.publish.AndroidMultiVariantLibrary
import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * -------------------------------------------------------------------------------------------------
 * 文件名：ProjectPublishConfig.kt
 * 用途与功能：
 *   提供统一的 Maven 发布配置扩展（`configureMavenPublish`）。
 *   适配 AGP 9.4.1、Kotlin 2.4.10+ 及高版本 vanniktech maven publish 插件，
 *   支持多模块、Android 库、Java 库及 Gradle 插件的自动化元数据、POM、签名及本地构建仓库发布。
 * 工作流程线路图：
 *   1. 检查是否应用了 maven-publish-base 插件。
 *   2. 注册跨模块测试依赖关系挂钩（确保测试前自动发布依赖）。
 *   3. 配置 Maven 中央仓库元数据（POM, SCM, Developers, Licenses）。
 *   4. 根据当前模块应用的具体插件（AndroidLibrary / GradlePlugin / JavaLibrary）动态适配发布策略。
 * 上下文关系与父类：
 *   作为 [Project] 的扩展函数运行，由各个子模块的 build.gradle.kts 按需调用。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 */

private val projectsRequiringMavenLocalForTests = setOf(":tooling:plugin")
private val mavenLocalRepos = ConcurrentHashMap<String, String>()
private var isProjectsEvaluatedHookRegistered = false

/**
 * -------------------------------------------------------------------------------------------------
 * 函数名：configureMavenPublish
 * 用途与功能：
 *   [Project] 的核心扩展函数，为应用了 Maven Publish 插件的项目执行全套标准化配置。
 * -------------------------------------------------------------------------------------------------
 */
@Suppress("UnstableApiUsage")
fun Project.configureMavenPublish() {
  assert(plugins.hasPlugin("com.vanniktech.maven.publish.base")) {
    "${javaClass.simpleName} can only be applied to maven publish projects."
  }

  // 确保全局仅注册一次跨模块测试依赖关系，避免多个发布模块重复触发挂接
  registerCrossProjectTestDependenciesOnce(rootProject)

  afterEvaluate {
    if (project.description.isNullOrBlank()) {
      throw GradleException("Project ${project.path} must have a description")
    }
  }

  configure<MavenPublishBaseExtension> {
    project.configureMavenLocal()

    pom {
      name.set(project.name)
      description.set(project.provider { project.description ?: "" })
      inceptionYear.set("2021")
      url.set(ProjectConfig.REPO_URL)
      licenses {
        license {
          name.set("The GNU General Public License, v3.0")
          url.set("https://www.gnu.org/licenses/gpl-3.0.en.html")
          distribution.set("https://www.gnu.org/licenses/gpl-3.0.en.html")
        }
      }

      developers {
        developer {
          id.set("androidide")
          name.set("AndroidIDE")
          url.set(ProjectConfig.PROJECT_SITE)
        }
      }

      scm {
        url.set(ProjectConfig.REPO_URL)
        connection.set(ProjectConfig.SCM_GIT)
        developerConnection.set(ProjectConfig.SCM_SSH)
      }
    }

    coordinates(project.group.toString(), project.name, project.publishingVersion)
    
    // 兼容高版本 vanniktech 插件的 SonatypeHost 配置
    runCatching {
      publishToMavenCentral(SonatypeHost.S01)
    }.onFailure {
      // 适配新版插件如果移除了 SonatypeHost 或改用中央门户的情况
      logger.debug("SonatypeHost S01 configuration bypassed or handled by modern defaults: ${it.message}")
    }
    
    signAllPublications()

    // 采用 plugins.withId 响应式监听，消除外部 build.gradle 中插件应用顺序的影响
    project.plugins.withId("com.android.library") {
      configure(AndroidMultiVariantLibrary())
    }
    project.plugins.withId("java-gradle-plugin") {
      configure(GradlePlugin(javadocJar = JavadocJar.Javadoc()))
    }
    project.plugins.withId("java-library") {
      // 避免 java-gradle-plugin 与 android.library 也同时携带 java-library 时被重复/错误覆盖
      if (!project.plugins.hasPlugin("java-gradle-plugin") && !project.plugins.hasPlugin("com.android.library")) {
        configure(JavaLibrary(javadocJar = JavadocJar.Javadoc()))
      }
    }
  }
}

/**
 * -------------------------------------------------------------------------------------------------
 * 函数名：configureMavenLocal
 * 用途与功能：
 *   为模块配置本地构建 Maven 仓库路径、清理任务，以及在特定测试任务执行前自动打包发布到本地仓库。
 * -------------------------------------------------------------------------------------------------
 */
private fun Project.configureMavenLocal() {
  val mavenLocalPath = layout.buildDirectory.dir("maven-local")
  mavenLocalRepos[project.path] = mavenLocalPath.get().asFile.absolutePath

  extensions.findByType(PublishingExtension::class.java)?.apply {
    repositories {
      maven {
        name = "buildMavenLocal"
        url = uri(mavenLocalPath)
      }
    }
  }

  val deleteBuildMavenLocalTask = project.tasks.register("deleteBuildMavenLocal", Delete::class.java) {
    delete(mavenLocalPath)
  }

  // 针对被依赖测试的模块，配置运行测试前先写入 repos.txt
  if (project.path in projectsRequiringMavenLocalForTests) {
    tasks.withType<Test>().configureEach {
      dependsOn(tasks.matching { it.name == "publishAllPublicationsToBuildMavenLocalRepository" })
      doFirst {
        val file = mavenLocalPath.get().file("repos.txt").asFile
        file.parentFile.mkdirs()
        file.writeText(mavenLocalRepos.values.joinToString(separator = File.pathSeparator))
      }
    }
  }

  // 惰性挂载任务依赖
  tasks.matching { it.name == "publishAllPublicationsToBuildMavenLocalRepository" }.configureEach {
    dependsOn(deleteBuildMavenLocalTask)
  }
}

/**
 * -------------------------------------------------------------------------------------------------
 * 函数名：registerCrossProjectTestDependenciesOnce
 * 用途与功能：
 *   在根工程评估完成后，全局仅执行一次跨模块测试依赖关系挂钩，
 *   保证所有测试任务能够正确等待其依赖的发布任务完成。
 * -------------------------------------------------------------------------------------------------
 */
private fun Project.registerCrossProjectTestDependenciesOnce(rootProject: Project) {
  if (isProjectsEvaluatedHookRegistered) return
  isProjectsEvaluatedHookRegistered = true

  gradle.projectsEvaluated {
    for (path in projectsRequiringMavenLocalForTests) {
      val testProject = rootProject.findProject(path) ?: continue
      testProject.tasks.withType<Test>().configureEach {
        for (repoProjectPath in mavenLocalRepos.keys) {
          val repoProject = rootProject.findProject(repoProjectPath) ?: continue
          val publishTask = repoProject.tasks.matching {
            it.name == "publishAllPublicationsToBuildMavenLocalRepository"
          }
          dependsOn(publishTask)
        }
      }
    }
  }
}