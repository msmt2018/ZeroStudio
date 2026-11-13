/*
 * This file is part of ZeroStudio.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */

package com.itsaky.androidide.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ConvertUtils
import com.google.android.material.shape.CornerFamily
import com.itsaky.androidide.databinding.LayoutTemplateListItemBinding
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.Template

/**
 * RecyclerView.Adapter for showing templates in a grid layout within a category tab.
 *
 * This adapter binds `ProjectTemplate` data to the `layout_template_list_item` view. It displays
 * the template's preview image, title, and description.
 *
 * @param templates The list of templates to display.
 * @param onClick A lambda function to be executed when a template item is clicked.
 * @author android_zero
 */
class TemplateGridAdapter(
    private val templates: List<ProjectTemplate>,
    private val onClick: (Template<*>) -> Unit,
) : RecyclerView.Adapter<TemplateGridAdapter.ViewHolder>() {

  class ViewHolder(internal val binding: LayoutTemplateListItemBinding) :
      RecyclerView.ViewHolder(binding.root)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding =
        LayoutTemplateListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return ViewHolder(binding)
  }

  override fun getItemCount(): Int = templates.size

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val template = templates[position]
    holder.binding.apply {
      // Set template title
      templateName.text = root.context.getString(template.templateName)

      // Set template preview image
      templateIcon.setImageResource(template.thumb)
      templateIcon.shapeAppearanceModel =
          templateIcon.shapeAppearanceModel
              .toBuilder()
              .setAllCorners(CornerFamily.ROUNDED, ConvertUtils.dp2px(8f).toFloat())
              .build()

      // Set template description if available
      val descriptionRes = template.description
      if (descriptionRes != null) {
        templateDescription.text = root.context.getString(descriptionRes)
        templateDescription.visibility = View.VISIBLE
      } else {
        templateDescription.visibility = View.GONE
      }

      root.setOnClickListener { onClick.invoke(template) }
    }
  }
}
