package com.itsaky.androidide.activities.editor.ui.state

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.itsaky.androidide.fragments.output.AppLogFragment
import com.itsaky.androidide.fragments.output.BuildOutputFragment
import com.itsaky.androidide.models.LogLine
import com.itsaky.androidide.utils.ILogger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class EditorBottomPanelTest {
    @Test fun buildOutputSurvivesPageRemovalAndCanBeClearedWithoutAView() {
        val panel = EditorBottomPanel(ApplicationProvider.getApplicationContext())
        panel.appendBuildOut("first")
        val first = BuildOutputFragment()
        panel.attach(first)
        assertEquals("first\n", first.getContent())
        panel.detach(first)
        panel.appendBuildOut("second")
        val replacement = BuildOutputFragment()
        panel.attach(replacement)
        assertEquals("first\nsecond\n", replacement.getContent())
        panel.clearSelectedOutput()
        assertEquals("", panel.selectedOutputSnapshot()?.second)
        assertEquals("", replacement.getContent())
    }

    @Test fun pooledApplicationLogsAreCopiedBeforeMainThreadDelivery() {
        val panel = EditorBottomPanel(ApplicationProvider.getApplicationContext())
        panel.selectTabByFragmentClass(AppLogFragment::class.java)
        val line = LogLine.obtain(ILogger.Level.INFO, "test", "before recycle")
        val expected = line.toString()
        Thread { panel.appendApkLog(line) }.apply { start(); join() }
        // A recycled object must no longer be retained by the output buffer.
        line.message = "changed after recycle"
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(expected, panel.selectedOutputSnapshot()?.second)
        panel.clearSelectedOutput()
        assertEquals("", panel.selectedOutputSnapshot()?.second)
    }
}
