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
                    long refTypeId = in.readLong();
                    long locationClassId = in.readLong();
                    long locationMethodId = in.readLong();
                    long locationCodeIndex = in.readLong();
                    // catchLocation is unused for now
                    SuspendInfo info = buildSuspend(debugger, threadId, SuspendInfo.Reason.EXCEPTION,
                            "Exception at " + locationClassId + ":" + locationMethodId);
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
                    publish(DebugEvents.of(DebugEvents.Type.CLASS_PREPARE, "Class prepare"));
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

    public interface DebugEventsListener {
        void onDebugEvent(@NonNull DebugEvents event);
    }
}
