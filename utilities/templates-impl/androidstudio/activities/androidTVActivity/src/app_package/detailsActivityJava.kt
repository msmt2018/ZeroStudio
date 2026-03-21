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
 * Generates Java source code for DetailsActivity in Android TV project.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun detailsActivityJava(detailsActivity: String, detailsFragmentClass: String, detailsLayoutName: String, packageName: String) =
  """
package ${packageName};

import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;

/**
 * Details activity class that loads LeanbackDetailsFragment class
 */
public class ${detailsActivity} extends FragmentActivity {
    public static final String SHARED_ELEMENT_NAME = "hero";
    public static final String MOVIE = "Movie";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${detailsLayoutName});
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.details_fragment, new ${detailsFragmentClass}())
                .commitNow();
        }
    }

}
"""