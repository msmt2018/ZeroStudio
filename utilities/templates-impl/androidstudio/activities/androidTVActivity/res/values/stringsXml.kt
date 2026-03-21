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

package com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.res.values

/**
 * Returns strings.xml content designed for the Android TV application layout UI components.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun stringsXml(activityClass: String, isNewModule: Boolean): String {
  val labelBlock =
    if (isNewModule) "<string name=\"app_name\">Leanback ${activityClass}</string>"
    else "<string name=\"title_${activityClass}\">Leanback ${activityClass}</string>"
  return """
<resources>
    $labelBlock
    <string name="browse_title">Videos by Your Company</string>
    <string name="related_movies">Related Videos</string>
    <string name="grid_view">Grid View</string>
    <string name="error_fragment">Error Fragment</string>
    <string name="personal_settings">Personal Settings</string>
    <string name="watch_trailer_1">Watch trailer</string>
    <string name="watch_trailer_2">FREE</string>
    <string name="rent_1">Rent By Day</string>
    <string name="rent_2">From $1.99</string>
    <string name="buy_1">Buy and Own</string>
    <string name="buy_2">AT $9.99</string>
    <string name="movie">Movie</string>

    <!-- Error messages -->
    <string name="error_fragment_message">An error occurred</string>
    <string name="dismiss_error">Dismiss</string>
</resources>
"""
}