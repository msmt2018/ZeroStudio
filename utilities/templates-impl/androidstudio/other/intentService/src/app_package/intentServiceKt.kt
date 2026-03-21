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

package com.itsaky.androidide.templates.impl.androidstudio.other.intentService.src.app_package

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.renderIf

fun intentServiceKt(className: String, includeHelper: Boolean, packageName: String): String {
  val classDocBlock =
    if (includeHelper)
      """
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
  """
    else "* TODO: Customize class - update intent actions and extra parameters."

  val companionObjectBlock =
    renderIf(includeHelper) {
      """
    companion object {
        /**
         * Starts this service to perform action Foo with the given parameters. If
         * the service is already performing a task this action will be queued.
         *
         * @see IntentService
         */
        // TODO: Customize helper method
        @JvmStatic fun startActionFoo(context: Context, param1: String, param2: String) {
            val intent = Intent(context, ${className}::class.java).apply {
                action = ACTION_FOO
                putExtra(EXTRA_PARAM1, param1)
                putExtra(EXTRA_PARAM2, param2)
            }
            context.startService(intent)
        }

        /**
         * Starts this service to perform action Baz with the given parameters. If
         * the service is already performing a task this action will be queued.
         *
         * @see IntentService
         */
        // TODO: Customize helper method
        @JvmStatic fun startActionBaz(context: Context, param1: String, param2: String) {
            val intent = Intent(context, ${className}::class.java).apply {
                action = ACTION_BAZ
                putExtra(EXTRA_PARAM1, param1)
                putExtra(EXTRA_PARAM2, param2)
            }
            context.startService(intent)
        }
    }
  """
    }

  return """
package ${escapeKotlinIdentifier(packageName)}

import android.app.IntentService
import android.content.Intent
${renderIf(includeHelper) { "import android.content.Context" }}

// TODO: Rename actions, choose action names that describe tasks that this
// IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
${renderIf(includeHelper) { "private" }} const val ACTION_FOO = "${packageName}.action.FOO"
${renderIf(includeHelper) { "private" }} const val ACTION_BAZ = "${packageName}.action.BAZ"

// TODO: Rename parameters
${renderIf(includeHelper) { "private" }} const val EXTRA_PARAM1 = "${packageName}.extra.PARAM1"
${renderIf(includeHelper) { "private" }} const val EXTRA_PARAM2 = "${packageName}.extra.PARAM2"
/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
$classDocBlock
 */
class ${className} : IntentService("${className}") {

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_FOO -> {
                val param1 = intent.getStringExtra(EXTRA_PARAM1)
                val param2 = intent.getStringExtra(EXTRA_PARAM2)
                handleActionFoo(param1, param2)
            }
            ACTION_BAZ -> {
                val param1 = intent.getStringExtra(EXTRA_PARAM1)
                val param2 = intent.getStringExtra(EXTRA_PARAM2)
                handleActionBaz(param1, param2)
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private fun handleActionFoo(param1: String?, param2: String?) {
        TODO("Handle action Foo")
    }

    /**
     * Handle action Baz in the provided background thread with the provided
     * parameters.
     */
    private fun handleActionBaz(param1: String?, param2: String?) {
        TODO("Handle action Baz")
    }
$companionObjectBlock
}
"""
}
