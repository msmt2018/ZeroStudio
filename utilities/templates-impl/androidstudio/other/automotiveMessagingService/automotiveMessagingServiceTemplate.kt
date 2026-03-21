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

package com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMessagingService

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val automotiveMessagingServiceTemplate
  get() = template {
    name = "Messaging Service"
    description = "Create a service that sends notifications compatible with Android Auto"
    minApi = 21

    category = Category.Car
    formFactor = FormFactor.Car
    screens = listOf(WizardUiContext.MenuEntry, WizardUiContext.NewModule)

    val serviceName = stringParameter {
      name = "Service class name"
      default = "MyMessagingService"
      help = "The name of the service that will handle incoming messages and send corresponding notifications"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val readReceiverName = stringParameter {
      name = "Read receiver class name"
      default = "MessageReadReceiver"
      help = "The broadcast receiver that will handle Read notifications"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val replyReceiverName = stringParameter {
      name = "Reply receiver class name"
      default = "MessageReplyReceiver"
      help = "The broadcast receiver that will handle Reply notifications"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val packageName = defaultPackageNameParameter

    widgets(
      TextFieldWidget(serviceName),
      TextFieldWidget(readReceiverName),
      TextFieldWidget(replyReceiverName),
      PackageNameWidget(packageName),
      LanguageWidget(),
    )

    recipe = { data: TemplateData ->
      automotiveMessagingServiceRecipe(
        data as ModuleTemplateData,
        serviceName.value,
        readReceiverName.value,
        replyReceiverName.value,
        packageName.value,
      )
    }

    thumb { File("automotive-messaging-service").resolve("automotive-messaging-service.png") }
  }
