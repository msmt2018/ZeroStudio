package com.zerostudio.idea.editor.ui.lsp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.lsp.bridge.getRemoteServer
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either

data class OutlineNode(
    val name: String,
    val detail: String?,
    val line: Int,
    val column: Int,
    val depth: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentOutlineSheet(
    editor: ZeroCodeEditorView,
    connectorProvider: () -> ZeroLspConnector?,
    onDismiss: () -> Unit
) {
    var outlineNodes by remember { mutableStateOf<List<OutlineNode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val connector = connectorProvider()
        val server = connector?.getRemoteServer()
        if (server != null) {
            val symbols = withContext(Dispatchers.IO) {
                val params = DocumentSymbolParams(TextDocumentIdentifier(connector.fileUri))
                runCatching {
                    server.textDocumentService.documentSymbol(params).get()
                }.getOrNull() ?: emptyList()
            }
            outlineNodes = flattenSymbols(symbols)
        }
        isLoading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "代码大纲与符号导航 (Document Symbols)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (outlineNodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无可用符号大纲", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(outlineNodes) { node ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    editor.setSelection(node.line, node.column)
                                    editor.ensurePositionVisible(node.line, node.column)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .padding(start = (node.depth * 16).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = node.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            node.detail?.let {
                                Text(
                                    text = it,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun flattenSymbols(symbols: List<Either<SymbolInformation, DocumentSymbol>>): List<OutlineNode> {
    val result = mutableListOf<OutlineNode>()
    fun traverse(symbol: DocumentSymbol, depth: Int) {
        result.add(
            OutlineNode(
                name = symbol.name,
                detail = symbol.detail,
                line = symbol.range.start.line,
                column = symbol.range.start.character,
                depth = depth
            )
        )
        symbol.children?.forEach { traverse(it, depth + 1) }
    }

    for (item in symbols) {
        if (item.isRight) {
            traverse(item.right, 0)
        } else if (item.isLeft) {
            val info = item.left
            result.add(
                OutlineNode(
                    name = info.name,
                    detail = null,
                    line = info.location.range.start.line,
                    column = info.location.range.start.character,
                    depth = 0
                )
            )
        }
    }
    return result
}