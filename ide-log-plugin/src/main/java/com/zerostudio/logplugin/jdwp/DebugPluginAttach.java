/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.jdwp;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.logplugin.capture.LogCaptureService;

/**
 * Hook that the IDE build pipeline injects into the host application's
 * {@code <application android:name=...>} attribute. The hook fires before
 * any of the host's own code runs (because ContentProviders are created
 * before {@code Application.onCreate()}) and starts the JDWP server as
 * early as possible.
 *
 * <p>PR-1 only starts the log service and reserves a JDWP port. PR-2 wires
 * in the actual JDWP protocol handling.
 */
public final class DebugPluginAttach {

    private DebugPluginAttach() {
        // no instances
    }

    private static final String TAG = "DebugPluginAttach";

    /** Start the log service and JDWP server for the given application. */
    public static void attach(@NonNull Application app) {
        try {
            LogCaptureService.getInstance().initialize(app);
            // Try to start the JDWP server on a port derived from the log
            // service port (e.g. logPort + 1). The IDE will be told the
            // actual port via the hello packet.
            int jdwpPort = JdwpServer.startAndRegister(0);
            Log.i(TAG, "DebugPluginAttach installed (jdwpPort=" + jdwpPort + ")");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to attach debug plugin", t);
        }
    }

    /**
     * Start the log service and JDWP server for a plain context. The
     * application context is used so that we never accidentally hold a
     * reference to an Activity.
     */
    public static void attach(@Nullable Context context) {
        if (context == null) {
            return;
        }
        Application app = findApplication(context);
        if (app != null) {
            attach(app);
            return;
        }
        try {
            LogCaptureService.getInstance().initialize(context.getApplicationContext());
            JdwpServer.startAndRegister(0);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to attach debug plugin (ctx)", t);
        }
    }

    @Nullable
    private static Application findApplication(@NonNull Context context) {
        Context c = context;
        while (c != null) {
            if (c instanceof Application) {
                return (Application) c;
            }
            if (c.getClass().getName().equals("android.app.ContextImpl")) {
                // Walk the wrapper for the base context.
                try {
                    java.lang.reflect.Field f = c.getClass().getDeclaredField("mPackageInfo");
                    f.setAccessible(true);
                    Object pi = f.get(c);
                    if (pi != null) {
                        java.lang.reflect.Field a = pi.getClass().getDeclaredField("mApplication");
                        a.setAccessible(true);
                        Object ap = a.get(pi);
                        if (ap instanceof Application) {
                            return (Application) ap;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            c = c.getApplicationContext();
        }
        return null;
    }
}
