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
package com.itsaky.androidide.templates.impl.androidstudio.activities.aiStarter.src.app_package

/**
 * Provides the boilerplate Kotlin source code for the AI Starter's MainActivity.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun mainActivityKt(activityClass: String, defaultPreview: String, greeting: String, packageName: String, themeName: String) =
  """
package $packageName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import $packageName.ui.theme.${themeName}

class $activityClass : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            $themeName {
                Scaffold( modifier = Modifier.fillMaxSize() ) { innerPadding ->
                    ${greeting}(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ${greeting}(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello ${"$"}name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun ${defaultPreview}() {
    $themeName {
        ${greeting}("Android")
    }
}
"""