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

package com.itsaky.androidide.lsp.kotlin

import com.itsaky.androidide.projects.IWorkspace
import com.itsaky.androidide.projects.android.AndroidModule
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 桥接器：Kotlin 和 Java 编译器的中间层接口。 允许从 Kotlin 补全中引用尚未导入的 Java 类/SDK框架类。
 *
 * @author android_zero
 */
class KotlinJavaCompilerBridge(private val workspace: IWorkspace) {

  companion object {
    private val log = LoggerFactory.getLogger(KotlinJavaCompilerBridge::class.java)
  }

  // 缓存工作区类字典 (Group: SimpleName -> ClassInfo) 以提高查询速度
  private val classDictionary = ConcurrentHashMap<String, ClassInfo>()

  init {
    initializeCompiler()
    buildDictionaryAsync()
  }

  private fun initializeCompiler() {
    try {
      val mainModule =
          workspace.getSubProjects().filterIsInstance<AndroidModule>().firstOrNull {
            it.isApplication
          } ?: workspace.getSubProjects().filterIsInstance<AndroidModule>().firstOrNull()

      if (mainModule != null) {
        // bridge currently indexes from workspace class metadata.
      }
    } catch (e: Exception) {
      log.error("Failed to initialize Java compiler bridge", e)
    }
  }

  /**
   * 异步从当前打开项目的所有子模块类路径建立类字典。
   * 此举能让 Kotlin 代码不仅能访问内部源码类，也能无缝联想到 Android SDK 库（如 Activity 等）和导入的第三方 JAR/AAR。
   */
  private fun buildDictionaryAsync() {
    Thread {
      try {
        workspace.getSubProjects().forEach { project ->
          if (project is com.itsaky.androidide.projects.ModuleProject) {
            // 获取项目源码的所有 Class 名称
            val srcClasses = project.compileJavaSourceClasses.allClassNames()
            // 获取项目引用的所有第三方 Class 名称 (包含 SDK 和依赖库)
            val cpClasses = project.compileClasspathClasses.allClassNames()

            val allNames = srcClasses + cpClasses
            allNames.forEach { fqn ->
              val simpleName = fqn.substringAfterLast('.')
              if (simpleName.isNotEmpty()) {
                classDictionary[simpleName.lowercase()] = ClassInfo(
                    simpleName = simpleName,
                    fullyQualifiedName = fqn,
                    packageName = fqn.substringBeforeLast('.', "")
                )
              }
            }
          }
        }
        log.info("KotlinJavaCompilerBridge indexing complete. Size: ${classDictionary.size}")
      } catch (e: Exception) {
        log.warn("Failed to build class dictionary for Kotlin bridge", e)
      }
    }.apply { name = "Kotlin-Java-Bridge-Indexer" }.start()
  }

  /**
   * 获取由编译器分析提供的可用公开类 (备用兼容)
   */
  fun getAllAvailableClasses(): List<String> {
    return try {
      classDictionary.values.map { it.fullyQualifiedName }
    } catch (e: Exception) {
      log.error("Failed to get available classes", e)
      emptyList()
    }
  }

  /**
   * 根据用户输入的前缀快速检索最佳匹配（最少 2 个字符触发）。
   * 用于向 KotlinCompletionConverter 提供 Java 层的类对象补全项并自动附加 Import 插入。
   * 
   * @param prefix 当前用户的输入前缀（例如 "ViewM"）
   * @return 匹配的 [ClassInfo] 集合
   */
  fun findClassesByPrefix(prefix: String): List<ClassInfo> {
    if (prefix.length < 2) return emptyList()
    val lowerPrefix = prefix.lowercase()
    
    return classDictionary.entries
        .filter { it.key.startsWith(lowerPrefix) }
        .map { it.value }
        .sortedBy { it.simpleName.length }
        .take(20) // 返回最优匹配的前 20 条记录防止列表膨胀与卡顿
  }

  /**
   * 用于传递给上层补全转换器的基础数据模型。
   */
  data class ClassInfo(
      val simpleName: String,
      val fullyQualifiedName: String,
      val packageName: String,
  )
}
