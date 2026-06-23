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
 */
package com.zerostudio.logplugin.bootstrap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zerostudio.logplugin.capture.LogCaptureService;
import com.zerostudio.logplugin.jdwp.JdwpServer;

public final class DebuggerBootstrapProvider extends ContentProvider {

    private static final String TAG = "DebuggerBootstrap";

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
}
