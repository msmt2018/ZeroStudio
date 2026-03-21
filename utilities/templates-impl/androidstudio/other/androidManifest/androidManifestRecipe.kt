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

package com.itsaky.androidide.templates.impl.androidstudio.other.androidManifest

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.SourceSetType
import java.io.File

fun RecipeExecutor.androidManifestRecipe(
  moduleData: ModuleTemplateData,
  remapFolder: Boolean,
  relativeNewLocation: String,
  sourceProviderNameSupplier: () -> String,
) {
  if (remapFolder) {
    val newLocation = moduleData.rootDir.resolve(relativeNewLocation)
    mergeXml(androidManifestXml(), newLocation)
    addSourceSet(SourceSetType.MANIFEST, sourceProviderNameSupplier(), File(relativeNewLocation))
  } else {
    mergeXml(androidManifestXml(), moduleData.manifestDir.resolve("AndroidManifest.xml"))
  }
}
