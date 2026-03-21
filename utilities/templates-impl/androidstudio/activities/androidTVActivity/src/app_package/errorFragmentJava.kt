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

package com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package

/**
 * Generates Java source code for ErrorFragment in Android TV project.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun errorFragmentJava(minApiLevel: Int, packageName: String): String {
  val getDrawableArgBlock = if (minApiLevel >= 23) "getContext()" else "getActivity()"
  return """
package ${packageName};

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.leanback.app.ErrorSupportFragment;

/**
 * This class demonstrates how to extend ErrorSupportFragment.
 */
public class ErrorFragment extends ErrorSupportFragment {
    private static final String TAG = "ErrorFragment";
    private static final boolean TRANSLUCENT = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setTitle(getResources().getString(R.string.app_name));
    }

    void setErrorContent() {
        setImageDrawable(ContextCompat.getDrawable($getDrawableArgBlock, androidx.leanback.R.drawable.lb_ic_sad_cloud));
        setMessage(getResources().getString(R.string.error_fragment_message));
        setDefaultBackground(TRANSLUCENT);

        setButtonText(getResources().getString(R.string.dismiss_error));
        setButtonClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        getFragmentManager().beginTransaction().remove(ErrorFragment.this).commit();
                    }
                });
    }
}
"""
}