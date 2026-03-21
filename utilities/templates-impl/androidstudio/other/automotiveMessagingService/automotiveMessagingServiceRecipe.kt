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

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMessagingService.res.xml.automotiveAppDescXml
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMessagingService.src.app_package.messageReadReceiverJava
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMessagingService.src.app_package.messageReadReceiverKt
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMessagingService.src.app_package.messageReplyReceiverJava
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMessagingService.src.app_package.messageReplyReceiverKt
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMessagingService.src.app_package.messagingServiceJava
import com.itsaky.androidide.templates.impl.androidstudio.other.automotiveMessagingService.src.app_package.messagingServiceKt

fun RecipeExecutor.automotiveMessagingServiceRecipe(
  moduleData: ModuleTemplateData,
  serviceName: String,
  readReceiverName: String,
  replyReceiverName: String,
  packageName: String,
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:support-v4:+")
  addDependency("com.android.support:support-v13:+")
  mergeXml(androidManifestXml(packageName, readReceiverName, replyReceiverName, serviceName), manifestOut.resolve("AndroidManifest.xml"))
  mergeXml(automotiveAppDescXml(), resOut.resolve("xml/automotive_app_desc.xml"))

  val messagingService =
    when (projectData.language) {
      Language.Java -> messagingServiceJava(packageName, serviceName, useAndroidX)
      Language.Kotlin -> messagingServiceKt(packageName, serviceName, useAndroidX)
    }
  save(messagingService, srcOut.resolve("${serviceName}.${ktOrJavaExt}"))

  val messageReadReceiver =
    when (projectData.language) {
      Language.Java -> messageReadReceiverJava(packageName, readReceiverName, serviceName, useAndroidX)
      Language.Kotlin -> messageReadReceiverKt(packageName, readReceiverName, useAndroidX)
    }
  save(messageReadReceiver, srcOut.resolve("${readReceiverName}.${ktOrJavaExt}"))

  val messageReplyReceiver =
    when (projectData.language) {
      Language.Java -> messageReplyReceiverJava(packageName, replyReceiverName, serviceName, useAndroidX)
      Language.Kotlin -> messageReplyReceiverKt(packageName, replyReceiverName, useAndroidX)
    }
  save(messageReplyReceiver, srcOut.resolve("${replyReceiverName}.${ktOrJavaExt}"))

  open(srcOut.resolve("${serviceName}.${ktOrJavaExt}"))
  open(srcOut.resolve("${readReceiverName}.${ktOrJavaExt}"))
  open(srcOut.resolve("${replyReceiverName}.${ktOrJavaExt}"))
}
