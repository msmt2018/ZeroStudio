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
 * Generates the MainActivity providing the startup screen to launch the XR Glasses component.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun mainActivityKt(activityClass: String, glassesActivityClass: String, packageName: String, themeName: String) =
  // language=kotlin
  """
@file:OptIn(ExperimentalProjectedApi::class)

package $packageName

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import $packageName.ui.theme.${themeName}

class $activityClass : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            $themeName {
                ConnectionScreen()
            }
        }
    }
}

@Composable
fun ConnectionScreen() {
    val context = LocalContext.current
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.hello_ai_glasses),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM){
                val scope = rememberCoroutineScope()
                val isGlassesConnected by ProjectedContext.isProjectedDeviceConnected(context, scope.coroutineContext).collectAsStateWithLifecycle(initialValue = false)
                Button(
                    onClick = {
                        val options = ProjectedContext.createProjectedActivityOptions(context)
                        val intent = Intent(context, $glassesActivityClass::class.java)
                        context.startActivity(intent, options.toBundle())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGlassesConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    ),
                    enabled = isGlassesConnected
                ) {
                    Text(
                        text = stringResource(id = R.string.launch),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(id = R.string.status_prefix) + if (isGlassesConnected) stringResource(id = R.string.status_connected) else stringResource(id = R.string.status_disconnected),
                    style = MaterialTheme.typography.titleMedium
                )
            }else {
                Text(
                    text = stringResource(id = R.string.unsupported_android_version),
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun ConnectionScreenPreview() {
    ConnectionScreen()
}
"""