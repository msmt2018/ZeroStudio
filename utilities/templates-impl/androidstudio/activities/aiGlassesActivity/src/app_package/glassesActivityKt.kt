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
package com.itsaky.androidide.templates.impl.androidstudio.activities.aiGlassesActivity.src.app_package

/**
 * Generates the GlassesActivity handling XR projection and rendering via Glimmer components.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun glassesActivityKt(activityClass: String, packageName: String) =
  // language=kotlin
  """
package $packageName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.xr.glimmer.Button
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.surface

class $activityClass : ComponentActivity() {
    private lateinit var audioInterface : AudioInterface
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioInterface = AudioInterface(
            this,
            getString(R.string.hello_ai_glasses))
        lifecycle.addObserver(audioInterface)
        setContent {
            GlimmerTheme {
                HomeScreen(onClose = {
                    audioInterface.speak("Goodbye!")
                    finish()
                })
            }
        }
    }
    override fun onStart() {
        super.onStart()
        // Do things to make the user aware that this activity is active (for
        // example, play audio), when the display state is off
    }
    override fun onStop() {
        super.onStop()
        //Stop all the data source access
    }
}
@Composable
fun HomeScreen(modifier: Modifier = Modifier, onClose: () -> Unit ) {
    Box(
        modifier = modifier
            .surface(focusable = false).fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            title = {  Text(stringResource(id = R.string.app_name)) },
            action = {
                Button(onClick = {
                    onClose()
                }) {
                    Text(stringResource(id = R.string.close))
                }
            }
        ) {
            Text(stringResource(id = R.string.hello_ai_glasses))
        }
    }
}
@Preview(device = "id:ai_glasses_device", backgroundColor = 0x00FF00)
@Composable
fun DefaultPreview() {
    GlimmerTheme {
        HomeScreen(onClose = {})
    }
}
"""