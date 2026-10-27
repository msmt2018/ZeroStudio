package com.zerostudio.idea.editor.ui.keys

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import com.zerostudio.idea.editor.ui.view.VirtualCodeEditorView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class ExtraKeyAction {
    data class InsertSymbol(val symbol: String, val pairClosing: String? = null) : ExtraKeyAction()
    data class EmulateKey(val keyCode: Int) : ExtraKeyAction()
    object IndentTab : ExtraKeyAction()
    object SnippetNextTab : ExtraKeyAction()
    object SnippetPrevTab : ExtraKeyAction()
}

data class ExtraKeyItem(
    val id: String,
    val displayLabel: String,
    val action: ExtraKeyAction,
    val isPrimary: Boolean = false
)

/**
 * 工业级移动端代码键入辅助条
 */
@Composable
fun ZeroEditorExtraKeysBar(
    editorView: VirtualCodeEditorView?,
    preferences: EditorPreferencesState,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // 默认高密指令与符号集
    val commandKeys = remember {
        listOf(
            ExtraKeyItem("tab", "Tab", ExtraKeyAction.IndentTab, isPrimary = true),
            ExtraKeyItem("tab_next", "⇥", ExtraKeyAction.SnippetNextTab),
            ExtraKeyItem("tab_prev", "⇤", ExtraKeyAction.SnippetPrevTab),
            ExtraKeyItem("left", "←", ExtraKeyAction.EmulateKey(KeyEvent.KEYCODE_DPAD_LEFT)),
            ExtraKeyItem("right", "→", ExtraKeyAction.EmulateKey(KeyEvent.KEYCODE_DPAD_RIGHT)),
            ExtraKeyItem("up", "↑", ExtraKeyAction.EmulateKey(KeyEvent.KEYCODE_DPAD_UP)),
            ExtraKeyItem("down", "↓", ExtraKeyAction.EmulateKey(KeyEvent.KEYCODE_DPAD_DOWN))
        )
    }

    val symbolKeys = remember {
        listOf(
            ExtraKeyItem("paren_open", "(", ExtraKeyAction.InsertSymbol("(", ")")),
            ExtraKeyItem("brace_open", "{", ExtraKeyAction.InsertSymbol("{", "}")),
            ExtraKeyItem("bracket_open", "[", ExtraKeyAction.InsertSymbol("[", "]")),
            ExtraKeyItem("quote_double", "\"", ExtraKeyAction.InsertSymbol("\"", "\"")),
            ExtraKeyItem("quote_single", "'", ExtraKeyAction.InsertSymbol("'", "'")),
            ExtraKeyItem("semi", ";", ExtraKeyAction.InsertSymbol(";")),
            ExtraKeyItem("colon", ":", ExtraKeyAction.InsertSymbol(":")),
            ExtraKeyItem("dot", ".", ExtraKeyAction.InsertSymbol(".")),
            ExtraKeyItem("comma", ",", ExtraKeyAction.InsertSymbol(",")),
            ExtraKeyItem("equal", "=", ExtraKeyAction.InsertSymbol("=")),
            ExtraKeyItem("greater", ">", ExtraKeyAction.InsertSymbol(">")),
            ExtraKeyItem("less", "<", ExtraKeyAction.InsertSymbol("<")),
            ExtraKeyItem("slash", "/", ExtraKeyAction.InsertSymbol("/")),
            ExtraKeyItem("backslash", "\\", ExtraKeyAction.InsertSymbol("\\")),
            ExtraKeyItem("pipe", "|", ExtraKeyAction.InsertSymbol("|")),
            ExtraKeyItem("arrow", "->", ExtraKeyAction.InsertSymbol("->")),
            ExtraKeyItem("fat_arrow", "=>", ExtraKeyAction.InsertSymbol("=>"))
        )
    }

    val onExecuteAction: (ExtraKeyAction) -> Unit = { action ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        editorView?.let { editor ->
            when (action) {
                is ExtraKeyAction.InsertSymbol -> {
                    val cursor = editor.cursor
                    if (cursor.isSelected && action.pairClosing != null) {
                        // 选区自动包裹逻辑 (Auto-Surround)
                        val start = cursor.left()
                        val end = cursor.right()
                        val selectedText = editor.text.subSequence(start.index, end.index).toString()
                        editor.text.replace(start.line, start.column, end.line, end.column, "${action.symbol}$selectedText${action.pairClosing}")
                        editor.setSelectionRegion(start.line, start.column + action.symbol.length, end.line, end.column + action.symbol.length)
                    } else if (action.pairClosing != null && preferences.autoClosingBracket) {
                        // 空选区自动补全闭合符号，并将光标移动至两者之间
                        val pos = cursor.left()
                        editor.commitText("${action.symbol}${action.pairClosing}")
                        editor.setSelection(pos.line, pos.column + action.symbol.length)
                    } else {
                        editor.commitText(action.symbol)
                    }
                }
                is ExtraKeyAction.EmulateKey -> {
                    editor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, action.keyCode))
                    editor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, action.keyCode))
                }
                is ExtraKeyAction.IndentTab -> {
                    if (editor.snippetController.isInSnippet) {
                        editor.snippetController.shiftToNextTabStop()
                    } else {
                        editor.indentOrCommitTab()
                    }
                }
                is ExtraKeyAction.SnippetNextTab -> editor.snippetController.shiftToNextTabStop()
                is ExtraKeyAction.SnippetPrevTab -> editor.snippetController.shiftToPreviousTabStop()
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)) {
            // 分栏模式（Split Mode）或者单行滚屏模式
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(commandKeys, key = { it.id }) { item ->
                    KeyItemButton(item, onExecuteAction)
                }

                item {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                items(symbolKeys, key = { it.id }) { item ->
                    KeyItemButton(item, onExecuteAction)
                }
            }
        }
    }
}

@Composable
private fun KeyItemButton(
    item: ExtraKeyItem,
    onExecuteAction: (ExtraKeyAction) -> Unit
) {
    val containerColor = if (item.isPrimary) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val contentColor = if (item.isPrimary) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // 支持按住连发手势检测（Repeat-on-Hold）
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 38.dp, height = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .pointerInput(item.id) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var isHeld = true
                        val repeatJob = launch {
                            onExecuteAction(item.action)
                            delay(400) // 长按触发延时
                            while (isHeld) {
                                onExecuteAction(item.action)
                                delay(60) // 连发频率 60ms
                            }
                        }
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        isHeld = false
                        repeatJob.cancel()
                    }
                }
            }
    ) {
        Text(
            text = item.displayLabel,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = contentColor
        )
    }
}