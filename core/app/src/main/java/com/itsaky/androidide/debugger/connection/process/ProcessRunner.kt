/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  ProcessRunner: 走 `ProcessBuilder` 跑外部 binary (adb / su -c / socat / 等)
 *  的公共实现。抽这个类的原因:
 *
 *    - AdbRunner.run (DefaultAdbRunner) 跟 DefaultRootClient.execWithTimeout 是
 *      同款 ProcessBuilder + daemon thread + CountDownLatch + timeout +
 *      destroyForcibly 模式, 抽公共避免双份维护
 *    - stderr / stdout 分离 (AdbRunner) vs stderr 合 stdout (RootClient 找 pid)
 *      走 redirectErrorStream 参数控制
 *    - daemon thread + isDaemon = true 跟 host app 进程生命周期对齐 (host 端
 *      thread 退出时 JVM 不会被 block)
 *
 *  限制:
 *    - 同步阻塞, 调用方自己包 withContext(Dispatchers.IO)
 *    - 不支持交互式 stdin 写入 (e.g. su -c 拿 socat stdin/stdout 用 RootJdwpStream,
 *      那种长生命周期 stream 不走 ProcessRunner.run, 自己 ProcessBuilder.start()
 *      + thread drain 即可)
 */

package com.itsaky.androidide.debugger.connection.process

import com.itsaky.androidide.utils.ILogger
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 进程执行结果。
 *
 * @param exitCode 进程退出码 (destroyForcibly 后 waitFor 真退出才有, 否则 137)
 * @param stdout 标准输出
 * @param stderr 标准错误 (redirectErrorStream = true 时为 "")
 */
data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String = "",
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * 公共进程运行器。线程安全: 同一实例可被多协程调用。
 */
class ProcessRunner {

    private val log = ILogger.ROOT

    /**
     * 跑一条命令, 同步等完成或超时。
     *
     * @param cmd 完整命令 (含 binary 路径, e.g. `["/path/to/adb", "connect", "127.0.0.1:5555"]`
     *            或 `["su", "-c", "pidof com.example"]`)
     * @param timeoutMs 超时 (毫秒), 超时走 destroyForcibly + 抛 IOException
     * @param redirectErrorStream true: stderr 合 stdout (适合"跑命令拿结果"场景, e.g.
     *                           su -c 'pidof pkg'; false: stderr 独立 (适合"长生命周期
     *                           stream" 场景, e.g. su -c 'socat - UNIX-CONNECT:...').
     *                           默认 false (跟 AdbRunner 行为一致)。
     * @return [ProcessResult]
     * @throws IOException 启动失败 / 超时 / 读 stdout 失败
     */
    @Throws(IOException::class)
    fun run(
        cmd: List<String>,
        timeoutMs: Long,
        redirectErrorStream: Boolean = false,
    ): ProcessResult {
        require(cmd.isNotEmpty()) { "cmd must not be empty" }
        val proc = try {
            ProcessBuilder(cmd).redirectErrorStream(redirectErrorStream).start()
        } catch (t: Throwable) {
            throw IOException("ProcessRunner: failed to start ${cmd[0]}: ${t.message}", t)
        }
        return try {
            readWithTimeout(proc, cmd, timeoutMs)
        } catch (t: Throwable) {
            // 失败兜底: 进程 destroy 避免 zombie
            runCatching { proc.destroyForcibly() }
            throw t
        }
    }

    /**
     * 长生命周期进程执行: 启动后立刻返 proc, 不等 exit。调用方负责 close。
     *
     * 跟 [run] 的区别: run 是"跑完拿结果", 这个是"启动 daemon 进程拿流"。
     * 用例: RootClient.openJdwpStream 启 `su -c 'socat - ...'` 拿 stdin/stdout
     * 当 JDWP 字节流, 进程由 RootConnection 生命周期控制。
     *
     * @param cmd 完整命令
     * @return [LiveProcess] 含 inputStream / outputStream / errorStream + close hook
     */
    fun startLive(cmd: List<String>): LiveProcess {
        require(cmd.isNotEmpty()) { "cmd must not be empty" }
        val proc = try {
            ProcessBuilder(cmd).redirectErrorStream(false).start()
        } catch (t: Throwable) {
            throw IOException("ProcessRunner.startLive: failed to start ${cmd[0]}: ${t.message}", t)
        }
        // 起 daemon thread drain stderr 防 kernel pipe buffer 满 deadlock
        val errDrain = thread(name = "ProcessRunner-err-drain", isDaemon = true) {
            runCatching { proc.errorStream.readBytes() }
        }
        return LiveProcess(
            inputStream = proc.inputStream,
            outputStream = proc.outputStream,
            errorStream = proc.errorStream,
            onClose = {
                runCatching { proc.destroyForcibly() }
                runCatching {
                    proc.waitFor(2_000L, TimeUnit.MILLISECONDS)
                }
                runCatching { errDrain.join(500L) }
            },
        )
    }

    // ---- 私有 ----

    private fun readWithTimeout(
        proc: Process,
        cmd: List<String>,
        timeoutMs: Long,
    ): ProcessResult {
        val outBuf = arrayOfNulls<String>(1)
        val errBuf = arrayOfNulls<String>(1)
        val outErr = arrayOfNulls<Throwable>(1)
        val errErr = arrayOfNulls<Throwable>(1)
        val done = CountDownLatch(1)

        thread(name = "ProcessRunner-out", isDaemon = true) {
            try {
                outBuf[0] = proc.inputStream.readBytes().toString(Charsets.UTF_8)
            } catch (t: Throwable) {
                outErr[0] = t
            }
        }
        thread(name = "ProcessRunner-err", isDaemon = true) {
            try {
                errBuf[0] = proc.errorStream.readBytes().toString(Charsets.UTF_8)
            } catch (t: Throwable) {
                errErr[0] = t
            }
        }
        thread(name = "ProcessRunner-wait", isDaemon = true) {
            try {
                proc.waitFor()
            } catch (_: Throwable) { }
            done.countDown()
        }

        if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            runCatching { proc.destroyForcibly() }
            throw IOException("ProcessRunner: timeout (${timeoutMs}ms): ${cmd.joinToString(" ")}")
        }
        // out thread 失败: 抛 IOException 包, 跟 ProcessResult 协议一致
        outErr[0]?.let { throw IOException("ProcessRunner: read stdout failed: ${it.message}", it) }
        // err thread 失败: log warn 但不抛 (跟 AdbRunner 行为一致; 主流程靠 stdout + exit code 判定)
        errErr[0]?.let { log.warn("ProcessRunner: read stderr failed: {}", it.message) }

        val stdout = outBuf[0] ?: ""
        val stderr = errBuf[0] ?: ""
        log.debug(
            "ProcessRunner: {} -> exit={}, stdout={}, stderr={}",
            cmd.joinToString(" ").take(80),
            proc.exitValue(),
            stdout.trim().take(120),
            stderr.trim().take(120),
        )
        return ProcessResult(exitCode = proc.exitValue(), stdout = stdout, stderr = stderr)
    }
}

/**
 * 长生命周期进程封装: 暴露 input / output / error 流 + close hook。
 */
data class LiveProcess(
    val inputStream: java.io.InputStream,
    val outputStream: java.io.OutputStream,
    val errorStream: java.io.InputStream,
    val onClose: () -> Unit,
) : java.io.Closeable {
    override fun close() {
        runCatching { inputStream.close() }
        runCatching { outputStream.close() }
        runCatching { errorStream.close() }
        runCatching { onClose() }
    }
}
