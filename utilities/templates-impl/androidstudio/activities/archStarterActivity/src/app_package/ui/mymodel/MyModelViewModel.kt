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
 * Generates the Hilt ViewModel serving the state holder for the architecture starter template.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun ArchStarterActivityTemplateVariables.viewModel() =
  """
package $modelPackage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import $repositoryNameQualified
import $modelUiStateQualified.Error
import $modelUiStateQualified.Loading
import $modelUiStateQualified.Success
import javax.inject.Inject

@HiltViewModel
class $viewModelName @Inject constructor(
    private val $repositoryVarName: $repositoryName
) : ViewModel() {

    val uiState: StateFlow<$modelUiState> = $repositoryVarName
        .myModels.map<List<String>, $modelUiState>(::Success)
        .catch { emit(Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Loading)

    fun add$modelName(name: String) {
        viewModelScope.launch {
            $repositoryVarName.add(name)
        }
    }
}

sealed interface $modelUiState {
    object Loading : $modelUiState
    data class Error(val throwable: Throwable) : $modelUiState
    data class Success(val data: List<String>) : $modelUiState
}
"""