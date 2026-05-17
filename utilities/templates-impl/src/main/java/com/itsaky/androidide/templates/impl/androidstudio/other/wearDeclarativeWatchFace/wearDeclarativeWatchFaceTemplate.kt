package com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun wearDeclarativeWatchFaceProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_empty_activity
  thumb = R.drawable.template_empty_activity
  description = R.string.template_empty_activity

  defaultAppModule { wearDeclarativeWatchFaceRecipe() }
}
