/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  The constants used by the JDWP wire protocol. The values are defined by
 *  the JDWP specification and must not change across implementations.
 *
 *  Source: https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp-spec.html
 */

package com.zerostudio.debugger.jdwp;

/** All JDWP command set identifiers. */
public final class CommandSet {
    public static final byte VirtualMachine = 1;
    public static final byte ReferenceType = 2;
    public static final byte ClassType = 3;
    public static final byte ArrayType = 4;
    public static final byte InterfaceType = 5;
    public static final byte Method = 6;
    public static final byte Field = 8;
    public static final byte ObjectReference = 9;
    public static final byte StringReference = 10;
    public static final byte ThreadReference = 11;
    public static final byte ThreadGroupReference = 12;
    public static final byte ArrayReference = 13;
    public static final byte ClassLoaderReference = 14;
    public static final byte EventRequest = 15;
    public static final byte StackFrame = 16;
    public static final byte ClassObjectReference = 17;
    public static final byte Module = 18;
    public static final byte ModulePackage = 19;
    public static final byte ModuleClass = 20;

    private CommandSet() {
        // no instances
    }
}
