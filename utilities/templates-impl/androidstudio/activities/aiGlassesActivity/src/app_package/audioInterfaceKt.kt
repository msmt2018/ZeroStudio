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
 * Generates the AudioInterface class acting as an adapter for TextToSpeech capabilities inside AI Glasses.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun audioInterfaceKt(packageName: String) =
  // language=kotlin
  """
package $packageName

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AudioInterface(
    private val context: Context,
    private val initializationMessage: String
) : DefaultLifecycleObserver {
    private lateinit var tts : TextToSpeech
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speak(initializationMessage)
            } else {
                Log.e(TAG, "Initialization failed with status: ${'$'}status")
            }
        }
    }
    fun speak(textToSpeak: String){
        tts.speak(textToSpeak,
            TextToSpeech.QUEUE_ADD,
            null,
            initializationMessage.lowercase().replace(" ", "_"))
    }
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        tts.shutdown()
    }

    companion object {
        private const val TAG = "AudioInterface"
    }
}
"""