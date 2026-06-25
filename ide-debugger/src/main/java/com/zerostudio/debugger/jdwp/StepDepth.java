/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  JDWP Step depth constants (used in SINGLE_STEP EventRequest modifier).
 *
 *  Per the JDWP spec:
 *    INTO  = 0 — step into method calls
 *    OVER  = 1 — step over method calls
 *    OUT   = 2 — step out of the current frame
 *
 *  See: VirtualMachine/ThreadReference docs and the SINGLE_STEP
 *  EventRequest modifier.
 */
package com.zerostudio.debugger.jdwp;

public enum StepDepth {
    INTO((byte) 0),
    OVER((byte) 1),
    OUT((byte) 2);

    public final byte value;

    StepDepth(byte v) { this.value = v; }
}
