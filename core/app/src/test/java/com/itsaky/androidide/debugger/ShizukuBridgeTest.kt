/*
 *  ZeroStudio IDE - ShizukuBridge 单元测试
 *
 *  PR-D3: 验证反射式探针在 shizuku-api 不存在时不会崩溃.
 *  在 shizuku 实际运行下的行为留给 instrumented 测试.
 */

package com.itsaky.androidide.debugger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShizukuBridgeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `shizuku api is not available in unit-test env`() {
        // Robolectric + our test classpath 不包含 shizuku-api AAR,
        // 所以 isApiAvailable 必须返回 false (这是预期的 — 表示 IDE 在
        // 当前测试环境下不会尝试反射 shizuku).
        val bridge = ShizukuBridge(context)
        assertFalse("shizuku-api not on test classpath",
            bridge.isApiAvailable())
    }

    @Test
    fun `isBinderReady is false when api is not available`() {
        val bridge = ShizukuBridge(context)
        assertFalse(bridge.isBinderReady())
    }

    @Test
    fun `checkPermission returns NONE when api is not available`() {
        val bridge = ShizukuBridge(context)
        assertEquals(ShizukuBridge.SHIZUKU_PERMISSION_NONE, bridge.checkPermission())
    }

    @Test
    fun `getServerUid returns -1 when api is not available`() {
        val bridge = ShizukuBridge(context)
        assertEquals(-1, bridge.getServerUid())
    }

    @Test
    fun `exec returns empty string when binder is not ready`() {
        val bridge = ShizukuBridge(context)
        assertEquals("", bridge.exec("echo hello"))
    }

    @Test
    fun `installApk returns false when binder is not ready`() {
        val bridge = ShizukuBridge(context)
        assertFalse(bridge.installApk("/data/local/tmp/app.apk"))
    }
}
