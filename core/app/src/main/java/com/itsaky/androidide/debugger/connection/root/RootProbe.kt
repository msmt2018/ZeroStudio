/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  RootProbe: 探测设备是否 root + 探测 su 是否可用。
 *  RootClient: 走 su 起 root 进程做具体操作 (找 pid / open JDWP socket)。
 *
 *  设计原则:
 *    - 全部走 su -c, 不依赖 Magisk 模块, 兼容各种 su (Magisk / KernelSU / APatch / etc.)
 *    - 不主动 mount / 改 system, 走最小权限路径
 *    - 所有 su exec 走超时控制, 避免卡死
 */

package com.itsaky.androidide.debugger.connection.root

import com.itsaky.androidide.utils.ILogger
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Root 探测器: 跑 `su -c 'id'` 看是否成功拿到 root。
 */
interface RootProbe {
    fun probeHasRoot(suBin: String): Boolean

    companion object {
        @JvmStatic
        fun create(): RootProbe = DefaultRootProbe()
    }
}

class DefaultRootProbe : RootProbe {
    private val log = ILogger.ROOT

    override fun probeHasRoot(suBin: String): Boolean {
        return try {
            val p = ProcessBuilder(suBin, "-c", "id").redirectErrorStream(true).start()
            val out = p.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            val exit = p.waitFor()
            val ok = exit == 0 && out.contains("uid=0")
            log.debug("RootProbe: exit={}, stdout={}", exit, out.trim())
            ok
        } catch (t: Throwable) {
            log.debug("RootProbe: failed: {}", t.message)
            false
        }
    }
}

class FakeRootProbe(private val shouldSucceed: Boolean = true) : RootProbe {
    var probeCount: Int = 0
        private set
    override fun probeHasRoot(suBin: String): Boolean {
        probeCount++
        return shouldSucceed
    }
}
