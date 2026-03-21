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

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.importViewBindingClass
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.layoutToViewBindingClass

fun recyclerViewAdapterJava(
  adapterClassName: String,
  fragmentLayout: String,
  packageName: String,
  applicationPackage: String?,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean,
): String {

  val onCreateViewHolderBlock =
    if (isViewBindingSupported)
      """
    return new ViewHolder(${layoutToViewBindingClass(fragmentLayout)}.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  """
    else
      """
    return new ViewHolder(LayoutInflater.from(parent.getContext()), parent);
  """

  val viewHolderBlock =
    if (isViewBindingSupported)
      """
    public ViewHolder(${layoutToViewBindingClass(fragmentLayout)} binding) {
      super(binding.getRoot());
      mIdView = binding.itemNumber;
      mContentView = binding.content;
    }
  """
    else
      """
    public ViewHolder(View view) {
      super(view);
      mIdView = (TextView) view.findViewById(R.id.item_number);
      mContentView = (TextView) view.findViewById(R.id.content);
    }
  """

  return """
package ${packageName};

import ${getMaterialComponentName("android.support.v7.widget.RecyclerView", useAndroidX)};
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ${packageName}.placeholder.PlaceholderContent.PlaceholderItem;
${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, fragmentLayout, Language.Java)}

import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link PlaceholderItem}.
 * TODO: Replace the implementation with code for your data type.
 */
public class ${adapterClassName} extends RecyclerView.Adapter<${adapterClassName}.ViewHolder> {

    private final List<PlaceholderItem> mValues;

    public ${adapterClassName}(List<PlaceholderItem> items) {
        mValues = items;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        $onCreateViewHolderBlock
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mIdView.setText(mValues.get(position).id);
        holder.mContentView.setText(mValues.get(position).content);
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView mIdView;
        public final TextView mContentView;
        public PlaceholderItem mItem;

        $viewHolderBlock

        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }
    }
}
"""
}
