/*
 *  ZeroStudio IDE - Debug Connection Layer 单测
 *
 *  BaseDebugConnection.finishAttach 行为测试:
 *    - 解决之前 attach() 阶段"onSuccess 里 transitionTo(Closed)"返回 success 的反模式。
 *    - 现在 finishAttach(info, ok=true) 返回 success + state=Attached,
 *      finishAttach(info, ok=false) 返回 failure + state=Closed(IoFailure),
 *      保证 Result 和 state 一致。
 */

package com.itsaky.androidide.debugger.connection

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BaseDebugConnectionFinishAttachTest {

    /** 最小可用的 BaseDebugConnection 子类, 把 finishAttach 暴露给测试用。 */
    private class TestConn(
        type: ConnectionType = ConnectionType.AidlSocket,
        target: DebugTarget = DebugTarget("com.test", null),
    ) : BaseDebugConnection(type, target) {
        var attachedCallbackInvoked = false

        fun callFinishAttach(
            info: AttachInfo,
            ok: Boolean,
            failureMsg: String = "post-condition not met",
        ): Result<AttachInfo> = finishAttach(
            info = info,
            ok = ok,
            failureMsg = failureMsg,
            onAttached = { attachedCallbackInvoked = true },
        )
    }

    @Test
    fun `finishAttach with ok=true returns success and transitions to Attached`() = runBlocking {
        val conn = TestConn()
        val info = AttachInfo(pid = 1234, jdwpSessionId = 5678, jdwpDescription = "test")
        val r = conn.callFinishAttach(info, ok = true)
        assertTrue("result should be success", r.isSuccess)
        assertEquals(info, r.getOrNull())
        assertTrue("state should be Attached", conn.state.value is ConnectionState.Attached)
        val attached = conn.state.value as ConnectionState.Attached
        assertEquals(1234, attached.pid)
        assertEquals(5678, attached.jdwpSessionId)
        assertTrue("onAttached callback should have been invoked", conn.attachedCallbackInvoked)
    }

    @Test
    fun `finishAttach with ok=false returns failure and transitions to Closed`() = runBlocking {
        val conn = TestConn()
        val info = AttachInfo(pid = 1234, jdwpSessionId = 5678, jdwpDescription = "test")
        val r = conn.callFinishAttach(info, ok = false, failureMsg = "client socket missing")
        assertTrue("result should be failure", r.isFailure)
        val ex = r.exceptionOrNull()
        assertNotNull("failure should have exception", ex)
        assertTrue(
            "exception should be IllegalStateException: ${ex!!::class}",
            ex is IllegalStateException,
        )
        assertTrue("error message should contain failureMsg: ${ex.message}",
            ex.message!!.contains("client socket missing"))
        assertTrue("state should be Closed", conn.state.value is ConnectionState.Closed)
        val closed = conn.state.value as ConnectionState.Closed
        assertNotNull("Closed should have error", closed.error)
        assertTrue("Closed error should be IoFailure: ${closed.error!!::class}",
            closed.error is ConnectionError.IoFailure)
        assertEquals(false, conn.attachedCallbackInvoked)
    }

    @Test
    fun `finishAttach consistent invariant - result and state always agree`() = runBlocking {
        // 反模式检测: 之前实现里, onSuccess { transitionTo(Closed) } 会导致 result=success
        // 但 state=Closed, 这是反 invariant 的行为。
        // 现在 finishAttach 保证 result 和 state 永远一致。
        val cases = listOf(
            true to ConnectionState.Attached(pid = 1, jdwpSessionId = 2),
            false to null,  // 期望 Closed, 后面再校验
        )
        for ((ok, expectedAttached) in cases) {
            val conn = TestConn()
            val info = AttachInfo(pid = 1, jdwpSessionId = 2, jdwpDescription = "x")
            val r = conn.callFinishAttach(info, ok = ok)
            if (ok) {
                assertTrue("ok=true should yield success", r.isSuccess)
                assertEquals(expectedAttached, conn.state.value)
            } else {
                assertTrue("ok=false should yield failure", r.isFailure)
                assertTrue("ok=false should yield Closed state",
                    conn.state.value is ConnectionState.Closed)
            }
        }
    }
}
