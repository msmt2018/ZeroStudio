/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.repository.materials

import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * Minimal JVM bytecode disassembler. Produces a smali-like textual listing of the
 * `Code` attribute of a method. The output is good enough for inspection – it is
 * not a full reimplementation of the JVM spec.
 *
 * Branch instructions are annotated with target labels where possible, and the
 * operands of pool references are resolved using the supplied [ConstantPool].
 *
 * @author android_zero
 */
internal object BytecodeDisassembler {

  fun disassemble(code: ByteArray, cp: ConstantPool): List<String> {
    if (code.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    val targets = collectBranchTargets(code)
    val reader = DataInputStream(ByteArrayInputStream(code))
    var pc = 0
    try {
      while (pc < code.size) {
        val offset = pc
        val op = reader.readUnsignedByte()
        pc++
        val (mnemonic, operandText, consumed) = decode(op, reader, cp, pc)
        pc += consumed
        val labelSuffix = if (targets.contains(offset)) "  ; <label${"%04x".format(offset)}>" else ""
        out += hex(offset) + ": " + mnemonic + if (operandText.isNotEmpty()) " " + operandText else "" + labelSuffix
      }
    } catch (t: Throwable) {
      out += "... truncated (${t.javaClass.simpleName})"
    }
    return out
  }

  private data class Decoded(val mnemonic: String, val operand: String, val consumed: Int)

  private fun decode(op: Int, reader: DataInputStream, cp: ConstantPool, pc: Int): Decoded {
    val name = OPCODES.getOrNull(op) ?: "unknown_0x${"%02x".format(op)}"
    return when (op) {
      // No operands
      in 0x00..0x0f, in 0x1a..0x35, in 0x3b..0x4f, 0x57, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f,
      0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e,
      0x6f, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x7b, 0x7c, 0x7d,
      0x7e, 0x7f, 0x80, 0x81, 0x82, 0x83, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d,
      0x8e, 0x8f, 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0xa9, 0xac, 0xb1, 0xb2,
      0xb3, 0xb4, 0xb5, 0xca -> Decoded(name, "", 0)

      // bipush / sipush
      0x10 -> Decoded(name, "#${reader.readByte()}", 1)
      0x11 -> Decoded(name, "#${reader.readShort()}", 2)

      // ldc
      0x12 -> {
        val idx = reader.readUnsignedByte()
        Decoded(name, describePoolItem(cp, idx), 1)
      }
      0x13 -> {
        val idx = reader.readUnsignedShort()
        Decoded(name, describePoolItem(cp, idx), 2)
      }
      0x14 -> {
        val idx = reader.readUnsignedShort()
        Decoded(name, describePoolItem(cp, idx, wide = true), 2)
      }

      // iload / lload / fload / dload / aload (local var index)
      0x15, 0x16, 0x17, 0x18, 0x19 -> {
        val idx = reader.readUnsignedByte()
        Decoded(name, "v$idx", 1)
      }

      // istore / lstore / fstore / dstore / astore (local var index)
      0x36, 0x37, 0x38, 0x39, 0x3a -> {
        val idx = reader.readUnsignedByte()
        Decoded(name, "v$idx", 1)
      }

      // iinc
      0x84 -> {
        val idx = reader.readUnsignedByte()
        val inc = reader.readByte()
        Decoded(name, "v$idx, #$inc", 2)
      }

      // branches
      0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f -> {
        val s = reader.readShort()
        Decoded(name, label(pc + s), 2)
      }
      0xc6, 0xc7 -> {
        val s = reader.readShort()
        Decoded(name, label(pc + s), 2)
      }
      0xc8, 0xc9 -> {
        val s = reader.readInt()
        Decoded(name, label(pc + s), 4)
      }
      0xa7, 0xa8 -> {
        val s = reader.readShort()
        Decoded(name, label(pc + s), 2)
      }

      // getstatic / putstatic / getfield / putfield
      0xb2, 0xb3, 0xb4, 0xb5 -> {
        val idx = reader.readUnsignedShort()
        Decoded(name, describeRef(cp, idx), 2)
      }

      // invokevirtual / invokespecial / invokestatic
      0xb6, 0xb7, 0xb8 -> {
        val idx = reader.readUnsignedShort()
        Decoded(name, describeRef(cp, idx), 2)
      }
      // invokeinterface
      0xb9 -> {
        val idx = reader.readUnsignedShort()
        val count = reader.readUnsignedByte()
        reader.readUnsignedByte() // padding
        Decoded(name, describeRef(cp, idx) + ", #$count", 3)
      }
      // invokedynamic
      0xba -> {
        val idx = reader.readUnsignedShort()
        reader.readUnsignedShort()
        Decoded(name, describeRef(cp, idx, dynamic = true), 4)
      }

      // new / anewarray / checkcast / instanceof
      0xbb, 0xbd, 0xc0, 0xc1 -> {
        val idx = reader.readUnsignedShort()
        Decoded(name, describeClass(cp, idx), 2)
      }
      0xbc -> {
        val atype = reader.readUnsignedByte()
        Decoded(name, "atype=$atype", 1)
      }
      0xbe, 0xbf -> {
        val idx = reader.readUnsignedShort()
        Decoded(name, describeClass(cp, idx), 2)
      }

      // multianewarray
      0xc5 -> {
        val idx = reader.readUnsignedShort()
        val dims = reader.readUnsignedByte()
        Decoded(name, "${describeClass(cp, idx)}, #$dims", 3)
      }

      // wide
      0xc4 -> {
        val sub = reader.readUnsignedByte()
        if (sub == 0x84) {
          val idx = reader.readUnsignedShort()
          val inc = reader.readShort()
          Decoded("wide", "iinc v$idx, #$inc", 5)
        } else {
          val idx = reader.readUnsignedShort()
          Decoded("wide", "v$idx", 3)
        }
      }

      // goto_w / jsr_w
      0xc8, 0xc9 -> {
        val s = reader.readInt()
        Decoded(name, label(pc + s), 4)
      }

      // tableswitch / lookupswitch
      0xaa -> {
        val padding = (4 - (pc % 4)) % 4
        repeat(padding) { reader.readByte() }
        val def = reader.readInt()
        val low = reader.readInt()
        val high = reader.readInt()
        val count = high - low + 1
        repeat(count) { reader.readInt() }
        Decoded(name, "default=" + label(pc + def) + " range=$low..$high", 1 + padding + 12 + count * 4)
      }
      0xab -> {
        val padding = (4 - (pc % 4)) % 4
        repeat(padding) { reader.readByte() }
        val def = reader.readInt()
        val npairs = reader.readInt()
        repeat(npairs) {
          reader.readInt()
          reader.readInt()
        }
        Decoded(name, "default=" + label(pc + def) + " npairs=$npairs", 1 + padding + 8 + npairs * 8)
      }

      else -> Decoded(name, "", 0)
    }
  }

  private fun describePoolItem(cp: ConstantPool, idx: Int, wide: Boolean = false): String {
    val entry = cp[idx] ?: return "#$idx"
    return when (entry) {
      is CpString -> "string \"" + cp.utf(entry.stringIndex).escape() + "\""
      is CpInteger -> "int ${entry.value}"
      is CpLong -> "long ${entry.value}L"
      is CpFloat -> "float ${entry.value}f"
      is CpDouble -> "double ${entry.value}d"
      is CpClass -> "class " + cp.utf(entry.nameIndex)
      is CpRef -> describeRef(cp, idx)
      is CpNameAndType ->
          cp.utf(entry.nameIndex) + ":" + cp.utf(entry.descriptorIndex).take(if (wide) 32 else 16)
      is CpUtf8 -> "utf8 \"" + entry.value.escape() + "\""
      else -> "#$idx"
    }
  }

  private fun describeRef(cp: ConstantPool, idx: Int, dynamic: Boolean = false): String {
    val entry = cp[idx] as? CpRef ?: return "#$idx"
    val className = (cp[entry.classIndex] as? CpClass)?.let { cp.utf(it.nameIndex) } ?: "?"
    val nt = cp[entry.nameAndTypeIndex] as? CpNameAndType
    val name = nt?.let { cp.utf(it.nameIndex) } ?: "?"
    val desc = nt?.let { cp.utf(it.descriptorIndex) } ?: "?"
    val type =
        if (dynamic) "invoke-dynamic"
        else when (entry.kind) {
          CpRefKind.FIELD -> "field"
          CpRefKind.METHOD -> "method"
          CpRefKind.INTERFACE -> "interface-method"
        }
    return "$type $className->$name$desc"
  }

  private fun describeClass(cp: ConstantPool, idx: Int): String {
    val entry = cp[idx] as? CpClass ?: return "#$idx"
    return "class " + cp.utf(entry.nameIndex)
  }

  private fun label(target: Int): String = "label${"%04x".format(target)}"

  private fun hex(offset: Int): String = "0000${"%x".format(offset)}".takeLast(4)

  private fun collectBranchTargets(code: ByteArray): Set<Int> {
    val targets = mutableSetOf<Int>()
    val reader = DataInputStream(ByteArrayInputStream(code))
    var pc = 0
    while (pc < code.size) {
      val start = pc
      val op = reader.readUnsignedByte()
      pc++
      val next = pc
      when (op) {
        0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xc6, 0xc7, 0xa7, 0xa8 -> {
          val s = reader.readShort()
          targets += next + s
          pc += 2
        }
        0xc8, 0xc9 -> {
          val s = reader.readInt()
          targets += next + s
          pc += 4
        }
        0x84 -> pc += 2
        0xc4 -> {
          val sub = reader.readUnsignedByte()
          pc += 2
          if (sub == 0x84) pc += 2
        }
        0xc5 -> pc += 3
        0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xbe, 0xbf, 0xc0, 0xc1, 0x13, 0x14 -> pc += 2
        0xb9 -> {
          reader.readUnsignedShort()
          reader.readUnsignedByte()
          reader.readUnsignedByte()
          pc += 4
        }
        0xba -> pc += 4
        0xb2, 0xb3, 0xb4, 0xb5 -> pc += 2
        0x12, 0xbc, 0x10 -> pc += 1
        0x11 -> pc += 2
        0xaa -> {
          val padding = (4 - (pc % 4)) % 4
          repeat(padding) { reader.readByte() }
          val def = reader.readInt()
          val low = reader.readInt()
          val high = reader.readInt()
          val count = high - low + 1
          targets += next + def
          repeat(count) { reader.readInt() }
          pc += padding + 12 + count * 4
        }
        0xab -> {
          val padding = (4 - (pc % 4)) % 4
          repeat(padding) { reader.readByte() }
          val def = reader.readInt()
          val npairs = reader.readInt()
          targets += next + def
          repeat(npairs) {
            reader.readInt()
            reader.readInt()
          }
          pc += padding + 8 + npairs * 8
        }
        else -> {
          // no operands
        }
      }
      if (pc < start) break // safety
    }
    return targets
  }

  private fun String.escape(): String =
      replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

  private val OPCODES = arrayOf(
      "nop", "aconst_null", "iconst_m1", "iconst_0", "iconst_1", "iconst_2", "iconst_3", "iconst_4",
      "iconst_5", "lconst_0", "lconst_1", "fconst_0", "fconst_1", "fconst_2", "dconst_0", "dconst_1",
      "bipush", "sipush", "ldc", "ldc_w", "ldc2_w", "iload", "lload", "fload", "dload", "aload",
      "iload_0", "iload_1", "iload_2", "iload_3", "lload_0", "lload_1", "lload_2", "lload_3",
      "fload_0", "fload_1", "fload_2", "fload_3", "dload_0", "dload_1", "dload_2", "dload_3",
      "aload_0", "aload_1", "aload_2", "aload_3", "iaload", "laload", "faload", "daload", "aaload",
      "baload", "caload", "saload", "istore", "lstore", "fstore", "dstore", "astore", "istore_0",
      "istore_1", "istore_2", "istore_3", "lstore_0", "lstore_1", "lstore_2", "lstore_3", "fstore_0",
      "fstore_1", "fstore_2", "fstore_3", "dstore_0", "dstore_1", "dstore_2", "dstore_3", "astore_0",
      "astore_1", "astore_2", "astore_3", "iastore", "lastore", "fastore", "dastore", "aastore",
      "bastore", "castore", "sastore", "pop", "pop2", "dup", "dup_x1", "dup_x2", "dup2", "dup2_x1",
      "dup2_x2", "swap", "iadd", "ladd", "fadd", "dadd", "isub", "lsub", "fsub", "dsub", "imul",
      "lmul", "fmul", "dmul", "idiv", "ldiv", "fdiv", "ddiv", "irem", "lrem", "frem", "drem",
      "ineg", "lneg", "fneg", "dneg", "ishl", "lshl", "ishr", "lshr", "iushr", "lushr", "iand",
      "land", "ior", "lor", "ixor", "lxor", "iinc", "i2l", "i2f", "i2d", "l2i", "l2f", "l2d",
      "f2i", "f2l", "f2d", "d2i", "d2l", "d2f", "i2b", "i2c", "i2s", "lcmp", "fcmpl", "fcmpg",
      "dcmpl", "dcmpg", "ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle", "if_icmpeq", "if_icmpne",
      "if_icmplt", "if_icmpge", "if_icmpgt", "if_icmple", "if_acmpeq", "if_acmpne", "goto", "jsr",
      "ret", "tableswitch", "lookupswitch", "ireturn", "lreturn", "freturn", "dreturn", "areturn",
      "return", "getstatic", "putstatic", "getfield", "putfield", "invokevirtual", "invokespecial",
      "invokestatic", "invokeinterface", "invokedynamic", "new", "newarray", "anewarray", "arraylength",
      "athrow", "checkcast", "instanceof", "monitorenter", "monitorexit", "wide", "multianewarray",
      "ifnull", "ifnonnull", "goto_w", "jsr_w", "breakpoint", "*", "impdep1", "impdep2",
  )
}
