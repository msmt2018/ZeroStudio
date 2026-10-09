package com.itsaky.androidide.activities.editor.ui.screen


import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.R

/** Compose equivalent of `layout_search_project.xml`. */
@Composable
fun SearchProjectScreen(
    searchText: String,
    filterText: String,
    onSearchTextChange: (String) -> Unit,
    onFilterTextChange: (String) -> Unit,
    modules: List<SearchProjectModule> = emptyList(),
    onModuleCheckedChange: (moduleId: String, checked: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
    ) {
        Text(stringResource(R.string.msg_search_modules), modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            modules.forEach { module ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = module.checked,
                        onCheckedChange = { checked -> onModuleCheckedChange(module.id, checked) },
                    )
                    Text(module.name)
                }
            }
        }
        EditorTextField(searchText, onSearchTextChange, stringResource(R.string.text_to_search), R.drawable.ic_search)
        EditorTextField(filterText, onFilterTextChange, stringResource(R.string.hint_find_project_filter), R.drawable.ic_filter, stringResource(R.string.msg_find_project_filter))
    }
}

data class SearchProjectModule(val id: String, val name: String, val checked: Boolean = true)

@Composable
private fun EditorTextField(value: String, onValueChange: (String) -> Unit, label: String, iconRes: Int, helper: String? = null) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().padding(horizontal = 8.dp), label = { Text(label) }, leadingIcon = { Icon(painterResource(iconRes), null) }, supportingText = helper?.let { { Text(it) } }, singleLine = true)
}

/** Compose equivalent of `layout_editor_bottom_action.xml`. */
@Composable
fun EditorBottomAction(action: String, progress: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(action, Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
