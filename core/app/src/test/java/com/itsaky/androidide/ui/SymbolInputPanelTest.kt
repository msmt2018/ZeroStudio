package com.itsaky.androidide.ui

import android.app.Application
import android.content.Context
import android.zero.studio.widget.editor.symbolinput.SymbolDataManager
import android.zero.studio.widget.editor.symbolinput.SymbolGroup
import android.zero.studio.widget.editor.symbolinput.SymbolInputPanel
import android.zero.studio.widget.editor.symbolinput.SymbolItem
import android.zero.studio.widget.editor.symbolinput.SymbolUiSettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SymbolInputPanelTest {
  @get:Rule val compose = createComposeRule()
  private val context get() = ApplicationProvider.getApplicationContext<Context>()

  @Before fun resetPreferences() {
    context.getSharedPreferences("advanced_symbol_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    SymbolDataManager.saveUiSettings(context, SymbolUiSettings(symbolTextSizeSp = 18))
  }

  @Test fun pressesFollowTheCurrentEditor() {
    SymbolDataManager.saveData(context, listOf(SymbolGroup("Symbols", mutableListOf(
      SymbolItem(display = "Insert", shortText = "short", longAction = 0, longText = "long")
    ))))
    val first = CodeEditor(context)
    val second = CodeEditor(context)
    var current by mutableStateOf<CodeEditor?>(first)
    compose.setContent { MaterialTheme { SymbolInputPanel(current, {}) } }
    compose.onNodeWithText("Insert").performClick()
    compose.runOnIdle {
      assertEquals("short", first.text.toString())
      current = second
    }
    compose.onNodeWithText("Insert").performTouchInput { longClick() }
    compose.runOnIdle {
      assertEquals("short", first.text.toString())
      assertEquals("long", second.text.toString())
      first.release()
      second.release()
    }
  }

  @Test fun removingTheSelectedGroupReloadsAndClampsThePage() {
    SymbolDataManager.saveData(context, listOf(
      SymbolGroup("First", mutableListOf(SymbolItem(display = "first symbol"))),
      SymbolGroup("Last", mutableListOf(SymbolItem(display = "last symbol")))
    ))
    compose.setContent { MaterialTheme { SymbolInputPanel(null, {}) } }
    compose.onNodeWithText("Last").performClick()
    compose.onNodeWithText("last symbol").assertExists()
    compose.runOnIdle {
      SymbolDataManager.saveData(context, listOf(
        SymbolGroup("Remaining", mutableListOf(SymbolItem(display = "replacement symbol")))
      ))
    }
    compose.onNodeWithText("Remaining").assertExists()
    compose.onNodeWithText("replacement symbol").assertExists()
  }

  @Test fun managerAndExpansionWorkWithoutAnEditor() {
    SymbolDataManager.saveData(context, listOf(
      SymbolGroup("Symbols", mutableListOf(SymbolItem(display = "disabled symbol")))
    ))
    var opened = 0
    compose.setContent { MaterialTheme { SymbolInputPanel(null, { opened++ }) } }
    compose.onNodeWithText("disabled symbol").assertIsNotEnabled()
    compose.onNodeWithContentDescription(context.getString(
      android.zero.studio.widget.editor.symbolinput.R.string.symbol_manager_title
    )).performClick()
    compose.runOnIdle { assertEquals(1, opened) }
    compose.onNodeWithContentDescription("Expand symbols").performClick()
    compose.onNodeWithContentDescription("Collapse symbols").assertExists()
  }
}
