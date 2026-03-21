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

package com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.ui.mymodel

import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.ArchStarterActivityTemplateVariables

/**
 * Generates the Compose UI screen implementing the Hilt ViewModel behaviors for states.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun ArchStarterActivityTemplateVariables.modelScreen() =
  """
package $modelPackage

import $themeNameQualified
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun $modelScreen(modifier: Modifier = Modifier, viewModel: $viewModelName = hiltViewModel()) {
    val items by viewModel.uiState.collectAsStateWithLifecycle()
    if (items is $modelUiState.Success) {
        $modelScreen(
            items = (items as $modelUiState.Success).data,
            onSave = viewModel::add$modelName,
            modifier = modifier
        )
    }
}

@Composable
internal fun $modelScreen(
    items: List<String>,
    onSave: (name: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        var text by remember { mutableStateOf("Compose") }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it }
            )

            Button(modifier = Modifier.width(96.dp), onClick = { onSave(text) }) {
                Text("Save")
            }
        }
        items.forEach {
            Text("Saved item: ${"$"}{it}")
        }
    }
}

// Previews

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    $themeName {
        $modelScreen(listOf("Compose", "Room", "Kotlin"), onSave = {})
    }
}

@Preview(showBackground = true, widthDp = 480)
@Composable
private fun PortraitPreview() {
    $themeName {
        $modelScreen(listOf("Compose", "Room", "Kotlin"), onSave = {})
    }
}
"""