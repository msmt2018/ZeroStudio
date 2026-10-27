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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class ExtraKeyAction {
    data class InsertSymbol(val symbol: String, val pairClosing: String? = null) : ExtraKeyAction()
    data class EmulateKey(val keyCode: Int) : ExtraKeyAction()
    object IndentTab : ExtraKeyAction()
    object SelectNextOccurrence : ExtraKeyAction()
}

data class ExtraKeyItem(
    val id: String,
    val displayLabel: String,
    val action: ExtraKeyAction,
    val isPrimary: Boolean = false
)

@Composable
fun ZeroEditorExtraKeysBar(
    editorView: ZeroCodeEditorView?,
    preferences: EditorPreferencesState,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val keys = remember {
        listOf(
            ExtraKeyItem("tab", "Tab", ExtraKeyAction.IndentTab, isPrimary = true),
            ExtraKeyItem("ctrl_d", "D", ExtraKeyAction.SelectNextOccurrence, isPrimary = true),
            ExtraKeyItem("left", "←", ExtraKeyAction.EmulateKey(KeyEvent.KEYCODE_DPAD_LEFT)),
            ExtraKeyItem("right", "→", ExtraKeyAction.EmulateKey(KeyEvent.KEYCODE_DPAD_RIGHT)),
            ExtraKeyItem("up", "↑", ExtraKeyAction.EmulateKey(KeyEvent.KEYCODE_DPAD_UP)),
            ExtraKeyItem("down", "↓", ExtraKeyAction.EmulateKey(KeyEvent.KEYCODE_DPAD_DOWN)),
            ExtraKeyItem("paren", "(", ExtraKeyAction.InsertSymbol("(", ")")),
            ExtraKeyItem("brace", "{", ExtraKeyAction.InsertSymbol("{", "}")),
            ExtraKeyItem("bracket", "[", ExtraKeyAction.InsertSymbol("[", "]")),
            ExtraKeyItem("quote", "\"", ExtraKeyAction.InsertSymbol("\"", "\"")),
            ExtraKeyItem("semi", ";", ExtraKeyAction.InsertSymbol(";")),
            ExtraKeyItem("colon", ":", ExtraKeyAction.InsertSymbol(":")),
            ExtraKeyItem("arrow", "->", ExtraKeyAction.InsertSymbol("->")),
            ExtraKeyItem("fat_arrow", "=>", ExtraKeyAction.InsertSymbol("=>"))
        )
    }

    val onActionExecute: (ExtraKeyAction) -> Unit = { action ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        editorView?.let { editor ->
            when (action) {
                is ExtraKeyAction.InsertSymbol -> {
                    val cursor = editor.cursor
                    if (cursor.isSelected && action.pairClosing != null) {
                        // 选区自动包裹
                        val start = cursor.left()
                        val end = cursor.right()
                        val selected = editor.text.subSequence(start.index, end.index).toString()
                        editor.text.replace(start.line, start.column, end.line, end.column, "${action.symbol}$selected${action.pairClosing}")
                        editor.setSelectionRegion(start.line, start.column + action.symbol.length, end.line, end.column + action.symbol.length)
                    } else if (action.pairClosing != null && preferences.autoClosingBracket) {
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
                    editor.indentOrCommitTab()
                }
                is ExtraKeyAction.SelectNextOccurrence -> {
                    editor.multiCursorModel.selectNextOccurrence()
                    editor.invalidate()
                }
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(keys, key = { it.id }) { item ->
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

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 40.dp, height = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerColor)
                        .pointerInput(item.id) {
                            coroutineScope {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var isHeld = true
                                    val repeatJob = launch {
                                        onActionExecute(item.action)
                                        delay(400) // 长按 400ms 触发高频连击
                                        while (isHeld) {
                                            onActionExecute(item.action)
                                            delay(50) // 50ms 频率连发
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
        }
    }
}