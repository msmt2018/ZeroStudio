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
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.findViewById
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.importViewBindingClass
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun adMobInterstitialAdFragmentJava(
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
      binding = ${layoutToViewBindingClass(layoutName)}.inflate(inflater, container, false);
      return binding.getRoot();
  """
    else "return inflater.inflate(R.layout.$layoutName, container, false);"

  return """
package ${packageName};

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import ${getMaterialComponentName("android.support.annotation.NonNull", useAndroidX)};
import ${getMaterialComponentName("android.support.annotation.Nullable", useAndroidX)};
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)};

${renderIf(applicationPackage != null) { "import ${applicationPackage}.R;" }}
${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, layoutName, Language.Java)}
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

import java.util.Locale;

public class ${fragmentClass} extends Fragment {
    // Remove the below line after defining your own ad unit ID.
    private static final String TOAST_TEXT = "Test ads are being shown. "
            + "To show live ads, replace the ad unit ID in res/values/strings.xml with your own ad unit ID.";
    private static final String TAG = "${fragmentClass.take(23)}";

    private static final int START_LEVEL = 1;
    private int mLevel;
    private Button mNextLevelButton;
    private InterstitialAd mInterstitialAd;
    private TextView mLevelTextView;
${renderIf(isViewBindingSupported) {"""
    private ${layoutToViewBindingClass(layoutName)} binding;
"""}}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        $onCreateViewBlock
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Create the next level button, which tries to show an interstitial when clicked.
        mNextLevelButton = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "next_level_button",
          parentView = "view",)};

        // Create the text view to show the level number.
        mLevelTextView = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "level",
          parentView = "view",)};
        mLevel = START_LEVEL;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (getActivity() == null || getActivity().getApplicationContext() == null) return;
        final Context appContext = getActivity().getApplicationContext();

        mNextLevelButton.setEnabled(false);
        mNextLevelButton.setOnClickListener(view -> showInterstitial(appContext));

        MobileAds.initialize(appContext, initializationStatus -> {
        });
        // Load the InterstitialAd and set the adUnitId (defined in values/strings.xml).
        loadInterstitialAd(appContext);

        // Toasts the test ad message on the screen.
        // Remove this after defining your own ad unit ID.
        Toast.makeText(appContext, TOAST_TEXT, Toast.LENGTH_LONG).show();
    }

    private void loadInterstitialAd(Context context) {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(context, getString(R.string.interstitial_ad_unit_id), adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        // The mInterstitialAd reference will be null until
                        // an ad is loaded.
                        mInterstitialAd = interstitialAd;
                        mNextLevelButton.setEnabled(true);

                        Toast.makeText(getContext(), "onAdLoaded()", Toast.LENGTH_SHORT).show();
                        interstitialAd.setFullScreenContentCallback(
                                new FullScreenContentCallback() {
                                    @Override
                                    public void onAdDismissedFullScreenContent() {
                                        // Called when fullscreen content is dismissed.
                                        // Make sure to set your reference to null so you don't
                                        // show it a second time.
                                        mInterstitialAd = null;
                                        Log.d(TAG, "The ad was dismissed.");
                                    }

                                    @Override
                                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                        // Called when fullscreen content failed to show.
                                        // Make sure to set your reference to null so you don't
                                        // show it a second time.
                                        mInterstitialAd = null;
                                        Log.d(TAG, "The ad failed to show.");
                                    }

                                    @Override
                                    public void onAdShowedFullScreenContent() {
                                        // Called when fullscreen content is shown.
                                        Log.d(TAG, "The ad was shown.");
                                    }
                                });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        // Handle the error
                        Log.i(TAG, loadAdError.getMessage());
                        mInterstitialAd = null;
                        mNextLevelButton.setEnabled(true);

                        String error = String.format(
                                Locale.ENGLISH,
                                "domain: %s, code: %d, message: %s",
                                loadAdError.getDomain(),
                                loadAdError.getCode(),
                                loadAdError.getMessage());
                        Toast.makeText(
                                getContext(),
                                "onAdFailedToLoad() with error: " + error, Toast.LENGTH_SHORT)
                                .show();
                    }
                });
    }

    private void showInterstitial(Context context) {
        // Show the ad if it"s ready. Otherwise toast and reload the ad.
        if (mInterstitialAd != null && getActivity() != null) {
            mInterstitialAd.show(getActivity());
        } else {
            Toast.makeText(context, "Ad did not load", Toast.LENGTH_SHORT).show();
            goToNextLevel(context);
        }
    }

    private void goToNextLevel(Context context) {
        // Show the next level and reload the ad to prepare for the level after.
        mLevelTextView.setText(context.getString(R.string.level_text, ++mLevel));
        if (mInterstitialAd == null) {
            loadInterstitialAd(context);
        }
    }

${renderIf(isViewBindingSupported) {"""
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
"""}}
}"""
}
