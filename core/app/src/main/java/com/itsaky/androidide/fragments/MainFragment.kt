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
 *
 * @author android_zero
 */
package com.itsaky.androidide.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.fragment.app.viewModels
import com.itsaky.androidide.activities.MainActivity
import com.itsaky.androidide.activities.PreferencesActivity
import com.itsaky.androidide.activities.TerminalActivity
import com.itsaky.androidide.fragments.git.function.ZeroCloneDialogBottomSheetFragment
import com.itsaky.androidide.fragments.main.DeleteDialogState
import com.itsaky.androidide.fragments.main.DeleteProjectConfirmDialog
import com.itsaky.androidide.fragments.main.DeleteProjectProgressDialog
import com.itsaky.androidide.fragments.main.DeleteProjectResultDialog
import com.itsaky.androidide.fragments.main.ProjectManagerPage
import com.itsaky.androidide.fragments.main.SwipeableProjectItem
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.ProjectHistory
import com.itsaky.androidide.utils.RecentProjectsManager
import com.itsaky.androidide.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainFragment : BaseFragment() {

  private val viewModel by viewModels<MainViewModel>(ownerProducer = { requireActivity() })
  private val historyState = mutableStateListOf<ProjectHistory>()

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MaterialTheme {
          Surface(color = Color.White, modifier = Modifier.fillMaxSize()) { ZeroStudioMainLayout() }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewLifecycleScope.launch {
      val list = RecentProjectsManager.getHistoryAsync(requireContext())
      historyState.clear()
      historyState.addAll(list)
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun ZeroStudioMainLayout() {
    val scrollState = rememberScrollState()
    var selectedNav by rememberSaveable { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // Three-stage delete state machine driving the confirm / progress / result
    // dialogs. See `DeleteDialogState` for the transitions.
    var deleteState by remember { mutableStateOf<DeleteDialogState>(DeleteDialogState.Idle) }

    suspend fun refreshHistory() {
      val list = RecentProjectsManager.getHistoryAsync(requireContext())
      historyState.clear()
      historyState.addAll(list)
    }

    fun performPinToggle(project: ProjectHistory) {
      coroutineScope.launch {
        RecentProjectsManager.togglePinAsync(requireContext(), project.path)
        refreshHistory()
      }
    }

    fun performDelete(project: ProjectHistory) {
      // Stage 1: confirm. The actual destructive work happens once the user
      // confirms; see the `onConfirm` branch of [DeleteProjectConfirmDialog].
      deleteState = DeleteDialogState.Confirming(project)
    }

    Scaffold(
        topBar = {
          CenterAlignedTopAppBar(
              title = {
                Text(
                    stringResource(R.string.app_name),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
              },
              navigationIcon = { IconButton(onClick = {}) { Icon(Icons.Default.Menu, "Menu") } },
              actions = {
                IconButton(onClick = {}) { Icon(Icons.Default.Search, "Search") }
                IconButton(onClick = {}) {
                  Box(
                      modifier =
                          Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFEEEEEE))
                  ) {
                    Icon(Icons.Outlined.Person, "User", modifier = Modifier.align(Alignment.Center))
                  }
                }
              },
              colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
          )
        },
        bottomBar = {
          NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
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
          ProjectManagerPage(
              onOpenProject = { projectPath ->
                viewModel.openProject(requireContext(), File(projectPath))
              }
          )
        } else {
          Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp)
          ) {
          QuickStartGradientCard()

          Spacer(modifier = Modifier.height(20.dp))

          Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1.5f)) {
              SectionTitle(stringResource(R.string.main_recent_projects))
              if (historyState.isEmpty()) {
                Text(
                    stringResource(R.string.main_empty_history),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(8.dp),
                )
              } else {
                // Pinned items always float to the top, then the most recent
                // first. The list is rendered with a LazyColumn so the swipe
                // gestures on each row do not fight the outer vertical
                // scroll state.
                val recentSorted =
                    historyState.sortedWith(
                        compareByDescending<ProjectHistory> { it.isPinned }
                            .thenByDescending { it.timestamp }
                    )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  items(recentSorted, key = { it.path }) { project ->
                    SwipeableProjectItem(
                        project = project,
                        onClick = { openProject(File(project.path)) },
                        onPin = { performPinToggle(project) },
                        onDelete = { performDelete(project) },
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(0.9f)) {
              SectionTitle(stringResource(R.string.main_frequent_projects))
              if (historyState.isNotEmpty()) {
                val frequentSorted =
                    historyState.sortedWith(
                        compareByDescending<ProjectHistory> { it.isPinned }
                            .thenByDescending { it.openCount }
                    )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  items(frequentSorted, key = { it.path }) { project ->
                    SwipeableProjectItem(
                        project = project,
                        onClick = { openProject(File(project.path)) },
                        onPin = { performPinToggle(project) },
                        onDelete = { performDelete(project) },
                    )
                  }
                }
              } else {
                Text(
                    stringResource(R.string.main_empty_history),
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
              }
            }
          }
          }

          // 工具与服务区域
          Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 20.dp))
          ) {
            Surface(
              modifier = Modifier.matchParentSize().blur(radius = 18.dp), // 模糊半径
              color = Color.White.copy(alpha = 0.9f),
            ) {}

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
              SectionTitle(stringResource(R.string.main_tools_services))
              ToolsServiceGrid()
            }
          }
        }
      }
    }

    // ---- Delete flow: confirm -> in-progress -> result dialogs ----
    when (val state = deleteState) {
      DeleteDialogState.Idle -> { /* no dialog */ }
      is DeleteDialogState.Confirming -> {
        DeleteProjectConfirmDialog(
            project = state.project,
            onConfirm = {
              val project = state.project
              deleteState = DeleteDialogState.InProgress(project.path)
              coroutineScope.launch {
                val deleteError =
                    withContext(Dispatchers.IO) {
                      try {
                        val file = File(project.path)
                        // First, drop the history entry. We do this even if
                        // the directory delete later fails so the user is not
                        // stuck with a stale entry in the recent list.
                        RecentProjectsManager.removeProjectAsync(requireContext(), project.path)
                        if (file.exists()) {
                          file.deleteRecursively()
                        }
                        null
                      } catch (e: Exception) {
                        e.message ?: e::class.java.simpleName
                      }
                    }
                // Refresh the in-memory list so the swipe row collapses out
                // of the LazyColumn regardless of delete success/failure.
                refreshHistory()
                deleteState =
                    DeleteDialogState.Done(
                        success = deleteError == null,
                        projectName = project.name,
                    )
              }
            },
            onDismiss = { deleteState = DeleteDialogState.Idle },
        )
      }
      is DeleteDialogState.InProgress -> {
        DeleteProjectProgressDialog(
            projectPath = state.projectPath,
            error = state.error,
        )
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

  @Composable
  private fun QuickStartGradientCard() {
    val cardGradient =
        Brush.linearGradient(
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
                horizontalArrangement = Arrangement.spacedBy(8.dp), // 按钮之间的间距
                verticalAlignment = Alignment.CenterVertically,
            ) {
              QuickActionButton(
                  modifier = Modifier.weight(1f),
                  icon = Icons.Default.Add,
                  label = stringResource(R.string.main_new_project),
              ) {
                viewModel.setScreen(MainViewModel.SCREEN_TEMPLATE_LIST)
              }

              QuickActionButton(
                  modifier = Modifier.weight(1f),
                  icon = Icons.Default.FolderOpen,
                  label = stringResource(R.string.main_open_project),
              ) {
                pickDirectory { openProject(it) }
              }

              QuickActionButton(
                  modifier = Modifier.weight(1f),
                  icon = Icons.Default.Share,
                  label = stringResource(R.string.main_clone_repo),
              ) {
                ZeroCloneDialogBottomSheetFragment.newInstance(repoId = "")
                    .show(childFragmentManager, "CloneBottomSheet")
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun QuickActionButton(
      modifier: Modifier = Modifier,
      icon: ImageVector,
      label: String,
      onClick: () -> Unit,
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
        // 图标尺寸
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1, // 设置为不换行
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }

  /** 工具与服务区使用 `SwipeableProjectItem` 渲染带侧滑操作的列表项；见 [com.itsaky.androidide.fragments.main.SwipeableProjectItem]。 */
  @Composable
  private fun ToolsServiceGrid() {
    val context = LocalContext.current
    val tools =
        listOf(
            Triple(Icons.Default.Settings, Color(0xFFFFCCBC)) {
              startActivity(Intent(requireActivity(), PreferencesActivity::class.java))
            },
            Triple(Icons.Default.Terminal, Color(0xFFC8E6C9)) {
              startActivity(Intent(requireActivity(), TerminalActivity::class.java))
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
            Triple(Icons.Default.BugReport, Color(0xFFFFF9C4)) {
              Toast.makeText(context, R.string.msg_unimplemented_feature, Toast.LENGTH_SHORT).show()
            },
        )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
      items(tools) { (icon, color, action) ->
        // 工具与服务按钮
        Surface(
            onClick = action,
            modifier = Modifier.size(42.dp),
            color = color,
            shape = RoundedCornerShape(8.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            // 工具与服务内部Icon的尺寸
            Icon(
                icon,
                null,
                tint = Color.DarkGray.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp),
            )
          }
        }
      }
    }
  }

  @Composable
  private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF333333),
        modifier = Modifier.padding(bottom = 6.dp),
    )
  }

  private fun openProject(root: File) {
    (requireActivity() as MainActivity).openProject(root)
  }
}
