package com.itsaky.androidide.templates.impl.androidstudio.other.files.aidlFile

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.template

val aidlFileTemplate
  get() = template {
    templateName = R.string.template_empty_activity
    thumb = R.drawable.template_empty_activity
    recipe = { data: TemplateData -> aidlFileRecipe(data as ModuleTemplateData, "IMyAidl") }
  }
