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

package com.itsaky.androidide.templates.impl.noAndroidXActivity

internal fun BaselineProfileGeneratorSrcKt(packageName: String, activityClass: String): String =
    """
package ${data.packageName}

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class generates a basic startup baseline profile for the target package.
 *
 * We recommend you start with this but add important user flows to the profile to improve their
 * performance. Refer to the
 * [baseline profile documentation](https://d.android.com/topic/performance/baselineprofiles) for
 * more information.
 *
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android
 * Studio or the equivalent `generateBaselineProfile` gradle task:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 *
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 *
 * Check
 * [documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args)
 * for more information about available instrumentation arguments.
 *
 * After you run the generator, you can verify the improvements running the [StartupBenchmarks]
 * benchmark.
 *
 * When using this class to generate a baseline profile, only API 33+ or rooted API 28+ are
 * supported.
 *
 * The minimum required version of androidx.benchmark to generate a baseline profile is 1.2.0.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

  @get:Rule val rule = BaselineProfileRule()

  @Test
  fun generate() {
    // The application id for the running build variant is read from the instrumentation arguments.
    rule.collect(
        packageName =
            InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw Exception("targetAppId not passed as instrumentation runner arg"),

        // See: https://d.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
        includeInStartupProfile = true,
    ) {
      // This block defines the app's critical user journey. Here we are interested in
      // optimizing for app startup. But you can also navigate and scroll through your most
      // important UI.

      // Start default activity for your app
      pressHome()
      startActivityAndWait()

      // TODO Write more interactions to optimize advanced journeys of your app.
      // For example:
      // 1. Wait until the content is asynchronously loaded
      // 2. Scroll the feed content
      // 3. Navigate to detail screen

      // Check UiAutomator documentation for more information how to interact with the app.
      // https://d.android.com/training/testing/other-components/ui-automator
    }
  }
}

  """
        .trim()

internal fun StartupBenchmarksSrcKt(packageName: String, activityClass: String): String =
    """
package ${data.packageName}

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class benchmarks the speed of app startup. Run this benchmark to verify how effective a
 * Baseline Profile is. It does this by comparing [CompilationMode.None], which represents the app
 * with no Baseline Profiles optimizations, and [CompilationMode.Partial], which uses Baseline
 * Profiles.
 *
 * Run this benchmark to see startup measurements and captured system traces for verifying the
 * effectiveness of your Baseline Profiles. You can run it directly from Android Studio as an
 * instrumentation test, or run all benchmarks for a variant, for example benchmarkRelease, with
 * this Gradle task:
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * ```
 *
 * You should run the benchmarks on a physical device, not an Android emulator, because the emulator
 * doesn't represent real world performance and shares system resources with its host.
 *
 * For more information, see the
 * [Macrobenchmark documentation](https://d.android.com/macrobenchmark#create-macrobenchmark) and
 * the
 * [instrumentation arguments documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

  @get:Rule val rule = MacrobenchmarkRule()

  @Test fun startupCompilationNone() = benchmark(CompilationMode.None())

  @Test
  fun startupCompilationBaselineProfiles() =
      benchmark(CompilationMode.Partial(BaselineProfileMode.Require))

  private fun benchmark(compilationMode: CompilationMode) {
    // The application id for the running build variant is read from the instrumentation arguments.
    rule.measureRepeated(
        packageName =
            InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw Exception("targetAppId not passed as instrumentation runner arg"),
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
        measureBlock = {
          startActivityAndWait()

          // TODO Add interactions to wait for when your app is fully drawn.
          // The app is fully drawn when Activity.reportFullyDrawn is called.
          // For Jetpack Compose, you can use ReportDrawn, ReportDrawnWhen and ReportDrawnAfter
          // from the AndroidX Activity library.

          // Check the UiAutomator documentation for more information on how to
          // interact with the app.
          // https://d.android.com/training/testing/other-components/ui-automator
        },
    )
  }
}
  """
        .trim()
