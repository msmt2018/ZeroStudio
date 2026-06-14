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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * v2.2 P5 单元测试 — SourceChangeWatcher (P3 手动 API + FNV-1a hash + WatchService).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceChangeWatcherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ---------- FNV-1a hash ----------

    @Test
    fun `fnv1a empty string returns offset basis`() {
        // 0x811c9dc5 (2166136261) 是 FNV-1a 32-bit offset basis
        // Kotlin Int 溢出后等价于 -2128831035
        val expected = -2128831035
        assertEquals(expected, SourceChangeWatcher.fnv1aHash(""))
    }

    @Test
    fun `fnv1a same string same hash`() {
        val h1 = SourceChangeWatcher.fnv1aHash("hello world")
        val h2 = SourceChangeWatcher.fnv1aHash("hello world")
        assertEquals(h1, h2)
    }

    @Test
    fun `fnv1a different strings different hashes`() {
        val h1 = SourceChangeWatcher.fnv1aHash("hello world")
        val h2 = SourceChangeWatcher.fnv1aHash("hello WORLD")
        assertNotEquals(h1, h2)
    }

    @Test
    fun `fnv1a unicode handled`() {
        val h1 = SourceChangeWatcher.fnv1aHash("中文测试")
        val h2 = SourceChangeWatcher.fnv1aHash("中文测试")
        assertEquals(h1, h2)
        assertTrue("hash should be non-zero: $h1", h1 != 0)
    }

    @Test
    fun `fnv1a deterministic across calls`() {
        val a = SourceChangeWatcher.fnv1aHash("foobar")
        val b = SourceChangeWatcher.fnv1aHash("foobar")
        val c = SourceChangeWatcher.fnv1aHash("foobar")
        assertEquals(a, b)
        assertEquals(b, c)
        assertTrue("hash non-zero: $a", a != 0)
    }

    // ---------- 手动 notify API ----------

    @Test
    fun `notifySourceChanged emits event with correct fields`() = runTest {
        val watcher = SourceChangeWatcher()
        val text = "package com.example\n@Composable fun Foo() {}"
        val collected = collectEvents(watcher, scope = backgroundScope, maxItems = 1)
        watcher.notifySourceChanged(text, path = "/test/foo.kt")
        val event = collected.await(timeoutMs = 2000L)
        assertEquals(text, event.sourceText)
        assertEquals("/test/foo.kt", event.sourcePath)
        assertEquals(SourceChangeWatcher.fnv1aHash(text), event.sourceHash)
        assertTrue("manual flag should be true", event.manual)
    }

    @Test
    fun `notifySourceChanged without path`() = runTest {
        val watcher = SourceChangeWatcher()
        val collected = collectEvents(watcher, scope = backgroundScope, maxItems = 1)
        watcher.notifySourceChanged("abc")
        val event = collected.await(timeoutMs = 2000L)
        assertEquals(null, event.sourcePath)
        assertTrue(event.manual)
    }

    // ---------- WatchService 生命周期 ----------

    @Test
    fun `startWatch on nonexistent file returns false`() {
        val watcher = SourceChangeWatcher()
        val nonexistent = File(tempFolder.root, "does-not-exist.kt")
        val ok = watcher.startWatch(nonexistent)
        assertFalse(ok)
        assertFalse(watcher.isWatching)
    }

    @Test
    fun `startWatch on existing file returns true then isWatching`() {
        val watcher = SourceChangeWatcher()
        val existing = tempFolder.newFile("test.kt").apply { writeText("initial") }
        val ok = watcher.startWatch(existing)
        assertTrue("startWatch should succeed for existing file", ok)
        assertTrue(watcher.isWatching)
        watcher.stopWatch()
        assertFalse(watcher.isWatching)
    }

    @Test
    fun `stopWatch is idempotent`() {
        val watcher = SourceChangeWatcher()
        watcher.stopWatch() // 没启动过, no-op
        watcher.stopWatch() // 多次 no-op
        assertFalse(watcher.isWatching)
    }
}

/**
 * 极简事件收集器: 监听 [flow] 直到 [maxItems] 个, 放进 [items] 供 await 读取.
 */
private class EventCollector<T>(
    val items: MutableList<T>,
    private val flow: SharedFlow<T>,
    scope: CoroutineScope,
) {
    private val job: Job = scope.launch {
        flow.collect { items.add(it) }
    }

    suspend fun await(timeoutMs: Long): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (items.isNotEmpty()) return items.removeAt(0)
            delay(10L)
        }
        throw AssertionError("Timeout waiting for item, got ${items.size} items")
    }

    fun cancel() {
        job.cancel()
    }
}

private fun TestScope.collectEvents(
    flow: SharedFlow<SourceChangeEvent>,
    maxItems: Int = 1,
): EventCollector<SourceChangeEvent> {
    val items = mutableListOf<SourceChangeEvent>()
    return EventCollector(items, flow, this)
}
