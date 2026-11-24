package com.itsaky.androidide.actions.menu.api

/**
 * Represents a code snippet template.
 *
 * @param name The user-friendly name displayed in the selection dialog (e.g., "Public Static
 *   Method").
 * @param template The code template itself. Uses %s for name substitution and $0 for final cursor
 *   position.
 * @param description A brief description of what the snippet does.
 * @param requiresNameInput True if the template contains a %s and needs the user to provide a name.
 */
data class CodeSnippet(
    val name: String,
    val template: String,
    val description: String,
    val requiresNameInput: Boolean = template.contains("%s"),
)

