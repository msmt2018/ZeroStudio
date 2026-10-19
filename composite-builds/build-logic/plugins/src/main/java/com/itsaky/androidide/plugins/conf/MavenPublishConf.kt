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

private val projectsRequiringMavenLocalForTests = setOf(":tooling:plugin")
private val mavenLocalRepos = ConcurrentHashMap<String, String>()
private var isProjectsEvaluatedHookRegistered = false

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
    publishToMavenCentral(SonatypeHost.S01)
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

  // 惰性挂载任务依赖，替代脆弱的 afterEvaluate { tasks.named(...) }
  tasks.matching { it.name == "publishAllPublicationsToBuildMavenLocalRepository" }.configureEach {
    dependsOn(deleteBuildMavenLocalTask)
  }
}

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