package com.catpuppyapp.puppygit.compose

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catpuppyapp.puppygit.play.pro.R
import com.catpuppyapp.puppygit.utils.doJobThenOffLoading
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshBox(
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,

    //这个变量一般设为真或假，若设为null，代表不使用这个值，这时lading圆圈会在执行任务时默认转几下，然后就消失
    //因为我之前弄了其他的loading弹窗或文字，所以一般不需要这个东西指示是否loading，只要下拉刷新的功能就行了
//    isRefreshing: Boolean? = null,


    modifier: Modifier = Modifier,
    content: @Composable ()->Unit,
) {
    val state = rememberPullToRefreshState()

//    val isRefreshRawValue = remember(isRefreshing) { isRefreshing }
    val isRefreshing = remember { mutableStateOf(false) }


    //如果设为null，代表不依赖此组件显示loading，但好歹让圆圈转一下，意思意思，不然一拉就弹回去了，感觉很奇怪
    val onRefresh = {
        doJobThenOffLoading {
            isRefreshing.value = true
            delay(500)
            isRefreshing.value = false
        }

        onRefresh()
    }


    PullToRefreshBox(
        isRefreshing = isRefreshing.value,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            MyCustomIndicator(
                state = state,
                isRefreshing = isRefreshing.value,
                modifier = Modifier.align(Alignment.TopCenter).padding(contentPadding)
            )
        }
    ) {
        content()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyCustomIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.pullToRefreshIndicator(
            state = state,
            isRefreshing = isRefreshing,
            containerColor = PullToRefreshDefaults.containerColor,
        ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isRefreshing,
            animationSpec = tween(),
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp)
        ) { refreshing ->
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(2.dp)
                    ,

                    // line width
                    strokeWidth = 3.dp,
                )
            } else {
                val distanceFraction = { state.distanceFraction.coerceIn(0f, 1f) }
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                    modifier = Modifier
                        .graphicsLayer {
                            val progress = distanceFraction()
                            this.alpha = progress
                            this.scaleX = progress
                            this.scaleY = progress
                        }
                )
            }
        }
    }
}
