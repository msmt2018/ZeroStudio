/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.repository.materials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import java.io.File

/**
 * Type of output the user wants when decompiling a `.class` entry.
 */
enum class ClassDecompileFormat(val displayName: String, val extension: String, val icon: ImageVector) {
  JAVA("Java source", "java", Icons.Filled.Code),
  SMALI("Smali assembly", "smali", Icons.Filled.Memory),
  ;

  companion object {
    fun fromPreference(engine: String): ClassDecompileFormat =
        when (engine.lowercase()) {
          "smali" -> SMALI
          else -> JAVA
        }
  }
}

/** What the decompilation dialog is currently showing. */
sealed interface ClassDecompileState {
  object Choosing : ClassDecompileState

  data class Running(val format: ClassDecompileFormat, val stage: String) : ClassDecompileState

  data class Success(val format: ClassDecompileFormat, val output: File) : ClassDecompileState

  data class Failure(val format: ClassDecompileFormat, val message: String) : ClassDecompileState
}

/** Information about a `.class` entry to be decompiled – the entry path and the archive it lives in. */
data class ClassEntryTarget(
    val archive: File,
    val entryName: String,
    val displayName: String = entryName.substringAfterLast('/'),
)

/**
 * Top-level dialog that wraps the whole flow:
 *  1. Asks the user whether they want Java source or Smali.
 *  2. Shows progress while decompilation runs.
 *  3. Shows the final output file (and an error message on failure).
 *
 * The actual decompilation work is driven by [onChoose] which is invoked with the user
 * selection. The dialog is purely presentational – it does not touch the file system.
 *
 * @param state Current dialog state.
 * @param target Description of the entry being decompiled.
 * @param onChoose Called when the user picks a decompilation format.
 * @param onDismiss Called when the user closes the dialog.
 * @param onOpen Called when the user wants to open the produced file in the editor.
 */
@Composable
fun ClassDecompileDialog(
    state: ClassDecompileState,
    target: ClassEntryTarget,
    onChoose: (ClassDecompileFormat) -> Unit,
    onDismiss: () -> Unit,
    onOpen: (File) -> Unit,
) {
  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.92f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Header(target = target, onClose = onDismiss)
        Spacer(Modifier.height(8.dp))
        when (state) {
          ClassDecompileState.Choosing -> ChooseFormatContent(target = target, onChoose = onChoose)
          is ClassDecompileState.Running -> RunningContent(state = state)
          is ClassDecompileState.Success -> SuccessContent(state = state, onOpen = onOpen, onClose = onDismiss)
          is ClassDecompileState.Failure -> FailureContent(state = state, onRetry = {
            onChoose(state.format)
          }, onClose = onDismiss)
        }
      }
    }
  }
}

@Composable
private fun Header(target: ClassEntryTarget, onClose: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth(),
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
          imageVector = Icons.Filled.Science,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = "Decompile class",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )
      Text(
          text = target.entryName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
      )
    }
    TextButton(onClick = onClose) { Text("Close") }
  }
}

@Composable
private fun ChooseFormatContent(target: ClassEntryTarget, onChoose: (ClassDecompileFormat) -> Unit) {
  Text(
      text = "Choose how the class should be disassembled:",
      style = MaterialTheme.typography.bodyMedium,
  )
  Spacer(Modifier.height(12.dp))
  val engine = GeneralPreferences.decompilerEngine
  Column(modifier = Modifier.fillMaxWidth()) {
    ClassDecompileFormat.entries.forEach { format ->
      FormatRow(
          format = format,
          recommended = format == ClassDecompileFormat.fromPreference(engine),
          engine = engine,
          onClick = { onChoose(format) },
      )
      Spacer(Modifier.height(8.dp))
    }
  }
  Spacer(Modifier.height(4.dp))
  Text(
      text = "Default engine from preferences: $engine",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun FormatRow(
    format: ClassDecompileFormat,
    recommended: Boolean,
    engine: String,
    onClick: () -> Unit,
) {
  Surface(
      color =
          if (recommended) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
      shape = RoundedCornerShape(12.dp),
      tonalElevation = if (recommended) 2.dp else 0.dp,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = format.icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = format.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text =
                when (format) {
                  ClassDecompileFormat.JAVA ->
                      "Reconstructs a Java source representation (class declaration, fields, method signatures). Output: .${format.extension}"
                  ClassDecompileFormat.SMALI ->
                      "Low-level disassembly of the bytecode in smali format. Output: .${format.extension}"
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (recommended) {
          Spacer(Modifier.height(2.dp))
          Text(
              text = "Recommended for current engine ($engine)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      Button(onClick = onClick) { Text("Use") }
    }
  }
}

@Composable
private fun RunningContent(state: ClassDecompileState.Running) {
  Column(
      modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CircularProgressIndicator()
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Decompiling to ${state.format.displayName}…",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = state.stage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SuccessContent(
    state: ClassDecompileState.Success,
    onOpen: (File) -> Unit,
    onClose: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1B873F).copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          imageVector = Icons.Filled.Done,
          contentDescription = null,
          tint = Color(0xFF1B873F),
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = "${state.format.displayName} generated",
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
      )
      Text(
          text = state.output.absolutePath,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontFamily = FontFamily.Monospace,
      )
    }
  }
  Spacer(Modifier.height(16.dp))
  HorizontalDivider()
  Spacer(Modifier.height(12.dp))
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(onClick = onClose) { Text("Close") }
    Spacer(Modifier.width(8.dp))
    Button(onClick = { onOpen(state.output) }) { Text("Open in editor") }
  }
}

@Composable
private fun FailureContent(
    state: ClassDecompileState.Failure,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
  Row(verticalAlignment = Alignment.Top) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          imageVector = Icons.Filled.Error,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onErrorContainer,
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = "${state.format.displayName} failed",
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
      )
      Spacer(Modifier.height(4.dp))
      Text(
          text = state.message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          fontFamily = FontFamily.Monospace,
      )
    }
  }
  Spacer(Modifier.height(16.dp))
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(onClick = onClose) { Text("Close") }
    Spacer(Modifier.width(8.dp))
    Button(onClick = onRetry) { Text("Retry") }
  }
}
