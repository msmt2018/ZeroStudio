package com.itsaky.androidide.ui.screen.editor.bottomsheet

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import com.itsaky.androidide.activities.editor.ui.state.EditorPage
import com.itsaky.androidide.activities.editor.ui.state.EditorPageRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

// ============================================================================
// 1. 三分屏核心状态机（Animatable 单一事实来源驱动）
// ============================================================================

enum class BottomDrawerValue(val heightFraction: Float) {
  HIDDEN(0.00f),
  PEEK(0.30f),
  HALF(0.60f),
  FULL(0.80f),
}

@Stable
class BottomDrawerSheetState(
    initialValue: BottomDrawerValue = BottomDrawerValue.HIDDEN,
    val animationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    ),
) {
  internal val animatable = Animatable(initialValue.heightFraction)

  var currentValue by mutableStateOf(initialValue)
    private set

  var targetValue by mutableStateOf(initialValue)
    private set

  val progressFraction: Float
    get() = animatable.value

  val isExpanded: Boolean
    get() = currentValue != BottomDrawerValue.HIDDEN || animatable.value > 0.005f

  val isFull: Boolean
    get() = currentValue == BottomDrawerValue.FULL

  internal suspend fun snapToFraction(fraction: Float) {
    animatable.snapTo(fraction.coerceIn(-0.02f, 0.95f))
  }

  suspend fun animateTo(target: BottomDrawerValue) {
    targetValue = target
    // 展开预热
    if (target != BottomDrawerValue.HIDDEN) {
      currentValue = target
    }
    // 物理动效平滑收敛
    animatable.animateTo(
        targetValue = target.heightFraction,
        animationSpec = animationSpec,
    )
    // 收起滞后
    if (target == BottomDrawerValue.HIDDEN) {
      currentValue = target
    }
  }

  suspend fun snapTo(target: BottomDrawerValue) {
    targetValue = target
    currentValue = target
    animatable.snapTo(target.heightFraction)
  }
}

@Composable
fun rememberBottomDrawerSheetState(
    initialValue: BottomDrawerValue = BottomDrawerValue.HIDDEN,
): BottomDrawerSheetState {
  return remember { BottomDrawerSheetState(initialValue) }
}

fun Modifier.bottomDrawerDraggable(
    state: BottomDrawerSheetState,
    scope: CoroutineScope,
    totalHeightPx: Float,
): Modifier = pointerInput(totalHeightPx) {
  val velocityTracker = VelocityTracker()
  val dragChannel = Channel<Float>(Channel.CONFLATED)

  val consumerJob = scope.launch {
    dragChannel.receiveAsFlow().collect { targetFraction ->
      state.snapToFraction(targetFraction)
    }
  }

  try {
    awaitEachGesture {
      val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
      velocityTracker.resetTracking()
      velocityTracker.addPosition(down.uptimeMillis, down.position)

      var currentFraction = state.progressFraction
      val pointerId = down.id

      while (true) {
        val event = awaitPointerEvent()
        val dragChange = event.changes.firstOrNull { it.id == pointerId } ?: break
        if (dragChange.isConsumed) break

        val deltaY = dragChange.positionChange().y
        if (deltaY != 0f && totalHeightPx > 0f) {
          dragChange.consume()
          velocityTracker.addPosition(dragChange.uptimeMillis, dragChange.position)

          val deltaFraction = -deltaY / totalHeightPx
          val damping = when {
            currentFraction > BottomDrawerValue.FULL.heightFraction && deltaFraction > 0 -> 0.22f
            currentFraction < 0f && deltaFraction < 0 -> 0.22f
            else -> 1.0f
          }

          currentFraction = (currentFraction + deltaFraction * damping).coerceIn(-0.02f, 0.95f)
          dragChannel.trySend(currentFraction)
        }

        if (event.changes.all { it.changedToUp() }) break
      }

      val velocity = velocityTracker.calculateVelocity().y
      val endFraction = currentFraction

      val target = when {
        velocity < -1200f -> {
          when {
            endFraction < 0.20f -> BottomDrawerValue.PEEK
            endFraction < 0.50f -> BottomDrawerValue.HALF
            else -> BottomDrawerValue.FULL
          }
        }
        velocity > 1200f -> {
          when {
            endFraction > 0.65f -> BottomDrawerValue.HALF
            endFraction > 0.35f -> BottomDrawerValue.PEEK
            else -> BottomDrawerValue.HIDDEN
          }
        }
        else -> {
          BottomDrawerValue.entries.minByOrNull { abs(it.heightFraction - endFraction) }
              ?: BottomDrawerValue.HIDDEN
        }
      }

      scope.launch { state.animateTo(target) }
    }
  } finally {
    consumerJob.cancel()
    dragChannel.close()
  }
}

// ============================================================================
// 2. 响应式栅格系统与无限测量屏障隔离 (Measurement Barrier)
// ============================================================================

enum class DrawerWidthBreakpoint {
  COMPACT,   // < 600dp
  MEDIUM,    // 600dp ~ 840dp
  EXPANDED,  // >= 840dp
}

enum class DrawerHeightInterval {
  HIDDEN,    // 0%
  PEEK,      // 30% 露头态
  HALF,      // 60% 半开态
  FULL,      // 80% 全屏态
}

@Stable
data class DrawerGridSpec(
    val columns: Int,
    val horizontalMarginRatio: Float,
)

@Stable
data class DrawerWindowContext(
    val widthBreakpoint: DrawerWidthBreakpoint,
    val heightInterval: DrawerHeightInterval,
    val containerWidth: Dp,
    val containerHeight: Dp,
    val gridSpec: DrawerGridSpec,
)

val LocalDrawerWindowContext = compositionLocalOf<DrawerWindowContext> {
  error("LocalDrawerWindowContext 未被注入")
}

fun resolveDrawerWindowContext(
    containerWidth: Dp,
    containerHeight: Dp,
    heightFraction: Float,
): DrawerWindowContext {
  val widthBreakpoint = when {
    containerWidth < 600.dp -> DrawerWidthBreakpoint.COMPACT
    containerWidth < 840.dp -> DrawerWidthBreakpoint.MEDIUM
    else -> DrawerWidthBreakpoint.EXPANDED
  }

  val heightInterval = when {
    heightFraction <= 0.05f -> DrawerHeightInterval.HIDDEN
    heightFraction < 0.45f -> DrawerHeightInterval.PEEK
    heightFraction < 0.70f -> DrawerHeightInterval.HALF
    else -> DrawerHeightInterval.FULL
  }

  val gridSpec = when (widthBreakpoint) {
    DrawerWidthBreakpoint.COMPACT -> DrawerGridSpec(columns = 4, horizontalMarginRatio = 0.03f)
    DrawerWidthBreakpoint.MEDIUM -> DrawerGridSpec(columns = 8, horizontalMarginRatio = 0.04f)
    DrawerWidthBreakpoint.EXPANDED -> DrawerGridSpec(columns = 12, horizontalMarginRatio = 0.05f)
  }

  return DrawerWindowContext(
      widthBreakpoint = widthBreakpoint,
      heightInterval = heightInterval,
      containerWidth = containerWidth,
      containerHeight = containerHeight,
      gridSpec = gridSpec,
  )
}

/**
 * 具有测量屏障隔离的自适应栅格系统
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveDrawerGrid(
    modifier: Modifier = Modifier,
    fixedGutter: Dp = 12.dp, // 钉死物理像素槽间距
    content: @Composable AdaptiveGridScope.() -> Unit,
) {
  val drawerContext = LocalDrawerWindowContext.current
  val gridSpec = drawerContext.gridSpec

  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val totalWidth = maxWidth
    val margin = totalWidth * gridSpec.horizontalMarginRatio

    val scope = remember(gridSpec.columns, totalWidth, fixedGutter) {
      object : AdaptiveGridScope {
        override val totalColumns: Int = gridSpec.columns

        @Composable
        override fun GridItem(span: Int, modifier: Modifier, itemContent: @Composable () -> Unit) {
          val safeSpan = span.coerceIn(1, totalColumns)
          val totalGutterSpace = fixedGutter * (totalColumns - 1)
          val availableWidth = totalWidth - (margin * 2) - totalGutterSpace
          val columnWidth = availableWidth / totalColumns
          val itemWidth = (columnWidth * safeSpan) + (fixedGutter * (safeSpan - 1))

          // 核心治理：通过宽度截断与裁剪屏障，隔离内部可能产生的任何无限扩展测量
          Box(
              modifier = modifier
                  .width(itemWidth)
                  .clipToBounds(),
          ) {
            itemContent()
          }
        }
      }
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = margin),
        horizontalArrangement = Arrangement.spacedBy(fixedGutter),
        verticalArrangement = Arrangement.spacedBy(fixedGutter),
    ) {
      scope.content()
    }
  }
}

interface AdaptiveGridScope {
  val totalColumns: Int

  @Composable
  fun GridItem(
      span: Int,
      modifier: Modifier = Modifier,
      itemContent: @Composable () -> Unit,
  )
}

// ============================================================================
// 靶向生命周期冷冻控制器
// ============================================================================

@Composable
fun DrawerFragmentLifecycleController(
    drawerState: BottomDrawerSheetState,
    activity: FragmentActivity,
    containerId: Int = View.NO_ID,
    targetTagPrefix: String = "bottom_sheet_frag_",
) {
  val currentActivity by rememberUpdatedState(activity)
  val currentContainerId by rememberUpdatedState(containerId)
  val currentTagPrefix by rememberUpdatedState(targetTagPrefix)

  val isTargetVisible = drawerState.targetValue != BottomDrawerValue.HIDDEN
  val isCurrentVisible = drawerState.currentValue != BottomDrawerValue.HIDDEN
  val shouldBeActive = isTargetVisible || isCurrentVisible

  val targetLifecycle = if (shouldBeActive) {
    Lifecycle.State.RESUMED
  } else {
    Lifecycle.State.CREATED
  }

  DisposableEffect(targetLifecycle) {
    val fm = currentActivity.supportFragmentManager
    if (!fm.isStateSaved) {
      applyFragmentLifecycle(
          fragmentManager = fm,
          targetContainerId = currentContainerId,
          tagPrefix = currentTagPrefix,
          targetState = targetLifecycle,
      )
    }

    onDispose {
      val disposeFm = currentActivity.supportFragmentManager
      if (!disposeFm.isStateSaved && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
        applyFragmentLifecycle(
            fragmentManager = disposeFm,
            targetContainerId = currentContainerId,
            tagPrefix = currentTagPrefix,
            targetState = Lifecycle.State.RESUMED,
        )
      }
    }
  }
}

private fun applyFragmentLifecycle(
    fragmentManager: FragmentManager,
    targetContainerId: Int,
    tagPrefix: String,
    targetState: Lifecycle.State,
) {
  val fragments = fragmentManager.fragments
  if (fragments.isEmpty()) return

  var transaction: FragmentTransaction? = null
  for (frag in fragments) {
    if (frag == null || !frag.isAdded || frag.isDetached || frag.isRemoving) continue

    val isTarget = (targetContainerId != View.NO_ID && frag.id == targetContainerId) ||
        (!frag.tag.isNullOrBlank() && frag.tag!!.startsWith(tagPrefix))

    if (isTarget && frag.lifecycle.currentState != targetState) {
      if (transaction == null) {
        transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
      }
      transaction.setMaxLifecycle(frag, targetState)
    }
  }
  transaction?.commitAllowingStateLoss()
}

// ============================================================================
// 4. M3 响应式抽屉主布局
// ============================================================================

@Composable
fun M3ResponsiveBottomDrawerLayout(
    drawerState: BottomDrawerSheetState,
    pages: EditorPageRegistry,
    activity: FragmentActivity,
    modifier: Modifier = Modifier,
    topActions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current

  DrawerFragmentLifecycleController(
      drawerState = drawerState,
      activity = activity,
      targetTagPrefix = "bottom_sheet_frag_",
  )

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val totalHeightPx = with(density) { maxHeight.toPx() }
    val drawerContext = resolveDrawerWindowContext(
        containerWidth = maxWidth,
        containerHeight = maxHeight,
        heightFraction = drawerState.progressFraction,
    )

    // 主编辑区
    Box(modifier = Modifier.fillMaxSize()) {
      content()
    }

    // 副视口遮罩层 (Scrim) 优化：仅在明确进入 HALF 或 FULL 档位时淡入
    val shouldShowScrim = drawerState.targetValue == BottomDrawerValue.HALF ||
        drawerState.targetValue == BottomDrawerValue.FULL

    val scrimAlpha by animateFloatAsState(
        targetValue = if (shouldShowScrim) {
          if (drawerState.targetValue == BottomDrawerValue.FULL) 0.50f else 0.32f
        } else {
          0.00f
        },
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "scrim-fade",
    )

    if (scrimAlpha > 0.01f) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = scrimAlpha))
              .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = { scope.launch { drawerState.animateTo(BottomDrawerValue.PEEK) } },
              )
      )
    }

    val currentHeightFraction = drawerState.progressFraction
    if (currentHeightFraction > 0.005f) {
      val currentHeightDp = maxHeight * currentHeightFraction

      CompositionLocalProvider(LocalDrawerWindowContext provides drawerContext) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeightDp)
                .align(Alignment.BottomCenter)
                .bottomDrawerDraggable(drawerState, scope, totalHeightPx)
                .imePadding(),
            shape = RoundedCornerShape(
                topStart = (maxWidth * 0.04f).coerceIn(14.dp, 28.dp),
                topEnd = (maxWidth * 0.04f).coerceIn(14.dp, 28.dp),
            ),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            ResponsiveDrawerHandle(
                drawerState = drawerState,
                drawerContext = drawerContext,
                onToggleNext = {
                  val next = when (drawerState.currentValue) {
                    BottomDrawerValue.HIDDEN -> BottomDrawerValue.PEEK
                    BottomDrawerValue.PEEK -> BottomDrawerValue.HALF
                    BottomDrawerValue.HALF -> BottomDrawerValue.FULL
                    BottomDrawerValue.FULL -> BottomDrawerValue.PEEK
                  }
                  scope.launch { drawerState.animateTo(next) }
                },
                onClose = { scope.launch { drawerState.animateTo(BottomDrawerValue.HIDDEN) } },
                actions = topActions,
            )

            // 区分大屏与手机竖屏的展示架构
            val isWideScreen = drawerContext.widthBreakpoint != DrawerWidthBreakpoint.COMPACT
            val isFullOrHalf = drawerContext.heightInterval == DrawerHeightInterval.FULL ||
                drawerContext.heightInterval == DrawerHeightInterval.HALF

            if (isWideScreen && isFullOrHalf) {
              // -------------------------------------------------------------
              // M3 Canonical Supporting Pane 架构 (折叠屏/大平板双手舒适区排版)
              // -------------------------------------------------------------
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .weight(1f)
                      .navigationBarsPadding(),
              ) {
                // 左侧舒适区：页面切换导航面板 (Supporting Navigation Pane)
                Surface(
                    modifier = Modifier
                        .width((drawerContext.containerWidth * 0.24f).coerceIn(180.dp, 260.dp))
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                  Column(
                      modifier = Modifier
                          .fillMaxSize()
                          .padding(8.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Text(
                        text = "CONSOLE VIEWS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                    pages.pages.forEach { page ->
                      val selected = page.id == pages.selectedId
                      Surface(
                          selected = selected,
                          onClick = { pages.select(page.id) },
                          shape = RoundedCornerShape(10.dp),
                          color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                          modifier = Modifier.fillMaxWidth().height(36.dp),
                      ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                          Text(
                              text = page.title,
                              fontSize = 12.sp,
                              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                              color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis,
                          )
                        }
                      }
                    }
                  }
                }

                // 物理分割线
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))

                // 右侧主工作区：主控制台 / 活跃子视口
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds(),
                ) {
                  RenderSelectedPage(pages, activity, drawerState)
                }
              }
            } else {
              // -------------------------------------------------------------
              // 标准移动端紧凑架构 (顶部 ScrollableTabRow + 单列主视口)
              // -------------------------------------------------------------
              val pageList = pages.pages
              if (pageList.isNotEmpty()) {
                val selectedIndex = pageList.indexOfFirst { it.id == pages.selectedId }.coerceAtLeast(0)

                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    edgePadding = maxWidth * 0.02f,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
                ) {
                  pageList.forEachIndexed { index, page ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { pages.select(page.id) },
                        text = {
                          Text(
                              text = page.title,
                              fontSize = 11.sp,
                              fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis,
                          )
                        },
                    )
                  }
                }
              }

              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .weight(1f)
                      .navigationBarsPadding(),
              ) {
                RenderSelectedPage(pages, activity, drawerState)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RenderSelectedPage(
    pages: EditorPageRegistry,
    activity: FragmentActivity,
    drawerState: BottomDrawerSheetState,
) {
  pages.selectedPage?.let { activePage ->
    when (activePage) {
      is EditorPage.ScreenPage -> activePage.content()
      is EditorPage.FragmentPage -> {
        AndroidFragmentBridge(
            activity = activity,
            page = activePage,
            isActive = drawerState.currentValue != BottomDrawerValue.HIDDEN,
        )
      }
      is EditorPage.ActionPage -> Unit
    }
  }
}

@Composable
private fun ResponsiveDrawerHandle(
    drawerState: BottomDrawerSheetState,
    drawerContext: DrawerWindowContext,
    onToggleNext: () -> Unit,
    onClose: () -> Unit,
    actions: @Composable () -> Unit,
) {
  Row(
      modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = drawerContext.containerWidth * 0.03f, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clickable { onToggleNext() },
    ) {
      Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically
      ) {
        val stateText = when (drawerContext.heightInterval) {
          DrawerHeightInterval.PEEK -> "30% 露头态"
          DrawerHeightInterval.HALF -> "60% 半开态"
          DrawerHeightInterval.FULL -> "80% 全屏态"
          DrawerHeightInterval.HIDDEN -> "已折叠"
        }
        Text(
            text = stateText,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Icon(
            imageVector = if (drawerState.isFull) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
      }
    }

    Spacer(Modifier.weight(1f))

    Box(
        modifier = Modifier
            .width((drawerContext.containerWidth * 0.10f).coerceIn(32.dp, 64.dp))
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
    )

    Spacer(Modifier.weight(1f))

    actions()

    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
      Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
    }
  }
}

@Composable
private fun AndroidFragmentBridge(
    activity: FragmentActivity,
    page: EditorPage.FragmentPage,
    isActive: Boolean,
) {
  val tag = "bottom_sheet_frag_${page.id}"
  val containerId = remember(page.id) { View.generateViewId() }

  // 严格引入 key(page.id)，彻底消灭 Tab 切换复用旧 View 导致的视图不更新 Bug
  key(page.id) {
    AndroidView(
        factory = { ctx ->
          android.widget.FrameLayout(ctx).apply {
            id = containerId
            val fm = activity.supportFragmentManager
            if (!fm.isStateSaved) {
              var frag = fm.findFragmentByTag(tag)
              if (frag == null) {
                frag = fm.fragmentFactory.instantiate(page.fragmentClass.classLoader!!, page.fragmentClass.name)
                page.arguments?.let { frag.arguments = it }
                fm.beginTransaction()
                    .replace(id, frag, tag)
                    .setMaxLifecycle(frag, if (isActive) Lifecycle.State.RESUMED else Lifecycle.State.CREATED)
                    .commitNowAllowingStateLoss()
              } else {
                fm.beginTransaction()
                    .replace(id, frag, tag)
                    .commitNowAllowingStateLoss()
              }
            }
          }
        },
        update = {
          // 纯粹留空：Fragment 生命周期与内容由外部控制器和自身内部订阅精准响应
        },
        modifier = Modifier.fillMaxSize(),
    )
  }
}