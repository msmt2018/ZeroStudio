package com.zerostudio.idea.editor.ui.lsp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity

/**
 * 光标锚定自适应位置计算器，防止弹窗跳出屏幕
 */
class CursorAnchorPopupPositionProvider(
    private val cursorX: Int,
    private val cursorY: Int,
    private val lineHeight: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        var x = cursorX
        var y = cursorY + lineHeight

        // 右向边缘碰撞收缩
        if (x + popupContentSize.width > windowSize.width) {
            x = (windowSize.width - popupContentSize.width - 16).coerceAtLeast(0)
        }
        // 底向边缘碰撞翻转到光标上方
        if (y + popupContentSize.height > windowSize.height) {
            y = (cursorY - popupContentSize.height - 8).coerceAtLeast(0)
        }

        return IntOffset(x, y)
    }
}

@Composable
fun LspCompletionPopup(
    items: List<CompletionItem>,
    cursorOffsetXPx: Int,
    cursorOffsetYPx: Int,
    lineHeightPx: Int,
    onSelect: (CompletionItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return

    val positionProvider = remember(cursorOffsetXPx, cursorOffsetYPx, lineHeightPx) {
        CursorAnchorPopupPositionProvider(cursorOffsetXPx, cursorOffsetYPx, lineHeightPx)
    }

    Popup(popupPositionProvider = positionProvider, onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(min = 220.dp, max = 340.dp)
                .heightIn(max = 240.dp)
        ) {
            LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                items(items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 类型角标标识
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (item.kind?.name?.firstOrNull() ?: 'T').toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = item.label,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )

                        item.detail?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LspDiagnosticTooltip(
    diagnostic: Diagnostic,
    cursorOffsetXPx: Int,
    cursorOffsetYPx: Int,
    lineHeightPx: Int,
    onApplyQuickFix: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val positionProvider = remember(cursorOffsetXPx, cursorOffsetYPx, lineHeightPx) {
        CursorAnchorPopupPositionProvider(cursorOffsetXPx, cursorOffsetYPx, lineHeightPx)
    }

    val bannerColor = when (diagnostic.severity) {
        DiagnosticSeverity.Error -> MaterialTheme.colorScheme.errorContainer
        DiagnosticSeverity.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    Popup(popupPositionProvider = positionProvider, onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bannerColor,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 320.dp).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (diagnostic.severity == DiagnosticSeverity.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = diagnostic.source ?: "LSP 诊断",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = diagnostic.message,
                    style = MaterialTheme.typography.bodySmall
                )

                if (onApplyQuickFix != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onApplyQuickFix,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("快速修复", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}