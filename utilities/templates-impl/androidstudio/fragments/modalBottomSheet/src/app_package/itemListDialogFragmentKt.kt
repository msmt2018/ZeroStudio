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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.modalBottomSheet.src.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.importViewBindingClass
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun itemListDialogFragmentKt(
  applicationPackage: String?,
  columnCount: Int,
  fragmentClass: String,
  itemLayout: String,
  listLayout: String,
  objectKind: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean,
): String {
  val layoutManagerImport =
    if (columnCount == 1) "import ${getMaterialComponentName("android.support.v7.widget.LinearLayoutManager", useAndroidX)}"
    else "import ${getMaterialComponentName("android.support.v7.widget.GridLayoutManager", useAndroidX)}"

  val layoutManagerInstantiation =
    if (columnCount == 1) "activity?.findViewById<RecyclerView>(R.id.list)?.layoutManager = LinearLayoutManager(context)"
    else "list.layoutManager = GridLayoutManager(context, ${columnCount})"

  val onCreateViewBlock =
    if (isViewBindingSupported)
      """
      _binding = ${layoutToViewBindingClass(listLayout)}.inflate(inflater, container, false)
      return binding.root
  """
    else "return inflater.inflate(R.layout.$listLayout, container, false)"

  val viewHolderBlock =
    if (isViewBindingSupported)
      """
    private inner class ViewHolder internal constructor(binding: ${layoutToViewBindingClass(itemLayout)})
        : RecyclerView.ViewHolder(binding.root) {

        internal val text: TextView = binding.text
    }
  """
    else
      """
    private inner class ViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup)
        : RecyclerView.ViewHolder(inflater.inflate(R.layout.${itemLayout}, parent, false)) {

        internal val text: TextView = itemView.findViewById(R.id.text)
    }
  """

  val onCreateViewHolderBlock =
    if (isViewBindingSupported)
      """
    return ViewHolder(${layoutToViewBindingClass(itemLayout)}.inflate(LayoutInflater.from(parent.context), parent, false))
  """
    else
      """
    return ViewHolder(LayoutInflater.from(parent.context), parent)
  """

  return """
package ${escapeKotlinIdentifier(packageName)}

import android.os.Bundle
import ${getMaterialComponentName("android.support.design.widget.BottomSheetDialog", useAndroidX)}Fragment
$layoutManagerImport
import ${getMaterialComponentName("android.support.v7.widget.RecyclerView", useAndroidX)}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
${renderIf(applicationPackage != null) { "import ${escapeKotlinIdentifier(applicationPackage!!)}.R" }}
${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, itemLayout, Language.Kotlin)}
${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, listLayout, Language.Kotlin)}

// TODO: Customize parameter argument names
const val ARG_ITEM_COUNT = "item_count"

/**
 *
 * A fragment that shows a list of items as a modal bottom sheet.
 *
 * You can show this modal bottom sheet from your activity like this:
 * <pre>
 *    ${fragmentClass}.newInstance(30).show(supportFragmentManager, "dialog")
 * </pre>
 */
class ${fragmentClass} : BottomSheetDialogFragment() {

${renderIf(isViewBindingSupported) {"""
    private var _binding: ${layoutToViewBindingClass(listLayout)}? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
"""}}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        $onCreateViewBlock
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        $layoutManagerInstantiation
        activity?.findViewById<RecyclerView>(R.id.list)?.adapter = arguments?.getInt(ARG_ITEM_COUNT)?.let { ItemAdapter(it) }
    }

    $viewHolderBlock

    private inner class ${objectKind}Adapter internal constructor(private val mItemCount: Int) : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            $onCreateViewHolderBlock
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.text.text = position.toString()
        }

        override fun getItemCount(): Int {
            return mItemCount
        }
    }

    companion object {

        // TODO: Customize parameters
        fun newInstance(itemCount: Int): ${fragmentClass} =
                ${fragmentClass}().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_ITEM_COUNT, itemCount)
                    }
                }

    }

${renderIf(isViewBindingSupported) {"""
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
"""}}
}
"""
}
