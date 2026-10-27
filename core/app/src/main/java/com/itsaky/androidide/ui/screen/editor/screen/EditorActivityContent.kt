package com.itsaky.androidide.activities.editor.ui.screen

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.MenuItem
import android.zero.studio.widget.editor.symbolinput.SymbolInputPanel
import android.zero.studio.widget.editor.symbolinput.SymbolManagerActivity
import androidx.activity.compose.BackHandler
import androidx.appcompat.view.menu.MenuBuilder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.BaseEditorActivity
import com.itsaky.androidide.activities.editor.ui.state.EditorUiState
import com.itsaky.androidide.onboarding.OnboardingTarget
import com.itsaky.androidide.onboarding.onboardingBind
import com.itsaky.androidide.preferences.internal.SidebarPreferences
import com.itsaky.androidide.ui.SymbolInputVisibilityManager
import com.itsaky.androidide.ui.screen.editor.EditorWorkspaceScreen
import com.itsaky.androidide.ui.screen.editor.bottomsheet.BottomDrawerValue
import com.itsaky.androidide.ui.screen.editor.bottomsheet.rememberBottomDrawerSheetState
import com.itsaky.androidide.ui.screen.editor.sidebar.FullscreenDrawerValue
import com.itsaky.androidide.ui.screen.editor.sidebar.rememberFullscreenDrawerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑器主操作界面：
 * - 补齐了符号输入面板 (SymbolInputPanel)、气泡引导 (EditorOnboarding)、构建状态与光标位置
 * - 彻底清除任何旧版 ModalBottomSheet、传统 DrawerLayout 等沉积代码
 */
@Composable
fun EditorActivityContent(activity: BaseEditorActivity, state: EditorUiState) {
  val scope = rememberCoroutineScope()
  var sharing by remember { mutableStateOf(false) }

  // 初始化气泡引导目标点
  val guideTargets = remember {
    listOf("editor.drawer", "editor.status", "editor.symbols").map(OnboardingTarget::of)
  }

  // 状态机绑定：两态全屏左侧抽屉 + 三分屏铁律底栏抽屉
  val sidebarDrawerState = rememberFullscreenDrawerState(
      initialValue = if (state.drawerOpen) FullscreenDrawerValue.OPEN else FullscreenDrawerValue.CLOSED
  )
  val bottomDrawerState = rememberBottomDrawerSheetState(
      initialValue = if (state.bottomSheet.visible) BottomDrawerValue.PEEK else BottomDrawerValue.HIDDEN
  )

  // 实时双向状态总线对齐
  LaunchedEffect(sidebarDrawerState.isOpen) {
    state.drawerOpen = sidebarDrawerState.isOpen
    activity.editorViewModel.startDrawerOpened = sidebarDrawerState.isOpen
  }
  LaunchedEffect(state.drawerOpen) {
    if (state.drawerOpen != sidebarDrawerState.isOpen) {
      if (state.drawerOpen) sidebarDrawerState.open() else sidebarDrawerState.close()
    }
  }

  LaunchedEffect(bottomDrawerState.isExpanded) {
    state.bottomSheet.visible = bottomDrawerState.isExpanded
  }
  LaunchedEffect(state.bottomSheet.visible) {
    if (state.bottomSheet.visible && !bottomDrawerState.isExpanded) {
      bottomDrawerState.animateTo(BottomDrawerValue.PEEK)
    } else if (!state.bottomSheet.visible && bottomDrawerState.isExpanded) {
      bottomDrawerState.animateTo(BottomDrawerValue.HIDDEN)
    }
  }

  // 当两抽屉均处于收起状态时，拦截系统返回键触发关闭工程确认
  BackHandler(!sidebarDrawerState.isOpen && !bottomDrawerState.isExpanded && state.dialog == null) {
    if (state.memoryVisible) {
      state.memoryVisible = false
    } else {
      activity.doConfirmProjectClose()
    }
  }

  val colors = editorColorScheme(activity)
  val documentStates = rememberEditorPageStates(state.documents)

  MaterialTheme(colorScheme = colors) {
    Box(modifier = Modifier.fillMaxSize()) {
      EditorWorkspaceScreen(
          sidebarDrawerState = sidebarDrawerState,
          bottomDrawerState = bottomDrawerState,
          sidebarRegistry = state.sidebar,
          bottomPages = state.bottomSheet.pages,
          activity = activity,
          sidebarPosition = SidebarPreferences.sidebarBarPosition, // 动态读取设置项四向方位
          toolbarActions = {
            state.toolbarVersion // 触发重组
            val items = (0 until activity.toolbarMenu.size())
                .map(activity.toolbarMenu::getItem)
                .filter { it.isVisible }

            items.filter { it.icon != null }.forEach { item ->
              ActionToolbarItem(item = item, menu = activity.toolbarMenu)
            }

            IconButton(onClick = { state.memoryVisible = !state.memoryVisible }) {
              Icon(
                  imageVector = Icons.Default.Memory,
                  contentDescription = stringResource(R.string.editor_memory_usage),
                  tint = MaterialTheme.colorScheme.onSurface,
              )
            }

            var overflowExpanded by remember { mutableStateOf(false) }
            Box {
              IconButton(onClick = { overflowExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.editor_actions),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
              }

              DropdownMenu(
                  expanded = overflowExpanded,
                  onDismissRequest = { overflowExpanded = false },
                  offset = DpOffset(0.dp, 4.dp),
              ) {
                items.filter { it.icon == null }.forEach { item ->
                  DropdownMenuItem(
                      text = {
                        Text(
                            text = item.title?.toString().orEmpty(),
                            fontSize = 13.sp,
                            color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                      },
                      enabled = item.isEnabled,
                      onClick = {
                        overflowExpanded = false
                        activity.toolbarMenu.performIdentifierAction(item.itemId, 0)
                      },
                  )
                }
              }
            }
          },
          editorTabs = {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.memoryVisible) EditorMemoryChart(state.memories)
            if (state.tabs.visible) state.tabs.Content()
          },
          editorContent = {
            state.editorContainer.editors.forEach { editor ->
              androidx.compose.runtime.key(editor) {
                val selected = state.surface == 0 && state.editorContainer.selected === editor
                AndroidView(
                    factory = { editor },
                    modifier = if (selected) Modifier.fillMaxSize() else Modifier.size(0.dp),
                    update = { it.visibility = if (selected) android.view.View.VISIBLE else android.view.View.GONE },
                )
              }
            }
            when (state.surface) {
              0 -> Unit
              1 -> RegisteredEditorPage(state.documents, Modifier.fillMaxSize(), documentStates)
              else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                TextButton(onClick = { scope.launch { sidebarDrawerState.open() } }) {
                  Text(stringResource(R.string.msg_file_tree))
                }
                TextButton(onClick = { scope.launch { bottomDrawerState.animateTo(BottomDrawerValue.PEEK) } }) {
                  Text(stringResource(R.string.build_output))
                }
              }
            }
          },
          // --------------------------------------------------------------------
          // 底部常驻条（状态文本、光标指示、构建进度条、符号键盘面板、气泡绑定）
          // --------------------------------------------------------------------
          bottomBar = {
            Surface(tonalElevation = 2.dp) {
              Column(Modifier.navigationBarsPadding()) {
                // 状态栏与把手行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onboardingBind(guideTargets[1]),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                  // 构建状态文本（点击直接展开底栏至 PEEK 档位）
                  TextButton(
                      onClick = {
                        scope.launch {
                          val next = if (bottomDrawerState.isExpanded) BottomDrawerValue.HIDDEN else BottomDrawerValue.PEEK
                          bottomDrawerState.animateTo(next)
                        }
                      },
                      modifier = Modifier.weight(1f),
                  ) {
                    Text(
                        text = state.bottomSheet.status.ifBlank { stringResource(R.string.build_output) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                  }

                  // 光标当前行列指示器
                  Text(
                      text = state.cursor,
                      modifier = Modifier.padding(horizontal = 8.dp),
                      style = MaterialTheme.typography.labelSmall,
                      fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                  )

                  // 抽屉展开折叠小箭头（绑定气泡引导 target 0）
                  IconButton(
                      modifier = Modifier.onboardingBind(guideTargets[0]),
                      onClick = {
                        scope.launch {
                          val next = if (bottomDrawerState.isExpanded) BottomDrawerValue.HIDDEN else BottomDrawerValue.PEEK
                          bottomDrawerState.animateTo(next)
                        }
                      },
                  ) {
                    Icon(
                        imageVector = if (bottomDrawerState.isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.ExpandLess,
                        contentDescription = stringResource(R.string.build_output),
                    )
                  }
                }

                // 符号输入栏与构建动作进度展示
                if (state.headerVisible && !SymbolInputVisibilityManager.previewHidden) {
                  // 构建进度条
                  if (state.bottomSheet.headerPage == 1) {
                    EditorBottomAction(state.bottomSheet.actionText, state.bottomSheet.actionProgress)
                  }

                  // 符号输入键盘面板（绑定气泡引导 target 2）
                  SymbolInputPanel(
                      if (state.surface == 0) state.currentEditor?.editor else null,
                      onOpenManager = { activity.startActivity(Intent(activity, SymbolManagerActivity::class.java)) },
                      modifier = Modifier
                          .onboardingBind(guideTargets[2])
                          .onSizeChanged { state.symbolHeight = it.height },
                  )
                }
              }
            }
          },
          // --------------------------------------------------------------------
          // 抽屉顶部扩展业务操作（清空控制台与分享导出）
          // --------------------------------------------------------------------
          bottomActions = {
            IconButton(
                onClick = state.bottomSheet::clearSelectedOutput,
                modifier = Modifier.size(28.dp),
            ) {
              Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = stringResource(R.string.editor_clear_output),
                  modifier = Modifier.size(16.dp),
              )
            }

            IconButton(
                enabled = !sharing,
                onClick = {
                  val snapshot = state.bottomSheet.selectedOutputSnapshot() ?: return@IconButton
                  val (filename, text) = snapshot
                  scope.launch {
                    sharing = true
                    try {
                      val file = withContext(Dispatchers.IO) {
                        java.io.File(activity.filesDir, "$filename.txt").apply { writeText(text) }
                      }
                      com.itsaky.androidide.utils.IntentUtils.shareFile(activity, file, "text/plain")
                    } catch (_: Exception) {
                      activity.showEditorMessage(
                          activity.getString(R.string.editor_share_output),
                          activity.getString(R.string.msg_output_text_extraction_failed),
                      )
                    } finally {
                      sharing = false
                    }
                  }
                },
                modifier = Modifier.size(28.dp),
            ) {
              Icon(
                  imageVector = Icons.Default.Share,
                  contentDescription = stringResource(R.string.editor_share_output),
                  modifier = Modifier.size(16.dp),
              )
            }
            if (sharing) {
              CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
          },
      )

      // 渲染引导气泡层
      EditorOnboarding(targets = guideTargets)

      // 挂载弹窗句柄
      state.dialog?.invoke()
    }
  }
}

@Composable
internal fun ActionToolbarItem(
    item: MenuItem,
    menu: MenuBuilder,
) {
  var subMenuExpanded by remember { mutableStateOf(false) }

  Box {
    IconButton(
        enabled = item.isEnabled,
        onClick = {
          if (item.hasSubMenu()) {
            subMenuExpanded = true
          } else {
            menu.performIdentifierAction(item.itemId, 0)
          }
        },
        modifier = Modifier.size(36.dp),
    ) {
      val tint = if (item.isEnabled) {
        MaterialTheme.colorScheme.onSurface
      } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
      }
      ActionIcon(drawable = item.icon!!, description = item.title?.toString(), tint = tint)
    }

    if (item.hasSubMenu()) {
      DropdownMenu(
          expanded = subMenuExpanded,
          onDismissRequest = { subMenuExpanded = false },
          offset = DpOffset(0.dp, 4.dp),
      ) {
        val subMenu = item.subMenu!!
        (0 until subMenu.size()).map(subMenu::getItem).filter { it.isVisible }.forEach { subItem ->
          DropdownMenuItem(
              text = {
                Text(
                    text = subItem.title?.toString().orEmpty(),
                    fontSize = 13.sp,
                    color = if (subItem.isEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
              },
              enabled = subItem.isEnabled,
              onClick = {
                subMenuExpanded = false
                subMenu.performIdentifierAction(subItem.itemId, 0)
              },
          )
        }
      }
    }
  }
}

@Composable
internal fun ActionIcon(
    drawable: Drawable,
    description: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
  val bitmap = remember(drawable) {
    drawable.toBitmap(24, 24).asImageBitmap()
  }
  Icon(
      bitmap = bitmap,
      contentDescription = description,
      modifier = modifier.size(20.dp),
      tint = tint,
  )
}