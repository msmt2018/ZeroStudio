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
 * Holder for the singleton {@link ILogService}. Kept separate from the
 * interface so that the public API surface stays small and dependency-free.
 */
final class LogServiceHolder {

    /** The default singleton. Resolved lazily on first use. */
    static final ILogService INSTANCE;

    static {
        // The class is loaded reflectively so that the host application does
        // not need a hard reference to the Logback / AndroidX stack in its
        // own classpath. We resolve the implementation lazily on first access.
        ILogService instance = null;
        try {
            Class<?> clazz = Class.forName(
                    "com.zerostudio.logplugin.capture.LogCaptureService");
            Object obj = clazz.getMethod("getInstance").invoke(null);
            if (obj instanceof ILogService) {
                instance = (ILogService) obj;
            }
        } catch (Throwable t) {
            // Plugin will simply not function; the IDE will see a silent
            // disconnect and surface an explanatory error message.
        }
        INSTANCE = instance != null
                ? instance
                : new ILogService() {
                    @Override public void initialize(android.content.Context context) { }
                    @Override public void registerSink(ILogSink sink) { }
                    @Override public void unregisterSink(ILogSink sink) { }
                    @Override public boolean isConnected() { return false; }
                    @Override public int getListenPort() { return 0; }
                    @Override public void submitSynthetic(int level, String tag, String message) { }
                    @Override public void shutdown() { }
                };
    }

    private LogServiceHolder() {
        // no instances
    }
}
