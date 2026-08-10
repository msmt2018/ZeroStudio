package android.zero.studio.termux.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.zero.studio.termux.resources.strings
import android.zero.studio.termux.model.WorkingMode

enum class TerminalEnvironmentCategory(val title: String) {
    DEVELOPMENT("开发"),
    NON_DEVELOPMENT("非开发"),
    OTHER("其它"),
}

enum class TerminalEnvironmentOption(
    val labelRes: Int,
    val supportsRoot: Boolean,
    val category: TerminalEnvironmentCategory,
    val versions: List<String> = emptyList(),
) {
    UBUNTU(
        labelRes = strings.terminal_env_ubuntu,
        supportsRoot = true,
        category = TerminalEnvironmentCategory.DEVELOPMENT,
        versions = listOf("18.04", "20.04", "22.04", "24.04", "25.10", "26.04", "26.10 snapshot-1", "26.10 snapshot-2"),
    ),
    ALPINE(
        labelRes = strings.terminal_env_alpine,
        supportsRoot = true,
        category = TerminalEnvironmentCategory.NON_DEVELOPMENT,
    ),
    ARCH(
        labelRes = strings.terminal_env_arch,
        supportsRoot = true,
        category = TerminalEnvironmentCategory.NON_DEVELOPMENT,
    ),
    ANDROID(
        labelRes = strings.terminal_env_android,
        supportsRoot = false,
        category = TerminalEnvironmentCategory.OTHER,
    ),
}

fun terminalEnvironmentFromWorkingMode(mode: Int): TerminalEnvironmentOption = when (mode) {
    WorkingMode.UBUNTU,
    WorkingMode.UBUNTU_ROOT -> TerminalEnvironmentOption.UBUNTU
    WorkingMode.ALPINE,
    WorkingMode.ALPINE_ROOT -> TerminalEnvironmentOption.ALPINE
    WorkingMode.ARCH,
    WorkingMode.ARCH_ROOT -> TerminalEnvironmentOption.ARCH
    WorkingMode.ANDROID -> TerminalEnvironmentOption.ANDROID
    else -> TerminalEnvironmentOption.UBUNTU
}

fun workingModeIsRoot(mode: Int): Boolean = when (mode) {
    WorkingMode.ALPINE_ROOT,
    WorkingMode.ARCH_ROOT,
    WorkingMode.UBUNTU_ROOT -> true
    else -> false
}

fun terminalEnvironmentToWorkingMode(environment: TerminalEnvironmentOption, runAsRoot: Boolean): Int {
    val normalizedRoot = runAsRoot && environment.supportsRoot
    return when (environment) {
        TerminalEnvironmentOption.UBUNTU -> if (normalizedRoot) WorkingMode.UBUNTU_ROOT else WorkingMode.UBUNTU
        TerminalEnvironmentOption.ALPINE -> if (normalizedRoot) WorkingMode.ALPINE_ROOT else WorkingMode.ALPINE
        TerminalEnvironmentOption.ARCH -> if (normalizedRoot) WorkingMode.ARCH_ROOT else WorkingMode.ARCH
        TerminalEnvironmentOption.ANDROID -> WorkingMode.ANDROID
    }
}

fun terminalEnvironmentDescriptionRes(environment: TerminalEnvironmentOption, runAsRoot: Boolean): Int {
    val normalizedRoot = runAsRoot && environment.supportsRoot
    return when (environment) {
        TerminalEnvironmentOption.UBUNTU -> if (normalizedRoot) strings.ubuntu_root_desc else strings.ubuntu_desc
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
    val selectedCategory = selectedEnvironment.category
    Column(modifier = modifier.fillMaxWidth()) {
        SegmentedPill(
            options = TerminalEnvironmentCategory.entries,
            selected = selectedCategory,
            label = { it.title },
            onSelected = { category ->
                TerminalEnvironmentOption.entries.firstOrNull { it.category == category }?.let(onSelected)
            },
            minButtonHeight = minButtonHeight,
        )
        SegmentedPill(
            options = TerminalEnvironmentOption.entries.filter { it.category == selectedCategory },
            selected = selectedEnvironment,
            label = { stringResource(it.labelRes) },
            onSelected = onSelected,
            minButtonHeight = minButtonHeight,
        )
    }
}

@Composable
private fun <T> SegmentedPill(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    minButtonHeight: Dp,
) {
    val containerShape = RoundedCornerShape(12.dp)
    val pillShape = RoundedCornerShape(10.dp)
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val selectedPillColor = MaterialTheme.colorScheme.primary
    val selectedTextColor = MaterialTheme.colorScheme.onPrimary
    val unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(minButtonHeight)
            .clip(containerShape)
            .background(backgroundColor)
            .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            label = "terminalSelectorIndicatorOffset",
        )
        Box(
            modifier = Modifier.offset(x = indicatorOffset).width(segmentWidth).fillMaxHeight().padding(2.dp)
                .clip(pillShape).background(selectedPillColor).zIndex(0f),
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            options.forEach { option ->
                val isSelected = selected == option
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) selectedTextColor else unselectedTextColor,
                    animationSpec = tween(durationMillis = 200),
                    label = "${option.hashCode()}TextColor",
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(pillShape).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { onSelected(option) }.zIndex(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, color = textColor, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
