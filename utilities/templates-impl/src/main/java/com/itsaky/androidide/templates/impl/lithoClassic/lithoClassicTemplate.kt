package com.itsaky.androidide.templates.impl.lithoClassic

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.lithoClassicDependencies
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.LAYOUT
import com.itsaky.androidide.templates.base.util.SourceWriter
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.emptyThemesAndColors
import com.itsaky.androidide.templates.impl.base.writeMainActivity
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun lithoClassicProject(): ProjectTemplate = baseProjectImpl {
  templateName = string.template_litho_classic
  thumb = R.drawable.template_empty_activity
  description = string.title_template_description_litho_classic

  defaultAppModule {
    recipe = createRecipe {
      lithoClassicDependencies()

      sources { writeLithoClassicSources(this) }

      res {
        writeXmlResource("activity_main", LAYOUT) { "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" />" }
        emptyThemesAndColors()
      }
    }
  }
}

private fun AndroidModuleTemplateBuilder.writeLithoClassicSources(writer: SourceWriter) {
  writeMainActivity(writer, ::mainActivityKt, ::mainActivityJava)
  writeKtSrc(data.packageName, "MyApplication", source = ::appKt)
  writeJavaSrc(data.packageName, "MyApplication", source = ::appJava)
}
