/*
 *  ZeroStudio IDE - utilities/logwire
 *
 *  Mirrors android.util.Log priorities so the IDE can map a numeric
 *  log level back to a human-readable string without depending on
 *  android.util.Log at the wire layer.
 */
package com.itsaky.androidide.logwire;

public final class LogLevel {

    private LogLevel() {
        // no instances
    }

    public static final byte VERBOSE = WireConstants.LOG_VERBOSE;
    public static final byte DEBUG   = WireConstants.LOG_DEBUG;
    public static final byte INFO    = WireConstants.LOG_INFO;
    public static final byte WARN    = WireConstants.LOG_WARN;
    public static final byte ERROR   = WireConstants.LOG_ERROR;
    public static final byte ASSERT  = WireConstants.LOG_ASSERT;

    /** Single-letter abbreviation for compact display in the IDE log. */
    public static char letter(byte level) {
        switch (level) {
            case VERBOSE: return 'V';
            case DEBUG:   return 'D';
            case INFO:    return 'I';
            case WARN:    return 'W';
            case ERROR:   return 'E';
            case ASSERT:  return 'A';
            default:      return '?';
        }
    }

    /** Parse a single-letter abbreviation back to a level, or -1 if unknown. */
    public static byte fromLetter(char c) {
        switch (Character.toUpperCase(c)) {
            case 'V': return VERBOSE;
            case 'D': return DEBUG;
            case 'I': return INFO;
            case 'W': return WARN;
            case 'E': return ERROR;
            case 'A': return ASSERT;
            default:  return -1;
        }
    }
}
