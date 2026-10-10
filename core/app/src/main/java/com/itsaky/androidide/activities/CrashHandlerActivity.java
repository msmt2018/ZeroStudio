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

package com.itsaky.androidide.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.app.IDEActivity
import com.itsaky.androidide.buildinfo.BuildInfo
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.BuildInfoUtils

/**
 * 崩溃接管 Activity，1:1 精确复刻原 XML (layout_crash_report.xml) 的 UI/UX 画面。
 * 纯粹遵循 Google 官方现代 Jetpack Compose 与 Material 3 规范开发。
 *
 * @author android_zero
 */
class CrashHandlerActivity : IDEActivity() {

  companion object {
    const val REPORT_ACTION = "com.itsaky.androidide.REPORT_CRASH"
    const val TRACE_KEY = "crash_trace"
  }

  private var rawTraceLog: String = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    val extras = intent?.extras
    rawTraceLog = extras?.getString(TRACE_KEY)?.takeIf { it.isNotBlank() }
        ?: "No stack trace was provided for the report."

    super.onCreate(savedInstanceState)
  }

  @Composable
  override fun ComposeContent() {
    val context = LocalContext.current
    val fullReport = remember(rawTraceLog) {
      buildString {
        appendLine("AndroidIDE Crash Report")
        appendLine(BuildInfoUtils.getBuildInfoHeader())
        appendLine()
        appendLine("Stacktrace:")
        appendLine(rawTraceLog)
      }
    }

    CrashHandlerScreen(
        reportText = fullReport,
        onCloseClicked = { finishAffinity() },
        onRestartClicked = { restartApp(context) },
        onCopyLogClicked = { copyReport(context, fullReport) },
        onReportIssueClicked = { reportIssue(context, fullReport) },
        onExitClicked = { finishAffinity() },
    )
  }

  private fun copyReport(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("AndroidIDE CrashLog", report)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, R.string.msg_log_copied, Toast.LENGTH_SHORT).show()
  }

  private fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    finishAffinity()
  }

  private fun reportIssue(context: Context, report: String) {
    copyReport(context, report)
    Toast.makeText(context, R.string.msg_log_copied_opening_browser, Toast.LENGTH_LONG).show()

    val url = "${BuildInfo.REPO_URL}/issues/new?assignees=&labels=bug&template=bug_report.md&title=[Bug]"
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }.onFailure {
      Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
    }
  }
}

/**
 * 严格按照 layout_crash_report.xml 的视觉约束复刻的 Compose 布局
 */
@Composable
private fun CrashHandlerScreen(
    reportText: String,
    onCloseClicked: () -> Unit,
    onRestartClicked: () -> Unit,
    onCopyLogClicked: () -> Unit,
    onReportIssueClicked: () -> Unit,
    onExitClicked: () -> Unit,
) {
  val verticalScrollState = rememberScrollState()
  val horizontalScrollState = rememberScrollState()

  Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
  ) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
      // ----------------------------------------------------------------------
      // 顶部区域：Title, Subtitle 与 右上角的 Close ImageButton (对应 XML 约束)
      // ----------------------------------------------------------------------
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .padding(top = 16.dp, start = 16.dp, end = 16.dp),
          verticalAlignment = Alignment.Top,
      ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
          // @id/crash_title
          Text(
              text = stringResource(R.string.msg_ide_crashed),
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
          )

          Spacer(modifier = Modifier.height(4.dp))

          // @id/crash_subtitle
          Text(
              text = stringResource(R.string.msg_report_crash),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // @id/close_button (48x48dp, ?attr/colorOnPrimaryContainer 着色)
        IconButton(
            onClick = onCloseClicked,
            modifier = Modifier.size(48.dp),
        ) {
          Icon(
              imageVector = Icons.Default.Close,
              contentDescription = stringResource(android.R.string.cancel),
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(24.dp),
          )
        }
      }

      // ----------------------------------------------------------------------
      // 中间区域：@id/log_text_container (NestedScrollView + HorizontalScrollView)
      // ----------------------------------------------------------------------
      Box(
          modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(16.dp),
      ) {
        SelectionContainer(
            modifier = Modifier.fillMaxSize(),
        ) {
          Box(
              modifier = Modifier
                  .fillMaxSize()
                  .verticalScroll(verticalScrollState)
                  .horizontalScroll(horizontalScrollState),
          ) {
            // @id/log_text
            Text(
                text = reportText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
            )
          }
        }
      }

      // ----------------------------------------------------------------------
      // 下方提示语：@id/textView4 (@string/msg_crash_report_hint)
      // ----------------------------------------------------------------------
      Text(
          text = stringResource(R.string.msg_crash_report_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
      )

      Spacer(modifier = Modifier.height(16.dp))

      // ----------------------------------------------------------------------
      // 底部操作按钮：Horizontal Packed Chain
      // restart_button (TextButton) -> copy_log_button (TextButton) ->
      // report_issue_button (ElevatedButton) -> exit_button (TextButton)
      // ----------------------------------------------------------------------
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp, start = 8.dp, end = 8.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        // @id/restart_button
        TextButton(
            onClick = onRestartClicked,
        ) {
          Text(text = stringResource(R.string.action_restart_app))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // @id/copy_log_button
        TextButton(
            onClick = onCopyLogClicked,
        ) {
          Text(text = stringResource(R.string.action_copy_log))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // @id/report_issue_button (ElevatedButton)
        ElevatedButton(
            onClick = onReportIssueClicked,
        ) {
          Text(text = stringResource(R.string.msg_report_issue_action))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // @id/exit_button
        TextButton(
            onClick = onExitClicked,
        ) {
          Text(text = stringResource(R.string.action_exit_app))
        }
      }
    }
  }
}