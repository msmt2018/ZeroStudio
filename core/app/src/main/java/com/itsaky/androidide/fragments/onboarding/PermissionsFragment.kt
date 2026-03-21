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
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.appintro.SlidePolicy
import com.itsaky.androidide.R
import com.itsaky.androidide.buildinfo.BuildInfo
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import com.itsaky.androidide.adapters.onboarding.OnboardingPermissionsAdapter
import com.itsaky.androidide.databinding.FragmentOnboardingPermissionsBinding
import com.itsaky.androidide.fragments.FragmentWithBinding
import com.itsaky.androidide.models.OnboardingPermissionItem
import com.itsaky.androidide.utils.isAtLeastO
import com.itsaky.androidide.utils.isAtLeastR
import com.itsaky.androidide.utils.isAtLeastT
import org.slf4j.LoggerFactory

/**
 * PermissionsFragment : 权限申请页面
 *
 * @author Akash Yadav
 * @author android_zero
 */
class PermissionsFragment : FragmentWithBinding<FragmentOnboardingPermissionsBinding>(FragmentOnboardingPermissionsBinding::inflate), SlidePolicy {

    private var adapter: OnboardingPermissionsAdapter? = null
    private val permissionsList: MutableList<OnboardingPermissionItem> = mutableListOf()
    private var isAutoRequesting = false

    // 运行时权限启动器 (用于基础存储权限)
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsUpdated()
    }

    // 设置页面启动器 (用于通知、所有文件访问、安装应用)
    private val settingsTogglePermissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onPermissionsUpdated()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        binding.onboardingTitle.text = args.getCharSequence(OnboardingFragment.KEY_ONBOARDING_TITLE)
        binding.onboardingSubtitle.text = args.getCharSequence(OnboardingFragment.KEY_ONBOARDING_SUBTITLE)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // 初始化列表
        initPermissionList()
        
        adapter = OnboardingPermissionsAdapter(permissionsList) { permissionKey ->
            isAutoRequesting = false
            requestPermission(permissionKey)
        }
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null

        binding.grantAllButton.setOnClickListener {
            isAutoRequesting = true
            processNextUngrantedPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        onPermissionsUpdated()
    }

    private fun initPermissionList() {
        permissionsList.clear()
        val context = requireContext()

        // 通知权限
        permissionsList.add(
            OnboardingPermissionItem(
                permission = POST_NOTIFICATIONS,
                title = R.string.permission_title_notifications,
                description = R.string.permission_desc_notifications,
                isGranted = isNotificationGranted(context)
            )
        )

        // 基础存储权限 (READ/WRITE) - 所有版本都显示
        permissionsList.add(
            OnboardingPermissionItem(
                permission = PERMISSION_KEY_BASIC_STORAGE,
                title = R.string.permission_title_storage, // "Storage Access"
                description = R.string.permission_desc_storage, // "Read and write files..."
                isGranted = isBasicStorageGranted(context)
            )
        )

        //所有文件访问权限 (仅 Android 11+)
        if (isAtLeastR()) {
            permissionsList.add(
                OnboardingPermissionItem(
                    permission = PERMISSION_KEY_ALL_FILES,
                    title = R.string.permission_title_storage_all_files, // "All Files Access"
                    description = R.string.permission_desc_storage_all_files, // "Manage all files..."
                    isGranted = Environment.isExternalStorageManager()
                )
            )
        }

        // 安装未知应用权限 (Android 8+)
        if (isAtLeastO()) {
            permissionsList.add(
                OnboardingPermissionItem(
                    permission = PERMISSION_KEY_INSTALL_PACKAGES,
                    title = R.string.permission_title_install_packages,
                    description = R.string.permission_desc_install_packages,
                    isGranted = context.packageManager.canRequestPackageInstalls()
                )
            )
        }
    }

    private fun requestPermission(key: String) {
        val context = requireContext()
        when (key) {
           POST_NOTIFICATIONS -> {
            val intent = Intent()
            when {
                // Android 8.0 (API 26) 及以上：直接进入「通知开关」页面
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                
                // Android 5.0 - 7.1 兼容方法
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                    intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    intent.putExtra("app_package", context.packageName)
                    intent.putExtra("app_uid", context.applicationInfo.uid)
                }

                // 兜底：跳转到应用详情页
                else -> {
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    intent.data = Uri.fromParts("package", context.packageName, null)
                }
            }

            try {
                settingsTogglePermissionRequestLauncher.launch(intent)
            } catch (e: Exception) {
                // 如果特定页面崩溃（部分定制 ROM），则尝试进入应用详情页
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                settingsTogglePermissionRequestLauncher.launch(fallback)
            }
        }

        PERMISSION_KEY_BASIC_STORAGE -> {
            if (isAtLeastR()) {
                if (Environment.isExternalStorageManager()) {
                    onPermissionsUpdated()
                    return
                }
                requestPermission(PERMISSION_KEY_ALL_FILES)
            } else {
                runtimePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }

        PERMISSION_KEY_ALL_FILES -> {
            if (isAtLeastR()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                settingsTogglePermissionRequestLauncher.launch(intent)
            }
        }

        PERMISSION_KEY_INSTALL_PACKAGES -> {
            if (isAtLeastO()) {
                requestSettingsTogglePermission(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            }
        }
    }
}

    /**
     * 核心 Intent 构建方法
     */
    private fun requestSettingsTogglePermission(
        action: String,
        setData: Boolean = true,
    ) {
		val intent = Intent(action)
		intent.putExtra(Settings.EXTRA_APP_PACKAGE, BuildInfo.PACKAGE_NAME)
		if (setData) {
			intent.setData(Uri.fromParts("package", BuildInfo.PACKAGE_NAME, null))
		}
		try {
			settingsTogglePermissionRequestLauncher.launch(intent)
		} catch (err: Throwable) {
			logger.error("Failed to launch settings with intent {}", intent, err)
			flashError(getString(R.string.err_no_activity_to_handle_action, action))
		}
    }

    private fun onPermissionsUpdated() {
        val context = requireContext()
        permissionsList.forEach { item ->
            item.isGranted = when (item.permission) {
                POST_NOTIFICATIONS -> isNotificationGranted(context)
                PERMISSION_KEY_BASIC_STORAGE -> isBasicStorageGranted(context)
                PERMISSION_KEY_ALL_FILES -> if (isAtLeastR()) Environment.isExternalStorageManager() else true
                PERMISSION_KEY_INSTALL_PACKAGES -> if (isAtLeastO()) context.packageManager.canRequestPackageInstalls() else true
                else -> true
            }
        }

        adapter?.notifyDataSetChanged()
        
        val allGranted = permissionsList.all { it.isGranted }
        binding.grantAllButton.isEnabled = !allGranted
        binding.grantAllButton.alpha = if (allGranted) 0.5f else 1.0f

        if (isAutoRequesting) {
            processNextUngrantedPermission()
        }
    }

private fun processNextUngrantedPermission() {
    val nextItem = permissionsList.firstOrNull { !it.isGranted }
    
    if (nextItem != null) {
        val key = if (isAtLeastR() && nextItem.permission == PERMISSION_KEY_BASIC_STORAGE) {
            PERMISSION_KEY_ALL_FILES
        } else {
            nextItem.permission
        }
        requestPermission(key)
    } else {
        isAutoRequesting = false
        flashSuccess("All permissions are ready")
    }
}

    private fun isNotificationGranted(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun isBasicStorageGranted(context: Context): Boolean {
        if (isAtLeastR() && Environment.isExternalStorageManager()) {
            return true
    }
    
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
          return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
   }


    // --- SlidePolicy ---

    override val isPolicyRespected: Boolean
        get() = permissionsList.all { it.isGranted }

    override fun onUserIllegallyRequestedNextPage() {
        Toast.makeText(context, R.string.msg_grant_permissions, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionsFragment::class.java)

        private const val POST_NOTIFICATIONS = "permission_notifications"
        private const val PERMISSION_KEY_BASIC_STORAGE = "permission_basic_storage"
        private const val PERMISSION_KEY_ALL_FILES = "permission_all_files"
        private const val PERMISSION_KEY_INSTALL_PACKAGES = "permission_install_packages"

        @JvmStatic
        fun newInstance(context: Context): PermissionsFragment {
            return PermissionsFragment().apply {
                arguments = Bundle().apply {
                    putCharSequence(OnboardingFragment.KEY_ONBOARDING_TITLE, context.getString(R.string.onboarding_title_permissions))
                    putCharSequence(OnboardingFragment.KEY_ONBOARDING_SUBTITLE, context.getString(R.string.onboarding_subtitle_permissions))
                }
            }
        }
    }
}