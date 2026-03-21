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

package com.itsaky.androidide.templates.impl.androidstudio.other.sliceProvider.src.app_package

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun sliceProviderKt(className: String, packageName: String, pathPrefix: String) =
  """
package ${escapeKotlinIdentifier(packageName)}

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.slice.Slice
import androidx.slice.SliceProvider
import androidx.slice.builders.ListBuilder
import androidx.slice.builders.SliceAction

class ${className} : SliceProvider() {
    /**
     * Instantiate any required objects. Return true if the provider was successfully created,
     * false otherwise.
     */
    override fun onCreateSliceProvider(): Boolean {
        return true
    }

    /**
     * Converts URL to content URI (i.e. content://${packageName}...)
     */
    override fun onMapIntentToUri(intent: Intent?): Uri {
        // Note: implementing this is only required if you plan on catching URL requests.
        // This is an example solution.
        var uriBuilder: Uri.Builder = Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
        if (intent == null) return uriBuilder.build()
        val data = intent.data
	val dataPath = data?.path
        if (data != null && dataPath != null) {
            val path = dataPath.replace("${pathPrefix}", "")
            uriBuilder = uriBuilder.path(path)
        }
        val context = context
        if (context != null) {
            uriBuilder = uriBuilder.authority(context.packageName)
        }
        return uriBuilder.build()
    }

    /**
     * Construct the Slice and bind data if available.
     */
    override fun onBindSlice(sliceUri: Uri): Slice? {
        // Note: you should switch your build.gradle dependency to
        // slice-builders-ktx for a nicer interface in Kotlin.
        val context = context ?: return null
        val activityAction = createActivityAction() ?: return null
        return if (sliceUri.path == "/") {
            // Path recognized. Customize the Slice using the androidx.slice.builders API.
            // Note: ANR and StrictMode are enforced here so don"t do any heavy operations. 
            // Only bind data that is currently available in memory.
            ListBuilder(context, sliceUri, ListBuilder.INFINITY)
                .addRow(
                    ListBuilder.RowBuilder()
                        .setTitle("URI found.")
                        .setPrimaryAction(activityAction)
                )
                .build()
        } else {
            // Error: Path not found.
            ListBuilder(context, sliceUri, ListBuilder.INFINITY)
                .addRow(
                    ListBuilder.RowBuilder()
                        .setTitle("URI not found.")
                        .setPrimaryAction(activityAction)
                )
                .build()
        }
    }

    private fun createActivityAction(): SliceAction? {
        return null
        /*
        Instead of returning null, you should create a SliceAction. Here is an example:
        return SliceAction.create(
            PendingIntent.getActivity(
                context, 0, Intent(context, MyActivityClass::class.java), 0
            ),
            IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground),
            ListBuilder.ICON_IMAGE,
            "Open App"
        )
        */
    }

    /**
     * Slice has been pinned to external process. Subscribe to data source if necessary.
     */
    override fun onSlicePinned(sliceUri: Uri?) {
        // When data is received, call context.contentResolver.notifyChange(sliceUri, null) to
        // trigger ${className}#onBindSlice(Uri) again.
    }

    /**
     * Unsubscribe from data source if necessary.
     */
    override fun onSliceUnpinned(sliceUri: Uri?) {
        // Remove any observers if necessary to avoid memory leaks.
    }
}
"""
