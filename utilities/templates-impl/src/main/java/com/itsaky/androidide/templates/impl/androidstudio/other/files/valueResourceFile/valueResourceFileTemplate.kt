package com.itsaky.androidide.templates.impl.androidstudio.other.files.valueResourceFile

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.template

val valueResourceFileTemplate
  get() = template {
    templateName = R.string.template_empty_activity
    thumb = R.drawable.template_empty_activity
    recipe = { data: TemplateData -> valueResourceFileRecipe(data as ModuleTemplateData, "values") }
  }
