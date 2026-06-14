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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * v2.2 P5 单元测试 — LiveEditCoordinator 状态机.
 *
 * 覆盖: 7 态转换, debounce 合并, paused 跳过, forceReload 优先级, 失败保留旧 preview,
 * 串行化 (mutex).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveEditCoordinatorTest {

    private lateinit var coordinator: LiveEditCoordinator
    private lateinit var callback: RecordingCallback

    @Before
    fun setUp() {
        callback = RecordingCallback()
        coordinator = LiveEditCoordinator(debounceMs = 50L)
        coordinator.start(callback)
    }

    @After
    fun tearDown() {
        coordinator.stop()
    }

    // ---------- 状态机 ----------

    @Test
    fun `initial state is Idle`() {
        assertEquals(LiveEditState.Idle, coordinator.state.value)
    }

    @Test
    fun `paused toggle updates state to Idle`() {
        coordinator.setPaused(true)
        assertTrue(coordinator.isPaused)
        // paused 时 state 仍然是 Idle (直到下次 source change)
        assertEquals(LiveEditState.Idle, coordinator.state.value)
    }

    // ---------- 串行化 (Mutex) ----------

    @Test
    fun `multiple rapid events collapse via debounce`() = runTest {
        // 短 debounce 让测试快
        coordinator.stop()
        coordinator = LiveEditCoordinator(debounceMs = 50L)
        coordinator.start(callback)

        val n = 5
        for (i in 0 until n) {
            coordinator.notifySourceChanged("source $i")
        }
        // 50ms debounce 期间所有事件合并
        advanceTimeBy(60L)
        runCurrent()

        // 1 次 reload (collectLatest 取消前几次)
        assertTrue("expected 1 reload, got ${callback.reloadCount.get()}", callback.reloadCount.get() == 1)
    }

    // ---------- paused 跳过 ----------

    @Test
    fun `paused ignores source change events`() = runTest {
        coordinator.setPaused(true)
        val n = 3
        for (i in 0 until n) {
            coordinator.notifySourceChanged("source $i")
        }
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(0, callback.reloadCount.get())
    }

    // ---------- 失败保留旧 preview ----------

    @Test
    fun `compile failure updates state to Error but coordinator still alive`() = runTest {
        coordinator.stop()
        val failingCallback = FailingCallback()
        coordinator = LiveEditCoordinator(debounceMs = 50L)
        coordinator.start(failingCallback)

        coordinator.notifySourceChanged("source")
        advanceTimeBy(100L)
        runCurrent()

        // 状态应该变为 Error
        assertTrue("state should be Error, got ${coordinator.state.value}", coordinator.state.value is LiveEditState.Error)
        val result = coordinator.lastResult
        assertNotNull(result)
        assertTrue("result should be Error, got $result", result is LiveEditResult.Error)
    }

    // ---------- forceReload ----------

    @Test
    fun `forceReload with no source text returns Error`() = runTest {
        val result = coordinator.forceReload()
        assertTrue("result should be Error, got $result", result is LiveEditResult.Error)
    }

    @Test
    fun `forceReload with explicit source triggers reload`() = runTest {
        val result = coordinator.forceReload(sourceText = "manual reload")
        assertTrue("result should be Success, got $result", result is LiveEditResult.Success)
        assertEquals(1, callback.reloadCount.get())
    }

    @Test
    fun `forceReload works even when paused`() = runTest {
        coordinator.setPaused(true)
        val result = coordinator.forceReload(sourceText = "manual reload while paused")
        assertTrue("result should be Success, got $result", result is LiveEditResult.Success)
        assertEquals(1, callback.reloadCount.get())
    }

    // ---------- stop 是 idempotent ----------

    @Test
    fun `stop is idempotent`() {
        coordinator.stop()
        coordinator.stop() // 多次 no-op
        // 不会抛异常
    }

    // ---------- 无 callback 时不崩 ----------

    @Test
    fun `forceReload without callback returns Error`() = runTest {
        coordinator.stop()
        // 没 start, 没 callback
        val result = coordinator.forceReload("source")
        assertTrue(result is LiveEditResult.Error)
    }

    // ---------- Test Fixtures ----------

    /**
     * 录制所有 LiveEditCallback 调用的 mock.
     */
    private class RecordingCallback : LiveEditCallback {
        val reloadCount = AtomicInteger(0)
        val parseCount = AtomicInteger(0)
        val swapCount = AtomicInteger(0)
        val reRenderCount = AtomicInteger(0)

        override fun parseSource(sourceText: String): Any? {
            parseCount.incrementAndGet()
            return "parsed:$sourceText" // 任何非 null 即可
        }

        override fun recompile(sourceText: String, parsed: Any): Any? {
            // 模拟 "compilation": 返回 CompilationResult 替代
            reloadCount.incrementAndGet()
            return FakeCompilationResult(
                dexFile = File.createTempFile("test-", ".dex").apply { deleteOnExit() },
                className = "com.test.GeneratedKt",
            )
        }

        override fun swapClassLoader(dexFile: File, className: String) {
            swapCount.incrementAndGet()
        }

        override fun reRender(compilation: Any) {
            reRenderCount.incrementAndGet()
        }
    }

    /**
     * 总是失败的 callback. parse 返回 null 或 recompile 返回 null 模拟编译失败.
     */
    private class FailingCallback : LiveEditCallback {
        override fun parseSource(sourceText: String): Any? = "parsed"
        override fun recompile(sourceText: String, parsed: Any): Any? = null
        override fun swapClassLoader(dexFile: File, className: String) {}
        override fun reRender(compilation: Any) {}
    }

    private data class FakeCompilationResult(
        val dexFile: File,
        val className: String,
    )
}
