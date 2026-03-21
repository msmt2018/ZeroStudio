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

package com.itsaky.androidide.templates.impl.androidstudio.other.customView.res.values

fun attrsXml(viewClass: String) =
  """
<resources>
    <declare-styleable name="${viewClass}">
        <attr name="exampleString" format="string" />
        <attr name="exampleDimension" format="dimension" />
        <attr name="exampleColor" format="color" />
        <attr name="exampleDrawable" format="color|reference" />
    </declare-styleable>
</resources>
"""
