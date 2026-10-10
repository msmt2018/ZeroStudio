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
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.androidide.theme.AndroidIDETheme
import com.itsaky.androidide.eventbus.events.preferences.PreferenceChangeEvent
import com.itsaky.androidide.tasks.cancelIfActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import com.itsaky.androidide.app.BaseComposeIDEActivity
/**
 * 纯粹遵循 Google 官方现代标准的 Jetpack Compose + Material 3 基准 Activity。
 *
 * @author android_zero
 */
abstract class IDEActivity : BaseComposeIDEActivity() {

  /** 全局 Application 单例快捷访问 */
  val app: IDEApplication
    get() = application as IDEApplication

  /** 后台非 UI 任务协程作用域，将在 Activity 销毁时自动取消 */
  protected val activityScope = CoroutineScope(Dispatchers.Default)

  /** 是否自动监听 EventBus 事件（例如主题与设置变动） */
  open val subscribeToEvents: Boolean = true

  /** 是否开启官方标准边缘沉浸式（Edge-to-Edge） */
  open val enableEdgeToEdgeScreen: Boolean = true

  override fun onCreate(savedInstanceState: Bundle?) {
    //采用官方标准 Edge-to-Edge 沉浸式 API
    if (enableEdgeToEdgeScreen) {
      enableEdgeToEdge()
    }

    super.onCreate(savedInstanceState)

    //  Jetpack Compose 根视图装载
    setContent {
      AndroidIDETheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
          ComposeContent()
        }
      }
    }
  }

  /**
   * 子类唯一的 UI 实现入口。
   * 完全遵循声明式 UI 规范，编写纯粹的 Jetpack Compose 与 Material 3 界面。
   */
  @Composable
  protected abstract fun ComposeContent()

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
    // 销毁时释放该 Activity 的后台任务，防止内存泄漏
    activityScope.cancelIfActive("Activity is being destroyed")
  }

  /**
   * 全局偏好设置变动监听。
   * 当用户在主题设置中切换了日夜模式或静态主题包时，平滑更新页面状态。
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  open fun onPreferenceChanged(event: PreferenceChangeEvent) {
    when (event.key) {
      // 日间/夜间模式变动（由 AppCompatDelegate 统一调度）
      KEY_UI_MODE -> Unit
      // 切换了调色盘/应用主题，安全重建页面以应用资源刷新
      KEY_SELECTED_THEME -> recreate()
    }
  }

  companion object {
    private const val KEY_UI_MODE = "idepref_general_uiMode"
    private const val KEY_SELECTED_THEME = "idpref_general_theme"
  }
}