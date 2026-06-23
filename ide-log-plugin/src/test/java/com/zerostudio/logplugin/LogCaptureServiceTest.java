/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Phase C4: smoke tests for the host-side service classes. The
 *  tests use no Android framework features so they can run as
 *  plain JVM unit tests.
 */
package com.zerostudio.logplugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.itsaky.androidide.logwire.LogPayload;
import com.zerostudio.logplugin.api.LogLevel;
import com.zerostudio.logplugin.api.LogTransportType;
import com.zerostudio.logplugin.util.LogBuffer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class LogCaptureServiceTest {

    // ---------- LogLevel ----------

    @Test
    public void logLevel_constantsMatchAndroidLog() {
        assertEquals(2, LogLevel.VERBOSE);
        assertEquals(3, LogLevel.DEBUG);
        assertEquals(4, LogLevel.INFO);
        assertEquals(5, LogLevel.WARN);
        assertEquals(6, LogLevel.ERROR);
        assertEquals(7, LogLevel.ASSERT);
    }

    @Test
    public void logLevel_letter() {
        assertEquals('I', LogLevel.letter(LogLevel.INFO));
        assertEquals('W', LogLevel.letter(LogLevel.WARN));
        assertEquals('?', LogLevel.letter((byte) 99));
    }

    // ---------- LogTransportType ----------

    @Test
    public void logTransportType_valuesPresent() {
        assertEquals(2, LogTransportType.values().length);
        assertNotNull(LogTransportType.valueOf("TCP"));
        assertNotNull(LogTransportType.valueOf("FILE"));
    }

    @Test
    public void logTransportType_fromWire() {
        assertEquals(LogTransportType.TCP, LogTransportType.fromWire((byte) 0x01));
        assertEquals(LogTransportType.FILE, LogTransportType.fromWire((byte) 0x02));
        assertEquals(LogTransportType.TCP, LogTransportType.fromWire((byte) 0x99));
    }

    // ---------- LogBuffer ----------

    @Test
    public void logBuffer_startsEmpty() {
        LogBuffer b = new LogBuffer(4);
        assertEquals(0, b.size());
        assertEquals(4, b.capacity());
    }

    @Test
    public void logBuffer_appendsAndDrains() {
        LogBuffer b = new LogBuffer(4);
        b.append(new LogPayload(LogLevel.INFO, 1, "t", "tag", "a", null));
        b.append(new LogPayload(LogLevel.INFO, 2, "t", "tag", "b", null));
        assertEquals(2, b.size());

        List<LogPayload> snap = b.snapshot();
        assertEquals(2, snap.size());
        assertEquals("a", snap.get(0).message);
        assertEquals("b", snap.get(1).message);
    }

    @Test
    public void logBuffer_overwritesOldest() {
        LogBuffer b = new LogBuffer(2);
        b.append(new LogPayload(LogLevel.INFO, 1, "t", "tag", "1", null));
        b.append(new LogPayload(LogLevel.INFO, 2, "t", "tag", "2", null));
        b.append(new LogPayload(LogLevel.INFO, 3, "t", "tag", "3", null));
        assertEquals(2, b.size());
        List<LogPayload> snap = b.snapshot();
        assertEquals("2", snap.get(0).message);
        assertEquals("3", snap.get(1).message);
    }

    @Test
    public void logBuffer_clear() {
        LogBuffer b = new LogBuffer(2);
        b.append(new LogPayload(LogLevel.INFO, 1, "t", "tag", "1", null));
        b.clear();
        assertEquals(0, b.size());
        assertEquals(0, b.snapshot().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void logBuffer_rejectsZeroCapacity() {
        new LogBuffer(0);
    }

    // ---------- LogPayload (plugin-side wrapper) ----------

    @Test
    public void logPayload_carriesFields() {
        com.zerostudio.logplugin.api.LogPayload p = new com.zerostudio.logplugin.api.LogPayload(
                LogLevel.INFO, 100L, "main", "Z", "hi", "stacktrace");
        assertEquals(LogLevel.INFO, p.level);
        assertEquals(100L, p.timestampMs);
        assertEquals("main", p.threadId);
        assertEquals("Z", p.tag);
        assertEquals("hi", p.message);
        assertEquals("stacktrace", p.throwable);

        LogPayload wire = p.toWire();
        assertEquals(LogLevel.INFO, wire.level);
        assertEquals("hi", wire.message);
    }

    @Test
    public void logPayload_nullThrowableRoundTrips() {
        com.zerostudio.logplugin.api.LogPayload p = new com.zerostudio.logplugin.api.LogPayload(
                LogLevel.WARN, 0, "t", "tag", "m", null);
        assertNull(p.throwable);
        LogPayload wire = p.toWire();
        assertNull(wire.throwable);
    }
}
