package com.itsaky.androidide.templates.impl.androidstudio.other.androidManifest

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.Template
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.template

val androidManifestTemplate: Template<*>
  get() = template {
    name = R.string.template_empty_activity
    thumb = R.drawable.template_empty_activity
    recipe = { data: TemplateData -> androidManifestRecipe(data as ModuleTemplateData, "main") }
  }
