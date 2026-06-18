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

package com.itsaky.androidide.compose.preview.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.itsaky.androidide.compose.preview.data.device.DesktopApp
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 桌面 app 图标 Composable (PR-C).
 *
 * 渲染策略:
 * 1. 如果 [DesktopApp.iconResName] 非空 + 模块路径已知: 解析 [modulePath] 的 res/mipmap-* 目录
 *    找到对应 drawable, 用 [BitmapFactory] 解码, [Image] 渲染.
 * 2. 如果上面失败 / 无 icon: 调 [fallbackIcon] 拿 Material icon 显示.
 *
 * 这是 [DesktopLauncher] 的"真实 app icon"路径. 对于系统应用占位, 调用方传一个
 * [fallbackIcon] (例如 [Icons.Filled.Settings] for Settings).
 *
 * @param app 桌面应用
 * @param modulePath Android 模块路径 (例如 /storage/.../MyApp), 用来找 mipmap 文件
 * @param sizeDp 图标显示尺寸
 * @param fallbackIcon 找不到 mipmap 时用这个 Material icon 兜底
 */
@Composable
fun AppIcon(
    app: DesktopApp,
    modulePath: String?,
    sizeDp: Dp,
    fallbackIcon: ImageVector = Icons.Filled.Apps,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(app.id, app.iconResName) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loadAttempted by remember(app.id, app.iconResName) { mutableStateOf(false) }

    LaunchedEffect(app.id, app.iconResName, modulePath) {
        if (loadAttempted) return@LaunchedEffect
        loadAttempted = true
        if (app.iconResName.isNullOrBlank() || modulePath.isNullOrBlank()) return@LaunchedEffect

        bitmap = runCatching {
            val iconFile = findAppIconFile(modulePath, app.iconResName)
            if (iconFile != null && iconFile.exists()) {
                BitmapFactory.decodeFile(iconFile.absolutePath)
            } else {
                null
            }
        }.onFailure { LOG.warn("Failed to load app icon for {}", app.id, it) }
            .getOrNull()
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(sizeDp * 0.22f))
            .background(Color(0xFFFFFFFF).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        val bm = bitmap
        if (bm != null) {
            Image(
                bitmap = bm.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(sizeDp * 0.78f),
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = app.label,
                tint = if (app.isClickable) Color.White else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(sizeDp * 0.55f),
            )
        }
    }
}

/**
 * 在模块的 res 目录里找 app icon 文件.
 *
 * 搜索顺序:
 * 1. `res/mipmap-anydpi-v26/<iconResName>.xml` (adaptive icon) — v26 之后用这个
 * 2. `res/mipmap-xxxhdpi/<iconResName>.png` (高密度)
 * 3. `res/mipmap-xxhdpi/<iconResName>.png`
 * 4. `res/mipmap-xhdpi/<iconResName>.png`
 * 5. `res/mipmap-hdpi/<iconResName>.png`
 * 6. `res/mipmap-mdpi/<iconResName>.png`
 * 7. `res/drawable-xxxhdpi/<iconResName>.png`
 * 8. `res/drawable/<iconResName>.png` / `.xml`
 *
 * 任意一项找到就返回. null = 找不到.
 */
internal fun findAppIconFile(modulePath: String, iconResName: String): File? {
    val moduleDir = File(modulePath)
    if (!moduleDir.isDirectory) return null

    val mainResDir = File(moduleDir, "src/main/res")
    if (!mainResDir.isDirectory) {
        // 兜底: 直接用 modulePath 自身
        return findIconIn(File(modulePath, "res"), iconResName)
    }
    return findIconIn(mainResDir, iconResName)
}

private fun findIconIn(resDir: File, iconResName: String): File? {
    // 1) adaptive icon (v26+)
    val adaptive = File(resDir, "mipmap-anydpi-v26/$iconResName.xml")
    if (adaptive.exists()) return adaptive

    // 2) mipmap png (按密度从高到低)
    val densities = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")
    for (density in densities) {
        val png = File(resDir, "mipmap-$density/$iconResName.png")
        if (png.exists()) return png
        val webp = File(resDir, "mipmap-$density/$iconResName.webp")
        if (webp.exists()) return webp
    }

    // 3) drawable 兜底
    for (density in densities) {
        val png = File(resDir, "drawable-$density/$iconResName.png")
        if (png.exists()) return png
    }
    val drawable = File(resDir, "drawable/$iconResName.png")
    if (drawable.exists()) return drawable
    val drawableXml = File(resDir, "drawable/$iconResName.xml")
    if (drawableXml.exists()) return drawableXml

    return null
}

private val LOG = LoggerFactory.getLogger("AppIcon")
