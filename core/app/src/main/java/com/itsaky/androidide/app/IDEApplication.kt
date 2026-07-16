/*
 * This file is part of AndroidIDE.
 *
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.itsaky.androidide.app

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.blankj.utilcode.util.ThrowableUtils.getFullStackTrace
import com.google.android.material.color.DynamicColors
import com.itsaky.androidide.BuildConfig
import com.itsaky.androidide.activities.CrashHandlerActivity
import com.itsaky.androidide.activities.editor.IDELogcatReader
import com.itsaky.androidide.buildinfo.BuildInfo
import com.itsaky.androidide.debugger.connection.DebugConnectionPreferences
import com.itsaky.androidide.debugger.connection.host.AppReadyAutoConnect
import com.itsaky.androidide.editor.schemes.IDEColorSchemeProvider
import com.itsaky.androidide.eventbus.events.preferences.PreferenceChangeEvent
import com.itsaky.androidide.events.AppEventsIndex
import com.itsaky.androidide.events.EditorEventsIndex
import com.itsaky.androidide.events.LspApiEventsIndex
import com.itsaky.androidide.events.LspJavaEventsIndex
import com.itsaky.androidide.events.LspKotlinEventsIndex
import com.itsaky.androidide.preferences.internal.DevOpsPreferences
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import com.itsaky.androidide.resources.localization.LocaleProvider
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE
import com.itsaky.androidide.ui.themes.IDETheme
import com.itsaky.androidide.ui.themes.IThemeManager
import com.itsaky.androidide.utils.RecyclableObjectPool
import com.itsaky.androidide.utils.flashError
import com.termux.app.TermuxApplication
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.lang.Thread.UncaughtExceptionHandler
import kotlin.system.exitProcess
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory

/**
 * Main Application class for AndroidIDE. Initializes global environment, tools, and event bus.
 *
 * @author android_zero
 */
class IDEApplication : TermuxApplication() {

  private var uncaughtExceptionHandler: UncaughtExceptionHandler? = null
  private var ideLogcatReader: IDELogcatReader? = null

  /**
   * 断点调试器自动 attach 协调器。启动后同时监听:
   *   - host app logcat "READY pkg=... jdwp=PORT" 信号 (AppReadySignalWatcher)
   *   - host app 反向连接 + HELLO 协议 (HostBridgeServer LocalServerSocket)
   * 任一触发都会尝试用默认连接方案 attach 到 host app。
   */
  @Volatile private var appReadyAutoConnect: AppReadyAutoConnect? = null

  init {
    RecyclableObjectPool.DEBUG = BuildConfig.DEBUG
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun onCreate() {
    val bootStart = System.currentTimeMillis()

    instance = this
    super.onCreate()

    applyPersistedLocale()

    // 启动 chatai 模块的 Koin 容器。
    // 原本是 me.rerere.rikkahub.RikkaHubRuntime.ensureKoinStarted(this),
    // 但 RikkaHubRuntime 已被移除, 而 chatai 模块的 Application 类
    // (RikkaHubApp) 没有被注册到 AndroidManifest (manifest 里只有 IDEApplication),
    // 所以原来这条链路下 Koin 根本不会启动, chatai 的 Compose UI
    // (RouteFragment -> RikkahubTheme -> rememberUserSettingsState) 第一次
    // 访问 Koin 就会抛 "KoinApplication has not been started"。
    // 把 startKoin 直接放在这里, 用 chatai 模块的 4 个 module 装配。
    startKoin {
      androidLogger()
      androidContext(this@IDEApplication)
      workManagerFactory()
      modules(appModule, viewModelModule, dataSourceModule, repositoryModule)
    }

    uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, th -> handleCrash(thread, th) }

    if (BuildConfig.DEBUG) {
      // LeakCanary 2.14 的 RootViewWatcher 在监听到 WebView 的 SelectPopup
      // 弹窗 (chromium 内部 Dialog.show) 时, 会尝试读取一个 bool 资源
      // (leak_canary_watcher_ignore_select_popup), 该资源在某些打包配置
      // 下会从 resource table 中丢失, 导致 Resources$NotFoundException 崩溃.
      // RootViewWatcher 是 LeakCanary 中误报率最高的 watcher (Dialog/Toast
      // 都会触发), 关闭它不影响 Activity/Fragment/ViewModel 泄漏检测.
      // LeakCanary 2.14 的 AppWatcher.Config 不再提供 watchRootViews 字段,
      // RootViewWatcher 会自动安装且无法通过 config 关闭。此处保留注释说明意图:
      // 若需禁用 RootViewWatcher, 应升级 LeakCanary 或通过反射操作内部开关。
      // leakcanary.AppWatcher.config =
      //     leakcanary.AppWatcher.config.copy(watchRootViews = false)

      if (DevOpsPreferences.dumpLogs) {
        startLogcatReader()
      }
    }

    EventBus.builder()
        .addIndex(AppEventsIndex())
        .addIndex(EditorEventsIndex())
        .addIndex(LspApiEventsIndex())
        .addIndex(LspJavaEventsIndex())
        .addIndex(LspKotlinEventsIndex())
        .installDefaultEventBus(true)

    EventBus.getDefault().register(this)

    AppCompatDelegate.setDefaultNightMode(GeneralPreferences.uiMode)

    if (IThemeManager.getInstance().getCurrentTheme() == IDETheme.MATERIAL_YOU) {
      DynamicColors.applyToActivitiesIfAvailable(this)
    }

    EditorColorScheme.setDefault(SchemeAndroidIDE.newInstance(null))

    GlobalScope.launch(Dispatchers.IO) {
      IDEColorSchemeProvider.init()
    }

    // 启动断点调试器的自动 attach 协调器 (后台 IO 线程):
    //   start() 会 spawn logcat 子进程 + bind abstract LocalServerSocket,
    //   不能在主线程做 (会触发 ANR)。
    //   宿主 app 启动后会反连这个 LocalServerSocket (HostAttachAgentBootstrap),
    //   或通过 logcat 发 "READY" 信号, 触发 IDE 自动 attach。
    GlobalScope.launch(Dispatchers.IO) {
      try {
        appReadyAutoConnect = AppReadyAutoConnect(
            settings = DebugConnectionPreferences.load(),
        ).also { it.start() }
      } catch (t: Throwable) {
        log.warn("Failed to start AppReadyAutoConnect: {}", t.message)
      }
    }

    log.info("IDEApplication onCreate completed in {} ms", System.currentTimeMillis() - bootStart)
  }

  /**
   * Reads the saved locale key and applies it globally to prevent the system from resetting the app
   * language to the device default on cold start.
   */
  private fun applyPersistedLocale() {
    val selectedLocaleKey = GeneralPreferences.selectedLocale
    val localeListCompat =
        selectedLocaleKey?.let { key ->
          LocaleProvider.getLocale(key)?.let { LocaleListCompat.create(it) }
        } ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(localeListCompat)
  }

  fun showChangelog() {
    val intent = Intent(Intent.ACTION_VIEW)
    var version = BuildInfo.VERSION_NAME_SIMPLE
    if (!version.startsWith('v')) {
      version = "v${version}"
    }
    intent.data = Uri.parse("${BuildInfo.REPO_URL}/releases/tag/${version}")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      startActivity(intent)
    } catch (th: Throwable) {
      log.error("Unable to start activity to show changelog", th)
      flashError("Unable to start activity")
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onPrefChanged(event: PreferenceChangeEvent) {
    val enabled = event.value as? Boolean?

    if (event.key == DevOpsPreferences.KEY_DEVOPTS_DEBUGGING_DUMPLOGS) {
      if (enabled == true) {
        startLogcatReader()
      } else {
        stopLogcatReader()
      }
    } else if (event.key == GeneralPreferences.UI_MODE) {
      val mode = GeneralPreferences.uiMode
      if (mode != AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.setDefaultNightMode(mode)
      }
    } else if (event.key == GeneralPreferences.SELECTED_LOCALE) {
      val selectedLocale = GeneralPreferences.selectedLocale
      val localeListCompat =
          selectedLocale?.let { key ->
            LocaleProvider.getLocale(key)?.let { LocaleListCompat.create(it) }
          } ?: LocaleListCompat.getEmptyLocaleList()

      AppCompatDelegate.setApplicationLocales(localeListCompat)
    }
  }

  private fun handleCrash(thread: Thread, th: Throwable) {
    writeException(th)

    try {
      val intent = Intent()
      intent.action = CrashHandlerActivity.REPORT_ACTION
      intent.putExtra(CrashHandlerActivity.TRACE_KEY, getFullStackTrace(th))
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      startActivity(intent)
      if (uncaughtExceptionHandler != null) {
        uncaughtExceptionHandler!!.uncaughtException(thread, th)
      }

      exitProcess(1)
    } catch (error: Throwable) {
      log.error("Unable to show crash handler activity", error)
    }
  }

  private fun startLogcatReader() {
    if (ideLogcatReader != null) {
      // already started
      return
    }

    log.info("Starting logcat reader...")
    ideLogcatReader = IDELogcatReader().also { it.start() }
  }

  private fun stopLogcatReader() {
    log.info("Stopping logcat reader...")
    ideLogcatReader?.stop()
    ideLogcatReader = null
  }

  override fun attachBaseContext(base: android.content.Context?) {
    if (base != null) {
      val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(base)
      val selectedLocaleKey = prefs.getString(GeneralPreferences.SELECTED_LOCALE, null)
      val localeListCompat =
          selectedLocaleKey?.let { key ->
            LocaleProvider.getLocale(key)?.let { LocaleListCompat.create(it) }
          } ?: LocaleListCompat.getEmptyLocaleList()
      AppCompatDelegate.setApplicationLocales(localeListCompat)
    }
    super.attachBaseContext(base)
  }

  companion object {
    private val log = LoggerFactory.getLogger(IDEApplication::class.java)

    @JvmStatic
    lateinit var instance: IDEApplication
      private set
  }
}
