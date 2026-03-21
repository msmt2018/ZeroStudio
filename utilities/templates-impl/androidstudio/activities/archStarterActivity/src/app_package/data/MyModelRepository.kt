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

package com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.data

import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.ArchStarterActivityTemplateVariables

/**
 * Generates the repository implementation handling data model interactions.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun ArchStarterActivityTemplateVariables.repository() =
  """
package $dataPackage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import $dataModelQualified
import $modelDaoQualified
import javax.inject.Inject

interface $repositoryName {
    val myModels: Flow<List<String>>

    suspend fun add(name: String)
}

class Default$repositoryName @Inject constructor(
    private val $modelDaoVar: $modelDao
) : $repositoryName {

    override val myModels: Flow<List<String>> =
        $modelDaoVar.get${modelName}s().map { items -> items.map { it.name } }

    override suspend fun add(name: String) {
        $modelDaoVar.insert$modelName($modelName(name = name))
    }
}
"""