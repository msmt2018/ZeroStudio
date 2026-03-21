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
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.importViewBindingClass
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun recyclerViewAdapterKt(
  adapterClassName: String,
  applicationPackage: String?,
  fragmentLayout: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean,
): String {

  val onCreateViewHolderBlock =
    if (isViewBindingSupported)
      """
    return ViewHolder(${layoutToViewBindingClass(fragmentLayout)}.inflate(LayoutInflater.from(parent.context), parent, false))
  """
    else
      """
    val view = LayoutInflater.from(parent.context).inflate(R.layout.${fragmentLayout}, parent, false)
    return ViewHolder(view)
  """

  val viewHolderBlock =
    if (isViewBindingSupported)
      """
    inner class ViewHolder(binding: ${layoutToViewBindingClass(fragmentLayout)}) : RecyclerView.ViewHolder(binding.root) {
        val idView: TextView = binding.itemNumber
        val contentView: TextView = binding.content

        override fun toString(): String {
            return super.toString() + " '" + contentView.text + "'"
        }
    }
  """
    else
      """
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val idView: TextView = view.findViewById(R.id.item_number)
        val contentView: TextView = view.findViewById(R.id.content)

        override fun toString(): String {
            return super.toString() + " '" + contentView.text + "'"
        }
    }
  """
  return """
package ${escapeKotlinIdentifier(packageName)}

import ${getMaterialComponentName("android.support.v7.widget.RecyclerView", useAndroidX)}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
${renderIf(applicationPackage != null) { "import ${escapeKotlinIdentifier(applicationPackage!!)}.R" }}

import ${escapeKotlinIdentifier(packageName)}.placeholder.PlaceholderContent.PlaceholderItem
${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, fragmentLayout, Language.Kotlin)}

/**
 * [RecyclerView.Adapter] that can display a [PlaceholderItem].
 * TODO: Replace the implementation with code for your data type.
 */
class ${adapterClassName}(
        private val values: List<PlaceholderItem>)
    : RecyclerView.Adapter<${adapterClassName}.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        $onCreateViewHolderBlock
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        holder.idView.text = item.id
        holder.contentView.text = item.content
    }

    override fun getItemCount(): Int = values.size

    $viewHolderBlock
}
"""
}
