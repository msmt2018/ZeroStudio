package android.zero.studio.lsp.clangd.model

/**
 * Native 层 Location 结果
 */
data class Location(
    val filePath: String,
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int
)
