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

package com.itsaky.androidide.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.androidide.theme.AndroidIDETheme
import com.itsaky.androidide.eventbus.events.preferences.PreferenceChangeEvent
import com.itsaky.androidide.tasks.cancelIfActive
import com.itsaky.androidide.ui.themes.IThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * 完全基于 [ComponentActivity] 开发的纯粹 Jetpack Compose 核心基类。
 *
 * @author android_zero
 */
abstract class BaseComposeIDEActivity : ComponentActivity() {

  companion object {
    private const val KEY_UI_MODE = "idepref_general_uiMode"
    private const val KEY_SELECTED_THEME = "idpref_general_theme"
  }

  /** 是否监听 EventBus 基础配置事件（如主题切换、全局配置变更等）。 */
  open val subscribeToEvents: Boolean = true

  /** 是否启用官方标准边缘沉浸式（Edge-to-Edge），默认启用。 */
  open var enableSystemBarTheming: Boolean = true

  /**
   * 用于执行非 UI 后台任务的结构化协程作用域。
   * 使用 [SupervisorJob] 保证任务异常隔离，在 Activity [onDestroy] 时统一取消。
   */
  val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate(savedInstanceState: Bundle?) {
    // 1. 同步主题配置到全局 ThemeManager
    IThemeManager.getInstance().applyTheme(this)

    // 2. 启用 Edge-to-Edge 全屏沉浸式
    if (enableSystemBarTheming) {
      enableEdgeToEdge()
    }

    super.onCreate(savedInstanceState)

    // 3. 布局设置前置同步钩子
    preSetContentLayout()

    // 4. 纯 Compose 根视图装载并注入主题
    setContent {
      AndroidIDETheme {
        ComposeContent()
      }
    }
  }

  override fun onStart() {
    super.onStart()
    if (subscribeToEvents && !EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
  }

  override fun onStop() {
    super.onStop()
    if (EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().unregister(this)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    // 销毁时清理未执行完毕的后台协程，防止持有 Activity 造成泄漏
    activityScope.cancelIfActive("Activity is being destroyed")
  }

  /**
   * 全局配置变动事件监听。
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  open fun onBasePreferenceChanged(event: PreferenceChangeEvent) {
    when (event.key) {
      KEY_UI_MODE -> Unit
      KEY_SELECTED_THEME -> recreateActivitySafe()
    }
  }

  /** 主题或关键资源变更时安全重建当前页面。 */
  protected fun recreateActivitySafe() {
    recreate()
  }

  /**
   * 在 [setContent] 之前执行的轻量级初始化钩子，子类可根据需要重写。
   */
  protected open fun preSetContentLayout() = Unit

  /**
   * 纯 Compose 页面实现入口，子类在此编写纯粹的声明式 UI。
   */
  @Composable
  protected abstract fun ComposeContent()
}