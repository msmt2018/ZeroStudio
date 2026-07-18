package android.zero.studio.shell.file_browser.presentation.component.dialog

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.zero.studio.R
import android.zero.studio.core.presentation.components.buttongroup.OverflowButtonGroup
import android.zero.studio.core.presentation.components.dialog.DialogContainer
import android.zero.studio.core.presentation.components.text.AutoResizeableText
import android.zero.studio.core.presentation.components.text.DialogTitle
import android.zero.studio.core.presentation.model.ButtonConfigDefaults
import android.zero.studio.core.presentation.model.ButtonGroupItem
import android.zero.studio.core.presentation.model.ButtonType

@Composable
fun DeleteFileDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    title: String,
    message: String
) {

    DialogContainer(
        onDismiss = onDismiss,
    ) {
        DialogTitle(text = title)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        val buttonGroupItems = listOf(
            ButtonGroupItem(
                buttonConfig = ButtonConfigDefaults.defaultConfig(type = ButtonType.OutlinedButton),
                text = stringResource(R.string.cancel),
                onClick = { onDismiss() }
            ),
            ButtonGroupItem(
                text = stringResource(R.string.delete),
                onClick = { onDelete() }
            )
        )

        OverflowButtonGroup(items = buttonGroupItems)
    }
}