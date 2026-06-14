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

package com.itsaky.androidide.compose.preview.r8

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v2.4 P0 R8 规则验证测试.
 *
 * 验证:
 * - proguard-preview-rules.pro 文件存在
 * - 文件含 10 大类规则
 * - build.gradle.kts 配置了 buildTypes.release.minify
 */
class R8ConfigTest {

    @Test
    fun `01 proguard-preview-rules pro exists`() {
        val file = File("proguard-preview-rules.pro")
        assertTrue("proguard-preview-rules.pro not found at $file", file.exists())
        assertTrue("proguard-preview-rules.pro is empty", file.length() > 100)
    }

    @Test
    fun `02 rules contain all 10 categories`() {
        val text = File("proguard-preview-rules.pro").readText()
        // 1. Compose 注解
        assertTrue("missing @Composable keep", text.contains("@androidx.compose.runtime.Composable"))
        // 2. LiveLiterals
        assertTrue("missing LiveLiterals keep", text.contains("LiveLiterals"))
        // 3. Preview function 签名
        assertTrue("missing @Preview function keep", text.contains("@androidx.compose.ui.tooling.preview.Preview <methods>"))
        // 4. PreviewParameterProvider
        assertTrue("missing PreviewParameterProvider keep",
            text.contains("PreviewParameterProvider"))
        // 5. ViewModel
        assertTrue("missing ViewModel keep", text.contains("ViewModel"))
        // 6. Coroutines
        assertTrue("missing Coroutines dontwarn", text.contains("kotlinx.coroutines"))
        // 7. Constructor
        assertTrue("missing constructor keep", text.contains("<init>"))
        // 8. R8 fullMode
        assertTrue("missing -dontwarn", text.contains("-dontwarn"))
        // 9. ASM
        assertTrue("missing asm keep", text.contains("org.ow2.asm"))
        // 10. SourceFile / LineNumberTable
        assertTrue("missing stack info", text.contains("SourceFile"))
    }

    @Test
    fun `03 build_gradle_kts has release minify enabled`() {
        val text = File("build.gradle.kts").readText()
        assertTrue("missing buildTypes.release block", text.contains("buildTypes"))
        assertTrue("missing isMinifyEnabled", text.contains("isMinifyEnabled"))
        assertTrue("missing isShrinkResources", text.contains("isShrinkResources"))
        assertTrue("missing proguard-preview-rules.pro reference",
            text.contains("proguard-preview-rules.pro"))
    }
}
