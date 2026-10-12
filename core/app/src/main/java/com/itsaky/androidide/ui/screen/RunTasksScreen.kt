package com.itsaky.androidide.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.tooling.api.models.GradleTask
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunTasksScreen(
    activity: EditorHandlerActivity,
    onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()

  var query by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(true) }
  var isConfirming by remember { mutableStateOf(false) }

  val allTasks = remember { mutableStateListOf<GradleTask>() }
  val selectedPaths = remember { mutableStateListOf<String>() }

  // 异步加载项目全部 Gradle 任务
  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      val workspace = IProjectManager.getInstance().getWorkspace()
      val tasks = workspace?.getSubProjects()?.flatMap { it.tasks }.orEmpty()
      withContext(Dispatchers.Main) {
        allTasks.clear()
        allTasks.addAll(tasks)
        isLoading = false
      }
    }
  }

  val filteredTasks = remember(query, allTasks.size) {
    if (query.isBlank()) allTasks
    else allTasks.filter { it.path.contains(query, ignoreCase = true) || it.description?.contains(query, ignoreCase = true) == true }
  }

  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = sheetState,
      shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
  ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      Text(
          text = stringResource(R.string.title_run_tasks),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
      )

      Spacer(modifier = Modifier.height(10.dp))

      if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      } else if (isConfirming) {
        // 二次确认面板
        Text(
            text = "确认运行以下选中的任务吗？",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
          items(selectedPaths) { path ->
            Text(text = "• $path", style = MaterialTheme.typography.bodySmall)
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = { isConfirming = false }) {
            Text("返回选择")
          }
          Button(
              onClick = {
                val service = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
                if (service == null || !service.isToolingServerStarted) {
                  activity.flashError(R.string.msg_tooling_server_unavailable)
                  return@Button
                }
                service.executeTasks(*selectedPaths.toTypedArray())
                onDismiss()
              },
          ) {
            Text("立即执行")
          }
        }
      } else {
        // 搜索与多选面板
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索任务名称...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(filteredTasks, key = { it.path }) { task ->
            val checked = selectedPaths.contains(task.path)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      if (checked) selectedPaths.remove(task.path)
                      else selectedPaths.add(task.path)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                  checked = checked,
                  onCheckedChange = { isChecked ->
                    if (isChecked == true) selectedPaths.add(task.path)
                    else selectedPaths.remove(task.path)
                  },
              )
              Column(modifier = Modifier.padding(start = 6.dp)) {
                Text(
                    text = task.path,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
                task.description?.let {
                  Text(text = it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = "已选 ${selectedPaths.size} 项",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(
                enabled = selectedPaths.isNotEmpty(),
                onClick = { isConfirming = true },
            ) {
              Icon(Icons.Default.PlayArrow, contentDescription = null)
              Text("下一步")
            }
          }
        }
      }
    }
  }
}