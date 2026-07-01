package com.itsaky.androidide.templates.impl.template.library.baselineProfile

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.ProjectVersionData
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl

/**
 * Baseline Profile library project template.
 *
 * 生成包含 `:app` 主模块与 `:baselineprofile` 基线性能模块的 Android 项目。
 * 基线模块使用 `com.android.test` + `androidx.baselineprofile` 插件，
 * 自动生成 `BaselineProfileGenerator` 与 `StartupBenchmarks` 源文件，
 * 并写入 `libs.versions.toml` 中的相关依赖与插件。
 */
fun baselineProfileLibraryProject(): ProjectTemplate =
    baseProjectImpl(
        projectVersionData = ProjectVersionData(
            gradlePlugin = "9.3.0-alpha06",
            kotlin = "2.2.10",
        ),
    ) {
      templateName = R.string.template_baseline_profile
      thumb = R.drawable.ic_bolt_boost
      description = R.string.title_template_description_baseline_profile

      // 默认 :app 模块
      defaultAppModule {
        // 使用默认空白应用模块
      }

      // 新增 :baselineprofile 模块（包含完整的依赖、清单与 DSL 生成）
      addBaselineProfileModule()
    }
