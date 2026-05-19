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

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.escapeKotlinIdentifier
import com.itsaky.androidide.templates.getMaterialComponentName
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.findViewById
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.importViewBindingClass
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.layoutToViewBindingClass
import com.itsaky.androidide.templates.renderIf

fun firstFragmentKt(
    packageName: String,
    applicationPackage: String?,
    firstFragmentClass: String,
    navFragmentPrefix: String,
    navViewModelClass: String,
    useAndroidX: Boolean,
    isViewBindingSupported: Boolean,
): String {
  val viewModelInitializationBlock =
      if (useAndroidX) "ViewModelProvider(this).get(${navViewModelClass}::class.java)"
      else
          "ViewModelProvider(this, ViewModelProvider.NewInstanceFactory()).get(${navViewModelClass}::class.java)"

  val layoutName = "fragment_${navFragmentPrefix}"
  val onCreateViewBlock =
      if (isViewBindingSupported)
          """
    _binding = ${layoutToViewBindingClass(layoutName)}.inflate(inflater, container, false)
    val root: View = binding.root
  """
      else "View root = inflater.inflate(R.layout.$layoutName, container, false);"

  return """
package ${escapeKotlinIdentifier(packageName)}.ui.${navFragmentPrefix}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)}
import ${getMaterialComponentName("android.arch.lifecycle.ViewModelProvider", useAndroidX)}
${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, layoutName, Language.Kotlin)}

class $firstFragmentClass : Fragment() {

${renderIf(isViewBindingSupported) {"""
  private var _binding: ${layoutToViewBindingClass(layoutName)}? = null
  // This property is only valid between onCreateView and
  // onDestroyView.
  private val binding get() = _binding!!
"""}}

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val ${navFragmentPrefix}ViewModel =
            $viewModelInitializationBlock
    $onCreateViewBlock
    val textView: TextView = ${findViewById(
      language = Language.Kotlin,
      isViewBindingSupported = isViewBindingSupported,
      id = "text_${navFragmentPrefix}",
      className = "TextView",
      parentView = "root",)}
    ${navFragmentPrefix}ViewModel.text.observe(viewLifecycleOwner) {
      textView.text = it
    }
    return root
  }

${renderIf(isViewBindingSupported) {"""
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
"""}}
}
"""
}
