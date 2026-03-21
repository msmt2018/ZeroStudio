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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment.src.app_package

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun listFragmentKt(
  adapterClassName: String,
  applicationPackage: String?,
  columnCount: Int,
  fragmentClass: String,
  fragment_layout_list: String,
  packageName: String,
  useAndroidX: Boolean,
) =
  """
package ${escapeKotlinIdentifier(packageName)}

import android.os.Bundle
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)}
import ${getMaterialComponentName("android.support.v7.widget.GridLayoutManager", useAndroidX)}
import ${getMaterialComponentName("android.support.v7.widget.LinearLayoutManager", useAndroidX)}
import ${getMaterialComponentName("android.support.v7.widget.RecyclerView", useAndroidX)}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
${renderIf(applicationPackage != null) { "import ${escapeKotlinIdentifier(applicationPackage!!)}.R" }}
import ${escapeKotlinIdentifier(packageName)}.placeholder.PlaceholderContent

/**
 * A fragment representing a list of Items.
 */
class ${fragmentClass} : Fragment() {

    private var columnCount = ${columnCount}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.${fragment_layout_list}, container, false)

        // Set the adapter
        if (view is RecyclerView) {
            with(view) {
                layoutManager = when {
                    columnCount <= 1 -> LinearLayoutManager(context)
                    else -> GridLayoutManager(context, columnCount)
                }
                adapter = ${adapterClassName}(PlaceholderContent.ITEMS)
            }
        }
        return view
    }

    companion object {

        // TODO: Customize parameter argument names
        const val ARG_COLUMN_COUNT = "column-count"

        // TODO: Customize parameter initialization
        @JvmStatic
        fun newInstance(columnCount: Int) =
                ${fragmentClass}().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_COLUMN_COUNT, columnCount)
                    }
                }
    }
}
"""
