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
 *  along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.activities.editor.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.R

/**
 * `layout_editor_sidebar_header.xml` 的 Jetpack Compose 一比一复刻。
 *
 * 结构映射 (XML → Compose):
 * - `ShapeableImageView` (48dp × 48dp, padding=8dp,
 *   shapeAppearance=ShapeAppearance.Material3.Corner.Medium[圆角 8dp],
 *   srcCompat=@mipmap/ic_launcher)
 *   → [Image] + size(48dp) + padding(8dp) + clip(RoundedCornerShape(8dp))
 */
@Composable
fun EditorSidebarHeaderScreen(modifier: Modifier = Modifier) {
  // ShapeableImageView: 48dp, padding 8dp, Corner.Medium 圆角, srcCompat=ic_launcher
  Image(
      painter = painterResource(R.mipmap.ic_launcher),
      contentDescription = null,
      modifier =
          modifier
              .size(48.dp)
              .padding(8.dp)
              .clip(RoundedCornerShape(8.dp)),
      contentScale = ContentScale.Fit)
}
