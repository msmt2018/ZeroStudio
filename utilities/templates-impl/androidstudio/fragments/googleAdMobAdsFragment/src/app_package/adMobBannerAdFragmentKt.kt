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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.src.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.findViewById
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.importViewBindingClass
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun adMobBannerAdFragmentKt(
  applicationPackage: String?,
  fragmentClass: String,
  layoutName: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean,
): String {

  val onCreateViewBlock =
    if (isViewBindingSupported)
      """
      _binding = ${layoutToViewBindingClass(layoutName)}.inflate(inflater, container, false)
      return binding.root
  """
    else "return inflater.inflate(R.layout.$layoutName, container, false)"

  return """
package ${escapeKotlinIdentifier(packageName)}

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView

import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.Toast
${renderIf(applicationPackage != null) { "import ${escapeKotlinIdentifier(applicationPackage!!)}.R" }}
${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, layoutName, Language.Kotlin)}

class ${fragmentClass} : Fragment() {

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
    ): View? {
        $onCreateViewBlock
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load an ad into the AdMob banner view.
        val adView: AdView = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "ad_view",
          parentView = "view",)}
        val adRequest = AdRequest.Builder()
            .setRequestAgent("android_studio:ad_template").build()
        adView.loadAd(adRequest)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val appContext = activity?.applicationContext ?: return
        Toast.makeText(appContext, TOAST_TEXT, Toast.LENGTH_LONG).show()
    }

    companion object {
        // Remove the below line after defining your own ad unit ID.
        private const val TOAST_TEXT =
            "Test ads are being shown. " + "To show live ads, replace the ad unit ID in res/values/strings.xml with your own ad unit ID."
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
