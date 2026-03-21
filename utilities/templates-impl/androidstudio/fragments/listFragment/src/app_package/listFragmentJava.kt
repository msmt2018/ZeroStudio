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

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun listFragmentJava(
  adapterClassName: String,
  applicationPackage: String?,
  columnCount: Int,
  fragmentClass: String,
  fragmentLayoutList: String,
  packageName: String,
  useAndroidX: Boolean,
) =
  """
package ${packageName};

import android.content.Context;
import android.os.Bundle;
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)};
import ${getMaterialComponentName("android.support.v7.widget.GridLayoutManager", useAndroidX)};
import ${getMaterialComponentName("android.support.v7.widget.LinearLayoutManager", useAndroidX)};
import ${getMaterialComponentName("android.support.v7.widget.RecyclerView", useAndroidX)};
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R;" }}
import ${packageName}.placeholder.PlaceholderContent;

/**
 * A fragment representing a list of Items.
 */
public class ${fragmentClass} extends Fragment {

    // TODO: Customize parameters
    private int mColumnCount = ${columnCount};

    // TODO: Customize parameter argument names
    private static final String ARG_COLUMN_COUNT = "column-count";

    // TODO: Customize parameter initialization
    @SuppressWarnings("unused")
    public static ${fragmentClass} newInstance(int columnCount) {
        ${fragmentClass} fragment = new ${fragmentClass}();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ${fragmentClass}() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.${fragmentLayoutList}, container, false);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }
            recyclerView.setAdapter(new ${adapterClassName}(PlaceholderContent.ITEMS));
        }
        return view;
    }
}
"""
