package com.itsaky.androidide.activities.editor.ui.state

import android.app.Application
import androidx.fragment.app.Fragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class EditorPageRegistryTest {
    private fun screen(id: String) = EditorPage.ScreenPage(id, id) {}

    @Test fun fragmentAndComposePagesShareStableSelection() {
        val registry = EditorPageRegistry()
        val fragment = EditorPage.FragmentPage("files", "Files", Fragment::class.java)
        val compose = screen("tools")
        registry.register(fragment)
        registry.register(compose)
        assertSame(fragment, registry.selectedPage)
        assertTrue(registry.select("tools"))
        registry.unregister("files")
        assertSame(compose, registry.selectedPage)
    }

    @Test fun selectedRemovalFallsBackToNeighborThenEmpty() {
        val registry = EditorPageRegistry()
        listOf("a", "b", "c").forEach { registry.register(screen(it)) }
        registry.select("b")
        registry.unregister("b")
        assertEquals("c", registry.selectedId)
        registry.unregister("c")
        assertEquals("a", registry.selectedId)
        registry.unregister("a")
        assertNull(registry.selectedPage)
        registry.register(screen("d"))
        assertEquals("d", registry.selectedId)
    }

    @Test fun duplicateRegistrationDoesNotReplacePageState() {
        val registry = EditorPageRegistry()
        val original = screen("a")
        registry.register(original)
        assertThrows(IllegalArgumentException::class.java) { registry.register(screen("a")) }
        assertSame(original, registry.selectedPage)
        assertEquals(1, registry.pages.size)
    }

    @Test fun staleEventsCannotSelectOrRemoveAnotherPage() {
        val registry = EditorPageRegistry()
        registry.register(screen("a"))
        assertFalse(registry.select("missing"))
        assertFalse(registry.unregister("missing"))
        assertEquals("a", registry.selectedId)
        registry.clear()
        assertTrue(registry.pages.isEmpty())
        assertNull(registry.selectedId)
    }

    @Test fun sidebarActionsDoNotReplaceTheVisiblePage() {
        val registry = EditorPageRegistry()
        var calls = 0
        registry.register(EditorPage.ActionPage("settings", "Settings") { calls++ })
        assertNull(registry.selectedId)
        registry.register(screen("files"))
        registry.select("settings")
        registry.select("settings")
        assertEquals(2, calls)
        assertEquals("files", registry.selectedId)
        registry.unregister("files")
        assertNull(registry.selectedId)
    }

    @Test fun blankIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { EditorPageRegistry().register(screen(" ")) }
    }
}
