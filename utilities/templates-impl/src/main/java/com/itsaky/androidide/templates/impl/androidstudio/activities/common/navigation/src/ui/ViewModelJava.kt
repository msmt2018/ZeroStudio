/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.itsaky.androidide.templates.impl.androidstudio.activities.common.navigation.src.ui

import com.itsaky.androidide.templates.getMaterialComponentName

fun viewModelJava(
    packageName: String,
    navFragmentPrefix: String,
    navViewModelClass: String,
    useAndroidX: Boolean = true,
) =
    """
package ${packageName}.ui.${navFragmentPrefix};

import ${getMaterialComponentName("android.arch.lifecycle.LiveData", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.MutableLiveData", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.ViewModel", useAndroidX)};

public class $navViewModelClass extends ViewModel {

    private final MutableLiveData<String> mText;

    public ${navViewModelClass}() {
        mText = new MutableLiveData<>();
        mText.setValue("This is $navFragmentPrefix fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
"""
