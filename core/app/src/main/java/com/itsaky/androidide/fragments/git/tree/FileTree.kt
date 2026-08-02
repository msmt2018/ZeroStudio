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

package com.itsaky.androidide.fragments.git.tree

import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import com.itsaky.androidide.eventbus.events.Event
import com.itsaky.androidide.models.SheetOption

/**
 * Git 专属的 FileTree 事件总线定义
 *
 * @author android_zero
 */
internal data class FileContextMenuItemClickEvent(val option: SheetOption) : Event()

data class ExpandTreeNodeRequestEvent(val node: Node<FileObject>) : Event()

class ListProjectFilesRequestEvent : Event()
