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
package com.itsaky.androidide.templates.impl.androidstudio.activities.common

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.escapeKotlinIdentifier
import com.itsaky.androidide.templates.renderIf
import com.itsaky.androidide.templates.underscoreToCamelCase
import com.itsaky.androidide.templates.underscoreToLowerCamelCase

/**
 * Replaces the findViewById call with the equivalent of view binding.
 *
 * @param language the language of the template that calls findViewById
 * @param isViewBindingSupported indicates if the caller supports view binding
 * @param id String representation of the id
 * @param bindingName name of the variable of the view binding class. E.g. used in a form of
 *   ${bidingName}.${id}. "binding" is used as a default value
 * @param className name of the class if the obtained view needs explicit cast
 * @param parentView name of the parent view if exists. Usually called from a class that doesn't
 *   have findViewById method. E.g. Fragment
 */
fun findViewById(
    language: Language,
    isViewBindingSupported: Boolean,
    id: String,
    bindingName: String = "binding",
    className: String? = null,
    parentView: String? = null,
) =
    when (language) {
      Language.Java ->
          findViewByIdJava(
              isViewBindingSupported = isViewBindingSupported,
              id = id,
              bindingName = bindingName,
              parentView = parentView,
          )
      Language.Kotlin ->
          findViewByIdKotlin(
              isViewBindingSupported = isViewBindingSupported,
              id = id,
              bindingName = bindingName,
              className = className,
              parentView = parentView,
          )
    }

private fun findViewByIdJava(
    isViewBindingSupported: Boolean,
    id: String,
    bindingName: String,
    parentView: String? = null,
) =
    if (isViewBindingSupported) "$bindingName.${underscoreToLowerCamelCase(id)}"
    else """${renderIf(parentView != null) { "$parentView." }} findViewById(R.id.${id})"""

private fun findViewByIdKotlin(
    isViewBindingSupported: Boolean,
    id: String,
    bindingName: String,
    className: String? = null,
    parentView: String? = null,
) =
    if (isViewBindingSupported) "$bindingName.${underscoreToLowerCamelCase(id)}"
    else
        """${renderIf(parentView != null) { "$parentView." }} findViewById${renderIf(className != null) { "<$className>" }}(R.id.${id})"""

fun importViewBindingClass(
    isViewBindingSupported: Boolean,
    packageName: String,
    applicationPackage: String?,
    layoutName: String,
    language: Language,
) =
    renderIf(isViewBindingSupported) {
      // The databinding class is generated in the root application package. But in case that is
      // ever
      // unavailable, the containing class's package is used instead. That may be incorrect, but
      // it's
      // better than having no import statement; at least the user will see that the statement
      // doesn't
      // resolve, and may be able to fix it themselves.
      val escapedPackageName =
          if (language == Language.Kotlin) {
            escapeKotlinIdentifier(applicationPackage ?: packageName)
          } else {
            applicationPackage ?: packageName
          }
      "import ${escapedPackageName}.databinding.${layoutToViewBindingClass(layoutName)}${renderIf(language == Language.Java){";"}}"
    }

fun layoutToViewBindingClass(layoutName: String) = underscoreToCamelCase(layoutName) + "Binding"
