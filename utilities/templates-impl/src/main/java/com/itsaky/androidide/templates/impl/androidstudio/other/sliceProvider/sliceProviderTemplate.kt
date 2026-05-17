package com.itsaky.androidide.templates.impl.androidstudio.other.sliceProvider

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.template

val sliceProviderTemplate
  get() = template {
    templateName = R.string.template_empty_activity
    thumb = R.drawable.template_empty_activity
    recipe = {
      data: TemplateData ->
      sliceProviderRecipe(data as ModuleTemplateData, "MySliceProvider", "com.example.provider", "example.com", "/")
    }
  }
