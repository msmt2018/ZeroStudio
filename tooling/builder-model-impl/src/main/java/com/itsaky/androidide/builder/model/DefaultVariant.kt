/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.TestSuiteArtifact
import com.android.builder.model.v2.ide.Variant
import java.io.File
import java.io.Serializable

/**
 * @author Akash Yadav
 * @author android_zero
 *
 * Update: Added missing interface implementations: testSuiteArtifacts, experimentalProperties to
 * fix compilation errors.
 * Added fromVariant() factory method to support model mapping from AGP v2.
 */
class DefaultVariant : Variant, Serializable {

  companion object {
    private const val serialVersionUID = 1L

    /**
     * Create a DefaultVariant instance from a Variant model.
     *
     * @param variant The Variant model from AGP
     * @return A DefaultVariant instance
     */
    @JvmStatic
    fun fromVariant(variant: Variant): DefaultVariant {
      return DefaultVariant().apply {
        this.name = variant.name
        this.displayName = variant.displayName
        this.isInstantAppCompatible = variant.isInstantAppCompatible
        this.runTestInSeparateProcess = variant.runTestInSeparateProcess
        this.desugaredMethods = variant.desugaredMethods
        this.mainArtifact = DefaultAndroidArtifact.fromArtifact(variant.mainArtifact)

        variant.androidTestArtifact?.let {
          this.androidTestArtifact = DefaultAndroidArtifact.fromArtifact(it)
        }

        variant.testFixturesArtifact?.let {
          this.testFixturesArtifact = DefaultAndroidArtifact.fromArtifact(it)
        }

        variant.testedTargetVariant?.let {
          this.testedTargetVariant = DefaultTestedTargetVariant.fromTestedTargetVariant(it)
        }

        variant.unitTestArtifact?.let {
          this.unitTestArtifact = DefaultJavaArtifact.fromJavaArtifact(it)
        }

        this.deviceTestArtifacts = variant.deviceTestArtifacts.mapValues { (_, artifact) ->
          DefaultAndroidArtifact.fromArtifact(artifact)
        }

        this.hostTestArtifacts = variant.hostTestArtifacts.mapValues { (_, artifact) ->
          DefaultJavaArtifact.fromJavaArtifact(artifact)
        }

        this.testSuiteArtifacts = variant.testSuiteArtifacts.mapValues { (_, artifact) ->
          DefaultTestSuiteArtifact.fromArtifact(artifact)
        }

        this.experimentalProperties = variant.experimentalProperties
      }
    }
  }

  @Deprecated("Contained in deviceTestArtifacts")
  override var androidTestArtifact: DefaultAndroidArtifact? = null
  override var displayName: String = ""
  override var isInstantAppCompatible: Boolean = false
  override var desugaredMethods: List<File> = emptyList()
  override var mainArtifact: DefaultAndroidArtifact = DefaultAndroidArtifact()
  override var name: String = ""
  override var testFixturesArtifact: DefaultAndroidArtifact? = null
  override var testedTargetVariant: DefaultTestedTargetVariant? = null
  @Deprecated("Contained in hostTestArtifacts")
  override var unitTestArtifact: DefaultJavaArtifact? = null
  override var runTestInSeparateProcess: Boolean = false
  override var deviceTestArtifacts: Map<String, AndroidArtifact> = emptyMap()
  override var hostTestArtifacts: Map<String, JavaArtifact> = emptyMap()

  /** The test suite artifacts. Added to satisfy Variant interface requirements. */
  override var testSuiteArtifacts: Map<String, TestSuiteArtifact> = emptyMap()

  /** Experimental properties map. Added to satisfy Variant interface requirements. */
  override var experimentalProperties: Map<String, String> = emptyMap()
}
