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

package com.itsaky.androidide.fragments.onboarding

import com.itsaky.androidide.app.configuration.CpuArch

private val ARM_ONLY = arrayOf(CpuArch.AARCH64, CpuArch.ARM)
private val ARM_AARCH64 = arrayOf(CpuArch.AARCH64)
private val ALL = arrayOf(CpuArch.AARCH64, CpuArch.ARM, CpuArch.X86_64)


/**
 * Android sdk versions.
 *
 * @author Akash Yadav
 */
enum class SdkVersion(val version: String, val supportedArchs: Array<CpuArch>) {

  SDK_33_0_1("33.0.1", ARM_ONLY),
  SDK_33_0_3("33.0.3", ARM_ONLY),
  SDK_34_0_0("34.0.0", ARM_ONLY),
  SDK_34_0_1("34.0.1", ARM_ONLY),
  SDK_34_0_3("34.0.3", ARM_ONLY),
  SDK_34_0_4("34.0.4", ALL),
  SDK_35_0_0("35.0.0", ALL),
  SDK_35_0_1("35.0.1", ARM_ONLY),
  SDK_35_0_2("35.0.2", ALL),
  SDK_36_0_0("36.0.0", ALL),
  ;

  val displayName = "SDK $version"

  companion object {

    @JvmStatic
    fun fromDisplayName(displayName: CharSequence) =
      entries.first { it.displayName.contentEquals(displayName) }

    @JvmStatic
    fun fromVersion(version: CharSequence) = entries.first { it.version.contentEquals(version) }
  }
}

/**
 * Android NDK versions.
 *
 * @author android_zero
 */
enum class NdkVersion(val version: String, val supportedArchs: Array<CpuArch>) {
    R17("17.2.4988734",ARM_AARCH64),
    R18("18.1.5063045",ARM_AARCH64),
    R19("19.2.5345600",ARM_AARCH64),
    R20("20.1.5948944",ARM_AARCH64),
    R21("21.4.7075529",ARM_AARCH64),
    R22("22.1.7171670",ARM_AARCH64),
    R23("23.2.8568313",ARM_AARCH64),
    R24("24.0.8215888",ARM_AARCH64),
    R25("25.2.9519653",ARM_AARCH64),
    R26("26.3.11579264", ALL),
    R27A("27.1.12297006",ARM_AARCH64),
    R27B("27.3.13750724", ALL),
    R28A2("28.2.13676358.A2",ARM_AARCH64),
    R28A1("28.2.13676358.A1", ALL),
    R29A("29.0.13113456",ARM_AARCH64),
    R29B("29.0.14033849", ALL),
    R29C("29.0.14206865",ARM_AARCH64);

    val displayName = "NDK $version"

  companion object {

    @JvmStatic
    fun fromDisplayName(displayName: CharSequence) =
      entries.first { it.displayName.contentEquals(displayName) }

    @JvmStatic
    fun fromVersion(version: CharSequence) = entries.first { it.version.contentEquals(version) }
  }
}

/**
 * Android Cmake versions.
 *
 * @author android_zero
 */
enum class CmakeVersion(val version: String, val supportedArchs: Array<CpuArch>) {
    V3_10_2("3.10.2",ARM_AARCH64),
    V3_18_1("3.18.1",ARM_AARCH64), 
    V3_22_1("3.22.1",ARM_AARCH64), 
    V3_25_1("3.25.1",ARM_AARCH64), 
    V3_30_3("3.30.3",ALL), 
    V3_30_4("3.30.4",ALL), 
    V3_30_5("3.30.5",ALL), 
    V3_31_0("3.31.0",ALL), 
    V3_31_1("3.31.1",ALL), 
    V3_31_4("3.31.4",ALL), 
    V3_31_5("3.31.5",ALL),
    V3_31_6("3.31.6",ALL),
    V4_0_2("4.0.2",ALL),
    V4_0_3("4.0.3",ALL),
    V4_1_0("4.1.0",ALL), 
    V4_1_1("4.1.1",ALL), 
    V4_1_2("4.1.2",ALL);

    val displayName = "CMake $version"

    companion object {

      @JvmStatic
      fun fromDisplayName(displayName: CharSequence) =
        entries.first { it.displayName.contentEquals(displayName) }

      @JvmStatic
      fun fromVersion(version: CharSequence) = entries.first { it.version.contentEquals(version) }
  }
}


/**
 * JDK versions.
 *
 * @author Akash Yadav
 */
enum class JdkVersion(val version: String) {

  JDK_17("17"),
  JDK_21("21"),
  ;

  val displayName = "JDK $version"

  companion object {

    @JvmStatic
    fun fromDisplayName(displayName: CharSequence) =
      entries.first { it.displayName.contentEquals(displayName) }

    @JvmStatic
    fun fromVersion(version: CharSequence) = entries.first { it.version.contentEquals(version) }
  }
}