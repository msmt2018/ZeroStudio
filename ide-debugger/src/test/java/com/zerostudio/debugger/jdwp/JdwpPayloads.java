/*
 *  ZeroStudio IDE - JDWP payload builders
 *
 *  把各 JDWP 响应(Frames / VariableTable / GetValues / ThisObject /
 *  ReferenceType / Fields / Methods / ClassesBySignature / CreateString
 *  等)的字节编码封装成可读的辅助方法,供端到端测试在 FakeJdwpClient
 *  上排队使用。
 *
 *  这些方法的命名和签名尽量跟 JDWP 规范保持一致,便于对照查看。
 */

package com.zerostudio.debugger.jdwp;

import com.zerostudio.debugger.util.ByteBuf;
import androidx.annotation.NonNull;

public final class JdwpPayloads {

    private JdwpPayloads() {}

    // ---------- 工具 ----------

    /** 把字节 tag 转换成单字节数组(便于 ByteBuf 操作)。 */
    @NonNull
    public static byte[] bytes(byte tag) {
        return new byte[] { tag };
    }

    /** ThreadReference.Frames 的响应:1 个 frame。 */
    @NonNull
    public static byte[] framesReply(
            long frameId, byte typeTag, long classId, long methodId, long codeIndex) {
        ByteBuf b = new ByteBuf();
        b.writeInt(1);
        b.writeLong(frameId);
        b.writeByte(typeTag);
        b.writeLong(classId);
        b.writeLong(methodId);
        b.writeLong(codeIndex);
        return b.toByteArray();
    }

    /** 一个 VariableTable 中的变量条目。 */
    public static final class Var {
        public final long codeIndex;
        @NonNull public final String name;
        @NonNull public final String signature;
        public final int length;
        public final int slot;
        public Var(long codeIndex, @NonNull String name, @NonNull String signature,
                   int length, int slot) {
            this.codeIndex = codeIndex;
            this.name = name;
            this.signature = signature;
            this.length = length;
            this.slot = slot;
        }
    }

    /** Method.VariableTable 的响应,变量条目数 = vars.length。 */
    @NonNull
    public static byte[] variableTableReply(@NonNull Var[] vars) {
        ByteBuf b = new ByteBuf();
        b.writeInt(0); // argCnt (本实现忽略)
        b.writeInt(vars.length);
        for (Var v : vars) {
            b.writeLong(v.codeIndex);
            b.writeString(v.name);
            b.writeString(v.signature);
            b.writeInt(v.length);
            b.writeInt(v.slot);
        }
        return b.toByteArray();
    }

    /**
     * StackFrame.GetValues 的响应,只有 1 个值。
     *  写入 [count=1] + [tag] + [value bytes]。
     */
    @NonNull
    public static byte[] getValuesReply(byte tag, @NonNull byte[] value) {
        ByteBuf b = new ByteBuf();
        b.writeInt(1);
        b.writeByte(tag);
        b.writeBytes(value);
        return b.toByteArray();
    }

    /**
     * StackFrame.GetValues 的响应,多个值,按 [tags[i]] + [valueBytes] 编码。
     *  每个 valueBytes 是该 tag 对应原始字节。
     */
    @NonNull
    public static byte[] getValuesReplyMulti(
            @NonNull byte[] tags, @NonNull byte[][] valueBytes) {
        if (tags.length != valueBytes.length) {
            throw new IllegalArgumentException("tags/valueBytes length mismatch");
        }
        ByteBuf b = new ByteBuf();
        b.writeInt(tags.length);
        for (int i = 0; i < tags.length; i++) {
            b.writeByte(tags[i]);
            b.writeBytes(valueBytes[i]);
        }
        return b.toByteArray();
    }

    /** StackFrame.ThisObject 的响应:tag + (可选) objectId。 */
    @NonNull
    public static byte[] thisObjectReply(byte tag, long objectId) {
        ByteBuf b = new ByteBuf();
        b.writeByte(tag);
        if (tag == 'L' || tag == '[') {
            b.writeLong(objectId);
        }
        return b.toByteArray();
    }

    /** ReferenceType.Signature 的响应。 */
    @NonNull
    public static byte[] signatureReply(@NonNull String signature) {
        ByteBuf b = new ByteBuf();
        b.writeString(signature);
        return b.toByteArray();
    }

    /** ObjectReference.ReferenceType 的响应:typeTag + classId。 */
    @NonNull
    public static byte[] referenceTypeReply(byte typeTag, long classId) {
        ByteBuf b = new ByteBuf();
        b.writeByte(typeTag);
        b.writeLong(classId);
        return b.toByteArray();
    }

    /** 一个字段定义。 */
    public static final class Field {
        public final long fieldId;
        @NonNull public final String name;
        @NonNull public final String signature;
        public final int modBits;
        public Field(long fieldId, @NonNull String name, @NonNull String signature, int modBits) {
            this.fieldId = fieldId;
            this.name = name;
            this.signature = signature;
            this.modBits = modBits;
        }
    }

    /** ReferenceType.Fields 的响应。 */
    @NonNull
    public static byte[] fieldsReply(@NonNull Field[] fields) {
        ByteBuf b = new ByteBuf();
        b.writeInt(fields.length);
        for (Field f : fields) {
            b.writeLong(f.fieldId);
            b.writeString(f.name);
            b.writeString(f.signature);
            b.writeInt(f.modBits);
        }
        return b.toByteArray();
    }

    /** 一个方法定义。 */
    public static final class Method {
        public final long methodId;
        @NonNull public final String name;
        @NonNull public final String signature;
        public final int modBits;
        public Method(long methodId, @NonNull String name, @NonNull String signature, int modBits) {
            this.methodId = methodId;
            this.name = name;
            this.signature = signature;
            this.modBits = modBits;
        }
    }

    /** ReferenceType.Methods 的响应。 */
    @NonNull
    public static byte[] methodsReply(@NonNull Method[] methods) {
        ByteBuf b = new ByteBuf();
        b.writeInt(methods.length);
        for (Method m : methods) {
            b.writeLong(m.methodId);
            b.writeString(m.name);
            b.writeString(m.signature);
            b.writeInt(m.modBits);
        }
        return b.toByteArray();
    }

    /** VirtualMachine.ClassesBySignature 的响应(1 个类)。 */
    @NonNull
    public static byte[] classesBySignatureReply(byte typeTag, long classId, int status) {
        ByteBuf b = new ByteBuf();
        b.writeInt(1);
        b.writeByte(typeTag);
        b.writeLong(classId);
        b.writeInt(status);
        return b.toByteArray();
    }

    /** VirtualMachine.ClassesBySignature 的响应(0 个类)。 */
    @NonNull
    public static byte[] classesBySignatureEmpty() {
        ByteBuf b = new ByteBuf();
        b.writeInt(0);
        return b.toByteArray();
    }

    /** VirtualMachine.CreateString 的响应:返回的 stringId。 */
    @NonNull
    public static byte[] createStringReply(long stringId) {
        ByteBuf b = new ByteBuf();
        b.writeLong(stringId);
        return b.toByteArray();
    }

    /** 编码一个 int 值的原始字节(用于 GetValues 的 value 负载)。 */
    @NonNull
    public static byte[] intValue(int v) {
        ByteBuf b = new ByteBuf();
        b.writeInt(v);
        return b.toByteArray();
    }

    /** 编码一个 long 值的原始字节。 */
    @NonNull
    public static byte[] longValue(long v) {
        ByteBuf b = new ByteBuf();
        b.writeLong(v);
        return b.toByteArray();
    }

    /** 编码一个 boolean 值的原始字节。 */
    @NonNull
    public static byte[] boolValue(boolean v) {
        return new byte[] { (byte) (v ? 1 : 0) };
    }

    /** 编码一个 short 值的原始字节。 */
    @NonNull
    public static byte[] shortValue(short v) {
        ByteBuf b = new ByteBuf();
        b.writeShort(v);
        return b.toByteArray();
    }

    /** 编码一个 byte 值的原始字节。 */
    @NonNull
    public static byte[] byteValue(byte v) {
        return new byte[] { v };
    }

    /** 编码一个 char 值的原始字节(2 字节 unsigned)。 */
    @NonNull
    public static byte[] charValue(char v) {
        ByteBuf b = new ByteBuf();
        b.writeShort(v);
        return b.toByteArray();
    }

    /** 编码一个 float 值的原始字节。 */
    @NonNull
    public static byte[] floatValue(float v) {
        ByteBuf b = new ByteBuf();
        b.writeFloat(v);
        return b.toByteArray();
    }

    /** 编码一个 double 值的原始字节。 */
    @NonNull
    public static byte[] doubleValue(double v) {
        ByteBuf b = new ByteBuf();
        b.writeDouble(v);
        return b.toByteArray();
    }
}
