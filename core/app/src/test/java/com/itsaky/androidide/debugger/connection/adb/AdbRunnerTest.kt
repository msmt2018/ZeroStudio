/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  AdbRunner 单元测试: 主要测 FakeAdbRunner 的 matcher 行为 + callHistory。
 */

package com.itsaky.androidide.debugger.connection.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AdbRunnerTest {

    @Test
    fun `FakeAdbRunner returns configured response for matching args`() {
        val fake = FakeAdbRunner().apply {
            respond(FakeAdbRunner.contains("connect")) { _ ->
                AdbResult(0, "connected to 127.0.0.1:5555")
            }
        }
        val r = fake.run(listOf("connect", "127.0.0.1:5555"), 5_000L)
        assertEquals(0, r.exitCode)
        assertEquals("connected to 127.0.0.1:5555", r.stdout)
    }

    @Test
    fun `FakeAdbRunner returns failure for un-matched args`() {
        val fake = FakeAdbRunner()
        val r = fake.run(listOf("foo"), 5_000L)
        assertEquals(1, r.exitCode)
        assertTrue("stderr should mention 'no fake response'",
            r.stderr.contains("no fake response"))
    }

    @Test
    fun `FakeAdbRunner records call history`() {
        val fake = FakeAdbRunner().apply {
            respond(FakeAdbRunner.contains("connect")) { _ -> AdbResult(0, "ok") }
            respond(FakeAdbRunner.contains("pidof")) { _ -> AdbResult(0, "12345") }
        }
        fake.run(listOf("connect", "1.2.3.4:5555"), 5_000L)
        fake.run(listOf("shell", "pidof", "com.foo"), 5_000L)
        fake.run(listOf("forward", "tcp:1234", "localabstract:jdwp-1234"), 5_000L)  // 不匹配任何 rule
        assertEquals(3, fake.callCount)
        assertEquals(listOf("connect", "1.2.3.4:5555"), fake.callHistory[0])
        assertEquals(listOf("shell", "pidof", "com.foo"), fake.callHistory[1])
        assertEquals(listOf("forward", "tcp:1234", "localabstract:jdwp-1234"), fake.callHistory[2])
    }

    @Test
    fun `runOnSerial prefixes -s serial to args`() {
        val fake = FakeAdbRunner().apply {
            respond(FakeAdbRunner.contains("connect")) { args ->
                AdbResult(0, "args=${args.joinToString(",")}")
            }
        }
        val r = fake.runOnSerial("emulator-5554", listOf("connect", "127.0.0.1:5555"), 5_000L)
        assertEquals(0, r.exitCode)
        assertEquals("args=-s,emulator-5554,connect,127.0.0.1:5555", r.stdout)
    }

    @Test
    fun `contains matcher matches substring across args`() {
        val fake = FakeAdbRunner().apply {
            respond(FakeAdbRunner.contains("pidof")) { _ -> AdbResult(0, "matched") }
        }
        assertEquals(0, fake.run(listOf("shell", "pidof", "-s", "com.foo"), 5_000L).exitCode)
    }

    @Test
    fun `equalsTo matcher matches full args only`() {
        val fake = FakeAdbRunner().apply {
            respond(FakeAdbRunner.equalsTo(listOf("connect", "127.0.0.1:5555"))) { _ ->
                AdbResult(0, "matched")
            }
        }
        // 严格匹配 - 完全相同
        assertEquals(0, fake.run(listOf("connect", "127.0.0.1:5555"), 5_000L).exitCode)
        // 不完全相同 - 失败
        assertEquals(1, fake.run(listOf("connect", "127.0.0.1:5556"), 5_000L).exitCode)
        assertEquals(1, fake.run(listOf("-s", "serial", "connect", "127.0.0.1:5555"), 5_000L).exitCode)
    }

    @Test
    fun `getAdbBinaryPath returns fake path`() {
        val fake = FakeAdbRunner()
        assertEquals("/fake/adb", fake.getAdbBinaryPath())
    }
}
