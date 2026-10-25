package com.itsaky.androidide.ui.screen.editor.screen

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.MenuItem
import android.zero.studio.widget.editor.symbolinput.SymbolInputPanel
import android.zero.studio.widget.editor.symbolinput.SymbolManagerActivity
import androidx.activity.compose.BackHandler
import androidx.appcompat.view.menu.MenuBuilder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.activities.editor.BaseEditorActivity
import com.itsaky.androidide.ui.screen.editor.state.EditorPage
import com.itsaky.androidide.ui.screen.editor.state.EditorPageRegistry
import com.itsaky.androidide.ui.screen.editor.state.EditorUiState
import com.itsaky.androidide.onboarding.OnboardingTarget
import com.itsaky.androidide.onboarding.onboardingBind
import com.itsaky.androidide.ui.SymbolInputVisibilityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑器核心工作区框架。
 * 抽屉 100% 满屏无挤压展示，子页面不再限制宽度。
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
    editorContent: @Composable () -> Unit,
    editorTabs: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    sidebarIcon: @Composable (EditorPage) -> Unit = {},
    bottomActions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current

  BackHandler(enabled = drawerState.isOpen) {
    scope.launch { drawerState.close() }
  }

  Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
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
      Column(modifier = Modifier.fillMaxSize().padding(insets)) {
        editorTabs()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          editorContent()
        }
      }
    }

    // 100% 满屏覆盖侧滑抽屉
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
          // 极简紧凑 Header
          Surface(
              modifier = Modifier.fillMaxWidth().height(34.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
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

          // Rail 栏 (40dp) + 100% 满屏页面内容容器
          Row(modifier = Modifier.fillMaxSize()) {
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
                    val action = ActionsRegistry.getInstance().getActions(ActionItem.Location.EDITOR_SIDEBAR)[page.id]
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    // 微交互动效：按压弹性回弹动画
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.88f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "sidebar_tab_scale",
                    )

                    val bgColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .scale(scale)
                            .padding(2.dp)
                            .background(bgColor, RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) {
                              if (action is com.itsaky.androidide.actions.ComposeSidebarActionItem && action.visuals.enablePressHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                              }
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

            // 满屏无挤压内容区
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
              RegisteredEditorPage(
                  registry = sidebar,
                  modifier = Modifier.fillMaxSize(),
              )
            }
          }
        }
      }
    }

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
                  selectedTabIndex = pages.indexOfFirst { it.id == bottomPages.selectedId }.coerceAtLeast(0),
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
              modifier = Modifier.fillMaxWidth().weight(1f),
          )
        }
      }
    }
  }
}

@Composable
fun RegisteredEditorPage(
    registry: EditorPageRegistry,
    modifier: Modifier = Modifier,
    states: androidx.compose.runtime.saveable.SaveableStateHolder = rememberEditorPageStates(registry),
) {
  Box(modifier) {
    registry.selectedPage?.let { page ->
      key(page.id) {
        states.SaveableStateProvider(page.id) {
          when (page) {
            is EditorPage.ActionPage -> Unit
            is EditorPage.ScreenPage -> page.content()
          }
        }
      }
    }
  }
}

@Composable
private fun ActionToolbarItem(
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
            val tint = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
                                color = if (subItem.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
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
private fun ActionIcon(
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