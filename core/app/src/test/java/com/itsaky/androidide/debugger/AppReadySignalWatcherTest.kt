/*
 *  ZeroStudio IDE - AppReadySignalWatcher 单元测试
 *
 *  PR-D7: 校验信号协议解析. 我们不真的起 logcat 进程,只喂字符串给
 *  parseAndDispatch.
 */

package com.itsaky.androidide.debugger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicReference

@RunWith(JUnit4::class)
class AppReadySignalWatcherTest {

    @Test
    fun `valid signal is parsed`() {
        val watcher = AppReadySignalWatcher()
        val captured = AtomicReference<Triple<String, Int, String?>>()
        watcher.setListener { pkg, port, build ->
            captured.set(Triple(pkg, port, build))
        }
        watcher.parseAndDispatch("READY pkg=com.example jdwp=5005 build=debug")
        val got = captured.get()
        assertNotNull(got)
        assertEquals("com.example", got!!.first)
        assertEquals(5005, got.second)
        assertEquals("debug", got.third)
    }

    @Test
    fun `valid signal without build is accepted`() {
        val watcher = AppReadySignalWatcher()
        val captured = AtomicReference<Triple<String, Int, String?>>()
        watcher.setListener { pkg, port, build ->
            captured.set(Triple(pkg, port, build))
        }
        watcher.parseAndDispatch("READY pkg=com.example jdwp=5555")
        val got = captured.get()
        assertNotNull(got)
        assertEquals("com.example", got!!.first)
        assertEquals(5555, got.second)
        assertNull(got.third)
    }

    @Test
    fun `noise lines are ignored`() {
        val watcher = AppReadySignalWatcher()
        val captured = AtomicReference<Triple<String, Int, String?>>()
        watcher.setListener { pkg, port, build ->
            captured.set(Triple(pkg, port, build))
        }
        watcher.parseAndDispatch("Foo bar baz")
        watcher.parseAndDispatch("READY")
        watcher.parseAndDispatch("READY pkg=com.example")
        watcher.parseAndDispatch("READY pkg=com.example jdwp=0")
        watcher.parseAndDispatch("READY pkg=com.example jdwp=notanumber")
        assertNull(captured.get())
    }

    @Test
    fun `signal tag and pattern are stable`() {
        assertEquals("ZeroStudioDebug", AppReadySignalWatcher.SIGNAL_TAG)
        // pattern must contain "READY", "pkg=", "jdwp=" substrings
        val src = AppReadySignalWatcher.SIGNAL_PATTERN.pattern()
        assert(src.contains("READY"))
        assert(src.contains("pkg="))
        assert(src.contains("jdwp="))
    }
}
