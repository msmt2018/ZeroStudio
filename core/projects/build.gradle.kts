/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.itsaky.androidide.build.config.BuildConfig

plugins {
    id("kotlin-parcelize")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.com.google.devtools.ksp)
}

android {
  namespace = "${BuildConfig.packageName}.projects"

  // Avoid duplicated source roots being passed to KAPT/Javac for build variants, which can
  // surface as "duplicate class" errors for Kotlin-generated Java stubs.
  sourceSets.configureEach {
    java.setSrcDirs(
        java.srcDirs
            .map { it.canonicalFile }
            .distinct()
    )
  }
}

ksp {
  arguments { 
    arg("eventBusIndex", "${BuildConfig.packageName}.events.ProjectsApiEventsIndex") 
  }
}

dependencies {
  implementation(libs.grpc.android)

  ksp(projects.annotation.processors)
  ksp(libs.zacsweers.auto.service.ksp)
  
  api(projects.event.eventbus)
  api(projects.event.eventbusEvents)
  api(projects.tooling.api)

  implementation(projects.core.common)
  implementation(projects.java.javacServices)
  implementation(projects.logging.logger)
  implementation(projects.utilities.lookup)
  implementation(projects.utilities.shared)
  implementation(projects.xml.utils)


  implementation(libs.common.io)
  implementation(libs.common.kotlin.coroutines.android)
  implementation(libs.google.auto.service.annotations)
  implementation(libs.google.guava)
}
