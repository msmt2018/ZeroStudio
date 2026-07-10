/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Phase C4: log level constants. The plugin side re-exports the
 *  utilities/logwire numeric values so the JdwpServer doesn't
 *  need a transitive dependency on the logwire module.
 */
package com.zerostudio.logplugin.api;

import com.itsaky.androidide.logwire.WireConstants;

/** Log levels exposed for host-side use; values mirror android.util.Log. */
public final class LogLevel {
    public static final byte VERBOSE = WireConstants.LOG_VERBOSE;
    public static final byte DEBUG   = WireConstants.LOG_DEBUG;
    public static final byte INFO    = WireConstants.LOG_INFO;
    public static final byte WARN    = WireConstants.LOG_WARN;
    public static final byte ERROR   = WireConstants.LOG_ERROR;
    public static final byte ASSERT  = WireConstants.LOG_ASSERT;

    private LogLevel() {
        // no instances
    }

    public static char letter(byte level) {
        return com.itsaky.androidide.logwire.LogLevel.letter(level);
    }
}
