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
package com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.data.di

import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.ArchStarterActivityTemplateVariables

/**
 * Generates the Hilt DataModule to bind interface repositories to their implementations.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun ArchStarterActivityTemplateVariables.dataModule() =
  """
package $dataDiPackage

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import $repositoryNameQualified
import $dataPackage.Default$repositoryName
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Singleton
    @Binds
    fun binds$repositoryName(
        $repositoryVarName: Default$repositoryName
    ): $repositoryName
}

class Fake$repositoryName @Inject constructor() : $repositoryName {
    override val myModels: Flow<List<String>> = flowOf(fakeMyModels)

    override suspend fun add(name: String) {
        throw NotImplementedError()
    }
}

val fakeMyModels = listOf("One", "Two", "Three")
"""