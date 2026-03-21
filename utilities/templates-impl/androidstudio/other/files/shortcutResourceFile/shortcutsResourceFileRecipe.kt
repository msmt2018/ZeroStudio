/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.other.files.shortcutResourceFile

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.other.files.shortcutResourceFile.res.xml.shortcutXml

fun RecipeExecutor.shortcutsResourceFileRecipe(moduleData: ModuleTemplateData, fileName: String) {
  val (_, _, resOut) = moduleData

  save(shortcutXml(), resOut.resolve("xml/${fileName}.xml"))

  open(resOut.resolve("xml/${fileName}.xml"))
}
