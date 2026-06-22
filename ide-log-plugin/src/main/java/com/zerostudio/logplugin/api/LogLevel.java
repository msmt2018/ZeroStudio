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
 * Severity levels for log messages captured by the plugin.
 *
 * <p>The numeric values are aligned with android.util.Log priorities so that
 * the IDE can map them directly to its own filtering and styling.
 *
 * <p>These levels are used uniformly for plain logcat lines, application-level
 * log records, ANR events, crash reports and native JNI log lines.
 */
public final class LogLevel {

    /** Verbose level. Most detailed, typically disabled in production. */
    public static final int VERBOSE = 2;
    /** Debug level. Used for diagnostic information. */
    public static final int DEBUG = 3;
    /** Info level. Used for routine information. */
    public static final int INFO = 4;
    /** Warning level. Indicates a possible problem. */
    public static final int WARN = 5;
    /** Error level. Indicates a recoverable failure. */
    public static final int ERROR = 6;
    /** Assert level. Used for fatal / unrecoverable conditions. */
    public static final int ASSERT = 7;

    /** Special level for ANR events. */
    public static final int ANR = 100;
    /** Special level for native crash / signal reports. */
    public static final int NATIVE_CRASH = 101;
    /** Special level for uncaught Java exceptions. */
    public static final int JAVA_CRASH = 102;
    /** Special level for JNI / native (non-crash) log messages. */
    public static final int JNI = 103;
    /** Special level for performance / method timing events. */
    public static final int PERF = 104;

    private LogLevel() {
        // no instances
    }

    /** Returns the short character code (V/D/I/W/E/A) for a log level. */
    public static char shortCode(int level) {
        switch (level) {
            case VERBOSE: return 'V';
            case DEBUG:   return 'D';
            case INFO:    return 'I';
            case WARN:    return 'W';
            case ERROR:   return 'E';
            case ASSERT:  return 'A';
            case ANR:     return 'A';
            case NATIVE_CRASH: return 'F';
            case JAVA_CRASH:   return 'F';
            case JNI:     return 'N';
            case PERF:    return 'P';
            default:      return '?';
        }
    }
}
