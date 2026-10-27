package com.itsaky.androidide.ui.screen.editor.sidebar

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import com.itsaky.androidide.activities.editor.ui.state.EditorPage

@Composable
fun SidebarFragmentLifecycleController(
    drawerState: FullscreenDrawerState,
    activity: FragmentActivity,
    targetTagPrefix: String = "sidebar_drawer_frag_",
) {
  val currentActivity by rememberUpdatedState(activity)
  val currentTagPrefix by rememberUpdatedState(targetTagPrefix)

  // 展开预热：只要正在开，立刻升至 RESUMED；动画完全归零才降至 CREATED
  val shouldBeActive = drawerState.targetValue == FullscreenDrawerValue.OPEN || drawerState.progressFraction > 0.01f
  val targetLifecycle = if (shouldBeActive) Lifecycle.State.RESUMED else Lifecycle.State.CREATED

  DisposableEffect(targetLifecycle) {
    val fm = currentActivity.supportFragmentManager
    if (!fm.isStateSaved) {
      applySidebarLifecycle(fm, currentTagPrefix, targetLifecycle)
    }

    onDispose {
      val disposeFm = currentActivity.supportFragmentManager
      if (!disposeFm.isStateSaved && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
        applySidebarLifecycle(disposeFm, currentTagPrefix, Lifecycle.State.RESUMED)
      }
    }
  }
}

private fun applySidebarLifecycle(
    fm: FragmentManager,
    tagPrefix: String,
    targetState: Lifecycle.State,
) {
  val fragments = fm.fragments
  if (fragments.isEmpty()) return

  var tx: FragmentTransaction? = null
  for (frag in fragments) {
    if (frag != null && frag.isAdded && !frag.isDetached && !frag.tag.isNullOrBlank() && frag.tag!!.startsWith(tagPrefix)) {
      if (frag.lifecycle.currentState != targetState) {
        if (tx == null) tx = fm.beginTransaction().setReorderingAllowed(true)
        tx.setMaxLifecycle(frag, targetState)
      }
    }
  }
  tx?.commitAllowingStateLoss()
}

/**
 * 带有 key(page.id) 隔离与单次事务 Factory 的 Fragment 容器宿主
 */
@Composable
fun SidebarFragmentBridge(
    activity: FragmentActivity,
    page: EditorPage.FragmentPage,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
  val tag = "sidebar_drawer_frag_${page.id}"
  val containerId = remember(page.id) { View.generateViewId() }

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
                    .commitAllowingStateLoss()
              } else {
                fm.beginTransaction()
                    .replace(id, frag, tag)
                    .commitAllowingStateLoss()
              }
            }
          }
        },
        update = { /* 留空，避免重组触发重复事务 */ },
        modifier = modifier.fillMaxSize(),
    )
  }
}