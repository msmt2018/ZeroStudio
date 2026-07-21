@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package android.zero.studio.shell.common.presentation.components.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.zero.studio.commandexamples.presentation.component.chip.LabelChip
import android.zero.studio.core.presentation.components.card.CustomCard
import android.zero.studio.core.presentation.components.haptic.withHaptic
import android.zero.studio.core.presentation.theme.CustomCardShape
import android.zero.studio.shell.common.domain.model.Suggestion
import android.zero.studio.shell.common.domain.model.SuggestionLabel
import android.zero.studio.shell.common.domain.model.SuggestionType
import android.zero.studio.shell.common.presentation.viewmodel.ShellViewModel

@Composable
fun SuggestionCard(
    modifier: Modifier = Modifier,
    suggestion: Suggestion,
    shape: CustomCardShape,
    viewModel: ShellViewModel = hiltViewModel()
) {
    val containerColor = when (suggestion.type) {
        SuggestionType.COMMAND -> MaterialTheme.colorScheme.surfaceContainer
        SuggestionType.PACKAGE -> MaterialTheme.colorScheme.surfaceContainerHigh
        SuggestionType.PERMISSION -> MaterialTheme.colorScheme.tertiaryContainer
    }

    val contentColor = when (suggestion.type) {
        SuggestionType.COMMAND, SuggestionType.PACKAGE -> MaterialTheme.colorScheme.onSurface
        SuggestionType.PERMISSION -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    CustomCard(
        modifier = modifier,
        onClick = withHaptic { viewModel.applySuggestion(suggestion) },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = suggestion.text,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = contentColor,
                modifier = Modifier.weight(1f, fill = false)
            )

            suggestion.label?.let { label ->
                LabelChip(
                    modifier = Modifier.padding(start = 20.dp),
                    label = label.name
                        .lowercase()
                        .replaceFirstChar { it.uppercase() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (label == SuggestionLabel.SYSTEM) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = if (label == SuggestionLabel.SYSTEM) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        }
    }
}

