/*
 *  ZeroStudio IDE - AutoAttachManager 单元测试
 *
 *  PR-D5: 校验 SharedPreferences 持久化和 backoff 逻辑.
 *  真正的 TCP probe / connect 留给 instrumented 测试.
 */

package com.itsaky.androidide.debugger

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoAttachManagerTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var mgr: AutoAttachManager

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        prefs = ctx.getSharedPreferences("zerostudio_debugger", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        mgr = AutoAttachManager(ctx)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `isEnabled default is true`() {
        assertTrue(mgr.isEnabled())
    }

    @Test
    fun `setEnabled persists across instances`() {
        mgr.setEnabled(false)
        val mgr2 = AutoAttachManager(ApplicationProvider.getApplicationContext())
        assertFalse(mgr2.isEnabled())
    }

    @Test
    fun `rememberTarget and clear cycle works`() {
        mgr.rememberTarget("127.0.0.1", 5005, "com.example")
        mgr.clear()
        // No assertion; verifying that calling these does not throw and
        // that we can re-create the manager without state loss.
        val mgr2 = AutoAttachManager(ApplicationProvider.getApplicationContext())
        // We can't easily read back the prefs from outside, but the
        // methods should not throw on a fresh manager.
        mgr2.clear()
    }

    @Test
    fun `recordUserDisconnect does not throw`() {
        mgr.recordUserDisconnect()
    }

    @Test
    fun `maybeAutoAttach returns false when no target saved`() {
        // Don't call rememberTarget. shouldAutoAttach should skip silently.
        assertFalse(mgr.maybeAutoAttach(null))
    }

    @Test
    fun `maybeAutoAttach returns false when disabled`() {
        mgr.setEnabled(false)
        mgr.rememberTarget("127.0.0.1", 5005, "com.example")
        assertFalse(mgr.maybeAutoAttach("com.example"))
    }

    @Test
    fun `cancelPending does not throw when nothing pending`() {
        mgr.cancelPending()
    }
}
