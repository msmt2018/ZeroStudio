/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.api;

/**
 * Identifies the transport used to deliver a log payload.
 *
 * <p>The plugin multiplexes several sources (logcat, application logs, crashes,
 * ANR, native JNI) and the IDE uses the transport hint for filtering and
 * styling in the AppLogFragment view.
 */
public final class LogTransportType {

    /** Logcat line captured from a running app process. */
    public static final int LOGCAT = 1;
    /** Application-level log line (SLF4J / Logback). */
    public static final int APP = 2;
    /** Uncaught Java exception. */
    public static final int CRASH = 3;
    /** ANR (Application Not Responding) report. */
    public static final int ANR = 4;
    /** Native JNI log line. */
    public static final int JNI = 5;
    /** Performance / method timing record. */
    public static final int PERF = 6;
    /** Native crash (e.g. tombstone). */
    public static final int NATIVE_CRASH = 7;

    private LogTransportType() {
        // no instances
    }
}
