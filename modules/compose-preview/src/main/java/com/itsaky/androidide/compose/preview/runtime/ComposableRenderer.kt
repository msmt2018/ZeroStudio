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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Compose 渲染器 v2.
 *
 * - **MethodHandle**: 用 [MethodHandleResolver] 缓存反射查找, `invokeWithArguments` 替代 `Method.invoke`,
 *   JIT 友好, 无 boxing 数组分配.
 * - **零捕获异常**: `runCatching` + `Throwable` 一起捕获, 避免擦除后丢失原始 stack.
 * - **统一错误渲染**: 任何渲染失败都走 [ErrorContent], 用户能看到具体错误.
 *
 * 取代旧 [ComposableRenderer] (基于 [java.lang.reflect.Method.invoke]).
 */
class ComposableRenderer(
    private val composeView: ComposeView,
    private val classLoader: ComposeClassLoader,
    private val handleResolver: MethodHandleResolver = MethodHandleResolver()
) {

    /**
     * 渲染 [dexFile] 中 [className] 的 [functionName] Composable.
     */
    fun render(dexFile: File, className: String, functionName: String) {
        val clazz = classLoader.loadClass(dexFile, className)
        if (clazz == null) {
            showError("Failed to load class: $className")
            return
        }

        val resolved = handleResolver.resolve(clazz, functionName)
        if (resolved == null) {
            showError("Composable function not found: $functionName")
            return
        }

        composeView.setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RenderComposable(clazz, resolved)
                }
            }
        }
        LOG.debug("Rendered composable: {}#{}", className, functionName)
    }

    @Composable
    private fun RenderComposable(clazz: Class<*>, resolved: MethodHandleResolver.Resolved) {
        val isStatic = resolved.isStatic
        val method = resolved.method

        val instance: Any? = if (isStatic) {
            null
        } else {
            runCatching { clazz.getDeclaredConstructor().newInstance() }.getOrNull()
        }

        if (!isStatic && instance == null) {
            LOG.error("Failed to create instance for non-static method: {}", method.name)
            ErrorContent("Failed to create instance for ${clazz.simpleName}")
            return
        }

        val composer = currentComposer
        val paramCount = method.parameterCount

        val invokeResult: Result<Any?> = runCatching {
            when {
                // 静态无参 (顶层 main, IDE 风格预览)
                isStatic && paramCount == 0 -> {
                    resolved.handle.invokeWithArguments()
                }
                // 静态 @Composable: composer, changedFlags
                isStatic && paramCount == 2 -> {
                    resolved.handle.invokeWithArguments(composer, 0)
                }
                // 实例 @Composable: instance, composer, changedFlags
                !isStatic && paramCount == 2 -> {
                    resolved.handle.invokeWithArguments(instance, composer, 0)
                }
                // 实例多参: instance, [params...], composer, changedFlags
                paramCount > 2 -> {
                    val args = arrayOfNulls<Any>(paramCount)
                    args[0] = instance
                    args[paramCount - 2] = composer
                    args[paramCount - 1] = 0
                    resolved.handle.invokeWithArguments(*args)
                }
                else -> {
                    error("Unexpected parameter count $paramCount for method: ${method.name}")
                }
            }
        }

        invokeResult.exceptionOrNull()?.let { e ->
            LOG.error("Failed to invoke composable method: {}", method.name, e)
            ErrorContent("Invocation failed: ${e.message ?: e::class.java.simpleName}")
        }
    }

    private fun showError(message: String) {
        composeView.setContent {
            MaterialTheme {
                ErrorContent(message)
            }
        }
    }

    @Composable
    private fun ErrorContent(message: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF3F3))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Preview Error",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFB00020)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ComposableRenderer::class.java)
    }
}
