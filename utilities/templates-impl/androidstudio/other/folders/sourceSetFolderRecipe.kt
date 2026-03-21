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

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.SourceSetType

fun RecipeExecutor.generateResourcesFolder(
  moduleData: ModuleTemplateData,
  remapFolder: Boolean,
  location: String, // TODO(qumeric): make it File
  sourceProviderNameSupplier: () -> String,
  sourceSetType: SourceSetType = SourceSetType.RESOURCES,
  defaultDirName: String = "resources",
) {
  val sourceProviderName = sourceProviderNameSupplier()
  if (remapFolder) {
    val newDirectory = moduleData.rootDir.resolve(location)
    createDirectory(newDirectory)
    addSourceSet(sourceSetType, sourceProviderName, moduleData.manifestDir.resolve(defaultDirName))
    addSourceSet(sourceSetType, sourceProviderName, newDirectory)
  } else {
    createDirectory(moduleData.manifestDir.resolve(defaultDirName))
  }
}
