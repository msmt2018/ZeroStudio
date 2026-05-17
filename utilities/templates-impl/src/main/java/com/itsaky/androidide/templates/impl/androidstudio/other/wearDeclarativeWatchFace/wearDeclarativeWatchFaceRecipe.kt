package com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.VALUES
import com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace.res.raw.rawWatchFaceXml
import com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.other.wearDeclarativeWatchFace.res.xml.watchFaceInfoXml
import com.itsaky.androidide.templates.impl.base.createRecipe
import java.io.File

fun AndroidModuleTemplateBuilder.wearDeclarativeWatchFaceRecipe() {
  recipe = createRecipe {
    res {
      writeXmlResource("strings", VALUES, source = ::stringsXml)

      val rawDir = File(mainResDir(), "raw").also { it.mkdirs() }
      val xmlDir = File(mainResDir(), "xml").also { it.mkdirs() }

      executor.save(rawWatchFaceXml(), File(rawDir, "watchface.xml"))
      executor.save(watchFaceInfoXml(), File(xmlDir, "watch_face_info.xml"))

      copyAssetsRecursively("templates/wear-watchface/drawable", File(mainResDir(), "drawable"))
    }

    executor.save(androidManifestXml(), File(mainManifestDir(), "AndroidManifest.xml"))
  }
}
