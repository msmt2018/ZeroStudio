package com.itsaky.androidide.ui.screen.editor.sidebar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.actions.ComposeSidebarActionItem
import com.itsaky.androidide.activities.editor.ui.screen.ActionIcon
import com.itsaky.androidide.activities.editor.ui.state.EditorPage
import com.itsaky.androidide.activities.editor.ui.state.EditorPageRegistry
import kotlinx.coroutines.launch

/**
 * 严格按照 Navigation 3 规范驱动的 100% 全屏无顶栏侧滑栏抽屉
 */
@Composable
fun FullscreenNavigation3Drawer(
    drawerState: FullscreenDrawerState,
    sidebarRegistry: EditorPageRegistry,
    activity: FragmentActivity,
    barPosition: SidebarBarPosition = SidebarBarPosition.LEFT,
    modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current

  // Navigation 3 核心状态：用单向回退栈驱动当前活跃页面
  val nav3BackStack = remember {
    mutableStateListOf<String>().apply {
      sidebarRegistry.selectedId?.let { add(it) } ?: run {
        sidebarRegistry.pages.firstOrNull()?.let { add(it.id) }
      }
    }
  }

  val currentActivePageId = nav3BackStack.lastOrNull() ?: sidebarRegistry.selectedId

  // 挂载局部 Fragment 生命周期冷冻控制器
  SidebarFragmentLifecycleController(
      drawerState = drawerState,
      activity = activity,
      targetTagPrefix = "sidebar_drawer_frag_",
  )

  BackHandler(enabled = drawerState.isOpen) {
    if (nav3BackStack.size > 1) {
      nav3BackStack.removeLast() // Navigation 3 回退上一标签
    } else {
      scope.launch { drawerState.close() }
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val totalWidthPx = with(density) { maxWidth.toPx() }

    AnimatedVisibility(
        visible = drawerState.isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
      Surface(
          modifier = Modifier
              .fillMaxSize()
              .fullscreenDrawerDraggable(drawerState, scope, totalWidthPx)
              .statusBarsPadding()
              .navigationBarsPadding()
              .imePadding(),
          color = MaterialTheme.colorScheme.surface,
      ) {
        val pageContent = @Composable {
          // 彻底去除固定标题栏控件，子页面获得 100% 纵向与横向全开可视区域
          Box(modifier = Modifier.fillMaxSize()) {
            sidebarRegistry.pages.find { it.id == currentActivePageId }?.let { page ->
              when (page) {
                is EditorPage.ScreenPage -> page.content()
                is EditorPage.FragmentPage -> {
                  SidebarFragmentBridge(
                      activity = activity,
                      page = page,
                      isActive = drawerState.isOpen,
                  )
                }
                is EditorPage.ActionPage -> Unit
              }
            }
          }
        }

        // 依据四向预制配置渲染削减 60% 尺寸的超紧凑 Tab Bar
        when (barPosition) {
          SidebarBarPosition.TOP -> {
            Column(modifier = Modifier.fillMaxSize()) {
              UltraCompactHorizontalBar(
                  pages = sidebarRegistry.pages,
                  activeId = currentActivePageId,
                  onSelect = { id ->
                    if (nav3BackStack.lastOrNull() != id) nav3BackStack.add(id)
                    sidebarRegistry.select(id)
                  },
                  onClose = { scope.launch { drawerState.close() } },
              )
              Box(modifier = Modifier.weight(1f).fillMaxWidth()) { pageContent() }
            }
          }
          SidebarBarPosition.BOTTOM -> {
            Column(modifier = Modifier.fillMaxSize()) {
              Box(modifier = Modifier.weight(1f).fillMaxWidth()) { pageContent() }
              UltraCompactHorizontalBar(
                  pages = sidebarRegistry.pages,
                  activeId = currentActivePageId,
                  onSelect = { id ->
                    if (nav3BackStack.lastOrNull() != id) nav3BackStack.add(id)
                    sidebarRegistry.select(id)
                  },
                  onClose = { scope.launch { drawerState.close() } },
              )
            }
          }
          SidebarBarPosition.LEFT -> {
            Row(modifier = Modifier.fillMaxSize()) {
              UltraCompactVerticalBar(
                  pages = sidebarRegistry.pages,
                  activeId = currentActivePageId,
                  onSelect = { id ->
                    if (nav3BackStack.lastOrNull() != id) nav3BackStack.add(id)
                    sidebarRegistry.select(id)
                  },
                  onClose = { scope.launch { drawerState.close() } },
              )
              Box(modifier = Modifier.weight(1f).fillMaxHeight()) { pageContent() }
            }
          }
          SidebarBarPosition.RIGHT -> {
            Row(modifier = Modifier.fillMaxSize()) {
              Box(modifier = Modifier.weight(1f).fillMaxHeight()) { pageContent() }
              UltraCompactVerticalBar(
                  pages = sidebarRegistry.pages,
                  activeId = currentActivePageId,
                  onSelect = { id ->
                    if (nav3BackStack.lastOrNull() != id) nav3BackStack.add(id)
                    sidebarRegistry.select(id)
                  },
                  onClose = { scope.launch { drawerState.close() } },
              )
            }
          }
        }
      }
    }
  }
}

/**
 * 宽度削减 60% 的超紧凑竖向 Bar (由原本 56dp 锐减到 22dp)
 */
@Composable
private fun UltraCompactVerticalBar(
    pages: List<EditorPage>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
  Surface(
      modifier = Modifier.width(22.dp).fillMaxHeight(),
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
      tonalElevation = 1.dp,
  ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Spacer(Modifier.height(4.dp))

      // 退出抽屉手势按钮
      IconButton(onClick = onClose, modifier = Modifier.size(18.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Close",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(4.dp))

      pages.forEach { page ->
        CompactTabItem(
            page = page,
            isSelected = page.id == activeId,
            onClick = { onSelect(page.id) },
        )
      }
    }
  }
}

/**
 * 高度削减 60% 的超紧凑横向 Bar (由原本 48dp 锐减到 20dp)
 */
@Composable
private fun UltraCompactHorizontalBar(
    pages: List<EditorPage>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth().height(20.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
      tonalElevation = 1.dp,
  ) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      IconButton(onClick = onClose, modifier = Modifier.size(18.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Close",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      pages.forEach { page ->
        CompactTabItem(
            page = page,
            isSelected = page.id == activeId,
            onClick = { onSelect(page.id) },
        )
      }
    }
  }
}

@Composable
private fun CompactTabItem(
    page: EditorPage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  val action = ActionsRegistry.getInstance().getActions(ActionItem.Location.EDITOR_SIDEBAR)[page.id]
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()

  val scale = if (isPressed) 0.82f else 1.0f

  val containerColor = if (isSelected) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    Color.Transparent
  }

  val contentTint = if (isSelected) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
  }

  Box(
      modifier = Modifier
          .size(18.dp)
          .scale(scale)
          .clip(RoundedCornerShape(4.dp))
          .background(containerColor)
          .clickable(
              interactionSource = interactionSource,
              indication = null,
              onClick = {
                if (action is ComposeSidebarActionItem && action.visuals.enablePressHaptic) {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onClick()
              },
          ),
      contentAlignment = Alignment.Center,
  ) {
    if (action is ComposeSidebarActionItem && action.visuals.imageVector != null) {
      Icon(
          imageVector = action.visuals.imageVector!!,
          contentDescription = page.title,
          tint = contentTint,
          modifier = Modifier.size(11.dp),
      )
    } else {
      action?.icon?.let { ActionIcon(it, page.title, tint = contentTint, modifier = Modifier.size(11.dp)) }
          ?: Icon(Icons.Default.Folder, page.title, tint = contentTint, modifier = Modifier.size(11.dp))
    }
  }
}