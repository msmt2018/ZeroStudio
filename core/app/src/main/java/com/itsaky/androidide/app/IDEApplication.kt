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
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.blankj.utilcode.util.ThrowableUtils.getFullStackTrace
import com.google.android.material.color.DynamicColors
import com.itsaky.androidide.BuildConfig
import com.itsaky.androidide.activities.CrashHandlerActivity
import com.itsaky.androidide.activities.editor.IDELogcatReader
import com.itsaky.androidide.buildinfo.BuildInfo
import com.itsaky.androidide.editor.schemes.IDEColorSchemeProvider
import com.itsaky.androidide.eventbus.events.preferences.PreferenceChangeEvent
import com.itsaky.androidide.events.AppEventsIndex
import com.itsaky.androidide.events.EditorEventsIndex
import com.itsaky.androidide.events.LspApiEventsIndex
import com.itsaky.androidide.events.LspJavaEventsIndex
import com.itsaky.androidide.events.LspKotlinEventsIndex
import com.itsaky.androidide.perf.PerfApplication
import com.itsaky.androidide.perf.export.ThresholdPreferences
import com.itsaky.androidide.perf.monitor.ColdStartTracker
import com.itsaky.androidide.perf.monitor.CrashHandler
import com.itsaky.androidide.perf.monitor.ForegroundTracker
import com.itsaky.androidide.perf.monitor.MonitorCoordinator
import com.itsaky.androidide.perf.tracer.PerfTracer
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
import me.rerere.rikkahub.RikkaHubRuntime
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.LoggerFactory

/**
 * Main Application class for AndroidIDE. Initializes global environment, tools, and event bus.
 *
 * @author android_zero
 */
class IDEApplication : TermuxApplication() {

  private var uncaughtExceptionHandler: UncaughtExceptionHandler? = null
  private var ideLogcatReader: IDELogcatReader? = null

  init {
    RecyclableObjectPool.DEBUG = BuildConfig.DEBUG
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun onCreate() {
    val bootStart = System.currentTimeMillis()

    // :perf 进程: 只跑 super.onCreate (BaseApplication + TermuxApplication 必要初始化),
    // 然后调 PerfApplication.init 启动 server / PhaseCollector, 再 return.
    // 完全跳过 IDE 特有的初始化 (Koin/EventBus/StrictMode/ColorScheme 等).
    // 让 Perf Console 在 :perf 进程极轻量, 不被主 application 的任何阻塞影响.
    if (isPerfProcess()) {
      super.onCreate()
      log.info("IDEApplication onCreate skipped (running in :perf process)")
      PerfApplication.init(this)
      return
    }

    instance = this
    PerfTracer.reportInstant("ide_on_create_begin")
    ColdStartTracker.markAppStart() // PR (advanced): 冷启动第一段
    PerfTracer.tryAttach(this)
    PerfTracer.trace("super_on_create") { super.onCreate() }
    PerfTracer.trace("init_koin") { RikkaHubRuntime.ensureKoinStarted(this) }

    PerfTracer.trace("apply_persisted_locale") { applyPersistedLocale() }

    PerfTracer.trace("set_uncaught_handler") {
      uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { thread, th -> handleCrash(thread, th) }
    }

    if (BuildConfig.DEBUG) {
      PerfTracer.trace("strict_mode_setup") {
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy()).penaltyLog().detectAll().build()
        )
      }
      if (DevOpsPreferences.dumpLogs) {
        PerfTracer.trace("start_logcat_reader") { startLogcatReader() }
      }
    }

    PerfTracer.trace("eventbus_add_indexes") {
      EventBus.builder()
          .addIndex(AppEventsIndex())
          .addIndex(EditorEventsIndex())
          .addIndex(LspApiEventsIndex())
          .addIndex(LspJavaEventsIndex())
          .addIndex(LspKotlinEventsIndex())
          .installDefaultEventBus(true)
    }

    PerfTracer.trace("eventbus_register") { EventBus.getDefault().register(this) }

    PerfTracer.trace("set_default_night_mode") {
      AppCompatDelegate.setDefaultNightMode(GeneralPreferences.uiMode)
    }

    if (IThemeManager.getInstance().getCurrentTheme() == IDETheme.MATERIAL_YOU) {
      PerfTracer.trace("dynamic_colors_apply") {
        DynamicColors.applyToActivitiesIfAvailable(this)
      }
    }

    PerfTracer.trace("editor_color_scheme_default") {
      EditorColorScheme.setDefault(SchemeAndroidIDE.newInstance(null))
    }

    PerfTracer.reportInstant("ide_color_scheme_provider_init_start")
    GlobalScope.launch(Dispatchers.IO) {
      PerfTracer.trace("ide_color_scheme_provider_init") { IDEColorSchemeProvider.init() }
    }

    PerfTracer.reportInstant("ide_on_create_end")
    ColdStartTracker.markAppReady() // PR (advanced): 冷启动第二段
    ColdStartTracker.registerFirstActivityTracker(this) // PR (advanced): 等首 Activity
    ForegroundTracker.install() // PR (advanced): 监听前后台切换
    // PR (advanced): 启动时从 DataStore 加载用户自定义的 phase 告警阈值
    ThresholdPreferences.loadIntoThresholds(this)
    PerfTracer.endBoot()
    MonitorCoordinator.start(this)
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
      // PR (advanced): 先 dump 现场到 cacheDir, 再走原来的 crash UI
      CrashHandler.dumpCrashContext(this, thread, th)

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
      PerfTracer.reportInstant("ide_attach_base_context")
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

    /**
     * 当前进程是否在 `:perf` 进程 (Perf Console 独立进程).
     *
     * 用 `endsWith(":perf")` 而不是 equals 是因为: `Process.myProcessName()` 在某些
     * OEM (e.g. 小米) 上可能返回 `com.itsaky.androidide:perf` 或 `xxx:perf`,
     * 末尾 `:perf` 是稳定后缀.
     */
    @JvmStatic
    private fun isPerfProcess(): Boolean {
      val processName =
          runCatching {
            // android.os.Process.myProcessName() requires API 28
            @Suppress("DEPRECATION")
            android.os.Process.myProcessName()
          }.getOrDefault("")
      return processName.endsWith(":perf")
    }

    @JvmStatic
    lateinit var instance: IDEApplication
      private set
  }
}
