/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  The state of the active debug session. We keep this intentionally
 *  simple: a small enum and a single mutex-protected getter/setter. The
 *  IDE uses the state to drive its UI.
 */

package com.zerostudio.debugger.model;

public final class DebugSession {

    public enum State {
        IDLE,
        CONNECTED,
        RUNNING,
        SUSPENDED,
        STEPPING,
        DISCONNECTED
    }

    private volatile State state = State.IDLE;

    public State getState() { return state; }

    public void setState(State s) { this.state = s; }

    public boolean isRunning() {
        return state == State.RUNNING || state == State.STEPPING;
    }

    public boolean isSuspended() {
        return state == State.SUSPENDED;
    }
}
