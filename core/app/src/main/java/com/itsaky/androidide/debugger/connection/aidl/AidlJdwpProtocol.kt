/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  AidlJdwpProtocol: AIDL+Socket 方案用到的 JDWP 协议层小工具。
 *
 *  这里不放完整的 JDWP 协议栈(那在 ide-debugger 那边),只做子项目 2
 *  特有的两件事:
 *    1) 14 字节 "JDWP-Handshake" 的写入/读取/校验
 *    2) VM.Version 命令包构造 + 响应包解析 (拿 vmId + jdwpVersion)
 *
 *  这两件事之所以单独抽出来,是因为它们是 AIDL+Socket 方案的"协议
 *  握手层",与具体传输 (ServerSocket vs LocalServerSocket vs ...)
 *  解耦,便于:
 *    - 单测时直接喂 ServerSocket 跑握手 + VM.Version
 *    - 未来切到 LocalServerSocket 时复用这一层
 */

package com.itsaky.androidide.debugger.connection.aidl

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * JDWP 协议常量 + 握手/VM.Version 助手。
 *
 * 参考: https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp-spec.html
 *
 *  - 14 字节 ASCII 字符串 "JDWP-Handshake" 双向交换
 *  - VM.Version = CommandSet(1) / Command(1), 响应包含 description /
 *    jdwpMajor / jdwpMinor / vmVersion / vmName
 */
object AidlJdwpProtocol {

    /** JDWP 握手字符串,固定 14 字节 ASCII。 */
    const val HANDSHAKE: String = "JDWP-Handshake"

    /** 握手字节数组 (缓存,避免每次重新 encode)。 */
    val HANDSHAKE_BYTES: ByteArray = HANDSHAKE.toByteArray(StandardCharsets.US_ASCII)

    /** VirtualMachine CommandSet ID (JDWP 规范定义)。 */
    const val COMMAND_SET_VM: Byte = 1

    /** VM.Version Command ID。 */
    const val COMMAND_VM_VERSION: Byte = 1

    /**
     * VM.Dispose Command ID。
     *
     * 历史 bug: 之前 5 处 detach() 调 `buildVmVersionCommand(0)` 然后改
     * `cmd[10] = 2 // VM.Dispose`, 但 commandSet=VirtualMachine(=1) 下:
     *   - 1 = Version
     *   - 2 = ClassesBySignature    (这个被错误当作 Dispose)
     *   - 6 = Dispose               (实际正确的 Dispose)
     *   - 10 = Exit
     *
     * 也就是说旧代码发的实际是 ClassesBySignature, 不是 Dispose。VM 通常会
     * 把它当成未知/无返回命令忽略掉, 所以表面看"detach 没崩", 但语义错。
     * 改用专用 builder, 避免改字节。
     */
    const val COMMAND_VM_DISPOSE: Byte = 6

    /** VM.Version 响应中字符串字段的固定头部: 4 字节 length + utf-8 bytes。 */
    private const val JDWP_STRING_HEADER_SIZE = 4

    /**
     * 写入握手字符串到 socket 输出流。写完不 flush,留给调用方在
     * 合适的时机(通常是同方法里发完下一组数据)统一 flush。
     */
    @Throws(IOException::class)
    fun writeHandshake(out: DataOutputStream) {
        out.write(HANDSHAKE_BYTES)
    }

    /**
     * 从 socket 输入流读 14 字节并校验是否等于 [HANDSHAKE]。
     * 失败抛 IOException("Bad handshake response") 或 EOF 异常。
     */
    @Throws(IOException::class)
    fun readAndVerifyHandshake(input: DataInputStream) {
        val expected = ByteArray(HANDSHAKE_BYTES.size)
        input.readFully(expected)
        for (i in HANDSHAKE_BYTES.indices) {
            if (expected[i] != HANDSHAKE_BYTES[i]) {
                throw IOException("Bad handshake response: byte $i = ${expected[i].toInt()} (expected ${HANDSHAKE_BYTES[i].toInt()})")
            }
        }
    }

    /**
     * 构造一个 VM.Version 命令包 (raw 字节),包头:
     *   [length:int32][id:int32][flags:uint8][commandSet:uint8][command:uint8]
     * 长度字段是"包头之后的数据长度",对 VM.Version 来说就是 0。
     *
     * @param id 命令 id,任意大于 0 的整数;调用方自己负责唯一
     */
    fun buildVmVersionCommand(id: Int): ByteArray {
        // length(4) + id(4) + flags(1) + commandSet(1) + command(1) = 11
        // 数据区为 0 字节,所以 length = 11
        val out = ByteArray(11)
        out[0] = 0
        out[1] = 0
        out[2] = 0
        out[3] = 11 // length = 11 (header only)
        out[4] = ((id ushr 24) and 0xff).toByte()
        out[5] = ((id ushr 16) and 0xff).toByte()
        out[6] = ((id ushr 8) and 0xff).toByte()
        out[7] = (id and 0xff).toByte()
        out[8] = 0x00 // FLAG_COMMAND
        out[9] = COMMAND_SET_VM
        out[10] = COMMAND_VM_VERSION
        return out
    }

    /**
     * 构造一个 VM.Dispose 命令包 (raw 字节)。
     *
     * 与 [buildVmVersionCommand] 同结构, 但 command 字节是 [COMMAND_VM_DISPOSE]
     * (=6), data 字段同样为空 (length = 11)。
     *
     * 用途: detach() 时发给 VM, 让 host 端 JDWP session 主动关闭
     * (`VirtualMachine.Dispose` 在 spec 里描述为 "invalidates this virtual
     * machine ID in the target VM"; 不发这个 detach 时只能靠 socket close
     * 触发 VM 端的 EOF 处理, 但 VM 内部状态可能未释放)。
     *
     * @param id 命令 id, 跟 VM.Version 一组用, 便于在 log 里识别
     */
    fun buildVmDisposeCommand(id: Int): ByteArray {
        val out = ByteArray(11)
        out[0] = 0
        out[1] = 0
        out[2] = 0
        out[3] = 11 // length = 11 (header only)
        out[4] = ((id ushr 24) and 0xff).toByte()
        out[5] = ((id ushr 16) and 0xff).toByte()
        out[6] = ((id ushr 8) and 0xff).toByte()
        out[7] = (id and 0xff).toByte()
        out[8] = 0x00 // FLAG_COMMAND
        out[9] = COMMAND_SET_VM
        out[10] = COMMAND_VM_DISPOSE
        return out
    }

    /**
     * 解析 VM.Version 响应包 (含 11 字节 header),返回 description /
     * jdwpMajor / jdwpMinor / vmVersion / vmName。
     *
     * <p>协议: 先 2 字节 errorCode (这里必须为 0),接着:
     * <ul>
     *   <li>string description (4 字节 length + utf-8 bytes)</li>
     *   <li>int jdwpMajor (4 字节)</li>
     *   <li>int jdwpMinor (4 字节)</li>
     *   <li>string vmVersion</li>
     *   <li>string vmName</li>
     * </ul>
     */
    @Throws(IOException::class)
    fun parseVmVersionReply(packetBytes: ByteArray): VmVersionInfo {
        if (packetBytes.size < 11) {
            throw IOException("VM.Version reply too short: ${packetBytes.size} bytes")
        }
        val flags = packetBytes[8].toInt() and 0xff
        if (flags != 0x80) {
            throw IOException("VM.Version reply: flags=$flags (expected 0x80 reply flag)")
        }
        // [0..3] length, [4..7] id, [8] flags, [9] cmdSet, [10] cmd
        // data 区域: errorCode(2) + payload
        if (packetBytes.size < 13) {
            throw IOException("VM.Version reply: missing errorCode")
        }
        val errorCode = ((packetBytes[11].toInt() and 0xff) shl 8) or
                (packetBytes[12].toInt() and 0xff)
        if (errorCode != 0) {
            throw IOException("VM.Version reply: errorCode=$errorCode")
        }
        var cursor = 13
        val description = readJdwpString(packetBytes, cursor).also { cursor = it.nextCursor }
        val jdwpMajor = readInt(packetBytes, cursor); cursor += 4
        val jdwpMinor = readInt(packetBytes, cursor); cursor += 4
        val vmVersion = readJdwpString(packetBytes, cursor).also { cursor = it.nextCursor }
        val vmName = readJdwpString(packetBytes, cursor)
        return VmVersionInfo(
            description = description.value,
            jdwpMajor = jdwpMajor,
            jdwpMinor = jdwpMinor,
            vmVersion = vmVersion.value,
            vmName = vmName.value,
        )
    }

    /**
     * 一次性的"握手 + VM.Version"流程: 已经 accept()/connect() 完毕
     * 拿到 [socket],用 [commandId] 作为 VM.Version 命令 id (任意 > 0)。
     *
     * 顺序: 写 handshake -> 读 handshake -> 写 VM.Version -> 读 VM.Version 响应包
     *
     * @return 解析后的 [VmVersionInfo] (含 jdwpMajor/jdwpMinor 描述)
     */
    @Throws(IOException::class)
    fun performHandshakeAndVersionProbe(
        socket: Socket,
        commandId: Int = 1,
    ): VmVersionInfo = performHandshakeAndVersionProbe(
        output = socket.getOutputStream(),
        input = socket.getInputStream(),
        commandId = commandId,
    )

    /**
     * 子项目 9e: 重载, 接受 InputStream/OutputStream, 供 LocalSocket (不继承
     * java.net.Socket) 等场景使用。
     */
    @Throws(IOException::class)
    fun performHandshakeAndVersionProbe(
        output: java.io.OutputStream,
        input: java.io.InputStream,
        commandId: Int = 1,
    ): VmVersionInfo {
        val out = DataOutputStream(output)
        val ins = DataInputStream(input)
        // 1. 写 handshake
        writeHandshake(out)
        out.flush()
        // 2. 读 handshake 校验
        readAndVerifyHandshake(ins)
        // 3. 写 VM.Version
        out.write(buildVmVersionCommand(commandId))
        out.flush()
        // 4. 读响应包
        val header = ByteArray(11)
        ins.readFully(header)
        val payloadLength = readInt(header, 0)
        val payload = ByteArray(payloadLength)
        if (payloadLength > 0) {
            ins.readFully(payload)
        }
        val fullPacket = ByteArray(11 + payloadLength)
        System.arraycopy(header, 0, fullPacket, 0, 11)
        System.arraycopy(payload, 0, fullPacket, 11, payloadLength)
        return parseVmVersionReply(fullPacket)
    }

    // ----- 内部小工具 -----

    private data class CursorValue(val value: String, val nextCursor: Int)

    private fun readJdwpString(buf: ByteArray, offset: Int): CursorValue {
        val len = readInt(buf, offset)
        val start = offset + JDWP_STRING_HEADER_SIZE
        if (len < 0 || start + len > buf.size) {
            throw IOException("Bad JDWP string: len=$len offset=$offset bufSize=${buf.size}")
        }
        val s = String(buf, start, len, StandardCharsets.UTF_8)
        return CursorValue(s, start + len)
    }

    private fun readInt(buf: ByteArray, offset: Int): Int {
        if (offset + 4 > buf.size) {
            throw IOException("readInt: out of bounds at offset=$offset (bufSize=${buf.size})")
        }
        return ((buf[offset].toInt() and 0xff) shl 24) or
                ((buf[offset + 1].toInt() and 0xff) shl 16) or
                ((buf[offset + 2].toInt() and 0xff) shl 8) or
                (buf[offset + 3].toInt() and 0xff)
    }

    data class VmVersionInfo(
        val description: String,
        val jdwpMajor: Int,
        val jdwpMinor: Int,
        val vmVersion: String,
        val vmName: String,
    ) {
        val jdwpVersion: String get() = "$jdwpMajor.$jdwpMinor"
    }
}
