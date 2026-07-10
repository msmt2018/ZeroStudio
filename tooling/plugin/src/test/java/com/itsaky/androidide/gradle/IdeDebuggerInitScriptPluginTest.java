/*
 *  ZeroStudio IDE - tooling/plugin
 *
 *  Phase C5 + 子项目 9d + 10: smoke tests for IdeDebuggerInitScriptPlugin.
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

import java.lang.reflect.Method;
import java.util.Map;

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
        assertEquals("ide-debugger-host",
                IdeDebuggerInitScriptPlugin.IDE_DEBUGGER_HOST_ARTIFACT);
        assertEquals("ideLocalServerName",
                IdeDebuggerInitScriptPlugin.IDE_LOCAL_SERVER_NAME_PLACEHOLDER);
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

    // ---- computeLocalServerName 纯函数测试 ----
    // 名字现在是固定常量 "ide-debug-bridge", 跟 IDE 端
    // HostBridgeServer.WELL_KNOWN_NAME 对齐 (IDE 与宿主 app uid 不同,
    // per-uid / per-project 名字双方对不齐, 改用固定约定常量)。
    // group/name 参数保留兼容但不参与名字生成。

    @Test
    public void computeLocalServerName_normalGroupName() throws Exception {
        String name = invokeComputeLocalServerName("com.example", "myapp");
        assertEquals("ide-debug-bridge", name);
    }

    @Test
    public void computeLocalServerName_nullGroup_usesDefault() throws Exception {
        String name = invokeComputeLocalServerName(null, "myapp");
        assertEquals("ide-debug-bridge", name);
    }

    @Test
    public void computeLocalServerName_emptyGroup_usesDefault() throws Exception {
        String name = invokeComputeLocalServerName("", "myapp");
        assertEquals("ide-debug-bridge", name);
    }

    @Test
    public void computeLocalServerName_emptyName_usesApp() throws Exception {
        String name = invokeComputeLocalServerName("com.example", "");
        assertEquals("ide-debug-bridge", name);
    }

    @Test
    public void computeLocalServerName_uppercaseIsLowercased() throws Exception {
        String name = invokeComputeLocalServerName("Com.Example", "MyApp");
        assertEquals("ide-debug-bridge", name);
    }

    @Test
    public void computeLocalServerName_truncatesAt64() throws Exception {
        // 固定名字不依赖 group/name, 不截断; 验证长输入仍返回固定常量
        String longGroup = "a".repeat(100);
        String longName = "b".repeat(100);
        String name = invokeComputeLocalServerName(longGroup, longName);
        assertEquals("ide-debug-bridge", name);
    }

    // ---- computeBootstrapPlaceholders 纯函数测试 ----

    @Test
    public void computeBootstrapPlaceholders_basic() throws Exception {
        Map<String, String> ph = invokeComputeBootstrapPlaceholders("com.example", "myapp", 0, null);
        assertEquals(5, ph.size());
        assertEquals("ide-debug-bridge", ph.get("ideLocalServerName"));
        assertEquals("sdk=0", ph.get("ideDebuggerExtras"));
        assertEquals("", ph.get("ideDebuggerPreheatBreakpointsRaw"));
    }

    @Test
    public void computeBootstrapPlaceholders_withSdkInt() throws Exception {
        Map<String, String> ph = invokeComputeBootstrapPlaceholders("com.example", "myapp", 33, null);
        assertEquals("sdk=33", ph.get("ideDebuggerExtras"));
    }

    @Test
    public void computeBootstrapPlaceholders_withPreheatBreakpoints() throws Exception {
        String raw = "src=A.kt:10:5;src=B.kt:20:0";
        Map<String, String> ph = invokeComputeBootstrapPlaceholders("com.example", "myapp", 33, raw);
        assertEquals(raw, ph.get("ideDebuggerPreheatBreakpointsRaw"));
    }

    @Test
    public void computeBootstrapPlaceholders_includesBootstrapAuthority() throws Exception {
        Map<String, String> ph = invokeComputeBootstrapPlaceholders("com.example", "myapp", 0, null);
        assertEquals("com.zerostudio.debugger.bootstrap",
                ph.get("com.zerostudio.logplugin.bootstrap.DebuggerBootstrapProvider.AUTHORITY"));
        assertEquals("0",
                ph.get("com.zerostudio.logplugin.bootstrap.DebuggerBootstrapProvider.META_PORT"));
    }

    @Test
    public void computeBootstrapPlaceholders_nullGroupAndName_fallbacks() throws Exception {
        Map<String, String> ph = invokeComputeBootstrapPlaceholders(null, "", 0, null);
        assertEquals("ide-debug-bridge", ph.get("ideLocalServerName"));
    }

    // ---- reflection helpers ----

    private String invokeComputeLocalServerName(String group, String name) throws Exception {
        IdeDebuggerInitScriptPlugin plugin = new IdeDebuggerInitScriptPlugin();
        Method m = IdeDebuggerInitScriptPlugin.class.getDeclaredMethod(
                "computeLocalServerName", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(plugin, group, name);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> invokeComputeBootstrapPlaceholders(
            String group, String name, int sdkInt, String preheat) throws Exception {
        IdeDebuggerInitScriptPlugin plugin = new IdeDebuggerInitScriptPlugin();
        Method m = IdeDebuggerInitScriptPlugin.class.getDeclaredMethod(
                "computeBootstrapPlaceholders", String.class, String.class, int.class, String.class);
        m.setAccessible(true);
        return (Map<String, String>) m.invoke(plugin, group, name, sdkInt, preheat);
    }
}
