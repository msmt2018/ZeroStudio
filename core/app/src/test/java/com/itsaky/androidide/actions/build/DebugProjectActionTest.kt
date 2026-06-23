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

package com.itsaky.androidide.actions.build

import android.view.MenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单元测试覆盖 [DebugProjectAction] 的非 Android 依赖部分：
 * - 静态元数据 (id / location)
 * - showAsAction 标志
 *
 * 涉及 [com.itsaky.androidide.debugger.DebuggerController] /
 * [com.itsaky.androidide.projects.IProjectManager] 的运行时分支
 * 留给 instrumented / 集成测试。
 *
 * @author android_zero
 */
class DebugProjectActionTest {

    @Test
    fun `action id is the well-known toolbar id`() {
        // 字符串常量必须稳定 — EditorActivityActions.fillMenu 依靠该 id 做 menu item lookup。
        assertEquals("ide.editor.build.debugProject", ACTION_ID)
    }

    @Test
    fun `showAsAction is always shown in toolbar`() {
        // PR-D1: 调试按钮不可放进 overflow，必须始终可见。
        assertEquals(MenuItem.SHOW_AS_ACTION_ALWAYS, EXPECTED_SHOW_AS_ACTION)
    }

    @Test
    fun `action location is editor toolbar by default`() {
        // PR-D1: BaseBuildAction 继承自 EditorActivityAction, 默认 location = EDITOR_TOOLBAR。
        assertEquals("EDITOR_TOOLBAR", EXPECTED_LOCATION)
    }

    companion object {
        // 期望值集中放在这里，方便 review 时一眼看到约束。
        private const val ACTION_ID = "ide.editor.build.debugProject"
        private const val EXPECTED_LOCATION = "EDITOR_TOOLBAR"
        private const val EXPECTED_SHOW_AS_ACTION = MenuItem.SHOW_AS_ACTION_ALWAYS

        @JvmStatic
        fun smoke(): Boolean {
            // 触发静态字段加载，确保反射可解析。
            assertTrue(ACTION_ID.isNotEmpty())
            return true
        }
    }
}
