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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment.src.app_package.ui.login

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun loginViewModelFactoryJava(packageName: String, useAndroidX: Boolean) =
  """
package ${packageName}.ui.login;

import ${getMaterialComponentName("android.arch.lifecycle.ViewModel", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.ViewModelProvider", useAndroidX)};
import ${getMaterialComponentName("android.support.annotation.NonNull", useAndroidX)};

import ${packageName}.data.LoginDataSource;
import ${packageName}.data.LoginRepository;

/**
 * ViewModel provider factory to instantiate LoginViewModel.
 * Required given LoginViewModel has a non-empty constructor
 */
public class LoginViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(LoginViewModel.class)) {
            return (T) new LoginViewModel(LoginRepository.getInstance(new LoginDataSource()));
        } else {
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
"""
