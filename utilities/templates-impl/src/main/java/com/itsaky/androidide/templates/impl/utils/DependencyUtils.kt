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

package com.itsaky.androidide.templates.impl.utils

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.models.Dependency

/**
 * 注入标准库依赖 (用于普通视图项目)
 * @author android_zero
 */
fun AndroidModuleTemplateBuilder.addStandardDependencies() {
    if (data.language == Language.Kotlin) {
        addDependency(Dependency.AndroidX.Core_Ktx)
    } else {
        addDependency(Dependency.AndroidX.Core)
    }
    addDependency(Dependency.AndroidX.AppCompat)
    addDependency(Dependency.Google.Material)
    addDependency(Dependency.AndroidX.ConstraintLayout)
}

/**
 * 注入 Jetpack Compose 全家桶依赖
 * @author android_zero
 */
fun AndroidModuleTemplateBuilder.addComposeDependencies() {
    addDependency(Dependency.AndroidX.Core_Ktx)
    addDependency(Dependency.AndroidX.Compose.LifeCycle_Runtime_Ktx)
    addDependency(Dependency.AndroidX.Compose.Activity)
    addDependency(Dependency.AndroidX.Compose.BOM, isPlatform = true)
    addDependency(Dependency.AndroidX.Compose.UI)
    addDependency(Dependency.AndroidX.Compose.UI_Graphics)
    addDependency(Dependency.AndroidX.Compose.UI_Tooling_Preview)
    addDependency(Dependency.AndroidX.Compose.Material3)
    addDependency(Dependency.AndroidX.Compose.UI_Tooling)
    addDependency(Dependency.AndroidX.Compose.UI_Test_Manifest)
}