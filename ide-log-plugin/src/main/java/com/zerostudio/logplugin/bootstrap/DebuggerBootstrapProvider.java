/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Phase C5: the synthetic ContentProvider that the
 *  IdeDebuggerInitScriptPlugin (Phase C5) registers in every
 *  debuggable Android application.
 *
 *  The provider is invoked by the Android framework before
 *  Application.onCreate(), so we can attach the JDWP listener to
 *  the loopback interface as early as possible during process
 *  startup - before any user code runs, and before any
 *  attachBaseContext / content provider chains that might be
 *  doing their own thread attach.
 *
 *  The provider exposes a no-op query() / insert() / update() /
 *  delete() so the OS is satisfied that it has been initialised.
 *  The class is intentionally simple: a single static
 *  initializer kicks off the JdwpServer + LogCaptureService.
 *
 *  PR-D2: the call() method exposes the JDWP port as a Bundle
 *  so the IDE can discover it after launching the target
 *  application, without having to hardcode the port.
 */
package com.zerostudio.logplugin.bootstrap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zerostudio.logplugin.capture.LogCaptureService;
import com.zerostudio.logplugin.jdwp.JdwpServer;

public final class DebuggerBootstrapProvider extends ContentProvider {

    private static final String TAG = "DebuggerBootstrap";

    /** Authority string (must match IdeDebuggerInitScriptPlugin.BOOTSTRAP_AUTHORITY). */
    public static final String AUTHORITY = "com.zerostudio.debugger.bootstrap";

    /** call() method: returns a Bundle with the live JDWP port. */
    public static final String METHOD_GET_JDWP_PORT = "getJdwpPort";

    /** call() method: returns a Bundle with the live logcat port. */
    public static final String METHOD_GET_LOGCAT_PORT = "getLogcatPort";

    /** Bundle key for the port int value. */
    public static final String KEY_PORT = "port";

    @Override
    public boolean onCreate() {
        // Start the logcat stream first so even early log lines
        // produced by the JDWP startup are visible to the IDE.
        int logcatPort = LogCaptureService.getInstance().startLogcat(0);
        int jdwpPort = JdwpServer.startAndRegister(0);
        android.util.Log.i(TAG,
                "DebuggerBootstrap started: jdwp=" + jdwpPort
                        + " logcat=" + logcatPort);
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    /**
     * PR-D2: expose the live ports via [Bundle]. The IDE calls
     * `contentResolver.call(AUTHORITY, METHOD_GET_JDWP_PORT, null, null)`
     * after launching the target application; the returned
     * Bundle contains a single int entry [KEY_PORT].
     *
     * This avoids the need to hardcode a port in both the IDE
     * and the bootstrap provider.
     */
    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg,
                       @Nullable Bundle extras) {
        if (METHOD_GET_JDWP_PORT.equals(method)) {
            Bundle b = new Bundle();
            b.putInt(KEY_PORT, LogCaptureService.getInstance().getJdwpPort());
            return b;
        }
        if (METHOD_GET_LOGCAT_PORT.equals(method)) {
            Bundle b = new Bundle();
            b.putInt(KEY_PORT, LogCaptureService.getInstance().getLogcatPort());
            return b;
        }
        return super.call(method, arg, extras);
    }
}

