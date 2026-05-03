package com.zerostudio.jdwp

data class VmVersionReply(
  val description: String,
  val jdwpMajor: Int,
  val jdwpMinor: Int,
  val vmVersion: String,
  val vmName: String,
)

data class ClassInfo(
  val refTypeTag: Byte,
  val typeId: ReferenceTypeId,
  val status: Int,
)

data class MethodInfo(
  val id: MethodId,
  val name: String,
  val signature: String,
  val modifiers: Int,
)

data class FieldInfo(
  val id: FieldId,
  val name: String,
  val signature: String,
  val modifiers: Int,
)

object JdwpErrors {
  private val mapping = mapOf<UShort, String>(
    10u.toUShort() to "INVALID_THREAD",
    13u.toUShort() to "THREAD_NOT_SUSPENDED",
    15u.toUShort() to "INVALID_OBJECT",
    20u.toUShort() to "INVALID_CLASS",
    23u.toUShort() to "INVALID_METHODID",
    25u.toUShort() to "INVALID_FIELDID",
    99u.toUShort() to "NOT_IMPLEMENTED",
    112u.toUShort() to "VM_DEAD",
  )

  fun throwIfError(code: UShort) {
    if (code.toInt() == 0) return
    val name = mapping[code] ?: "UNKNOWN"
    throw IllegalStateException("JDWP error $code ($name)")
  }
}
