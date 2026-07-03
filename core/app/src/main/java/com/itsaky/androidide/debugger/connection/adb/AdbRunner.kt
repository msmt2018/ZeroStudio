/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  AdbRunner: 抽象 `adb` 命令执行, 供 InnetVmAdb (子项目 6) + UsbLan (子项目 7)
 *  共享。两种方案都需要调用 `adb connect / forward / shell`, 但不希望在 IDE 端
 *  直接拼字符串 fork exec; 抽成接口便于:
 *    1) 注入 fake: 单元测试时用 FakeAdbRunner 预置 stdout / exit code
 *    2) 多种实现: host 端走 ADB binary (Runtime.exec) / Shizuku 调用 (binder) /
 *       Root (su -c) - 同一份上层逻辑
 *    3) 后续切到 ADB over network / ADB over USB transport 时只换实现
 *
 *  设计要点:
 *    - 同步阻塞, 调用方自己包 withContext(Dispatchers.IO)
 *    - 走 ProcessRunner 公共实现 (Phase 13h, 替代直接 ProcessBuilder + 3 daemon
 *      thread + CountDownLatch + destroyForcibly, 跟 DefaultRootClient 共享)
 *    - 不假设 adb 路径: getAdbBinaryPath() 抽象出来, IDE 端默认返回
 *      打包进 assets 的 platform-tools/adb
 */

package com.itsaky.androidide.debugger.connection.adb

import com.itsaky.androidide.debugger.connection.process.ProcessResult
import com.itsaky.androidide.debugger.connection.process.ProcessRunner
import com.itsaky.androidide.utils.ILogger
import java.io.IOException

/**
 * ADB 命令执行结果。
 * @param exitCode 进程退出码
 * @param stdout 标准输出
 * @param stderr 标准错误 (合并到 stdout 时为 "")
 */
data class AdbResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String = "",
) {
    val isSuccess: Boolean get() = exitCode == 0

    /**
     * Phase 13h: 从 [ProcessResult] 适配 (ProcessRunner 是 AdbRunner / RootClient
     * 共享的底层, AdbResult 是 AdbRunner 的语义化包装)。
     */
    internal companion object {
        internal fun fromProcessResult(r: ProcessResult) = AdbResult(
            exitCode = r.exitCode, stdout = r.stdout, stderr = r.stderr,
        )
    }
}

/**
 * ADB runner 抽象。线程安全: 同一实例可被多协程调用。
 */
interface AdbRunner {

    /**
     * 跑一条 adb 命令, 等待完成或超时。
     *
     * @param args adb 子命令参数 (不含 `adb` 本身, 不含 -s serial 时拼到第一项)
     * @param timeoutMs 超时 (毫秒)
     * @return [AdbResult] 含 exit code / stdout / stderr
     * @throws IOException 启动失败 / 超时
     */
    @Throws(IOException::class)
    fun run(args: List<String>, timeoutMs: Long): AdbResult

    /**
     * 跑一条 adb 命令, 序列化到目标 serial (多设备场景)。
     * 默认实现: 拼 "-s $serial" 到 args 头部后调用 run(args, timeoutMs)。
     */
    @Throws(IOException::class)
    fun runOnSerial(serial: String, args: List<String>, timeoutMs: Long): AdbResult {
        return run(listOf("-s", serial) + args, timeoutMs)
    }

    /**
     * ADB binary 路径, IDE 端默认打包 platform-tools/adb。
     * DefaultAdbRunner 通过 ANDROID_ADB_PATH / IDE_ADB_PATH / 兜底
     * 查找; 其他实现可忽略。
     */
    fun getAdbBinaryPath(): String

    companion object {
        @JvmStatic
        fun create(): AdbRunner = DefaultAdbRunner()
    }
}

/**
 * 默认生产实现: 走 [ProcessRunner] 调 adb binary (Phase 13h 之前是
 * 直接 ProcessBuilder + 3 daemon thread + CountDownLatch, 跟 DefaultRootClient
 * 双份维护, 抽到 ProcessRunner 统一)。
 *
 *  - adb binary 路径按顺序尝试:
 *    1) ANDROID_ADB_PATH env
 *    2) IDE_ADB_PATH env
 *    3) 兜底 "/data/data/com.itsaky.androidide/files/usr/bin/adb"
 *       (跟 CxxConfigProvider / 既有 ADB 调用保持一致)
 *
 *  - 不打 adb-server: connect / forward 命令各自起 adb client, 让 adb server
 *    自动 spawn。如果 adb server 没启动, adb client 会自己启。
 */
class DefaultAdbRunner(
    private val processRunner: ProcessRunner = ProcessRunner(),
) : AdbRunner {

    private val log = ILogger.ROOT  // ILogger.ROOT 保留, processRunner 内部 log 走它

    @Volatile private var cachedPath: String? = null

    override fun getAdbBinaryPath(): String {
        cachedPath?.let { return it }
        val candidates = listOf(
            System.getenv("ANDROID_ADB_PATH"),
            System.getenv("IDE_ADB_PATH"),
            "/data/data/com.itsaky.androidide/files/usr/bin/adb",
            "/system/bin/adb",
        )
        for (p in candidates) {
            if (p != null && p.isNotBlank()) {
                cachedPath = p
                return p
            }
        }
        // 实在没有就假定 PATH 里有 adb
        cachedPath = "adb"
        return "adb"
    }

    @Throws(IOException::class)
    override fun run(args: List<String>, timeoutMs: Long): AdbResult {
        val binary = getAdbBinaryPath()
        val cmd = listOf(binary) + args
        val result = processRunner.run(cmd, timeoutMs, redirectErrorStream = false)
        log.debug("adb {} -> exit={}, stdout={}, stderr={}", args, result.exitCode,
            result.stdout.trim().take(120), result.stderr.trim().take(120))
        return AdbResult.fromProcessResult(result)
    }
}

/**
 * 测试用 fake: 可预置一组 (args 匹配规则 -> AdbResult) 响应。
 *
 * 用法:
 * ```
 *   val fake = FakeAdbRunner().apply {
 *       respond("connect".contained()) { AdbResult(0, "connected to 127.0.0.1:5555") }
 *       respond("forward".contained()) { AdbResult(0, "") }
 *       respond("pidof".contained()) { AdbResult(0, "12345") }
 *   }
 * ```
 *
 * 没匹配到规则时返回 AdbResult(1, "", "no fake response configured")。
 */
class FakeAdbRunner : AdbRunner {

    private data class Rule(
        val matcher: (List<String>) -> Boolean,
        val producer: (List<String>) -> AdbResult,
    )

    private val rules: MutableList<Rule> = mutableListOf()
    private val calls: MutableList<List<String>> = mutableListOf()

    val callCount: Int get() = calls.size
    val callHistory: List<List<String>> get() = calls.toList()

    fun respond(matcher: (List<String>) -> Boolean, producer: (List<String>) -> AdbResult): FakeAdbRunner {
        rules.add(Rule(matcher, producer))
        return this
    }

    override fun run(args: List<String>, timeoutMs: Long): AdbResult {
        synchronized(calls) { calls.add(args.toList()) }
        for (rule in rules) {
            if (rule.matcher(args)) return rule.producer(args)
        }
        return AdbResult(
            exitCode = 1,
            stdout = "",
            stderr = "FakeAdbRunner: no fake response for $args",
        )
    }

    override fun runOnSerial(serial: String, args: List<String>, timeoutMs: Long): AdbResult {
        return run(listOf("-s", serial) + args, timeoutMs)
    }

    override fun getAdbBinaryPath(): String = "/fake/adb"

    companion object {
        /** 简单 matcher: args 拼成字符串后 contains 关键字。 */
        fun contains(keyword: String): (List<String>) -> Boolean = { args ->
            args.joinToString(" ").contains(keyword)
        }
        /** matcher: 严格匹配完整 args。 */
        fun equalsTo(expected: List<String>): (List<String>) -> Boolean = { args ->
            args == expected
        }
    }
}
