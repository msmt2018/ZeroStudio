package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.models.Dependency
import com.itsaky.androidide.templates.base.models.parseDependency
import com.itsaky.androidide.templates.base.modules.android.ManifestActivity
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.VALUES
import com.itsaky.androidide.templates.base.util.SourceWriter
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.audioInterfaceKt
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.glassesActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.composeActivity.themeColorSrc
import com.itsaky.androidide.templates.impl.composeActivity.themeThemeSrc
import com.itsaky.androidide.templates.impl.composeActivity.themeTypeSrc
import com.itsaky.androidide.templates.impl.base.createRecipe

internal fun AndroidModuleTemplateBuilder.aiGlassesActivityRecipe() = createRecipe {
  executor.apply {
    addDependency(Dependency.AndroidX.Core_Ktx)
    addDependency(parseDependency("androidx.activity:activity:1.11.0", tomlAlias = "androidx-activity"))
    addDependency(Dependency.AndroidX.Compose.Activity)
    addDependency(Dependency.AndroidX.Compose.LifeCycle_Runtime_Ktx)
    addDependency(parseDependency("androidx.lifecycle:lifecycle-runtime-compose:2.9.2", tomlAlias = "androidx-lifecycle-runtime-compose"))
    addDependency(parseDependency("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2", tomlAlias = "androidx-lifecycle-viewmodel-compose"))
    addDependency(Dependency.AndroidX.Compose.Material3)
    addDependency(parseDependency("androidx.xr.glimmer:glimmer:1.0.0-alpha02", tomlAlias = "androidx-xr-glimmer"))
    addDependency(parseDependency("androidx.xr.projected:projected:1.0.0-alpha03", tomlAlias = "androidx-xr-projected"))
  }

  sources { writeAiGlassesSources(this) }
  res {
    writeXmlResource("strings", VALUES, source = ::stringsXml)
    writeXmlResource("themes", VALUES, source = ::composeThemesXml)
  }

  manifest {
    addActivity(ManifestActivity(name = "MainActivity", isExported = true, isLauncher = true))
    addActivity(
        ManifestActivity(
            name = "GlassesMainActivity",
            isExported = true,
            isLauncher = true,
            configureAttrs = {
              attribute("android:theme", "@style/${manifest.themeRes}")
              attribute("android:requiredDisplayCategory", "@string/display_category_xr_projected")
            },
        ))
  }
}

private fun AndroidModuleTemplateBuilder.writeAiGlassesSources(writer: SourceWriter) {
  writer.apply {
    writeKtSrc(data.packageName, "MainActivity", source = ::mainActivityKt)
    writeKtSrc(data.packageName, "GlassesMainActivity", source = ::glassesActivityKt)
    writeKtSrc(data.packageName, "AudioInterface", source = ::audioInterfaceKt)
    writeKtSrc("${data.packageName}.ui.theme", "Color", source = ::themeColorSrc)
    writeKtSrc("${data.packageName}.ui.theme", "Theme", source = ::themeThemeSrc)
    writeKtSrc("${data.packageName}.ui.theme", "Type", source = ::themeTypeSrc)
  }
}

private fun AndroidModuleTemplateBuilder.composeThemesXml() =
    """
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <style name="${manifest.themeRes}" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
"""
        .trimIndent()
