package android.zero.studio.termux.ui.components

import android.zero.studio.termux.model.WorkingMode
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val TAB_WIDTH = 160.dp
private val TAB_HEIGHT = 36.dp
private val ADD_BUTTON_OVERLAY_WIDTH = 40.dp

/**
 * Top session tab strip that replaces the old hidden left drawer session list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionTabBar(
    sessions: List<String>,
    currentSessionId: String,
    getDisplayTitle: (String) -> String,
    getWorkingMode: (String) -> Int?,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onCloseOtherSessions: (String) -> Unit,
    onCloseAllSessions: () -> Unit,
    onAddSession: () -> Unit,
    onRenameSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(currentSessionId, sessions.size) {
        val index = sessions.indexOf(currentSessionId)
        if (index >= 0) {
            val tabWidthPx = with(density) { TAB_WIDTH.toPx() }
            scope.launch {
                scrollState.animateScrollTo((index * tabWidthPx).toInt())
            }
        }
    }

    Surface(
        modifier = modifier.height(TAB_HEIGHT),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = ADD_BUTTON_OVERLAY_WIDTH)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                sessions.forEachIndexed { index, sessionId ->
                    SessionTab(
                        index = index,
                        sessionId = sessionId,
                        selected = sessionId == currentSessionId,
                        displayTitle = getDisplayTitle(sessionId),
                        workingMode = getWorkingMode(sessionId),
                        onSelectSession = onSelectSession,
                        onCloseSession = onCloseSession,
                        onCloseOtherSessions = onCloseOtherSessions,
                        onCloseAllSessions = onCloseAllSessions,
                        onRenameSession = onRenameSession,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }

            IconButton(
                onClick = onAddSession,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 4.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add session",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionTab(
    index: Int,
    sessionId: String,
    selected: Boolean,
    displayTitle: String,
    workingMode: Int?,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onCloseOtherSessions: (String) -> Unit,
    onCloseAllSessions: () -> Unit,
    onRenameSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuExpanded by remember(sessionId) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(TAB_WIDTH)
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceContainer,
            )
            .combinedClickable(
                onClick = { onSelectSession(sessionId) },
                onLongClick = { menuExpanded = true },
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (index < 9) {
                SessionNumberBadge(index = index, selected = selected)
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = getSessionColor(
                    workingMode = workingMode,
                    selected = selected,
                ),
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = { onCloseSession(sessionId) },
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close session",
                    modifier = Modifier.size(14.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        SessionTabContextMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onRenameSession = { onRenameSession(sessionId) },
            onCloseSession = { onCloseSession(sessionId) },
            onCloseOtherSessions = { onCloseOtherSessions(sessionId) },
            onCloseAllSessions = onCloseAllSessions,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
private fun SessionNumberBadge(index: Int, selected: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${index + 1}",
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
        )
    }
}

@Composable
private fun SessionTabContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRenameSession: () -> Unit,
    onCloseSession: () -> Unit,
    onCloseOtherSessions: () -> Unit,
    onCloseAllSessions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("重命名会话名") },
            onClick = {
                onDismiss()
                onRenameSession()
            },
        )
        DropdownMenuItem(
            text = { Text("关闭当前会话标签") },
            onClick = {
                onDismiss()
                onCloseSession()
            },
        )
        DropdownMenuItem(
            text = { Text("关闭其它会话") },
            onClick = {
                onDismiss()
                onCloseOtherSessions()
            },
        )
        DropdownMenuItem(
            text = { Text("关闭全部") },
            onClick = {
                onDismiss()
                onCloseAllSessions()
            },
        )
        DropdownMenuItem(
            text = { Text("设置") },
            onClick = {
                onDismiss()
                onOpenSettings()
            },
        )
    }
}

/**
 * Returns the display color for a session based on its working mode.
 */
@Composable
private fun getSessionColor(workingMode: Int?, selected: Boolean): Color {
    return when (workingMode) {
        WorkingMode.ALPINE_ROOT -> Color(0xFFEF5350)
        WorkingMode.ARCH_ROOT -> Color(0xFFEF5350)
        WorkingMode.UBUNTU -> Color(0xFFFFB300)
        WorkingMode.UBUNTU_ROOT -> Color(0xFFEF5350)
        WorkingMode.ANDROID -> Color(0xFFFFA726)
        else -> if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}
