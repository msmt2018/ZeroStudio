package com.termix.ui.components

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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import com.termix.model.WorkingMode

private val TAB_WIDTH = 160.dp
private val TAB_HEIGHT = 36.dp

/**
 * Horizontal session tab bar for wide screens (≥ 600dp).
 * Custom implementation using Row + horizontalScroll for full control over tab sizing.
 * Styled to match iTerm2/Chrome tabs — fixed-width tabs with horizontal scrolling.
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
    onAddSession: () -> Unit,
    onRenameSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) return

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Auto-scroll to selected tab when currentSessionId changes
    LaunchedEffect(currentSessionId) {
        val index = sessions.indexOf(currentSessionId)
        if (index >= 0) {
            val tabWidthPx = with(density) { TAB_WIDTH.toPx() }
            val scrollPosition = (index * tabWidthPx).toInt()
            scope.launch {
                scrollState.animateScrollTo(scrollPosition)
            }
        }
    }
    Surface(
        modifier = modifier.height(TAB_HEIGHT),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scrollable area: tabs + add button
            Row(
                            modifier = Modifier.weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sessions.forEachIndexed { index, sessionId ->
                    val selected = sessionId == currentSessionId

                    // Tab item with fixed width
                    Box(
                        modifier = Modifier
                            .width(TAB_WIDTH)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .combinedClickable(
                                onClick = { onSelectSession(sessionId) },
                                onLongClick = { onRenameSession(sessionId) }
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Session number badge (1-indexed, show for first 9)
                            if (index < 9) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (selected)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Text(
                                text = getDisplayTitle(sessionId),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = getSessionColor(
                                    workingMode = getWorkingMode(sessionId),
                                    selected = selected
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            // Close button
                            IconButton(
                                onClick = { onCloseSession(sessionId) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close session",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (selected)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Add button (scrolls with tabs)
                IconButton(
                    onClick = onAddSession,
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add session",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Settings button (fixed on right)
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Returns the display color for a session based on its working mode.
 * Colors indicate privilege level:
 * - Alpine (non-root): default theme color
 * - Android (system shell): amber/warning
 * - Alpine Root: red/danger
 */
@Composable
private fun getSessionColor(workingMode: Int?, selected: Boolean): Color {
    return when (workingMode) {
        WorkingMode.ALPINE_ROOT -> Color(0xFFEF5350) // Red — root danger
        WorkingMode.ARCH_ROOT -> Color(0xFFEF5350) // Red — root danger
        WorkingMode.ANDROID -> Color(0xFFFFA726) // Amber — system shell
        else -> if (selected)
            MaterialTheme.colorScheme.onSurface
        else
            MaterialTheme.colorScheme.onSurfaceVariant
    }
}
