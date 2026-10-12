/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.activities.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.provider.DocumentsContractCompat
import com.itsaky.androidide.activities.PreferencesActivity
import com.itsaky.androidide.activities.TerminalActivity
import com.itsaky.androidide.fragments.main.DeleteDialogState
import com.itsaky.androidide.fragments.main.DeleteProjectConfirmDialog
import com.itsaky.androidide.fragments.main.DeleteProjectProgressDialog
import com.itsaky.androidide.fragments.main.DeleteProjectResultDialog
import com.itsaky.androidide.fragments.main.ProjectManagerPage
import com.itsaky.androidide.fragments.main.SwipeableProjectItem
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.ProjectHistory
import com.itsaky.androidide.utils.RecentProjectsManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 纯 Compose 首页核心屏幕组件。
 * 替代旧版基于 BaseFragment 的 MainFragment。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNewProject: () -> Unit,
    onOpenProject: (File) -> Unit,
    onCloneRepo: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val historyState = remember { mutableStateListOf<ProjectHistory>() }

  val recentScrollState = rememberScrollState()
  val frequentScrollState = rememberScrollState()
  var selectedNav by rememberSaveable { mutableIntStateOf(0) }
  var deleteState by remember { mutableStateOf<DeleteDialogState>(DeleteDialogState.Idle) }

  // 刷新历史列表
  suspend fun refreshHistory() {
    val list = RecentProjectsManager.getHistoryAsync(context)
    historyState.clear()
    historyState.addAll(list)
  }

  LaunchedEffect(Unit) {
    refreshHistory()
  }

  // 系统目录选择器，用于打开本地项目
  val folderPicker = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    uri?.let {
      val targetDir = uriToDirectoryFile(context, it)
      if (targetDir != null && targetDir.exists()) {
        onOpenProject(targetDir)
      } else {
        Toast.makeText(context, "Cannot access selected directory", Toast.LENGTH_SHORT).show()
      }
    }
  }

  Scaffold(
      modifier = modifier.fillMaxSize(),
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  stringResource(R.string.app_name),
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 18.sp,
              )
            },
            navigationIcon = {
              IconButton(onClick = {}) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
            },
            actions = {
              IconButton(onClick = {}) { Icon(Icons.Default.Search, contentDescription = "Search") }
              IconButton(onClick = {}) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFEEEEEE)),
                    contentAlignment = Alignment.Center,
                ) {
                  Icon(Icons.Outlined.Person, contentDescription = "User", modifier = Modifier.size(18.dp))
                }
              }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
      },
      bottomBar = {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
          NavigationBarItem(
              selected = selectedNav == 0,
              onClick = { selectedNav = 0 },
              icon = { Icon(Icons.Default.Home, null) },
              label = { Text(stringResource(R.string.main_nav_home)) },
          )
          NavigationBarItem(
              selected = selectedNav == 1,
              onClick = { selectedNav = 1 },
              icon = { Icon(Icons.Default.Folder, null) },
              label = { Text(stringResource(R.string.main_nav_projects)) },
          )
          NavigationBarItem(
              selected = selectedNav == 2,
              onClick = { selectedNav = 2 },
              icon = { Icon(Icons.Default.History, null) },
              label = { Text(stringResource(R.string.main_nav_history)) },
          )
          NavigationBarItem(
              selected = selectedNav == 3,
              onClick = { selectedNav = 3 },
              icon = { Icon(Icons.Default.Build, null) },
              label = { Text(stringResource(R.string.main_nav_tools)) },
          )
          NavigationBarItem(
              selected = selectedNav == 4,
              onClick = { selectedNav = 4 },
              icon = { Icon(Icons.Default.Person, null) },
              label = { Text(stringResource(R.string.main_nav_mine)) },
          )
        }
      },
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
      if (selectedNav == 1) {
        ProjectManagerPage(onOpenProject = { path -> onOpenProject(File(path)) })
      } else {
        Box(modifier = Modifier.fillMaxSize()) {
          Column(
              modifier = Modifier
                  .fillMaxSize()
                  .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
          ) {
            // 顶部快捷开始卡片
            QuickStartGradientCard(
                onNewProject = onNewProject,
                onOpenProject = { folderPicker.launch(null) },
                onCloneRepo = onCloneRepo,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 最近项目与高频项目左右并排列表
            Row(modifier = Modifier.fillMaxSize()) {
              // 1. 最近项目（左）
              Column(modifier = Modifier.weight(1.5f).fillMaxHeight().clipToBounds()) {
                SectionTitle(stringResource(R.string.main_recent_projects))
                if (historyState.isEmpty()) {
                  Text(
                      stringResource(R.string.main_empty_history),
                      fontSize = 12.sp,
                      color = Color.Gray,
                      modifier = Modifier.padding(8.dp),
                  )
                } else {
                  val recentSorted = remember(historyState.size) {
                    historyState.sortedWith(
                        compareByDescending<ProjectHistory> { it.isPinned }
                            .thenByDescending { it.timestamp }
                    )
                  }
                  Column(
                      modifier = Modifier.fillMaxWidth().verticalScroll(recentScrollState).padding(end = 6.dp),
                      verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    recentSorted.forEach { project ->
                      key(project.path) {
                        SwipeableProjectItem(
                            project = project,
                            onClick = {
                              val target = File(project.path)
                              if (target.exists()) onOpenProject(target)
                              else {
                                coroutineScope.launch {
                                  RecentProjectsManager.removeProjectAsync(context, project.path)
                                  refreshHistory()
                                }
                              }
                            },
                            onPin = {
                              coroutineScope.launch {
                                RecentProjectsManager.togglePinAsync(context, project.path)
                                refreshHistory()
                              }
                            },
                            onDelete = { deleteState = DeleteDialogState.Confirming(project) },
                            showOpenCount = false,
                        )
                      }
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.width(8.dp))

              // 2. 高频项目（右）
              Column(modifier = Modifier.weight(0.9f).fillMaxHeight().clipToBounds()) {
                SectionTitle(stringResource(R.string.main_frequent_projects))
                if (historyState.isEmpty()) {
                  Text(
                      stringResource(R.string.main_empty_history),
                      fontSize = 12.sp,
                      color = Color.Gray,
                  )
                } else {
                  val frequentSorted = remember(historyState.size) {
                    historyState.sortedWith(
                        compareByDescending<ProjectHistory> { it.isPinned }
                            .thenByDescending { it.openCount }
                    )
                  }
                  Column(
                      modifier = Modifier.fillMaxWidth().verticalScroll(frequentScrollState).padding(start = 6.dp),
                      verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    frequentSorted.forEach { project ->
                      key(project.path) {
                        SwipeableProjectItem(
                            project = project,
                            onClick = {
                              val target = File(project.path)
                              if (target.exists()) onOpenProject(target)
                              else {
                                coroutineScope.launch {
                                  RecentProjectsManager.removeProjectAsync(context, project.path)
                                  refreshHistory()
                                }
                              }
                            },
                            onPin = {
                              coroutineScope.launch {
                                RecentProjectsManager.togglePinAsync(context, project.path)
                                refreshHistory()
                              }
                            },
                            onDelete = { deleteState = DeleteDialogState.Confirming(project) },
                            showOpenCount = true,
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // 悬浮底部工具网格
          Box(
              modifier = Modifier
                  .align(Alignment.BottomCenter)
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 20.dp)),
          ) {
            Surface(
                modifier = Modifier.matchParentSize().blur(radius = 18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {}

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
              SectionTitle(stringResource(R.string.main_tools_services))
              ToolsServiceGrid()
            }
          }
        }
      }
    }

    // 删除项目的三步对话框
    when (val state = deleteState) {
      DeleteDialogState.Idle -> {}
      is DeleteDialogState.Confirming -> {
        DeleteProjectConfirmDialog(
            project = state.project,
            onConfirm = {
              val project = state.project
              deleteState = DeleteDialogState.InProgress(project.path)
              coroutineScope.launch {
                val error = withContext(Dispatchers.IO) {
                  runCatching {
                    RecentProjectsManager.removeProjectAsync(context, project.path)
                    val f = File(project.path)
                    if (f.exists()) f.deleteRecursively()
                    null
                  }.getOrNull()
                }
                refreshHistory()
                deleteState = DeleteDialogState.Done(success = error == null, projectName = project.name)
              }
            },
            onDismiss = { deleteState = DeleteDialogState.Idle },
        )
      }
      is DeleteDialogState.InProgress -> {
        DeleteProjectProgressDialog(projectPath = state.projectPath, error = state.error)
      }
      is DeleteDialogState.Done -> {
        DeleteProjectResultDialog(
            success = state.success,
            projectName = state.projectName,
            onDismiss = { deleteState = DeleteDialogState.Idle },
        )
      }
    }
  }
}

@Composable
private fun QuickStartGradientCard(
    onNewProject: () -> Unit,
    onOpenProject: () -> Unit,
    onCloneRepo: () -> Unit,
) {
  val cardGradient = Brush.linearGradient(
      colors = listOf(Color(0xFF3F1D9B), Color(0xFF00A79D)),
      start = Offset(0f, 0f),
      end = Offset(1000f, 1000f),
  )

  Card(
      modifier = Modifier.fillMaxWidth().height(160.dp),
      shape = RoundedCornerShape(20.dp),
      elevation = CardDefaults.cardElevation(4.dp),
  ) {
    Box(modifier = Modifier.fillMaxSize().background(cardGradient).padding(20.dp)) {
      Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.main_quick_start),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            QuickActionButton(modifier = Modifier.weight(1f), icon = Icons.Default.Add, label = stringResource(R.string.main_new_project), onClick = onNewProject)
            QuickActionButton(modifier = Modifier.weight(1f), icon = Icons.Default.FolderOpen, label = stringResource(R.string.main_open_project), onClick = onOpenProject)
            QuickActionButton(modifier = Modifier.weight(1f), icon = Icons.Default.Share, label = stringResource(R.string.main_clone_repo), onClick = onCloneRepo)
          }
        }
      }
    }
  }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      onClick = onClick,
      color = Color.White.copy(alpha = 0.15f),
      shape = RoundedCornerShape(10.dp),
      modifier = modifier,
  ) {
    Row(
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(4.dp))
      Text(
          text = label,
          color = Color.White,
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun ToolsServiceGrid() {
  val context = LocalContext.current
  val tools = listOf(
      Triple(Icons.Default.Settings, Color(0xFFFFCCBC)) {
        context.startActivity(Intent(context, PreferencesActivity::class.java))
      },
      Triple(Icons.Default.Terminal, Color(0xFFC8E6C9)) {
        context.startActivity(Intent(context, TerminalActivity::class.java))
      },
      Triple(Icons.Default.Code, Color(0xFFE1BEE7)) {
        Toast.makeText(context, R.string.msg_unimplemented_feature, Toast.LENGTH_SHORT).show()
      },
      Triple(Icons.Default.Construction, Color(0xFFBBDEFB)) {
        Toast.makeText(context, R.string.msg_unimplemented_feature, Toast.LENGTH_SHORT).show()
      },
      Triple(Icons.Default.CloudSync, Color(0xFFD1C4E9)) {
        Toast.makeText(context, R.string.msg_unimplemented_feature, Toast.LENGTH_SHORT).show()
      },
  )

  LazyRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(vertical = 4.dp),
  ) {
    items(tools) { (icon, color, action) ->
      Surface(
          onClick = action,
          modifier = Modifier.size(42.dp),
          color = color,
          shape = RoundedCornerShape(8.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(icon, null, tint = Color.DarkGray.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
      text = text,
      fontSize = 13.sp,
      fontWeight = FontWeight.ExtraBold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(bottom = 6.dp),
  )
}

/** 解析 SAF 树 URI 到本地物理文件。 */
private fun uriToDirectoryFile(context: Context, uri: Uri): File? {
  return runCatching {
    val docUri = DocumentsContractCompat.buildDocumentUriUsingTree(uri, DocumentsContractCompat.getTreeDocumentId(uri)!!)!!
    val docId = DocumentsContractCompat.getDocumentId(docUri)!!
    val split = docId.split(':')
    if (split.size > 1 && "primary".equals(split[0], ignoreCase = true)) {
      File(Environment.getExternalStorageDirectory(), split[1])
    } else {
      File(docId)
    }
  }.getOrNull()
}