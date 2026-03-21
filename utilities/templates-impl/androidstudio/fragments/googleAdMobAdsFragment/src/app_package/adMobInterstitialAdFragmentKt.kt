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

fun adMobInterstitialAdFragmentKt(
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

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)}
${renderIf(applicationPackage != null) { "import ${escapeKotlinIdentifier(applicationPackage!!)}.R" }}
${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, layoutName, Language.Kotlin)}
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.*

class ${fragmentClass} : Fragment() {
    private val TAG = "${fragmentClass.take(23)}"
    private var level: Int = 0
    private var interstitialAd: InterstitialAd? = null
    private lateinit var nextLevelButton: Button
    private lateinit var levelTextView: TextView
${renderIf(isViewBindingSupported) {"""
    private var _binding: ${layoutToViewBindingClass(layoutName)}? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
"""}}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        $onCreateViewBlock
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Create the next level button, which tries to show an interstitial when clicked.
        nextLevelButton = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "next_level_button",
          parentView = "view",)}

        // Create the text view to show the level number.
        levelTextView = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "level",
          parentView = "view",)}
        level = START_LEVEL
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val appContext = activity?.applicationContext ?: return

        nextLevelButton.let {
            it.isEnabled = false
            it.setOnClickListener { showInterstitial(appContext) }
            // Create the InterstitialAd and set the adUnitId (defined in values/strings.xml).
        }
        MobileAds.initialize(appContext) { }
        // Load the InterstitialAd and set the adUnitId (defined in values/strings.xml).
        loadInterstitialAd(appContext)
        Toast.makeText(appContext, TOAST_TEXT, Toast.LENGTH_LONG).show()
    }

    private fun loadInterstitialAd(context: Context) {
      val adRequest = AdRequest.Builder().build()
      InterstitialAd.load(context, getString(R.string.interstitial_ad_unit_id), adRequest,
        object : InterstitialAdLoadCallback() {
          override fun onAdLoaded(ad: InterstitialAd) {
            // The mInterstitialAd reference will be null until
            // an ad is loaded.
            interstitialAd = ad
            nextLevelButton.isEnabled = true
            Toast.makeText(context, "onAdLoaded()", Toast.LENGTH_SHORT).show()
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
              override fun onAdDismissedFullScreenContent() {
                // Called when fullscreen content is dismissed.
                // Make sure to set your reference to null so you don't
                // show it a second time.
                interstitialAd = null
                Log.d(TAG, "The ad was dismissed.")
              }

              override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Called when fullscreen content failed to show.
                // Make sure to set your reference to null so you don't
                // show it a second time.
                interstitialAd = null
                Log.d(TAG, "The ad failed to show.")
              }

              override fun onAdShowedFullScreenContent() {
                // Called when fullscreen content is shown.
                Log.d(TAG, "The ad was shown.")
              }
            }
          }

          override fun onAdFailedToLoad(loadAdError: LoadAdError) {
            // Handle the error
            Log.i(TAG, loadAdError.message)
            interstitialAd = null
            nextLevelButton.isEnabled = true
            val error = String.format(
              Locale.ENGLISH,
              "domain: %s, code: %d, message: %s",
              loadAdError.domain,
              loadAdError.code,
              loadAdError.message
            )
            Toast.makeText(
              getContext(),
              "onAdFailedToLoad() with error: ${'$'}error", Toast.LENGTH_SHORT
            )
            .show()
          }
        })
    }

    private fun showInterstitial(context: Context) {
        // Show the ad if it"s ready. Otherwise toast and reload the ad.
        if (interstitialAd != null) {
          interstitialAd?.show(activity!!)
        } else {
            Toast.makeText(context, "Ad did not load", Toast.LENGTH_SHORT).show()
            goToNextLevel(context)
        }
    }

    private fun goToNextLevel(context: Context) {
        // Show the next level and reload the ad to prepare for the level after.
        levelTextView.text = context.getString(R.string.level_text, ++level)
        if (interstitialAd == null) {
            loadInterstitialAd(context)
        }
    }

    companion object {
        // Remove the below line after defining your own ad unit ID.
        private const val TOAST_TEXT =
            "Test ads are being shown. " + "To show live ads, replace the ad unit ID in res/values/strings.xml with your own ad unit ID."
        private const val START_LEVEL = 1
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
