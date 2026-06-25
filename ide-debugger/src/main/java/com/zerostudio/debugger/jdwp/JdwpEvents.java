/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Event kinds, suspend policies and step sizes used in JDWP
 *  EventRequest.Set packets.
 *
 *  Each nested class holds the constants for one concept
 *  (EventKind, SuspendPolicy, StepDepth, StepSize, ModKind).
 *  Callers reference them as {@code JdwpEvents.EventKind.BREAKPOINT}.
 */

package com.zerostudio.debugger.jdwp;

public final class JdwpEvents {

    private JdwpEvents() {
        // no instances
    }

    /** Event kinds sent in EventRequest.Set. */
    public static final class EventKind {
        public static final byte VM_START = (byte) 0x40;
        public static final byte VM_DEATH = (byte) 0x41;
        public static final byte THREAD_START = (byte) 0x42;
        public static final byte THREAD_DEATH = (byte) 0x43;
        public static final byte CLASS_PREPARE = (byte) 0x44;
        public static final byte CLASS_UNLOAD = (byte) 0x45;
        public static final byte BREAKPOINT = (byte) 0x46;
        public static final byte EXCEPTION = (byte) 0x47;
        public static final byte FIELD_ACCESS = (byte) 0x48;
        public static final byte FIELD_MODIFICATION = (byte) 0x49;
        public static final byte SINGLE_STEP = (byte) 0x4A;
        public static final byte METHOD_ENTRY = (byte) 0x4B;
        public static final byte METHOD_EXIT = (byte) 0x4C;
        public static final byte FRAME_POP = (byte) 0x4D;
        public static final byte USER_DEFINED = (byte) 0x4E;
        public static final byte VM_DISCONNECTED = (byte) 0x4F;

        private EventKind() {
            // no instances
        }
    }

    /** Suspend policies attached to event requests. */
    public static final class SuspendPolicy {
        public static final byte NONE = 0;
        public static final byte EVENT_THREAD = 1;
        public static final byte ALL = 2;

        private SuspendPolicy() {
            // no instances
        }
    }

    /** Step sizes for SINGLE_STEP events. */
    public static final class StepDepth {
        public static final byte INTO = 0;
        public static final byte OVER = 1;
        public static final byte OUT = 2;

        private StepDepth() {
            // no instances
        }
    }

    /** Step granularity. */
    public static final class StepSize {
        public static final byte MIN = 0;
        public static final byte LINE = 1;

        private StepSize() {
            // no instances
        }
    }

    /** Modifier kinds used inside an event request. */
    public static final class ModKind {
        public static final int COUNT = 1;
        public static final int CONDITIONAL = 2;
        public static final int THREAD_ONLY = 3;
        public static final int CLASS_ONLY = 4;
        public static final int CLASS_MATCH_PATTERN = 5;
        public static final int CLASS_EXCLUDE_PATTERN = 6;
        public static final int LOCATION = 7;
        public static final int EXCEPTION_ONLY = 8;
        public static final int FIELD_ONLY = 9;
        public static final int STEP = 10;
        public static final int INSTANCE_ONLY = 11;
        public static final int SOURCE_NAME_MATCH = 12;

        private ModKind() {
            // no instances
        }
    }
}
