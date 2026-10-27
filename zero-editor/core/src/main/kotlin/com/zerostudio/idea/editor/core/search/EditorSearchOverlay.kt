package com.zerostudio.idea.editor.ui.search

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerostudio.idea.editor.core.search.SearchOptions

@Composable
fun EditorSearchOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPerformSearch: (SearchOptions) -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrev: () -> Unit,
    onReplace: (String, Boolean) -> Unit, // replacementText, replaceAll
    modifier: Modifier = Modifier
) {
    var queryText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var isReplaceMode by remember { mutableStateOf(false) }
    var isRegex by remember { mutableStateOf(false) }
    var caseSensitive by remember { mutableStateOf(false) }
    var matchWholeWord by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 搜索主行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { isReplaceMode = !isReplaceMode }) {
                        Icon(
                            imageVector = if (isReplaceMode) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "切换替换"
                        )
                    }

                    OutlinedTextField(
                        value = queryText,
                        onValueChange = {
                            queryText = it
                            onPerformSearch(SearchOptions(queryText, isRegex, caseSensitive, matchWholeWord))
                        },
                        placeholder = { Text("搜索代码...", fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            Row {
                                FilterChip(
                                    selected = caseSensitive,
                                    onClick = {
                                        caseSensitive = !caseSensitive
                                        onPerformSearch(SearchOptions(queryText, isRegex, caseSensitive, matchWholeWord))
                                    },
                                    label = { Text("Aa") },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                FilterChip(
                                    selected = isRegex,
                                    onClick = {
                                        isRegex = !isRegex
                                        onPerformSearch(SearchOptions(queryText, isRegex, caseSensitive, matchWholeWord))
                                    },
                                    label = { Text(".*") }
                                )
                            }
                        }
                    )

                    IconButton(onClick = onNavigatePrev) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个")
                    }
                    IconButton(onClick = onNavigateNext) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                // 替换抽屉行
                AnimatedVisibility(visible = isReplaceMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Spacer(modifier = Modifier.width(48.dp))
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            placeholder = { Text("替换为...", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onReplace(replaceText, false) }) {
                            Text("替换")
                        }
                        FilledTonalButton(onClick = { onReplace(replaceText, true) }) {
                            Text("全部")
                        }
                    }
                }
            }
        }
    }
}