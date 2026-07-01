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

package com.itsaky.androidide.templates.base.modules.android

import com.itsaky.androidide.templates.ModuleTemplate
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.AndroidModuleTemplateConfigurator
import com.itsaky.androidide.templates.base.ProjectTemplateBuilder
import com.itsaky.androidide.templates.base.baseAndroidXDependencies
import com.itsaky.androidide.templates.base.util.AndroidManifestBuilder.ConfigurationType.APPLICATION_ATTR

/**
 * Configure the default template for the project.
 *
 * @param name The name of the module (gradle format, e.g. ':app').
 * @param copyDefAssets Whether to copy the default Android assets (except `values` directory) to
 *   this module.
 * @param block The module configurator.
 */
inline fun ProjectTemplateBuilder.defaultAppModule(
    name: String = ":app",
    addAndroidX: Boolean = true,
    copyDefAssets: Boolean = true,
    crossinline block: AndroidModuleTemplateConfigurator,
) {
  check(defModuleTemplate == null) { "Default module has been already configured" }

  val module =
      AndroidModuleTemplateBuilder()
          .apply {
            projectBuilder = this@defaultAppModule

            this@defaultAppModule.moduleBuilders.add(this)

            _name = name
            templateName = 0
            thumb = 0

            preRecipe = commonPreRecipe {
              return@commonPreRecipe defModule
            }

            postRecipe = commonPostRecipe {
              if (copyDefAssets) {
                copyDefaultRes()

                // add manifest attributes for data extraction rules
                // and backup rules
                manifest {
                  configure(APPLICATION_ATTR) {
                    androidAttribute("dataExtractionRules", "@xml/data_extraction_rules")

                    androidAttribute("fullBackupContent", "@xml/backup_rules")
                  }
                }
              }
            }

            if (addAndroidX) {
              baseAndroidXDependencies()
            }

            block()
          }
          .build() as ModuleTemplate

  modules.add(module)
}

/**
 * Adds an Android Library module to the project.
 *
 * Unlike [defaultAppModule] (which is tied to the project-level `defModule` data), this helper
 * is intended for **additional** library modules such as a `:baselineprofile` module. It
 * registers a fresh [AndroidModuleTemplateBuilder] in the project's internal module lists so its
 * dependencies and metadata participate in `libs.versions.toml` generation.
 *
 * The caller is expected to provide a fully constructed [ModuleTemplateData] describing the
 * module (name, package, SDK, NDK, language, etc.) and a [block] to further configure the builder
 * (e.g. dependencies, custom `postRecipe`).
 *
 * Example:
 * ```
 * androidLibraryModule(
 *     name = ":baselineprofile",
 *     moduleData = ModuleTemplateData(
 *         name = ":baselineprofile",
 *         appName = null,
 *         packageName = data.packageName,
 *         projectDir = data.moduleNameToDir(":baselineprofile"),
 *         type = ModuleType.AndroidLibrary,
 *         language = Language.Kotlin,
 *         minSdk = Sdk.Lollipop,
 *         ndkVersion = data.ndkVersion,
 *         cmakeVersion = data.cmakeVersion,
 *     ),
 * ) {
 *   isBaselineProfileModule = true
 *   postRecipe = { /* custom file writes */ }
 *   addDependency(/* ... */)
 * }
 * ```
 *
 * @param name The Gradle name of the module (e.g. `":baselineprofile"`).
 * @param moduleData The template data used to render the module directory and source files.
 * @param block Optional configuration block applied to the underlying
 *   [AndroidModuleTemplateBuilder] (dependencies, flags, custom recipes, ...).
 *
 * @author android_zero
 */
inline fun ProjectTemplateBuilder.androidLibraryModule(
    name: String,
    moduleData: ModuleTemplateData,
    crossinline block: AndroidModuleTemplateConfigurator = {},
) {
  val module =
      AndroidModuleTemplateBuilder()
          .apply {
            projectBuilder = this@androidLibraryModule

            this@androidLibraryModule.moduleBuilders.add(this)

            _name = name
            templateName = 0
            thumb = 0

            preRecipe = commonPreRecipe { moduleData }

            block()
          }
          .build() as ModuleTemplate

  modules.add(module)
}
