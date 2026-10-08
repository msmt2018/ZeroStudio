package com.itsaky.androidide.activities.editor.ui.state

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.itsaky.androidide.activities.editor.ui.screen.RegisteredEditorPage
import com.itsaky.androidide.ui.EditorTabStrip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class EditorPageStateTest {
    @get:Rule val compose = createComposeRule()

    private fun counter(id: String) = EditorPage.ScreenPage(id, id) {
        var count by rememberSaveable { mutableIntStateOf(0) }
        TextButton(onClick = { count++ }) { Text("$id:$count") }
    }

    @Test fun switchingPreservesScreenStateAndClosingReleasesIt() {
        val registry = EditorPageRegistry().apply {
            register(counter("a"))
            register(counter("b"))
        }
        compose.setContent { MaterialTheme { RegisteredEditorPage(registry, {}) } }
        compose.onNodeWithText("a:0").performClick()
        compose.onNodeWithText("a:1").assertExists()
        compose.runOnIdle { registry.select("b") }
        compose.onNodeWithText("b:0").assertExists()
        compose.runOnIdle { registry.select("a") }
        compose.onNodeWithText("a:1").assertExists()
        compose.runOnIdle { registry.unregister("a") }
        compose.onNodeWithText("b:0").assertExists()
        compose.runOnIdle { registry.register(counter("a")); registry.select("a") }
        compose.onNodeWithText("a:0").assertExists()
    }
    @Test fun manyFileTabsCanBeScrolledSelectedAndClosed() {
        var tabs by mutableStateOf((0..24).map { "File$it" })
        var selected by mutableStateOf("File0")
        compose.setContent {
            MaterialTheme {
                EditorTabStrip(tabs, selected, { it }, { selected = it }, { closed ->
                    tabs = tabs - closed
                    if (selected == closed) selected = tabs.last()
                })
            }
        }
        compose.onNodeWithText("File24", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("File24").assertIsSelected()
        compose.onNodeWithContentDescription("Close File24").performClick()
        compose.onNodeWithText("File23").assertIsSelected()
    }

}
