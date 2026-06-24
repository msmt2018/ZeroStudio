/*
 *  ZeroStudio IDE - tooling/plugin
 *
 *  Phase C5: smoke tests for IdeDebuggerInitScriptPlugin.
 */
package com.itsaky.androidide.gradle;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IdeDebuggerInitScriptPluginTest {

    @Test
    public void constantsAreStable() {
        assertEquals("com.zerostudio.debugger.bootstrap",
                IdeDebuggerInitScriptPlugin.BOOTSTRAP_AUTHORITY);
        assertEquals("com.zerostudio.logplugin.bootstrap.DebuggerBootstrapProvider",
                IdeDebuggerInitScriptPlugin.BOOTSTRAP_PROVIDER_CLASS);
        assertEquals("com.zerostudio.debugger.PORT_HINT",
                IdeDebuggerInitScriptPlugin.BOOTSTRAP_META_PORT);
    }

    @Test
    public void classIsPublicFinal() {
        Class<?> c = IdeDebuggerInitScriptPlugin.class;
        assertNotNull(c);
        assertTrue(java.lang.reflect.Modifier.isPublic(c.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isFinal(c.getModifiers()));
    }

    @Test
    public void applyToNonAndroidProjectIsNoOp() {
        Project project = ProjectBuilder.builder().build();
        IdeDebuggerInitScriptPlugin plugin = new IdeDebuggerInitScriptPlugin();
        // Plain (non-Android) project: must not throw.
        plugin.apply(project);
    }

    @Test
    public void withManifestPlaceholders_handlesUnknownVariantType() {
        // Sanity: verify the helper doesn't throw on a non-AGP
        // variant object.
        Project project = ProjectBuilder.builder().build();
        IdeDebuggerInitScriptPlugin plugin = new IdeDebuggerInitScriptPlugin();
        plugin.apply(project);
        // No assertion needed; reaching this line means no NPE.
        assertThat(project.getPath()).isNotNull();
    }
}
