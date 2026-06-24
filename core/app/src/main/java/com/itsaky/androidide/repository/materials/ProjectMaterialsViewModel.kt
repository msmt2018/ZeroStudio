package com.itsaky.androidide.repository.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsaky.androidide.projects.materials.ProjectMaterialItem
import com.itsaky.androidide.projects.materials.ProjectMaterialsRepository
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Snapshot of basic information shown in the Material Detail dialog. */
data class MaterialDetailInfo(
    val exists: Boolean,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val absolutePath: String,
    val canonicalPath: String?,
    val name: String,
    val parentPath: String?,
    val sizeBytes: Long,
    val readableSize: String,
    val lastModifiedEpochMs: Long,
    val lastModifiedText: String,
    val lastAccessedEpochMs: Long,
    val lastAccessedText: String,
    val creationEpochMs: Long,
    val creationText: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canExecute: Boolean,
    val isHidden: Boolean,
    val extension: String,
    val mimeType: String?,
    val md5: String?,
    val sha1: String?,
    val sha256: String?,
    val errorMessage: String? = null,
) {
  companion object {
    val EMPTY =
        MaterialDetailInfo(
            exists = false,
            isDirectory = false,
            isFile = false,
            absolutePath = "",
            canonicalPath = null,
            name = "",
            parentPath = null,
            sizeBytes = 0L,
            readableSize = "—",
            lastModifiedEpochMs = 0L,
            lastModifiedText = "—",
            lastAccessedEpochMs = 0L,
            lastAccessedText = "—",
            creationEpochMs = 0L,
            creationText = "—",
            canRead = false,
            canWrite = false,
            canExecute = false,
            isHidden = false,
            extension = "",
            mimeType = null,
            md5 = null,
            sha1 = null,
            sha256 = null,
        )
  }
}

data class ProjectMaterialsUiState(
    val loading: Boolean = false,
    val items: List<ProjectMaterialItem> = emptyList(),
    val selected: ProjectMaterialItem? = null,
    val category: ProjectMaterialCategory = ProjectMaterialCategory.ALL,
)

class ProjectMaterialsViewModel(
    private val repository: ProjectMaterialsRepository = ProjectMaterialsRepository()
) : ViewModel() {
  private val _uiState = MutableStateFlow(ProjectMaterialsUiState(loading = true))
  val uiState: StateFlow<ProjectMaterialsUiState> = _uiState.asStateFlow()

  private val _detailState = MutableStateFlow(MaterialDetailInfo.EMPTY)
  val detailState: StateFlow<MaterialDetailInfo> = _detailState.asStateFlow()

  fun refresh() {
    _uiState.value = _uiState.value.copy(loading = true)
    viewModelScope.launch(Dispatchers.IO) {
      val items = repository.loadMaterials()
      _uiState.value = ProjectMaterialsUiState(
          loading = false,
          items = items,
          selected = items.firstOrNull(),
          category = _uiState.value.category,
      )
    }
  }

  fun selectCategory(category: ProjectMaterialCategory) {
    if (_uiState.value.category == category) return
    _uiState.value = _uiState.value.copy(category = category, selected = null)
  }

  fun select(item: ProjectMaterialItem) {
    _uiState.value = _uiState.value.copy(selected = item)
  }

  /**
   * Asynchronously loads the detail information for [path]. The result is pushed into
   * [detailState]. [onReady] is invoked once on the calling thread when the computation
   * finishes (success or failure).
   */
  fun loadDetail(path: String?, onReady: (() -> Unit)? = null) {
    if (path.isNullOrBlank()) {
      _detailState.value = MaterialDetailInfo.EMPTY
      onReady?.invoke()
      return
    }
    _detailState.value = MaterialDetailInfo.EMPTY
    viewModelScope.launch(Dispatchers.IO) {
      _detailState.value = computeDetail(path)
      withContext(Dispatchers.Main) { onReady?.invoke() }
    }
  }

  private fun computeDetail(path: String): MaterialDetailInfo {
    val file = File(path)
    return try {
      if (!file.exists()) {
        MaterialDetailInfo.EMPTY.copy(
            absolutePath = file.absolutePath,
            name = file.name,
            errorMessage = "Path does not exist on disk",
        )
      } else {
        val canonical = runCatching { file.canonicalPath }.getOrNull()
        val length = if (file.isFile) file.length() else file.walkTopDown().map { if (it.isFile) it.length() else 0L }.sum()
        val md5 = if (file.isFile && file.length() <= HASH_MAX_BYTES) digest(file, "MD5") else null
        val sha1 = if (file.isFile && file.length() <= HASH_MAX_BYTES) digest(file, "SHA-1") else null
        val sha256 = if (file.isFile && file.length() <= HASH_MAX_BYTES) digest(file, "SHA-256") else null
        val lastAccessMs = runCatching { Files_lastAccess(file) }.getOrDefault(0L)
        val creationMs = runCatching { Files_creation(file) }.getOrDefault(0L)
        MaterialDetailInfo(
            exists = true,
            isDirectory = file.isDirectory,
            isFile = file.isFile,
            absolutePath = file.absolutePath,
            canonicalPath = canonical,
            name = file.name.ifBlank { file.absolutePath },
            parentPath = file.parent,
            sizeBytes = length,
            readableSize = humanReadableSize(length),
            lastModifiedEpochMs = file.lastModified(),
            lastModifiedText = formatTimestamp(file.lastModified()),
            lastAccessedEpochMs = lastAccessMs,
            lastAccessedText = if (lastAccessMs <= 0L) "—" else formatTimestamp(lastAccessMs),
            creationEpochMs = creationMs,
            creationText = if (creationMs <= 0L) "—" else formatTimestamp(creationMs),
            canRead = file.canRead(),
            canWrite = file.canWrite(),
            canExecute = file.canExecute(),
            isHidden = file.isHidden,
            extension = file.extension,
            mimeType = guessMimeType(file),
            md5 = md5,
            sha1 = sha1,
            sha256 = sha256,
        )
      }
    } catch (t: Throwable) {
      MaterialDetailInfo.EMPTY.copy(
          absolutePath = file.absolutePath,
          name = file.name,
          errorMessage = t.message ?: t.javaClass.simpleName,
      )
    }
  }

  private fun digest(file: File, algorithm: String): String? = runCatching {
    val digest = MessageDigest.getInstance(algorithm)
    FileInputStream(file).use { input ->
      val buffer = ByteArray(DIGEST_BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
  }.getOrNull()

  private fun formatTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date(epochMs))
  }

  private fun humanReadableSize(bytes: Long): String {
    if (bytes < 0L) return "—"
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var size = bytes.toDouble() / 1024.0
    var idx = 0
    while (size >= 1024.0 && idx < units.lastIndex) {
      size /= 1024.0
      idx++
    }
    return "%.2f %s".format(size, units[idx])
  }

  private fun guessMimeType(file: File): String? {
    if (file.isDirectory) return "inode/directory"
    val ext = file.extension.lowercase()
    return when (ext) {
      "" -> null
      "jar", "srcjar" -> "application/java-archive"
      "aar" -> "application/aar"
      "zip" -> "application/zip"
      "class" -> "application/java-vm"
      "kt" -> "text/x-kotlin"
      "kts" -> "text/x-kotlin-script"
      "java" -> "text/x-java"
      "xml" -> "text/xml"
      "json" -> "application/json"
      "properties" -> "text/x-java-properties"
      "gradle" -> "text/x-gradle"
      "png" -> "image/png"
      "jpg", "jpeg" -> "image/jpeg"
      "gif" -> "image/gif"
      "txt", "md" -> "text/plain"
      else -> "application/octet-stream"
    }
  }

  /**
   * Best-effort last access time. On some filesystems the Java [File.lastModified] call is
   * the only timestamp exposed, so we fall back to it when BasicFileAttributes attributes
   * are unavailable.
   */
  private fun Files_lastAccess(file: File): Long = readAttributes(file)?.let { attrs ->
    runCatching { attrs.lastAccessTime().toMillis() }.getOrNull()
  } ?: file.lastModified()

  private fun Files_creation(file: File): Long = readAttributes(file)?.let { attrs ->
    runCatching { attrs.creationTime().toMillis() }.getOrNull()
  } ?: 0L

  private fun readAttributes(file: File): java.nio.file.attribute.BasicFileAttributes? = runCatching {
    java.nio.file.Files.readAttributes(file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java)
  }.getOrNull()

  private companion object {
    /** Hashing files larger than 50 MB is skipped to avoid blocking the UI thread. */
    const val HASH_MAX_BYTES: Long = 50L * 1024L * 1024L
    const val DIGEST_BUFFER_SIZE: Int = 64 * 1024
  }
}
