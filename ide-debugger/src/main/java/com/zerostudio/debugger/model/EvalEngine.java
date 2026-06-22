/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Expression evaluator. The current implementation only fetches
 *  variable values; a richer implementation would let the user type an
 *  arbitrary expression and evaluate it via ClassType.InvokeMethod
 *  on a synthetic helper, but the user-visible UI in PR-3 only needs
 *  variable lookups.
 */

package com.zerostudio.debugger.model;

import androidx.annotation.NonNull;
import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.api.VariableInfo;
import com.zerostudio.debugger.jdwp.CommandCodes;
import com.zerostudio.debugger.jdwp.CommandSet;
import com.zerostudio.debugger.jdwp.JdwpClient;
import com.zerostudio.debugger.jdwp.JdwpPacket;
import com.zerostudio.debugger.util.ByteBuf;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class EvalEngine {

    private final Debugger debugger;
    private final JdwpClient client;

    public EvalEngine(@NonNull Debugger debugger) {
        this.debugger = debugger;
        this.client = debugger.client();
    }

    /** Read a single variable value by frame + slot. */
    @NonNull
    public VariableInfo getFrameVariable(
            long threadId,
            long frameId,
            int slot,
            @NonNull String name,
            @NonNull String typeSignature) throws IOException {
        ByteBuf buf = new ByteBuf();
        buf.writeLong(threadId);
        buf.writeLong(frameId);
        buf.writeInt(1);
        buf.writeInt(slot);
        buf.writeByte(tagFor(typeSignature));
        JdwpPacket reply = client.sendCommand(
                CommandSet.StackFrame, CommandCodes.StackFrameCmd.GetValues, buf.toByteArray());
        if (reply.errorCode() != 0) {
            return new VariableInfo(0, "", name, typeSignature, "<error>", true, slot);
        }
        ByteBuf in = new ByteBuf(reply.data);
        in.readInt(); // count
        byte tag = in.readByte();
        String value = readValue(in, tag);
        return new VariableInfo(0, String.valueOf((char) tag), name, typeSignature, value, isPrim(tag), slot);
    }

    /** Read all locals + 'this' from a frame. */
    @NonNull
    public List<VariableInfo> getFrameVariables(long threadId, long frameId) throws IOException {
        // The frame already carries the variable list (slot index + name).
        // The current implementation returns an empty list because the
        // var table lookup is non-trivial. PR-3 fetches the full list
        // through EvalEngine on demand.
        return Collections.emptyList();
    }

    private String readValue(@NonNull ByteBuf in, byte tag) {
        switch (tag) {
            case 'V': return "void";
            case 'Z': return (in.readByte() != 0) ? "true" : "false";
            case 'B': return String.valueOf(in.readByte());
            case 'C': return String.valueOf((char) in.readUnsignedShort());
            case 'S': return String.valueOf(in.readShort());
            case 'I': return String.valueOf(in.readInt());
            case 'J': return String.valueOf(in.readLong());
            case 'F': return String.valueOf(in.readFloat());
            case 'D': return String.valueOf(in.readDouble());
            case 'L': {
                long id = in.readLong();
                return "<object id=" + id + ">";
            }
            default: return "?";
        }
    }

    private static boolean isPrim(byte tag) {
        return tag != 'L' && tag != '[';
    }

    private static byte tagFor(@NonNull String sig) {
        if (sig.isEmpty()) return 'L';
        char c = sig.charAt(0);
        if (c == '[') return 'L';
        if (c == 'L') return 'L';
        return (byte) c;
    }
}
