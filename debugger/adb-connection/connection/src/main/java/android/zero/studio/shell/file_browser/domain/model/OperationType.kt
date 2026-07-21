package android.zero.studio.shell.file_browser.domain.model

import androidx.annotation.Keep

/**
 * Types of file operations supported by the file browser
 */
@Keep
enum class OperationType {
    UPLOAD,
    DOWNLOAD,
    COPY,
    MOVE
}
