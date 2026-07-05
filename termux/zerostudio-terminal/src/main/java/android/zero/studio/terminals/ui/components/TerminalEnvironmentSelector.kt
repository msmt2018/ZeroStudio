package com.termix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.termix.resources.strings
import com.termix.model.WorkingMode

enum class TerminalEnvironmentOption(
    val labelRes: Int,
    val supportsRoot: Boolean,
) {
    ALPINE(
        labelRes = strings.terminal_env_alpine,
        supportsRoot = true,
    ),
    ARCH(
        labelRes = strings.terminal_env_arch,
        supportsRoot = true,
    ),
    ANDROID(
        labelRes = strings.terminal_env_android,
        supportsRoot = false,
    ),
}

fun terminalEnvironmentFromWorkingMode(mode: Int): TerminalEnvironmentOption = when (mode) {
    WorkingMode.ALPINE,
    WorkingMode.ALPINE_ROOT -> TerminalEnvironmentOption.ALPINE
    WorkingMode.ARCH,
    WorkingMode.ARCH_ROOT -> TerminalEnvironmentOption.ARCH
    WorkingMode.ANDROID -> TerminalEnvironmentOption.ANDROID
    else -> TerminalEnvironmentOption.ALPINE
}

fun workingModeIsRoot(mode: Int): Boolean = when (mode) {
    WorkingMode.ALPINE_ROOT,
    WorkingMode.ARCH_ROOT -> true
    else -> false
}

fun terminalEnvironmentToWorkingMode(environment: TerminalEnvironmentOption, runAsRoot: Boolean): Int {
    val normalizedRoot = runAsRoot && environment.supportsRoot
    return when (environment) {
        TerminalEnvironmentOption.ALPINE -> if (normalizedRoot) WorkingMode.ALPINE_ROOT else WorkingMode.ALPINE
        TerminalEnvironmentOption.ARCH -> if (normalizedRoot) WorkingMode.ARCH_ROOT else WorkingMode.ARCH
        TerminalEnvironmentOption.ANDROID -> WorkingMode.ANDROID
    }
}

fun terminalEnvironmentDescriptionRes(environment: TerminalEnvironmentOption, runAsRoot: Boolean): Int {
    val normalizedRoot = runAsRoot && environment.supportsRoot
    return when (environment) {
        TerminalEnvironmentOption.ALPINE -> if (normalizedRoot) strings.alpine_root_desc else strings.alpine_desc
        TerminalEnvironmentOption.ARCH -> if (normalizedRoot) strings.arch_root_desc else strings.arch_desc
        TerminalEnvironmentOption.ANDROID -> strings.android_desc
    }
}

@Composable
fun TerminalEnvironmentSegmentedSelector(
    selectedEnvironment: TerminalEnvironmentOption,
    onSelected: (TerminalEnvironmentOption) -> Unit,
    modifier: Modifier = Modifier,
    minButtonHeight: Dp = 44.dp,
) {
    val options = TerminalEnvironmentOption.entries
    val containerShape = RoundedCornerShape(12.dp)
    val pillShape = RoundedCornerShape(10.dp)
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val selectedPillColor = MaterialTheme.colorScheme.primary
    val selectedTextColor = MaterialTheme.colorScheme.onPrimary
    val unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(minButtonHeight)
            .clip(containerShape)
            .background(backgroundColor)
            .padding(4.dp),
    ) {
        val maxWidth = maxWidth
        val segmentWidth = maxWidth / options.size
        val selectedIndex = options.indexOf(selectedEnvironment)
        val indicatorOffset by animateDpAsState(
            targetValue = if (selectedIndex >= 0) segmentWidth * selectedIndex else 0.dp,
            animationSpec = tween(
                durationMillis = 250,
                easing = FastOutSlowInEasing,
            ),
            label = "iosSelectorIndicatorOffset",
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .clip(pillShape)
                .background(selectedPillColor)
                .zIndex(0f),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { environment ->
                val isSelected = selectedEnvironment == environment
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) selectedTextColor else unselectedTextColor,
                    animationSpec = tween(durationMillis = 200),
                    label = "${environment.name}TextColor",
                )
                val fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(environment) }
                        .zIndex(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(environment.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = fontWeight,
                        color = textColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
