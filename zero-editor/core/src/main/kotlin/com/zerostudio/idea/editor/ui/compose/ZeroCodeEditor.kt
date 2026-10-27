package com.zerostudio.idea.editor.ui.compose

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import com.zerostudio.idea.editor.ui.actions.ZeroEditorFloatingActionMenu
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView

/**
 * 顶层 Jetpack Compose 高性能代码编辑器控件组件
 */
@Composable
fun ZeroCodeEditor(
    pieceTree: LargeTextPieceTree,
    preferences: EditorPreferencesState,
    modifier: Modifier = Modifier,
    onEditorInitialized: ((ZeroCodeEditorView) -> Unit)? = null
) {
    var editorInstance by remember { mutableStateOf<ZeroCodeEditorView?>(null) }
    var showFloatingMenu by remember { mutableStateOf(false) }
    var menuAnchorX by remember { mutableIntStateOf(0) }
    var menuAnchorY by remember { mutableIntStateOf(0) }
    var cursorHeight by remember { mutableIntStateOf(40) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                ZeroCodeEditorView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    bindPieceTreeStorage(pieceTree, preferences.autoPaginationThresholdChars)
                    onShowFloatingMenuListener = { x, y, h ->
                        menuAnchorX = x
                        menuAnchorY = y
                        cursorHeight = h
                        showFloatingMenu = true
                    }
                    editorInstance = this
                    onEditorInitialized?.invoke(this)
                }
            },
            update = { view ->
                view.isWordwrap = preferences.wordWrap
                view.lineSpacingMultiplier = preferences.lineSpacingMultiplier
                view.setTextSize(preferences.scaledFontSizeSp)
                view.isLineNumberEnabled = preferences.showLineNumbers
            }
        )

        // 现代 M3 悬浮 Action 菜单 (长按光标手柄处弹出)
        editorInstance?.let { editor ->
            ZeroEditorFloatingActionMenu(
                visible = showFloatingMenu,
                anchorXPx = menuAnchorX,
                anchorYPx = menuAnchorY,
                cursorHeightPx = cursorHeight,
                editor = editor,
                onDismiss = { showFloatingMenu = false }
            )
        }
    }
}