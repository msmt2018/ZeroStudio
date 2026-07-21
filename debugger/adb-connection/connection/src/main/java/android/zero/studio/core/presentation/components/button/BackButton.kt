package android.zero.studio.core.presentation.components.button

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import android.zero.studio.R
import android.zero.studio.core.presentation.components.haptic.withHaptic
import android.zero.studio.core.presentation.components.tooltip.TooltipContent
import android.zero.studio.navigation.LocalNavController
import android.zero.studio.navigation.navigateBack

@Composable
fun BackButton(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current

    TooltipContent(stringResource(R.string.back_button)) {
        IconButton(onClick = withHaptic(HapticFeedbackType.VirtualKey) {
            navController.navigateBack()
        }) {
            Icon(
                modifier = modifier,
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}