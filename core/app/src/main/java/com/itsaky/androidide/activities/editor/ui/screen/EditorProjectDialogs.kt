package com.itsaky.androidide.activities.editor.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.R
import java.io.File

@Composable
fun EditorFindDialog(modules: List<File>, onDismiss: () -> Unit, onSearch: (String, List<String>, List<File>) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(modules.map { it.absolutePath }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.menu_find_project)) }, text = {
        SearchProjectScreen(query, filter, { query = it }, { filter = it },
            modules = modules.map { SearchProjectModule(it.absolutePath, it.name, it.absolutePath in selected) },
            onModuleCheckedChange = { id, checked -> selected = if (checked) selected + id else selected - id },
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        )
    }, confirmButton = {
        TextButton(enabled = query.isNotBlank() && selected.isNotEmpty(), onClick = {
            onSearch(query.trim(), filter.split('|').map(String::trim).filter(String::isNotEmpty), modules.filter { it.absolutePath in selected }.map { File(it, "src") })
        }) { Text(stringResource(R.string.menu_find)) }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
fun EditorTextInputDialog(title: String, initial: String, hint: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(text, { text = it }, placeholder = { Text(hint) })
    }, confirmButton = { TextButton(onClick = { onDismiss(); onConfirm(text.trim()) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}
