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
package android.zero.studio.view.filetree.interfaces

import android.graphics.drawable.Drawable
import android.zero.studio.view.filetree.model.Node

/**
 * Optional decorator that adds a small trailing widget (icon/button) to the right
 * edge of a file tree row. Returning a `null` [getTrailingDrawable] hides the
 * widget for that node.
 *
 * @author android_zero
 */
fun interface FileItemTrailingProvider {
  /**
   * The drawable to render at the right edge of the row, or `null` to hide the
   * trailing widget for [node].
   */
  fun getTrailingDrawable(node: Node<FileObject>): Drawable?

  /**
   * Invoked when the user taps the trailing widget. The default implementation
   * does nothing; override via a wrapper if interaction is required.
   */
  fun onTrailingClick(node: Node<FileObject>) = Unit
}
