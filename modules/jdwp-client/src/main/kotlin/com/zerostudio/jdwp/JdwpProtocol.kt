package com.zerostudio.jdwp

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

interface JdwpTransport : AutoCloseable {
  fun connect()
  fun readBytes(size: Int): ByteArray
  fun writePacket(packet: ByteArray)
}

class SocketJdwpTransport(private val host: String, private val port: Int, private val timeoutMs: Int = 5_000) : JdwpTransport {
  private lateinit var socket: Socket
  private lateinit var input: InputStream
  private lateinit var output: OutputStream

  override fun connect() {
    socket = Socket(host, port).also { it.soTimeout = timeoutMs }
    input = socket.getInputStream()
    output = socket.getOutputStream()
  }

  override fun readBytes(size: Int): ByteArray {
    val buf = ByteArray(size)
    var off = 0
    while (off < size) {
      val read = input.read(buf, off, size - off)
      if (read == -1) throw EOFException("JDWP stream closed")
      off += read
    }
    return buf
  }

  override fun writePacket(packet: ByteArray) { output.write(packet); output.flush() }
  override fun close() { if (this::socket.isInitialized) socket.close() }
}

class JdwpClient(private val transport: JdwpTransport) : AutoCloseable {
  private val sequence = AtomicInteger(1)
  private val running = AtomicBoolean(false)
  private val eventExecutor = Executors.newSingleThreadExecutor()
  private val pending = ConcurrentHashMap<Int, java.util.concurrent.CompletableFuture<JdwpReplyPacket>>()
  @Volatile private var eventListener: ((JdwpEventPacket) -> Unit)? = null

  fun connectAndHandshake() {
    transport.connect()
    transport.writePacket(JdwpConstants.HANDSHAKE.toByteArray(Charsets.US_ASCII))
    val response = transport.readBytes(JdwpConstants.HANDSHAKE.length)
    require(response.toString(Charsets.US_ASCII) == JdwpConstants.HANDSHAKE) { "JDWP handshake failed" }
    startPump()
  }

  fun setEventListener(listener: (JdwpEventPacket) -> Unit) { eventListener = listener }

  fun vmVersion(): VmVersionReply {
    val reply = sendCommand(1u, 1u)
    val r = JdwpBuffer.wrap(reply.payload)
    return VmVersionReply(r.readUtf8(), r.readInt(), r.readInt(), r.readUtf8(), r.readUtf8())
  }

  fun allThreads(): List<ThreadId> {
    val reply = sendCommand(1u, 4u)
    val r = JdwpBuffer.wrap(reply.payload)
    return (0 until r.readInt()).map { ThreadId(r.readLong()) }
  }

  fun threadInfo(threadId: ThreadId): ThreadInfo {
    val nameReply = sendCommand(11u, 1u, JdwpBuffer.allocate(8).putLong(threadId.raw).toByteArray())
    val statusReply = sendCommand(11u, 4u, JdwpBuffer.allocate(8).putLong(threadId.raw).toByteArray())
    val n = JdwpBuffer.wrap(nameReply.payload).readUtf8()
    val s = JdwpBuffer.wrap(statusReply.payload)
    return ThreadInfo(n, s.readInt(), s.readInt())
  }

  fun classesBySignature(signature: String): List<ClassInfo> {
    val reply = sendCommand(1u, 2u, JdwpBuffer.allocate(signature.toByteArray().size + 4).putUtf8(signature).toByteArray())
    val r = JdwpBuffer.wrap(reply.payload)
    return (0 until r.readInt()).map { ClassInfo(r.readByte(), ReferenceTypeId(r.readLong()), r.readInt()) }
  }

  fun methods(typeId: ReferenceTypeId): List<MethodInfo> {
    val reply = sendCommand(2u, 5u, JdwpBuffer.allocate(8).putLong(typeId.raw).toByteArray())
    val r = JdwpBuffer.wrap(reply.payload)
    return (0 until r.readInt()).map { MethodInfo(MethodId(r.readLong()), r.readUtf8(), r.readUtf8(), r.readInt()) }
  }

  fun fields(typeId: ReferenceTypeId): List<FieldInfo> {
    val reply = sendCommand(2u, 4u, JdwpBuffer.allocate(8).putLong(typeId.raw).toByteArray())
    val r = JdwpBuffer.wrap(reply.payload)
    return (0 until r.readInt()).map { FieldInfo(FieldId(r.readLong()), r.readUtf8(), r.readUtf8(), r.readInt()) }
  }

  fun objectReferenceType(objectId: ObjectId): ObjectReferenceInfo {
    val reply = sendCommand(9u, 1u, JdwpBuffer.allocate(8).putLong(objectId.raw).toByteArray())
    val r = JdwpBuffer.wrap(reply.payload)
    return ObjectReferenceInfo(r.readByte(), ReferenceTypeId(r.readLong()))
  }

  fun getObjectValues(objectId: ObjectId, fieldIds: List<FieldId>): List<TaggedValue> {
    val payload = JdwpBuffer.allocate(8 + 4 + fieldIds.size * 8).putLong(objectId.raw).putInt(fieldIds.size).apply {
      fieldIds.forEach { putLong(it.raw) }
    }.toByteArray()
    val reply = sendCommand(9u, 2u, payload)
    val r = JdwpBuffer.wrap(reply.payload)
    return (0 until r.readInt()).map { TaggedValue(r.readByte(), r.readLong()) }
  }

  fun suspendVm() { sendCommand(1u, 8u) }
  fun resumeVm() { sendCommand(1u, 9u) }

  fun setVmDeathEvent(suspendPolicy: JdwpSuspendPolicy = JdwpSuspendPolicy.NONE): EventRequestId {
    val payload = JdwpBuffer.allocate(1 + 1 + 4)
      .putByte(JdwpEventKind.VM_DEATH.code)
      .putByte(suspendPolicy.code)
      .putInt(0)
      .toByteArray()
    val reply = sendCommand(15u, 1u, payload)
    return EventRequestId(JdwpBuffer.wrap(reply.payload).readInt())
  }

  fun clearEvent(eventKind: JdwpEventKind, requestId: EventRequestId) {
    val payload = JdwpBuffer.allocate(1 + 4).putByte(eventKind.code).putInt(requestId.raw).toByteArray()
    sendCommand(15u, 2u, payload)
  }

  fun sendCommand(commandSet: UByte, command: UByte, payload: ByteArray = byteArrayOf()): JdwpReplyPacket {
    val id = sequence.getAndIncrement()
    val future = java.util.concurrent.CompletableFuture<JdwpReplyPacket>()
    pending[id] = future
    transport.writePacket(JdwpCodec.encodeCommand(JdwpCommandPacket(id, commandSet, command, payload)))
    val reply = future.get()
    JdwpErrors.throwIfError(reply.errorCode)
    return reply
  }

  private fun startPump() {
    if (!running.compareAndSet(false, true)) return
    eventExecutor.execute {
      while (running.get()) {
        val packet = JdwpCodec.readPacket(transport)
        when (packet) {
          is JdwpReplyPacket -> pending.remove(packet.id)?.complete(packet)
          is JdwpEventPacket -> eventListener?.invoke(packet)
        }
      }
    }
  }

  override fun close() {
    running.set(false)
    eventExecutor.shutdownNow()
    transport.close()
  }
}

object JdwpCodec {
  fun encodeCommand(packet: JdwpCommandPacket): ByteArray {
    val totalLength = 11 + packet.payload.size
    return JdwpBuffer.allocate(totalLength)
      .putInt(totalLength)
      .putInt(packet.id)
      .putByte(0)
      .putByte(packet.commandSet.toByte())
      .putByte(packet.command.toByte())
      .putBytes(packet.payload)
      .toByteArray()
  }

  fun readPacket(transport: JdwpTransport): Any {
    val header = transport.readBytes(11)
    val h = JdwpBuffer.wrap(header)
    val len = h.readInt()
    val id = h.readInt()
    val flags = h.readByte().toUByte()
    val rest = transport.readBytes(len - 11)
    return if ((flags.toInt() and JdwpConstants.REPLY_FLAG.toInt()) != 0) {
      val e = JdwpBuffer.wrap(header.copyOfRange(9, 11)).readShort().toUShort()
      JdwpReplyPacket(id, e, rest)
    } else {
      JdwpEventPacket(id, header[9].toUByte(), header[10].toUByte(), rest)
    }
  }
}
