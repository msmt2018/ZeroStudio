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

package com.itsaky.androidide.templates.impl.nativeTemplate.baselineProfileLibraryProjet

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.NdkVersion
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.defaultAppModuleWithNdk
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.projectNdkVersionParameter
import com.itsaky.androidide.templates.useCmakeParameter
import com.itsaky.androidide.templates.useNdkParameter
import java.io.File

/**
 * baselineProfile Project Template.
 *
 * @author android_zero
 */
fun baselineProfileLibraryProjet(): ProjectTemplate =
    baseProjectImpl(
        useNdk = useNdkParameter { default = true },
        ndkVersion = projectNdkVersionParameter { default = NdkVersion.R27A },
        useCmake = useCmakeParameter { default = false },
    ) {
      templateName = "baselineProfile"
      thumb = R.drawable.ic_bolt_boost

        recipe = createRecipe {
          val mainDir = File(data.projectDir, "src/main")
          mainDir.mkdirs()


        }
      }
    }