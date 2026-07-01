package com.itsaky.androidide.fragments.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.ProjectHistory

/**
 * 删除当前项目前先弹一次确认。标题与提示语使用已存在的
 * `title_confirm_delete` / `msg_confirm_delete` 资源 (接受 %s 参数),
 * 与“是否删除”类问题的措辞保持一致。
 */
@Composable
fun DeleteProjectConfirmDialog(
    project: ProjectHistory,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
      icon = {
        Icon(
            Icons.Filled.FolderDelete,
            contentDescription = null,
            tint = Color(0xFFC62828),
            modifier = Modifier.size(28.dp),
        )
      },
      title = {
        Text(
            androidx.compose.ui.res.stringResource(R.string.title_confirm_delete),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Column {
          Text(
              androidx.compose.ui.res.stringResource(R.string.msg_confirm_delete, project.path),
              fontSize = 13.sp,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
              stringResource(R.string.delete_warning_disk),
              fontSize = 11.sp,
              color = Color.Gray,
          )
        }
      },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
        ) { Text(androidx.compose.ui.res.stringResource(R.string.title_viewaction_delete)) }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) {
          Text(androidx.compose.ui.res.stringResource(R.string.cancel_button))
        }
      },
  )
}

/**
 * 第二个对话框：删除进行中。带模糊的磨砂背景 + 进度条 + 正在删除的
 * 路径提示，不允许用户按返回或点击外部关闭，避免操作被静默中断。
 */
@Composable
fun DeleteProjectProgressDialog(
    projectPath: String,
    error: String?,
) {
  AlertDialog(
      onDismissRequest = { /* not dismissable while in-flight */ },
      properties =
          DialogProperties(
              dismissOnBackPress = error != null,
              dismissOnClickOutside = error != null,
          ),
      icon = {
        if (error == null) {
          CircularProgressIndicator(
              modifier = Modifier.size(28.dp),
              strokeWidth = 3.dp,
              color = Color(0xFF1565C0),
          )
        } else {
          Icon(
              Icons.Filled.Error,
              contentDescription = null,
              tint = Color(0xFFC62828),
              modifier = Modifier.size(28.dp),
          )
        }
      },
      title = {
        Text(
            if (error == null) stringResource(R.string.delete_in_progress)
            else stringResource(R.string.delete_project_failed),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Column {
          if (error == null) {
            // 进度条 (indeterminate) + 路径提示
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = Color(0xFF1565C0),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                projectPath,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.DarkGray,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.delete_in_progress_hint),
                fontSize = 11.sp,
                color = Color.Gray,
            )
          } else {
            Text(
                error,
                fontSize = 12.sp,
                color = Color(0xFFC62828),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.delete_failed_hint),
                fontSize = 11.sp,
                color = Color.Gray,
            )
          }
        }
      },
      confirmButton = {
        if (error != null) {
          TextButton(onClick = { /* dismissed by state machine */ }) {
            Text(stringResource(R.string.action_close))
          }
        }
      },
  )
}

/**
 * 第三个对话框：删除结果（成功 / 失败），给用户最终反馈后关闭整个流。
 */
@Composable
fun DeleteProjectResultDialog(
    success: Boolean,
    projectName: String,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
      icon = {
        Icon(
            if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (success) Color(0xFF2E7D32) else Color(0xFFC62828),
            modifier = Modifier.size(28.dp),
        )
      },
      title = {
        Text(
            if (success) stringResource(R.string.delete_success)
            else stringResource(R.string.delete_incomplete),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Text(
            if (success) stringResource(R.string.delete_success_detail, projectName)
            else stringResource(R.string.delete_incomplete_detail),
            fontSize = 12.sp,
        )
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
  )
}

/**
 * 三个对话框的状态机。`DeleteDialogState` 决定了当前展示哪一个对话框
 * (同时刻最多一个)，由调用方在协程完成后推进状态。
 */
sealed interface DeleteDialogState {
  data object Idle : DeleteDialogState

  data class Confirming(val project: ProjectHistory) : DeleteDialogState

  data class InProgress(val projectPath: String, val error: String? = null) :
      DeleteDialogState

  data class Done(val success: Boolean, val projectName: String) : DeleteDialogState
}
