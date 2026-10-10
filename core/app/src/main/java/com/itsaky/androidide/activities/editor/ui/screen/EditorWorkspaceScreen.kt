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

package com.itsaky.androidide.activities.editor.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.activities.editor.ui.state.EditorPage
import com.itsaky.androidide.activities.editor.ui.state.EditorPageRegistry
import kotlinx.coroutines.launch

/**
 * 编辑器核心工作区框架。
 *
 * 核心升级点：
 * 1. 抽屉彻底改为 **100% 满屏自适应覆盖层**，杜绝传统抽屉在右侧留出的无用半透明白边，给文件树/Git 提供最大宽度。
 * 2. 抽屉内左侧 Rail 栏压缩 50% 宽度至 40.dp，顶部 Header 压缩 50% 高度至 32.dp。
 * 3. 抽屉关闭时不销毁状态，打开时全速平移并带 60fps 缓动动画。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorWorkspaceScreen(
    title: String,
    drawerState: DrawerState,
    bottomSheetState: SheetState,
    bottomSheetVisible: Boolean,
    onDismissBottomSheet: () -> Unit,
    sidebar: EditorPageRegistry,
    bottomPages: EditorPageRegistry,
    openDrawerDescription: String,
    closeDescription: String,
    fragmentPage: @Composable (EditorPage.FragmentPage) -> Unit,
    editorContent: @Composable () -> Unit,
    editorTabs: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    sidebarIcon: @Composable (EditorPage) -> Unit = {},
    bottomActions: @Composable RowScope.() -> Unit = {},
    bottomFragmentPage: @Composable (EditorPage.FragmentPage) -> Unit = fragmentPage,
    modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()

  // 拦截系统返回键：如果抽屉打开，则优先关闭抽屉
  BackHandler(enabled = drawerState.isOpen) {
    scope.launch { drawerState.close() }
  }

  Box(modifier = modifier.fillMaxSize()) {
    // 主页面层：包含编辑器本体与各类状态栏
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
          // 紧凑型顶部操作栏：去除占用空间的中间标题，让 Action 按钮紧贴汉堡菜单
          Surface(
              modifier = Modifier.fillMaxWidth().height(42.dp),
              color = MaterialTheme.colorScheme.surface,
              tonalElevation = 1.dp,
          ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                  onClick = { scope.launch { drawerState.open() } },
                  modifier = Modifier.size(36.dp),
              ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = openDrawerDescription,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
              }
              Spacer(Modifier.width(4.dp))
              // Actions 紧随其后靠左排列，横向填满
              Row(
                  modifier = Modifier.weight(1f),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Start,
              ) {
                toolbarActions()
              }
            }
          }
        },
        bottomBar = bottomBar,
    ) { insets ->
      Column(
          modifier = Modifier.fillMaxSize().padding(insets),
      ) {
        editorTabs()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          editorContent()
        }
      }
    }

    // 自定义 100% 满屏全覆盖侧滑抽屉（彻底代替限制宽度的 ModalDrawerSheet）
    AnimatedVisibility(
        visible = drawerState.isOpen,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(150)),
        modifier = Modifier.fillMaxSize(),
    ) {
      Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.surface,
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          // 顶部栏高度压缩 50%（32.dp）
          Surface(
              modifier = Modifier.fillMaxWidth().height(32.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                  onClick = { scope.launch { drawerState.close() } },
                  modifier = Modifier.size(28.dp),
              ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = closeDescription,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
              }
              Spacer(Modifier.width(6.dp))
              Text(
                  text = sidebar.selectedPage?.title.orEmpty(),
                  style = MaterialTheme.typography.titleSmall.copy(
                      fontSize = 12.sp,
                      fontWeight = FontWeight.SemiBold,
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                  color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }

          // 下方区域：左侧 Rail 栏（宽度减少 50% 至 40.dp） + 右侧 100% 满屏页面容器
          Row(modifier = Modifier.fillMaxSize()) {
            // 左侧极简 Rail 栏
            Surface(
                modifier = Modifier.width(40.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              Column(
                  modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                  horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Spacer(Modifier.height(4.dp))
                sidebar.pages.forEach { page ->
                  key(page.id) {
                    val isSelected = sidebar.selectedId == page.id
                    val bgColor = if (isSelected) {
                      MaterialTheme.colorScheme.secondaryContainer
                    } else {
                      Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .padding(2.dp)
                            .background(bgColor, RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                              sidebar.select(page.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                      sidebarIcon(page)
                    }
                    Spacer(Modifier.height(4.dp))
                  }
                }
              }
            }

            // 右侧内容容器：占满剩余所有宽度（不再挤压），提供极致的操作空间
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
              RegisteredEditorPage(
                  registry = sidebar,
                  fragmentPage = fragmentPage,
                  modifier = Modifier.fillMaxSize(),
              )
            }
          }
        }
      }
    }

    // 底部调试与控制台 Sheet 弹窗
    if (bottomSheetVisible) {
      ModalBottomSheet(
          sheetState = bottomSheetState,
          onDismissRequest = onDismissBottomSheet,
      ) {
        Column(modifier = Modifier.fillMaxHeight().imePadding()) {
          Row(modifier = Modifier.fillMaxWidth()) {
            bottomActions()
            IconButton(onClick = {
              scope.launch {
                bottomSheetState.hide()
                if (!bottomSheetState.isVisible) onDismissBottomSheet()
              }
            }) {
              Icon(Icons.Default.Close, closeDescription)
            }
          }
          val pages = bottomPages.pages
          if (pages.isNotEmpty()) {
            key(pages.map { it.id }) {
              ScrollableTabRow(
                  selectedTabIndex = pages.indexOfFirst { it.id == bottomPages.selectedId }
                      .coerceAtLeast(0),
              ) {
                pages.forEach { page ->
                  key(page.id) {
                    Tab(
                        selected = bottomPages.selectedId == page.id,
                        onClick = { bottomPages.select(page.id) },
                        text = { Text(page.title, maxLines = 1) },
                    )
                  }
                }
              }
            }
          }
          RegisteredEditorPage(
              registry = bottomPages,
              fragmentPage = bottomFragmentPage,
              modifier = Modifier.fillMaxWidth().weight(1f),
          )
        }
      }
    }
  }
}

/**
 * 多态页面宿主容器。
 */
@Composable
fun RegisteredEditorPage(
    registry: EditorPageRegistry,
    fragmentPage: @Composable (EditorPage.FragmentPage) -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    registry.selectedPage?.let { page ->
      key(page.id) {
        when (page) {
          is EditorPage.ActionPage -> Unit
          is EditorPage.ScreenPage -> page.content()
          is EditorPage.FragmentPage -> fragmentPage(page)
        }
      }
    }
  }
}