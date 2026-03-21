/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.itsaky.androidide.templates.impl.androidstudio.other.folders

import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.SourceSetType
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.invisibleSourceProviderNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

private fun getSourceSetFolderTemplate(_name: String, _description: String, sourceSetType: SourceSetType, defaultDirName: String) =
  template {
    name = _name
    minApi = MIN_API
    description = _description

    category = Category.Folder
    formFactor = FormFactor.Generic
    screens = listOf(WizardUiContext.MenuEntry)

    val remapFolder: BooleanParameter = booleanParameter {
      name = "Change Folder Location"
      default = false
      help = "Change the folder location to another folder within the module"
    }

    val newLocation: StringParameter = stringParameter {
      name = "New Folder Location"
      constraints = listOf(Constraint.NONEMPTY, Constraint.SOURCE_SET_FOLDER, Constraint.UNIQUE)
      default = ""
      suggest = { "src/${sourceProviderName}/$defaultDirName/" }
      help = "The location for the new folder"
      enabled = { remapFolder.value }
      loggable = true
    }

    val sourceProviderName = invisibleSourceProviderNameParameter

    widgets(
      CheckBoxWidget(remapFolder),
      // TODO(qumeric): make a widget for path input?
      TextFieldWidget(newLocation),
      TextFieldWidget(sourceProviderName),
    )

    thumb {
      // TODO(b/147126989)
      File("no_activity.png")
    }

    recipe = { data: TemplateData ->
      generateResourcesFolder(
        data as ModuleTemplateData,
        remapFolder.value,
        newLocation.value,
        { sourceProviderName.suggest()!! },
        sourceSetType,
        defaultDirName,
      )
    }
  }

private fun getSimpleFolderTemplate(_name: String, _description: String, dirName: String) = template {
  name = _name
  minApi = MIN_API
  description = _description

  category = Category.Folder
  formFactor = FormFactor.Generic
  screens = listOf(WizardUiContext.MenuEntry)

  val remapFolder: BooleanParameter = booleanParameter {
    name = "Change Folder Location"
    default = false
    help = "Change the folder location to another folder within the module"
  }

  val newLocation: StringParameter = stringParameter {
    name = "New Folder Location"
    constraints = listOf(Constraint.NONEMPTY, Constraint.SOURCE_SET_FOLDER, Constraint.UNIQUE)
    default = ""
    suggest = { "src/${sourceProviderName}/$dirName/" }
    help = "The location for the new folder"
    enabled = { remapFolder.value }
    loggable = true
  }

  val sourceProviderName = invisibleSourceProviderNameParameter

  widgets(
    CheckBoxWidget(remapFolder),
    // TODO(qumeric): make a widget for path input?
    TextFieldWidget(newLocation),
    TextFieldWidget(sourceProviderName),
  )

  thumb {
    // TODO(b/147126989)
    File("no_activity.png")
  }

  recipe = { data: TemplateData ->
    with(data as ModuleTemplateData) {
      createDirectory(if (remapFolder.value) rootDir.resolve(newLocation.value) else resDir.resolve(dirName))
    }
  }
}

internal val folderTemplates =
  listOf(
    getSourceSetFolderTemplate(
      "AIDL Folder",
      "Creates a source root for Android Interface Description Language files",
      SourceSetType.AIDL,
      "aidl",
    ),
    getSourceSetFolderTemplate(
      "Assets Folder",
      "Creates a source root for assets which will be included in the APK",
      SourceSetType.ASSETS,
      "assets",
    ),
    getSimpleFolderTemplate("Font Folder", "Font Resources Folder", "font"),
    getSourceSetFolderTemplate("Java Folder", "Creates a source root for Java files", SourceSetType.JAVA, "java"),
    getSourceSetFolderTemplate("JNI Folder", "Creates a source root for Java Native Interface files", SourceSetType.JNI, "jni"),
    getSimpleFolderTemplate("Raw Resources Folder", "Creates a folder for resources included in the APK in their raw form", "raw"),
    getSourceSetFolderTemplate("Res Folder", "Creates a source root for Android Resource files", SourceSetType.RES, "res"),
    getSourceSetFolderTemplate(
      "Java Resources Folder",
      "Creates a source root for Java Resource (NOT Android resource) files",
      SourceSetType.RESOURCES,
      "resources",
    ),
    getSimpleFolderTemplate("XML Resources Folder", "Creates a folder for arbitrary XML files to be included in the APK", "xml"),
  )
