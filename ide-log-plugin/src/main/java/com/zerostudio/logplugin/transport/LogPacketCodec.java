/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.transport;

import androidx.annotation.NonNull;
import com.zerostudio.logplugin.api.LogLevel;
import com.zerostudio.logplugin.api.LogPayload;
import com.zerostudio.logplugin.api.LogTransportType;
import com.zerostudio.logwire.WireCodec;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Backwards-compatible codec wrapper around {@link WireCodec}. The plugin
 * still ships its own helpers so that the rest of the plugin can keep using
 * {@link LogPayload}-typed values; the actual wire encoding lives in the
 * shared {@code :utilities:logwire} module.
 */
public final class LogPacketCodec {

    public static final int WIRE_VERSION = com.zerostudio.logwire.WireConstants.WIRE_VERSION;

    private LogPacketCodec() {
        // no instances
    }

    @NonNull
    public static byte[] encode(@NonNull LogPacket packet) {
        return WireCodec.encode(toWirePacket(packet));
    }

    @NonNull
    public static LogPacket decode(@NonNull DataInputStream in) throws IOException {
        com.zerostudio.logwire.WireCodec.WirePacket p = WireCodec.decode(in);
        return new LogPacket(p.type, p.body);
    }

    @NonNull
    public static byte[] encodeLog(@NonNull LogPayload payload) {
        return WireCodec.encodeLogRecord(
                payload.id,
                payload.timestamp,
                payload.level,
                payload.transport,
                payload.tag,
                payload.pid,
                payload.tid,
                payload.message,
                payload.throwable == null ? "" : stackToString(payload.throwable));
    }

    @NonNull
    public static LogPayload decodeLog(@NonNull byte[] body) throws IOException {
        WireCodec.LogRecord r = WireCodec.decodeLogRecord(body);
        Throwable t = r.stack.isEmpty() ? null : new Throwable(r.stack) {
            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        };
        return new LogPayload(
                r.id, r.timestamp, r.level, r.transport, r.tag,
                r.pid, r.tid, r.message, t);
    }

    @NonNull
    public static byte[] encodeHello(
            @NonNull String pluginVersion, int apiVersion, int logcatPort, int jdwpPort) {
        return WireCodec.encodeHello(
                "ide-log-plugin", pluginVersion, apiVersion, logcatPort, jdwpPort);
    }

    @NonNull
    public static HelloInfo decodeHello(@NonNull byte[] body) throws IOException {
        WireCodec.HelloInfo h = WireCodec.decodeHello(body);
        return new HelloInfo(h.pluginName, h.pluginVersion, h.apiVersion, h.logcatPort, h.jdwpPort);
    }

    @NonNull
    public static byte[] encodeBackpressure(int dropped) {
        return WireCodec.encodeBackpressure(dropped);
    }

    public static int decodeBackpressure(@NonNull byte[] body) throws IOException {
        return WireCodec.decodeBackpressure(body);
    }

    @NonNull
    private static String stackToString(@NonNull Throwable t) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8.name())) {
            t.printStackTrace(ps);
            return baos.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return t.toString();
        }
    }

    private static com.zerostudio.logwire.WireCodec.WirePacket toWirePacket(@NonNull LogPacket p) {
        return new com.zerostudio.logwire.WireCodec.WirePacket(p.type, p.body);
    }

    /** Mirror of {@link WireCodec.HelloInfo} with the plugin's preferred type names. */
    public static final class HelloInfo {
        @NonNull public final String pluginName;
        @NonNull public final String pluginVersion;
        public final int apiVersion;
        public final int logcatPort;
        public final int jdwpPort;

        public HelloInfo(
                @NonNull String pluginName,
                @NonNull String pluginVersion,
                int apiVersion,
                int logcatPort,
                int jdwpPort) {
            this.pluginName = pluginName;
            this.pluginVersion = pluginVersion;
            this.apiVersion = apiVersion;
            this.logcatPort = logcatPort;
            this.jdwpPort = jdwpPort;
        }
    }

    @SuppressWarnings("unused")
    private static void keepReferences() {
        int l = LogLevel.INFO;
        int t = LogTransportType.APP;
    }
}
