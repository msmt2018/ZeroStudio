package com.zerostudio.jdwp

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

interface JdwpTransport : AutoCloseable { fun connect(); fun readBytes(size: Int): ByteArray; fun writePacket(packet: ByteArray) }
class SocketJdwpTransport(private val host: String, private val port: Int, private val timeoutMs: Int = 5_000) : JdwpTransport {
  private lateinit var socket: Socket; private lateinit var input: InputStream; private lateinit var output: OutputStream
  override fun connect() { socket = Socket(host, port).also { it.soTimeout = timeoutMs }; input = socket.getInputStream(); output = socket.getOutputStream() }
  override fun readBytes(size: Int): ByteArray { val b=ByteArray(size); var o=0; while (o<size){ val r=input.read(b,o,size-o); if(r==-1) throw EOFException("JDWP stream closed"); o+=r }; return b }
  override fun writePacket(packet: ByteArray) { output.write(packet); output.flush() }
  override fun close() { if (this::socket.isInitialized) socket.close() }
}

class JdwpClient(private val transport: JdwpTransport) : AutoCloseable {
  private val sequence = AtomicInteger(1); private val running = AtomicBoolean(false)
  private val pumpExecutor = Executors.newSingleThreadExecutor()
  private val pending = ConcurrentHashMap<Int, CompletableFuture<JdwpReplyPacket>>()
  @Volatile private var eventListener: ((JdwpEventPacket) -> Unit)? = null
  var idSizes: IdSizes = IdSizes(8,8,8,8,8); private set

  fun connectAndHandshake() { transport.connect(); transport.writePacket(JdwpConstants.HANDSHAKE.toByteArray(Charsets.US_ASCII)); val r=transport.readBytes(JdwpConstants.HANDSHAKE.length); require(r.toString(Charsets.US_ASCII)==JdwpConstants.HANDSHAKE); startPump(); idSizes = queryIdSizes() }
  fun setEventListener(listener: (JdwpEventPacket) -> Unit) { eventListener = listener }

  fun vmVersion(): VmVersionReply { val x=JdwpBuffer.wrap(sendCommand(1u,1u).payload); return VmVersionReply(x.readUtf8(), x.readInt(), x.readInt(), x.readUtf8(), x.readUtf8()) }
  fun queryIdSizes(): IdSizes { val x=JdwpBuffer.wrap(sendCommand(1u,7u).payload); return IdSizes(x.readInt(),x.readInt(),x.readInt(),x.readInt(),x.readInt()) }
  fun allThreads(): List<ThreadId> { val x=JdwpBuffer.wrap(sendCommand(1u,4u).payload); return (0 until x.readInt()).map { ThreadId(x.readId(idSizes.objectIdSize)) } }
  fun threadInfo(threadId: ThreadId): ThreadInfo {
    val name = JdwpBuffer.wrap(sendCommand(11u,1u,id(threadId.raw,idSizes.objectIdSize)).payload).readUtf8()
    val st = JdwpBuffer.wrap(sendCommand(11u,4u,id(threadId.raw,idSizes.objectIdSize)).payload)
    return ThreadInfo(name, st.readInt(), st.readInt())
  }

  fun classesBySignature(signature: String): List<ClassInfo> { val r=JdwpBuffer.wrap(sendCommand(1u,2u,JdwpBuffer.allocate(signature.toByteArray().size+4).putUtf8(signature).toByteArray()).payload); return (0 until r.readInt()).map { ClassInfo(r.readByte(), ReferenceTypeId(r.readId(idSizes.referenceTypeIdSize)), r.readInt()) } }
  fun methods(typeId: ReferenceTypeId): List<MethodInfo> { val r=JdwpBuffer.wrap(sendCommand(2u,5u,id(typeId.raw,idSizes.referenceTypeIdSize)).payload); return (0 until r.readInt()).map { MethodInfo(MethodId(r.readId(idSizes.methodIdSize)),r.readUtf8(),r.readUtf8(),r.readInt()) } }
  fun fields(typeId: ReferenceTypeId): List<FieldInfo> { val r=JdwpBuffer.wrap(sendCommand(2u,4u,id(typeId.raw,idSizes.referenceTypeIdSize)).payload); return (0 until r.readInt()).map { FieldInfo(FieldId(r.readId(idSizes.fieldIdSize)),r.readUtf8(),r.readUtf8(),r.readInt()) } }

  fun objectReferenceType(objectId: ObjectId): ObjectReferenceInfo { val r=JdwpBuffer.wrap(sendCommand(9u,1u,id(objectId.raw,idSizes.objectIdSize)).payload); return ObjectReferenceInfo(r.readByte(), ReferenceTypeId(r.readId(idSizes.referenceTypeIdSize))) }
  fun getObjectValues(objectId: ObjectId, fieldIds: List<FieldId>): List<TaggedValue> {
    val p=JdwpBuffer.allocate(idSizes.objectIdSize+4+fieldIds.size*idSizes.fieldIdSize).putBytes(id(objectId.raw,idSizes.objectIdSize)).putInt(fieldIds.size).apply { fieldIds.forEach { putBytes(id(it.raw,idSizes.fieldIdSize)) } }.toByteArray()
    val r=JdwpBuffer.wrap(sendCommand(9u,2u,p).payload); return (0 until r.readInt()).map { TaggedValue(r.readByte(), r.readIdByTag(idSizes.objectIdSize)) }
  }
  fun setObjectValues(objectId: ObjectId, values: List<ValueToSet>) {
    val payload = JdwpBuffer.allocate(8192).putBytes(id(objectId.raw,idSizes.objectIdSize)).putInt(values.size).apply { values.forEach { putBytes(id(it.fieldId.raw,idSizes.fieldIdSize)); putByte(it.value.tag); putBytes(id(it.value.rawValue,idSizes.objectIdSize)) } }.toByteArray()
    sendCommand(9u,3u,payload)
  }

  fun invokeMethod(objectId: ObjectId, threadId: ThreadId, classId: ReferenceTypeId, methodId: MethodId, args: List<TaggedValue>, options: Int = 0x01): TaggedValue {
    val b = JdwpBuffer.allocate(16384).putBytes(id(objectId.raw,idSizes.objectIdSize)).putBytes(id(threadId.raw,idSizes.objectIdSize)).putBytes(id(classId.raw,idSizes.referenceTypeIdSize)).putBytes(id(methodId.raw,idSizes.methodIdSize)).putInt(args.size)
    args.forEach { b.putByte(it.tag).putBytes(id(it.rawValue, idSizes.objectIdSize)) }
    val r = JdwpBuffer.wrap(sendCommand(9u,6u,b.putInt(options).toByteArray()).payload)
    return TaggedValue(r.readByte(), r.readIdByTag(idSizes.objectIdSize))
  }

  fun suspendVm() { sendCommand(1u,8u) }; fun resumeVm() { sendCommand(1u,9u) }
  fun setEvent(eventKind: JdwpEventKind, suspendPolicy: JdwpSuspendPolicy, modifiersPayload: ByteArray = JdwpBuffer.allocate(4).putInt(0).toByteArray()): EventRequestId {
    val p = JdwpBuffer.allocate(2 + modifiersPayload.size).putByte(eventKind.code).putByte(suspendPolicy.code).putBytes(modifiersPayload).toByteArray(); return EventRequestId(JdwpBuffer.wrap(sendCommand(15u,1u,p).payload).readInt())
  }
  fun clearEvent(eventKind: JdwpEventKind, requestId: EventRequestId) { sendCommand(15u,2u,JdwpBuffer.allocate(5).putByte(eventKind.code).putInt(requestId.raw).toByteArray()) }

  fun sendCommand(commandSet: UByte, command: UByte, payload: ByteArray = byteArrayOf()): JdwpReplyPacket {
    val id = sequence.getAndIncrement(); val f = CompletableFuture<JdwpReplyPacket>(); pending[id] = f
    transport.writePacket(JdwpCodec.encodeCommand(JdwpCommandPacket(id,commandSet,command,payload))); val rep = f.get(); JdwpErrors.throwIfError(rep.errorCode); return rep
  }

  private fun startPump() { if (!running.compareAndSet(false,true)) return; pumpExecutor.execute { while (running.get()) { when (val p = JdwpCodec.readPacket(transport)) { is JdwpReplyPacket -> pending.remove(p.id)?.complete(p); is JdwpEventPacket -> eventListener?.invoke(p) } } } }
  private fun id(v: Long, size: Int): ByteArray = JdwpBuffer.allocate(size).apply { if (size==8) putLong(v) else putInt(v.toInt()) }.toByteArray()
  override fun close() { running.set(false); pumpExecutor.shutdownNow(); transport.close() }
}

object JdwpCodec {
  fun encodeCommand(packet: JdwpCommandPacket): ByteArray = JdwpBuffer.allocate(11 + packet.payload.size).putInt(11 + packet.payload.size).putInt(packet.id).putByte(0).putByte(packet.commandSet.toByte()).putByte(packet.command.toByte()).putBytes(packet.payload).toByteArray()
  fun readPacket(transport: JdwpTransport): Any {
    val h = transport.readBytes(11); val b = JdwpBuffer.wrap(h); val len=b.readInt(); val id=b.readInt(); val flags=b.readByte().toUByte(); val body=transport.readBytes(len-11)
    return if ((flags.toInt() and JdwpConstants.REPLY_FLAG.toInt()) != 0) JdwpReplyPacket(id, JdwpBuffer.wrap(h.copyOfRange(9,11)).readShort().toUShort(), body) else JdwpEventPacket(id, h[9].toUByte(), h[10].toUByte(), body)
  }
}

private fun JdwpBuffer.readId(size: Int): Long = if (size == 8) readLong() else readInt().toLong() and 0xffffffffL
private fun JdwpBuffer.readIdByTag(objectSize: Int): Long = if (remaining() >= objectSize) readId(objectSize) else readLong()
