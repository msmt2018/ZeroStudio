package com.itsaky.androidide.activities.editor.ui.state

import androidx.compose.runtime.*
import com.itsaky.androidide.activities.editor.BaseEditorActivity
import com.itsaky.androidide.ui.CodeEditorView
import com.itsaky.androidide.ui.ComposeEditorTabs

/** Activity-owned UI state; it contains no inflated layout or ViewBinding. */
class EditorUiState(activity: BaseEditorActivity) {
    val tabs = ComposeEditorTabs(activity)
    val editorContainer = EditorBufferStore()
    val bottomSheet = EditorBottomPanel(activity)
    val sidebar = EditorPageRegistry()
    val documents = EditorPageRegistry()
    var surface by mutableIntStateOf(2) // 0: file engine, 1: registered page, 2: empty
    var drawerOpen by mutableStateOf(false)
    var memoryVisible by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var cursor by mutableStateOf("1:1")
    var currentEditor by mutableStateOf<CodeEditorView?>(null)
    var dialog by mutableStateOf<(@Composable () -> Unit)?>(null)
    var toolbarVersion by mutableIntStateOf(0)
    var headerVisible by mutableStateOf(true)
    var symbolHeight by mutableIntStateOf(0)
    var memories by mutableStateOf<List<Pair<String, List<Float>>>>(emptyList())
}

/** Keep Sora instances alive while Compose mounts only the selected buffer. */
class EditorBufferStore {
    val editors = mutableStateListOf<CodeEditorView>()
    var displayedChild by mutableIntStateOf(0)
    val childCount get() = editors.size
    fun getChildAt(index: Int): CodeEditorView? = editors.getOrNull(index)
    fun addView(editor: CodeEditorView) { editors.add(editor) }
    fun removeViewAt(index: Int) { editors.removeAt(index) }
    fun removeAllViews() { editors.clear(); displayedChild = 0 }
    val selected get() = editors.getOrNull(displayedChild)
}
