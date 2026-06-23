/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  JDWP command identifiers within each command set.
 */

package com.zerostudio.debugger.jdwp;

/** VirtualMachine command set (1) command codes. */
public final class VirtualMachineCmd {
    public static final byte Version = 1;
    public static final byte ClassesBySignature = 2;
    public static final byte AllClasses = 3;
    public static final byte AllThreads = 4;
    public static final byte TopLevelThreadGroups = 5;
    public static final byte Dispose = 6;
    public static final byte IDSizes = 7;
    public static final byte Suspend = 8;
    public static final byte Resume = 9;
    public static final byte Exit = 10;
    public static final byte CreateString = 11;
    public static final byte Capabilities = 12;
    public static final byte ClassPaths = 13;
    public static final byte DisposeObjects = 14;
    public static final byte HoldEvents = 15;
    public static final byte ReleaseEvents = 16;
    public static final byte CapabilitiesNew = 17;
    public static final byte RedefineClasses = 18;
    public static final byte SetDefaultStratum = 19;
    public static final byte AllClassesWithGeneric = 20;
    public static final byte InstanceCounts = 21;

    private VirtualMachineCmd() {
        // no instances
    }
}

/** ReferenceType command set (2) command codes. */
public final class ReferenceTypeCmd {
    public static final byte Signature = 1;
    public static final byte ClassLoader = 2;
    public static final byte Modifiers = 3;
    public static final byte Fields = 4;
    public static final byte Methods = 5;
    public static final byte GetValues = 6;
    public static final byte SourceFile = 7;
    public static final byte NestedTypes = 8;
    public static final byte Status = 9;
    public static final byte Interfaces = 10;
    public static final byte ClassObject = 11;
    public static final byte SourceDebugExtension = 12;
    public static final byte SignatureWithGeneric = 13;
    public static final byte FieldsWithGeneric = 14;
    public static final byte MethodsWithGeneric = 15;
    public static final byte Instances = 16;
    public static final byte ClassFileVersion = 17;
    public static final byte ConstantPool = 18;

    private ReferenceTypeCmd() {
        // no instances
    }
}

/** ClassType command set (3) command codes. */
public final class ClassTypeCmd {
    public static final byte Superclass = 1;
    public static final byte SetValues = 2;
    public static final byte InvokeMethod = 3;
    public static final byte NewInstance = 4;
    public static final byte ReflectedType = 5;

    private ClassTypeCmd() {
        // no instances
    }
}

/**
 * Phase A6: ArrayReference command set (13) command codes. Only the
 * subset used by the evaluator is listed here.
 */
public final class ArrayReferenceCmd {
    public static final byte Length = 1;
    public static final byte GetValues = 2;
    public static final byte SetValues = 3;

    private ArrayReferenceCmd() {
        // no instances
    }
}

/** EventRequest command set (15) command codes. */
public final class EventRequestCmd {
    public static final byte Set = 1;
    public static final byte Clear = 2;
    public static final byte ClearAllBreakpoints = 3;

    private EventRequestCmd() {
        // no instances
    }
}

/** StackFrame command set (16) command codes. */
public final class StackFrameCmd {
    public static final byte GetValues = 1;
    public static final byte SetValues = 2;
    public static final byte ThisObject = 3;
    public static final byte PopFrames = 4;

    private StackFrameCmd() {
        // no instances
    }
}

/** ThreadReference command set (11) command codes. */
public final class ThreadReferenceCmd {
    public static final byte Name = 1;
    public static final byte Suspend = 2;
    public static final byte Resume = 3;
    public static final byte Status = 4;
    public static final byte ThreadGroup = 5;
    public static final byte Frames = 6;
    public static final byte FrameCount = 7;
    public static final byte OwnedMonitors = 8;
    public static final byte CurrentContendedMonitor = 9;
    public static final byte Stop = 10;
    public static final byte Interrupt = 11;
    public static final byte SuspendCount = 12;
    public static final byte ForceEarlyReturn = 13;

    private ThreadReferenceCmd() {
        // no instances
    }
}

/** ObjectReference command set (9) command codes. */
public final class ObjectReferenceCmd {
    public static final byte ReferenceType = 1;
    public static final byte GetValues = 2;
    public static final byte SetValues = 3;
    public static final byte MonitorInfo = 5;
    public static final byte InvokeMethod = 6;
    public static final byte DisableCollection = 7;
    public static final byte EnableCollection = 8;
    public static final byte IsCollected = 9;
    public static final byte ReferringObjects = 10;

    private ObjectReferenceCmd() {
        // no instances
    }
}

/** Method command set (6) command codes. */
public final class MethodCmd {
    public static final byte LineTable = 1;
    public static final byte VariableTable = 2;
    public static final byte Bytecodes = 3;
    public static final byte IsObsolete = 4;
    public static final byte VariableTableWithGeneric = 5;

    private MethodCmd() {
        // no instances
    }
}
