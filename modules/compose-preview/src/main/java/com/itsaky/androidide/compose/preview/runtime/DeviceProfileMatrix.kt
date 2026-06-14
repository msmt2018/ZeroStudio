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

package com.itsaky.androidide.compose.preview.runtime

import com.itsaky.androidide.compose.preview.data.device.DeviceCatalog
import com.itsaky.androidide.compose.preview.ui.DeviceProfile

/**
 * v2.3 P2 设备 profile 矩阵.
 *
 * 设计师友好: 一次性看到所有 (w, h) 尺寸, 横向对比.
 *
 * ## 用法
 *
 * ```kotlin
 * val matrix = DeviceProfileMatrix.default()        // 20+ 主流设备
 * val phonesOnly = DeviceProfileMatrix.phones()     // 仅 phone
 * val tabletsOnly = DeviceProfileMatrix.tablets()   // 仅 tablet
 * val custom = DeviceProfileMatrix.fromProfiles(listOf(myProfile))
 * ```
 */
class DeviceProfileMatrix(val profiles: List<DeviceProfile>) {

    val size: Int get() = profiles.size

    fun byFormFactor(formFactor: DeviceProfile.FormFactor): DeviceProfileMatrix {
        return DeviceProfileMatrix(profiles.filter { it.formFactor == formFactor })
    }

    /**
     * 按 id 查 profile.
     */
    fun byId(id: String): DeviceProfile? = profiles.firstOrNull { it.id == id }

    companion object {
        /**
         * 20+ 主流设备 profile. 从 [DeviceCatalog.builtinProfiles] 抽.
         */
        fun default(): DeviceProfileMatrix {
            return DeviceProfileMatrix(DeviceCatalog.builtinProfiles)
        }

        /**
         * 仅 phone profile.
         */
        fun phones(): DeviceProfileMatrix {
            return default().byFormFactor(DeviceProfile.FormFactor.PHONE)
        }

        /**
         * 仅 tablet profile.
         */
        fun tablets(): DeviceProfileMatrix {
            return default().byFormFactor(DeviceProfile.FormFactor.TABLET)
        }

        /**
         * 仅 foldable.
         */
        fun foldables(): DeviceProfileMatrix {
            return default().byFormFactor(DeviceProfile.FormFactor.FOLDABLE)
        }

        fun fromProfiles(profiles: List<DeviceProfile>): DeviceProfileMatrix {
            return DeviceProfileMatrix(profiles)
        }
    }
}
