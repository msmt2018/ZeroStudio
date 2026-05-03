package com.zerostudio.jdwp

import java.nio.ByteBuffer
import java.nio.ByteOrder

@JvmInline value class ObjectId(val raw: Long)
@JvmInline value class ReferenceTypeId(val raw: Long)
@JvmInline value class MethodId(val raw: Long)
@JvmInline value class FieldId(val raw: Long)
@JvmInline value class ThreadId(val raw: Long)

object JdwpConstants {
  const val HANDSHAKE = "JDWP-Handshake"
}

data class JdwpPacketHeader(
  val length: Int,
  val id: Int,
  val flags: UByte,
  val commandSet: UByte,
  val command: UByte,
  val errorCode: UShort,
)

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

class JdwpBuffer(private val delegate: ByteBuffer) {
  companion object {
    fun wrap(bytes: ByteArray): JdwpBuffer = JdwpBuffer(ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN))
    fun allocate(size: Int): JdwpBuffer = JdwpBuffer(ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN))
  }

  fun putByte(value: Byte) = apply { delegate.put(value) }
  fun putBoolean(value: Boolean) = apply { delegate.put(if (value) 1 else 0) }
  fun putInt(value: Int) = apply { delegate.putInt(value) }
  fun putLong(value: Long) = apply { delegate.putLong(value) }
  fun putUtf8(value: String) = apply {
    val bytes = value.toByteArray(Charsets.UTF_8)
    delegate.putInt(bytes.size)
    delegate.put(bytes)
  }

  fun readByte(): Byte = delegate.get()
  fun readBoolean(): Boolean = delegate.get().toInt() != 0
  fun readInt(): Int = delegate.int
  fun readLong(): Long = delegate.long
  fun readUtf8(): String {
    val len = delegate.int
    val bytes = ByteArray(len)
    delegate.get(bytes)
    return bytes.toString(Charsets.UTF_8)
  }

  fun toByteArray(): ByteArray {
    val out = ByteArray(delegate.position())
    delegate.rewind()
    delegate.get(out)
    return out
  }
}
