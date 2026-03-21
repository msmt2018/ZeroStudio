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
package com.itsaky.androidide.templates.impl.androidstudio.activities.basicActivity.src

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.impl.activities.common.findViewById
import com.itsaky.androidide.templates.impl.activities.common.layoutToViewBindingClass

/**
 * Generates the Java source code for the second fragment.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun secondFragmentJava(
  packageName: String,
  firstFragmentClass: String,
  secondFragmentClass: String,
  secondFragmentLayoutName: String,
  isViewBindingSupported: Boolean,
): String {
  val onCreateViewBlock =
    if (isViewBindingSupported)
      """
      binding = FragmentSecondBinding.inflate(inflater, container, false);
      return binding.getRoot();
    """
    else "return inflater.inflate(R.layout.$secondFragmentLayoutName, container, false);"

  return """package ${packageName};

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import databinding.FragmentSecondBinding;

public class ${secondFragmentClass} extends Fragment {

    private FragmentSecondBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        $onCreateViewBlock
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonSecond.setOnClickListener(v ->
                NavHostFragment.findNavController(${secondFragmentClass}.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment)
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
"""
}