/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  In-process pub/sub bus for high-level debug events. The bus has two
 *  kinds of subscribers:
 *
 *  - [DebugEventsListener] receives the structured [DebugEvents] payloads.
 *  - Raw JDWP packet subscribers are handled inside [JdwpClient]; they
 *    drive the bus.
 */

package com.zerostudio.debugger.event;

import android.util.Log;
import androidx.annotation.NonNull;
import com.zerostudio.debugger.api.Debugger;
import com.zerostudio.debugger.api.StackFrameInfo;
import com.zerostudio.debugger.api.SuspendInfo;
import com.zerostudio.debugger.jdwp.CommandSet;
import com.zerostudio.debugger.jdwp.EventKind;
import com.zerostudio.debugger.jdwp.JdwpPacket;
import com.zerostudio.debugger.jdwp.SuspendPolicy;
import com.zerostudio.debugger.util.ByteBuf;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DebugEventBus {

    private static final String TAG = "DebugEventBus";
    private final CopyOnWriteArrayList<DebugEventsListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(@NonNull DebugEventsListener l) {
        listeners.addIfAbsent(l);
    }

    public void unsubscribe(@NonNull DebugEventsListener l) {
        listeners.remove(l);
    }

    public void publish(@NonNull DebugEvents event) {
        for (DebugEventsListener l : listeners) {
            try {
                l.onDebugEvent(event);
            } catch (Throwable t) {
                Log.w(TAG, "listener failed", t);
            }
        }
    }

    /**
     * Dispatch a single JDWP event packet. The packet is decoded based on
     * the command set (the JDWP spec puts events on the EventRequest
     * command set, command code 100).
     */
    public void dispatch(@NonNull JdwpPacket packet, @NonNull Debugger debugger) {
        if (packet.commandSet != 64) {
            // 64 is the synthetic command set for events. Anything else
            // is unexpected.
            Log.w(TAG, "Unexpected command set: " + packet.commandSet);
            return;
        }
        ByteBuf in = new ByteBuf(packet.data);
        byte suspendPolicy = in.readByte();
        int eventCount = in.readInt();
        for (int i = 0; i < eventCount; i++) {
            byte eventKind = in.readByte();
            int requestId = in.readInt();
            long threadId = in.readLong();
            switch (eventKind) {
                case EventKind.VM_START: {
                    debugger.onVmStart();
                    publish(DebugEvents.vmStart());
                    break;
                }
                case EventKind.BREAKPOINT: {
                    SuspendInfo info = buildSuspend(debugger, threadId, SuspendInfo.Reason.BREAKPOINT, "");
                    debugger.onSuspend(info);
                    break;
                }
                case EventKind.SINGLE_STEP: {
                    SuspendInfo info = buildSuspend(debugger, threadId, SuspendInfo.Reason.STEP, "");
                    debugger.onSuspend(info);
                    break;
                }
                case EventKind.EXCEPTION: {
                    // Phase B2: parse the per-event payload and
                    // carry the exception class + object through to
                    // the suspend info. The JDWP Composite Event
                    // format for EXCEPTION is:
                    //   refTypeId (8)         - exception class
                    //   tag (1) + objectId (8) - the thrown exception instance
                    //   catchClassId (8)
                    //   catchMethodId (8)
                    //   catchIndex (8)
                    // The last three are all-zero when the exception
                    // is uncaught.
                    long exClassId = in.readLong();
                    byte exTag = in.readByte();
                    long exObjectId = in.readLong();
                    long catchClassId = in.readLong();
                    long catchMethodId = in.readLong();
                    long catchIndex = in.readLong();
                    boolean caught = (catchClassId != 0L || catchMethodId != 0L || catchIndex != 0L);
                    String message = debugger.fetchExceptionMessage(exObjectId, exTag);
                    String desc = (message == null || message.isEmpty())
                            ? "Exception at " + catchClassId + ":" + catchMethodId
                            : "Exception " + exClassId + ": " + message;
                    SuspendInfo info = buildSuspendEx(
                            debugger, threadId, SuspendInfo.Reason.EXCEPTION,
                            desc, exClassId, message, caught);
                    debugger.onSuspend(info);
                    break;
                }
                case EventKind.VM_DEATH: {
                    publish(DebugEvents.of(DebugEvents.Type.VM_DEATH, "VM death"));
                    break;
                }
                case EventKind.THREAD_START: {
                    publish(DebugEvents.of(DebugEvents.Type.THREAD_START, "Thread start"));
                    break;
                }
                case EventKind.THREAD_DEATH: {
                    publish(DebugEvents.of(DebugEvents.Type.THREAD_DEATH, "Thread death"));
                    break;
                }
                case EventKind.CLASS_PREPARE: {
                    // Phase B1: parse the per-event payload and
                    // hand the new class off to the Debugger so it
                    // can retry any pending breakpoints.
                    //   typeTag (1) + refTypeId (8) + sig (utf) + sourceFile (utf)
                    byte typeTag = in.readByte();
                    long classId = in.readLong();
                    String classSig = in.readString();
                    String sourceFile = in.readString();
                    debugger.onClassPrepare(classId, classSig, sourceFile);
                    publish(DebugEvents.of(DebugEvents.Type.CLASS_PREPARE, "Class prepare: " + classSig));
                    break;
                }
                default: {
                    Log.w(TAG, "Unhandled event kind: " + eventKind);
                }
            }
        }
        if (suspendPolicy == SuspendPolicy.ALL) {
            // No-op: the per-event handlers already invoked onSuspend.
        }
    }

    @NonNull
    private SuspendInfo buildSuspend(
            @NonNull Debugger debugger,
            long threadId,
            @NonNull SuspendInfo.Reason reason,
            @NonNull String description) {
        List<StackFrameInfo> frames;
        try {
            frames = debugger.getStackFrames(threadId, 0, 32);
        } catch (Throwable t) {
            frames = java.util.Collections.emptyList();
        }
        return new SuspendInfo(threadId, reason, frames, description);
    }

    /**
     * Phase B2: build a SuspendInfo that also carries the exception
     * class, message and caught/uncaught flag. Stack frames are
     * fetched in the same defensive way as the simpler builder.
     */
    @NonNull
    private SuspendInfo buildSuspendEx(
            @NonNull Debugger debugger,
            long threadId,
            @NonNull SuspendInfo.Reason reason,
            @NonNull String description,
            long exceptionClassId,
            @NonNull String exceptionMessage,
            boolean exceptionCaught) {
        List<StackFrameInfo> frames;
        try {
            frames = debugger.getStackFrames(threadId, 0, 32);
        } catch (Throwable t) {
            frames = java.util.Collections.emptyList();
        }
        return new SuspendInfo(threadId, reason, frames, description,
                exceptionClassId, exceptionMessage, exceptionCaught);
    }

    public interface DebugEventsListener {
        void onDebugEvent(@NonNull DebugEvents event);
    }
}
