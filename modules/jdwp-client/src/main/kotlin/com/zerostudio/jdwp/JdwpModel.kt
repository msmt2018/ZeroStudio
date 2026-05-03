package com.zerostudio.jdwp

data class VmVersionReply(val description: String, val jdwpMajor: Int, val jdwpMinor: Int, val vmVersion: String, val vmName: String)
data class IdSizes(val fieldIdSize: Int, val methodIdSize: Int, val objectIdSize: Int, val referenceTypeIdSize: Int, val frameIdSize: Int)
data class ClassInfo(val refTypeTag: Byte, val typeId: ReferenceTypeId, val status: Int)
data class MethodInfo(val id: MethodId, val name: String, val signature: String, val modifiers: Int)
data class FieldInfo(val id: FieldId, val name: String, val signature: String, val modifiers: Int)
data class ThreadInfo(val name: String, val threadStatus: Int, val suspendStatus: Int)
data class ObjectReferenceInfo(val refTypeTag: Byte, val typeId: ReferenceTypeId)

data class TaggedValue(val tag: Byte, val rawValue: Long)
data class ValueToSet(val fieldId: FieldId, val value: TaggedValue)

enum class JdwpEventKind(val code: Byte) { BREAKPOINT(2), SINGLE_STEP(1), METHOD_ENTRY(40), METHOD_EXIT(41), CLASS_PREPARE(8), VM_DEATH(99) }
enum class JdwpSuspendPolicy(val code: Byte) { NONE(0), EVENT_THREAD(1), ALL(2) }
data class EventRequestId(val raw: Int)

data class JdwpClassRef(val id: ReferenceTypeId, val signature: String)
data class JdwpMethodRef(val id: MethodId, val name: String, val signature: String)

object JdwpErrors {
  private val mapping = mapOf<UShort, String>(10u.toUShort() to "INVALID_THREAD", 13u.toUShort() to "THREAD_NOT_SUSPENDED", 15u.toUShort() to "INVALID_OBJECT", 20u.toUShort() to "INVALID_CLASS", 23u.toUShort() to "INVALID_METHODID", 25u.toUShort() to "INVALID_FIELDID", 99u.toUShort() to "NOT_IMPLEMENTED", 112u.toUShort() to "VM_DEAD")
  fun throwIfError(code: UShort) { if (code.toInt() != 0) throw IllegalStateException("JDWP error $code (${mapping[code] ?: "UNKNOWN"})") }
}
