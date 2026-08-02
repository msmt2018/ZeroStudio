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

package com.itsaky.androidide.activities.editor

import dagger.hilt.android.AndroidEntryPoint

/**
 * 编辑器主 Activity。
 *
 * 标注 @AndroidEntryPoint 以启用 Hilt 依赖注入: 设备连接管理 BottomSheet
 * (DeviceConnectionBottomSheet) 中的 WiFi/OTG/Fastboot 三个 Tab 通过
 * hiltViewModel() 获取 connection 模块的 @HiltViewModel, 这要求宿主 Activity
 * 是 Hilt 组件持有者 (实现 GeneratedComponentManager)。IDEApplication 已标注
 * @HiltAndroidApp, 此处标注后 hiltViewModel() 即可正常解析 ViewModel。
 *
 * @author Akash Yadav
 */
@AndroidEntryPoint
class EditorActivityKt : EditorHandlerActivity()

// TODO: Should we adjust to the display cutout insets?
