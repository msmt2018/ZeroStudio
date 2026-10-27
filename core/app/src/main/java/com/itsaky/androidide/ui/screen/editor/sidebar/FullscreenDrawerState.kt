package com.itsaky.androidide.ui.screen.editor.sidebar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class FullscreenDrawerValue {
  CLOSED,
  OPEN,
}

@Stable
class FullscreenDrawerState(
    initialValue: FullscreenDrawerValue = FullscreenDrawerValue.CLOSED,
    val animationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    ),
) {
  internal val animatable = Animatable(if (initialValue == FullscreenDrawerValue.OPEN) 1f else 0f)

  var currentValue by mutableStateOf(initialValue)
    private set

  var targetValue by mutableStateOf(initialValue)
    private set

  val progressFraction: Float
    get() = animatable.value

  val isOpen: Boolean
    get() = currentValue == FullscreenDrawerValue.OPEN || animatable.value > 0.005f

  internal suspend fun snapToFraction(fraction: Float) {
    animatable.snapTo(fraction.coerceIn(0f, 1f))
  }

  suspend fun open() {
    targetValue = FullscreenDrawerValue.OPEN
    currentValue = FullscreenDrawerValue.OPEN
    animatable.animateTo(1f, animationSpec)
  }

  suspend fun close() {
    targetValue = FullscreenDrawerValue.CLOSED
    animatable.animateTo(0f, animationSpec)
    currentValue = FullscreenDrawerValue.CLOSED
  }

  suspend fun animateTo(target: FullscreenDrawerValue) {
    if (target == FullscreenDrawerValue.OPEN) open() else close()
  }
}

@Composable
fun rememberFullscreenDrawerState(
    initialValue: FullscreenDrawerValue = FullscreenDrawerValue.CLOSED,
): FullscreenDrawerState {
  return remember { FullscreenDrawerState(initialValue) }
}

/**
 * 线性双向全屏拖拽修饰符：使用 Conflated 管道在 120Hz 下平滑更新，松手时计算初速度吸附
 */
fun Modifier.fullscreenDrawerDraggable(
    state: FullscreenDrawerState,
    scope: CoroutineScope,
    totalWidthPx: Float,
): Modifier = pointerInput(totalWidthPx) {
  val velocityTracker = VelocityTracker()
  val dragChannel = Channel<Float>(Channel.CONFLATED)

  val consumerJob = scope.launch {
    dragChannel.receiveAsFlow().collect { fraction ->
      state.snapToFraction(fraction)
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

        val deltaX = dragChange.positionChange().x
        if (deltaX != 0f && totalWidthPx > 0f) {
          dragChange.consume()
          velocityTracker.addPosition(dragChange.uptimeMillis, dragChange.position)

          val deltaFraction = deltaX / totalWidthPx
          currentFraction = (currentFraction + deltaFraction).coerceIn(0f, 1f)
          dragChannel.trySend(currentFraction)
        }

        if (event.changes.all { it.changedToUp() }) break
      }

      val velocityX = velocityTracker.calculateVelocity().x
      val endFraction = currentFraction

      // 仅有两个动作的磁吸策略：根据速度和过半判定
      val target = when {
        velocityX > 800f -> FullscreenDrawerValue.OPEN
        velocityX < -800f -> FullscreenDrawerValue.CLOSED
        endFraction > 0.40f -> FullscreenDrawerValue.OPEN
        else -> FullscreenDrawerValue.CLOSED
      }

      scope.launch { state.animateTo(target) }
    }
  } finally {
    consumerJob.cancel()
    dragChannel.close()
  }
}