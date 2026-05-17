package com.itsaky.androidide.templates.impl.androidstudio.other.files.layoutResourceFile

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.template

val layoutResourceFileTemplate
  get() = template {
    templateName = R.string.template_empty_activity
    thumb = R.drawable.template_empty_activity
    recipe = { data: TemplateData -> layoutResourceFileRecipe(data as ModuleTemplateData, "layout_main", "LinearLayout") }
  }
