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

package com.itsaky.androidide.actions.filetree

import android.app.Activity
import android.content.ClipData
import androidx.core.content.ContextCompat
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.requireContext
import com.itsaky.androidide.actions.requireFile
import java.io.File

/**
 * An action to convert a file path within a source directory (java/kotlin) to its
 * corresponding package/class path.
 *
 * @author android_zero
 */
class ConvertPathToAction(context: Context, override val order: Int) : ActionItem {

    override val id: String = "filetree.convert.path"
    override var label: String = "Copy as Java/Kotlin Path"
    override var visible: Boolean = true
    override var enabled: Boolean = true
    override var icon: Drawable? = null
    override var requiresUIThread: Boolean = true // Dialogs must be on UI thread
    override var location: ActionItem.Location = ActionItem.Location.EDITOR_FILE_TREE

    private var targetFile: File? = null
    private var javaPackagePath: String? = null
    private var kotlinPackagePath: String? = null

  init {
    // label = context.getString(R.string.title_file_copy_convert_path)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_file_copy_convert_path)
  }
  
    override fun prepare(data: ActionData) {
        val file = data.requireFile()
        targetFile = file
        
        val sourceRoots = listOf("src/main/java", "src/main/kotlin")
        var relativePath: String? = null

        for (root in sourceRoots) {
            if (file.absolutePath.contains(root)) {
                // Split path by the source root to get the package part
                relativePath = file.absolutePath.substringAfter("$root/")
                break
            }
        }

        if (relativePath != null) {
            javaPackagePath = relativePath.removeSuffix(".java").replace(File.separator, ".")
            kotlinPackagePath = relativePath.removeSuffix(".kt").replace(File.separator, ".")
            visible = true
            enabled = true
        } else {
            visible = false
            enabled = false
        }
    }

    override suspend fun execAction(data: ActionData): Any {
        val context = data.requireContext()
        val results = buildResults()
        
        showResultsDialog(context, results)
        
        return true
    }

    private fun buildResults(): List<Pair<String, String>> {
        val javaPath = javaPackagePath ?: return emptyList()
        val kotlinPath = kotlinPackagePath ?: return emptyList()

        val isClass = targetFile?.extension in listOf("java", "kt")
        
        return mutableListOf<Pair<String, String>>().apply {
            add("Java Package" to "package $javaPath;")
            add("Kotlin Package" to "package $kotlinPath")
            
            if (isClass) {
                add("Java Import" to "import $javaPath;")
                add("Kotlin Import" to "import $kotlinPath")
                add("Java Class Path" to javaPath)
                add("Kotlin Class Path" to kotlinPath)
            } else {
                 add("Java Package Path" to javaPath)
                 add("Kotlin Package Path" to kotlinPath)
            }
        }
    }

    private fun showResultsDialog(context: Context, results: List<Pair<String, String>>) {
        val composeView = ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    PathConverterResultsDialog(
                        results = results,
                        onCopy = { text ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Path", text))
                            (context as? Activity)?.let {
                                // You would need a flashbar/toast utility here
                                // Toast.makeText(it, "Copied!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Copy Path")
            .setView(composeView)
            .setPositiveButton("Close", null)
            .show()
    }

    @Composable
    fun PathConverterResultsDialog(results: List<Pair<String, String>>, onCopy: (String) -> Unit) {
        LazyColumn(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { (title, value) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopy(value) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}