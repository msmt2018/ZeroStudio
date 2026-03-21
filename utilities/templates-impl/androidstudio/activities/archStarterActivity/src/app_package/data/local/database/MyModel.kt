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
package com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.src.app_package.data.local.database

import com.itsaky.androidide.templates.impl.androidstudio.activities.archStarterActivity.ArchStarterActivityTemplateVariables

/**
 * Generates the Room Entity and DAO mappings for the architecture project.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun ArchStarterActivityTemplateVariables.dataModel() =
  """
package $databasePackage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity
data class $modelName(
    val name: String
) {
    @PrimaryKey(autoGenerate = true)
    var uid: Int = 0
}

@Dao
interface $modelDao {
    @Query("SELECT * FROM ${modelName.lowercase()} ORDER BY uid DESC LIMIT 10")
    fun get${modelName}s(): Flow<List<$modelName>>

    @Insert
    suspend fun insert$modelName(item: $modelName)
}
"""