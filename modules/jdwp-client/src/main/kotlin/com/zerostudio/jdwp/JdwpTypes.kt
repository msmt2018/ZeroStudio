package com.zerostudio.jdwp

import java.nio.ByteBuffer
import java.nio.ByteOrder

@JvmInline value class ObjectId(val raw: Long)
@JvmInline value class ReferenceTypeId(val raw: Long)
@JvmInline value class MethodId(val raw: Long)
@JvmInline value class FieldId(val raw: Long)
@JvmInline value class ThreadId(val raw: Long)
@JvmInline value class FrameId(val raw: Long)

object JdwpConstants {
  const val HANDSHAKE = "JDWP-Handshake"
  const val REPLY_FLAG: UByte = 0x80u
}

data class JdwpCommandPacket(
  val id: Int,
  val commandSet: UByte,
  val command: UByte,
  val payload: ByteArray,
)

data class JdwpReplyPacket(
  val id: Int,
  val errorCode: UShort,
  val payload: ByteArray,
)

data class JdwpEventPacket(
  val id: Int,
  val commandSet: UByte,
  val command: UByte,
  val payload: ByteArray,
)

class JdwpBuffer(private val delegate: ByteBuffer) {
  companion object {
    fun wrap(bytes: ByteArray): JdwpBuffer = JdwpBuffer(ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN))
    fun allocate(size: Int): JdwpBuffer = JdwpBuffer(ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN))
  }

  fun putByte(value: Byte) = apply { delegate.put(value) }
  fun putShort(value: Short) = apply { delegate.putShort(value) }
  fun putInt(value: Int) = apply { delegate.putInt(value) }
  fun putLong(value: Long) = apply { delegate.putLong(value) }
  fun putBytes(value: ByteArray) = apply { delegate.put(value) }
  fun putUtf8(value: String) = apply {
    val bytes = value.toByteArray(Charsets.UTF_8)
    delegate.putInt(bytes.size)
    delegate.put(bytes)
  }

  fun readByte(): Byte = delegate.get()
  fun readShort(): Short = delegate.short
  fun readInt(): Int = delegate.int
  fun readLong(): Long = delegate.long
  fun readBytes(size: Int): ByteArray = ByteArray(size).also(delegate::get)
  fun readUtf8(): String {
    val len = delegate.int
    val bytes = ByteArray(len)
    delegate.get(bytes)
    return bytes.toString(Charsets.UTF_8)
  }

  fun remaining(): Int = delegate.remaining()

  fun toByteArray(): ByteArray {
    val out = ByteArray(delegate.position())
    delegate.rewind()
    delegate.get(out)
    return out
  }
}
