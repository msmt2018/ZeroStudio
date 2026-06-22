/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.plugin;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.logplugin.capture.LogCaptureService;
import com.zerostudio.logplugin.jdwp.DebugPluginAttach;

/**
 * A {@link ContentProvider} that the IDE build pipeline injects into the
 * manifest of the user's debug-variant APK. Content providers are created
 * by the Android runtime <em>before</em> the {@link Application} instance,
 * so installing our hook here gives us the earliest possible moment to
 * start the log service and the JDWP server.
 *
 * <p>The class is referenced by the AAR's manifest with a per-application
 * authority (see {@code ${applicationId}.ide.logplugin.Installer}). The
 * authority is intentionally unique to avoid collisions with any other
 * provider in the host application.
 */
public final class IdeLogInstaller extends ContentProvider {

    private static final String TAG = "IdeLogInstaller";

    @Override
    public boolean onCreate() {
        try {
            Context ctx = getContext();
            if (ctx == null) {
                Log.w(TAG, "IdeLogInstaller.onCreate called with null context");
                return false;
            }
            Application app = findApplication(ctx);
            if (app != null) {
                DebugPluginAttach.attach(app);
            } else {
                DebugPluginAttach.attach(ctx);
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install log plugin", t);
            return false;
        }
    }

    @Nullable
    private Application findApplication(@NonNull Context context) {
        Context c = context;
        while (c != null) {
            if (c instanceof Application) {
                return (Application) c;
            }
            c = c.getApplicationContext();
        }
        return null;
    }

    // The IDE never queries this provider. The methods below exist only to
    // satisfy the ContentProvider contract.

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
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
    public int delete(
            @NonNull Uri uri,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }

    /**
     * For diagnostic use. The IDE can call this via reflection to ask the
     * plugin for its running port; the plugin already exposes the same
     * information via {@link LogCaptureService#getLogcatPort()}.
     */
    public static int currentLogcatPort() {
        return LogCaptureService.getInstance().getLogcatPort();
    }
}
