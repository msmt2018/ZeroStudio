/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.res.layout.activityDetailsXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.res.layout.activityMainXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.res.values.colorsXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.res.values.themesXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.browseErrorActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.browseErrorActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.cardPresenterJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.cardPresenterKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.detailsActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.detailsActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.detailsDescriptionPresenterJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.detailsDescriptionPresenterKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.errorFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.errorFragmentKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.mainActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.mainFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.mainFragmentKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.movieJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.movieKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.movieListJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.movieListKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.playbackActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.playbackActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.playbackVideoFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.playbackVideoFragmentKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.videoDetailsFragmentJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package.videoDetailsFragmentKt

/**
 * Formats the recipe for Android TV Blank Views Activity containing leanback support library configurations.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun AndroidModuleTemplateBuilder.androidTVActivityRecipe(
  activityClass: String,
  layoutName: String,
  mainFragmentClass: String,
  detailsActivityClass: String,
  detailsLayoutName: String,
  detailsFragmentClass: String,
  packageName: String,
  isLauncher: Boolean = true
) {
    addDependency("androidx.leanback", "leanback", "1.2.0")
    addDependency("com.github.bumptech.glide", "glide", "4.11.0")

    val themeName = "${data.appName}Theme"

    recipe = createRecipe {
        save(androidManifestXml(activityClass, detailsActivityClass, data.type.name.contains("Library"), true, packageName, "@style/$themeName"), manifestFile())

        res {
            writeXmlResource("strings", ResourceType.VALUES, source = { stringsXml(activityClass, true) })
            writeXmlResource("colors", ResourceType.VALUES, source = { colorsXml() })
            writeXmlResource("themes", ResourceType.VALUES, source = { themesXml(themeName) })
            
            writeXmlResource(layoutName, ResourceType.LAYOUT, source = { activityMainXml(activityClass, packageName) })
            writeXmlResource(detailsLayoutName, ResourceType.LAYOUT, source = { activityDetailsXml(detailsActivityClass, packageName) })
        }

        sources {
            val minApi = data.versions.minSdk.apiLevel()
            if (data.language == Language.Java) {
                writeJavaSrc(packageName, activityClass, source = { mainActivityJava(activityClass, layoutName, mainFragmentClass, packageName) })
                writeJavaSrc(packageName, mainFragmentClass, source = { mainFragmentJava(detailsActivityClass, mainFragmentClass, minApi, packageName) })
                writeJavaSrc(packageName, detailsActivityClass, source = { detailsActivityJava(detailsActivityClass, detailsFragmentClass, detailsLayoutName, packageName) })
                writeJavaSrc(packageName, detailsFragmentClass, source = { videoDetailsFragmentJava(activityClass, detailsActivityClass, detailsFragmentClass, minApi, packageName) })
                writeJavaSrc(packageName, "Movie", source = { movieJava(packageName) })
                writeJavaSrc(packageName, "MovieList", source = { movieListJava(packageName) })
                writeJavaSrc(packageName, "CardPresenter", source = { cardPresenterJava(packageName) })
                writeJavaSrc(packageName, "DetailsDescriptionPresenter", source = { detailsDescriptionPresenterJava(packageName) })
                writeJavaSrc(packageName, "PlaybackActivity", source = { playbackActivityJava(packageName) })
                writeJavaSrc(packageName, "PlaybackVideoFragment", source = { playbackVideoFragmentJava(minApi, packageName) })
                writeJavaSrc(packageName, "BrowseErrorActivity", source = { browseErrorActivityJava(layoutName, packageName, mainFragmentClass) })
                writeJavaSrc(packageName, "ErrorFragment", source = { errorFragmentJava(minApi, packageName) })
            } else {
                writeKtSrc(packageName, activityClass, source = { mainActivityKt(activityClass, layoutName, mainFragmentClass, packageName) })
                writeKtSrc(packageName, mainFragmentClass, source = { mainFragmentKt(detailsActivityClass, mainFragmentClass, minApi, packageName) })
                writeKtSrc(packageName, detailsActivityClass, source = { detailsActivityKt(detailsActivityClass, detailsFragmentClass, detailsLayoutName, packageName) })
                writeKtSrc(packageName, detailsFragmentClass, source = { videoDetailsFragmentKt(activityClass, detailsActivityClass, detailsFragmentClass, minApi, packageName) })
                writeKtSrc(packageName, "Movie", source = { movieKt(packageName) })
                writeKtSrc(packageName, "MovieList", source = { movieListKt(packageName) })
                writeKtSrc(packageName, "CardPresenter", source = { cardPresenterKt(packageName) })
                writeKtSrc(packageName, "DetailsDescriptionPresenter", source = { detailsDescriptionPresenterKt(packageName) })
                writeKtSrc(packageName, "PlaybackActivity", source = { playbackActivityKt(packageName) })
                writeKtSrc(packageName, "PlaybackVideoFragment", source = { playbackVideoFragmentKt(minApi, packageName) })
                writeKtSrc(packageName, "BrowseErrorActivity", source = { browseErrorActivityKt(layoutName, packageName, mainFragmentClass) })
                writeKtSrc(packageName, "ErrorFragment", source = { errorFragmentKt(minApi, packageName) })
            }
        }
    }
}