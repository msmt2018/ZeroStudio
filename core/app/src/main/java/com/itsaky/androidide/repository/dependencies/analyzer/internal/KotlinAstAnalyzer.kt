/*
 * @author android_zero
 */
package com.itsaky.androidide.repository.dependencies.analyzer.internal

import com.itsaky.androidide.repository.dependencies.models.datas.*
import com.itsaky.androidide.repository.dependencies.models.enums.*
import com.itsaky.androidide.repository.dependencies.models.interfaces.*
import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.TSParser
import com.itsaky.androidide.treesitter.kotlin.TSLanguageKotlin
import java.io.File

class KotlinAstAnalyzer : ScriptAnalyzer {

  override fun analyze(file: File): Pair<List<ScopedRepositoryInfo>, List<ScopedDependencyInfo>> {
    val repos = mutableListOf<ScopedRepositoryInfo>()
    val deps = mutableListOf<ScopedDependencyInfo>()

    if (!file.exists()) return Pair(repos, deps)

    val text = file.readText()
    val versionVariables = extractVersionVariables(text)
    val parser = TSParser.create()
    parser.language = TSLanguageKotlin.getInstance()

    // 修复资源泄漏：使用 try-finally 确保 TSParser/TSTree 在异常时也能被关闭。
    // 修复前：parser/tree 仅在正常流程末尾手动 close，traverse() 抛异常时泄漏 native 资源。
    try {
      val tree = parser.parseString(null, text) ?: return Pair(repos, deps)
      tree.use { t ->
        val rootNode = t.rootNode

        fun traverse(node: TSNode, currentBlock: String?) {
          if (!node.canAccess()) return

          var nextBlock = currentBlock

          if (node.type == "call_expression") {
            val idNode = node.getChild(0)
            if (idNode != null && (idNode.type == "identifier" || idNode.type == "simple_identifier")) {
              val funcName = text.substring(idNode.startByte / 2, idNode.endByte / 2)

              if (
                  funcName == "dependencies" ||
                      funcName == "repositories" ||
                      funcName == "pluginManagement"
              ) {
                nextBlock = funcName
              } else if (currentBlock == "dependencies") {
                if (DependencyDslConfigurations.isDependencyConfiguration(funcName)) {
                  extractDependency(node, funcName, text, file, deps, versionVariables)
                }
              } else if (currentBlock == "repositories" || currentBlock == "pluginManagement") {
                extractRepository(node, funcName, text, file, repos)
              }
            }
          }

          for (i in 0 until node.childCount) {
            traverse(node.getChild(i), nextBlock)
          }
        }

        traverse(rootNode, null)
      }
    } finally {
      parser.close()
    }

    return Pair(repos, deps)
  }

  private fun extractDependency(
      callNode: TSNode,
      configName: String,
      text: String,
      file: File,
      deps: MutableList<ScopedDependencyInfo>,
      versionVariables: Map<String, Pair<String, TextRange>>,
  ) {
    var argsNode: TSNode? = null
    for (i in 0 until callNode.childCount) {
      val child = callNode.getChild(i)
      if (child.type == "value_arguments") {
        argsNode = child
        break
      }
    }

    if (argsNode == null || argsNode.childCount == 0) return

    for (i in 0 until argsNode.childCount) {
      val arg = argsNode.getChild(i)
      if (arg.type == "value_argument") {
        val exprNode = arg.getChild(0)
        if (exprNode != null) {
          if (exprNode.type == "string_literal") {
            val rawStr = text.substring(exprNode.startByte / 2, exprNode.endByte / 2)
            val cleanStr = rawStr.trim('\"')
            val parts = cleanStr.split(":")
            if (parts.size >= 3) {
              val rawVersion = parts[2]
              val variableName =
                  Regex("""^\$(\w+)$|^\$\{(\w+)}$""")
                      .matchEntire(rawVersion)
                      ?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }
              val variable = variableName?.let { versionVariables[it] }
              val version = variable?.first ?: rawVersion
              val versionRange =
                  variable?.second
                      ?: run {
                        val verStart = exprNode.startByte / 2 + rawStr.lastIndexOf(rawVersion)
                        TextRange(verStart, verStart + rawVersion.length)
                      }

              deps.add(
                  ScopedDependencyInfo(
                      configuration = configName,
                      groupId = parts[0],
                      artifactId = parts[1],
                      version = version,
                      declaredFile = file,
                      declarationType = DeclarationType.STRING_LITERAL,
                      // 修复：使用 versionDefinitionRange
                      versionDefinitionRange = versionRange,
                      statementTextRange = TextRange(callNode.startByte / 2, callNode.endByte / 2),
                  )
              )
            }
          } else if (exprNode.type == "navigation_expression") {
            val refStr = text.substring(exprNode.startByte / 2, exprNode.endByte / 2)
            if (refStr.startsWith("libs.")) {
              deps.add(
                  ScopedDependencyInfo(
                      configuration = configName,
                      groupId = "placeholder",
                      artifactId = "placeholder",
                      version = "placeholder",
                      declaredFile = file,
                      declarationType = DeclarationType.CATALOG_ACCESSOR,
                      versionReference = refStr.removePrefix("libs."),
                      statementTextRange = TextRange(callNode.startByte / 2, callNode.endByte / 2),
                  )
              )
            }
          }
        }
        break
      }
    }
  }

  private fun extractVersionVariables(text: String): Map<String, Pair<String, TextRange>> {
    val result = mutableMapOf<String, Pair<String, TextRange>>()
    val pattern =
        Regex(
            """(?m)^\s*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*["']([^"']+)["']\s*$"""
        )
    pattern.findAll(text).forEach { match ->
      val name = match.groupValues[1]
      val value = match.groupValues[2]
      val start = match.range.first + match.value.indexOf(value)
      result[name] = value to TextRange(start, start + value.length)
    }
    return result
  }

  private fun extractRepository(
      callNode: TSNode,
      funcName: String,
      text: String,
      file: File,
      repos: MutableList<ScopedRepositoryInfo>,
  ) {
    if (funcName == "google") {
      repos.add(
          ScopedRepositoryInfo("google", "https://maven.google.com/", RepositoryType.GOOGLE, file)
      )
    } else if (funcName == "mavenCentral") {
      repos.add(
          ScopedRepositoryInfo(
              "mavenCentral",
              "https://repo1.maven.org/maven2/",
              RepositoryType.MAVEN_CENTRAL,
              file,
          )
      )
    } else if (funcName == "maven") {
      var lambdaNode: TSNode? = null
      for (i in 0 until callNode.childCount) {
        val child = callNode.getChild(i)
        if (child.type == "lambda_literal") {
          lambdaNode = child
          break
        }
      }
      if (lambdaNode != null) {
        val lambdaText = text.substring(lambdaNode.startByte / 2, lambdaNode.endByte / 2)
        val matcher =
            Regex("""url\s*=\s*uri\s*\(\s*["']([^"']+)["']\s*\)""").find(lambdaText)
                ?: Regex("""url\s*=\s*["']([^"']+)["']""").find(lambdaText)
                ?: Regex("""url\s*\(\s*["']([^"']+)["']\s*\)""").find(lambdaText)
                ?: Regex("""setUrl\s*\(\s*["']([^"']+)["']\s*\)""").find(lambdaText)

        if (matcher != null) {
          val url = matcher.groupValues[1]
          if (url.startsWith("http")) {
            repos.add(
                ScopedRepositoryInfo(
                    "custom_${url.hashCode()}",
                    url,
                    RepositoryType.CUSTOM_MAVEN,
                    file,
                )
            )
          }
        }
      }
    }
  }
}
