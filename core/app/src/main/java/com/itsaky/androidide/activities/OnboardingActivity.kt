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
import com.itsaky.androidide.fragments.onboarding.GreetingFragment
import com.itsaky.androidide.fragments.onboarding.OdSdkToolInstallFragment
import com.itsaky.androidide.fragments.onboarding.OnboardingInfoFragment
import com.itsaky.androidide.fragments.onboarding.PermissionsFragment
import com.itsaky.androidide.preferences.internal.prefManager
import com.itsaky.androidide.repository.sdkmanager.SdkChecker
import com.itsaky.androidide.ui.themes.IThemeManager
import com.itsaky.androidide.utils.*
import com.termux.shared.android.PackageUtils
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.termux.TermuxConstants

/**
 * 引导页与环境初始化 Activity
 *
 * @author Akash Yadav
 * @author android_zero
 */
class OnboardingActivity : AppIntro2() {

  companion object {
    private const val TAG = "OnboardingActivity"
    private const val KEY_ARCHCONFIG_WARNING_IS_SHOWN = "ide.archConfig.experimentalWarning.isShown"
  }

  @SuppressLint("SourceLockedOrientationActivity")
  override fun onCreate(savedInstanceState: Bundle?) {
    IThemeManager.getInstance().applyTheme(this)
    try {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } catch (e: Exception) {}

    super.onCreate(savedInstanceState)

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

    // 环境判定如果已完成，直接前往 MainActivity 并退出
    if (tryNavigateToMainIfSetupIsCompleted()) {
      return
    }

    isWizardMode = false
    setSwipeLock(true)

    setTransformer(AppIntroPageTransformerType.Fade)
    setProgressIndicator()
    showStatusBar(true)
    isIndicatorEnabled = true

    addSlide(GreetingFragment())

    if (!PackageUtils.isCurrentUserThePrimaryUser(this)) {
      val errorMessage =
          getString(
              string.bootstrap_error_not_primary_user_message,
              MarkdownUtils.getMarkdownCodeForString(TermuxConstants.TERMUX_PREFIX_DIR_PATH, false),
          )
      addSlide(
          OnboardingInfoFragment.newInstance(
              getString(string.title_unsupported_user),
              errorMessage,
              R.drawable.ic_alert,
              ContextCompat.getColor(this, R.color.color_error),
          )
      )
      return
    }

    if (isInstalledOnSdCard()) {
      val errorMessage =
          getString(
              string.bootstrap_error_installed_on_portable_sd,
              MarkdownUtils.getMarkdownCodeForString(TermuxConstants.TERMUX_PREFIX_DIR_PATH, false),
          )
      addSlide(
          OnboardingInfoFragment.newInstance(
              getString(string.title_install_location_error),
              errorMessage,
              R.drawable.ic_alert,
              ContextCompat.getColor(this, R.color.color_error),
          )
      )
      return
    }

    if (!checkDeviceSupported()) {
      return
    }

    if (!checkAllPermissionsGranted()) {
      addSlide(PermissionsFragment.newInstance(this))
    }

    if (!SdkChecker.isEnvironmentReadySync()) {
      addSlide(OdSdkToolInstallFragment.newInstance(this))
    }
  }

  override fun onResume() {
    super.onResume()
    tryNavigateToMainIfSetupIsCompleted()
  }

  override fun onDonePressed(currentFragment: Fragment?) {
    if (!IDEBuildConfigProvider.getInstance().supportsCpuAbi()) {
      finishAffinity()
      return
    }

    if (!SdkChecker.isEnvironmentReadySync() && currentFragment is OdSdkToolInstallFragment) {
      flashError(getString(string.msg_install_tools))
      return
    }

    tryNavigateToMainIfSetupIsCompleted()
  }

  fun onSetupCompleted() {
    if (!tryNavigateToMainIfSetupIsCompleted()) {
      val hasJdk = SdkChecker.hasValidJdk()
      val hasSdk = SdkChecker.hasValidSdk()
      val msg =
          when {
            !hasJdk && !hasSdk -> "Both JDK and Android SDK are missing."
            !hasJdk -> "JDK installation is missing or invalid."
            !hasSdk -> "Android SDK installation is missing or invalid."
            !checkAllPermissionsGranted() -> "Permissions are not fully granted."
            else -> "Environment setup is incomplete."
          }
      // 明确指出无法启动的原因
      flashError(msg)
    }
  }

  private fun checkAllPermissionsGranted(): Boolean {
    val context = this
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (!context.packageManager.canRequestPackageInstalls()) return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!android.os.Environment.isExternalStorageManager()) return false
    } else {
      val legacyPerms =
          listOf(
              Manifest.permission.READ_EXTERNAL_STORAGE,
              Manifest.permission.WRITE_EXTERNAL_STORAGE,
          )
      if (
          legacyPerms.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
          }
      ) {
        return false
      }
    }
    return true
  }

  private fun isSetupCompleted(): Boolean {
    return SdkChecker.isEnvironmentReadySync() && checkAllPermissionsGranted()
  }

  private fun tryNavigateToMainIfSetupIsCompleted(): Boolean {
    if (isSetupCompleted()) {
      startActivity(Intent(this, MainActivity::class.java))
      finish()
      return true
    }
    return false
  }

  private fun isInstalledOnSdCard(): Boolean {
    return PackageUtils.isAppInstalledOnExternalStorage(this) &&
        TermuxConstants.TERMUX_FILES_DIR_PATH !=
            filesDir.absolutePath.replace("^/data/user/0/".toRegex(), "/data/data/")
  }

  private fun checkDeviceSupported(): Boolean {
    val configProvider = IDEBuildConfigProvider.getInstance()
    if (!configProvider.supportsCpuAbi()) {
      addSlide(
          OnboardingInfoFragment.newInstance(
              getString(string.title_unsupported_device),
              getString(
                  string.msg_unsupported_device,
                  configProvider.cpuArch.abi,
                  configProvider.deviceArch.abi,
              ),
              R.drawable.ic_alert,
              ContextCompat.getColor(this, R.color.color_error),
          )
      )
      return false
    }

    if (configProvider.cpuArch != configProvider.deviceArch) {
      if (!prefManager.getBoolean(KEY_ARCHCONFIG_WARNING_IS_SHOWN, false)) {
        addSlide(
            OnboardingInfoFragment.newInstance(
                getString(string.title_experiment_flavor),
                getString(
                    string.msg_experimental_flavor,
                    configProvider.cpuArch.abi,
                    configProvider.deviceArch.abi,
                ),
                R.drawable.ic_alert,
                ContextCompat.getColor(this, R.color.color_warning),
            )
        )
        prefManager.putBoolean(KEY_ARCHCONFIG_WARNING_IS_SHOWN, true)
      }
    }
    return true
  }
}
