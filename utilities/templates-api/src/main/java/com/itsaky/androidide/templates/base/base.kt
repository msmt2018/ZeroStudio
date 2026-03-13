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

package com.itsaky.androidide.templates.base

import android.content.Context
import androidx.annotation.StringRes
import android.view.View
import com.itsaky.androidide.templates.BooleanParameter
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.EnumParameter
import com.itsaky.androidide.templates.FileTemplate
import com.itsaky.androidide.templates.FileTemplateRecipeResult
import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ModuleTemplate
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.ModuleType
import com.itsaky.androidide.templates.ModuleType.AndroidApp
import com.itsaky.androidide.templates.ModuleType.AndroidLibrary
import com.itsaky.androidide.templates.NdkVersion
import com.itsaky.androidide.templates.CmakeVersion
import com.itsaky.androidide.templates.ParameterConstraint.DIRECTORY
import com.itsaky.androidide.templates.ParameterConstraint.EXISTS
import com.itsaky.androidide.templates.ParameterConstraint.MODULE_NAME
import com.itsaky.androidide.templates.ParameterConstraint.NONEMPTY
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.ProjectTemplateData
import com.itsaky.androidide.templates.ProjectVersionData
import com.itsaky.androidide.templates.R
import com.itsaky.androidide.templates.Sdk
import com.itsaky.androidide.templates.SpinnerWidget
import com.itsaky.androidide.templates.StringParameter
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.base.util.getNewProjectName
import com.itsaky.androidide.templates.base.util.moduleNameToDir
import com.itsaky.androidide.templates.enumParameter
import com.itsaky.androidide.templates.minSdkParameter
import com.itsaky.androidide.templates.packageNameParameter
import com.itsaky.androidide.templates.projectLanguageParameter
import com.itsaky.androidide.templates.projectNameParameter
import com.itsaky.androidide.templates.projectNdkVersionParameter
import com.itsaky.androidide.templates.projectCmakeVersionParameter
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.useKtsParameter
import com.itsaky.androidide.templates.useNdkParameter
import com.itsaky.androidide.templates.useTomlParameter
import com.itsaky.androidide.utils.AndroidUtils
import com.itsaky.androidide.utils.Environment
import java.io.File

typealias AndroidModuleTemplateConfigurator = AndroidModuleTemplateBuilder.() -> Unit

internal const val PREFS_NAME = "project_template_prefs"
internal const val ANDROIDIDE_KEY_LAST_SAVE_LOCATION = "last_save_location"

/** Get the last saved location from androidide.properties */
@PublishedApi
internal fun getLastSaveLocation(context: Context?): String {
    val androidideFile = Environment.ANDROIDIDE
    if (androidideFile?.exists() == true) {
        try {
            val props = java.util.Properties()
            androidideFile.inputStream().use { props.load(it) }
            val savedLocation = props.getProperty(ANDROIDIDE_KEY_LAST_SAVE_LOCATION)
            if (savedLocation != null && File(savedLocation).exists()) {
                return savedLocation
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return Environment.PROJECTS_DIR.absolutePath
}

/** Save the last save location to androidide.properties */
@PublishedApi
internal fun saveLastSaveLocation(context: Context?, location: String) {
    val androidideFile = Environment.ANDROIDIDE ?: return
    try {
        val props = java.util.Properties()

        // Load existing properties if file exists
        if (androidideFile.exists()) {
            androidideFile.inputStream().use { props.load(it) }
        }

        // Update the location
        props.setProperty(ANDROIDIDE_KEY_LAST_SAVE_LOCATION, location)

        // Save back to file
        androidideFile.outputStream().use { props.store(it, "AndroidCS Configuration") }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** File browser callback interface that should be implemented by the UI layer */
interface FileBrowserCallback {
    fun openFileBrowser(currentPath: String, parameter: StringParameter)
}

@PublishedApi internal var _fileBrowserCallback: FileBrowserCallback? = null

@PublishedApi internal var _currentBrowseParameter: StringParameter? = null

/** Set the file browser callback. This should be called from the UI layer. */
fun setFileBrowserCallback(callback: FileBrowserCallback?) {
    _fileBrowserCallback = callback
}

/** Create a click listener for the browse button */
@PublishedApi
internal fun createBrowseClickListener(): View.OnClickListener {
    return View.OnClickListener {
        _currentBrowseParameter?.let { param ->
            _fileBrowserCallback?.openFileBrowser(param.value, param)
        }
    }
}

/** Update the browse click listener with the parameter reference */
@PublishedApi
internal fun updateBrowseClickListener(parameter: StringParameter) {
    _currentBrowseParameter = parameter
}

/**
 * Setup base files for project templates.
 *
 * @param description Optional description for the template.
 * @param block Function to configure the template.
 * @author android_zero
 */
inline fun baseProject(
    projectName: StringParameter = projectNameParameter(),
    packageName: StringParameter = packageNameParameter(),
    useKts: BooleanParameter = useKtsParameter(),
    useNdk: BooleanParameter = useNdkParameter(),
    useToml: BooleanParameter = useTomlParameter(), 
    minSdk: EnumParameter<Sdk> = minSdkParameter(),
    language: EnumParameter<Language> = projectLanguageParameter(),
    ndkVersion: EnumParameter<NdkVersion> = projectNdkVersionParameter(),
    cmakeVersion: EnumParameter<CmakeVersion> = projectCmakeVersionParameter(), // 新增参数
    projectVersionData: ProjectVersionData = ProjectVersionData(),
    context: Context? = null,
    @StringRes description: Int? = null,
    crossinline block: ProjectTemplateBuilder.() -> Unit
): ProjectTemplate {
    return ProjectTemplateBuilder().apply {

        // Pass the description to the builder
        this.description = description

        // When project name is changed, change the package name accordingly
        projectName.observe { name ->
            val newPackage = AndroidUtils.appNameToPackageName(name.value, packageName.value)
            packageName.setValue(newPackage)
        }

        Environment.mkdirIfNotExits(Environment.PROJECTS_DIR)

        // Create the click listener that will be used
        val browseClickListener =
            View.OnClickListener {
                _currentBrowseParameter?.let { param ->
                    _fileBrowserCallback?.openFileBrowser(param.value, param)
                }
            }

        val saveLocation = stringParameter {
            name = R.string.wizard_save_location
            default = Environment.PROJECTS_DIR.absolutePath
            // default = getLastSaveLocation(context)
            constraints = listOf(NONEMPTY, DIRECTORY, EXISTS)
            endIcon = { R.drawable.ic_folder }
            onEndIconClick = browseClickListener
        }

        // Store reference to saveLocation for the click listener
        saveLocation.doAfterCreateView { param ->
            updateBrowseClickListener(param as StringParameter)
        }

        projectName.doBeforeCreateView {
            it.setValue(getNewProjectName(saveLocation.value, projectName.value))
        }

        widgets(
            TextFieldWidget(projectName),
            TextFieldWidget(packageName),
            TextFieldWidget(saveLocation),
            SpinnerWidget(language),
            SpinnerWidget(minSdk),
            CheckBoxWidget(useKts),
            CheckBoxWidget(useNdk),
            SpinnerWidget(ndkVersion),
            SpinnerWidget(cmakeVersion),
            CheckBoxWidget(useToml)
        )

        ndkVersion.isVisible = useNdk.default
        cmakeVersion.isVisible = useNdk.default

        // 监听复选框状态改变
        useNdk.observe { ndkParam ->
            val isNativeEnabled = ndkParam.value
            // 当勾选 NDK 时显示，取消勾选时隐藏（监听器会自动更新UI）
            ndkVersion.isVisible = isNativeEnabled
            cmakeVersion.isVisible = isNativeEnabled
        }

          // Save the selected location for future use
          saveLastSaveLocation(context, saveLocation.value)
 
        // Setup the required properties before executing the recipe
        preRecipe = {
            this@apply._executor = this

            this@apply._data = ProjectTemplateData(
                name = projectName.value,
                projectDir = File(saveLocation.value, projectName.value),
                version = projectVersionData,
                language = language.value,
                useKts = useKts.value,
                useNdk = useNdk.value,
                ndkVersion = ndkVersion.value.version,
                cmakeVersion = cmakeVersion.value.version,
                useToml = useToml.value
            )

            if (data.projectDir.exists() && data.projectDir.listFiles()?.isNotEmpty() == true) {
                throw IllegalArgumentException("Project directory already exists")
            }

            setDefaultModuleData(
                ModuleTemplateData(
                    name = ":app",
                    appName = data.name,
                    packageName = packageName.value,
                    projectDir = data.moduleNameToDir(":app"),
                    type = AndroidApp,
                    language = language.value,
                    minSdk = minSdk.value,
                    useKts = data.useKts,
                    useNdk = data.useNdk,
                    ndkVersion = data.ndkVersion,
                    cmakeVersion = data.cmakeVersion,
                    useToml = data.useToml
                )
            )
        }

        // After the recipe is executed, finalize the project creation
        postRecipe = {
            // build.gradle[.kts]
            buildGradle()

            // settings.gradle[.kts]
            settingsGradle()

            // gradle.properties
            gradleProps()

            // gradlew
            // gradlew.bat
            // gradle/wrapper/gradle-wrapper.jar
            // gradle/wrapper/gradle-wrapper.properties
            gradleWrapper()

            // .gitignore
            gitignore()
            
            if (data.useToml) {
                generateTomlFile()
            }
        }

        block()

    }.build() as ProjectTemplate
}

/**
 * Create a new module project in this project.
 *
 * @param block The module configurator.
 */
inline fun baseAndroidModule(
    isLibrary: Boolean = false,
    context: Context? = null,
    projectBuilder: ProjectTemplateBuilder? = null,
    crossinline block: AndroidModuleTemplateConfigurator
): ModuleTemplate {
    return AndroidModuleTemplateBuilder()
        .apply {

            val appName = if (isLibrary) null else projectNameParameter()
            val language = projectLanguageParameter()
            val ndkVersion = projectNdkVersionParameter()
            val cmakeVersion = projectCmakeVersionParameter() // 新增参数
            val minSdk = minSdkParameter()
            val packageName = packageNameParameter()
            val useKts = useKtsParameter()
            val useNdk = useNdkParameter()
            val useToml = useTomlParameter()

            val moduleName = stringParameter {
                name = R.string.wizard_module_name
                default = ":app"
                constraints = listOf(NONEMPTY, MODULE_NAME)
            }

            val type = enumParameter<ModuleType> {
                name = R.string.wizard_module_type
                default = AndroidLibrary
                startIcon = { R.drawable.ic_android }
                displayName = ModuleType::typeName
            }

            widgets(TextFieldWidget(moduleName))

            appName?.let { widgets(TextFieldWidget(it)) }

            widgets(
                TextFieldWidget(packageName),
                SpinnerWidget(minSdk),
                SpinnerWidget(type),
                SpinnerWidget(language),
                CheckBoxWidget(useKts),
                CheckBoxWidget(useNdk),
                SpinnerWidget(ndkVersion),
                SpinnerWidget(cmakeVersion),
                CheckBoxWidget(useToml)
            )

            preRecipe = commonPreRecipe {
                ModuleTemplateData(
                    name = moduleName.value,
                    appName = appName?.value,
                    packageName = packageName.value,
                    projectDir = requireProjectData().moduleNameToDir(moduleName.value),
                    type = type.value,
                    language = language.value,
                    minSdk = minSdk.value,
                    useKts = useKts.value,
                    useNdk = useNdk.value,
                    ndkVersion = ndkVersion.value.version,
                    cmakeVersion = cmakeVersion.value.version,
                    useToml = useToml.value
                )
            }
            
            postRecipe = commonPostRecipe()

            block()
        }.build() as ModuleTemplate
}

/**
 * Creates a template for a file.
 *
 * @param dir The directory in which the file will be created.
 * @param configurator The configurator to configure the template.
 * @return The[FileTemplate].
 */
inline fun <R : FileTemplateRecipeResult> baseFile(
    dir: File,
    crossinline configurator: FileTemplateBuilder<R>.() -> Unit
): FileTemplate<R> {
    return FileTemplateBuilder<R>(dir).apply(configurator)
        .build() as FileTemplate<R>
}