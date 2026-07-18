package android.zero.studio.commandexamples.presentation.model

import androidx.compose.ui.text.input.TextFieldValue

/**
 * @param Search Holds the states for SearchBar
 */
sealed class CmdScreenUiState {
    data class Search(
        val textFieldValue: TextFieldValue = TextFieldValue(""),
        val isVisible: Boolean = true
    )
}