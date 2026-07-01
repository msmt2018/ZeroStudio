package com.itsaky.androidide.templates.impl.template.library.baselineProfile

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ModuleTemplate
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.ModuleType
import com.itsaky.androidide.templates.Sdk
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.ProjectTemplateBuilder
import com.itsaky.androidide.templates.base.models.parseDependency

/**
 * Version catalog values used by the baseline profile module. Centralised so the
 * generated `libs.versions.toml` and module-level `build.gradle` always agree.
 *
 * The `baselineprofile` plugin version itself is written by
 * [com.itsaky.androidide.templates.base.ProjectTemplateBuilder.generateToml].
 */
private const val BENCHMARK_MACRO_JUNIT4_VERSION = "1.2.4"
private const val ANDROIDX_JUNIT_VERSION = "1.1.5"
private const val ESPRESSO_CORE_VERSION = "3.5.1"
private const val UIAUTOMATOR_VERSION = "2.2.0"
private const val PROFILEINSTALLER_VERSION = "1.3.1"

/**
 * Adds a `:baselineprofile` library module to the project.
 *
 * Behaviour:
 *  - Creates an [AndroidModuleTemplateBuilder] that targets `:baselineprofile`.
 *  - Marks the project as having a baseline profile module, which causes
 *    [com.itsaky.androidide.templates.base.ProjectTemplateBuilder.generateToml]
 *    to also emit the `android-test` and `baselineprofile` plugin aliases.
 *  - Registers the benchmark-related dependencies so they show up in
 *    `gradle/libs.versions.toml`.
 *  - Writes an empty `AndroidManifest.xml` (only `<manifest />`) and the
 *    module-specific `build.gradle[.kts]` source code.
 *  - Generates `BaselineProfileGenerator` and `StartupBenchmarks` source files
 *    under `src/main/java`.
 */
internal inline fun ProjectTemplateBuilder.addBaselineProfileModule(
    name: String = ":baselineprofile",
    crossinline block: AndroidModuleTemplateBuilder.() -> Unit = {},
) {
  // 通知项目模板需要 baselineprofile 相关插件
  enableBaselineProfileSupport()

  // 提前收集项目级参数，用于模块数据
  val projectData = data
  val moduleData =
      ModuleTemplateData(
          name = name,
          appName = null,
          packageName = projectData.packageName,
          projectDir = projectData.moduleNameToDir(name),
          type = ModuleType.AndroidLibrary,
          language = Language.Kotlin,
          minSdk = Sdk.Lollipop,
          // 与 project 一致：
          ndkVersion = projectData.ndkVersion,
          cmakeVersion = projectData.cmakeVersion,
      )

  val module =
      AndroidModuleTemplateBuilder()
          .apply {
            projectBuilder = this@addBaselineProfileModule
            this@addBaselineProfileModule.moduleBuilders.add(this)
            _name = name
            templateName = 0
            thumb = 0

            preRecipe = commonPreRecipe { moduleData }

            postRecipe = {
              // .gitignore
              gitignore()

              // 1) 写入空 AndroidManifest.xml（只有 <manifest />）
              executor.save(
                  """<?xml version="1.0" encoding="utf-8"?>
<manifest />
""",
                  manifestFile(),
              )

              // 2) 写入模块级 build.gradle[.kts]
              executor.save(
                  if (data.useKts) baselineProfileKtDsl() else baselineProfileGroovyDsl(),
                  buildGradleFile(),
              )

              // 3) 生成源代码：BaselineProfileGenerator / StartupBenchmarks
              sources {
                writeKtSrc(
                    data.packageName,
                    "BaselineProfileGenerator",
                    source = { baselineProfileGeneratorSrc() },
                )
                writeKtSrc(
                    data.packageName,
                    "StartupBenchmarks",
                    source = { startupBenchmarksSrc() },
                )
              }
            }

            // —— 依赖注册 ——
            // 使用 parseDependency 让 DSL/toml 自动按别名写入 libs.versions.toml
            // versionRefName 与已有 toml 的 [versions] 段命名保持一致，避免生成新的别名
            addDependency(
                parseDependency(
                    "androidx.benchmark:benchmark-macro-junit4:$BENCHMARK_MACRO_JUNIT4_VERSION",
                    tomlAlias = "androidx-benchmark-macro-junit4",
                    versionRefName = "benchmarkMacroJunit4",
                ),
            )
            addDependency(
                parseDependency(
                    "androidx.test.ext:junit:$ANDROIDX_JUNIT_VERSION",
                    tomlAlias = "androidx-junit",
                    versionRefName = "junitVersion",
                ),
            )
            addDependency(
                parseDependency(
                    "androidx.test.espresso:espresso-core:$ESPRESSO_CORE_VERSION",
                    tomlAlias = "androidx-espresso-core",
                    versionRefName = "espressoCore",
                ),
            )
            addDependency(
                parseDependency(
                    "androidx.test.uiautomator:uiautomator:$UIAUTOMATOR_VERSION",
                    tomlAlias = "androidx-uiautomator",
                    versionRefName = "uiautomator",
                ),
            )
            addDependency(
                parseDependency(
                    "androidx.profileinstaller:profileinstaller:$PROFILEINSTALLER_VERSION",
                    tomlAlias = "androidx-profileinstaller",
                    versionRefName = "profileinstaller",
                ),
            )

            block()
          }
          .build() as ModuleTemplate

  modules.add(module)
}
