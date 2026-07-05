/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuSubPathResolver 单元测试: Auto 模式按顺序探测。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ShizukuConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ShizukuSubPathResolverTest {

    private val target = DebugTarget("com.example.app", "com.example.app.MainActivity")

    private class FakeCap(
        override val subPath: ShizukuConfig.SubPath,
        private val usable: Boolean,
    ) : ShizukuSubPathCapability {
        var probeCount: Int = 0
            private set
        override fun probeUsable(target: DebugTarget): SubPathUsability {
            probeCount++
            return SubPathUsability(isUsable = usable)
        }
    }

    @Test
    fun `Auto with Shizuku not running returns WifiAdb directly`() {
        val probe = FakeShizukuProbe(
            status = ShizukuStatus(false, false, -1, -1, "not running"),
        )
        val resolver = ShizukuSubPathResolver(probe, listOf(
            FakeCap(ShizukuConfig.SubPath.Binder, true),
            FakeCap(ShizukuConfig.SubPath.Socks, true),
        ))
        val chosen = resolver.resolve(ShizukuConfig.SubPath.Auto, target)
        assertEquals(ShizukuConfig.SubPath.WifiAdb, chosen)
    }

    @Test
    fun `Auto picks first usable subPath`() {
        val probe = FakeShizukuProbe()  // running + granted by default
        val binder = FakeCap(ShizukuConfig.SubPath.Binder, false)
        val socks = FakeCap(ShizukuConfig.SubPath.Socks, true)
        val resolver = ShizukuSubPathResolver(probe, listOf(binder, socks))
        val chosen = resolver.resolve(ShizukuConfig.SubPath.Auto, target)
        // 按 capabilities 顺序: Binder 不可用, Socks 可用 -> 选 Socks
        assertEquals(ShizukuConfig.SubPath.Socks, chosen)
        assertEquals(1, binder.probeCount)
        assertEquals(1, socks.probeCount)
    }

    @Test
    fun `Auto falls back to WifiAdb when none usable`() {
        val probe = FakeShizukuProbe()
        val binder = FakeCap(ShizukuConfig.SubPath.Binder, false)
        val socks = FakeCap(ShizukuConfig.SubPath.Socks, false)
        val resolver = ShizukuSubPathResolver(probe, listOf(binder, socks))
        val chosen = resolver.resolve(ShizukuConfig.SubPath.Auto, target)
        assertEquals(ShizukuConfig.SubPath.WifiAdb, chosen)
    }

    @Test
    fun `Explicit non-Auto subPath is returned without probing`() {
        val probe = FakeShizukuProbe()
        val binder = FakeCap(ShizukuConfig.SubPath.Binder, false)
        val resolver = ShizukuSubPathResolver(probe, listOf(binder))
        val chosen = resolver.resolve(ShizukuConfig.SubPath.Binder, target)
        assertEquals(ShizukuConfig.SubPath.Binder, chosen)
        // 不应探测
        assertEquals(0, binder.probeCount)
    }
}
