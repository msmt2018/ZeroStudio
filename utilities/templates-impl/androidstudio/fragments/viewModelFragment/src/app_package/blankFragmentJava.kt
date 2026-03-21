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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.viewModelFragment.src.app_package

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun blankFragmentJava(
  applicationPackage: String?,
  fragmentClass: String,
  layoutName: String,
  packageName: String,
  useAndroidX: Boolean,
  viewModelName: String,
): String {

  val viewModelInitializationBlock =
    if (useAndroidX) "new ViewModelProvider(this).get(${viewModelName}.class);"
    else "new ViewModelProvider(this, new ViewModelProvider.NewInstanceFactory()).get(${viewModelName}.class);"

  return """
package ${packageName};

import ${getMaterialComponentName("android.arch.lifecycle.ViewModelProvider", useAndroidX)};
import android.os.Bundle;
import ${getMaterialComponentName("android.support.annotation.NonNull", useAndroidX)};
import ${getMaterialComponentName("android.support.annotation.Nullable", useAndroidX)};
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)};
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R;" }}

public class ${fragmentClass} extends Fragment {

    public static ${fragmentClass} newInstance() {
        return new ${fragmentClass}();
    }

    private ${viewModelName} mViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.${layoutName}, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = $viewModelInitializationBlock
        // TODO: Use the ViewModel
    }

}
"""
}
