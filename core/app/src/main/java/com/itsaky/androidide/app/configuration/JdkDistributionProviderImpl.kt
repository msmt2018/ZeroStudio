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

package com.itsaky.androidide.app.configuration

import android.system.ErrnoException
import android.system.Os
import com.google.auto.service.AutoService
import com.itsaky.androidide.models.JdkDistribution
import com.itsaky.androidide.preferences.internal.BuildPreferences
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.JdkUtils
import java.io.File
import org.slf4j.LoggerFactory

/**
 * Implementation of [IJdkDistributionProvider]. Responsible for finding JDKs, selecting the active
 * one, and injecting it into the global environment.
 *
 * @author android_zero
 * @author Akash Yadav
 */
@AutoService(IJdkDistributionProvider::class)
class JdkDistributionProviderImpl : IJdkDistributionProvider {

  companion object {

    private val log = LoggerFactory.getLogger(JdkDistributionProviderImpl::class.java)
  }

  private var _installedDistributions: List<JdkDistribution>? = null

  override val installedDistributions: List<JdkDistribution>
    get() = _installedDistributions ?: emptyList()

  override fun loadDistributions() {
    _installedDistributions = doLoadDistributions()
  }

  private fun doLoadDistributions(): List<JdkDistribution> {
    return JdkUtils.findJavaInstallations().also { distributions ->

      // set the default value for the 'javaHome' preference
      if (BuildPreferences.javaHome.isBlank() && distributions.isNotEmpty()) {
        var defaultDist = distributions.find {
          it.javaVersion.startsWith(IJdkDistributionProvider.DEFAULT_JAVA_VERSION)
        }

        if (defaultDist == null) {
          // if JDK 17 is not installed, use the first available installation
          defaultDist = distributions[0]
        }

        BuildPreferences.javaHome = defaultDist.javaHome
      }

      var home = File(BuildPreferences.javaHome)
      var java = File(home, "bin/java")

      // the previously selected JDK distribution does not exist
      // check if we have other distributions installed
      if (!home.exists() || !java.exists() || !java.isFile) {
        if (distributions.isNotEmpty()) {
          log.warn(
              "Previously selected java.home does not exists! Falling back to ${distributions[0]}..."
          )
          BuildPreferences.javaHome = distributions[0].javaHome
          home = File(BuildPreferences.javaHome)
          java = File(home, "bin/java")
        } else {
          log.warn("No JDK distribution is available. Skipping JAVA_HOME update.")
          return@also
        }
      }

      if (java.exists() && !java.canExecute()) {
        java.setExecutable(true)
      }

      log.debug("Setting Environment.JAVA_HOME to {}", home.absolutePath)

      Environment.JAVA_HOME = home
      Environment.JAVA = java

      // Critical: Inject the selected JDK into the Native OS Environment
      // This ensures that ProcessBuilder, Terminal, and Shell sessions use THIS specific JDK
      updateNativeEnvironment(Environment.JAVA_HOME, Environment.JAVA)
    }
  }

  /**
   * Updates the native environment variables (JAVA_HOME and PATH) to reflect the currently selected
   * JDK.
   */
  private fun updateNativeEnvironment(javaHome: File, javaBinary: File) {
    try {
      Os.setenv("JAVA_HOME", javaHome.absolutePath, true)

      // Update PATH to prioritize the new JDK's bin directory
      val currentPath = System.getenv("PATH") ?: ""
      val newBinPath = javaHome.resolve("bin").absolutePath

      // Prevent duplicate entries if possible, but ensure priority
      if (!currentPath.startsWith(newBinPath)) {
        val newPath = "$newBinPath:$currentPath"
        Os.setenv("PATH", newPath, true)
      }

      log.info("Global Native Environment updated: JAVA_HOME=${javaHome.absolutePath}")
    } catch (e: ErrnoException) {
      log.error("Failed to update native environment for JDK", e)
    } catch (e: Exception) {
      log.error("Unexpected error updating native environment", e)
    }
  }
}
