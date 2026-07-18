package android.zero.studio.shell.common.presentation.model

sealed class ShellState {
    object Free : ShellState()
    object Busy : ShellState()
    data class InputQuery(val input: String) : ShellState()
}