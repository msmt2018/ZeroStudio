/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp.src.app_package

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun carAppScreenKt(screenName: String, packageName: String): String {
  return """
package ${escapeKotlinIdentifier(packageName)}

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

class $screenName(carContext: CarContext) : Screen(carContext) {
  override fun onGetTemplate(): Template {
    return MessageTemplate.Builder("Hello world!")
      .setHeaderAction(Action.APP_ICON)
      .setTitle("$screenName")
      .build()
  }
}
"""
}
