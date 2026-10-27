package com.zerostudio.idea.editor.ui.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView

/**
 * 抽象编辑器 Action 契约 (对标 AndroidIDE AnAction)
 */
interface ZeroEditorAction {
    val id: String
    val title: String
    val icon: ImageVector?
    fun isAvailable(editor: ZeroCodeEditorView): Boolean
    fun performAction(editor: ZeroCodeEditorView)
}

/**
 * 标准内置 Action 集合
 */
object ZeroEditorBuiltinActions {
    val COPY = object : ZeroEditorAction {
        override val id = "editor.action.copy"
        override val title = "复制"
        override val icon = Icons.Default.Share
        override fun isAvailable(editor: ZeroCodeEditorView) = editor.multiCursorModel.selections.any { !it.isCollapsed }
        override fun performAction(editor: ZeroCodeEditorView) = editor.copyMultiSelectionToClipboard()
    }

    val CUT = object : ZeroEditorAction {
        override val id = "editor.action.cut"
        override val title = "剪切"
        override val icon = Icons.Default.Clear
        override fun isAvailable(editor: ZeroCodeEditorView) = !editor.isReadOnly && editor.multiCursorModel.selections.any { !it.isCollapsed }
        override fun performAction(editor: ZeroCodeEditorView) = editor.cutMultiSelectionToClipboard()
    }

    val PASTE = object : ZeroEditorAction {
        override val id = "editor.action.paste"
        override val title = "粘贴"
        override val icon = Icons.Default.Check
        override fun isAvailable(editor: ZeroCodeEditorView) = !editor.isReadOnly && editor.hasClipboardContent()
        override fun performAction(editor: ZeroCodeEditorView) = editor.pasteFromClipboardToMultiCursor()
    }

    val SELECT_ALL = object : ZeroEditorAction {
        override val id = "editor.action.select_all"
        override val title = "全选"
        override val icon = null
        override fun isAvailable(editor: ZeroCodeEditorView) = editor.text.length() > 0
        override fun performAction(editor: ZeroCodeEditorView) = editor.selectAll()
    }

    val ADD_CURSOR_NEXT = object : ZeroEditorAction {
        override val id = "editor.action.add_cursor_next"
        override val title = "选择下一处"
        override val icon = Icons.Default.Add
        override fun isAvailable(editor: ZeroCodeEditorView) = true
        override fun performAction(editor: ZeroCodeEditorView) = editor.multiCursorModel.selectNextOccurrence()
    }
}

/**
 * 智能避障位置计算器
 */
private class FloatingMenuPositionProvider(
    private val anchorXPx: Int,
    private val anchorYPx: Int,
    private val cursorHeightPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        var x = anchorXPx - (popupContentSize.width / 2)
        // 确保不超出左右边缘
        x = x.coerceIn(16, windowSize.width - popupContentSize.width - 16)

        // 默认放置在手柄上方；若顶部溢出，则翻转到光标下方
        var y = anchorYPx - popupContentSize.height - 12
        if (y < 48) {
            y = anchorYPx + cursorHeightPx + 12
        }
        return IntOffset(x, y)
    }
}

/**
 * 现代 M3 浮动操作栏组件
 */
@Composable
fun ZeroEditorFloatingActionMenu(
    visible: Boolean,
    anchorXPx: Int,
    anchorYPx: Int,
    cursorHeightPx: Int,
    editor: ZeroCodeEditorView,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val actions = remember(editor) {
        listOf(
            ZeroEditorBuiltinActions.CUT,
            ZeroEditorBuiltinActions.COPY,
            ZeroEditorBuiltinActions.PASTE,
            ZeroEditorBuiltinActions.SELECT_ALL,
            ZeroEditorBuiltinActions.ADD_CURSOR_NEXT
        ).filter { it.isAvailable(editor) }
    }

    if (actions.isEmpty()) return

    val positionProvider = remember(anchorXPx, anchorYPx, cursorHeightPx) {
        FloatingMenuPositionProvider(anchorXPx, anchorYPx, cursorHeightPx)
    }

    Popup(popupPositionProvider = positionProvider, onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.forEach { action ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                action.performAction(editor)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        action.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = action.title,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = action.title,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}