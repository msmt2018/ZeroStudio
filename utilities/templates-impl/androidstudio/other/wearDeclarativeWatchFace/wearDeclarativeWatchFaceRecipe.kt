/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace.res.raw.rawWatchFaceXml
import com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace.res.xml.watchFaceInfoXml
import java.io.File

fun RecipeExecutor.wearDeclarativeWatchFaceRecipe(moduleData: ModuleTemplateData) {
  val (_, _, resOut, manifestOut) = moduleData

  mergeXml(androidManifestXml(), manifestOut.resolve("AndroidManifest.xml"))
  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  mergeXml(rawWatchFaceXml(), resOut.resolve("raw/watchface.xml"))
  mergeXml(watchFaceInfoXml(), resOut.resolve("xml/watch_face_info.xml"))
  copy(File("wear-watchface").resolve("drawable"), resOut.resolve("drawable"))
}
