/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  JDWP Step size constants (used in SINGLE_STEP EventRequest modifier).
 *
 *  Per the JDWP spec:
 *    MIN  = 0 — step by the minimum code unit (Java bytecode)
 *    LINE = 1 — step by source line
 *
 *  LINE is the common choice for IDE-driven stepping; it stops at
 *  every source line even if a single line covers multiple bytecodes.
 */
package com.zerostudio.debugger.jdwp;

public enum StepSize {
    MIN((byte) 0),
    LINE((byte) 1);

    public final byte value;

    StepSize(byte v) { this.value = v; }
}
