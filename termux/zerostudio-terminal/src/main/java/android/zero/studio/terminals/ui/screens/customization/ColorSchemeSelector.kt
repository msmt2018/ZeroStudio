package com.termix.ui.screens.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termix.settings.Settings
import com.termix.resources.strings
import com.termix.ui.theme.colorscheme.ColorSchemeManager
import com.termix.ui.theme.colorscheme.ColorSchemes
import com.termix.ui.theme.colorscheme.TerminalColorScheme

/**
 * A composable that displays a horizontal scrollable list of color scheme options.
 * Each scheme is shown as a preview card with sample colors.
 */
@Composable
fun ColorSchemeSelector(
    modifier: Modifier = Modifier,
    onSchemeSelected: (TerminalColorScheme) -> Unit = {}
) {
    val currentScheme by ColorSchemeManager.currentScheme
    val systemDarkTheme = isSystemInDarkTheme()
    val appDarkTheme = when (Settings.default_night_mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> systemDarkTheme
    }
    val scrollState = rememberScrollState()
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ColorSchemes.all.forEach { scheme ->
                val previewScheme = if (scheme.id == "default") {
                    ColorSchemeManager.resolveSchemeForAppTheme(scheme, appDarkTheme)
                } else {
                    scheme
                }

                ColorSchemeCard(
                    scheme = previewScheme,
                    isSelected = scheme.id == currentScheme.id,
                    onClick = {
                        ColorSchemeManager.setColorScheme(scheme)
                        onSchemeSelected(scheme)
                    }
                )
            }
        }
    }
}

/**
 * A card that displays a preview of a color scheme.
 */
@Composable
private fun ColorSchemeCard(
    scheme: TerminalColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color(scheme.background)
    val foregroundColor = Color(scheme.foreground)
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    
    Card(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Terminal preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(backgroundColor)
                    .padding(6.dp)
            ) {
                Column {
                    // Simulated terminal prompt
                    Text(
                        text = "$ ls -la",
                        color = foregroundColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "drwxr",
                            color = Color(scheme.blue),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "user",
                            color = Color(scheme.green),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "file.txt",
                        color = foregroundColor,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Color palette preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ColorDot(Color(scheme.red))
                ColorDot(Color(scheme.green))
                ColorDot(Color(scheme.yellow))
                ColorDot(Color(scheme.blue))
                ColorDot(Color(scheme.magenta))
                ColorDot(Color(scheme.cyan))
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Scheme name with selection indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = scheme.name,
                    color = foregroundColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * A small colored dot for the palette preview.
 */
@Composable
private fun ColorDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}
