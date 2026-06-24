package com.itsaky.androidide.repository.materials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.itsaky.androidide.projects.materials.ProjectMaterialItem

/**
 * Modal dialog that renders the full metadata of a [ProjectMaterialItem] – path, size,
 * timestamps, permissions, MIME type and cryptographic hashes – in a scrollable list
 * of labelled rows.
 */
@Composable
fun MaterialDetailDialog(
    material: ProjectMaterialItem,
    detail: MaterialDetailInfo,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Header(material = material, loading = loading, onDismiss = onDismiss)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
        ) {
          if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Reading file metadata…", color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          } else {
            if (detail.errorMessage != null) {
              Text(
                  text = "⚠ ${detail.errorMessage}",
                  color = MaterialTheme.colorScheme.error,
                  modifier = Modifier.padding(vertical = 12.dp),
              )
            }
            FileSection(detail = detail)
            Spacer(Modifier.height(16.dp))
            TimeSection(detail = detail)
            Spacer(Modifier.height(16.dp))
            SecuritySection(detail = detail)
            Spacer(Modifier.height(16.dp))
            TypeSection(detail = detail, material = material)
            Spacer(Modifier.height(16.dp))
            SourceSection(material = material)
            Spacer(Modifier.height(20.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun Header(material: ProjectMaterialItem, loading: Boolean, onDismiss: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          imageVector = Icons.Filled.Tag,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = material.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
      )
      Text(
          text = material.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
      )
    }
    if (!loading) {
      TextButton(onClick = onDismiss) { Text("Close") }
    }
  }
}

@Composable
private fun FileSection(detail: MaterialDetailInfo) {
  SectionTitle("File", Icons.Filled.InsertDriveFile)
  InfoRow("Name", detail.name, monospace = false)
  InfoRow("Absolute Path", detail.absolutePath, copyable = true)
  detail.canonicalPath?.takeIf { it != detail.absolutePath }?.let {
    InfoRow("Canonical Path", it, copyable = true)
  }
  detail.parentPath?.let { InfoRow("Parent", it, copyable = true) }
  InfoRow("Size", "${detail.readableSize}  (${detail.sizeBytes} B)")
  InfoRow("Permissions",
      buildString {
        append(if (detail.isDirectory) "d" else "-")
        append(if (detail.canRead) "r" else "-")
        append(if (detail.canWrite) "w" else "-")
        append(if (detail.canExecute) "x" else "-")
        append(if (detail.isHidden) "  (hidden)" else "")
      })
}

@Composable
private fun TimeSection(detail: MaterialDetailInfo) {
  SectionTitle("Timestamps", Icons.Filled.Schedule)
  InfoRow("Last modified", detail.lastModifiedText, copyable = false)
  InfoRow("Last accessed", detail.lastAccessedText, copyable = false)
  InfoRow("Created", detail.creationText, copyable = false)
}

@Composable
private fun SecuritySection(detail: MaterialDetailInfo) {
  SectionTitle("Hashes", Icons.Filled.Security)
  InfoRow("MD5", detail.md5 ?: "—  (file too large or unreadable)", copyable = detail.md5 != null)
  InfoRow("SHA-1", detail.sha1 ?: "—  (file too large or unreadable)", copyable = detail.sha1 != null)
  InfoRow("SHA-256", detail.sha256 ?: "—  (file too large or unreadable)", copyable = detail.sha256 != null)
}

@Composable
private fun TypeSection(detail: MaterialDetailInfo, material: ProjectMaterialItem) {
  SectionTitle("Type", Icons.Filled.Folder)
  InfoRow("Kind", if (detail.isDirectory) "Directory" else "File")
  if (detail.extension.isNotEmpty()) InfoRow("Extension", ".${detail.extension}")
  detail.mimeType?.let { InfoRow("MIME", it) }
  InfoRow("API", material.apiName)
}

@Composable
private fun SourceSection(material: ProjectMaterialItem) {
  SectionTitle("Source", Icons.Filled.Tag)
  InfoRow("Origin", material.sourceType.name)
  InfoRow("Identifier", material.id, copyable = true)
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(6.dp))
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
  }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    copyable: Boolean = false,
    monospace: Boolean = true,
) {
  val clipboard = LocalClipboardManager.current
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      verticalAlignment = Alignment.Top,
  ) {
    Text(
        text = label,
        modifier = Modifier.width(120.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (copyable) {
      IconButton(
          onClick = { clipboard.setText(AnnotatedString(value)) },
          modifier = Modifier.size(28.dp),
      ) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = "Copy $label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
      }
    }
  }
}
