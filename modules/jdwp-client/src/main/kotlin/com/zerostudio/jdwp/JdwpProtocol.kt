package com.zerostudio.jdwp

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface JdwpTransport : AutoCloseable {
  fun connect()
  fun readPacket(): ByteArray
  fun writePacket(packet: ByteArray)
}

class SocketJdwpTransport(
  private val host: String,
  private val port: Int,
  private val timeoutMs: Int = 5_000,
) : JdwpTransport {
  private lateinit var socket: Socket
  private lateinit var input: InputStream
  private lateinit var output: OutputStream

  override fun connect() {
    socket = Socket(host, port).also { it.soTimeout = timeoutMs }
    input = socket.getInputStream()
    output = socket.getOutputStream()
  }

  override fun readPacket(): ByteArray {
    val header = ByteArray(11)
    readFully(input, header)
    val len = JdwpBuffer.wrap(header).readInt()
    val payload = ByteArray(len - 11)
    readFully(input, payload)
    return header + payload
  }

  override fun writePacket(packet: ByteArray) {
    output.write(packet)
    output.flush()
  }

  override fun close() {
    if (this::socket.isInitialized) socket.close()
  }

  private fun readFully(input: InputStream, buf: ByteArray) {
    var off = 0
    while (off < buf.size) {
      val read = input.read(buf, off, buf.size - off)
      if (read == -1) throw EOFException("JDWP stream closed")
      off += read
    }
  }
}

class JdwpClient(private val transport: JdwpTransport) : AutoCloseable {
  private val sequence = AtomicInteger(1)
  private val pendingReplies = ConcurrentHashMap<Int, JdwpReplyPacket>()

  fun connectAndHandshake() {
    transport.connect()
    transport.writePacket(JdwpConstants.HANDSHAKE.toByteArray(Charsets.US_ASCII))
    val response = transport.readPacket()
    val text = response.toString(Charsets.US_ASCII)
    require(text == JdwpConstants.HANDSHAKE) { "JDWP handshake failed: $text" }
  }

  fun sendCommand(commandSet: UByte, command: UByte, payload: ByteArray = byteArrayOf()): JdwpReplyPacket {
    val id = sequence.getAndIncrement()
    val packet = JdwpCodec.encodeCommand(JdwpCommandPacket(id, commandSet, command, payload))
    transport.writePacket(packet)
    val reply = JdwpCodec.decodeReply(transport.readPacket())
    pendingReplies[id] = reply
    return reply
  }

  fun vmVersion(): VmVersionReply {
    val reply = sendCommand(1u, 1u)
    JdwpErrors.throwIfError(reply.errorCode)
    val r = JdwpBuffer.wrap(reply.payload)
    return VmVersionReply(r.readUtf8(), r.readInt(), r.readInt(), r.readUtf8(), r.readUtf8())
  }

  fun classesBySignature(signature: String): List<ClassInfo> {
    val payload = JdwpBuffer.allocate(signature.length + 8).putUtf8(signature).toByteArray()
    val reply = sendCommand(1u, 2u, payload)
    JdwpErrors.throwIfError(reply.errorCode)
    val reader = JdwpBuffer.wrap(reply.payload)
    val count = reader.readInt()
    return (0 until count).map {
      ClassInfo(reader.readByte(), ReferenceTypeId(reader.readLong()), reader.readInt())
    }
  }

  fun methods(typeId: ReferenceTypeId): List<MethodInfo> {
    val reply = sendCommand(2u, 5u, JdwpBuffer.allocate(8).putLong(typeId.raw).toByteArray())
    JdwpErrors.throwIfError(reply.errorCode)
    val r = JdwpBuffer.wrap(reply.payload)
    val count = r.readInt()
    return (0 until count).map {
      MethodInfo(MethodId(r.readLong()), r.readUtf8(), r.readUtf8(), r.readInt())
    }
  }

  fun fields(typeId: ReferenceTypeId): List<FieldInfo> {
    val reply = sendCommand(2u, 4u, JdwpBuffer.allocate(8).putLong(typeId.raw).toByteArray())
    JdwpErrors.throwIfError(reply.errorCode)
    val r = JdwpBuffer.wrap(reply.payload)
    val count = r.readInt()
    return (0 until count).map {
      FieldInfo(FieldId(r.readLong()), r.readUtf8(), r.readUtf8(), r.readInt())
    }
  }

  override fun close() = transport.close()
}

object JdwpCodec {
  fun encodeCommand(packet: JdwpCommandPacket): ByteArray {
    val totalLength = 11 + packet.payload.size
    val out = JdwpBuffer.allocate(totalLength)
    out.putInt(totalLength)
      .putInt(packet.id)
      .putByte(0)
      .putByte(packet.commandSet.toByte())
      .putByte(packet.command.toByte())
    packet.payload.forEach(out::putByte)
    return out.toByteArray()
  }

  fun decodeReply(bytes: ByteArray): JdwpReplyPacket {
    val r = JdwpBuffer.wrap(bytes)
    r.readInt()
    val id = r.readInt()
    r.readByte()
    val error = ((r.readByte().toInt() and 0xFF) shl 8 or (r.readByte().toInt() and 0xFF)).toUShort()
    val payload = ByteArray(bytes.size - 11)
    System.arraycopy(bytes, 11, payload, 0, payload.size)
    return JdwpReplyPacket(id, error, payload)
  }
}
