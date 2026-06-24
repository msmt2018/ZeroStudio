/*
 *  ZeroStudio IDE - tooling/plugin
 *
 *  Phase C3: smoke tests for IdeLogInitScriptPlugin.
 *
 *  The plugin's main work happens in the Android Gradle Plugin
 *  variant API and can only be exercised in a real AGP build, so
 *  the unit tests focus on:
 *    - the artifact id constant,
 *    - the class is loadable / public,
 *    - the public describeDependency helper is non-throwing when
 *      called against a non-Android project (it should return
 *      null and warn rather than throw).
 */

package com.itsaky.androidide.gradle

import static com.google.common.truth.Truth.assertThat
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4.class)
public class IdeLogInitScriptPluginTest {

  @Test
  public fun artifactId_isStable() {
    // The IDE / build-script orchestrator relies on this constant.
    assertEquals("ide-log-plugin", IdeLogInitScriptPlugin.IDE_LOG_PLUGIN_ARTIFACT)
  }

  @Test
  public fun class_isPublic() {
    val c = IdeLogInitScriptPlugin::class.java
    assertNotNull(c)
    assertThat(java.lang.reflect.Modifier.isPublic(c.modifiers)).isTrue()
    assertThat(java.lang.reflect.Modifier.isFinal(c.modifiers)).isTrue()
  }

  @Test
  public fun apply_toNonAndroidProject_isNoOp() {
    val project: Project = ProjectBuilder.builder().build()
    val plugin = IdeLogInitScriptPlugin()
    // Should not throw on a plain (non-Android) project.
    plugin.apply(project)
  }

  @Test
  public fun describeDependency_unknownArtifact_returnsNull() {
    val project: Project = ProjectBuilder.builder().build()
    val plugin = IdeLogInitScriptPlugin()
    // Without a real repository, the dependency cannot be resolved
    // and the helper returns null.
    val dep = plugin.describeDependency(project)
    assertNull(dep)
  }
}
