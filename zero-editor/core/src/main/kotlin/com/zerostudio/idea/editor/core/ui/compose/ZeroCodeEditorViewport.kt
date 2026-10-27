package com.zerostudio.idea.editor.ui.compose

import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.zerostudio.idea.editor.core.buffer.LargeTextPieceTree
import com.zerostudio.idea.editor.core.config.EditorPreferencesState
import com.zerostudio.idea.editor.ui.view.VirtualCodeEditorView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

/**
 * 三分屏视口离散枚举
 */
enum class ThreeScreenState(val fraction: Float) {
    HIDDEN(0.0f),
    PEEK(0.3f),
    HALF(0.6f),
    FULL(0.8f)
}

/**
 * 工业级混合自适应 IDE 编辑器核心视口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroCodeEditorViewport(
    state: ThreeScreenState,
    preferences: EditorPreferencesState,
    pieceTree: LargeTextPieceTree,
    onStateChangeRequested: (ThreeScreenState) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 展开动画启动瞬间即提早提升生命周期预热 View 树
    var viewLifecycleState by remember { mutableStateOf(Lifecycle.State.CREATED) }
    val animatedFraction = remember { Animatable(state.fraction) }

    LaunchedEffect(state) {
        if (state != ThreeScreenState.HIDDEN) {
            viewLifecycleState = Lifecycle.State.RESUMED // 提早拉起消灭白屏
        }
        animatedFraction.animateTo(
            targetValue = state.fraction,
            animationSpec = tween(durationMillis = 280)
        )
        if (state == ThreeScreenState.HIDDEN) {
            viewLifecycleState = Lifecycle.State.CREATED // 动画落地后再冷冻防蒸发
        }
    }

    // 弹性断点网格：外边距与物理间距根据视口动态配置
    val physicalGutter: Dp = remember(state) {
        when (state) {
            ThreeScreenState.PEEK -> 12.dp
            ThreeScreenState.HALF -> 16.dp
            ThreeScreenState.FULL -> 16.dp
            ThreeScreenState.HIDDEN -> 0.dp
        }
    }

    // 高频触控手势背压通道 (Channel.CONFLATED)，阻断 120Hz 下的排队雪崩
    val touchChannel = remember { Channel<Offset>(Channel.CONFLATED) }
    var editorViewRef by remember { mutableStateOf<VirtualCodeEditorView?>(null) }

    LaunchedEffect(touchChannel) {
        touchChannel.consumeAsFlow().collect { offset ->
            editorViewRef?.dispatchScrollOffset(offset.x, offset.y)
        }
    }

    if (animatedFraction.value > 0f) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(animatedFraction.value)
                .clipToBounds(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = physicalGutter)
                    // 使用 modern pointerInput 替代过时交互逻辑，拦截缩放与点击
                    .pointerInput(preferences.pinchToZoomEnabled) {
                        if (preferences.pinchToZoomEnabled) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (zoom != 1.0f) {
                                    preferences.updateZoomFactor(zoom)
                                }
                                touchChannel.trySend(pan)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                editorViewRef?.handleTap(offset.x, offset.y)
                            },
                            onLongPress = { offset ->
                                editorViewRef?.handleLongPress(offset.x, offset.y)
                            }
                        )
                    }
            ) {
                // 严禁全局重组：AndroidView 仅在底层参数发生变更时通过 update 局部同步
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VirtualCodeEditorView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            bindBufferEngine(pieceTree)
                            applyPreferences(preferences)
                            editorViewRef = this
                        }
                    },
                    update = { view ->
                        // 局部轻量状态推演，杜绝整个 View 树重建
                        view.applyFluencyMode(preferences.isFluencyModeActive(pieceTree.length()))
                        view.updateStylingProperties(
                            wrapWord = preferences.wordWrap,
                            antiWordBreak = preferences.antiWordBreaking,
                            lineSpacingMultiplier = preferences.lineSpacingMultiplier,
                            fontSize = preferences.scaledFontSizeSp
                        )
                    }
                )
            }
        }
    }
}