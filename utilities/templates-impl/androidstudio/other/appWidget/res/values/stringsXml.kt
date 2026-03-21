/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.other.appWidget.res.values

import com.android.tools.idea.wizard.template.renderIf

fun stringsXml(configurable: Boolean) =
  """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="appwidget_text">EXAMPLE</string>
${renderIf(configurable) {"""
    <string name="configure">Configure</string>
"""}}
    <string name="add_widget">Add widget</string>
    <string name="app_widget_description">This is an app widget description</string>
</resources>"""
