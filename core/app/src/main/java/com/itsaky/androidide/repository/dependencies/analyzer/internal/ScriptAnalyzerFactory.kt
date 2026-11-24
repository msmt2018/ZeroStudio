/*
 * @author android_zero
 * 包名：com.itsaky.androidide.repository.dependencies.analyzer.internal
 * 用途：构建脚本解析器的通用接口与工厂。
 */
package com.itsaky.androidide.repository.dependencies.analyzer.internal

import com.itsaky.androidide.repository.dependencies.models.datas.*
import com.itsaky.androidide.repository.dependencies.models.enums.*
import com.itsaky.androidide.repository.dependencies.models.interfaces.*
import java.io.File

interface ScriptAnalyzer {
  /**
   * 分析指定的 Gradle 脚本文件
   *
   * @param file build.gradle 或 build.gradle.kts 文件
   * @return 提取出的仓库列表与依赖声明列表
   */
  fun analyze(file: File): Pair<List<ScopedRepositoryInfo>, List<ScopedDependencyInfo>>
}

object ScriptAnalyzerFactory {
  fun create(file: File): ScriptAnalyzer {
    return if (file.name.endsWith(".kts")) {
      UnsupportedKotlinDslAnalyzer
    } else {
      GroovyAstAnalyzer()
    }
  }
}

private object UnsupportedKotlinDslAnalyzer : ScriptAnalyzer {
  override fun analyze(file: File) = emptyList<ScopedRepositoryInfo>() to emptyList<ScopedDependencyInfo>()
}
