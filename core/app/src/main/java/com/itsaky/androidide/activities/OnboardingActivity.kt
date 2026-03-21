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

package com.itsaky.androidide.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro2
import com.github.appintro.AppIntroPageTransformerType
import com.itsaky.androidide.R
import com.itsaky.androidide.R.string
import com.itsaky.androidide.app.configuration.IDEBuildConfigProvider
import com.itsaky.androidide.app.configuration.IJdkDistributionProvider
import com.itsaky.androidide.fragments.onboarding.DisclaimerFragment
import com.itsaky.androidide.fragments.onboarding.GreetingFragment
import com.itsaky.androidide.fragments.onboarding.IdeSetupConfigurationFragment
import com.itsaky.androidide.fragments.onboarding.OnboardingInfoFragment
import com.itsaky.androidide.fragments.onboarding.PermissionsFragment
import com.itsaky.androidide.models.JdkDistribution
import com.itsaky.androidide.preferences.internal.prefManager
import com.itsaky.androidide.tasks.launchAsyncWithProgress
import com.itsaky.androidide.ui.themes.IThemeManager
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.*
import com.itsaky.androidide.utils.isAtLeastV
import com.itsaky.androidide.utils.isSystemInDarkMode
import com.itsaky.androidide.utils.resolveAttr
import com.termux.shared.android.PackageUtils
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class OnboardingActivity : AppIntro2() {

  private val terminalActivityCallback = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()) {
    Log.d(TAG, "TerminalActivity: resultCode=${it.resultCode}")
    if (!isFinishing) {
      reloadJdkDistInfo {
        tryNavigateToMainIfSetupIsCompleted()
      }
    }
  }

  private val activityScope =
    CoroutineScope(Dispatchers.Main + CoroutineName("OnboardingActivity"))

  private var listJdkInstallationsJob: Job? = null

  companion object {

    private const val TAG = "OnboardingActivity"
    private const val KEY_ARCHCONFIG_WARNING_IS_SHOWN = "ide.archConfig.experimentalWarning.isShown"
  }

  @SuppressLint("SourceLockedOrientationActivity")
  override fun onCreate(savedInstanceState: Bundle?) {
    IThemeManager.getInstance().applyTheme(this)
    // 强制竖屏，避免引导页旋转导致重建问题
    try {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } catch (e: Exception) {
            // 某些设备或特定情况下锁定方向可能会抛出异常（如 Translucent Activity），忽略即可
        }

    super.onCreate(savedInstanceState)
    
    // 沉浸式状态栏设置
    WindowCompat.getInsetsController(this.window, this.window.decorView).apply {
        isAppearanceLightStatusBars = !isSystemInDarkMode()
        isAppearanceLightNavigationBars = !isSystemInDarkMode()
    }

    if (isAtLeastV()) {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            view.setBackgroundColor(resolveAttr(R.attr.colorSurface))
            insets
        }
    } else {
        @Suppress("DEPRECATION")
        window.statusBarColor = resolveAttr(R.attr.colorSurface)
    }

    if (tryNavigateToMainIfSetupIsCompleted()) {
      return
    }

    setSwipeLock(true)
    setTransformer(AppIntroPageTransformerType.Fade)
    setProgressIndicator()
    showStatusBar(true)
    isIndicatorEnabled = true
    isWizardMode = true

    // addSlide(GreetingFragment())

    if (!PackageUtils.isCurrentUserThePrimaryUser(this)) {
      val errorMessage = getString(string.bootstrap_error_not_primary_user_message,
        MarkdownUtils.getMarkdownCodeForString(TermuxConstants.TERMUX_PREFIX_DIR_PATH, false))
      addSlide(OnboardingInfoFragment.newInstance(
        getString(string.title_unsupported_user),
        errorMessage,
        R.drawable.ic_alert,
        ContextCompat.getColor(this, R.color.color_error)
      ))
      return
    }

    if (isInstalledOnSdCard()) {
      val errorMessage = getString(string.bootstrap_error_installed_on_portable_sd,
        MarkdownUtils.getMarkdownCodeForString(TermuxConstants.TERMUX_PREFIX_DIR_PATH, false))
      addSlide(OnboardingInfoFragment.newInstance(
        getString(string.title_install_location_error),
        errorMessage,
        R.drawable.ic_alert,
        ContextCompat.getColor(this, R.color.color_error)
      ))
      return
    }

    if (!checkDeviceSupported()) {
      return
    }
    
    // ===免责与隐私协议 ===
    addSlide(DisclaimerFragment.newInstance(this))

    // 如果权限未全部满足，则显示权限页
    if (!checkAllPermissionsGranted()) {
      addSlide(PermissionsFragment.newInstance(this))
    }

    if (!checkToolsIsInstalled()) {
      addSlide(IdeSetupConfigurationFragment.newInstance(this))
    }
  }

  override fun onResume() {
    super.onResume()
    reloadJdkDistInfo {
      tryNavigateToMainIfSetupIsCompleted()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    activityScope.cancel("Activity is being destroyed")
  }

  override fun onDonePressed(currentFragment: Fragment?) {

    if (!IDEBuildConfigProvider.getInstance().supportsCpuAbi()) {
      finishAffinity()
      return
    }

    if (!checkToolsIsInstalled() && currentFragment is IdeSetupConfigurationFragment) {
      val intent = Intent(this, TerminalActivity::class.java)
      if (currentFragment.isAutoInstall()) {
        intent.putExtra(TerminalActivity.EXTRA_ONBOARDING_RUN_IDESETUP, true)
        intent.putExtra(TerminalActivity.EXTRA_ONBOARDING_RUN_IDESETUP_ARGS,
          currentFragment.buildIdeSetupArguments())
      }
      terminalActivityCallback.launch(intent)
      return
    }

    tryNavigateToMainIfSetupIsCompleted()
  }


  /**
   * 严格检查所有权限
   */
  private fun checkAllPermissionsGranted(): Boolean {
    val context = this

    // 通知
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        return false
    }

    // 安装权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return false
        }
    }

    // 全文件管理
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!android.os.Environment.isExternalStorageManager()) {
            return false
        }
    }

    // 基础媒体/读写
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val mediaPerms = listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        if (mediaPerms.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            return false
        }
    } else {
        val legacyPerms = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (legacyPerms.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            return false
        }
    }

    return true
  }

  private fun checkToolsIsInstalled(): Boolean {
    return IJdkDistributionProvider.getInstance().installedDistributions.isNotEmpty()
        && Environment.ANDROID_HOME.exists()
  }

  private fun isSetupCompleted(): Boolean {
    return checkToolsIsInstalled()
        && checkAllPermissionsGranted()
  }

  private fun tryNavigateToMainIfSetupIsCompleted(): Boolean {
    if (isSetupCompleted()) {
      startActivity(Intent(this, MainActivity::class.java))
      finish()
      return true
    }

    return false
  }

  private inline fun reloadJdkDistInfo(crossinline distConsumer: (List<JdkDistribution>) -> Unit) {
    listJdkInstallationsJob?.cancel("Reloading JDK distributions")

    listJdkInstallationsJob = activityScope.launchAsyncWithProgress(Dispatchers.Default,
      configureFlashbar = { builder, _ ->
        builder.message(string.please_wait)
      }) { _, _ ->
      val distributionProvider = IJdkDistributionProvider.getInstance()
      distributionProvider.loadDistributions()
      withContext(Dispatchers.Main) {
        distConsumer(distributionProvider.installedDistributions)
      }
    }.also {
      it?.invokeOnCompletion {
        listJdkInstallationsJob = null
      }
    }
  }

  private fun isInstalledOnSdCard(): Boolean {
    // noinspection SdCardPath
    return PackageUtils.isAppInstalledOnExternalStorage(this) &&
        TermuxConstants.TERMUX_FILES_DIR_PATH != filesDir.absolutePath
      .replace("^/data/user/0/".toRegex(), "/data/data/")
  }

  private fun checkDeviceSupported(): Boolean {
    val configProvider = IDEBuildConfigProvider.getInstance()

    if (!configProvider.supportsCpuAbi()) {
      addSlide(OnboardingInfoFragment.newInstance(
        getString(string.title_unsupported_device),
        getString(
          string.msg_unsupported_device,
          configProvider.cpuArch.abi,
          configProvider.deviceArch.abi
        ),
        R.drawable.ic_alert,
        ContextCompat.getColor(this, R.color.color_error)
      ))
      return false
    }

    if (configProvider.cpuArch != configProvider.deviceArch) {
      if (!archConfigExperimentalWarningIsShown()) {
        addSlide(OnboardingInfoFragment.newInstance(
          getString(string.title_experiment_flavor),
          getString(string.msg_experimental_flavor,
            configProvider.cpuArch.abi,
            configProvider.deviceArch.abi
          ),
          R.drawable.ic_alert,
          ContextCompat.getColor(this, R.color.color_warning)
        ))
        prefManager.putBoolean(KEY_ARCHCONFIG_WARNING_IS_SHOWN, true)
      }
    }

    return true
  }

  private fun archConfigExperimentalWarningIsShown() =
    prefManager.getBoolean(KEY_ARCHCONFIG_WARNING_IS_SHOWN, false)
}