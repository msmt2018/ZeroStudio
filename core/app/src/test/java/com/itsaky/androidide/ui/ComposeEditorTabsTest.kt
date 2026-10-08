package com.itsaky.androidide.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ComposeEditorTabsTest {
    private fun host() = ComposeEditorTabs(ApplicationProvider.getApplicationContext())

    @Test fun closingEarlierFileKeepsSelectedPageIdentity() {
        val host = host()
        val file = host.newTab().apply { tag = "editor:/a.kt" }
        val fragment = host.newTab().apply { tag = "fragment:preview" }
        val screen = host.newTab().apply { tag = "compose:settings" }
        listOf(file, fragment, screen).forEach(host::addTab)
        screen.select()
        host.removeTab(file)
        assertSame(screen, host.getTabAt(host.selectedTabPosition))
        assertEquals(1, screen.position)
        assertEquals(-1, file.position)
    }

    @Test fun closingSelectedMiddleTabSelectsItsRightNeighbor() {
        val host = host()
        val tabs = List(3) { host.newTab() }
        tabs.forEach(host::addTab)
        tabs[1].select()
        host.removeTab(tabs[1])
        assertSame(tabs[2], host.getTabAt(host.selectedTabPosition))
        host.removeTab(tabs[2])
        assertSame(tabs[0], host.getTabAt(host.selectedTabPosition))
    }

    @Test fun closingLastTabAndReopeningLeavesValidSelection() {
        val host = host()
        val old = host.newTab()
        host.addTab(old)
        host.removeTab(old)
        assertEquals(-1, host.selectedTabPosition)
        assertEquals(0, host.tabCount)
        val next = host.newTab()
        host.addTab(next)
        assertSame(next, host.getTabAt(0))
        assertEquals(0, host.selectedTabPosition)
        old.select() // A delayed event from the disposed tab must not change selection.
        assertEquals(0, host.selectedTabPosition)
    }

    @Test fun duplicateOpenAndRepeatedCloseAreHarmless() {
        val host = host()
        val tab = host.newTab()
        host.addTab(tab)
        host.addTab(tab)
        assertEquals(1, host.tabCount)
        host.removeTab(tab)
        host.removeTab(tab)
        host.removeTabAt(100)
        assertEquals(0, host.tabCount)
    }

    @Test fun closeRequestDoesNotBypassSaveConfirmation() {
        val host = host()
        val tab = host.newTab()
        host.addTab(tab)
        var requested: ComposeEditorTabs.Tab? = null
        host.onCloseTab = { requested = it }
        host.onCloseTab(tab)
        assertSame(tab, requested)
        assertEquals(1, host.tabCount)
        assertEquals(0, host.selectedTabPosition)
    }

    @Test fun removingAllTabsNotifiesUnselectionOnce() {
        val host = host()
        var unselected = 0
        host.addOnTabSelectedListener(object : ComposeEditorTabs.OnTabSelectedListener {
            override fun onTabSelected(tab: ComposeEditorTabs.Tab) = Unit
            override fun onTabUnselected(tab: ComposeEditorTabs.Tab) { unselected++ }
            override fun onTabReselected(tab: ComposeEditorTabs.Tab) = Unit
        })
        repeat(3) { host.addTab(host.newTab()) }
        host.removeAllTabs()
        host.removeAllTabs()
        assertEquals(1, unselected)
        assertEquals(-1, host.selectedTabPosition)
    }
}
