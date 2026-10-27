package com.itsaky.androidide.ui.screen.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.itsaky.androidide.activities.editor.ui.state.EditorPageRegistry
import com.itsaky.androidide.ui.screen.editor.bottomsheet.BottomDrawerSheetState
import com.itsaky.androidide.ui.screen.editor.bottomsheet.BottomDrawerValue
import com.itsaky.androidide.ui.screen.editor.bottomsheet.M3ResponsiveBottomDrawerLayout
import com.itsaky.androidide.ui.screen.editor.sidebar.FullscreenDrawerState
import com.itsaky.androidide.ui.screen.editor.sidebar.FullscreenNavigation3Drawer
import com.itsaky.androidide.ui.screen.editor.sidebar.SidebarBarPosition
import kotlinx.coroutines.launch

/**
 * 编辑器核心工作区框架：
 * 1. 挂载 100% 全屏态左侧抽屉（Navigation 3 架构，四向极窄导航 Bar，去除各子页面伪标题栏）；
 * 2. 挂载 M3 三分屏底栏抽屉（30% Peek / 60% Half / 80% Full，Animatable 单一事实来源驱动）；
 * 3. 严格分发系统级返回键拦截顺序：底栏展开 -> 侧栏打开 -> 退出确认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorWorkspaceScreen(
    sidebarDrawerState: FullscreenDrawerState,
    bottomDrawerState: BottomDrawerSheetState,
    sidebarRegistry: EditorPageRegistry,
    bottomPages: EditorPageRegistry,
    activity: FragmentActivity,
    sidebarPosition: SidebarBarPosition = SidebarBarPosition.LEFT,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    editorTabs: @Composable () -> Unit = {},
    editorContent: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    bottomActions: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()

  // 系统级返回手势分发链（严防死锁）
  BackHandler(enabled = bottomDrawerState.isExpanded) {
    scope.launch { bottomDrawerState.animateTo(BottomDrawerValue.HIDDEN) }
  }

  BackHandler(enabled = !bottomDrawerState.isExpanded && sidebarDrawerState.isOpen) {
    scope.launch { sidebarDrawerState.close() }
  }

  Box(modifier = modifier.fillMaxSize()) {
    // 2. 底层架构：三分屏自适应抽屉（包裹主编辑器界面，承载 Console 与日志）
    M3ResponsiveBottomDrawerLayout(
        drawerState = bottomDrawerState,
        pages = bottomPages,
        activity = activity,
        topActions = bottomActions,
    ) {
      Scaffold(
          modifier = Modifier
              .fillMaxSize()
              .imePadding(),
          topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
              Row(
                  modifier = Modifier
                      .fillMaxSize()
                      .padding(horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                // 打开左侧全屏抽屉
                IconButton(
                    onClick = { scope.launch { sidebarDrawerState.open() } },
                    modifier = Modifier.size(32.dp),
                ) {
                  Icon(
                      imageVector = Icons.Default.Menu,
                      contentDescription = "Open Sidebar",
                      modifier = Modifier.size(18.dp),
                      tint = MaterialTheme.colorScheme.onSurface,
                  )
                }
                Spacer(Modifier.width(4.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                  toolbarActions()
                }
              }
            }
          },
          bottomBar = bottomBar,
      ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
          editorTabs()
          Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            editorContent()
          }
        }
      }
    }

    // 顶层架构：100% 全屏态左侧抽屉（尺寸锐减 60% 的四向 TabBar，去伪顶栏，无外层挤压）
    FullscreenNavigation3Drawer(
        drawerState = sidebarDrawerState,
        sidebarRegistry = sidebarRegistry,
        activity = activity,
        barPosition = sidebarPosition,
    )
  }
}