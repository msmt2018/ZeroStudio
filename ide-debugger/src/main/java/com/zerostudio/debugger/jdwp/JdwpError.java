/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Error codes returned by the JDWP server in reply packets.
 */

package com.zerostudio.debugger.jdwp;

/** Error codes returned by the JDWP server. */
public final class JdwpError {
    public static final int NONE = 0;
    public static final int INVALID_THREAD = 10;
    public static final int INVALID_THREAD_GROUP = 11;
    public static final int INVALID_PRIORITY = 12;
    public static final int THREAD_NOT_SUSPENDED = 13;
    public static final int THREAD_SUSPENDED = 14;
    public static final int THREAD_NOT_ALIVE = 15;
    public static final int INVALID_OBJECT = 20;
    public static final int INVALID_CLASS = 21;
    public static final int CLASS_NOT_PREPARED = 22;
    public static final int INVALID_METHODID = 23;
    public static final int INVALID_LOCATION = 24;
    public static final int INVALID_FIELDID = 25;
    public static final int INVALID_FRAMEID = 30;
    public static final int NO_MORE_FRAMES = 31;
    public static final int OPAQUE_FRAME = 32;
    public static final int NOT_CURRENT_FRAME = 33;
    public static final int TYPE_MISMATCH = 34;
    public static final int INVALID_SLOT = 35;
    public static final int OUT_OF_MEMORY = 40;
    public static final int ACCESS_DENIED = 41;
    public static final int VM_DEAD = 42;
    public static final int INVALID_EVENT_TYPE = 50;
    public static final int ILLEGAL_ARGUMENT = 51;
    public static final int OUT_OF_MEMORY_EVENTS = 52;
    public static final int CANNOT_MODIFY_EVENT = 53;
    public static final int CANNOT_SUSPEND_EVENT_THREAD = 54;
    public static final int NOT_IMPLEMENTED = 99;
    public static final int NULL_POINTER = 100;
    public static final int DUPLICATE = 101;
    public static final int NOT_FOUND = 102;
    public static final int INVALID_MONITOR = 103;
    public static final int NOT_MONITOR_OWNER = 104;
    public static final int INTERRUPT = 105;
    public static final int INVALID_CLASS_FORMAT = 106;
    public static final int CIRCULAR_CLASS_DEFINITION = 107;
    public static final int FAILS_VERIFICATION = 108;
    public static final int ADD_METHOD_NOT_IMPLEMENTED = 109;
    public static final int SCHEMA_CHANGE_NOT_IMPLEMENTED = 110;
    public static final int INVALID_TYPESTATE = 111;
    public static final int HIERARCHY_CHANGE_NOT_IMPLEMENTED = 112;
    public static final int DELETE_METHOD_NOT_IMPLEMENTED = 113;
    public static final int UNSUPPORTED_VERSION = 114;
    public static final int NAMES_DONT_MATCH = 115;
    public static final int CLASS_MODIFIERS_CHANGE_NOT_IMPLEMENTED = 116;
    public static final int METHOD_MODIFIERS_CHANGE_NOT_IMPLEMENTED = 117;
    public static final int NOT_IN_PRE_VERIFIER = 118;
    public static final int NO_CLASS_PATHS = 119;
    public static final int CRITICAL_CLASS_EDIT = 120;
    public static final int ABSENT_INFORMATION = 121;

    private JdwpError() {
        // no instances
    }
}
